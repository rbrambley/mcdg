package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mcdg.McdgMod;
import com.mcdg.net.SkillsStatusSync;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/**
 * Manages player skill progression and persistence.
 * Tracks throws, rounds, aces, crafting tiers, and unlocks skill abilities.
 */
public final class PlayerSkillManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg-player-skills.json";
    private static final int CURRENT_VERSION = 1;

    private static final Map<UUID, PlayerSkillData> playerData = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> dirtyPlayers = new ConcurrentHashMap<>();

    private static Path resolvedPath;

    private PlayerSkillManager() {}

    /**
     * Clears all in-memory skill data. Used by tests to ensure isolation.
     */
    static void resetForTests() {
        playerData.clear();
        dirtyPlayers.clear();
        resolvedPath = null;
    }

    public static void load(MinecraftServer server) {
        resolvedPath = server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
        playerData.clear();
        dirtyPlayers.clear();
        if (!Files.exists(resolvedPath)) {
            return;
        }
        try {
            String json = Files.readString(resolvedPath);
            SavedSkillFile saved = GSON.fromJson(json, SavedSkillFile.class);
            if (saved != null && saved.players != null) {
                for (Map.Entry<String, PlayerSkillData> entry : saved.players.entrySet()) {
                    try {
                        playerData.put(UUID.fromString(entry.getKey()), entry.getValue());
                    } catch (IllegalArgumentException ex) {
                        McdgMod.LOGGER.warn("Invalid player skill UUID: {}", entry.getKey());
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            McdgMod.LOGGER.error("Failed to load player skills from {}", resolvedPath, ex);
        }
    }

    public static void save(MinecraftServer server) {
        if (resolvedPath == null) {
            resolvedPath = server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME);
        }
        SavedSkillFile saved = new SavedSkillFile();
        saved.version = CURRENT_VERSION;
        saved.players = new HashMap<>();
        for (Map.Entry<UUID, PlayerSkillData> entry : playerData.entrySet()) {
            saved.players.put(entry.getKey().toString(), entry.getValue());
        }
        try {
            Files.createDirectories(resolvedPath.getParent());
            Files.writeString(resolvedPath, GSON.toJson(saved));
            dirtyPlayers.clear();
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save player skills to {}", resolvedPath, ex);
        }
    }

    public static void tickAutosave(MinecraftServer server) {
        if (!dirtyPlayers.isEmpty()) {
            save(server);
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        PlayerSkillData data = playerData.get(player.getUuid());
        if (data == null) {
            data = new PlayerSkillData();
            playerData.put(player.getUuid(), data);
            dirtyPlayers.put(player.getUuid(), true);
        }
        sendSkillsStatus(player);
    }

    private static void sendSkillsStatus(ServerPlayerEntity player) {
        PlayerSkillData data = playerData.get(player.getUuid());
        if (data == null) {
            return;
        }
        Set<String> unlockedSkills = new HashSet<>();
        for (SkillUnlock skill : SkillUnlock.values()) {
            if (Boolean.TRUE.equals(data.unlockedSkills.get(skill.key()))) {
                unlockedSkills.add(skill.key());
            }
        }
        SkillsStatusSync.Payload payload = new SkillsStatusSync.Payload(Set.copyOf(unlockedSkills));
        ServerPlayNetworking.send(player, payload);
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        save(player.getServer());
    }

    public static void recordThrow(ServerPlayerEntity player, DiscTier tier) {
        modify(player, data -> {
            data.totalThrows++;
            if (tier != null) {
                data.tierDiscsCrafted.putIfAbsent(tier.name().toLowerCase(), 1);
            }
            evaluateUnlocks(player, data);
        });
    }

    public static void recordRoundCompleted(ServerPlayerEntity player, int holes, int aces) {
        modify(player, data -> {
            data.roundsCompleted++;
            data.holesCompleted += holes;
            data.aces += aces;
            evaluateUnlocks(player, data);
        });
    }

    public static void recordNearPin(ServerPlayerEntity player) {
        modify(player, data -> {
            data.nearPins++;
            evaluateUnlocks(player, data);
        });
    }

    public static void awardXp(ServerPlayerEntity player, int xp) {
        modify(player, data -> {
            data.totalXp += xp;
            evaluateUnlocks(player, data);
        });
    }

    public static boolean hasSkill(ServerPlayerEntity player, SkillUnlock skill) {
        PlayerSkillData data = playerData.get(player.getUuid());
        return data != null && Boolean.TRUE.equals(data.unlockedSkills.get(skill.key()));
    }

    public static Map<SkillUnlock, Boolean> getSkillStatus(ServerPlayerEntity player) {
        Map<SkillUnlock, Boolean> status = new HashMap<>();
        PlayerSkillData data = playerData.get(player.getUuid());
        for (SkillUnlock skill : SkillUnlock.values()) {
            status.put(skill, data != null && Boolean.TRUE.equals(data.unlockedSkills.get(skill.key())));
        }
        return status;
    }

    public static int getSkillProgress(ServerPlayerEntity player, SkillUnlock skill) {
        PlayerSkillData data = playerData.get(player.getUuid());
        if (data == null) {
            return 0;
        }
        return switch (skill) {
            case POWER_CONTROL -> data.totalXp;
            case RELEASE_CONTROL -> data.roundsCompleted;
            case WIND_READING -> data.totalThrows;
            case FOCUS -> data.nearPins;
            case DISC_MASTERY -> {
                int tiersCrafted = 0;
                for (DiscTier tier : DiscTier.values()) {
                    if (data.tierDiscsCrafted.getOrDefault(tier.name().toLowerCase(), 0) >= 1) {
                        tiersCrafted++;
                    }
                }
                yield tiersCrafted;
            }
        };
    }

    private static void modify(ServerPlayerEntity player, java.util.function.Consumer<PlayerSkillData> action) {
        PlayerSkillData data = playerData.computeIfAbsent(player.getUuid(), uuid -> new PlayerSkillData());
        action.accept(data);
        dirtyPlayers.put(player.getUuid(), true);
    }

    private static void evaluateUnlocks(ServerPlayerEntity player, PlayerSkillData data) {
        boolean newUnlock = false;
        for (SkillUnlock skill : SkillUnlock.values()) {
            if (Boolean.TRUE.equals(data.unlockedSkills.get(skill.key()))) {
                continue;
            }
            if (SkillUnlockEvaluator.isSkillUnlocked(skill, data)) {
                data.unlockedSkills.put(skill.key(), true);
                SkillUnlockNotifier.notifyUnlock(player, skill);
                newUnlock = true;
            }
        }
        if (newUnlock) {
            sendSkillsStatus(player);
        }
    }

    private static class SavedSkillFile {
        public int version = CURRENT_VERSION;
        public Map<String, PlayerSkillData> players = new HashMap<>();
    }
}