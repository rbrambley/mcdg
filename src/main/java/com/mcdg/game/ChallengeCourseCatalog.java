package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Persistent storage for discovered challenge courses with per-player tracking.
 */
public final class ChallengeCourseCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-challenge-course-catalog.json";
    private static final int CURRENT_VERSION = 1;

    private final Map<UUID, CatalogEntry> entries = new HashMap<>();

    public ChallengeCourseCatalog() {
    }

    /**
     * Adds or updates a course in the catalog.
     */
    public void addOrUpdateCourse(LostCourse course, Course generatedCourse) {
        CatalogEntry entry = new CatalogEntry(
            course.courseId(),
            course.name(),
            course.type(),
            course.entrancePosition(),
            course.courseAnchor(),
            generatedCourse,
            Instant.now(),
            new HashMap<>(), // playerRewards
            new HashMap<>()  // playerCompletions
        );
        entries.put(course.courseId(), entry);
    }

    /**
     * Gets a course entry by ID.
     */
    public Optional<CatalogEntry> getCourse(UUID courseId) {
        return Optional.ofNullable(entries.get(courseId));
    }

    /**
     * Gets all discovered courses.
     */
    public List<CatalogEntry> getAllCourses() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Records that a player has received discovery rewards for a course.
     */
    public void markDiscoveryRewardClaimed(UUID courseId, UUID playerId) {
        CatalogEntry entry = entries.get(courseId);
        if (entry != null) {
            entry.playerRewards().put(playerId, new PlayerRewardData(Instant.now(), null));
        }
    }

    /**
     * Checks if a player has already received discovery rewards for a course.
     */
    public boolean hasClaimedDiscoveryRewards(UUID courseId, UUID playerId) {
        CatalogEntry entry = entries.get(courseId);
        return entry != null && entry.playerRewards().containsKey(playerId);
    }

    /**
     * Records a player's completion of a course with their score.
     */
    public void recordCourseCompletion(UUID courseId, UUID playerId, int score) {
        CatalogEntry entry = entries.get(courseId);
        if (entry != null) {
            PlayerCompletionData completionData = new PlayerCompletionData(Instant.now(), score);
            entry.playerCompletions().put(playerId, completionData);
        }
    }

    /**
     * Gets the best score across all players for a course.
     */
    public Optional<Integer> getBestScore(UUID courseId) {
        CatalogEntry entry = entries.get(courseId);
        if (entry == null || entry.playerCompletions().isEmpty()) {
            return Optional.empty();
        }

        return entry.playerCompletions().values().stream()
            .map(PlayerCompletionData::score)
            .min(Integer::compareTo);
    }

    /**
     * Gets all players who have completed a course.
     */
    public List<UUID> getPlayersWhoCompleted(UUID courseId) {
        CatalogEntry entry = entries.get(courseId);
        if (entry == null) {
            return List.of();
        }
        return new ArrayList<>(entry.playerCompletions().keySet());
    }

    /**
     * Saves the catalog to disk.
     */
    public void save(MinecraftServer server) {
        Path path = resolvePath(server);
        try {
            Files.createDirectories(path.getParent());
            CatalogSnapshot snapshot = new CatalogSnapshot(CURRENT_VERSION, entries);
            Files.writeString(path, GSON.toJson(snapshot));
            McdgMod.LOGGER.info("Saved challenge course catalog with {} entries", entries.size());
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save challenge course catalog to {}", path, ex);
        }
    }

    /**
     * Loads the catalog from disk.
     */
    public static Optional<ChallengeCourseCatalog> load(MinecraftServer server) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path);
            JsonElement root = JsonParser.parseString(json);
            CatalogSnapshot snapshot = GSON.fromJson(root, CatalogSnapshot.class);
            
            if (snapshot == null || snapshot.version() != CURRENT_VERSION) {
                McdgMod.LOGGER.warn("Challenge course catalog version mismatch, creating new catalog");
                return Optional.of(new ChallengeCourseCatalog());
            }

            ChallengeCourseCatalog catalog = new ChallengeCourseCatalog();
            catalog.entries.putAll(snapshot.entries());
            McdgMod.LOGGER.info("Loaded challenge course catalog with {} entries", catalog.entries.size());
            return Optional.of(catalog);
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load challenge course catalog from {}", path, ex);
            return Optional.empty();
        }
    }

    private static Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("mcdg").resolve(FILE_NAME);
    }

    /**
     * Snapshot for JSON serialization.
     */
    private record CatalogSnapshot(
        int version,
        Map<UUID, CatalogEntry> entries
    ) {}

    /**
     * Entry for a single course in the catalog.
     */
    public static class CatalogEntry {
        private final UUID courseId;
        private final String name;
        private final ChallengeCourseType type;
        private final BlockPos entrancePosition;
        private final BlockPos courseAnchor;
        private final Course generatedCourse;
        private final Instant discoveredAt;
        private final Map<UUID, PlayerRewardData> playerRewards;
        private final Map<UUID, PlayerCompletionData> playerCompletions;

        public CatalogEntry(UUID courseId, String name, ChallengeCourseType type, 
                          BlockPos entrancePosition, BlockPos courseAnchor, Course generatedCourse,
                          Instant discoveredAt, Map<UUID, PlayerRewardData> playerRewards,
                          Map<UUID, PlayerCompletionData> playerCompletions) {
            this.courseId = courseId;
            this.name = name;
            this.type = type;
            this.entrancePosition = entrancePosition;
            this.courseAnchor = courseAnchor;
            this.generatedCourse = generatedCourse;
            this.discoveredAt = discoveredAt;
            this.playerRewards = playerRewards;
            this.playerCompletions = playerCompletions;
        }

        public UUID courseId() { return courseId; }
        public String name() { return name; }
        public ChallengeCourseType type() { return type; }
        public BlockPos entrancePosition() { return entrancePosition; }
        public BlockPos courseAnchor() { return courseAnchor; }
        public Course generatedCourse() { return generatedCourse; }
        public Instant discoveredAt() { return discoveredAt; }
        public Map<UUID, PlayerRewardData> playerRewards() { return playerRewards; }
        public Map<UUID, PlayerCompletionData> playerCompletions() { return playerCompletions; }
    }

    /**
     * Tracks player reward claims.
     */
    private record PlayerRewardData(
        Instant claimedAt,
        Integer completionScore
    ) {}

    /**
     * Tracks player course completions.
     */
    private record PlayerCompletionData(
        Instant completedAt,
        int score
    ) {}
}