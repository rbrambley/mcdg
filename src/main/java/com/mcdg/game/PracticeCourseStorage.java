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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final int CURRENT_SNAPSHOT_VERSION = 2;

    public void save(MinecraftServer server, Course course, PlacedCourseState placedCourseState) {
        PracticeCourseSnapshot snapshot = PracticeCourseSnapshot.from(course, placedCourseState);
        Path path = resolvePath(server);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(snapshot));
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save practice course snapshot to {}", path, ex);
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

    private Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(McdgMod.MOD_ID).resolve(FILE_NAME);
    }

    public record LoadedPracticeCourse(Course course, PlacedCourseState placedCourseState, boolean legacyFormat) {
    }

    private static final class PracticeCourseSnapshot {
        private int snapshotVersion;
        private String worldKey;
        private CourseSnapshot course;
        private List<BlockEntrySnapshot> originalBlocks;
        private List<IndexedPosSnapshot> placedTees;
        private List<IndexedPosSnapshot> placedBaskets;

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

            if (tees.isEmpty()) {
                tees = course.toHoleTees();
            }
            if (baskets.isEmpty()) {
                baskets = course.toHoleBaskets();
            }

            return new PlacedCourseState(worldKey, blocks, tees, baskets);
        }

        private boolean isLegacyFormat() {
            return snapshotVersion < CURRENT_SNAPSHOT_VERSION || placedTees == null || placedBaskets == null;
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
            return new Course(seed, name, parsedHoles);
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

        private static HoleSnapshot from(Hole hole) {
            HoleSnapshot snapshot = new HoleSnapshot();
            snapshot.index = hole.index();
            snapshot.par = hole.par();
            snapshot.distanceFeet = hole.distanceFeet();
            snapshot.tee = BlockPosSnapshot.from(hole.tee().x(), hole.tee().y(), hole.tee().z());
            snapshot.basket = BasketSnapshot.from(hole.basket());
            snapshot.fairwaySegments = new ArrayList<>();
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
            return new Hole(index, par, distanceFeet, tee.toTeePoint(), basket.toBasketPoint(), segments, SignatureHoleType.NONE);
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
                return null;
            }
        }
    }
}
