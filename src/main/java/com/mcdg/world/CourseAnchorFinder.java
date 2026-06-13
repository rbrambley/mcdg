package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Finds and scores candidate anchor positions for course placement.
 */
final class CourseAnchorFinder {
    private static final int ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ANCHOR;
    private static final int COURSE_ANCHOR_MAX_RETRIES = CoursePlacementConfig.CourseAnchor.MAX_RETRIES;
    private static final double COURSE_ANCHOR_HARD_REJECT_WATER_RATIO = CoursePlacementConfig.CourseAnchor.HARD_REJECT_WATER_RATIO;
    private static final double COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO = CoursePlacementConfig.CourseAnchor.MAX_WATER_SAMPLE_RATIO;
    private static final int COURSE_ANCHOR_WATER_RATIO_SCORE_WEIGHT = CoursePlacementConfig.CourseAnchor.WATER_RATIO_SCORE_WEIGHT;
    private static final int COURSE_ANCHOR_WATER_REJECT_PENALTY = CoursePlacementConfig.CourseAnchor.WATER_REJECT_PENALTY;

    private CourseAnchorFinder() {}

    static CourseBounds findCourseBounds(Course course) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Hole hole : course.holes()) {
            minX = Math.min(minX, Math.min(hole.tee().x(), hole.basket().x()));
            minZ = Math.min(minZ, Math.min(hole.tee().z(), hole.basket().z()));
            maxX = Math.max(maxX, Math.max(hole.tee().x(), hole.basket().x()));
            maxZ = Math.max(maxZ, Math.max(hole.tee().z(), hole.basket().z()));
        }

        if (minX == Integer.MAX_VALUE) {
            return new CourseBounds(0, 0, 0, 0, 0, 0);
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        return new CourseBounds(minX, minZ, maxX, maxZ, centerX, centerZ);
    }

    static BlockPos findPreferredCourseAnchor(
            ServerWorld world,
            BlockPos origin,
            Course course,
            CourseBounds courseBounds,
            Set<Long> rejectedAnchorKeys
    ) {
        int x = origin.getX();
        int z = origin.getZ();
        BlockPos best = SurfaceResolver.resolveSurfacePos(world, x, z);
        int bestScore = scoreCourseAnchor(world, best, x, z, course, courseBounds, rejectedAnchorKeys);

        int ringStep = 4;
        for (int radius = ringStep; radius <= ANCHOR_SEARCH_RADIUS; radius += ringStep) {
            for (int sx = x - radius; sx <= x + radius; sx += ringStep) {
                BlockPos north = SurfaceResolver.resolveSurfacePos(world, sx, z - radius);
                int northScore = scoreCourseAnchor(world, north, x, z, course, courseBounds, rejectedAnchorKeys);
                if (northScore < bestScore) {
                    bestScore = northScore;
                    best = north;
                }

                BlockPos south = SurfaceResolver.resolveSurfacePos(world, sx, z + radius);
                int southScore = scoreCourseAnchor(world, south, x, z, course, courseBounds, rejectedAnchorKeys);
                if (southScore < bestScore) {
                    bestScore = southScore;
                    best = south;
                }
            }

            for (int sz = z - radius + ringStep; sz <= z + radius - ringStep; sz += ringStep) {
                BlockPos west = SurfaceResolver.resolveSurfacePos(world, x - radius, sz);
                int westScore = scoreCourseAnchor(world, west, x, z, course, courseBounds, rejectedAnchorKeys);
                if (westScore < bestScore) {
                    bestScore = westScore;
                    best = west;
                }

                BlockPos east = SurfaceResolver.resolveSurfacePos(world, x + radius, sz);
                int eastScore = scoreCourseAnchor(world, east, x, z, course, courseBounds, rejectedAnchorKeys);
                if (eastScore < bestScore) {
                    bestScore = eastScore;
                    best = east;
                }
            }
        }

        return SurfaceResolver.refineLandCandidate(world, best, x, z);
    }

    private static int scoreCourseAnchor(
            ServerWorld world,
            BlockPos candidate,
            int targetX,
            int targetZ,
            Course course,
            CourseBounds courseBounds,
            Set<Long> rejectedAnchorKeys
    ) {
        int score = SurfaceResolver.scoreSurface(world, candidate, targetX, targetZ, true) + CoursePlacementService.localWaterPenalty(world, candidate);
        if (rejectedAnchorKeys.contains(anchorClusterKey(candidate))) {
            score += 2_000_000;
        }

        double waterRatio = estimateProjectedWaterRatio(world, course, candidate, courseBounds);
        score += (int) Math.round(waterRatio * COURSE_ANCHOR_WATER_RATIO_SCORE_WEIGHT);

        if (waterRatio > COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO) {
            score += COURSE_ANCHOR_WATER_REJECT_PENALTY;
            score += (int) Math.round((waterRatio - COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO) * 100000.0);
        }

        return score;
    }

    static double estimateProjectedWaterRatio(
            ServerWorld world,
            Course course,
            BlockPos anchor,
            CourseBounds courseBounds
    ) {
        if (course.holes().isEmpty()) {
            return 0.0;
        }

        int offsetX = anchor.getX() - courseBounds.centerX();
        int offsetZ = anchor.getZ() - courseBounds.centerZ();
        int waterSamples = 0;
        int totalSamples = 0;

        for (Hole hole : course.holes()) {
            waterSamples += projectedWaterSample(world, hole.tee().x(), hole.tee().z(), offsetX, offsetZ);
            waterSamples += projectedWaterSample(world, hole.basket().x(), hole.basket().z(), offsetX, offsetZ);
            waterSamples += projectedWaterSample(
                    world,
                    (hole.tee().x() + hole.basket().x()) / 2,
                    (hole.tee().z() + hole.basket().z()) / 2,
                    offsetX,
                    offsetZ
            );
            totalSamples += 3;
        }

        return totalSamples == 0 ? 0.0 : (waterSamples / (double) totalSamples);
    }

    private static int projectedWaterSample(ServerWorld world, int templateX, int templateZ, int offsetX, int offsetZ) {
        int worldX = templateX + offsetX;
        int worldZ = templateZ + offsetZ;
        return CoursePlacementService.isWaterCrossingColumn(world, worldX, worldZ) ? 1 : 0;
    }

    static long anchorClusterKey(BlockPos pos) {
        // Use 16-block clusters so that rejecting a refined anchor also penalises
        // nearby raw candidates that refine to the same spot.
        long cx = (long) (pos.getX() >> 4);
        long cz = (long) (pos.getZ() >> 4);
        return (cx << 32) ^ (cz & 0xffffffffL);
    }

    record CourseBounds(int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ) {
    }
}
