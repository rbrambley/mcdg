package com.mcdg.game;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import java.util.List;

public final class EntityCapper {
    private static final int TICK_INTERVAL = 200;
    private static final int SCAN_RADIUS = 400;
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

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (ServerPlayerEntity p : players) {
                double px = p.getX();
                double py = p.getY();
                double pz = p.getZ();
                minX = Math.min(minX, px - SCAN_RADIUS);
                minY = Math.min(minY, py - 100);
                minZ = Math.min(minZ, pz - SCAN_RADIUS);
                maxX = Math.max(maxX, px + SCAN_RADIUS);
                maxY = Math.max(maxY, py + 100);
                maxZ = Math.max(maxZ, pz + SCAN_RADIUS);
            }
            Box box = new Box(minX, minY, minZ, maxX, maxY, maxZ);

            List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, box, e -> {
                SpawnGroup group = e.getType().getSpawnGroup();
                return group == SpawnGroup.MONSTER || group == SpawnGroup.AMBIENT;
            });

            int removed = 0;
            for (MobEntity mob : mobs) {
                boolean nearPlayer = false;
                for (PlayerEntity player : players) {
                    if (player.squaredDistanceTo(mob) < KEEP_DISTANCE_SQ) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (!nearPlayer) {
                    mob.discard();
                    removed++;
                }
            }

            if (removed > 0) {
                com.mcdg.McdgMod.LOGGER.info("EntityCapper removed {} distant mobs/bats in {}",
                        removed, world.getRegistryKey().getValue());
            }
        }
    }
}
