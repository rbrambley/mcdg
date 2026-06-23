package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One-time migration for removed Elytra Disc items.
 * Scans player data files on server start and replaces any remaining
 * mcdg:elytra_disc or mcdg:elytra_disc_netherite stacks with mcdg:training_disc.
 */
public final class ElytraDiscMigration {

    private static final Set<String> ELYTRA_IDS = Set.of(
            "mcdg:elytra_disc",
            "mcdg:elytra_disc_netherite"
    );
    private static final String TRAINING_DISC_ID = "mcdg:training_disc";
    private static final String MIGRATION_MARKER = "mcdg_elytra_migration_done";

    private ElytraDiscMigration() {
        // Utility class
    }

    /**
     * Run the migration once per world. Converts Elytra Disc items found in player data
     * files into Training Discs so that removing the item registration does not leave
     * players with invalid/unknown items.
     */
    public static void run(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT);
        Path playerDataDir = worldRoot.resolve("playerdata");
        Path marker = worldRoot.resolve(MIGRATION_MARKER);

        if (Files.exists(marker)) {
            McdgMod.LOGGER.info("Elytra Disc migration already completed for this world");
            return;
        }

        if (!Files.exists(playerDataDir)) {
            McdgMod.LOGGER.info("No playerdata directory found; skipping Elytra Disc migration");
            return;
        }

        McdgMod.LOGGER.info("Starting Elytra Disc migration | playerDataDir={}", playerDataDir);
        int converted = 0;

        try (Stream<Path> files = Files.list(playerDataDir)) {
            for (Path file : files.toList()) {
                if (!file.toString().endsWith(".dat")) {
                    continue;
                }
                try {
                    NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
                    if (root == null) {
                        continue;
                    }
                    int before = converted;
                    scanAndConvert(root);
                    if (converted > before) {
                        NbtIo.writeCompressed(root, file);
                        McdgMod.LOGGER.info("Migrated Elytra Disc items in player data | file={} converted={}",
                                file.getFileName(), converted - before);
                    }
                } catch (IOException e) {
                    McdgMod.LOGGER.error("Failed to migrate Elytra Disc items in {} | {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            McdgMod.LOGGER.error("Failed to list player data files for Elytra Disc migration | {}", e.getMessage());
        }

        try {
            Files.createFile(marker);
        } catch (IOException e) {
            McdgMod.LOGGER.error("Failed to write Elytra Disc migration marker | {}", e.getMessage());
        }

        McdgMod.LOGGER.info("Elytra Disc migration complete | totalConverted={}", converted);
    }

    /**
     * Recursively scan an NBT element, replacing any item compounds whose id matches
     * a removed Elytra Disc variant with a Training Disc. Incompatible components/tags
     * are stripped because the new item type does not use them.
     */
    private static void scanAndConvert(NbtElement element) {
        if (element instanceof NbtCompound compound) {
            if (compound.contains("id", NbtElement.STRING_TYPE) && compound.contains("count")) {
                String id = compound.getString("id");
                if (ELYTRA_IDS.contains(id)) {
                    compound.putString("id", TRAINING_DISC_ID);
                    compound.remove("components");
                    compound.remove("tag");
                }
            }
            for (String key : compound.getKeys()) {
                NbtElement child = compound.get(key);
                if (child != null) {
                    scanAndConvert(child);
                }
            }
        } else if (element instanceof NbtList list) {
            for (int i = 0; i < list.size(); i++) {
                scanAndConvert(list.get(i));
            }
        }
    }
}
