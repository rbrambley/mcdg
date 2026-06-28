package com.mcdg.game;

import com.mcdg.net.NextThrowModifierSync;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class RoundStateManager {
    private final ConcurrentMap<UUID, PlayerRoundState> stateByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> completedTotalByPlayer = new ConcurrentHashMap<>();

    public void startRoundForPlayer(UUID playerId, BlockPos startLie) {
        stateByPlayer.put(playerId, PlayerRoundState.start(startLie));
        completedTotalByPlayer.remove(playerId);
    }

    public void startRoundForPlayer(ServerPlayerEntity player, BlockPos startLie) {
        startRoundForPlayer(player.getUuid(), startLie);
        syncNextThrowPowerMultiplier(player);
    }

    public PlayerRoundState recordThrow(UUID playerId, BlockPos throwLie) {
        PlayerRoundState newState = stateByPlayer.compute(playerId, (id, existing) -> {
            if (existing == null) {
                return PlayerRoundState.start(throwLie).recordThrow(throwLie);
            }
            return existing.recordThrow(throwLie);
        });

        // Notify TurnManager of the throw for turn rotation
        if (newState != null) {
            TurnManager.recordThrow(playerId, newState.currentHole());
        }

        return newState;
    }

    public Optional<PlayerRoundState> getState(UUID playerId) {
        return Optional.ofNullable(stateByPlayer.get(playerId));
    }

    public float getNextThrowPowerMultiplier(UUID playerId) {
        PlayerRoundState state = stateByPlayer.get(playerId);
        return state != null ? state.nextThrowPowerMultiplier() : 1.0f;
    }

    public Optional<PlayerRoundState> setNextThrowPowerMultiplier(UUID playerId, float multiplier) {
        if (multiplier <= 0.0f) {
            return Optional.empty();
        }
        PlayerRoundState updated = stateByPlayer.computeIfPresent(playerId, (id, existing) -> {
            float effective = Math.min(existing.nextThrowPowerMultiplier(), multiplier);
            return existing.withNextThrowPowerMultiplier(effective);
        });
        return Optional.ofNullable(updated);
    }

    public void syncNextThrowPowerMultiplier(ServerPlayerEntity player) {
        float multiplier = getNextThrowPowerMultiplier(player.getUuid());
        ServerPlayNetworking.send(player, new NextThrowModifierSync.Payload(multiplier));
    }

    public Map<UUID, PlayerRoundState> snapshotStates() {
        return Map.copyOf(stateByPlayer);
    }

    public Optional<PlayerRoundState> advanceToNextHole(UUID playerId, BlockPos nextTeeLie) {
        PlayerRoundState previousState = stateByPlayer.get(playerId);
        int previousHole = previousState != null ? previousState.currentHole() : -1;
        
        PlayerRoundState updated = stateByPlayer.computeIfPresent(playerId, (id, existing) -> existing.advanceToNextHole(nextTeeLie));
        
        // Clear last thrower for the hole that was just completed
        if (previousHole >= 1) {
            TurnManager.clearLastThrower(previousHole);
        }
        
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

    public Optional<PlayerRoundState> recordAce(UUID playerId) {
        PlayerRoundState updated = stateByPlayer.computeIfPresent(
                playerId,
                (id, existing) -> existing.recordAce()
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
        TurnManager.reset();

        if (states != null && !states.isEmpty()) {
            stateByPlayer.putAll(states);
        }
        if (completedTotals != null && !completedTotals.isEmpty()) {
            completedTotalByPlayer.putAll(completedTotals);
        }
    }

    public void clearPlayer(UUID playerId) {
        PlayerRoundState previousState = stateByPlayer.get(playerId);
        int previousHole = previousState != null ? previousState.currentHole() : -1;
        
        stateByPlayer.remove(playerId);
        
        // Clear last thrower for the hole the player was on
        if (previousHole >= 1) {
            TurnManager.clearLastThrowerForPlayer(playerId);
        }
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
        TurnManager.reset();
    }
}
