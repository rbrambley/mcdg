package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.data.Hole;
import com.mcdg.game.BotSimulator;
import com.mcdg.net.AceCinematicSync;
import com.mcdg.net.RoundRunningScoresSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.ui.HudStateFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import com.mcdg.world.SafePositionFinder;

public final class HoleProgressTracker {
    private static final int BASKET_RADIUS_BLOCKS = 2;
    private static final int BASKET_HEIGHT_TOLERANCE = 4;
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 14;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;
    // Proximity make radius: flat putts within this distance that hit the basket column count as makes
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();
    private static final Map<UUID, Map<Integer, Integer>> HOLE_SCORE_HISTORY = new HashMap<>();
    static final Map<UUID, Integer> HOLE_ONE_RANDOM_ORDER = new HashMap<>();
    private static final Map<UUID, Integer> CACHED_CORRIDOR_HALF_WIDTH = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_LIE_POSITION = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_BREADCRUMB_POSITION = new HashMap<>();
    private static int LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
    private static boolean ROUND_WAS_ACTIVE = false;

    private HoleProgressTracker() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            LeaderboardManager leaderboardManager,
            boolean hudScoringDebug,
            boolean strictFlowDebug,
            boolean enableSurvivalRewards
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            AceCompanionService.tick(server);
            if (!courseManager.isRoundActive()) {
                if (ROUND_WAS_ACTIVE) {
                    ROUND_WAS_ACTIVE = false;
        ThrowResolver.reset();
                    DiscFlightSimulator.reset();
                    HOLE_ONE_RANDOM_ORDER.clear();
        TurnManager.reset();
                    LAST_LIE_POSITION.clear();
                    LAST_BREADCRUMB_POSITION.clear();
                    CACHED_CORRIDOR_HALF_WIDTH.clear();
                    AceCompanionService.reset();
                    RoundRewardService.reset();
                    BotSimulator.reset();
                    if (LAST_RUNNING_SCOREBOARD_HASH != Integer.MIN_VALUE) {
                        sendRunningScoreboardInactive(server);
                    }
                    HoleMapSyncService.sendInactive(server);
                    HoleMapSyncService.reset();
                    LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
                    LieMarkerService.clearAllLieMarkers(server);
                }
                return;
            }
            ROUND_WAS_ACTIVE = true;

            Course course = courseManager.getActiveCourse().orElse(null);
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (course == null || placed == null) {
                return;
            }

            Map<UUID, PlayerRoundState> snapshot = roundStateManager.snapshotStates();
            ensureHoleOneRandomOrder(snapshot);
            
            // Process bot throws for multiplayer testing
            BotSimulator.tick(server, courseManager, roundStateManager);
            
            TurnManager.enforceTurnTimeouts(server, courseManager, roundStateManager, course, placed, snapshot);
            maybeSendRunningScoreboard(server, courseManager, course, placed, roundStateManager, snapshot);
            for (Map.Entry<UUID, PlayerRoundState> entry : snapshot.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
                    continue;
                }

                // Keep random course-construction pickups out of player inventories during active play.
                if ((server.getTicks() % 20) == 0) {
                    RoundInventoryCleaner.purgeJunkItems(player);
                }

                boolean suppressHud = player.isUsingItem() && McdgItems.isDisc(player.getActiveItem());

                PlayerRoundState state = entry.getValue();
                Hole currentHole = course.holes().get(state.currentHole() - 1);
                BlockPos basket = placed.holeBaskets().get(state.currentHole());
                BlockPos tee = placed.holeTees().get(state.currentHole());
                BlockPos alternateAnchor = placed.holeAlternateAnchors().get(state.currentHole());
                if (basket == null) {
                    continue;
                }

