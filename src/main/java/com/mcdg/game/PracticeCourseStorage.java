package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mcdg.McdgMod;
import com.mcdg.data.BasketPoint;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.data.TeePoint;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PracticeCourseStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-practice-course.json";
    private static final String CATALOG_FILE_NAME = "mcdg-course-catalog.json";
    private static final int MAX_CATALOG_ENTRIES = 12;
    private static final int CURRENT_SNAPSHOT_VERSION = 4;

    public boolean save(MinecraftServer server, Course course, PlacedCourseState placedCourseState) {
        PracticeCourseSnapshot snapshot = PracticeCourseSnapshot.from(course, placedCourseState);
        Path path = resolvePath(server);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(snapshot));
            return true;
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save practice course snapshot to {}", path, ex);
            return false;
        }
    }

    public Optional<LoadedPracticeCourse> load(MinecraftServer server) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(path);
            PracticeCourseSnapshot snapshot = GSON.fromJson(json, PracticeCourseSnapshot.class);
            if (snapshot == null || snapshot.course == null) {
                return Optional.empty();
            }

            RegistryKey<World> worldKey = snapshot.parseWorldKey();
            if (worldKey == null) {
                return Optional.empty();
            }

            Course course = snapshot.course.toCourse();
            PlacedCourseState placedCourseState = snapshot.toPlacedCourseState(worldKey);
            boolean legacyFormat = snapshot.isLegacyFormat();
            return Optional.of(new LoadedPracticeCourse(course, placedCourseState, legacyFormat));
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load practice course snapshot from {}", path, ex);
            return Optional.empty();
        }
    }

    public void clear(MinecraftServer server) {
        Path path = resolvePath(server);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to delete practice course snapshot {}", path, ex);
        }
    }

    public boolean saveReusable(MinecraftServer server, Course course, PlacedCourseState placedCourseState, String sourceTag, boolean compactPreferred) {
        PracticeCourseSnapshot snapshot = PracticeCourseSnapshot.from(course, placedCourseState);
        Path path = resolveCatalogPath(server);

        try {
            Files.createDirectories(path.getParent());
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            CourseCatalogEntrySnapshot entry = CourseCatalogEntrySnapshot.from(snapshot, sourceTag, compactPreferred);

            // Keep only the newest entry for identical world+seed+layout snapshots.
            String targetWorldKey = snapshot.worldKey;
            long targetSeed = snapshot.course == null ? Long.MIN_VALUE : snapshot.course.seed;
            String targetLayoutSignature = entry.layoutSignature == null
                    ? buildLayoutSignature(snapshot.course)
                    : entry.layoutSignature;
            catalog.entries.removeIf(existing -> isDuplicateReusableEntry(existing, targetWorldKey, targetSeed, targetLayoutSignature));

            catalog.entries.add(entry);
            catalog.entries.sort(Comparator.comparingLong((CourseCatalogEntrySnapshot value) -> value.createdAtMs).reversed());
            if (catalog.entries.size() > MAX_CATALOG_ENTRIES) {
                catalog.entries = new ArrayList<>(catalog.entries.subList(0, MAX_CATALOG_ENTRIES));
            }
            Files.writeString(path, GSON.toJson(catalog));
            return true;
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to save reusable course catalog to {}", path, ex);
            return false;
        }
    }

    public Optional<LoadedPracticeCourse> loadMostRecentReusable(MinecraftServer server, RegistryKey<World> preferredWorld) {
        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            if (catalog.entries == null || catalog.entries.isEmpty()) {
                return Optional.empty();
            }

            catalog.entries.sort(Comparator.comparingLong((CourseCatalogEntrySnapshot value) -> value.createdAtMs).reversed());
            for (CourseCatalogEntrySnapshot entry : catalog.entries) {
                if (entry == null || entry.snapshot == null) {
                    continue;
                }
                RegistryKey<World> worldKey = entry.snapshot.parseWorldKey();
                if (worldKey == null) {
                    continue;
                }
                if (preferredWorld != null && !preferredWorld.equals(worldKey)) {
                    continue;
                }

                Course parsedCourse = entry.snapshot.course.toCourse();
                PlacedCourseState parsedPlaced = entry.snapshot.toPlacedCourseState(worldKey);
                boolean legacyFormat = entry.snapshot.isLegacyFormat();
                return Optional.of(new LoadedPracticeCourse(parsedCourse, parsedPlaced, legacyFormat));
            }
            return Optional.empty();
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load reusable course catalog from {}", path, ex);
            return Optional.empty();
        }
    }

    public Optional<LoadedPracticeCourse> loadReusableByIndex(MinecraftServer server, int oneBasedIndex) {
        if (oneBasedIndex < 1) {
            return Optional.empty();
        }

        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            if (catalog.entries == null || catalog.entries.isEmpty()) {
                return Optional.empty();
            }

            List<CourseCatalogEntrySnapshot> sortedEntries = sortedEntries(catalog.entries);
            int zeroBasedIndex = oneBasedIndex - 1;
            if (zeroBasedIndex >= sortedEntries.size()) {
                return Optional.empty();
            }

            CourseCatalogEntrySnapshot entry = sortedEntries.get(zeroBasedIndex);
            if (entry == null || entry.snapshot == null) {
                return Optional.empty();
            }

            RegistryKey<World> worldKey = entry.snapshot.parseWorldKey();
            if (worldKey == null) {
                return Optional.empty();
            }

            Course parsedCourse = entry.snapshot.course.toCourse();
            PlacedCourseState parsedPlaced = entry.snapshot.toPlacedCourseState(worldKey);
            return Optional.of(new LoadedPracticeCourse(parsedCourse, parsedPlaced, entry.snapshot.isLegacyFormat()));
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load reusable course #{} from {}", oneBasedIndex, path, ex);
            return Optional.empty();
        }
    }

    public List<ReusableCourseEntry> listReusable(MinecraftServer server) {
        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            if (catalog.entries == null || catalog.entries.isEmpty()) {
                return List.of();
            }

            List<CourseCatalogEntrySnapshot> sortedEntries = sortedEntries(catalog.entries);
            List<ReusableCourseEntry> result = new ArrayList<>();
            int index = 1;
            for (CourseCatalogEntrySnapshot entry : sortedEntries) {
                if (entry == null || entry.snapshot == null || entry.snapshot.course == null) {
                    continue;
                }

                String worldValue = entry.snapshot.worldKey == null ? "unknown" : entry.snapshot.worldKey;
                CourseSnapshot courseSnapshot = entry.snapshot.course;
                int holes = courseSnapshot.holes == null ? 0 : courseSnapshot.holes.size();
                result.add(new ReusableCourseEntry(
                        index,
                        entry.createdAtMs,
                        entry.sourceTag == null ? "unknown" : entry.sourceTag,
                        entry.compactPreferred,
                        worldValue,
                        courseSnapshot.seed,
                        courseSnapshot.name == null ? "unnamed" : courseSnapshot.name,
                        holes
                ));
                index++;
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to list reusable courses from {}", path, ex);
            return List.of();
        }
    }

    public int pruneReusable(MinecraftServer server, int keepCount) {
        int safeKeep = Math.max(0, keepCount);
        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return 0;
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            if (catalog.entries == null || catalog.entries.isEmpty()) {
                return 0;
            }

            List<CourseCatalogEntrySnapshot> sortedEntries = sortedEntries(catalog.entries);
            int existingCount = sortedEntries.size();
            if (existingCount <= safeKeep) {
                return 0;
            }

            catalog.entries = new ArrayList<>(sortedEntries.subList(0, safeKeep));
            Files.writeString(path, GSON.toJson(catalog));
            return existingCount - safeKeep;
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to prune reusable course catalog at {}", path, ex);
            return 0;
        }
    }

    public int reusableCount(MinecraftServer server) {
        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return 0;
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            return catalog.entries == null ? 0 : catalog.entries.size();
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to read reusable course count from {}", path, ex);
            return 0;
        }
    }

    public int pruneReusableByIndices(MinecraftServer server, Set<Integer> oneBasedIndices) {
        if (oneBasedIndices == null || oneBasedIndices.isEmpty()) {
            return 0;
        }

        Path path = resolveCatalogPath(server);
        if (!Files.exists(path)) {
            return 0;
        }

        try {
            CourseCatalogSnapshot catalog = readCatalogSnapshot(path);
            if (catalog.entries == null || catalog.entries.isEmpty()) {
                return 0;
            }

            List<CourseCatalogEntrySnapshot> sortedEntries = sortedEntries(catalog.entries);
            List<CourseCatalogEntrySnapshot> keptEntries = new ArrayList<>();
            int removed = 0;
            for (int i = 0; i < sortedEntries.size(); i++) {
                int index = i + 1;
                if (oneBasedIndices.contains(index)) {
                    removed++;
                } else {
                    keptEntries.add(sortedEntries.get(i));
                }
            }

            if (removed <= 0) {
                return 0;
            }

            catalog.entries = keptEntries;
            Files.writeString(path, GSON.toJson(catalog));
            return removed;
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to prune reusable entries by indices at {}", path, ex);
            return 0;
        }
    }

    private Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(McdgMod.MOD_ID).resolve(FILE_NAME);
    }

    private Path resolveCatalogPath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(McdgMod.MOD_ID).resolve(CATALOG_FILE_NAME);
    }

    private CourseCatalogSnapshot readCatalogSnapshot(Path path) throws IOException {
        if (!Files.exists(path)) {
            return CourseCatalogSnapshot.empty();
        }

        String json = Files.readString(path);
        CourseCatalogSnapshot parsed = GSON.fromJson(json, CourseCatalogSnapshot.class);
        if (parsed == null) {
            return CourseCatalogSnapshot.empty();
        }
        if (parsed.entries == null) {
            parsed.entries = new ArrayList<>();
        }
        return parsed;
    }

    private List<CourseCatalogEntrySnapshot> sortedEntries(List<CourseCatalogEntrySnapshot> entries) {
        List<CourseCatalogEntrySnapshot> sorted = new ArrayList<>();
        for (CourseCatalogEntrySnapshot entry : entries) {
            if (entry != null) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator.comparingLong((CourseCatalogEntrySnapshot value) -> value.createdAtMs).reversed());
        return sorted;
    }

    private boolean isDuplicateReusableEntry(CourseCatalogEntrySnapshot existing, String targetWorldKey, long targetSeed, String targetLayoutSignature) {
        if (existing == null || existing.snapshot == null || existing.snapshot.course == null) {
            return false;
        }

        String existingWorldKey = existing.snapshot.worldKey;
        long existingSeed = existing.snapshot.course.seed;
        String existingLayoutSignature = existing.layoutSignature;
        if (existingLayoutSignature == null || existingLayoutSignature.isBlank()) {
            existingLayoutSignature = buildLayoutSignature(existing.snapshot.course);
        }

        return Objects.equals(existingWorldKey, targetWorldKey)
                && existingSeed == targetSeed
                && Objects.equals(existingLayoutSignature, targetLayoutSignature);
    }

    private static String buildLayoutSignature(CourseSnapshot course) {
        if (course == null || course.holes == null || course.holes.isEmpty()) {
            return "empty";
        }

        StringBuilder signature = new StringBuilder();
        signature.append("holes=").append(course.holes.size());
        for (HoleSnapshot hole : course.holes) {
            if (hole == null || hole.tee == null || hole.basket == null) {
                continue;
            }
            signature
                    .append("|h").append(hole.index)
                    .append(":p").append(hole.par)
                    .append(":d").append(hole.distanceFeet)
                    .append(":t").append(hole.tee.x()).append(',').append(hole.tee.y()).append(',').append(hole.tee.z())
                    .append(":b").append(hole.basket.x()).append(',').append(hole.basket.y()).append(',').append(hole.basket.z())
                    .append(":bh").append(hole.basket.basketHeight())
                    .append(":sig=").append(hole.signatureType == null ? "NONE" : hole.signatureType);
        }
        return signature.toString();
    }

    public record LoadedPracticeCourse(Course course, PlacedCourseState placedCourseState, boolean legacyFormat) {
    }

    public record ReusableCourseEntry(
            int index,
            long createdAtMs,
            String sourceTag,
            boolean compactPreferred,
            String worldKey,
            long seed,
            String name,
            int holeCount
    ) {
    }

    private static final class PracticeCourseSnapshot {
        private int snapshotVersion;
        private String worldKey;
        private CourseSnapshot course;
        private List<BlockEntrySnapshot> originalBlocks;
        private List<IndexedPosSnapshot> placedTees;
        private List<IndexedPosSnapshot> placedBaskets;
        private List<IndexedPosSnapshot> placedAlternateAnchors;

        private static PracticeCourseSnapshot from(Course course, PlacedCourseState placedCourseState) {
            PracticeCourseSnapshot snapshot = new PracticeCourseSnapshot();
            snapshot.snapshotVersion = CURRENT_SNAPSHOT_VERSION;
            snapshot.worldKey = placedCourseState.worldKey().getValue().toString();
            snapshot.course = CourseSnapshot.from(course);
            snapshot.originalBlocks = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> entry : placedCourseState.originalBlocks().entrySet()) {
                snapshot.originalBlocks.add(BlockEntrySnapshot.from(entry.getKey(), entry.getValue()));
            }
            snapshot.placedTees = IndexedPosSnapshot.fromMap(placedCourseState.holeTees());
            snapshot.placedBaskets = IndexedPosSnapshot.fromMap(placedCourseState.holeBaskets());
            snapshot.placedAlternateAnchors = IndexedPosSnapshot.fromMap(placedCourseState.holeAlternateAnchors());
            return snapshot;
        }

        private RegistryKey<World> parseWorldKey() {
            Identifier identifier = Identifier.tryParse(worldKey);
            if (identifier == null) {
                return null;
            }
            return RegistryKey.of(RegistryKeys.WORLD, identifier);
        }

        private PlacedCourseState toPlacedCourseState(RegistryKey<World> worldKey) {
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            if (originalBlocks != null) {
                for (BlockEntrySnapshot entry : originalBlocks) {
                    BlockState state = entry.decodeBlockState();
                    if (state != null) {
                        blocks.put(entry.pos.toBlockPos(), state);
                    }
                }
            }
            Map<Integer, BlockPos> tees = IndexedPosSnapshot.toMap(placedTees);
            Map<Integer, BlockPos> baskets = IndexedPosSnapshot.toMap(placedBaskets);
            Map<Integer, BlockPos> alternateAnchors = IndexedPosSnapshot.toMap(placedAlternateAnchors);

            if (tees.isEmpty()) {
                tees = course.toHoleTees();
            }
            if (baskets.isEmpty()) {
                baskets = course.toHoleBaskets();
            }

            return new PlacedCourseState(worldKey, blocks, tees, baskets, alternateAnchors);
        }

        private boolean isLegacyFormat() {
            return snapshotVersion < CURRENT_SNAPSHOT_VERSION
                    || placedTees == null
                    || placedBaskets == null
                    || placedAlternateAnchors == null;
        }
    }

    private record IndexedPosSnapshot(int hole, BlockPosSnapshot pos) {
        private static List<IndexedPosSnapshot> fromMap(Map<Integer, BlockPos> source) {
            List<IndexedPosSnapshot> list = new ArrayList<>();
            for (Map.Entry<Integer, BlockPos> entry : source.entrySet()) {
                list.add(new IndexedPosSnapshot(entry.getKey(), BlockPosSnapshot.from(entry.getValue().getX(), entry.getValue().getY(), entry.getValue().getZ())));
            }
            return list;
        }

        private static Map<Integer, BlockPos> toMap(List<IndexedPosSnapshot> source) {
            Map<Integer, BlockPos> map = new HashMap<>();
            if (source == null) {
                return map;
            }
            for (IndexedPosSnapshot entry : source) {
                if (entry != null && entry.pos != null) {
                    map.put(entry.hole, entry.pos.toBlockPos());
                }
            }
            return map;
        }
    }

    private record CourseSnapshot(long seed, String name, List<HoleSnapshot> holes) {
        private static CourseSnapshot from(Course course) {
            List<HoleSnapshot> holeSnapshots = new ArrayList<>();
            for (Hole hole : course.holes()) {
                holeSnapshots.add(HoleSnapshot.from(hole));
            }
            return new CourseSnapshot(course.seed(), course.name(), holeSnapshots);
        }

        private Course toCourse() {
            List<Hole> parsedHoles = new ArrayList<>();
            for (HoleSnapshot holeSnapshot : holes) {
                parsedHoles.add(holeSnapshot.toHole());
            }
            return new Course(seed, name, normalizeSignatureHoles(seed, parsedHoles));
        }

        private static List<Hole> normalizeSignatureHoles(long seed, List<Hole> holes) {
            if (holes.isEmpty()) {
                return holes;
            }

            List<Hole> normalized = new ArrayList<>(holes);
            int signatureCount = 0;
            for (int i = 0; i < normalized.size(); i++) {
                if (normalized.get(i).isSignature()) {
                    signatureCount++;
                }
            }

            if (signatureCount == 1) {
                return normalized;
            }

            for (int i = 0; i < normalized.size(); i++) {
                Hole hole = normalized.get(i);
                if (hole.isSignature()) {
                    normalized.set(i, new Hole(
                            hole.index(),
                            hole.par(),
                            hole.distanceFeet(),
                            hole.tee(),
                            hole.basket(),
                            hole.fairwaySegments(),
                            SignatureHoleType.NONE
                    ));
                }
            }

            int signatureIndex = new Random(seed).nextInt(normalized.size());
            Hole selected = normalized.get(signatureIndex);
            normalized.set(signatureIndex, new Hole(
                    selected.index(),
                    selected.par(),
                    selected.distanceFeet(),
                    selected.tee(),
                    selected.basket(),
                    selected.fairwaySegments(),
                    SignatureHoleType.ISLAND_GREEN
            ));
            return normalized;
        }

        private Map<Integer, BlockPos> toHoleTees() {
            Map<Integer, BlockPos> tees = new HashMap<>();
            for (HoleSnapshot holeSnapshot : holes) {
                tees.put(holeSnapshot.index, holeSnapshot.tee.toBlockPos());
            }
            return tees;
        }

        private Map<Integer, BlockPos> toHoleBaskets() {
            Map<Integer, BlockPos> baskets = new HashMap<>();
            for (HoleSnapshot holeSnapshot : holes) {
                baskets.put(holeSnapshot.index, holeSnapshot.basket.toBlockPos());
            }
            return baskets;
        }
    }

    private static final class HoleSnapshot {
        private int index;
        private int par;
        private int distanceFeet;
        private BlockPosSnapshot tee;
        private BasketSnapshot basket;
        private List<FairwaySegmentSnapshot> fairwaySegments;
        private String signatureType;

        private static HoleSnapshot from(Hole hole) {
            HoleSnapshot snapshot = new HoleSnapshot();
            snapshot.index = hole.index();
            snapshot.par = hole.par();
            snapshot.distanceFeet = hole.distanceFeet();
            snapshot.tee = BlockPosSnapshot.from(hole.tee().x(), hole.tee().y(), hole.tee().z());
            snapshot.basket = BasketSnapshot.from(hole.basket());
            snapshot.fairwaySegments = new ArrayList<>();
            snapshot.signatureType = hole.signatureType().name();
            for (FairwaySegment segment : hole.fairwaySegments()) {
                snapshot.fairwaySegments.add(FairwaySegmentSnapshot.from(segment));
            }
            return snapshot;
        }

        private Hole toHole() {
            List<FairwaySegment> segments = new ArrayList<>();
            if (fairwaySegments != null) {
                for (FairwaySegmentSnapshot snapshot : fairwaySegments) {
                    segments.add(snapshot.toFairwaySegment());
                }
            }
            SignatureHoleType parsedSignatureType = SignatureHoleType.NONE;
            if (signatureType != null && !signatureType.isBlank()) {
                try {
                    parsedSignatureType = SignatureHoleType.valueOf(signatureType);
                } catch (IllegalArgumentException ex) {
                    McdgMod.LOGGER.warn("Unknown signature hole type '{}' in snapshot, defaulting to NONE", signatureType);
                    parsedSignatureType = SignatureHoleType.NONE;
                }
            }
            return new Hole(index, par, distanceFeet, tee.toTeePoint(), basket.toBasketPoint(), segments, parsedSignatureType);
        }
    }

    private record FairwaySegmentSnapshot(int startX, int startZ, int endX, int endZ, int width) {
        private static FairwaySegmentSnapshot from(FairwaySegment segment) {
            return new FairwaySegmentSnapshot(segment.startX(), segment.startZ(), segment.endX(), segment.endZ(), segment.width());
        }

        private FairwaySegment toFairwaySegment() {
            return new FairwaySegment(startX, startZ, endX, endZ, width);
        }
    }

    private record BlockPosSnapshot(int x, int y, int z) {
        private static BlockPosSnapshot from(int x, int y, int z) {
            return new BlockPosSnapshot(x, y, z);
        }

        private BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        private TeePoint toTeePoint() {
            return new TeePoint(x, y, z);
        }
    }

    private record BasketSnapshot(int x, int y, int z, int basketHeight) {
        private static BasketSnapshot from(BasketPoint basketPoint) {
            return new BasketSnapshot(basketPoint.x(), basketPoint.y(), basketPoint.z(), basketPoint.basketHeight());
        }

        private BasketPoint toBasketPoint() {
            return new BasketPoint(x, y, z, basketHeight);
        }

        private BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    private static final class BlockEntrySnapshot {
        private BlockPosSnapshot pos;
        private String blockStateJson;

        private static BlockEntrySnapshot from(BlockPos pos, BlockState state) {
            BlockEntrySnapshot snapshot = new BlockEntrySnapshot();
            snapshot.pos = BlockPosSnapshot.from(pos.getX(), pos.getY(), pos.getZ());
            JsonElement encoded = BlockState.CODEC.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
            snapshot.blockStateJson = GSON.toJson(encoded);
            return snapshot;
        }

        private BlockState decodeBlockState() {
            try {
                JsonElement parsed = JsonParser.parseString(blockStateJson);
                return BlockState.CODEC.parse(JsonOps.INSTANCE, parsed).result().orElse(null);
            } catch (RuntimeException ex) {
                McdgMod.LOGGER.warn("Failed to decode block state from snapshot JSON '{}': {}", blockStateJson, ex.getMessage());
                return null;
            }
        }
    }

    private static final class CourseCatalogSnapshot {
        @SuppressWarnings("unused")
        private int version;
        private List<CourseCatalogEntrySnapshot> entries;

        private static CourseCatalogSnapshot empty() {
            CourseCatalogSnapshot snapshot = new CourseCatalogSnapshot();
            snapshot.version = 1;
            snapshot.entries = new ArrayList<>();
            return snapshot;
        }
    }

    private static final class CourseCatalogEntrySnapshot {
        private long createdAtMs;
        private String sourceTag;
        private boolean compactPreferred;
        private String layoutSignature;
        private PracticeCourseSnapshot snapshot;

        private static CourseCatalogEntrySnapshot from(PracticeCourseSnapshot snapshot, String sourceTag, boolean compactPreferred) {
            CourseCatalogEntrySnapshot entry = new CourseCatalogEntrySnapshot();
            entry.createdAtMs = System.currentTimeMillis();
            entry.sourceTag = sourceTag == null ? "unknown" : sourceTag;
            entry.compactPreferred = compactPreferred;
            entry.layoutSignature = buildLayoutSignature(snapshot.course);
            entry.snapshot = snapshot;
            return entry;
        }
    }
}
