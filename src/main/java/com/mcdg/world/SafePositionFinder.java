package com.mcdg.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SafePositionFinder {
    private static final int SKY_ACCESS_CHECK_HEIGHT = 3;  // Check 3 blocks above head
    private static final int SKY_ACCESS_OPENING_WIDTH = 2;  // 2x2 block opening
    
    private SafePositionFinder() {
    }

    public static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
        if (isStandableFeet(world, preferredFeet) && hasSkyAccess(world, preferredFeet)) {
            return preferredFeet;
        }

        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = preferredFeet.up(dy);
            if (isStandableFeet(world, up) && hasSkyAccess(world, up)) {
                return up;
            }
            BlockPos down = preferredFeet.down(dy);
            if (isStandableFeet(world, down) && hasSkyAccess(world, down)) {
                return down;
            }
        }

        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = preferredFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidate) && hasSkyAccess(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidate.up(dy);
                        if (isStandableFeet(world, candidateUp) && hasSkyAccess(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidate.down(dy);
                        if (isStandableFeet(world, candidateDown) && hasSkyAccess(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        return preferredFeet;
    }

    public static BlockPos findNearestStandableFeet(ServerWorld world, BlockPos baseFeet) {
        BlockPos candidate = baseFeet;
        if (isStandableFeet(world, candidate) && hasSkyAccess(world, candidate)) {
            return candidate;
        }

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos up = baseFeet.up(offset);
            if (isStandableFeet(world, up) && hasSkyAccess(world, up)) {
                return up;
            }
            BlockPos down = baseFeet.down(offset);
            if (isStandableFeet(world, down) && hasSkyAccess(world, down)) {
                return down;
            }
        }

        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidateAtRadius = baseFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidateAtRadius) && hasSkyAccess(world, candidateAtRadius)) {
                        return candidateAtRadius;
                    }

                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidateAtRadius.up(dy);
                        if (isStandableFeet(world, candidateUp) && hasSkyAccess(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidateAtRadius.down(dy);
                        if (isStandableFeet(world, candidateDown) && hasSkyAccess(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        int fallbackY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, baseFeet.getX(), baseFeet.getZ());
        BlockPos fallback = new BlockPos(baseFeet.getX(), fallbackY, baseFeet.getZ());
        if (isStandableFeet(world, fallback) && hasSkyAccess(world, fallback)) {
            return fallback;
        }

        return baseFeet;
    }

    public static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        if (!world.getFluidState(feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }

        BlockPos head = feet.up();
        if (!world.getFluidState(head).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }

        BlockPos ground = feet.down();
        if (!world.getFluidState(ground).isEmpty()) {
            return false;
        }

        return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
    }

    /**
     * Checks if a position has a clear opening to the sky (2x2 opening, 3 blocks above head).
     * Used to prevent players from being trapped in underground caves with no escape route.
     */
    public static boolean hasSkyAccess(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up();
        
        // Check 2x2 opening at head level and 3 blocks above
        for (int y = 0; y <= SKY_ACCESS_CHECK_HEIGHT; y++) {
            BlockPos checkY = head.up(y);
            
            for (int dx = 0; dx < SKY_ACCESS_OPENING_WIDTH; dx++) {
                for (int dz = 0; dz < SKY_ACCESS_OPENING_WIDTH; dz++) {
                    BlockPos checkPos = checkY.add(dx, 0, dz);
                    
                    // Position must be either air or non-solid (e.g., leaves, glass, water)
                    BlockState state = world.getBlockState(checkPos);
                    if (!state.isAir() && !state.getCollisionShape(world, checkPos).isEmpty()) {
                        return false;  // Solid block blocks the opening
                    }
                }
            }
        }
        
        return true;
    }
}
