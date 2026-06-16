package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.data.Course;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

public final class PlayerRoundSessionStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-player-round-sessions.json";
    private static final int CURRENT_VERSION = 1;

    // In-memory cache for the sessions file -- invalidated on every write.
    private SessionsFileSnapshot sessionsCache = null;

    public boolean savePlayer(
            MinecraftServer server,
            UUID playerId,
            Course course,
            PlacedCourseState placed,
            PlayerRoundState state,
            Logger logger
    ) {
        if (server == null || playerId == null || course == null || placed == null || state == null) {
            return false;
        }

        SessionsFileSnapshot fileSnapshot = readFileSnapshot(resolvePath(server), logger);
        fileSnapshot.sessions.put(playerId.toString(), PlayerSessionSnapshot.from(course, placed, state));
        return writeFileSnapshot(resolvePath(server), fileSnapshot, logger);
    }

    public Optional<LoadedPlayerRoundSession> loadPlayer(MinecraftServer server, UUID playerId, Logger logger) {
        if (server == null || playerId == null) {
            return Optional.empty();
        }

        SessionsFileSnapshot fileSnapshot = readFileSnapshot(resolvePath(server), logger);
        PlayerSessionSnapshot snapshot = fileSnapshot.sessions.get(playerId.toString());
        if (snapshot == null) {
            return Optional.empty();
        }
        return snapshot.toLoadedSession();
    }

    public boolean clearPlayer(MinecraftServer server, UUID playerId, Logger logger) {
        if (server == null || playerId == null) {
            return false;
        }

        Path path = resolvePath(server);
        SessionsFileSnapshot fileSnapshot = readFileSnapshot(path, logger);
        boolean removed = fileSnapshot.sessions.remove(playerId.toString()) != null;
        if (!removed) {
            return false;
        }
        return writeFileSnapshot(path, fileSnapshot, logger);
    }

    private static Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
    }

    private SessionsFileSnapshot readFileSnapshot(Path path, Logger logger) {
        if (sessionsCache != null) {
            return sessionsCache;
        }
        if (!Files.exists(path)) {
            return new SessionsFileSnapshot(CURRENT_VERSION, new HashMap<>());
        }

        try {
            String json = Files.readString(path);
            SessionsFileSnapshot parsed = GSON.fromJson(json, SessionsFileSnapshot.class);
            if (parsed == null || parsed.sessions == null || parsed.version <= 0 || parsed.version > CURRENT_VERSION) {
                return new SessionsFileSnapshot(CURRENT_VERSION, new HashMap<>());
            }
            SessionsFileSnapshot snapshot = new SessionsFileSnapshot(CURRENT_VERSION, new HashMap<>(parsed.sessions));
            sessionsCache = snapshot;
            return snapshot;
        } catch (IOException | RuntimeException ex) {
            if (logger != null) {
                logger.error("Failed to read player round sessions from {}", path, ex);
            }
            return new SessionsFileSnapshot(CURRENT_VERSION, new HashMap<>());
        }
    }

    private boolean writeFileSnapshot(Path path, SessionsFileSnapshot fileSnapshot, Logger logger) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(fileSnapshot));
            sessionsCache = fileSnapshot;
            return true;
        } catch (IOException ex) {
            if (logger != null) {
                logger.error("Failed to write player round sessions to {}", path, ex);
            }
            return false;
        }
    }

    public record LoadedPlayerRoundSession(
            String worldKey,
            long courseSeed,
            int holeCount,
            String courseName,
            PlayerRoundState state,
            long savedAtMs
    ) {}

    private record SessionsFileSnapshot(
            int version,
            Map<String, PlayerSessionSnapshot> sessions
    ) {}

    private record PlayerSessionSnapshot(
            String worldKey,
            long courseSeed,
            int holeCount,
            String courseName,
            int currentHole,
            int lieX,
            int lieY,
            int lieZ,
            int holeStrokes,
            int totalStrokes,
            boolean lastThrowPenalty,
            long savedAtMs
    ) {
        private static PlayerSessionSnapshot from(Course course, PlacedCourseState placed, PlayerRoundState state) {
            BlockPos lie = state.lie();
            return new PlayerSessionSnapshot(
                    placed.worldKey().getValue().toString(),
                    course.seed(),
                    course.holes().size(),
                    course.name(),
                    state.currentHole(),
                    lie.getX(),
                    lie.getY(),
                    lie.getZ(),
                    state.holeStrokes(),
                    state.totalStrokes(),
                    state.lastThrowPenalty(),
                    System.currentTimeMillis()
            );
        }

        private Optional<LoadedPlayerRoundSession> toLoadedSession() {
            int activeOrdinal = currentHole;
            if (worldKey == null || worldKey.isBlank() || holeCount <= 0 || activeOrdinal < 1 || holeStrokes < 0 || totalStrokes < 0) {
                return Optional.empty();
            }

            PlayerRoundState state;
            try {
                state = new PlayerRoundState(
                        activeOrdinal,
                        new BlockPos(lieX, lieY, lieZ),
                        holeStrokes,
                        totalStrokes,
                        lastThrowPenalty
                );
            } catch (RuntimeException ex) {
                return Optional.empty();
            }

            String resolvedName = (courseName != null && !courseName.isBlank()) ? courseName : "Unknown Course";
            return Optional.of(new LoadedPlayerRoundSession(worldKey, courseSeed, holeCount, resolvedName, state, savedAtMs));
        }
    }
}