                BlockPos currentLie = state.lie();
                BlockPos lastLie = LAST_LIE_POSITION.get(player.getUuid());
                // Only update lie marker if the player has not yet completed the hole
                // This prevents texture changes when waiting at the basket for other players
                if ((lastLie == null || !currentLie.equals(lastLie)) && !isAtBasket(currentLie, basket)) {
                    LieMarkerService.updateLieMarker(player, currentLie);
                    LAST_LIE_POSITION.put(player.getUuid(), currentLie);
                }

                if (tee != null) {
                    state = ThrowResolver.resolve(
                            player,
                            state,
                            currentHole,
                            tee,
                            basket,
                            alternateAnchor,
                            roundStateManager,
                            rulesetManager,
                            courseManager,
                            hudScoringDebug,
                            strictFlowDebug
                    );
                }

                int lieDistMeters = DistanceUtils.distanceMeters(state.lie(), basket);
                int lieDistFeet = DistanceUtils.distanceFeet(state.lie(), basket);
                int completedPar = cumulativeParThroughHole(course, state.currentHole() - 1);
                int runningExpectedThrows = completedPar + state.holeStrokes();
                int holeParDelta = computeHolePaceDelta(
                    currentHole.par(),
                    currentHole.distanceFeet(),
                    lieDistMeters,
                    state.holeStrokes()
                );
                int cumulativeParDelta = state.totalStrokes() - runningExpectedThrows;
                UUID playerId = player.getUuid();
                Integer cachedHole = HoleMapSyncService.lastHoleForPlayer(playerId);
                int corridorHalfWidth;
                if (cachedHole == null || cachedHole != state.currentHole() || !CACHED_CORRIDOR_HALF_WIDTH.containsKey(playerId)) {
                    corridorHalfWidth = OutOfBoundsClassifier.strictCorridorHalfWidth(
                        currentHole,
                        (ServerWorld) player.getWorld(),
                        tee,
                        basket,
                        rulesetManager
                    );
                    CACHED_CORRIDOR_HALF_WIDTH.put(playerId, corridorHalfWidth);
                } else {
                    corridorHalfWidth = CACHED_CORRIDOR_HALF_WIDTH.get(playerId);
                }

                if ((server.getTicks() % 5) == 0) {
                    HoleMapSyncService.sync(
                            server,
                            player,
                            course,
                            placed,
                            state,
                            currentHole,
                            tee,
                            basket,
                            alternateAnchor,
                            rulesetManager,
                            corridorHalfWidth,
                            cumulativeParDelta,
                            ThrowResolver.lastThrowDistanceFeetForPlayer(player.getUuid()),
                            ThrowResolver.lastThrowStatsForPlayer(player.getUuid()),
                            strictFlowDebug
                    );
                }
                if (!suppressHud && hudScoringDebug && (server.getTicks() % 20) == 0) {
                    McdgMod.LOGGER.info(
                            "HUD score debug | player={} hole={} par={} holeDistFt={} lieDistMeters={} lieDistFeet={} holeStrokes={} totalStrokes={} expectedRunning={} holeDelta={} totalDelta={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            currentHole.par(),
                            currentHole.distanceFeet(),
                            lieDistMeters,
                            lieDistFeet,
                            state.holeStrokes(),
                            state.totalStrokes(),
                            runningExpectedThrows,
                            holeParDelta,
                            cumulativeParDelta
                    );
                }
                if (!suppressHud && (server.getTicks() % 20) == 0) {
                    if (player.getWorld() instanceof ServerWorld serverWorld) {
                        // Only spawn breadcrumb if player has moved significantly or is far from basket
                        BlockPos playerPos = player.getBlockPos();
                        double distToBasket = playerPos.getSquaredDistance(basket);
                        BlockPos lastBreadcrumbPos = LAST_BREADCRUMB_POSITION.get(player.getUuid());
                        if (lastBreadcrumbPos == null || playerPos.getSquaredDistance(lastBreadcrumbPos) > 16.0 || distToBasket > 100.0) {
                            LieMarkerService.spawnBreadcrumbLine(serverWorld, player, basket);
                            LAST_BREADCRUMB_POSITION.put(player.getUuid(), playerPos);
                        }
                    }
                }

