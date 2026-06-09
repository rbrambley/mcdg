package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.net.AceCinematicSync;
import com.mcdg.net.HoleMiniMapSync;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

public final class HoleProgressTracker {
    private static final int BASKET_RADIUS_BLOCKS = 2;
    private static final int BASKET_HEIGHT_TOLERANCE = 4;
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 7;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;
    // Proximity make radius: flat putts within this distance that hit the basket column count as makes
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();
    private static final Map<UUID, Map<BlockPos, LieMarkerState>> LIE_MARKER_HISTORY = new HashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> HOLE_SCORE_HISTORY = new HashMap<>();
    static final Map<UUID, Integer> HOLE_ONE_RANDOM_ORDER = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_LIE_POSITION = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_BREADCRUMB_POSITION = new HashMap<>();
    private static final Map<UUID, Integer> CACHED_CORRIDOR_HALF_WIDTH = new HashMap<>();
    private static int LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
    private static int AUTOTEST_MARKER_TRAIL_REFCOUNT = 0;
    private static boolean ROUND_WAS_ACTIVE = false;

    private HoleProgressTracker() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            LeaderboardManager leaderboardManager,
            boolean hudScoringDebug,
            boolean strictFlowDebug
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MiniMapSyncService.tickPendingInactive(server);
            if (!courseManager.isRoundActive()) {
                if (ROUND_WAS_ACTIVE) {
                    ROUND_WAS_ACTIVE = false;
        ThrowResolver.reset();
                    HOLE_ONE_RANDOM_ORDER.clear();
        TurnManager.reset();
                    LAST_LIE_POSITION.clear();
                    LAST_BREADCRUMB_POSITION.clear();
                    CACHED_CORRIDOR_HALF_WIDTH.clear();
                    MiniMapSyncService.reset();
                    if (LAST_RUNNING_SCOREBOARD_HASH != Integer.MIN_VALUE) {
                        sendRunningScoreboardInactive(server);
                    }
                    MiniMapSyncService.sendInactive(server);
                    LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
                    clearAllLieMarkers(server);
                    HoleTeeMapManager.clearAllRoundHoleMaps(server);
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

                boolean suppressHud = player.isUsingItem() && player.getActiveItem().isOf(McdgItems.TRAINING_DISC);

                PlayerRoundState state = entry.getValue();
                Hole currentHole = course.holes().get(state.currentHole() - 1);
                BlockPos basket = placed.holeBaskets().get(state.currentHole());
                BlockPos tee = placed.holeTees().get(state.currentHole());
                BlockPos alternateAnchor = placed.holeAlternateAnchors().get(state.currentHole());
                if (basket == null) {
                    continue;
                }

                if (tee != null) {
                    HoleTeeMapManager.ensureHoleMapForPlayer(player, state.currentHole(), tee, basket);
                }

                BlockPos currentLie = state.lie();
                BlockPos lastLie = LAST_LIE_POSITION.get(player.getUuid());
                if (lastLie == null || !currentLie.equals(lastLie)) {
                    updateLieMarker(player, currentLie);
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
                            hudScoringDebug,
                            strictFlowDebug
                    );
                }

                int lieDistMeters = distanceMeters(state.lie(), basket);
                int lieDistFeet = distanceFeet(state.lie(), basket);
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
                Integer cachedHole = MiniMapSyncService.lastHoleForPlayer(playerId);
                int corridorHalfWidth;
                if (cachedHole == null || cachedHole != state.currentHole() || !CACHED_CORRIDOR_HALF_WIDTH.containsKey(playerId)) {
                    corridorHalfWidth = strictCorridorHalfWidth(
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

                MiniMapSyncService.sync(
                        server,
                        player,
                        courseManager,
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
                        strictFlowDebug
                );
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
                            spawnBreadcrumbLine(serverWorld, player, basket);
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

                ScorecardManager.recordHoleScore(player, state.currentHole(), state.holeStrokes());
                recordHoleScore(player.getUuid(), state.currentHole(), state.holeStrokes());

                if (state.currentHole() >= course.holes().size()) {
                    int totalPar = totalCoursePar(course);
                    BlockPos firstTee = placed.holeTees().get(1);
                    if (firstTee != null) {
                        player.teleport(firstTee.getX() + 0.5, firstTee.getY() + 1.0, firstTee.getZ() + 0.5);
                    }

                    removeRoundThrowItems(player);

                    roundStateManager.recordCompletedRound(player.getUuid(), state.totalStrokes());
                    if (leaderboardManager != null) {
                        leaderboardManager.recordScore(server, course.name(), player.getGameProfile().getName(), state.totalStrokes());
                    }

                    int finalCompletedPar = cumulativeParThroughHole(course, state.currentHole() - 1);
                    int finalRunningExpected = finalCompletedPar + state.holeStrokes();
                    int finalCumulativeDelta = state.totalStrokes() - finalRunningExpected;
                    int finalCorridorHalfWidth = CACHED_CORRIDOR_HALF_WIDTH.getOrDefault(player.getUuid(), 24);
                    MiniMapSyncService.forceSync(
                            server,
                            player,
                            courseManager,
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
                            strictFlowDebug
                    );
                    MiniMapSyncService.scheduleInactiveForPlayer(
                            player.getUuid(),
                            server.getOverworld().getTime() + MiniMapSyncService.hudLingerTicks()
                    );

                    roundStateManager.clearPlayer(player.getUuid());

                    if (firstTee != null) {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                        player.sendMessage(Text.literal("Returned to Hole 1 tee."), false);
                    } else {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                    }

                    broadcastRoundLeaderboard(server, placed.worldKey(), roundStateManager, totalPar);

                    boolean roundEnded = roundStateManager.snapshotStates().isEmpty();
                    if (roundEnded) {
                        sendRoundCompleteCinematic(server, placed.worldKey(), roundStateManager, totalPar);
                        courseManager.setRoundActive(false);
                        ServerWorld courseWorld = server.getWorld(placed.worldKey());
                        if (courseWorld != null) {
                            CourseFireProtection.remove(courseWorld);
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
                BlockPos safeNextTee = resolveSafeFeetNear(player.getServerWorld(), nextTee);
                roundStateManager.advanceToNextHole(player.getUuid(), safeNextTee);
                player.teleport(safeNextTee.getX() + 0.5, safeNextTee.getY() + 1.0, safeNextTee.getZ() + 0.5);
                updateLieMarker(player, safeNextTee);
                if (state.holeStrokes() == 1) {
                    ServerPlayNetworking.send(player, AceCinematicSync.Payload.active(state.currentHole(), currentHole.distanceFeet()));
                }
                sendHoleFinishTitle(player, state.holeStrokes(), completedHolePar);
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

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            return ThrowTurnGate.allowed();
        }

        PlayerRoundState playerState = roundStateManager.getState(player.getUuid()).orElse(null);
        if (playerState == null) {
            return ThrowTurnGate.blocked("You are not enrolled in the active round.");
        }

        Map<UUID, PlayerRoundState> snapshot = roundStateManager.snapshotStates();
        ensureHoleOneRandomOrder(snapshot);
        UUID expectedPlayer = TurnManager.determineExpectedTurnPlayer(
                player.getServer(),
                roundStateManager,
                courseManager,
                snapshot,
                playerState.currentHole(),
                placed,
                null
        );
        if (expectedPlayer == null || expectedPlayer.equals(player.getUuid())) {
            return ThrowTurnGate.allowed();
        }

        ServerPlayerEntity expected = player.getServer().getPlayerManager().getPlayer(expectedPlayer);
        if (expected != null) {
            return ThrowTurnGate.blocked("Wait your turn. " + expected.getGameProfile().getName() + " throws first.");
        }

        return ThrowTurnGate.blocked("Wait your turn. Another player throws first.");
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
        BlockPos safeLie = findNearestStandableFeet(world, state.lie());
        if (!isStandableFeetBlock(world, safeLie)) {
            return Optional.empty();
        }

        roundStateManager.updateLie(player.getUuid(), safeLie);
        updateLieMarker(player, safeLie);
        player.teleport(safeLie.getX() + 0.5, safeLie.getY() + 1.0, safeLie.getZ() + 0.5);
        ThrowResolver.recordResolutionReason(player.getUuid(), "GOTOLIE");
        return Optional.of(safeLie);
    }

    public static void sendRunningScoreboardToPlayer(
            ServerPlayerEntity player,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (player == null || !courseManager.isRoundActive()) {
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
            String playerName = onlinePlayer != null
                    ? onlinePlayer.getGameProfile().getName()
                    : playerId.toString().substring(0, 8);
            boolean online = onlinePlayer != null;
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

        private ThrowTurnGate(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }

        static ThrowTurnGate allowed() {
            return new ThrowTurnGate(true, "");
        }

        static ThrowTurnGate blocked(String message) {
            return new ThrowTurnGate(false, message);
        }

        boolean isAllowed() {
            return allowed;
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
     * Detects makes from short flat putts that don't arc through the hopper.
     * When the player is very close to the basket and lands on the basket column, count it as a make.
     */

    static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) + Math.abs(from.getZ() - to.getZ());
    }

    static int distanceMeters(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        return Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }

    static int distanceFeet(BlockPos from, BlockPos to) {
        return Math.max(0, Math.round(distanceMeters(from, to) * 3.28084f));
    }

    private static void clearAllLieMarkers(MinecraftServer server) {
        for (Map<BlockPos, LieMarkerState> markerStates : LIE_MARKER_HISTORY.values()) {
            for (LieMarkerState markerState : markerStates.values()) {
                ServerWorld world = server.getWorld(markerState.worldKey());
                if (world == null) {
                    continue;
                }
                world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), Block.NOTIFY_ALL);
            }
        }
        LIE_MARKER_HISTORY.clear();
    }

    public static void beginAutotestLieMarkerTrail() {
        AUTOTEST_MARKER_TRAIL_REFCOUNT++;
    }

    public static void endAutotestLieMarkerTrail(MinecraftServer server) {
        AUTOTEST_MARKER_TRAIL_REFCOUNT = Math.max(0, AUTOTEST_MARKER_TRAIL_REFCOUNT - 1);
        if (AUTOTEST_MARKER_TRAIL_REFCOUNT == 0) {
            clearAllLieMarkers(server);
        }
    }

    /**
     * Resets all static state. Call this when cleaning up a course to ensure
     * no stale minimap packets or round state leak into the next session.
     */
    public static void resetAllState(MinecraftServer server) {
        ROUND_WAS_ACTIVE = false;
        ThrowResolver.reset();
        HOLE_ONE_RANDOM_ORDER.clear();
        TurnManager.reset();
        LAST_LIE_POSITION.clear();
        LAST_BREADCRUMB_POSITION.clear();
        CACHED_CORRIDOR_HALF_WIDTH.clear();
        MiniMapSyncService.reset();
        AUTOTEST_MARKER_TRAIL_REFCOUNT = 0;
        MiniMapSyncService.sendInactive(server);
        if (LAST_RUNNING_SCOREBOARD_HASH != Integer.MIN_VALUE) {
            sendRunningScoreboardInactive(server);
        }
        LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
        clearAllLieMarkers(server);
        HoleTeeMapManager.clearAllRoundHoleMaps(server);
    }

    static void updateLieMarker(ServerPlayerEntity player, BlockPos lieFeet) {
        ServerWorld world = player.getServerWorld();
        BlockPos markerPos = lieFeet.down();
        UUID playerId = player.getUuid();
        boolean keepTrail = AUTOTEST_MARKER_TRAIL_REFCOUNT > 0;

        Map<BlockPos, LieMarkerState> history = LIE_MARKER_HISTORY.computeIfAbsent(playerId, ignored -> new HashMap<>());
        BlockPos markerKey = markerPos.toImmutable();

        if (!keepTrail && !history.isEmpty()) {
            clearPlayerLieMarkers(player.getServer(), playerId);
            history = LIE_MARKER_HISTORY.computeIfAbsent(playerId, ignored -> new HashMap<>());
        }

        if (history.containsKey(markerKey)) {
            if (!world.getBlockState(markerKey).isOf(Blocks.LIME_WOOL)) {
                world.setBlockState(markerKey, Blocks.LIME_WOOL.getDefaultState(), 3);
            }
            return;
        }

        BlockState original = world.getBlockState(markerPos);
        world.setBlockState(markerPos, Blocks.LIME_WOOL.getDefaultState(), 3);
        history.put(markerKey, new LieMarkerState(world.getRegistryKey(), markerKey, original));
    }

    static void clearPlayerLieMarkers(MinecraftServer server, UUID playerId) {
        Map<BlockPos, LieMarkerState> markerStates = LIE_MARKER_HISTORY.get(playerId);
        if (markerStates == null || markerStates.isEmpty()) {
            return;
        }

        for (LieMarkerState markerState : markerStates.values()) {
            ServerWorld world = server.getWorld(markerState.worldKey());
            if (world == null) {
                continue;
            }
            world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), Block.NOTIFY_ALL);
        }

        markerStates.clear();
    }

    static void spawnBreadcrumbLine(ServerWorld world, ServerPlayerEntity player, BlockPos to) {
        double sx = player.getX();
        double sy = player.getY() + 6.5;
        double sz = player.getZ();
        double tx = to.getX() + 0.5;
        double ty = to.getY() + 6.5;
        double tz = to.getZ() + 0.5;

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(32, Math.max(8, (int) (distance / 3.0)));

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double px = sx + (dx * t);
            double py = sy + (dy * t);
            double pz = sz + (dz * t);
            world.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    static void sendClankTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 25, 8));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("CLANK!").formatted(net.minecraft.util.Formatting.GRAY, net.minecraft.util.Formatting.ITALIC)));
    }

    static void sendStrictPenaltyTitle(ServerPlayerEntity player, StrictPenaltyType landingPenalty, int penaltyStrokes) {
        String titleText = landingPenalty == StrictPenaltyType.OB ? "OB +" + penaltyStrokes : "Hazard +" + penaltyStrokes;
        String subtitleText = landingPenalty == StrictPenaltyType.OB ? "Returned to lie" : "Penalty applied";

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(net.minecraft.util.Formatting.WHITE)));
    }

    static void sendHoleFinishTitle(ServerPlayerEntity player, int holeScore, int holePar) {
        int holeDelta = holeScore - holePar;
        String resultName = golfResultName(holeScore, holeDelta);
        String deltaText = holeDelta == 0 ? "E" : (holeDelta > 0 ? "+" + holeDelta : Integer.toString(holeDelta));
        net.minecraft.util.Formatting resultColor = holeDelta <= 0
                ? net.minecraft.util.Formatting.GREEN
                : net.minecraft.util.Formatting.RED;

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(resultName).formatted(resultColor, net.minecraft.util.Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(deltaText).formatted(resultColor, net.minecraft.util.Formatting.BOLD)));
    }

    static String golfResultName(int holeScore, int holeDelta) {
        if (holeScore == 1) {
            return "Ace";
        }
        if (holeDelta == -3) {
            return "Albatross";
        }
        if (holeDelta <= -4) {
            return "Three or Better";
        }
        if (holeDelta == -2) {
            return "Eagle";
        }
        if (holeDelta == -1) {
            return "Birdie";
        }
        if (holeDelta == 0) {
            return "Par";
        }
        if (holeDelta == 1) {
            return "Bogey";
        }
        if (holeDelta == 2) {
            return "Double Bogey";
        }
        if (holeDelta == 3) {
            return "Triple Bogey";
        }
        return "+" + holeDelta + " Bogey";
    }

    static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
        if (isStandableFeet(world, preferredFeet)) {
            return preferredFeet;
        }

        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = preferredFeet.up(dy);
            if (isStandableFeet(world, up)) {
                return up;
            }
            BlockPos down = preferredFeet.down(dy);
            if (isStandableFeet(world, down)) {
                return down;
            }
        }

        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = preferredFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidate.up(dy);
                        if (isStandableFeet(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidate.down(dy);
                        if (isStandableFeet(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        return preferredFeet;
    }

    static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        if (!world.getFluidState(feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }

        BlockPos head = feet.up();
        if (!world.getFluidState(head).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }

        BlockPos ground = feet.down();
        if (!world.getFluidState(ground).isEmpty()) {
            return false;
        }

        return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
    }

    private static void removeRoundThrowItems(ServerPlayerEntity player) {
        RoundInventoryCleaner.purgeRoundItemsAndJunk(player);
    }








    static StrictPenaltyType classifyOutType(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        int corridorHalfWidth = strictCorridorHalfWidth(currentHole, world, tee, basket, rulesetManager);
        return classifyOutTypeWithCorridor(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth);
    }

    static StrictPenaltyType classifyOutTypeWithCorridor(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth
    ) {
        // Basket green is a fully safe zone — no penalties for any landing within it.
        if (isBasketGreenSafe(feet, basket.down())) {
            return StrictPenaltyType.NONE;
        }

        // Basket column (hopper, pole, lantern) is always safe.
        if (feet.getX() == basket.getX() && feet.getZ() == basket.getZ()) {
            return StrictPenaltyType.NONE;
        }

        if (isFluidPenaltyZone(world, feet)) {
            return StrictPenaltyType.OB;
        }

        double lateral = distanceFromPlayableRouteXZ(feet, tee, basket, alternateAnchor);
        if (lateral > corridorHalfWidth) {
            return StrictPenaltyType.OB;
        }

        if (rulesetManager.strictEnableSlopeHazard() && isSteepSlopeHazard(world, feet, rulesetManager.strictSlopeHazardDeltaY())) {
            return StrictPenaltyType.HAZARD;
        }

        if (rulesetManager.strictEnableRoughHazard() && isDenseRoughHazard(world, feet, rulesetManager.strictRoughHazardLeafLogThreshold())) {
            return StrictPenaltyType.HAZARD;
        }

        return StrictPenaltyType.NONE;
    }

    static boolean isBasketGreenSafe(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }

    static boolean isFluidPenaltyZone(ServerWorld world, BlockPos feet) {
        return world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA);
    }

    static int strictCorridorHalfWidth(Hole hole, ServerWorld world, BlockPos tee, BlockPos basket, TournamentRulesetManager rulesetManager) {
        int maxSegmentWidth = 0;
        for (var segment : hole.fairwaySegments()) {
            maxSegmentWidth = Math.max(maxSegmentWidth, segment.width());
        }
        int baseHalf = Math.max(3, maxSegmentWidth / 2);
        int baseline = Math.max(rulesetManager.strictCorridorMinimumHalfWidthBlocks(), baseHalf + rulesetManager.strictCorridorBasePaddingBlocks());

        int directCarryGap = computeLongestWaterCarryGap(world, tee, basket);
        if (directCarryGap > rulesetManager.strictAltRouteCarryTriggerBlocks()) {
            return Math.max(baseline, rulesetManager.strictAltRouteHalfWidthBlocks());
        }

        return baseline;
    }

/**
 * Finds the longest continuous water gap along the line from start to end.
 * Returns int[3] = { startDistanceBlocks, endDistanceBlocks, maxGapLengthBlocks }
 * where distances are measured from the start position.
 */
static int[] findLongestWaterGap(ServerWorld world, BlockPos start, BlockPos end) {
    int dx = end.getX() - start.getX();
    int dz = end.getZ() - start.getZ();
    int dominant = Math.max(Math.abs(dx), Math.abs(dz));
    if (dominant == 0) {
        return new int[]{0, 0, 0};
    }

    int stepX = Integer.signum(dx);
    int stepZ = Integer.signum(dz);

    int longest = 0;
    int current = 0;
    int currentStart = 0;
    int bestStart = 0;
    int bestEnd = 0;

    int x = start.getX();
    int z = start.getZ();
    int errX = 0;
    int errZ = 0;

    for (int i = 0; i <= dominant; i++) {
        if (isWaterCarryColumn(world, x, z)) {
            if (current == 0) {
                currentStart = i;
            }
            current++;
            if (current > longest) {
                longest = current;
                bestStart = currentStart;
                bestEnd = i;
            }
        } else if (current > 0) {
            current = 0;
        }

        errX += Math.abs(dx);
        if (errX >= dominant && stepX != 0) {
            x += stepX;
            errX -= dominant;
        }
        errZ += Math.abs(dz);
        if (errZ >= dominant && stepZ != 0) {
            z += stepZ;
            errZ -= dominant;
        }
    }

    return new int[]{bestStart, bestEnd, longest};
}

/** Legacy wrapper that returns just the max gap length for corridor width decisions. */
private static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
    return findLongestWaterGap(world, start, end)[2];
}

    /**
     * Returns a standable position in the 2-block ring around the basket (as if the disc bounced off it).
     * Tries all 8 adjacent + diagonal offsets at radius 1–2; picks the first solid standable block.
     * Falls back to one block north if no standable block is found.
     */

    static boolean isWaterCarryColumn(ServerWorld world, int x, int z) {
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY < world.getBottomY()) {
            return false;
        }

        BlockPos surface = new BlockPos(x, surfaceY, z);
        if (!world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return false;
        }

        return !hasAnySafeLandingNearby(world, surface);
    }

    static boolean hasAnySafeLandingNearby(ServerWorld world, BlockPos center) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if ((dx * dx) + (dz * dz) > 36) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos feet = new BlockPos(x, y + 1, z);
                if (isStandableFeetBlock(world, feet)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double nx = sx + (t * dx);
        double nz = sz + (t * dz);
        double mx = px - nx;
        double mz = pz - nz;
        return Math.sqrt(mx * mx + mz * mz);
    }

    private static double distanceFromPlayableRouteXZ(BlockPos point, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        if (alternateAnchor == null) {
            return distanceFromPointToSegmentXZ(point, tee, basket);
        }

        double firstLeg = distanceFromPointToSegmentXZ(point, tee, alternateAnchor);
        double secondLeg = distanceFromPointToSegmentXZ(point, alternateAnchor, basket);
        return Math.min(firstLeg, secondLeg);
    }


    /**
     * Finds the nearest point on the forward corridor boundary from the given lie.
     * "Forward" means the half of the corridor between the lie's projection along the
     * tee->basket axis and the basket (so we never point the player backward).
     *
     * Returns int[2] = { distanceFeet, bearingDegrees } where bearingDegrees is a
     * geographic bearing (0=N, 90=E, 180=S, 270=W), or null if the lie is already
     * inside the corridor (lateral distance <= corridorHalfWidth).
     */
    static int[] nearestForwardCorridorEntry(BlockPos lie, BlockPos tee, BlockPos basket, BlockPos alternateAnchor, int corridorHalfWidth) {
        // Choose the relevant segment: if there is an alternate anchor, use the leg
        // whose projected t-value places the lie further forward (closer to the basket).
        BlockPos segStart = tee;
        BlockPos segEnd = basket;
        if (alternateAnchor != null) {
            double t1 = projectionT(lie, tee, alternateAnchor);
            double t2 = projectionT(lie, alternateAnchor, basket);
            // Use the second leg if the lie is past the alternate anchor
            if (t1 >= 1.0 || t2 > 0.0) {
                segStart = alternateAnchor;
                segEnd = basket;
            }
        }

        double px = lie.getX() + 0.5;
        double pz = lie.getZ() + 0.5;
        double sx = segStart.getX() + 0.5;
        double sz = segStart.getZ() + 0.5;
        double ex = segEnd.getX() + 0.5;
        double ez = segEnd.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;

        double nearX, nearZ;
        if (lengthSquared < 1.0e-6) {
            // Degenerate segment - just point at the basket
            nearX = ex;
            nearZ = ez;
        } else {
            // Project lie onto the segment axis; clamp to [t_lie, 1.0] (forward half only)
            double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
            double tLie = Math.max(0.0, Math.min(1.0, t));
            // Forward half: from the lie's projection to the basket end
            double tClamped = Math.max(tLie, Math.min(1.0, t));

            // Nearest point on the axis
            double axisX = sx + tClamped * dx;
            double axisZ = sz + tClamped * dz;

            // Perpendicular unit vector (rotate (dx,dz) 90 degrees)
            double len = Math.sqrt(lengthSquared);
            double perpX = -dz / len;
            double perpZ = dx / len;

            // The two corridor edge candidates at this projection point
            double edgeL_X = axisX + perpX * corridorHalfWidth;
            double edgeL_Z = axisZ + perpZ * corridorHalfWidth;
            double edgeR_X = axisX - perpX * corridorHalfWidth;
            double edgeR_Z = axisZ - perpZ * corridorHalfWidth;

            double distL = Math.hypot(px - edgeL_X, pz - edgeL_Z);
            double distR = Math.hypot(px - edgeR_X, pz - edgeR_Z);

            // Check if already inside the corridor
            double lateralDist = Math.abs((px - axisX) * perpX + (pz - axisZ) * perpZ);
            if (lateralDist <= corridorHalfWidth) {
                return null; // already in-bounds
            }

            if (distL <= distR) {
                nearX = edgeL_X;
                nearZ = edgeL_Z;
            } else {
                nearX = edgeR_X;
                nearZ = edgeR_Z;
            }
        }

        double distMeters = Math.hypot(nearX - px, nearZ - pz);
        int distFeet = Math.max(1, (int) Math.round(distMeters * 3.28084));

        // Geographic bearing: 0=N (+Z in Minecraft is S, -Z is N, +X is E, -X is W)
        // Minecraft: Z increases south, X increases east
        double bearingRad = Math.atan2(nearX - px, -(nearZ - pz)); // atan2(east, north)
        int bearingDeg = (int) Math.round(Math.toDegrees(bearingRad));
        bearingDeg = ((bearingDeg % 360) + 360) % 360;

        return new int[]{distFeet, bearingDeg};
    }

    /** Returns the unclamped t-value for projecting point onto the start->end segment. */
    private static double projectionT(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5, pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5, sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5,   ez = end.getZ() + 0.5;
        double dx = ex - sx, dz = ez - sz;
        double lsq = dx * dx + dz * dz;
        if (lsq < 1.0e-6) return 0.0;
        return ((px - sx) * dx + (pz - sz) * dz) / lsq;
    }

    static boolean isSteepSlopeHazard(ServerWorld world, BlockPos feet, int slopeDeltaThreshold) {
        int centerY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        int[] offsets = { -2, 0, 2 };
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz) - 1;
                if (Math.abs(sampleY - centerY) >= slopeDeltaThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isDenseRoughHazard(ServerWorld world, BlockPos feet, int threshold) {
        int roughHits = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surface = new BlockPos(x, topY, z);
                BlockState surfaceState = world.getBlockState(surface);
                BlockState headState = world.getBlockState(surface.up());
                if (isRoughMaterial(surfaceState) || isRoughMaterial(headState)) {
                    roughHits++;
                }
            }
        }
        return roughHits >= threshold;
    }

    private static boolean isRoughMaterial(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CACTUS);
    }

    static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "-";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static BlockPos findNearestStandableFeet(ServerWorld world, BlockPos baseFeet) {
        BlockPos candidate = baseFeet;
        if (isStandableFeetBlock(world, candidate)) {
            return candidate;
        }

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos up = baseFeet.up(offset);
            if (isStandableFeetBlock(world, up)) {
                return up;
            }
            BlockPos down = baseFeet.down(offset);
            if (isStandableFeetBlock(world, down)) {
                return down;
            }
        }

        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidateAtRadius = baseFeet.add(dx, 0, dz);
                    if (isStandableFeetBlock(world, candidateAtRadius)) {
                        return candidateAtRadius;
                    }

                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidateAtRadius.up(dy);
                        if (isStandableFeetBlock(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidateAtRadius.down(dy);
                        if (isStandableFeetBlock(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        int fallbackY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, baseFeet.getX(), baseFeet.getZ());
        BlockPos fallback = new BlockPos(baseFeet.getX(), fallbackY, baseFeet.getZ());
        if (isStandableFeetBlock(world, fallback)) {
            return fallback;
        }

        return baseFeet;
    }

    static boolean isStandableFeetBlock(ServerWorld world, BlockPos feet) {
        if (!world.getFluidState(feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.up();
        if (!world.getFluidState(head).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }

        BlockPos ground = feet.down();
        if (!world.getFluidState(ground).isEmpty()) {
            return false;
        }

        return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
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


        private record LieMarkerState(
            RegistryKey<net.minecraft.world.World> worldKey,
            BlockPos markerPos,
            BlockState previousGroundState
        ) {
        }

    private static void broadcastRoundLeaderboard(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            RoundStateManager roundStateManager,
            int totalPar
    ) {
        Map<UUID, Integer> completed = roundStateManager.snapshotCompletedRounds();
        if (completed.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(completed.entrySet());
        ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == worldKey)
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        Text header = HUD_STATE_FORMATTER.formatRoundSummaryHeader(totalPar, ranked.size());
        for (ServerPlayerEntity viewer : viewers) {
            viewer.sendMessage(header, false);
        }

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            ServerPlayerEntity rankedPlayer = server.getPlayerManager().getPlayer(entry.getKey());
            String name = rankedPlayer != null ? rankedPlayer.getGameProfile().getName() : entry.getKey().toString().substring(0, 8);
            Text line = HUD_STATE_FORMATTER.formatRoundSummaryEntry(rank, name, entry.getValue(), totalPar);
            for (ServerPlayerEntity viewer : viewers) {
                viewer.sendMessage(line, false);
            }
            rank++;
        }
    }

    private static void sendRoundCompleteCinematic(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            RoundStateManager roundStateManager,
            int totalPar
    ) {
        Map<UUID, Integer> completed = roundStateManager.snapshotCompletedRounds();
        if (completed.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(completed.entrySet());
        ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

        String firstName = rankedName(server, ranked, 0);
        int firstScore = rankedScore(ranked, 0);
        String secondName = rankedName(server, ranked, 1);
        int secondScore = rankedScore(ranked, 1);
        String thirdName = rankedName(server, ranked, 2);
        int thirdScore = rankedScore(ranked, 2);

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == worldKey)
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity viewer : viewers) {
            int localRank = -1;
            int localScore = completed.getOrDefault(viewer.getUuid(), 0);
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).getKey().equals(viewer.getUuid())) {
                    localRank = i + 1;
                    break;
                }
            }

            ServerPlayNetworking.send(
                    viewer,
                    RoundCompleteCinematicSync.Payload.active(
                            totalPar,
                            ranked.size(),
                            firstName,
                            firstScore,
                            secondName,
                            secondScore,
                            thirdName,
                            thirdScore,
                            localRank,
                            localScore
                    )
            );
        }
    }

    private static String rankedName(MinecraftServer server, List<Map.Entry<UUID, Integer>> ranked, int index) {
        if (index < 0 || index >= ranked.size()) {
            return "-";
        }

        UUID playerId = ranked.get(index).getKey();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            return player.getGameProfile().getName();
        }

        return playerId.toString().substring(0, 8);
    }

    private static int rankedScore(List<Map.Entry<UUID, Integer>> ranked, int index) {
        if (index < 0 || index >= ranked.size()) {
            return 0;
        }
        return ranked.get(index).getValue();
    }
}
