package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent storage for lost course entrance metadata and placed challenge course states.
 * This allows challenge courses to be discovered and resumed after server restarts.
 */
public final class LostCourseStorage {
    private static final String FILE_NAME = "lost-courses.nbt";
    private static final Map<UUID, PlacedCourseState> PLACED_STATES = new ConcurrentHashMap<>();

    private LostCourseStorage() {
    }

    public static void save(MinecraftServer server, List<LostCourse> courses) {
        save(server, courses, PLACED_STATES);
    }

    private static void save(MinecraftServer server, List<LostCourse> courses, Map<UUID, PlacedCourseState> placedStates) {
        Path path = resolvePath(server);
        try {
            Files.createDirectories(path.getParent());
            NbtCompound root = new NbtCompound();
            NbtList list = new NbtList();
            for (LostCourse course : courses) {
                list.add(writeCourse(course));
            }
            root.put("courses", list);

            NbtCompound placedStatesTag = new NbtCompound();
            for (Map.Entry<UUID, PlacedCourseState> entry : placedStates.entrySet()) {
                placedStatesTag.put(entry.getKey().toString(), writePlacedState(entry.getValue()));
            }
            root.put("placedStates", placedStatesTag);

            NbtIo.write(root, path);
            McdgMod.LOGGER.info("Saved {} lost course(s) and {} placed state(s) to {}", courses.size(), placedStates.size(), path);
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save lost course storage to {}", path, ex);
        }
    }

