package com.mcdg.util;

import net.minecraft.util.math.BlockPos;

public final class McdgGeometry {

    public static final int BASKET_GREEN_RADIUS_BLOCKS = 7;
    public static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;

    private McdgGeometry() {
    }

    public static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "-";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static int horizontalDistance(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());
        return Math.max(dx, dz);
    }

    public static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = sx + (t * dx);
        double closestZ = sz + (t * dz);
        double mx = px - closestX;
        double mz = pz - closestZ;
        return Math.sqrt(mx * mx + mz * mz);
    }

    public static double distanceFromPlayableRouteXZ(BlockPos point, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        if (alternateAnchor == null) {
            return distanceFromPointToSegmentXZ(point, tee, basket);
        }

        double firstLeg = distanceFromPointToSegmentXZ(point, tee, alternateAnchor);
        double secondLeg = distanceFromPointToSegmentXZ(point, alternateAnchor, basket);
        return Math.min(firstLeg, secondLeg);
    }

    public static boolean isBasketGreenSafe(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }
}
