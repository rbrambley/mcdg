package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.McdgMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

public final class LeaderboardManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-leaderboards.json";
    private static final int MAX_ENTRIES_PER_COURSE = 5;

    private final Map<String, List<LeaderboardEntry>> entriesByCourse = new ConcurrentHashMap<>();

    public void recordScore(MinecraftServer server, String courseName, String playerName, int score) {
        if (courseName == null || courseName.isBlank() || playerName == null || playerName.isBlank() || score < 1) {
            return;
        }

        List<LeaderboardEntry> entries = entriesByCourse.computeIfAbsent(courseName, k -> new ArrayList<>());
        entries.add(new LeaderboardEntry(playerName, score, System.currentTimeMillis()));
        entries.sort(Comparator.comparingInt(LeaderboardEntry::score));
        while (entries.size() > MAX_ENTRIES_PER_COURSE) {
            entries.remove(entries.size() - 1);
        }

        save(server);
    }

    public List<LeaderboardEntry> getTopScores(String courseName, int count) {
        if (courseName == null || courseName.isBlank()) {
            return List.of();
        }

        List<LeaderboardEntry> entries = entriesByCourse.get(courseName);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        int safeCount = Math.max(0, Math.min(count, entries.size()));
        return List.copyOf(entries.subList(0, safeCount));
    }

    public void load(MinecraftServer server) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            return;
        }

        try {
            String json = Files.readString(path);
            LeaderboardSnapshot snapshot = GSON.fromJson(json, LeaderboardSnapshot.class);
            if (snapshot == null || snapshot.leaderboards == null) {
                return;
            }

            entriesByCourse.clear();
            for (Map.Entry<String, List<LeaderboardEntry>> entry : snapshot.leaderboards.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                List<LeaderboardEntry> sorted = new ArrayList<>(entry.getValue());
                sorted.sort(Comparator.comparingInt(LeaderboardEntry::score));
                while (sorted.size() > MAX_ENTRIES_PER_COURSE) {
                    sorted.remove(sorted.size() - 1);
                }
                entriesByCourse.put(entry.getKey(), sorted);
            }
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load leaderboard snapshot from {}", path, ex);
        }
    }

    public void save(MinecraftServer server) {
        Path path = resolvePath(server);
        try {
            Files.createDirectories(path.getParent());
            LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
            snapshot.leaderboards = new ConcurrentHashMap<>(entriesByCourse);
            Files.writeString(path, GSON.toJson(snapshot));
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save leaderboard snapshot to {}", path, ex);
        }
    }

    private static Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(McdgMod.MOD_ID).resolve(FILE_NAME);
    }

    public record LeaderboardEntry(String playerName, int score, long dateMs) {
    }

    @SuppressWarnings("unused")
    private static final class LeaderboardSnapshot {
        private Map<String, List<LeaderboardEntry>> leaderboards;
    }
}
