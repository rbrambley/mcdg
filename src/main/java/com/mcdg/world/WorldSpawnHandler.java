package com.mcdg.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.McdgMod;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.LostCourse;
import com.mcdg.game.LostCourseStorage;
import com.mcdg.game.PracticeCourseStorage;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Auto-builds the resort on fresh worlds and updates world spawn to the lobby.
 */
public final class WorldSpawnHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "mcdg";
    private static final String RESORT_FILE = "resort-data.json";
    private static final int SURROUND_COURSE_COUNT = 3;

    private WorldSpawnHandler() {}

    public static void onServerStarted(MinecraftServer server, AutoCourseService autoCourseService, PracticeCourseStorage practiceCourseStorage) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            McdgMod.LOGGER.warn("Overworld not available, cannot auto-build resort.");
            return;
        }

        BlockPos spawnPos = overworld.getSpawnPos();

        // Re-establish the starter-chest dispenser for resorts built on previous runs
        // (existing worlds never re-run placeResort, so chestPos would otherwise be null).
        ResortData existing = loadResortData(server);
        if (existing != null) {
            BlockPos center = new BlockPos(existing.centerX, existing.centerY, existing.centerZ);
            ResortBuilder.registerStarterChestFromCenter(overworld, center);

            // Restore resort waypoint from persisted data so teleport works after server restart
            BlockPos surfaceCenter = SurfaceResolver.resolveSurfacePos(overworld, center.getX(), center.getZ());
            BlockPos fountainCenter = new BlockPos(center.getX(), surfaceCenter.getY() + 1, center.getZ());
            ResortWaypointManager.setResortWaypoint(fountainCenter, existing.dimension);

            // If resort exists but courses were not completed, queue them now
            if (!existing.coursesBuilt) {
                McdgMod.LOGGER.info("Resort exists but courses not built. Queuing surround courses now.");
                ResortCourseBuilder.queueSurroundCourses(overworld, center, autoCourseService, practiceCourseStorage, server);
            } else {
                McdgMod.LOGGER.info("Resort and courses already built, skipping auto-build.");
            }
        } else if (isFreshWorld(server)) {
            File resortFile = getResortFile(server);
            McdgMod.LOGGER.info("Auto-building resort at world spawn ({}, {}, {})",
                    spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

            HashMap<BlockPos, BlockState> originalBlocks = new HashMap<>();
            HashSet<BlockPos> protectedPositions = new HashSet<>();

            ResortBuilder.placeResort(overworld, spawnPos, originalBlocks, protectedPositions);

            BlockPos surfaceCenter = SurfaceResolver.resolveSurfacePos(overworld, spawnPos.getX(), spawnPos.getZ());
            int baseY = surfaceCenter.getY();

            // Set spawn inside the lobby at floor level (baseY).
            // SurfaceResolver cannot be used here because the building roof is now the
            // highest solid block and resolveSurfacePos would return the roof position.
            BlockPos lobbySpawn = new BlockPos(spawnPos.getX() + 23, baseY, spawnPos.getZ());
            overworld.setSpawnPos(lobbySpawn, 0.0f);

            saveResortData(resortFile, spawnPos, overworld.getRegistryKey().getValue().toString());

            McdgMod.LOGGER.info("Resort auto-built. World spawn set to lobby at ({}, {}, {}).",
                    lobbySpawn.getX(), lobbySpawn.getY(), lobbySpawn.getZ());

            // Queue surround courses for background building (non-blocking)
            ResortCourseBuilder.queueSurroundCourses(overworld, spawnPos, autoCourseService, practiceCourseStorage, server);
        } else {
            McdgMod.LOGGER.info("World is not fresh, skipping resort auto-build.");
        }

        // Ensure lost course entrances are available in this world. If they have already
        // been generated and saved, just register them so discovery continues to work
        // across restarts. Otherwise, generate them now (including for pre-existing worlds).
        loadOrPlaceLostCourses(overworld, server, spawnPos);
    }

    private static void loadOrPlaceLostCourses(ServerWorld overworld, MinecraftServer server, BlockPos spawnPos) {
        var loaded = LostCourseStorage.load(server);
        if (loaded.isPresent()) {
            for (LostCourse course : loaded.get()) {
                ChallengeCourseManager.registerLostCourse(course);
            }
        } else {
            LostCoursePlacement.placeLostCourseEntrances(overworld, spawnPos);
            LostCourseStorage.save(server, ChallengeCourseManager.getAllLostCourses());
        }
    }

    private static boolean isFreshWorld(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        File playerDataDir = worldRoot.resolve("playerdata").toFile();
        if (playerDataDir.exists() && playerDataDir.isDirectory()) {
            File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
            return files == null || files.length == 0;
        }
        return true;
    }

    private static File getResortFile(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        File mcdgDir = worldRoot.resolve(DATA_DIR).toFile();
        if (!mcdgDir.exists()) {
            mcdgDir.mkdirs();
        }
        return new File(mcdgDir, RESORT_FILE);
    }

    private static void saveResortData(File file, BlockPos center, String dimension) {
        ResortData data = new ResortData(center.getX(), center.getY(), center.getZ(), dimension, System.currentTimeMillis(), false);
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            McdgMod.LOGGER.error("Failed to save resort data", e);
        }
    }

    public static ResortData loadResortData(MinecraftServer server) {
        File file = getResortFile(server);
        if (!file.exists()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, ResortData.class);
        } catch (IOException e) {
            McdgMod.LOGGER.error("Failed to load resort data", e);
            return null;
        }
    }

    public static void markCoursesBuilt(MinecraftServer server) {
        ResortData data = loadResortData(server);
        if (data == null) {
            McdgMod.LOGGER.warn("Cannot mark courses built: resort data not found");
            return;
        }
        data.coursesBuilt = true;
        File file = getResortFile(server);
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
            McdgMod.LOGGER.info("Marked resort courses as built in resort-data.json");
        } catch (IOException e) {
            McdgMod.LOGGER.error("Failed to update resort data", e);
        }
    }
}
