package com.mcdg.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class BuildCoursePositionHelper {
    private BuildCoursePositionHelper() {
    }

    static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
    }

    static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos anchor) {
        world.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        BlockPos candidate = anchor.withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()));
        if (isStandableFeet(world, candidate)) {
            return candidate;
        }

        int[] deltas = {1, -1, 2, -2, 3, -3, 4, -4};
        for (int dx : deltas) {
            for (int dz : deltas) {
                int x = anchor.getX() + dx;
                int z = anchor.getZ() + dz;
                world.getChunk(x >> 4, z >> 4);
                BlockPos probe = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (isStandableFeet(world, probe)) {
                    return probe;
                }
            }
        }

        return candidate;
    }

    static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        BlockPos below = feet.down();
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.up();
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }
        return !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
    }

}