                if ((server.getTicks() % 20) == 0) {
                    TurnManager.sendTurnActionBar(server, player, state.currentHole());
                }

                // Score completion only from the resolved lie, so walking into the basket does not count.
                if (!isAtBasket(state.lie(), basket)) {
                    continue;
                }

                boolean scoreAlreadyRecorded = hasHoleScore(player.getUuid(), state.currentHole());
                // Record and broadcast hole completion once per player per hole
                if (!scoreAlreadyRecorded) {
                    ScorecardManager.recordHoleScore(player, state.currentHole(), state.holeStrokes());
                    recordHoleScore(player.getUuid(), state.currentHole(), state.holeStrokes());
                    broadcastHoleCompletion(server, placed.worldKey(), player, state.currentHole(), state.holeStrokes(), state.totalStrokes(), course.holes().size());
                }

                // Do not advance to the next hole or finish the round until all players have completed this hole
                if (!TurnManager.isAllPlayersOnHoleCompleted(roundStateManager, courseManager, placed, state.currentHole(), 3.0)) {
                    continue;
                }

                if (state.currentHole() >= course.holes().size()) {
                    int totalPar = totalCoursePar(course);
                    BlockPos firstTee = placed.holeTees().get(1);
                    if (firstTee != null) {
                        player.teleport(firstTee.getX() + 0.5, firstTee.getY() + 1.0, firstTee.getZ() + 0.5);
                    }

                    if (state.holeStrokes() == 1) {
                        roundStateManager.recordAce(player.getUuid());
                        ServerPlayNetworking.send(player, AceCinematicSync.Payload.active(state.currentHole(), currentHole.distanceFeet()));
                        AceCompanionService.scheduleForPlayer(player.getUuid(), server.getOverworld().getTime());
                    }

                    removeTemporaryRoundItems(player);

                    roundStateManager.recordCompletedRound(player.getUuid(), state.totalStrokes());
                    ScorecardManager.recordCompletionPlayer(player);
                    if (leaderboardManager != null) {
                        leaderboardManager.recordScore(server, course.name(), player.getGameProfile().getName(), state.totalStrokes());
                    }

                    int finalCompletedPar = cumulativeParThroughHole(course, state.currentHole() - 1);
                    int finalRunningExpected = finalCompletedPar + state.holeStrokes();
                    int finalCumulativeDelta = state.totalStrokes() - finalRunningExpected;
                    int finalCorridorHalfWidth = CACHED_CORRIDOR_HALF_WIDTH.getOrDefault(player.getUuid(), 24);
                    HoleMapSyncService.forceSync(
                            server,
                            player,
                            course,
                            placed,
                            state,
                            currentHole,
                            tee,
                            basket,
                            alternateAnchor,
                            rulesetManager,
                            finalCorridorHalfWidth,
                            finalCumulativeDelta,
                            ThrowResolver.lastThrowDistanceFeetForPlayer(player.getUuid()),
                            ThrowResolver.lastThrowStatsForPlayer(player.getUuid()),
                            strictFlowDebug
                    );

                    if (enableSurvivalRewards) {
                        RoundRewardService.grantRoundRewards(player, state.totalStrokes(), totalPar, state.aceCount(), rulesetManager.isStrict());
                    }
                    StaminaXpService.awardRoundXp(player, state.totalStrokes(), totalPar, state.aceCount(), rulesetManager.isStrict());

                    PlayerSkillManager.awardXp(player, Math.max(0, totalPar - state.totalStrokes()) * 10 + state.aceCount() * 25);
                    PlayerSkillManager.recordRoundCompleted(player, state.currentHole(), state.aceCount());

                    roundStateManager.clearPlayer(player.getUuid());

                    if (firstTee != null) {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                        player.sendMessage(Text.literal("Returned to Hole 1 tee."), false);
                    } else {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                    }

                    RoundLeaderboardHelper.broadcastRoundLeaderboard(server, placed.worldKey(), roundStateManager, totalPar);

                    boolean roundEnded = roundStateManager.snapshotStates().isEmpty();
                    if (roundEnded) {
                        RoundLeaderboardHelper.sendRoundCompleteCinematic(server, placed.worldKey(), roundStateManager, totalPar);
                        courseManager.setRoundActive(false);
                        ServerWorld courseWorld = server.getWorld(placed.worldKey());
                        if (courseWorld != null) {
                            RoundChunkLoader.unloadAll(courseWorld);
                        }
                        courseManager.clearPlacedCourseState();
                    }
                    continue;
                }

