package com.mcdg.game;

import com.mcdg.data.Course;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

/**
 * Enforces turn timeouts and manages active-turn state per hole.
 */
public final class TurnManager {
    private static final int TURN_TIMEOUT_TICKS = 20 * 120;
    private static final Map<Integer, UUID> ACTIVE_TURN_PLAYER_BY_HOLE = new HashMap<>();
    private static final Map<Integer, Long> ACTIVE_TURN_STARTED_AT_BY_HOLE = new HashMap<>();
    private static final Map<Integer, Integer> ACTIVE_TURN_TOTAL_STROKES_BY_HOLE = new HashMap<>();
    private static final Map<Integer, UUID> TURN_SKIP_ONCE_BY_HOLE = new HashMap<>();

    private TurnManager() {
    }

    public static void reset() {
        ACTIVE_TURN_PLAYER_BY_HOLE.clear();
        ACTIVE_TURN_STARTED_AT_BY_HOLE.clear();
        ACTIVE_TURN_TOTAL_STROKES_BY_HOLE.clear();
        TURN_SKIP_ONCE_BY_HOLE.clear();
    }

    public static void sendTurnActionBar(MinecraftServer server, ServerPlayerEntity viewer, int hole) {
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

    public static void enforceTurnTimeouts(
            MinecraftServer server,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            Course course,
            PlacedCourseState placed,
            Map<UUID, PlayerRoundState> snapshot
    ) {
        Map<Integer, UUID> previousActiveByHole = new HashMap<>(ACTIVE_TURN_PLAYER_BY_HOLE);
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

        for (Map.Entry<Integer, UUID> entry : updatedActiveByHole.entrySet()) {
            Integer hole = entry.getKey();
            UUID newPlayer = entry.getValue();
            UUID oldPlayer = previousActiveByHole.get(hole);
            if (newPlayer != null && !newPlayer.equals(oldPlayer)) {
                broadcastTurnChange(server, placed.worldKey(), hole, newPlayer, courseManager.getActiveParticipantIds());
            }
        }
    }

    private static void broadcastTurnChange(
            MinecraftServer server,
            RegistryKey<World> worldKey,
            int hole,
            UUID newPlayerId,
            Set<UUID> participantIds
    ) {
        ServerPlayerEntity newPlayer = server.getPlayerManager().getPlayer(newPlayerId);
        if (newPlayer == null) {
            return;
        }
        String name = newPlayer.getGameProfile().getName();
        Text message = Text.literal("It's now " + name + "'s turn on Hole " + hole)
                .formatted(Formatting.GREEN);
        for (UUID id : participantIds) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(id);
            if (viewer != null && viewer.getWorld().getRegistryKey() == worldKey) {
                viewer.sendMessage(message, false);
            }
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
                roundStateManager.updateLie(playerId, tee);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && player.getWorld().getRegistryKey() == placed.worldKey()) {
                    player.teleport(tee.getX() + 0.5, tee.getY() + 1.0, tee.getZ() + 0.5);
                    player.sendMessage(Text.literal("Turn timeout: +1 stroke. Reset to tee, turn passed."), true);
                }
            }
        }
    }

    public static UUID determineExpectedTurnPlayer(
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
                int aDistance = aState == null ? 0 : DistanceUtils.distanceMeters(aState.lie(), basket);
                int bDistance = bState == null ? 0 : DistanceUtils.distanceMeters(bState.lie(), basket);
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
            int aScore = HoleProgressTracker.scoreForHole(a, priorHole);
            int bScore = HoleProgressTracker.scoreForHole(b, priorHole);
            if (aScore != bScore) {
                return Integer.compare(aScore, bScore);
            }
        }

        int aHoleOneRank = HoleProgressTracker.HOLE_ONE_RANDOM_ORDER.getOrDefault(a, Integer.MAX_VALUE);
        int bHoleOneRank = HoleProgressTracker.HOLE_ONE_RANDOM_ORDER.getOrDefault(b, Integer.MAX_VALUE);
        if (aHoleOneRank != bHoleOneRank) {
            return Integer.compare(aHoleOneRank, bHoleOneRank);
        }
        return a.compareTo(b);
    }

}
