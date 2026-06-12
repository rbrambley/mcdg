package com.mcdg.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.McdgMod;
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

    private WorldSpawnHandler() {}

    public static void onServerStarted(MinecraftServer server) {
        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            McdgMod.LOGGER.warn("Overworld not available, cannot auto-build resort.");
            return;
        }

        if (!isFreshWorld(server)) {
            McdgMod.LOGGER.info("World is not fresh, skipping resort auto-build.");
            return;
        }

        File resortFile = getResortFile(server);
        if (resortFile.exists()) {
            McdgMod.LOGGER.info("Resort already built, skipping auto-build.");
            return;
        }

        BlockPos spawnPos = overworld.getSpawnPos();
        McdgMod.LOGGER.info("Auto-building resort at world spawn ({}, {}, {})",
                spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

        HashMap<BlockPos, BlockState> originalBlocks = new HashMap<>();
        HashSet<BlockPos> protectedPositions = new HashSet<>();

        ResortBuilder.placeResort(overworld, spawnPos, originalBlocks, protectedPositions);

        BlockPos surfaceCenter = SurfaceResolver.resolveSurfacePos(overworld, spawnPos.getX(), spawnPos.getZ());
        BlockPos lobbyPos = new BlockPos(spawnPos.getX(), surfaceCenter.getY(), spawnPos.getZ()).east(23);
        BlockPos lobbySurface = SurfaceResolver.resolveSurfacePos(overworld, lobbyPos.getX(), lobbyPos.getZ());
        overworld.setSpawnPos(lobbySurface, 0.0f);

        saveResortData(resortFile, spawnPos, overworld.getRegistryKey().getValue().toString());

        McdgMod.LOGGER.info("Resort auto-built. World spawn set to lobby at ({}, {}, {}).",
                lobbySurface.getX(), lobbySurface.getY(), lobbySurface.getZ());
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
        ResortData data = new ResortData(center.getX(), center.getY(), center.getZ(), dimension, System.currentTimeMillis());
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
}
