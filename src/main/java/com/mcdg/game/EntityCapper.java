package com.mcdg.game;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntityCapper {
    private static final int TICK_INTERVAL = 600;        // 30 seconds (was 10)
    private static final int SCAN_RADIUS = 64;           // was 96
    private static final int SCAN_VERTICAL = 32;         // was 100
    private static final int MAX_REMOVALS_PER_TICK = 5;
    private static final double KEEP_DISTANCE_SQ = 50.0 * 50.0;
    private static int tickCounter = 0;

    // Pending discards amortized across ticks to avoid removal spikes
    private static final Deque<MobEntity> PENDING_DISCARDS = new ArrayDeque<>();

    private EntityCapper() {}

    public static void tick(MinecraftServer server) {
        tickCounter++;

        // Always process a few pending discards each tick to spread the cost
        if (!PENDING_DISCARDS.isEmpty()) {
            int processed = 0;
            while (!PENDING_DISCARDS.isEmpty() && processed < MAX_REMOVALS_PER_TICK) {
                MobEntity mob = PENDING_DISCARDS.pollFirst();
                if (mob != null && mob.isAlive()) {
                    mob.discard();
                }
                processed++;
            }
        }

        if (tickCounter % TICK_INTERVAL != 0) {
            return;
        }

        for (ServerWorld world : server.getWorlds()) {
            if (!world.getRegistryKey().equals(World.OVERWORLD)) {
                continue;
            }

            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }

            Set<MobEntity> checked = new HashSet<>();
            List<MobEntity> toQueue = new ArrayList<>();

            for (ServerPlayerEntity player : players) {
                double px = player.getX();
                double py = player.getY();
                double pz = player.getZ();
                Box box = new Box(
                        px - SCAN_RADIUS, py - SCAN_VERTICAL, pz - SCAN_RADIUS,
                        px + SCAN_RADIUS, py + SCAN_VERTICAL, pz + SCAN_RADIUS
                );

                List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, box, e -> {
                    SpawnGroup group = e.getType().getSpawnGroup();
                    return group == SpawnGroup.MONSTER || group == SpawnGroup.AMBIENT;
                });

                for (MobEntity mob : mobs) {
                    if (!checked.add(mob)) {
                        continue;
                    }
                    // Fast path: mob is near the scanning player
                    if (player.squaredDistanceTo(mob) < KEEP_DISTANCE_SQ) {
                        continue;
                    }
                    boolean nearAny = false;
                    for (PlayerEntity p : players) {
                        if (p.squaredDistanceTo(mob) < KEEP_DISTANCE_SQ) {
                            nearAny = true;
                            break;
                        }
                    }
                    if (!nearAny) {
                        toQueue.add(mob);
                    }
                }
            }

            if (!toQueue.isEmpty()) {
                PENDING_DISCARDS.addAll(toQueue);
                com.mcdg.McdgMod.LOGGER.info("EntityCapper queued {} distant mobs/bats for amortized removal in {}",
                        toQueue.size(), world.getRegistryKey().getValue());
            }
        }
    }
}
