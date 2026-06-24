package com.mcdg.world;

import com.mcdg.game.HazardType;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Places biome-specific hazards along fairways during course placement.
 * Uses the resolved BiomeHazardProfile to determine appropriate hazard types,
 * density, and placement patterns while respecting safe zones.
 */
public final class HazardPlacementService {
    private HazardPlacementService() {}

    /**
     * Places hazards along a fairway segment based on the biome hazard profile.
     * Called after fairway carving to add biome-appropriate hazards.
     *
     * @param world The server world
     * @param startX Start X coordinate of fairway
     * @param startZ Start Z coordinate of fairway
     * @param endX End X coordinate of fairway
     * @param endZ End Z coordinate of fairway
     * @param width Fairway width
     * @param originalBlocks Map to track original block states for cleanup
     * @param protectedPositions Set of positions that cannot be modified
     * @param profile The biome hazard profile to use
     * @param seed Random seed for deterministic placement
     * @param teePos Tee position (for safe zone calculation)
     * @param basketPos Basket position (for safe zone calculation)
     */
    public static void placeHazardsAlongFairway(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            int width,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeHazardProfile profile,
            long seed,
            BlockPos teePos,
            BlockPos basketPos
    ) {
        if (profile.hazardDensity() <= 0.0 || profile.preferredHazards().isEmpty()) {
            return; // No hazards to place
        }

        Random random = new Random(seed);
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps < 1) {
            steps = 1;
        }

        int radius = Math.max(1, width / 2);
        int hazardRadius = Math.max(1, radius + 1); // Place hazards slightly outside fairway

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(startX + (endX - startX) * t);
            int z = (int) Math.round(startZ + (endZ - startZ) * t);

            // Calculate distance from tee and basket
            double distanceFromTee = Math.sqrt(Math.pow(x - teePos.getX(), 2) + Math.pow(z - teePos.getZ(), 2));
            double distanceFromBasket = Math.sqrt(Math.pow(x - basketPos.getX(), 2) + Math.pow(z - basketPos.getZ(), 2));

            // Respect safe zones
            if (distanceFromTee < profile.minDistanceFromTee() || distanceFromBasket < profile.minDistanceFromBasket()) {
                continue;
            }

            // Random chance to place hazard at this step
            if (random.nextDouble() > profile.hazardDensity()) {
                continue;
            }

            // Select a random hazard type from preferred hazards
            HazardType hazardType = profile.preferredHazards().get(
                    random.nextInt(profile.preferredHazards().size())
            );

            BlockState hazardBlock = profile.hazardBlocks().get(hazardType);
            if (hazardBlock == null) {
                continue; // No block mapping for this hazard type
            }

            // Place hazard patches around the fairway edge
            placeHazardPatch(
                    world,
                    x,
                    z,
                    hazardRadius,
                    hazardBlock,
                    originalBlocks,
                    protectedPositions,
                    random
            );
        }
    }

    /**
     * Places a patch of hazard blocks around a center point.
     * Hazards are placed at the edges of the fairway, not in the center.
     */
    private static void placeHazardPatch(
            ServerWorld world,
            int centerX,
            int centerZ,
            int radius,
            BlockState hazardBlock,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Random random
    ) {
        // Place hazards in a ring around the center (fairway edge)
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Only place at edges (outside inner fairway area)
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < radius - 1 || distance > radius + 1) {
                    continue;
                }

                // Random chance to place each block in the patch
                if (random.nextDouble() > CoursePlacementConfig.HazardPlacement.PATCH_BLOCK_PROBABILITY) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;
                BlockPos surface = SurfaceResolver.resolveSurfacePos(world, x, z);
                BlockPos hazardPos = surface;

                // Don't place on protected positions
                if (PlacementUtils.isProtected(protectedPositions, hazardPos)) {
                    continue;
                }

                // Don't place if already the same block
                if (world.getBlockState(hazardPos).equals(hazardBlock)) {
                    continue;
                }

                // Don't place over water or lava (safety)
                if (!world.getFluidState(hazardPos).isEmpty()) {
                    continue;
                }

                PlacementUtils.setTrackedBlock(world, hazardPos, hazardBlock, originalBlocks);
            }
        }
    }
}
