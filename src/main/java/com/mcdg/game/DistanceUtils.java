package com.mcdg.game;

import net.minecraft.util.math.BlockPos;

public final class DistanceUtils {
    private DistanceUtils() {
    }

    public static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) + Math.abs(from.getZ() - to.getZ());
    }

    public static int distanceMeters(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        return Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }

    public static int distanceFeet(BlockPos from, BlockPos to) {
        return Math.max(0, Math.round(distanceMeters(from, to) * 3.28084f));
    }
}
