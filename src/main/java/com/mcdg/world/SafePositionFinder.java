package com.mcdg.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SafePositionFinder {
    private SafePositionFinder() {
    }

    public static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
        if (isStandableFeet(world, preferredFeet)) {
            return preferredFeet;
        }

        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = preferredFeet.up(dy);
            if (isStandableFeet(world, up)) {
                return up;
            }
            BlockPos down = preferredFeet.down(dy);
            if (isStandableFeet(world, down)) {
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
                    if (isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidate.up(dy);
                        if (isStandableFeet(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidate.down(dy);
                        if (isStandableFeet(world, candidateDown)) {
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
        if (isStandableFeet(world, candidate)) {
            return candidate;
        }

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos up = baseFeet.up(offset);
            if (isStandableFeet(world, up)) {
                return up;
            }
            BlockPos down = baseFeet.down(offset);
            if (isStandableFeet(world, down)) {
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
                    if (isStandableFeet(world, candidateAtRadius)) {
                        return candidateAtRadius;
                    }

                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidateAtRadius.up(dy);
                        if (isStandableFeet(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidateAtRadius.down(dy);
                        if (isStandableFeet(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        int fallbackY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, baseFeet.getX(), baseFeet.getZ());
        BlockPos fallback = new BlockPos(baseFeet.getX(), fallbackY, baseFeet.getZ());
        if (isStandableFeet(world, fallback)) {
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
}
