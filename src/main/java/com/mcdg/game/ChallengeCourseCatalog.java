package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.game.ChallengeCourseParameters;
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
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(PlayerCompletionData.class, new PlayerCompletionDataTypeAdapter())
        .create();
    private static final String FILE_NAME = "mcdg-challenge-course-catalog.json";
    private static final int CURRENT_VERSION = 2;

    private final Map<UUID, CatalogEntry> entries = new HashMap<>();

    public ChallengeCourseCatalog() {
    }

    /**
     * Adds or updates a course in the catalog.
     */
    public void addOrUpdateCourse(LostCourse course, Course generatedCourse, ChallengeCourseParameters parameters) {
        // Ensure the generated course uses the lost course name so scorecards and UI match
        Course namedCourse = ensureCourseName(generatedCourse, course.name());
        CatalogEntry entry = new CatalogEntry(
            course.courseId(),
            course.name(),
            course.type(),
            course.entrancePosition(),
            course.courseAnchor(),
            namedCourse,
            parameters,
            Instant.now(),
            new HashMap<>(), // playerRewards
            new HashMap<>(), // playerCompletions
            false
        );
        entries.put(course.courseId(), entry);
    }

    private static Course ensureCourseName(Course course, String name) {
        if (course == null || course.name().equals(name)) {
            return course;
        }
        return new Course(course.seed(), name, course.holes());
    }

    /**
     * Gets a course entry by ID.
     */
    public Optional<CatalogEntry> getCourse(UUID courseId) {
        return Optional.ofNullable(entries.get(courseId));
    }

    /**
     * Gets a course entry by name.
     */
    public Optional<CatalogEntry> getCourseByName(String courseName) {
        if (courseName == null || courseName.isBlank()) {
            return Optional.empty();
        }
        return entries.values().stream()
            .filter(entry -> entry.name().equalsIgnoreCase(courseName))
            .findFirst();
    }

    /**
     * Gets all discovered courses.
     */
    public List<CatalogEntry> getAllCourses() {
        return new ArrayList<>(entries.values());
    }

    /**
     * Gets the entries map for direct access (needed for repair operations).
     */
    public Map<UUID, CatalogEntry> entries() {
        return entries;
    }

    /**
     * Removes a course entry from the catalog.
     */
    public void removeCourse(UUID courseId) {
        entries.remove(courseId);
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
     * @deprecated Use {@link #recordCourseCompletion(UUID, UUID, int, String)} instead.
     */
    @Deprecated
    public void recordCourseCompletion(UUID courseId, UUID playerId, int score) {
        recordCourseCompletion(courseId, playerId, score, null);
    }

    /**
     * Records a player's completion of a course with their score and player name.
     */
    public void recordCourseCompletion(UUID courseId, UUID playerId, int score, String playerName) {
        CatalogEntry entry = entries.get(courseId);
        if (entry != null) {
            PlayerCompletionData completionData = new PlayerCompletionData(Instant.now(), score, playerName);
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
     * Gets completion entries for a course with player names.
     */
    public List<CompletionEntry> getCompletionEntries(UUID courseId) {
        CatalogEntry entry = entries.get(courseId);
        if (entry == null || entry.playerCompletions().isEmpty()) {
            return List.of();
        }

        List<CompletionEntry> completions = new ArrayList<>();
        for (Map.Entry<UUID, PlayerCompletionData> entryData : entry.playerCompletions().entrySet()) {
            PlayerCompletionData data = entryData.getValue();
            String playerName = data.playerName() != null ? data.playerName() : "Unknown Player";
            completions.add(new CompletionEntry(playerName, data.score(), data.completedAt()));
        }
        
        // Sort by score (ascending)
        completions.sort(Comparator.comparingInt(CompletionEntry::score));
        return completions;
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

            if (snapshot == null) {
                McdgMod.LOGGER.warn("Challenge course catalog snapshot is null, creating new catalog");
                return Optional.of(new ChallengeCourseCatalog());
            }

            // Handle version migration
            if (snapshot.version() > CURRENT_VERSION) {
                McdgMod.LOGGER.warn("Challenge course catalog version {} is newer than current version {}. Attempting to load with current version - data may be partially incompatible.",
                    snapshot.version(), CURRENT_VERSION);
                // Continue with load attempt rather than losing data
            }

            // Version 1 or earlier: The custom TypeAdapter will handle backward compatibility
            // for PlayerCompletionData by treating missing playerName as null
            ChallengeCourseCatalog catalog = new ChallengeCourseCatalog();
            catalog.entries.putAll(snapshot.entries());

            // Migrate existing entries whose generated course name does not match the lost course name
            boolean migrated = false;
            for (Map.Entry<UUID, CatalogEntry> entry : catalog.entries.entrySet()) {
                CatalogEntry oldEntry = entry.getValue();
                Course fixedCourse = ensureCourseName(oldEntry.generatedCourse(), oldEntry.name());
                if (fixedCourse != oldEntry.generatedCourse()) {
                    CatalogEntry migratedEntry = new CatalogEntry(
                        oldEntry.courseId(),
                        oldEntry.name(),
                        oldEntry.type(),
                        oldEntry.entrancePosition(),
                        oldEntry.courseAnchor(),
                        fixedCourse,
                        oldEntry.parameters(),
                        oldEntry.discoveredAt(),
                        oldEntry.playerRewards(),
                        oldEntry.playerCompletions(),
                        oldEntry.isPlaced()
                    );
                    entry.setValue(migratedEntry);
                    McdgMod.LOGGER.info("Migrated challenge course {} generated course name from '{}' to '{}'",
                        oldEntry.courseId(), oldEntry.generatedCourse().name(), oldEntry.name());
                    migrated = true;
                }

                // Note: PlayerCompletionData entries with null playerName will display as "Unknown Player"
                // These will be automatically populated with actual player names on the next completion
            }
            if (migrated) {
                catalog.save(server);
            }

            McdgMod.LOGGER.info("Loaded challenge course catalog (version {}) with {} entries",
                snapshot.version(), catalog.entries.size());
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
        private final ChallengeCourseParameters parameters;
        private final Instant discoveredAt;
        private final Map<UUID, PlayerRewardData> playerRewards;
        private final Map<UUID, PlayerCompletionData> playerCompletions;
        private final boolean isPlaced;

        public CatalogEntry(UUID courseId, String name, ChallengeCourseType type,
                          BlockPos entrancePosition, BlockPos courseAnchor, Course generatedCourse,
                          ChallengeCourseParameters parameters, Instant discoveredAt,
                          Map<UUID, PlayerRewardData> playerRewards,
                          Map<UUID, PlayerCompletionData> playerCompletions,
                          boolean isPlaced) {
            this.courseId = courseId;
            this.name = name;
            this.type = type;
            this.entrancePosition = entrancePosition;
            this.courseAnchor = courseAnchor;
            this.generatedCourse = generatedCourse;
            this.parameters = parameters;
            this.discoveredAt = discoveredAt;
            this.playerRewards = playerRewards;
            this.playerCompletions = playerCompletions;
            this.isPlaced = isPlaced;
        }

        public UUID courseId() { return courseId; }
        public String name() { return name; }
        public ChallengeCourseType type() { return type; }
        public BlockPos entrancePosition() { return entrancePosition; }
        public BlockPos courseAnchor() { return courseAnchor; }
        public Course generatedCourse() { return generatedCourse; }
        public ChallengeCourseParameters parameters() { return parameters; }
        public Instant discoveredAt() { return discoveredAt; }
        public Map<UUID, PlayerRewardData> playerRewards() { return playerRewards; }
        public Map<UUID, PlayerCompletionData> playerCompletions() { return playerCompletions; }
        public boolean isPlaced() { return isPlaced; }

        public CatalogEntry withPlaced(boolean placed) {
            return new CatalogEntry(
                courseId,
                name,
                type,
                entrancePosition,
                courseAnchor,
                generatedCourse,
                parameters,
                discoveredAt,
                playerRewards,
                playerCompletions,
                placed
            );
        }
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
        int score,
        String playerName
    ) {}

    /**
     * Public completion entry with player name resolved.
     */
    public record CompletionEntry(
        String playerName,
        int score,
        Instant completedAt
    ) {}

    /**
     * Marks a course as placed in the world.
     */
    public void markCourseAsPlaced(UUID courseId) {
        CatalogEntry entry = entries.get(courseId);
        if (entry != null) {
            entries.put(courseId, entry.withPlaced(true));
        }
    }

    /**
     * Custom TypeAdapter for java.time.Instant to avoid reflection issues with Java 17+ module system.
     * Serializes Instant as ISO-8601 string (e.g., "2024-01-15T10:30:00Z").
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            String value = in.nextString();
            if (value == null) {
                return null;
            }
            return Instant.parse(value);
        }
    }

    /**
     * Custom TypeAdapter for PlayerCompletionData to handle backward compatibility.
     * Handles both old format (without playerName) and new format (with playerName).
     */
    private static class PlayerCompletionDataTypeAdapter extends TypeAdapter<PlayerCompletionData> {
        @Override
        public void write(JsonWriter out, PlayerCompletionData value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            out.name("completedAt").value(value.completedAt().toString());
            out.name("score").value(value.score());
            out.name("playerName").value(value.playerName());
            out.endObject();
        }

        @Override
        public PlayerCompletionData read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            Instant completedAt = null;
            int score = 0;
            String playerName = null;

            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "completedAt":
                        String timeStr = in.nextString();
                        completedAt = timeStr != null ? Instant.parse(timeStr) : null;
                        break;
                    case "score":
                        score = in.nextInt();
                        break;
                    case "playerName":
                        playerName = in.nextString();
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
            in.endObject();

            return new PlayerCompletionData(completedAt, score, playerName);
        }
    }
}