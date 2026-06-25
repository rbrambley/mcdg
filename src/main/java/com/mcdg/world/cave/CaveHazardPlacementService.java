package com.mcdg.world.cave;

import com.mcdg.McdgMod;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Places cave-specific hazards along fairways during course placement.
 * Uses lava pools, magma blocks, and campfires as cave-appropriate hazards.
 */
public final class CaveHazardPlacementService {
    private static final double HAZARD_DENSITY = 0.03; // Reduced from 0.05 for tighter cave fairways
    private static final int TEE_SAFE_RADIUS = 5;
    private static final int BASKET_SAFE_RADIUS = 5;
    private static final int LAVA_POOL_MIN_RADIUS = 2;
    private static final int LAVA_POOL_MAX_RADIUS = 4;

    private CaveHazardPlacementService() {}

    /**
     * Places cave hazards along a fairway segment.
     * Places lava pools, magma blocks, and campfires while avoiding tee and basket safe zones.
     *
     * @param world The server world
     * @param tee Tee position
     * @param basket Basket position
     * @param originalBlocks Map to track original block states for cleanup
     * @param protectedPositions Set of positions that cannot be modified
     * @param seed Random seed for deterministic placement
     */
    public static void placeCaveHazards(
            ServerWorld world,
            BlockPos tee,
            BlockPos basket,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            long seed
    ) {
        Random random = new Random(seed);
        int dx = basket.getX() - tee.getX();
        int dz = basket.getZ() - tee.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps == 0) {
            return;
        }

        int hazardsPlaced = 0;

        // Walk along the fairway and place hazards
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            int x = (int) Math.round(tee.getX() + dx * t);
            int z = (int) Math.round(tee.getZ() + dz * t);

            // Check if we're in a safe zone (near tee or basket)
            double distToTee = Math.sqrt(Math.pow(x - tee.getX(), 2) + Math.pow(z - tee.getZ(), 2));
            double distToBasket = Math.sqrt(Math.pow(x - basket.getX(), 2) + Math.pow(z - basket.getZ(), 2));

            if (distToTee < TEE_SAFE_RADIUS || distToBasket < BASKET_SAFE_RADIUS) {
                continue; // Skip safe zones
            }

            // Check if too close to cave walls (solid blocks at head level)
            if (isTooCloseToWall(world, x, tee.getY(), z)) {
                continue; // Skip positions too close to walls
            }

            // Random chance to place hazard
            if (random.nextDouble() < HAZARD_DENSITY) {
                int hazardType = random.nextInt(3); // 0: lava pool, 1: magma blocks, 2: campfire

                switch (hazardType) {
                    case 0:
                        placeLavaPool(world, x, tee.getY(), z, random, originalBlocks, protectedPositions);
                        hazardsPlaced++;
                        break;
                    case 1:
                        placeMagmaBlocks(world, x, tee.getY(), z, random, originalBlocks, protectedPositions);
                        hazardsPlaced++;
                        break;
                    case 2:
                        placeCampfire(world, x, tee.getY(), z, originalBlocks, protectedPositions);
                        hazardsPlaced++;
                        break;
                }
            }
        }

        McdgMod.LOGGER.info("Cave hazards placed: {} hazards along fairway", hazardsPlaced);
    }

    /**
     * Places a lava pool at the specified location.
     */
    private static void placeLavaPool(
            ServerWorld world,
            int centerX,
            int centerY,
            int centerZ,
            Random random,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int radius = LAVA_POOL_MIN_RADIUS + random.nextInt(LAVA_POOL_MAX_RADIUS - LAVA_POOL_MIN_RADIUS + 1);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue; // Keep it circular
                }

                BlockPos pos = new BlockPos(centerX + dx, centerY - 1, centerZ + dz);
                if (protectedPositions.contains(pos.toImmutable())) {
                    continue;
                }

                // Replace floor with lava
                if (!originalBlocks.containsKey(pos.toImmutable())) {
                    originalBlocks.put(pos.toImmutable(), world.getBlockState(pos));
                }
                world.setBlockState(pos, Blocks.LAVA.getDefaultState(), 3);
            }
        }
    }

    /**
     * Places magma blocks in a small cluster.
     */
    private static void placeMagmaBlocks(
            ServerWorld world,
            int centerX,
            int centerY,
            int centerZ,
            Random random,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int clusterSize = 2 + random.nextInt(3); // 2-4 blocks

        for (int i = 0; i < clusterSize; i++) {
            int offsetX = random.nextInt(3) - 1; // -1 to 1
            int offsetZ = random.nextInt(3) - 1; // -1 to 1

            BlockPos pos = new BlockPos(centerX + offsetX, centerY - 1, centerZ + offsetZ);
            if (protectedPositions.contains(pos.toImmutable())) {
                continue;
            }

            // Replace floor with magma block
            if (!originalBlocks.containsKey(pos.toImmutable())) {
                originalBlocks.put(pos.toImmutable(), world.getBlockState(pos));
            }
            world.setBlockState(pos, Blocks.MAGMA_BLOCK.getDefaultState(), 3);
        }
    }

    /**
     * Places a campfire at the specified location.
     */
    private static void placeCampfire(
            ServerWorld world,
            int centerX,
            int centerY,
            int centerZ,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos pos = new BlockPos(centerX, centerY, centerZ);
        if (protectedPositions.contains(pos.toImmutable())) {
            return;
        }

        // Place campfire at player level
        if (!originalBlocks.containsKey(pos.toImmutable())) {
            originalBlocks.put(pos.toImmutable(), world.getBlockState(pos));
        }
        world.setBlockState(pos, Blocks.CAMPFIRE.getDefaultState(), 3);
    }

    /**
     * Checks if a position is too close to cave walls.
     * Checks head level (Y+1 to Y+3) for solid blocks within 2 blocks.
     */
    private static boolean isTooCloseToWall(ServerWorld world, int x, int y, int z) {
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip center
                    
                    BlockPos checkPos = new BlockPos(x + dx, y + dy, z + dz);
                    if (world.getBlockState(checkPos).isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}