package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    private static final Random RANDOM = new Random();

    private final UUID roundId;
    private final ServerPlayerEntity player;
    private final PlacedCourseState placedCourseState;
    private final BossMobConfig config;
    private final List<UUID> spawnedMobs;
    private long lastSpawnTick;
    private int spawnCount;

    private BossMobSpawner(UUID roundId, ServerPlayerEntity player, PlacedCourseState placedCourseState, BossMobConfig config) {
        this.roundId = roundId;
        this.player = player;
        this.placedCourseState = placedCourseState;
        this.config = config;
        this.spawnedMobs = new ArrayList<>();
        this.lastSpawnTick = 0;
        this.spawnCount = 0;
    }

    /**
     * Starts mob spawning for a boss hole round.
     */
    public static void startSpawning(UUID roundId, ServerPlayerEntity player, PlacedCourseState placedCourseState) {
        BossMobConfig config = BossMobConfig.defaultBossHoleConfig();
        BossMobSpawner spawner = new BossMobSpawner(roundId, player, placedCourseState, config);
        ACTIVE_SPAWNERS.put(roundId, spawner);

        McdgMod.LOGGER.info("Started boss hole mob spawning for round {}", roundId);
    }

    /**
     * Stops mob spawning for a boss hole round and despawns all mobs.
     */
    public static void stopSpawning(UUID roundId) {
        BossMobSpawner spawner = ACTIVE_SPAWNERS.remove(roundId);
        if (spawner != null) {
            spawner.despawnAllMobs();
            McdgMod.LOGGER.info("Stopped boss hole mob spawning for round {}", roundId);
        }
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
        // Check if it's time to spawn a new mob
        if (currentTick - lastSpawnTick >= config.spawnIntervalTicks()) {
            if (spawnCount < config.maxMobs()) {
                spawnMob();
                lastSpawnTick = currentTick;
            }
        }

        // Clean up dead mobs from tracking
        cleanupDeadMobs();
    }

    /**
     * Spawns a single mob at a strategic position.
     */
    private void spawnMob() {
        ServerWorld world = player.getServerWorld();
        BlockPos basketPos = placedCourseState.holeBaskets().get(1);
        BlockPos teePos = placedCourseState.holeTees().get(1);

        if (basketPos == null || teePos == null) {
            McdgMod.LOGGER.warn("Cannot spawn boss hole mob - missing basket or tee position");
            return;
        }

        // Select random mob type
        Identifier mobType = config.mobTypes().get(RANDOM.nextInt(config.mobTypes().size()));
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(mobType);

        if (entityType == null) {
            McdgMod.LOGGER.warn("Unknown entity type: {}", mobType);
            return;
        }

        // Determine spawn position based on interference type
        BlockPos spawnPos;
        MobInterferenceType interferenceType;

        if (config.guardBasket() && RANDOM.nextFloat() < 0.6f) {
            // 60% chance to spawn basket guard if enabled
            var positions = BossMobPositioner.findBasketGuardPositions(world, basketPos, 1);
            if (!positions.isEmpty()) {
                spawnPos = positions.get(0);
                interferenceType = MobInterferenceType.GUARDING_BASKET;
            } else {
                // Fallback to fairway patrol
                positions = BossMobPositioner.findFairwayPatrolPositions(world, teePos, basketPos, 1);
                spawnPos = positions.isEmpty() ? teePos : positions.get(0);
                interferenceType = MobInterferenceType.PATROL_FAIRWAY;
            }
        } else if (config.patrolFairway()) {
            // Spawn fairway patrol
            var positions = BossMobPositioner.findFairwayPatrolPositions(world, teePos, basketPos, 1);
            spawnPos = positions.isEmpty() ? teePos : positions.get(0);
            interferenceType = MobInterferenceType.PATROL_FAIRWAY;
        } else {
            // Fallback to tee harass
            var positions = BossMobPositioner.findTeeHarassPositions(world, teePos, 1);
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
                // Apply custom AI based on interference type
                applyCustomAI(mobEntity, interferenceType, basketPos);
            }

            world.spawnEntity(mob);
            spawnedMobs.add(mob.getUuid());
            spawnCount++;

            McdgMod.LOGGER.info("Spawned boss hole mob {} at ({}, {}, {}) for round {}",
                    mobType, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), roundId);
        } catch (Exception e) {
            McdgMod.LOGGER.error("Failed to spawn boss hole mob", e);
        }
    }

    /**
     * Applies custom AI behavior based on interference type.
     * For now, this is a placeholder - full AI implementation will be in the goal classes.
     */
    private void applyCustomAI(MobEntity mob, MobInterferenceType interferenceType, BlockPos basketPos) {
        // TODO: Apply custom AI goals when GuardBasketGoal and PatrolFairwayGoal are implemented
        // For now, mobs will use default vanilla AI
        McdgMod.LOGGER.debug("Applied custom AI for {} - basket at ({}, {}, {})",
                interferenceType, basketPos.getX(), basketPos.getY(), basketPos.getZ());
    }

    /**
     * Despawns all mobs tracked by this spawner.
     */
    private void despawnAllMobs() {
        if (spawnedMobs.isEmpty()) {
            return;
        }

        ServerWorld world = player.getServerWorld();
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
        ServerWorld world = player.getServerWorld();
        spawnedMobs.removeIf(mobId -> {
            Entity entity = world.getEntity(mobId);
            return entity == null || !entity.isAlive();
        });
    }

    /**
     * Checks if this spawner is still valid (player still in world, round still active).
     */
    private boolean isValid() {
        if (!player.isAlive()) {
            return false;
        }

        // Check if player is still in the same world
        ServerWorld world = player.getServerWorld();
        if (world == null || !world.getRegistryKey().equals(placedCourseState.worldKey())) {
            return false;
        }

        return true;
    }

    /**
     * Gets the number of currently spawned mobs.
     */
    public int getActiveMobCount() {
        return spawnedMobs.size();
    }
}