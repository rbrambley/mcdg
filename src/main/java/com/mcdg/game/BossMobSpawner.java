package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.game.ai.GuardBasketGoal;
import com.mcdg.game.ai.PatrolFairwayGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mob spawning for boss hole challenge courses.
 * Follows the pattern from AceCompanionService for entity management.
 */
public final class BossMobSpawner {
    private static final Map<UUID, BossMobSpawner> ACTIVE_SPAWNERS = new ConcurrentHashMap<>();
    private static final int GUARD_GOAL_PRIORITY = 2;
    private static final int PATROL_GOAL_PRIORITY = 3;

    private final UUID roundId;
    private final ServerPlayerEntity player;
    private final PlacedCourseState placedCourseState;
    private final BossMobConfig config;
    private final List<UUID> spawnedMobs;
    private final Random random;
    private long lastSpawnTick;

    private BossMobSpawner(UUID roundId, ServerPlayerEntity player, PlacedCourseState placedCourseState, BossMobConfig config) {
        this.roundId = roundId;
        this.player = player;
        this.placedCourseState = placedCourseState;
        this.config = config;
        this.spawnedMobs = new ArrayList<>();
        this.random = new Random(roundId.getMostSignificantBits());
        this.lastSpawnTick = 0;
    }

    /**
     * Starts mob spawning for a boss hole round.
     * If a spawner already exists for the round, its mobs are despawned first.
     */
    public static void startSpawning(UUID roundId, ServerPlayerEntity player, PlacedCourseState placedCourseState) {
        BossMobConfig config = BossMobConfig.defaultBossHoleConfig();
        BossMobSpawner spawner = new BossMobSpawner(roundId, player, placedCourseState, config);
        BossMobSpawner existing = ACTIVE_SPAWNERS.put(roundId, spawner);
        if (existing != null) {
            existing.despawnAllMobs();
            McdgMod.LOGGER.info("Replaced existing boss hole mob spawner for round {}", roundId);
        }

        McdgMod.LOGGER.info("Started boss hole mob spawning for round {}", roundId);
    }

    /**
     * Stops mob spawning for a boss hole round and despawns all mobs.
     */
    public static void stopSpawning(UUID roundId) {
        if (roundId == null) {
            return;
        }
        BossMobSpawner spawner = ACTIVE_SPAWNERS.remove(roundId);
        if (spawner != null) {
            spawner.despawnAllMobs();
            McdgMod.LOGGER.info("Stopped boss hole mob spawning for round {}", roundId);
        }
    }

