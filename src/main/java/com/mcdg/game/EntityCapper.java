package com.mcdg.game;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntityCapper {
    private static final int TICK_INTERVAL = 200;
    private static final int SCAN_RADIUS = 96;
    private static final double KEEP_DISTANCE_SQ = 50.0 * 50.0;
    private static int tickCounter = 0;

    private EntityCapper() {}

    public static void tick(MinecraftServer server) {
        tickCounter++;
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
            int removed = 0;

            for (ServerPlayerEntity player : players) {
                double px = player.getX();
                double py = player.getY();
                double pz = player.getZ();
                Box box = new Box(
                        px - SCAN_RADIUS, py - 100, pz - SCAN_RADIUS,
                        px + SCAN_RADIUS, py + 100, pz + SCAN_RADIUS
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
                        mob.discard();
                        removed++;
                    }
                }
            }

            if (removed > 0) {
                com.mcdg.McdgMod.LOGGER.info("EntityCapper removed {} distant mobs/bats in {}",
                        removed, world.getRegistryKey().getValue());
            }
        }
    }
}
