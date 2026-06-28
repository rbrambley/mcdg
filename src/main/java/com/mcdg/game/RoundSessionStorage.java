package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

public final class RoundSessionStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-round-session.json";
    private static final int CURRENT_VERSION = 1;

    public boolean save(MinecraftServer server, ActiveCourseManager courseManager, RoundStateManager roundStateManager, Logger logger) {
        if (server == null || courseManager == null || roundStateManager == null) {
            return false;
        }

        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        boolean roundActive = courseManager.isRoundActive();
        Set<UUID> participantIds = courseManager.getActiveParticipantIds();
        if (!roundActive || course == null || placed == null || participantIds.isEmpty()) {
            clear(server, logger);
            return false;
        }

        Map<UUID, PlayerRoundState> allStates = roundStateManager.snapshotStates();
        Map<UUID, PlayerRoundState> participantStates = new HashMap<>();
        for (UUID participantId : participantIds) {
            PlayerRoundState state = allStates.get(participantId);
            if (state != null) {
                participantStates.put(participantId, state);
            }
        }

        Map<UUID, Integer> allCompleted = roundStateManager.snapshotCompletedRounds();
        Map<UUID, Integer> participantCompleted = new HashMap<>();
        for (UUID participantId : participantIds) {
            Integer completed = allCompleted.get(participantId);
            if (completed != null) {
                participantCompleted.put(participantId, completed);
            }
        }

        RoundSessionSnapshot snapshot = RoundSessionSnapshot.from(
                roundActive,
                placed.worldKey().getValue().toString(),
                course.seed(),
                course.holes().size(),
                participantIds,
                participantStates,
                participantCompleted
        );

        Path path = resolvePath(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(snapshot));
            return true;
        } catch (IOException ex) {
            if (logger != null) {
                logger.error("Failed to save round session snapshot to {}", path, ex);
            }
            return false;
        }
    }

    public Optional<LoadedRoundSession> load(MinecraftServer server, Logger logger) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path);
            RoundSessionSnapshot snapshot = GSON.fromJson(json, RoundSessionSnapshot.class);
            if (snapshot == null) {
                return Optional.empty();
            }
            return snapshot.toLoadedRoundSession();
        } catch (IOException | RuntimeException ex) {
            if (logger != null) {
                logger.error("Failed to load round session snapshot from {}", path, ex);
            }
            return Optional.empty();
        }
    }

    public void clear(MinecraftServer server, Logger logger) {
        Path path = resolvePath(server);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            if (logger != null) {
                logger.error("Failed to delete round session snapshot {}", path, ex);
            }
        }
    }

    private static Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }

    public record LoadedRoundSession(
            boolean roundActive,
            String worldKey,
            long courseSeed,
            int holeCount,
            Set<UUID> participantIds,
            Map<UUID, PlayerRoundState> playerStates,
            Map<UUID, Integer> completedTotals
    ) {}

    private record RoundSessionSnapshot(
            int version,
            long createdAtMs,
            boolean roundActive,
            String worldKey,
            long courseSeed,
            int holeCount,
            List<String> participantIds,
            Map<String, PlayerStateSnapshot> playerStates,
            Map<String, Integer> completedTotals
    ) {
        private static RoundSessionSnapshot from(
                boolean roundActive,
                String worldKey,
                long courseSeed,
                int holeCount,
                Set<UUID> participantIds,
                Map<UUID, PlayerRoundState> playerStates,
                Map<UUID, Integer> completedTotals
        ) {
            List<String> participantIdValues = new ArrayList<>();
            Map<String, PlayerStateSnapshot> playerStateSnapshots = new HashMap<>();
            Map<String, Integer> completedValueSnapshots = new HashMap<>();

            for (UUID participantId : participantIds) {
                if (participantId == null) {
                    continue;
                }
                String idValue = participantId.toString();
                participantIdValues.add(idValue);

                PlayerRoundState state = playerStates.get(participantId);
                if (state != null) {
                    playerStateSnapshots.put(idValue, PlayerStateSnapshot.from(state));
                }

                Integer completed = completedTotals.get(participantId);
                if (completed != null) {
                    completedValueSnapshots.put(idValue, completed);
                }
            }

            return new RoundSessionSnapshot(
                    CURRENT_VERSION,
                    System.currentTimeMillis(),
                    roundActive,
                    worldKey,
                    courseSeed,
                    holeCount,
                    participantIdValues,
                    playerStateSnapshots,
                    completedValueSnapshots
            );
        }

        private Optional<LoadedRoundSession> toLoadedRoundSession() {
            if (version <= 0 || version > CURRENT_VERSION) {
                if (version > CURRENT_VERSION) {
                    McdgMod.LOGGER.warn("Round session version {} is newer than supported {}; discarding.",
                            version, CURRENT_VERSION);
                }
                return Optional.empty();
            }
            if (!roundActive) {
                return Optional.empty();
            }
            if (worldKey == null || worldKey.isBlank()) {
                return Optional.empty();
            }
            if (holeCount <= 0) {
                return Optional.empty();
            }

            Set<UUID> parsedParticipants = new java.util.LinkedHashSet<>();
            Map<UUID, PlayerRoundState> parsedStates = new HashMap<>();
            Map<UUID, Integer> parsedCompletedTotals = new HashMap<>();

            if (participantIds != null) {
                for (String idValue : participantIds) {
                    UUID playerId = parseUuid(idValue);
                    if (playerId == null) {
                        continue;
                    }
                    parsedParticipants.add(playerId);

                    PlayerStateSnapshot stateSnapshot = playerStates == null ? null : playerStates.get(idValue);
                    if (stateSnapshot != null) {
                        PlayerRoundState parsedState = stateSnapshot.toPlayerRoundState();
                        if (parsedState != null) {
                            parsedStates.put(playerId, parsedState);
                        }
                    }

                    Integer completed = completedTotals == null ? null : completedTotals.get(idValue);
                    if (completed != null && completed >= 0) {
                        parsedCompletedTotals.put(playerId, completed);
                    }
                }
            }

            if (parsedParticipants.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new LoadedRoundSession(
                    true,
                    worldKey,
                    courseSeed,
                    holeCount,
                    Set.copyOf(parsedParticipants),
                    Map.copyOf(parsedStates),
                    Map.copyOf(parsedCompletedTotals)
            ));
        }

        private static UUID parseUuid(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private record PlayerStateSnapshot(
            int currentHole,
            int lieX,
            int lieY,
            int lieZ,
            int holeStrokes,
            int totalStrokes,
            boolean lastThrowPenalty,
            int aceCount,
            Float nextThrowPowerMultiplier
    ) {
        private static PlayerStateSnapshot from(PlayerRoundState state) {
            BlockPos lie = state.lie();
            return new PlayerStateSnapshot(
                    state.currentHole(),
                    lie.getX(),
                    lie.getY(),
                    lie.getZ(),
                    state.holeStrokes(),
                    state.totalStrokes(),
                    state.lastThrowPenalty(),
                    state.aceCount(),
                    state.nextThrowPowerMultiplier()
            );
        }

        private PlayerRoundState toPlayerRoundState() {
            int activeOrdinal = currentHole;
            if (activeOrdinal < 1 || holeStrokes < 0 || totalStrokes < 0) {
                return null;
            }
            try {
                float multiplier = nextThrowPowerMultiplier != null ? nextThrowPowerMultiplier : 1.0f;
                return new PlayerRoundState(
                        activeOrdinal,
                        new BlockPos(lieX, lieY, lieZ),
                        holeStrokes,
                        totalStrokes,
                        lastThrowPenalty,
                        aceCount,
                        multiplier
                );
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