    /**
     * Stops the spawner(s) owned by the given player and despawns their mobs.
     * Used when a player disconnects.
     */
    public static void stopSpawningForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ACTIVE_SPAWNERS.entrySet().removeIf(entry -> {
            BossMobSpawner spawner = entry.getValue();
            if (playerId.equals(spawner.player.getUuid())) {
                spawner.despawnAllMobs();
                McdgMod.LOGGER.info("Stopped boss hole mob spawner for disconnected player {} (round {})",
                        playerId, spawner.roundId);
                return true;
            }
            return false;
        });
    }

    /**
     * Stops all active boss hole spawners and despawns every tracked mob.
     * Intended for server shutdown to avoid saving orphaned persistent mobs.
     */
    public static void stopAll() {
        if (ACTIVE_SPAWNERS.isEmpty()) {
            return;
        }
        int count = ACTIVE_SPAWNERS.size();
        for (BossMobSpawner spawner : ACTIVE_SPAWNERS.values()) {
            spawner.despawnAllMobs();
        }
        ACTIVE_SPAWNERS.clear();
        McdgMod.LOGGER.info("Stopped all {} active boss hole spawners during server shutdown", count);
    }

    /**
     * Ticks all active mob spawners. Called from server tick loop.
     */
    public static void tick(MinecraftServer server) {
        if (ACTIVE_SPAWNERS.isEmpty()) {
            return;
        }

        long currentTick = server.getOverworld().getTime();

        // Remove spawners for invalid rounds
        ACTIVE_SPAWNERS.entrySet().removeIf(entry -> {
            BossMobSpawner spawner = entry.getValue();
            if (!spawner.isValid()) {
                spawner.despawnAllMobs();
                return true;
            }
            return false;
        });

        // Tick remaining spawners
        for (BossMobSpawner spawner : ACTIVE_SPAWNERS.values()) {
            spawner.tickInternal(currentTick);
        }
    }

    /**
     * Internal tick logic for this spawner.
     */
    private void tickInternal(long currentTick) {
        // Clean up dead mobs before deciding whether to spawn more.
        cleanupDeadMobs();

        if (currentTick - lastSpawnTick >= config.spawnIntervalTicks()) {
            if (spawnedMobs.size() < config.maxMobs()) {
                spawnMob();
                lastSpawnTick = currentTick;
            }
        }
    }

    /**
     * Spawns a single mob at a strategic position.
     */
    private void spawnMob() {
        ServerWorld world = getCourseWorld();
        if (world == null) {
            McdgMod.LOGGER.warn("Cannot spawn boss hole mob - course world is unavailable for round {}", roundId);
            return;
        }

        BlockPos basketPos = placedCourseState.holeBaskets().get(1);
        BlockPos teePos = placedCourseState.holeTees().get(1);

        if (basketPos == null || teePos == null) {
            McdgMod.LOGGER.warn("Cannot spawn boss hole mob - missing basket or tee position");
            return;
        }

        // Select random mob type
        Identifier mobType = config.mobTypes().get(random.nextInt(config.mobTypes().size()));
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(mobType);

        if (entityType == null) {
            McdgMod.LOGGER.warn("Unknown entity type: {}", mobType);
            return;
        }

        // Determine spawn position based on interference type
        BlockPos spawnPos;
        MobInterferenceType interferenceType;

        if (config.guardBasket() && random.nextFloat() < 0.6f) {
            // 60% chance to spawn basket guard if enabled
            var positions = BossMobPositioner.findBasketGuardPositions(world, random, basketPos, 1);
            if (!positions.isEmpty()) {
                spawnPos = positions.get(0);
                interferenceType = MobInterferenceType.GUARDING_BASKET;
            } else {
                // Fallback to fairway patrol
                positions = BossMobPositioner.findFairwayPatrolPositions(world, random, teePos, basketPos, 1);
                spawnPos = positions.isEmpty() ? teePos : positions.get(0);
                interferenceType = MobInterferenceType.PATROL_FAIRWAY;
            }
        } else if (config.patrolFairway()) {
            // Spawn fairway patrol
            var positions = BossMobPositioner.findFairwayPatrolPositions(world, random, teePos, basketPos, 1);
            spawnPos = positions.isEmpty() ? teePos : positions.get(0);
            interferenceType = MobInterferenceType.PATROL_FAIRWAY;
        } else {
            // Fallback to tee harass
            var positions = BossMobPositioner.findTeeHarassPositions(world, random, teePos, 1);
            spawnPos = positions.isEmpty() ? teePos : positions.get(0);
            interferenceType = MobInterferenceType.HARASS_TEE;
        }

        // Create and spawn the mob
        try {
            Entity mob = entityType.create(world);
            if (mob == null) {
                McdgMod.LOGGER.warn("Failed to create entity for type: {}", mobType);
                return;
            }

            mob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

            // Make mob persistent so it doesn't despawn naturally
            if (mob instanceof MobEntity mobEntity) {
                mobEntity.setPersistent();
                // Prevent undead mobs from burning in daylight; keep the helmet from dropping as loot.
                if (mobEntity instanceof ZombieEntity || mobEntity instanceof AbstractSkeletonEntity) {
                    mobEntity.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    mobEntity.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
                }
                applyCustomAI(mobEntity, interferenceType, basketPos, teePos);
            }

            world.spawnEntity(mob);
            spawnedMobs.add(mob.getUuid());

            McdgMod.LOGGER.info("Spawned boss hole mob {} at ({}, {}, {}) for round {}",
                    mobType, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), roundId);
        } catch (Exception e) {
            McdgMod.LOGGER.error("Failed to spawn boss hole mob", e);
        }
    }

    /**
     * Applies custom AI behavior based on interference type.
     */
    private void applyCustomAI(MobEntity mob, MobInterferenceType interferenceType, BlockPos basketPos, BlockPos teePos) {
        switch (interferenceType) {
            case GUARDING_BASKET -> mob.goalSelector.add(GUARD_GOAL_PRIORITY, new GuardBasketGoal(mob, basketPos));
            case PATROL_FAIRWAY -> mob.goalSelector.add(PATROL_GOAL_PRIORITY, new PatrolFairwayGoal(mob, random, teePos, basketPos));
            case HARASS_TEE -> {
                // Vanilla AI handles harassment; no custom goals needed.
            }
        }
        McdgMod.LOGGER.debug("Applied custom AI {} to boss hole mob for round {}", interferenceType, roundId);
    }

    /**
     * Despawns all mobs tracked by this spawner.
     */
    private void despawnAllMobs() {
        if (spawnedMobs.isEmpty()) {
            return;
        }

        ServerWorld world = getCourseWorld();
        if (world == null) {
            // Player/world is gone; clear tracking so persistent mobs are not orphaned.
            spawnedMobs.clear();
            return;
        }

        int despawned = 0;
        for (UUID mobId : spawnedMobs) {
            Entity entity = world.getEntity(mobId);
            if (entity != null && entity.isAlive()) {
                entity.discard();
                despawned++;
            }
        }

        spawnedMobs.clear();
        McdgMod.LOGGER.info("Despawned {} mobs for round {}", despawned, roundId);
    }

    /**
     * Removes dead mobs from the tracking list.
     */
    private void cleanupDeadMobs() {
        ServerWorld world = getCourseWorld();
        if (world == null) {
            spawnedMobs.clear();
            return;
        }
        spawnedMobs.removeIf(mobId -> {
            Entity entity = world.getEntity(mobId);
            return entity == null || !entity.isAlive();
        });
    }

    /**
     * Checks if this spawner is still valid (player still alive and in the course world).
     */
    private boolean isValid() {
        if (!player.isAlive()) {
            return false;
        }

        ServerWorld world = player.getServerWorld();
        if (world == null) {
            return false;
        }

        return world.getRegistryKey().equals(placedCourseState.worldKey());
    }

    /**
     * Gets the world where the course was placed, even if the player has moved elsewhere.
     */
    private ServerWorld getCourseWorld() {
        if (player == null || player.getServer() == null) {
            return null;
        }
        return player.getServer().getWorld(placedCourseState.worldKey());
    }

    /**
     * Gets the number of currently spawned mobs.
     */
    public int getActiveMobCount() {
        return spawnedMobs.size();
    }
}
