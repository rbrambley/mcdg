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
    private static final int BASKET_RADIUS_BLOCKS = 1;
    private static final int BASKET_HEIGHT_TOLERANCE = 2;
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 7;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;
    private static final int MAX_THROW_RESOLUTION_WAIT_TICKS = 320;
    private static final int THROW_RELEASE_GRACE_TICKS = 8;
    private static final int TURN_TIMEOUT_TICKS = 20 * 120;
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final boolean ENABLE_STRICT_LANDING_PENALTIES = true;
    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();
    private static final Map<UUID, Integer> LAST_PROCESSED_THROW_TOTAL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_PENDING_TICKS = new HashMap<>();
    private static final Map<UUID, UUID> LAST_THROW_PEARL_UUID = new HashMap<>();
    private static final Map<UUID, Long> LAST_THROW_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, String> LAST_RESOLUTION_REASON = new HashMap<>();
    private static final Map<UUID, Map<BlockPos, LieMarkerState>> LIE_MARKER_HISTORY = new HashMap<>();
    private static final Map<UUID, Map<Integer, Integer>> HOLE_SCORE_HISTORY = new HashMap<>();
    private static final Map<UUID, Integer> HOLE_ONE_RANDOM_ORDER = new HashMap<>();
    private static final Map<Integer, UUID> ACTIVE_TURN_PLAYER_BY_HOLE = new HashMap<>();
    private static final Map<Integer, Long> ACTIVE_TURN_STARTED_AT_BY_HOLE = new HashMap<>();
    private static final Map<Integer, Integer> ACTIVE_TURN_TOTAL_STROKES_BY_HOLE = new HashMap<>();
    private static final Map<Integer, UUID> TURN_SKIP_ONCE_BY_HOLE = new HashMap<>();
    private static int LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
    private static boolean MINIMAP_ACTIVE_SENT = false;
    private static int AUTOTEST_MARKER_TRAIL_REFCOUNT = 0;

    private HoleProgressTracker() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean hudScoringDebug,
            boolean strictFlowDebug
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!courseManager.isRoundActive()) {
                LAST_PROCESSED_THROW_TOTAL.clear();
                LAST_THROW_PENDING_TICKS.clear();
                LAST_THROW_PEARL_UUID.clear();
                LAST_THROW_RELEASE_TICK.clear();
                LAST_RESOLUTION_REASON.clear();
                HOLE_SCORE_HISTORY.clear();
                HOLE_ONE_RANDOM_ORDER.clear();
                ACTIVE_TURN_PLAYER_BY_HOLE.clear();
                ACTIVE_TURN_STARTED_AT_BY_HOLE.clear();
                ACTIVE_TURN_TOTAL_STROKES_BY_HOLE.clear();
                TURN_SKIP_ONCE_BY_HOLE.clear();
                if (LAST_RUNNING_SCOREBOARD_HASH != Integer.MIN_VALUE) {
                    sendRunningScoreboardInactive(server);
                }
                if (MINIMAP_ACTIVE_SENT) {
                    sendMiniMapInactive(server);
                    MINIMAP_ACTIVE_SENT = false;
                }
                LAST_RUNNING_SCOREBOARD_HASH = Integer.MIN_VALUE;
                clearAllLieMarkers(server);
                HoleTeeMapManager.clearAllRoundHoleMaps(server);
                return;
            }

            Course course = courseManager.getActiveCourse().orElse(null);
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (course == null || placed == null) {
                return;
            }

            Map<UUID, PlayerRoundState> snapshot = roundStateManager.snapshotStates();
            ensureHoleOneRandomOrder(snapshot);
            enforceTurnTimeouts(server, courseManager, roundStateManager, course, placed, snapshot);
            maybeSendRunningScoreboard(server, courseManager, course, placed, roundStateManager, snapshot);
            for (Map.Entry<UUID, PlayerRoundState> entry : snapshot.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
                    continue;
                }

                // Keep random course-construction pickups out of player inventories during active play.
                RoundInventoryCleaner.purgeJunkItems(player);

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

                updateLieMarker(player, state.lie());

                if (tee != null) {
                    state = resolveThrowLanding(
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
                int corridorHalfWidth = strictCorridorHalfWidth(
                    currentHole,
                    (ServerWorld) player.getWorld(),
                    tee,
                    basket,
                    rulesetManager
                );

                BlockPos mapFocus = player.getBlockPos();
                HoleMiniMapSync.Payload miniMapPayload = buildMiniMapPayload(
                    courseManager,
                    course,
                    placed,
                        state.currentHole(),
                        currentHole.par(),
                        Math.max(1, state.holeStrokes() + 1),
                        state.totalStrokes(),
                        cumulativeParDelta,
                        tee == null ? state.lie() : tee,
                        basket,
                        state.lie(),
                        mapFocus,
                        rulesetManager.isStrict(),
                        rulesetManager.getStrictSurfacePreset().ordinal(),
                        corridorHalfWidth,
                        alternateAnchor
                );

                if (strictFlowDebug && (server.getTicks() % 20) == 0) {
                    HoleMiniMapSync.Payload payload = miniMapPayload;
                    BlockPos playerFeet = player.getBlockPos();
                    McdgMod.LOGGER.info(
                        "MINIMAP DEBUG | player={} hole={} feet=({}, {}) lie=({}, {}) tee=({}, {}) basket=({}, {}) span={} dFeetLie=({}, {})",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            playerFeet.getX(),
                            playerFeet.getZ(),
                            payload.lieX(),
                            payload.lieZ(),
                            payload.teeX(),
                            payload.teeZ(),
                            payload.basketX(),
                            payload.basketZ(),
                            payload.mapSpan(),
                            playerFeet.getX() - payload.lieX(),
                            playerFeet.getZ() - payload.lieZ()
                    );
                }

                ServerPlayNetworking.send(
                    player,
                    miniMapPayload
                );
                MINIMAP_ACTIVE_SENT = true;
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
                        spawnBreadcrumbLine(serverWorld, player, basket);
                    }
                }

                if ((server.getTicks() % 20) == 0) {
                    sendTurnActionBar(server, player, state.currentHole());
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
        UUID expectedPlayer = determineExpectedTurnPlayer(
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
        LAST_RESOLUTION_REASON.put(player.getUuid(), "GOTOLIE");
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
        ServerPlayNetworking.send(player, RoundRunningScoresSync.Payload.active(course.holes().size(), focusHole, rows));
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

    private static void sendTurnActionBar(MinecraftServer server, ServerPlayerEntity viewer, int hole) {
        UUID activeTurnPlayerId = ACTIVE_TURN_PLAYER_BY_HOLE.get(hole);
        if (activeTurnPlayerId == null) {
            return;
        }

        long startedAt = ACTIVE_TURN_STARTED_AT_BY_HOLE.getOrDefault(hole, (long) server.getTicks());
        long elapsedTicks = Math.max(0, server.getTicks() - startedAt);
        long remainingTicks = Math.max(0, TURN_TIMEOUT_TICKS - elapsedTicks);
        long remainingSeconds = (remainingTicks + 19) / 20;

        ServerPlayerEntity activeTurnPlayer = server.getPlayerManager().getPlayer(activeTurnPlayerId);
        String timer = formatTurnTimer(remainingSeconds);
        if (activeTurnPlayerId.equals(viewer.getUuid())) {
            viewer.sendMessage(Text.literal("Your turn | " + timer + " left"), true);
            return;
        }

        String throwerName = activeTurnPlayer == null
                ? "Player"
                : activeTurnPlayer.getGameProfile().getName();
        viewer.sendMessage(Text.literal("Turn: " + throwerName + " | " + timer + " left"), true);
    }

    private static String formatTurnTimer(long remainingSeconds) {
        long clamped = Math.max(0, remainingSeconds);
        long minutes = clamped / 60;
        long seconds = clamped % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static void enforceTurnTimeouts(
            MinecraftServer server,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            Course course,
            PlacedCourseState placed,
            Map<UUID, PlayerRoundState> snapshot
    ) {
        Map<Integer, UUID> updatedActiveByHole = new HashMap<>();
        Map<Integer, Long> updatedStartedAtByHole = new HashMap<>();
        Map<Integer, Integer> updatedTurnTotalByHole = new HashMap<>();

        for (Map.Entry<UUID, PlayerRoundState> entry : snapshot.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
                continue;
            }
            int hole = entry.getValue().currentHole();
            updatedActiveByHole.putIfAbsent(hole, null);
        }

        for (Integer hole : new ArrayList<>(updatedActiveByHole.keySet())) {
            UUID expected = determineExpectedTurnPlayer(server, roundStateManager, courseManager, snapshot, hole, placed, TURN_SKIP_ONCE_BY_HOLE.get(hole));
            if (expected == null) {
                continue;
            }

            UUID active = ACTIVE_TURN_PLAYER_BY_HOLE.get(hole);
            PlayerRoundState expectedState = snapshot.get(expected);
            if (expectedState == null) {
                continue;
            }

            int expectedTotal = expectedState.totalStrokes();
            long now = server.getTicks();
            long startedAt = ACTIVE_TURN_STARTED_AT_BY_HOLE.getOrDefault(hole, now);
            int trackedTotal = ACTIVE_TURN_TOTAL_STROKES_BY_HOLE.getOrDefault(hole, expectedTotal);

            if (!expected.equals(active)) {
                startedAt = now;
                trackedTotal = expectedTotal;
            } else if (expectedTotal != trackedTotal) {
                startedAt = now;
                trackedTotal = expectedTotal;
            }

            if ((now - startedAt) >= TURN_TIMEOUT_TICKS) {
                applyTurnTimeoutPenalty(server, roundStateManager, expected, expectedState, placed);
                TURN_SKIP_ONCE_BY_HOLE.put(hole, expected);

                Map<UUID, PlayerRoundState> refreshedSnapshot = roundStateManager.snapshotStates();
                UUID nextExpected = determineExpectedTurnPlayer(server, roundStateManager, courseManager, refreshedSnapshot, hole, placed, expected);
                if (nextExpected != null && !nextExpected.equals(expected)) {
                    active = nextExpected;
                    PlayerRoundState nextState = refreshedSnapshot.get(nextExpected);
                    trackedTotal = nextState == null ? 0 : nextState.totalStrokes();
                    startedAt = now;
                    TURN_SKIP_ONCE_BY_HOLE.remove(hole);
                } else {
                    active = expected;
                    PlayerRoundState refreshedExpected = refreshedSnapshot.get(expected);
                    trackedTotal = refreshedExpected == null ? trackedTotal : refreshedExpected.totalStrokes();
                    startedAt = now;
                }
            } else {
                active = expected;
            }

            if (active != null) {
                updatedActiveByHole.put(hole, active);
                updatedStartedAtByHole.put(hole, startedAt);
                updatedTurnTotalByHole.put(hole, trackedTotal);
            }
        }

        ACTIVE_TURN_PLAYER_BY_HOLE.clear();
        ACTIVE_TURN_PLAYER_BY_HOLE.putAll(updatedActiveByHole);
        ACTIVE_TURN_STARTED_AT_BY_HOLE.clear();
        ACTIVE_TURN_STARTED_AT_BY_HOLE.putAll(updatedStartedAtByHole);
        ACTIVE_TURN_TOTAL_STROKES_BY_HOLE.clear();
        ACTIVE_TURN_TOTAL_STROKES_BY_HOLE.putAll(updatedTurnTotalByHole);
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
                    RoundRunningScoresSync.Payload.active(course.holes().size(), focusHole, rows)
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

    private static void sendMiniMapInactive(MinecraftServer server) {
        HoleMiniMapSync.Payload inactive = HoleMiniMapSync.Payload.inactive();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, inactive);
        }
    }

    private static void applyTurnTimeoutPenalty(
            MinecraftServer server,
            RoundStateManager roundStateManager,
            UUID playerId,
            PlayerRoundState state,
            PlacedCourseState placed
    ) {
        roundStateManager.applyPenaltyStrokes(playerId, 1);

        BlockPos tee = placed.holeTees().get(state.currentHole());
        if (tee != null) {
            ServerWorld world = server.getWorld(placed.worldKey());
            if (world != null) {
                BlockPos safeTee = resolveSafeFeetNear(world, tee);
                roundStateManager.updateLie(playerId, safeTee);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && player.getWorld().getRegistryKey() == placed.worldKey()) {
                    player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                    player.sendMessage(Text.literal("Turn timeout: +1 stroke. Reset to tee, turn passed."), true);
                }
            }
        }
    }

    private static UUID determineExpectedTurnPlayer(
            MinecraftServer server,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            Map<UUID, PlayerRoundState> snapshot,
            int hole,
            PlacedCourseState placed,
            UUID skipCandidate
    ) {
        BlockPos basket = placed.holeBaskets().get(hole);
        if (basket == null) {
            return null;
        }

        List<UUID> eligible = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRoundState> entry : snapshot.entrySet()) {
            if (entry.getValue().currentHole() != hole) {
                continue;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
                continue;
            }
            if (!courseManager.getActiveParticipantIds().contains(entry.getKey())) {
                continue;
            }
            eligible.add(entry.getKey());
        }

        if (eligible.isEmpty()) {
            return null;
        }

        List<UUID> teePlayers = new ArrayList<>();
        for (UUID playerId : eligible) {
            PlayerRoundState state = snapshot.get(playerId);
            if (state != null && state.holeStrokes() == 0) {
                teePlayers.add(playerId);
            }
        }

        List<UUID> ordered = new ArrayList<>();
        if (!teePlayers.isEmpty()) {
            ordered.addAll(teePlayers);
            ordered.sort((a, b) -> compareTeeOrder(a, b, hole));
        } else {
            ordered.addAll(eligible);
            ordered.sort((a, b) -> {
                PlayerRoundState aState = snapshot.get(a);
                PlayerRoundState bState = snapshot.get(b);
                int aDistance = aState == null ? 0 : distanceMeters(aState.lie(), basket);
                int bDistance = bState == null ? 0 : distanceMeters(bState.lie(), basket);
                int distanceCompare = Integer.compare(bDistance, aDistance);
                if (distanceCompare != 0) {
                    return distanceCompare;
                }
                return compareTeeOrder(a, b, hole);
            });
        }

        if (skipCandidate != null && ordered.size() > 1 && skipCandidate.equals(ordered.get(0))) {
            return ordered.get(1);
        }
        return ordered.get(0);
    }

    private static int compareTeeOrder(UUID a, UUID b, int hole) {
        for (int priorHole = hole - 1; priorHole >= 1; priorHole--) {
            int aScore = scoreForHole(a, priorHole);
            int bScore = scoreForHole(b, priorHole);
            if (aScore != bScore) {
                return Integer.compare(aScore, bScore);
            }
        }

        int aHoleOneRank = HOLE_ONE_RANDOM_ORDER.getOrDefault(a, Integer.MAX_VALUE);
        int bHoleOneRank = HOLE_ONE_RANDOM_ORDER.getOrDefault(b, Integer.MAX_VALUE);
        if (aHoleOneRank != bHoleOneRank) {
            return Integer.compare(aHoleOneRank, bHoleOneRank);
        }
        return a.compareTo(b);
    }

    private static int scoreForHole(UUID playerId, int hole) {
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

    private static boolean shouldBounceOffBasketStructure(BlockPos liePos, BlockPos basketPos) {
        if (liePos == null || basketPos == null) {
            return false;
        }

        int dx = Math.abs(liePos.getX() - basketPos.getX());
        int dz = Math.abs(liePos.getZ() - basketPos.getZ());
        if (dx != 0 || dz != 0) {
            return false;
        }

        // Made basket remains hopper + one block above; upper basket structure should bounce.
        return liePos.getY() >= (basketPos.getY() + 2);
    }

    private static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) + Math.abs(from.getZ() - to.getZ());
    }

    private static int distanceMeters(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        return Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }

    private static int distanceFeet(BlockPos from, BlockPos to) {
        return Math.max(0, Math.round(distanceMeters(from, to) * 3.28084f));
    }

    private static HoleMiniMapSync.Payload buildMiniMapPayload(
            ActiveCourseManager courseManager,
            Course course,
            PlacedCourseState placed,
            int holeIndex,
            int par,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            BlockPos tee,
            BlockPos basket,
            BlockPos lie,
            BlockPos mapFocus,
            boolean strictMode,
                int strictSurfacePresetOrdinal,
                int corridorHalfWidth,
            BlockPos alternateAnchor
    ) {
        int span;
        int minX = Math.min(Math.min(tee.getX(), basket.getX()), mapFocus.getX());
        int maxX = Math.max(Math.max(tee.getX(), basket.getX()), mapFocus.getX());
        int minZ = Math.min(Math.min(tee.getZ(), basket.getZ()), mapFocus.getZ());
        int maxZ = Math.max(Math.max(tee.getZ(), basket.getZ()), mapFocus.getZ());
        if (alternateAnchor != null) {
            minX = Math.min(minX, alternateAnchor.getX());
            maxX = Math.max(maxX, alternateAnchor.getX());
            minZ = Math.min(minZ, alternateAnchor.getZ());
            maxZ = Math.max(maxZ, alternateAnchor.getZ());
        }
        int baseSpan = Math.max(Math.max(1, maxX - minX), Math.max(1, maxZ - minZ)) + 10;
        int maxLieDelta = maxLieDelta(mapFocus, tee, basket, alternateAnchor);
        int rawSpan = Math.max(baseSpan, (maxLieDelta * 2) + 24);
        span = Math.max(120, Math.round(rawSpan * HoleMiniMapSync.MAP_OVERSCAN_FACTOR));

        String courseWaypointName = resolveCourseWaypointName(courseManager, course);
        BlockPos courseAnchor = resolveTournamentCentralAnchor(
                placed.holeTees().get(1),
                placed.holeBaskets().get(1),
                tee,
                basket
        );
        int courseWaypointX = courseAnchor.getX();
        int courseWaypointZ = courseAnchor.getZ();

        List<Integer> holeTeeXs = new ArrayList<>();
        List<Integer> holeTeeZs = new ArrayList<>();
        int totalHoles = course.holes().size();
        for (int i = 1; i <= totalHoles; i++) {
            BlockPos holeTee = placed.holeTees().get(i);
            BlockPos holeBasket = placed.holeBaskets().get(i);
            BlockPos holeAnchor = resolveWaypointAnchor(holeTee, holeBasket, tee, basket);
            holeTeeXs.add(holeAnchor.getX());
            holeTeeZs.add(holeAnchor.getZ());
        }

        return HoleMiniMapSync.Payload.active(
                holeIndex,
                tee.getX(),
                tee.getZ(),
                basket.getX(),
                basket.getZ(),
                lie.getX(),
                lie.getZ(),
            par,
            throwNumber,
            totalStrokes,
            cumulativeParDelta,
                strictMode,
                strictSurfacePresetOrdinal,
                corridorHalfWidth,
                alternateAnchor != null,
                alternateAnchor == null ? 0 : alternateAnchor.getX(),
                alternateAnchor == null ? 0 : alternateAnchor.getZ(),
                span,
                courseWaypointName,
                courseWaypointX,
                courseWaypointZ,
                totalHoles,
                holeTeeXs,
                holeTeeZs
            );
    }

    private static String resolveCourseWaypointName(ActiveCourseManager courseManager, Course course) {
        return course.name() + " " + course.seed();
    }

    private static BlockPos resolveTournamentCentralAnchor(BlockPos preferredTee, BlockPos preferredBasket, BlockPos fallbackTee, BlockPos fallbackBasket) {
        BlockPos teeAnchor = preferredTee == null ? fallbackTee : preferredTee;
        if (teeAnchor == null) {
            return fallbackBasket == null ? BlockPos.ORIGIN : fallbackBasket;
        }

        BlockPos basketAnchor = preferredBasket == null ? fallbackBasket : preferredBasket;
        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        // Hub deck local bounds are u=-8..8, v=-3..8 around hubSurface; geometric center is at v=2.5.
        // Choose the nearest block center (v=3), so anchor is 12 blocks behind tee along back direction.
        return teeAnchor.add(back[0] * 12, 0, back[1] * 12);
    }

    private static BlockPos resolveWaypointAnchor(BlockPos preferredTee, BlockPos preferredBasket, BlockPos fallbackTee, BlockPos fallbackBasket) {
        BlockPos teeAnchor = preferredTee == null ? fallbackTee : preferredTee;
        if (teeAnchor == null) {
            return fallbackBasket == null ? BlockPos.ORIGIN : fallbackBasket;
        }

        BlockPos basketAnchor = preferredBasket == null ? fallbackBasket : preferredBasket;
        if (basketAnchor == null) {
            return teeAnchor;
        }

        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0], 0, back[1]);
    }

    private static int[] resolveBackCardinal(BlockPos teeAnchor, BlockPos basketAnchor) {
        if (teeAnchor == null || basketAnchor == null) {
            return new int[] { 0, -1 };
        }

        int dx = basketAnchor.getX() - teeAnchor.getX();
        int dz = basketAnchor.getZ() - teeAnchor.getZ();
        if (dx == 0 && dz == 0) {
            return new int[] { 0, -1 };
        }

        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { -Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, -Integer.compare(dz, 0) };
    }

    private static int maxLieDelta(BlockPos lie, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        int maxDelta = Math.max(
                Math.max(Math.abs(tee.getX() - lie.getX()), Math.abs(tee.getZ() - lie.getZ())),
                Math.max(Math.abs(basket.getX() - lie.getX()), Math.abs(basket.getZ() - lie.getZ()))
        );
        if (alternateAnchor != null) {
            maxDelta = Math.max(
                    maxDelta,
                    Math.max(Math.abs(alternateAnchor.getX() - lie.getX()), Math.abs(alternateAnchor.getZ() - lie.getZ()))
            );
        }
        return maxDelta;
    }

    private static void clearAllLieMarkers(MinecraftServer server) {
        for (Map<BlockPos, LieMarkerState> markerStates : LIE_MARKER_HISTORY.values()) {
            for (LieMarkerState markerState : markerStates.values()) {
                ServerWorld world = server.getWorld(markerState.worldKey());
                if (world == null) {
                    continue;
                }
                if (world.getBlockState(markerState.markerPos()).isOf(Blocks.LIME_WOOL)) {
                    world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), 3);
                }
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

    private static void updateLieMarker(ServerPlayerEntity player, BlockPos lieFeet) {
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

    private static void clearPlayerLieMarkers(MinecraftServer server, UUID playerId) {
        Map<BlockPos, LieMarkerState> markerStates = LIE_MARKER_HISTORY.get(playerId);
        if (markerStates == null || markerStates.isEmpty()) {
            return;
        }

        for (LieMarkerState markerState : markerStates.values()) {
            ServerWorld world = server.getWorld(markerState.worldKey());
            if (world == null) {
                continue;
            }
            if (world.getBlockState(markerState.markerPos()).isOf(Blocks.LIME_WOOL)) {
                world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), 3);
            }
        }

        markerStates.clear();
    }

    private static void spawnBreadcrumbLine(ServerWorld world, ServerPlayerEntity player, BlockPos to) {
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

    private static void sendClankTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 25, 8));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("CLANK!").formatted(net.minecraft.util.Formatting.GRAY, net.minecraft.util.Formatting.ITALIC)));
    }

    private static void sendStrictPenaltyTitle(ServerPlayerEntity player, StrictPenaltyType landingPenalty, int penaltyStrokes) {
        String titleText = landingPenalty == StrictPenaltyType.OB ? "OB +" + penaltyStrokes : "Hazard +" + penaltyStrokes;
        String subtitleText = landingPenalty == StrictPenaltyType.OB ? "Returned to lie" : "Penalty applied";

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(net.minecraft.util.Formatting.WHITE)));
    }

    private static void sendHoleFinishTitle(ServerPlayerEntity player, int holeScore, int holePar) {
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

    private static String golfResultName(int holeScore, int holeDelta) {
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

    private static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
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

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
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

    private static PlayerRoundState resolveThrowLanding(
            ServerPlayerEntity player,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
                boolean hudScoringDebug,
                boolean strictFlowDebug
    ) {
        Integer processedTotalObj = LAST_PROCESSED_THROW_TOTAL.get(player.getUuid());
        int processedTotal;
        if (processedTotalObj == null) {
            // First tracker tick for this player in the active round.
            // If no throws yet, initialize and wait. If throws already happened,
            // process the latest throw instead of skipping it.
            if (state.totalStrokes() == 0) {
                LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), 0);
                return state;
            }
            processedTotal = state.totalStrokes() - 1;
        } else {
            processedTotal = processedTotalObj;
        }

        if (state.totalStrokes() <= processedTotal) {
            LAST_RESOLUTION_REASON.put(player.getUuid(), "NO_NEW_THROW");
            return state;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos throwLie = state.lie();
        BlockPos currentFeet = player.getBlockPos();

        // Wait for the exact throw pearl (plus a short release grace window) before resolving lie.
        // This avoids stale, older pearls from keeping resolution pinned at the tee.
        boolean trackedPearlInFlight = hasTrackedPearlInFlight(world, player, throwLie);
        boolean withinReleaseGrace = isWithinThrowReleaseGrace(world, player.getUuid());
        if (trackedPearlInFlight || withinReleaseGrace) {
            int pendingTicks = LAST_THROW_PENDING_TICKS.merge(player.getUuid(), 1, Integer::sum);
            if (pendingTicks <= MAX_THROW_RESOLUTION_WAIT_TICKS) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing wait | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} inFlightPearl={} releaseGrace={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            pendingTicks,
                            trackedPearlInFlight,
                            withinReleaseGrace
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), trackedPearlInFlight ? "WAITING_TRACKED_PEARL" : "WAITING_RELEASE_GRACE");
                return state;
            }

            // If the exact throw pearl is still in flight after timeout, keep waiting instead of
            // force-resolving to the throw lie. This avoids false strict blocks on very long throws.
            if (trackedPearlInFlight) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.warn(
                            "Strict landing long-flight wait extension | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} trackedPearlStillInFlight=true",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            pendingTicks
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), "WAITING_TRACKED_PEARL_LONG_FLIGHT");
                return state;
            }

            McdgMod.LOGGER.warn(
                    "Forcing throw landing resolution after {} ticks with in-flight pearl still present | player={} hole={} throwLie={} {} {}",
                    pendingTicks,
                    player.getGameProfile().getName(),
                    state.currentHole(),
                    throwLie.getX(),
                    throwLie.getY(),
                    throwLie.getZ()
            );
        }
        LAST_THROW_PENDING_TICKS.remove(player.getUuid());

        BlockPos landingFeet = findNearestStandableFeet(world, currentFeet);

        BlockPos resultingLie = landingFeet;
        BlockPos firstOutCrossing = null;
        StrictPenaltyType landingPenalty = StrictPenaltyType.NONE;
        if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            StrictPenaltyType currentFeetPenalty = classifyOutType(world, currentFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            StrictPenaltyType standableFeetPenalty = classifyOutType(world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = combinePenalty(currentFeetPenalty, standableFeetPenalty);
            if (landingPenalty != StrictPenaltyType.NONE) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing classified | player={} hole={} total={} throwLie={} currentFeet={} landingFeet={} currentPenalty={} standablePenalty={} penalty={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            formatPos(landingFeet),
                            currentFeetPenalty.name(),
                            standableFeetPenalty.name(),
                            landingPenalty.name()
                    );
                }
                if (landingPenalty == StrictPenaltyType.OB) {
                    CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                            world,
                            throwLie,
                            currentFeet,
                            currentHole,
                            tee,
                            basket,
                                alternateAnchor,
                            rulesetManager
                    );
                    resultingLie = crossing.safeLie();
                    firstOutCrossing = crossing.firstOutCrossing();
                } else {
                    resultingLie = currentFeet.toImmutable();
                }

                int penaltyStrokes = landingPenalty == StrictPenaltyType.OB
                        ? rulesetManager.strictObPenaltyStrokes()
                        : rulesetManager.strictHazardPenaltyStrokes();
                if (penaltyStrokes > 0) {
                    roundStateManager.applyPenaltyStrokes(player.getUuid(), penaltyStrokes);
                }

                player.teleport(
                        resultingLie.getX() + 0.5,
                        resultingLie.getY() + 1.0,
                        resultingLie.getZ() + 0.5
                );

                String label = landingPenalty == StrictPenaltyType.OB ? "OB" : "Hazard";
                String penaltyText = landingPenalty == StrictPenaltyType.OB
                        ? "Returned to last in-bounds solid block."
                        : "Play next throw from hazard lie.";
                player.sendMessage(
                    Text.literal(label + " landing in strict mode: +" + penaltyStrokes + " stroke. " + penaltyText),
                    true
                );
                sendStrictPenaltyTitle(player, landingPenalty, penaltyStrokes);
                state = roundStateManager.markLastThrowPenalty(player.getUuid(), true).orElse(state);
                }

                if (hudScoringDebug) {
                player.sendMessage(Text.literal(
                        "Strict dbg | landing=" + landingPenalty.name()
                                + " | firstOut=" + formatPos(firstOutCrossing)
                                + " | safeLie=" + formatPos(resultingLie)
                ), false);
            }
        }

        if (landingPenalty == StrictPenaltyType.NONE) {
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
        }

        // Basket body hits (above the make-zone) should bounce to the ring with a CLANK cue.
        if (shouldBounceOffBasketStructure(resultingLie, basket)) {
            BlockPos bounced = basketBouncePosition(world, basket);
            resultingLie = bounced;
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);
            sendClankTitle(player);
        }

        resultingLie = findNearestStandableFeet(world, resultingLie);
        if (!isStandableFeetBlock(world, resultingLie)) {
            resultingLie = findNearestStandableFeet(world, throwLie);
        }

        roundStateManager.updateLie(player.getUuid(), resultingLie);
        updateLieMarker(player, resultingLie);
        PlayerRoundState updated = roundStateManager.getState(player.getUuid()).orElse(state);
        if (strictFlowDebug) {
            McdgMod.LOGGER.info(
                    "Strict landing resolved | player={} hole={} totalBefore={} totalAfter={} throwLie={} resultingLie={} penalty={} lastPenalty={}",
                    player.getGameProfile().getName(),
                    updated.currentHole(),
                    state.totalStrokes(),
                    updated.totalStrokes(),
                    formatPos(throwLie),
                    formatPos(resultingLie),
                    landingPenalty.name(),
                    updated.lastThrowPenalty()
            );
        }
        LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), updated.totalStrokes());
        LAST_THROW_PEARL_UUID.remove(player.getUuid());
        LAST_THROW_RELEASE_TICK.remove(player.getUuid());
        LAST_RESOLUTION_REASON.put(player.getUuid(), "RESOLVED");
        return updated;
    }

    static void registerThrowRelease(UUID playerId, UUID pearlId, long worldTime) {
        LAST_THROW_PEARL_UUID.put(playerId, pearlId);
        LAST_THROW_RELEASE_TICK.put(playerId, worldTime);
        LAST_THROW_PENDING_TICKS.remove(playerId);
    }

    static boolean isThrowResolutionPending(UUID playerId, int totalStrokes) {
        if (totalStrokes <= 0) {
            return false;
        }

        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        if (processedTotal == null) {
            return true;
        }

        return processedTotal < totalStrokes;
    }

    static String strictThrowGateDebugSnapshot(UUID playerId, int totalStrokes) {
        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        Integer pendingTicks = LAST_THROW_PENDING_TICKS.get(playerId);
        String reason = LAST_RESOLUTION_REASON.getOrDefault(playerId, "UNKNOWN");
        boolean pending = totalStrokes > 0 && (processedTotal == null || processedTotal < totalStrokes);
        return "pending=" + pending
                + " totalStrokes=" + totalStrokes
                + " processedTotal=" + (processedTotal == null ? "-" : processedTotal)
                + " pendingTicks=" + (pendingTicks == null ? "-" : pendingTicks)
                + " lastReason=" + reason;
    }

    private static StrictPenaltyType combinePenalty(StrictPenaltyType first, StrictPenaltyType second) {
        if (first == StrictPenaltyType.OB || second == StrictPenaltyType.OB) {
            return StrictPenaltyType.OB;
        }
        if (first == StrictPenaltyType.HAZARD || second == StrictPenaltyType.HAZARD) {
            return StrictPenaltyType.HAZARD;
        }
        return StrictPenaltyType.NONE;
    }

    private static boolean hasTrackedPearlInFlight(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        UUID trackedPearlId = LAST_THROW_PEARL_UUID.get(player.getUuid());
        if (trackedPearlId == null) {
            return false;
        }

        Box search = new Box(origin).expand(384.0, 192.0, 384.0);
        return !world.getEntitiesByClass(
                EnderPearlEntity.class,
                search,
                pearl -> trackedPearlId.equals(pearl.getUuid()) && !pearl.isRemoved()
        ).isEmpty();
    }

    private static boolean isWithinThrowReleaseGrace(ServerWorld world, UUID playerId) {
        Long releaseTick = LAST_THROW_RELEASE_TICK.get(playerId);
        if (releaseTick == null) {
            return false;
        }
        return (world.getTime() - releaseTick) <= THROW_RELEASE_GRACE_TICKS;
    }

    private static StrictPenaltyType classifyOutType(
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

    private static StrictPenaltyType classifyOutTypeWithCorridor(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth
    ) {
        if (isFluidPenaltyZone(world, feet)) {
            return StrictPenaltyType.OB;
        }

        double lateral = distanceFromPlayableRouteXZ(feet, tee, basket, alternateAnchor);
        if (lateral > corridorHalfWidth) {
            return StrictPenaltyType.OB;
        }

        // Basket green: hazard-safe within the placed green, but still not OB-safe.
        if (isBasketGreenSafe(feet, basket.down())) {
            return StrictPenaltyType.NONE;
        }

        if (rulesetManager.strictEnableSlopeHazard() && isSteepSlopeHazard(world, feet, rulesetManager.strictSlopeHazardDeltaY())) {
            return StrictPenaltyType.HAZARD;
        }

        if (rulesetManager.strictEnableRoughHazard() && isDenseRoughHazard(world, feet, rulesetManager.strictRoughHazardLeafLogThreshold())) {
            return StrictPenaltyType.HAZARD;
        }

        return StrictPenaltyType.NONE;
    }

    private static boolean isBasketGreenSafe(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }

    private static boolean isFluidPenaltyZone(ServerWorld world, BlockPos feet) {
        return world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA);
    }

    private static int strictCorridorHalfWidth(Hole hole, ServerWorld world, BlockPos tee, BlockPos basket, TournamentRulesetManager rulesetManager) {
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

    private static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int dominant = Math.max(Math.abs(dx), Math.abs(dz));
        if (dominant == 0) {
            return 0;
        }

        int stepX = Integer.signum(dx);
        int stepZ = Integer.signum(dz);

        int longest = 0;
        int current = 0;

        int x = start.getX();
        int z = start.getZ();
        int errX = 0;
        int errZ = 0;

        for (int i = 0; i <= dominant; i++) {
            if (isWaterCarryColumn(world, x, z)) {
                current++;
                if (current > longest) {
                    longest = current;
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

        return longest;
    }

    /**
     * Returns a standable position in the 2-block ring around the basket (as if the disc bounced off it).
     * Tries all 8 adjacent + diagonal offsets at radius 1–2; picks the first solid standable block.
     * Falls back to one block north if no standable block is found.
     */
    private static BlockPos basketBouncePosition(ServerWorld world, BlockPos basket) {
        int[] offsets = {1, -1, 2, -2};
        for (int dz : offsets) {
            for (int dx : offsets) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist < 1 || dist > 3) continue;
                BlockPos candidate = findNearestStandableFeet(world,
                        new BlockPos(basket.getX() + dx, basket.getY(), basket.getZ() + dz));
                if (candidate != null && manhattanDistance(candidate, basket) >= 1) {
                    return candidate;
                }
            }
        }
        // Absolute fallback: one block north at basket height.
        return basket.north();
    }

    private static boolean isWaterCarryColumn(ServerWorld world, int x, int z) {
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

    private static boolean hasAnySafeLandingNearby(ServerWorld world, BlockPos center) {
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

    private static CrossingResolution findLastSolidBeforeOutCrossing(
            ServerWorld world,
            BlockPos throwLie,
            BlockPos landingFeet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        BlockPos start = findNearestStandableFeet(world, throwLie);
        BlockPos end = landingFeet;
        BlockPos lastInBoundsSolid = start;
        BlockPos firstOut = null;

        int distance = Math.max(1, manhattanDistance(start, end));
        int samples = Math.max(24, distance * 4);
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) Math.floor((start.getX() + 0.5) + ((end.getX() + 0.5 - (start.getX() + 0.5)) * t));
            int y = (int) Math.floor((start.getY() + 0.5) + ((end.getY() + 0.5 - (start.getY() + 0.5)) * t));
            int z = (int) Math.floor((start.getZ() + 0.5) + ((end.getZ() + 0.5 - (start.getZ() + 0.5)) * t));
            BlockPos probeRaw = new BlockPos(x, y, z);

            if (classifyOutType(world, probeRaw, currentHole, tee, basket, alternateAnchor, rulesetManager) != StrictPenaltyType.NONE) {
                if (firstOut == null) {
                    firstOut = probeRaw.toImmutable();
                }
                continue;
            }

            BlockPos standableProbe = findNearestStandableFeet(world, probeRaw);
            if (isStandableFeetBlock(world, standableProbe)
                    && classifyOutType(world, standableProbe, currentHole, tee, basket, alternateAnchor, rulesetManager) == StrictPenaltyType.NONE) {
                lastInBoundsSolid = standableProbe;
            }
        }

        return new CrossingResolution(lastInBoundsSolid.toImmutable(), firstOut);
    }

    private static boolean isSteepSlopeHazard(ServerWorld world, BlockPos feet, int slopeDeltaThreshold) {
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

    private static boolean isDenseRoughHazard(ServerWorld world, BlockPos feet, int threshold) {
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

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "-";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos findNearestStandableFeet(ServerWorld world, BlockPos baseFeet) {
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

    private static boolean isStandableFeetBlock(ServerWorld world, BlockPos feet) {
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

    private enum StrictPenaltyType {
        NONE,
        HAZARD,
        OB
    }

    private record CrossingResolution(BlockPos safeLie, BlockPos firstOutCrossing) {
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