                int nextHole = state.currentHole() + 1;
                BlockPos nextTee = placed.holeTees().get(nextHole);
                if (nextTee == null) {
                    roundStateManager.clearPlayer(player.getUuid());
                    player.sendMessage(Text.literal("Round ended: next tee could not be resolved."), false);
                    continue;
                }

                int completedHolePar = currentHole.par();
                roundStateManager.advanceToNextHole(player.getUuid(), nextTee);
                roundStateManager.syncNextThrowPowerMultiplier(player);
                player.teleport(nextTee.getX() + 0.5, nextTee.getY() + 1.0, nextTee.getZ() + 0.5);
                LieMarkerService.updateLieMarker(player, nextTee);

                // Force sync hole map to show next hole immediately
                PlayerRoundState nextState = roundStateManager.getState(player.getUuid()).orElse(null);
                if (nextState != null) {
                    Hole nextHoleData = course.holes().get(nextState.currentHole() - 1);
                    BlockPos nextBasket = placed.holeBaskets().get(nextState.currentHole());
                    BlockPos nextAlternateAnchor = placed.holeAlternateAnchors().get(nextState.currentHole());
                    int nextCorridorHalfWidth = CACHED_CORRIDOR_HALF_WIDTH.getOrDefault(player.getUuid(), 24);
                    int nextCumulativeDelta = nextState.totalStrokes() - (cumulativeParThroughHole(course, nextState.currentHole() - 1) + nextState.holeStrokes());
                    HoleMapSyncService.forceSync(
                            server,
                            player,
                            course,
                            placed,
                            nextState,
                            nextHoleData,
                            nextTee,
                            nextBasket,
                            nextAlternateAnchor,
                            rulesetManager,
                            nextCorridorHalfWidth,
                            nextCumulativeDelta,
                            0,
                            null,
                            strictFlowDebug
                    );
                }