    public static Optional<List<LostCourse>> load(MinecraftServer server) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            PLACED_STATES.clear();
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path);
             DataInputStream dataIn = new DataInputStream(in)) {
            NbtCompound root = NbtIo.readCompound(dataIn);
            NbtList list = root.getList("courses", NbtElement.COMPOUND_TYPE);
            List<LostCourse> courses = new ArrayList<>(list.size());
            for (NbtElement element : list) {
                if (element instanceof NbtCompound compound) {
                    LostCourse course = readCourse(compound);
                    if (course != null) {
                        courses.add(course);
                    }
                }
            }

            PLACED_STATES.clear();
            NbtCompound placedStatesTag = root.getCompound("placedStates");
            for (String key : placedStatesTag.getKeys()) {
                try {
                    UUID courseId = UUID.fromString(key);
                    PlacedCourseState placed = readPlacedState(placedStatesTag.getCompound(key));
                    if (placed != null) {
                        PLACED_STATES.put(courseId, placed);
                    }
                } catch (Exception ex) {
                    McdgMod.LOGGER.error("Failed to read placed state for {}", key, ex);
                }
            }

            McdgMod.LOGGER.info("Loaded {} lost course(s) and {} placed state(s) from {}", courses.size(), PLACED_STATES.size(), path);
            return Optional.of(courses);
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to load lost course storage from {}", path, ex);
            PLACED_STATES.clear();
            return Optional.empty();
        }
    }

    /**
     * Saves the placed state for a specific challenge course.
     */
    public static void savePlacedState(MinecraftServer server, UUID courseId, PlacedCourseState placedState) {
        if (placedState == null) {
            PLACED_STATES.remove(courseId);
        } else {
            PLACED_STATES.put(courseId, placedState);
        }
        save(server, ChallengeCourseManager.getAllLostCourses(), PLACED_STATES);
    }

    /**
     * Loads the placed state for a specific challenge course.
     */
    public static Optional<PlacedCourseState> loadPlacedState(MinecraftServer server, UUID courseId) {
        if (PLACED_STATES.isEmpty()) {
            load(server);
        }
        return Optional.ofNullable(PLACED_STATES.get(courseId));
    }

    /**
     * Clears the placed state for a specific challenge course.
     */
    public static void clearPlacedState(MinecraftServer server, UUID courseId) {
        if (PLACED_STATES.remove(courseId) != null) {
            save(server, ChallengeCourseManager.getAllLostCourses(), PLACED_STATES);
        }
    }

    public static boolean exists(MinecraftServer server) {
        return Files.exists(resolvePath(server));
    }

    private static Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("mcdg").resolve(FILE_NAME);
    }

    private static NbtCompound writeCourse(LostCourse course) {
        NbtCompound compound = new NbtCompound();
        compound.putString("courseId", course.courseId().toString());
        compound.putString("name", course.name());
        compound.putInt("entranceX", course.entrancePosition().getX());
        compound.putInt("entranceY", course.entrancePosition().getY());
        compound.putInt("entranceZ", course.entrancePosition().getZ());
        compound.putInt("anchorX", course.courseAnchor().getX());
        compound.putInt("anchorY", course.courseAnchor().getY());
        compound.putInt("anchorZ", course.courseAnchor().getZ());
        compound.putString("type", course.type().name());
        compound.putBoolean("discovered", course.isDiscovered());

        NbtList rewards = new NbtList();
        for (ItemStack stack : course.rewards()) {
            if (stack.isEmpty()) {
                continue;
            }
            NbtCompound rewardCompound = new NbtCompound();
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            rewardCompound.putString("id", itemId.toString());
            rewardCompound.putInt("count", stack.getCount());
            rewards.add(rewardCompound);
        }
        compound.put("rewards", rewards);
        return compound;
    }

    private static LostCourse readCourse(NbtCompound compound) {
        try {
            UUID courseId = UUID.fromString(compound.getString("courseId"));
            String name = compound.getString("name");
            BlockPos entrance = new BlockPos(
                    compound.getInt("entranceX"),
                    compound.getInt("entranceY"),
                    compound.getInt("entranceZ")
            );
            BlockPos anchor = new BlockPos(
                    compound.getInt("anchorX"),
                    compound.getInt("anchorY"),
                    compound.getInt("anchorZ")
            );
            ChallengeCourseType type = ChallengeCourseType.valueOf(compound.getString("type"));
            boolean discovered = compound.getBoolean("discovered");

            List<ItemStack> rewards = new ArrayList<>();
            NbtList rewardList = compound.getList("rewards", NbtElement.COMPOUND_TYPE);
            for (NbtElement element : rewardList) {
                if (element instanceof NbtCompound rewardCompound) {
                    Identifier itemId = Identifier.tryParse(rewardCompound.getString("id"));
                    if (itemId == null) {
                        continue;
                    }
                    Item item = Registries.ITEM.get(itemId);
                    int count = rewardCompound.getInt("count");
                    if (count > 0) {
                        rewards.add(new ItemStack(item, count));
                    }
                }
            }

            return new LostCourse(courseId, name, entrance, anchor, rewards, type, discovered);
        } catch (Exception ex) {
            McdgMod.LOGGER.error("Failed to read lost course entry", ex);
            return null;
        }
    }

    private static NbtCompound writePlacedState(PlacedCourseState placed) {
        NbtCompound compound = new NbtCompound();
        compound.putString("world", placed.worldKey().getValue().toString());

        NbtList originalBlocks = new NbtList();
        for (Map.Entry<BlockPos, BlockState> entry : placed.originalBlocks().entrySet()) {
            NbtCompound blockCompound = new NbtCompound();
            blockCompound.putInt("x", entry.getKey().getX());
            blockCompound.putInt("y", entry.getKey().getY());
            blockCompound.putInt("z", entry.getKey().getZ());
            blockCompound.put("state", NbtHelper.fromBlockState(entry.getValue()));
            originalBlocks.add(blockCompound);
        }
        compound.put("originalBlocks", originalBlocks);

        compound.put("holeTees", writePosMap(placed.holeTees()));
        compound.put("holeBaskets", writePosMap(placed.holeBaskets()));
        compound.put("holeAlternates", writePosMap(placed.holeAlternateAnchors()));
        compound.put("effectivePars", writeIntMap(placed.effectiveHolePars()));
        return compound;
    }

    private static PlacedCourseState readPlacedState(NbtCompound compound) {
        try {
            Identifier worldId = Identifier.tryParse(compound.getString("world"));
            if (worldId == null) {
                return null;
            }
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKey.ofRegistry(Identifier.of("minecraft", "dimension")), worldId);

            Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
            NbtList originalBlocksList = compound.getList("originalBlocks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < originalBlocksList.size(); i++) {
                NbtCompound blockCompound = originalBlocksList.getCompound(i);
                BlockPos pos = new BlockPos(blockCompound.getInt("x"), blockCompound.getInt("y"), blockCompound.getInt("z"));
                BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), blockCompound.getCompound("state"));
                originalBlocks.put(pos, state);
            }

            Map<Integer, BlockPos> holeTees = readPosMap(compound.getList("holeTees", NbtElement.COMPOUND_TYPE));
            Map<Integer, BlockPos> holeBaskets = readPosMap(compound.getList("holeBaskets", NbtElement.COMPOUND_TYPE));
            Map<Integer, BlockPos> holeAlternates = readPosMap(compound.getList("holeAlternates", NbtElement.COMPOUND_TYPE));
            Map<Integer, Integer> effectivePars = readIntMap(compound.getList("effectivePars", NbtElement.COMPOUND_TYPE));

            return new PlacedCourseState(worldKey, originalBlocks, holeTees, holeBaskets, holeAlternates, effectivePars);
        } catch (Exception ex) {
            McdgMod.LOGGER.error("Failed to read placed course state", ex);
            return null;
        }
    }

    private static NbtList writePosMap(Map<Integer, BlockPos> map) {
        NbtList list = new NbtList();
        for (Map.Entry<Integer, BlockPos> entry : map.entrySet()) {
            NbtCompound compound = new NbtCompound();
            compound.putInt("hole", entry.getKey());
            BlockPos pos = entry.getValue();
            compound.putInt("x", pos.getX());
            compound.putInt("y", pos.getY());
            compound.putInt("z", pos.getZ());
            list.add(compound);
        }
        return list;
    }

    private static Map<Integer, BlockPos> readPosMap(NbtList list) {
        Map<Integer, BlockPos> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound compound = list.getCompound(i);
            map.put(compound.getInt("hole"), new BlockPos(compound.getInt("x"), compound.getInt("y"), compound.getInt("z")));
        }
        return map;
    }

    private static NbtList writeIntMap(Map<Integer, Integer> map) {
        NbtList list = new NbtList();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            NbtCompound compound = new NbtCompound();
            compound.putInt("hole", entry.getKey());
            compound.putInt("value", entry.getValue());
            list.add(compound);
        }
        return list;
    }

    private static Map<Integer, Integer> readIntMap(NbtList list) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound compound = list.getCompound(i);
            map.put(compound.getInt("hole"), compound.getInt("value"));
        }
        return map;
    }
}
