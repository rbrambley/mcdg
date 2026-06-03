package com.mcdg.game;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.util.math.BlockPos;

public final class RoundStateManager {
    private final ConcurrentMap<UUID, PlayerRoundState> stateByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> completedTotalByPlayer = new ConcurrentHashMap<>();

    public void startRoundForPlayer(UUID playerId, BlockPos startLie) {
        stateByPlayer.put(playerId, PlayerRoundState.start(startLie));
        completedTotalByPlayer.remove(playerId);
    }

    public PlayerRoundState recordThrow(UUID playerId, BlockPos throwLie) {
        return stateByPlayer.compute(playerId, (id, existing) -> {
            if (existing == null) {
                return PlayerRoundState.start(throwLie).recordThrow(throwLie);
            }
            return existing.recordThrow(throwLie);
        });
    }

    public Optional<PlayerRoundState> getState(UUID playerId) {
        return Optional.ofNullable(stateByPlayer.get(playerId));
    }

    public Map<UUID, PlayerRoundState> snapshotStates() {
        return Map.copyOf(stateByPlayer);
    }

    public Optional<PlayerRoundState> advanceToNextHole(UUID playerId, BlockPos nextTeeLie) {
        PlayerRoundState updated = stateByPlayer.computeIfPresent(playerId, (id, existing) -> existing.advanceToNextHole(nextTeeLie));
        return Optional.ofNullable(updated);
    }

    public Optional<PlayerRoundState> applyPenaltyStrokes(UUID playerId, int penaltyStrokes) {
        PlayerRoundState updated = stateByPlayer.computeIfPresent(
                playerId,
                (id, existing) -> existing.addPenaltyStrokes(penaltyStrokes)
        );
        return Optional.ofNullable(updated);
    }

    public Optional<PlayerRoundState> updateLie(UUID playerId, BlockPos lie) {
        PlayerRoundState updated = stateByPlayer.computeIfPresent(
                playerId,
                (id, existing) -> existing.withLie(lie)
        );
        return Optional.ofNullable(updated);
    }

    public Optional<PlayerRoundState> markLastThrowPenalty(UUID playerId, boolean lastThrowPenalty) {
        PlayerRoundState updated = stateByPlayer.computeIfPresent(
                playerId,
                (id, existing) -> existing.markLastThrowPenalty(lastThrowPenalty)
        );
        return Optional.ofNullable(updated);
    }

    public void recordCompletedRound(UUID playerId, int totalStrokes) {
        completedTotalByPlayer.put(playerId, totalStrokes);
    }

    public Map<UUID, Integer> snapshotCompletedRounds() {
        return Map.copyOf(completedTotalByPlayer);
    }

    public void setState(UUID playerId, PlayerRoundState state) {
        if (playerId == null || state == null) {
            return;
        }
        stateByPlayer.put(playerId, state);
    }

    public void setCompletedTotal(UUID playerId, int totalStrokes) {
        if (playerId == null) {
            return;
        }
        if (totalStrokes < 0) {
            completedTotalByPlayer.remove(playerId);
            return;
        }
        completedTotalByPlayer.put(playerId, totalStrokes);
    }

    public void restoreSnapshot(Map<UUID, PlayerRoundState> states, Map<UUID, Integer> completedTotals) {
        stateByPlayer.clear();
        completedTotalByPlayer.clear();

        if (states != null && !states.isEmpty()) {
            stateByPlayer.putAll(states);
        }
        if (completedTotals != null && !completedTotals.isEmpty()) {
            completedTotalByPlayer.putAll(completedTotals);
        }
    }

    public void clearPlayer(UUID playerId) {
        stateByPlayer.remove(playerId);
        completedTotalByPlayer.remove(playerId);
    }

    public void clearPlayers(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        for (UUID playerId : playerIds) {
            if (playerId != null) {
                clearPlayer(playerId);
            }
        }
    }

    public void clearAll() {
        stateByPlayer.clear();
        completedTotalByPlayer.clear();
    }
}