                if (state.holeStrokes() == 1) {
                    roundStateManager.recordAce(player.getUuid());
                    ServerPlayNetworking.send(player, AceCinematicSync.Payload.active(state.currentHole(), currentHole.distanceFeet()));
                    AceCompanionService.scheduleForPlayer(player.getUuid(), server.getOverworld().getTime());
                }
                GolfTitleMessenger.sendHoleFinishTitle(player, state.holeStrokes(), completedHolePar);
            }
        });
    }

    static ThrowTurnGate evaluateThrowGate(
            ServerPlayerEntity player,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (!courseManager.isRoundActive()) {
            return ThrowTurnGate.allowed();
        }

        if (courseManager.isWarmupActive()) {
            return ThrowTurnGate.blocked("Warmup in progress - wait for the round to start.");
        }

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            return ThrowTurnGate.allowed();
        }

        PlayerRoundState playerState = roundStateManager.getState(player.getUuid()).orElse(null);
        if (playerState == null) {
            return ThrowTurnGate.blocked("You are not enrolled in the active round.");
        }

        // Check if it is this player's turn on the current hole
        if (!TurnManager.isActiveTurnPlayer(player.getUuid(), playerState.currentHole())) {
            // Get the name of the active turn player
            UUID activeTurnPlayerId = TurnManager.getActiveTurnPlayer(playerState.currentHole());
            if (activeTurnPlayerId != null) {
                if (BotSimulator.isBot(activeTurnPlayerId)) {
                    String botName = BotSimulator.getBotProfile(activeTurnPlayerId)
                            .map(BotSimulator.BotProfile::name)
                            .orElse("Bot");
                    return ThrowTurnGate.blocked("Wait your turn. " + botName + " throws first.", true);
                } else {
                    ServerPlayerEntity activeTurnPlayer = player.getServer().getPlayerManager().getPlayer(activeTurnPlayerId);
                    if (activeTurnPlayer != null) {
                        return ThrowTurnGate.blocked("Wait your turn. " + activeTurnPlayer.getGameProfile().getName() + " throws first.", true);
                    }
                }
            }
            return ThrowTurnGate.blocked("Wait your turn.", true);
        }

        return ThrowTurnGate.allowed();
    }

    public static Optional<BlockPos> relocatePlayerToSafeLie(ServerPlayerEntity player, RoundStateManager roundStateManager) {
        if (player == null || roundStateManager == null) {
            return Optional.empty();
        }

        PlayerRoundState state = roundStateManager.getState(player.getUuid()).orElse(null);
        if (state == null) {
            return Optional.empty();
        }

        ServerWorld world = player.getServerWorld();
        BlockPos safeLie = SafePositionFinder.findNearestStandableFeet(world, state.lie());
        if (!SafePositionFinder.isStandableFeet(world, safeLie)) {
            return Optional.empty();
        }

        roundStateManager.updateLie(player.getUuid(), safeLie);
        LieMarkerService.updateLieMarker(player, safeLie);
        player.teleport(safeLie.getX() + 0.5, safeLie.getY() + 1.0, safeLie.getZ() + 0.5);
        ThrowResolver.recordResolutionReason(player.getUuid(), "GOTOLIE");
        return Optional.of(safeLie);
    }

    public static void sendRunningScoreboardToPlayer(
            ServerPlayerEntity player,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (player == null || (!courseManager.isRoundActive() && !courseManager.isWarmupActive())) {
            return;
        }

        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (course == null || placed == null) {
            return;
        }

        if (player.getWorld().getRegistryKey() != placed.worldKey()) {
            return;
        }

        Map<UUID, PlayerRoundState> snapshot = roundStateManager.snapshotStates();
        Set<UUID> participantIds = courseManager.getActiveParticipantIds();
        if (participantIds.isEmpty()) {
            return;
        }

        PlayerRoundState state = snapshot.get(player.getUuid());
        int focusHole = state == null
                ? inferFocusHoleFromHistory(course.holes().size())
                : Math.max(1, Math.min(course.holes().size(), state.currentHole()));
        List<RoundRunningScoresSync.PlayerRow> rows = buildRunningScoreRows(
                player.getServer(),
                participantIds,
                focusHole,
                snapshot,
                course.holes().size()
        );
        ServerPlayNetworking.send(player, RoundRunningScoresSync.Payload.active(course.holes().size(), focusHole, course.name(), rows));
    }

    private static int cumulativeParThroughHole(Course course, int holeIndexInclusive) {
        int par = 0;
        int max = Math.min(holeIndexInclusive, course.holes().size());
        for (int i = 0; i < max; i++) {
            par += course.holes().get(i).par();
        }
        return par;
    }

    private static int totalCoursePar(Course course) {
        int par = 0;
        for (Hole hole : course.holes()) {
            par += hole.par();
        }
        return par;
    }




    private static void maybeSendRunningScoreboard(
            MinecraftServer server,
            ActiveCourseManager courseManager,
            Course course,
            PlacedCourseState placed,
            RoundStateManager roundStateManager,
            Map<UUID, PlayerRoundState> snapshot
    ) {
        if (course == null || placed == null) {
            return;
        }

        Set<UUID> participantIds = courseManager.getActiveParticipantIds();
        if (participantIds.isEmpty()) {
            return;
        }

        int hash = computeRunningScoreboardHash(participantIds, snapshot);
        if (hash == LAST_RUNNING_SCOREBOARD_HASH) {
            return;
        }
        LAST_RUNNING_SCOREBOARD_HASH = hash;

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == placed.worldKey())
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity viewer : viewers) {
            PlayerRoundState viewerState = roundStateManager.getState(viewer.getUuid()).orElse(null);
            int focusHole = viewerState == null
                    ? inferFocusHoleFromHistory(course.holes().size())
                    : Math.max(1, Math.min(course.holes().size(), viewerState.currentHole()));
            List<RoundRunningScoresSync.PlayerRow> rows = buildRunningScoreRows(server, participantIds, focusHole, snapshot, course.holes().size());
            ServerPlayNetworking.send(
                    viewer,
                    RoundRunningScoresSync.Payload.active(course.holes().size(), focusHole, course.name(), rows)
            );
        }
    }

    private static int computeRunningScoreboardHash(Set<UUID> participantIds, Map<UUID, PlayerRoundState> snapshot) {
        int hash = 17;
        List<UUID> sortedIds = new ArrayList<>(participantIds);
        sortedIds.sort(UUID::compareTo);
        for (UUID playerId : sortedIds) {
            hash = (31 * hash) + playerId.hashCode();
            Map<Integer, Integer> scores = HOLE_SCORE_HISTORY.get(playerId);
            if (scores != null) {
                List<Integer> holes = new ArrayList<>(scores.keySet());
                holes.sort(Integer::compareTo);
                for (Integer hole : holes) {
                    hash = (31 * hash) + Objects.hash(hole, scores.get(hole));
                }
            }

            PlayerRoundState state = snapshot.get(playerId);
            if (state != null) {
                hash = (31 * hash) + state.currentHole();
                hash = (31 * hash) + state.totalStrokes();
            }
        }
        return hash;
    }

    private static int inferFocusHoleFromHistory(int totalHoles) {
        int maxCompletedHole = 1;
        for (Map<Integer, Integer> scoreByHole : HOLE_SCORE_HISTORY.values()) {
            for (Map.Entry<Integer, Integer> entry : scoreByHole.entrySet()) {
                if (entry.getValue() != null && entry.getValue() >= 0) {
                    maxCompletedHole = Math.max(maxCompletedHole, entry.getKey() + 1);
                }
            }
        }
        return Math.max(1, Math.min(totalHoles, maxCompletedHole));
    }

    private static List<RoundRunningScoresSync.PlayerRow> buildRunningScoreRows(
            MinecraftServer server,
            Set<UUID> participantIds,
            int focusHole,
            Map<UUID, PlayerRoundState> snapshot,
            int totalHoles
    ) {
        List<UUID> ranked = new ArrayList<>(participantIds);
        ranked.sort((a, b) -> {
            int aTotal = runningTotalThroughHole(a, focusHole);
            int bTotal = runningTotalThroughHole(b, focusHole);
            int totalCompare = Integer.compare(aTotal, bTotal);
            if (totalCompare != 0) {
                return totalCompare;
            }

            for (int priorHole = focusHole - 1; priorHole >= 1; priorHole--) {
                int aScore = scoreForHole(a, priorHole);
                int bScore = scoreForHole(b, priorHole);
                if (aScore != bScore) {
                    return Integer.compare(aScore, bScore);
                }
            }

            int aRank = HOLE_ONE_RANDOM_ORDER.getOrDefault(a, Integer.MAX_VALUE);
            int bRank = HOLE_ONE_RANDOM_ORDER.getOrDefault(b, Integer.MAX_VALUE);
            if (aRank != bRank) {
                return Integer.compare(aRank, bRank);
            }
            return a.compareTo(b);
        });

        List<RoundRunningScoresSync.PlayerRow> rows = new ArrayList<>();
        for (UUID playerId : ranked) {
            List<Integer> holeScores = new ArrayList<>();
            Map<Integer, Integer> scoreMap = HOLE_SCORE_HISTORY.get(playerId);
            for (int hole = 1; hole <= totalHoles; hole++) {
                int score = scoreMap == null ? -1 : scoreMap.getOrDefault(hole, -1);
                holeScores.add(score);
            }

            ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(playerId);
            String playerName;
            boolean online;
            
            // Check if this is a bot
            if (BotSimulator.isBot(playerId)) {
                playerName = BotSimulator.getBotProfile(playerId)
                        .map(BotSimulator.BotProfile::name)
                        .orElse(playerId.toString().substring(0, 8));
                online = false; // Bots are never "online" in the traditional sense
            } else {
                playerName = onlinePlayer != null
                        ? onlinePlayer.getGameProfile().getName()
                        : playerId.toString().substring(0, 8);
                online = onlinePlayer != null;
            }
            
            int runningTotal = runningTotalThroughHole(playerId, focusHole);
            rows.add(new RoundRunningScoresSync.PlayerRow(playerName, online, holeScores, runningTotal));
        }
        return rows;
    }

    private static int runningTotalThroughHole(UUID playerId, int focusHole) {
        int total = 0;
        Map<Integer, Integer> scores = HOLE_SCORE_HISTORY.get(playerId);
        if (scores == null) {
            return 0;
        }

        for (int hole = 1; hole <= focusHole; hole++) {
            int score = scores.getOrDefault(hole, -1);
            if (score >= 0) {
                total += score;
            }
        }
        return total;
    }

    /**
     * Record hole score for a bot (called from BotSimulator).
     * Bots don't have client-side scorecards, so we record directly in the history map.
     */
    public static void recordHoleScoreForBot(UUID botUuid, int holeIndex, int score) {
        Map<Integer, Integer> scores = HOLE_SCORE_HISTORY.computeIfAbsent(botUuid, k -> new HashMap<>());
        scores.put(holeIndex, score);
    }

    /**
     * Check if a bot has already recorded a score for a specific hole.
     */
    public static boolean hasHoleScoreForBot(UUID botUuid, int holeIndex) {
        Map<Integer, Integer> scores = HOLE_SCORE_HISTORY.get(botUuid);
        if (scores == null) {
            return false;
        }
        return scores.containsKey(holeIndex);
    }

    private static void sendRunningScoreboardInactive(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, RoundRunningScoresSync.Payload.inactive());
        }
    }




    static int scoreForHole(UUID playerId, int hole) {
        Map<Integer, Integer> scoreByHole = HOLE_SCORE_HISTORY.get(playerId);
        if (scoreByHole == null) {
            return Integer.MAX_VALUE;
        }
        return scoreByHole.getOrDefault(hole, Integer.MAX_VALUE);
    }

    private static void recordHoleScore(UUID playerId, int holeIndex, int score) {
        HOLE_SCORE_HISTORY
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(holeIndex, score);
    }

    private static boolean hasHoleScore(UUID playerId, int holeIndex) {
        Map<Integer, Integer> scoreByHole = HOLE_SCORE_HISTORY.get(playerId);
        if (scoreByHole == null) {
            return false;
        }
        return scoreByHole.containsKey(holeIndex);
    }

    private static void ensureHoleOneRandomOrder(Map<UUID, PlayerRoundState> snapshot) {
        if (!HOLE_ONE_RANDOM_ORDER.isEmpty() || snapshot.isEmpty()) {
            return;
        }

        List<UUID> playerIds = new ArrayList<>(snapshot.keySet());
        Collections.shuffle(playerIds, new Random(System.nanoTime()));
        for (int i = 0; i < playerIds.size(); i++) {
            HOLE_ONE_RANDOM_ORDER.put(playerIds.get(i), i);
        }
    }

    static final class ThrowTurnGate {
        private final boolean allowed;
        private final String message;
        private final boolean persistent;

        private ThrowTurnGate(boolean allowed, String message, boolean persistent) {
            this.allowed = allowed;
            this.message = message;
            this.persistent = persistent;
        }

        static ThrowTurnGate allowed() {
            return new ThrowTurnGate(true, "", false);
        }

        static ThrowTurnGate blocked(String message) {
            return new ThrowTurnGate(false, message, false);
        }

        static ThrowTurnGate blocked(String message, boolean persistent) {
            return new ThrowTurnGate(false, message, persistent);
        }

        boolean isAllowed() {
            return allowed;
        }

        String getMessage() {
            return message;
        }

        boolean isPersistent() {
            return persistent;
        }

        String message() {
            return message;
        }
    }

    private static int computeHolePaceDelta(int holePar, int holeDistanceFeet, int distanceToBasketBlocks, int holeStrokes) {
        int basketDistanceFeet = Math.max(0, Math.round(distanceToBasketBlocks * 3.28084f));
        int normalizedHoleDistance = Math.max(1, holeDistanceFeet);
        float progress = 1.0f - (basketDistanceFeet / (float) normalizedHoleDistance);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int expectedThrowsAtProgress = Math.round(progress * holePar);
        return holeStrokes - expectedThrowsAtProgress;
    }

    private static boolean isAtBasket(BlockPos playerPos, BlockPos basketPos) {
        int dx = Math.abs(playerPos.getX() - basketPos.getX());
        int dz = Math.abs(playerPos.getZ() - basketPos.getZ());
        int dy = Math.abs(playerPos.getY() - basketPos.getY());
        return dx <= BASKET_RADIUS_BLOCKS && dz <= BASKET_RADIUS_BLOCKS && dy <= BASKET_HEIGHT_TOLERANCE;
    }

    /**
     * Resets all static state. Call this when cleaning up a course to ensure
     * no stale minimap packets or round state leak into the next session.
     */
    public static void resetAllState(MinecraftServer server) {
        ROUND_WAS_ACTIVE = false;
        ThrowResolver.reset();
        DiscFlightSimulator.reset();
        HOLE_ONE_RANDOM_ORDER.clear();
        TurnManager.reset();
        LAST_LIE_POSITION.clear();
        LAST_BREADCRUMB_POSITION.clear();
        CACHED_CORRIDOR_HALF_WIDTH.clear();
        HoleMapSyncService.reset();
        LieMarkerService.reset();
        HoleMapSyncService.sendInactive(server);
        if (LAST_RUNNING_SCOREBOARD_HASH != Integer.MIN_VALUE) {
            sendRunningScoreboardInactive(server);
        }
        LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
        LieMarkerService.clearAllLieMarkers(server);
    }
    private static void removeTemporaryRoundItems(ServerPlayerEntity player) {
        RoundInventoryCleaner.purgeTemporaryRoundItemsAndJunk(player);
    }











    private static boolean hasPlayerMovedFromThrowLie(BlockPos playerFeet, BlockPos throwLie) {
        int dx = Math.abs(playerFeet.getX() - throwLie.getX());
        int dz = Math.abs(playerFeet.getZ() - throwLie.getZ());
        if (dx > 0 || dz > 0) {
            return true;
        }

        int dy = Math.abs(playerFeet.getY() - throwLie.getY());
        return dy > 1;
    }

    private static void broadcastHoleCompletion(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            ServerPlayerEntity player,
            int hole,
            int holeStrokes,
            int totalStrokes,
            int totalHoles
    ) {
        boolean isLastHole = hole >= totalHoles;
        String name = player.getGameProfile().getName();
        Text message;
        if (isLastHole) {
            message = Text.literal(name + " finished the round! Total: " + totalStrokes + " strokes")
                    .formatted(Formatting.GOLD);
        } else {
            message = Text.literal(name + " finished hole " + hole + " in " + holeStrokes + " strokes")
                    .formatted(Formatting.GREEN);
        }
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer.getWorld().getRegistryKey() == worldKey) {
                viewer.sendMessage(message, false);
            }
        }
    }

}


