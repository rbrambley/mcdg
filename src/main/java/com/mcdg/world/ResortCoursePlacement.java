package com.mcdg.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Computes terrain-aware course anchor points around a resort.
 */
public final class ResortCoursePlacement {

    private static final int MIN_RESORT_DISTANCE = 70; // blocks from resort center
    private static final int MIN_COURSE_SPACING = 200; // blocks between course anchors
    private static final int CANDIDATE_COUNT = 3;

    private ResortCoursePlacement() {}

    public static List<Candidate> selectCourseAnchors(ServerWorld world, BlockPos resortCenter, Random random) {
        List<Candidate> candidates = generateCandidates(world, resortCenter, random);
        return pickBestNonOverlapping(candidates);
    }

    private static List<Candidate> generateCandidates(ServerWorld world, BlockPos resortCenter, Random random) {
        List<Candidate> result = new ArrayList<>();

        int[] distances = { 90, 110, 130, 150, 170 };
        int angleCount = 12;

        for (int distance : distances) {
            for (int i = 0; i < angleCount; i++) {
                double angle = (2.0 * Math.PI * i) / angleCount + (random.nextDouble() * 0.3 - 0.15);
                int cx = resortCenter.getX() + (int) Math.round(Math.cos(angle) * distance);
                int cz = resortCenter.getZ() + (int) Math.round(Math.sin(angle) * distance);

                BlockPos surface = SurfaceResolver.resolveSurfacePos(world, cx, cz);
                int score = scoreCandidate(world, resortCenter, surface);
                result.add(new Candidate(surface, score, angle));
            }
        }

        return result;
    }

    private static int scoreCandidate(ServerWorld world, BlockPos resortCenter, BlockPos candidate) {
        int score = 0;
        int dx = Math.abs(candidate.getX() - resortCenter.getX());
        int dz = Math.abs(candidate.getZ() - resortCenter.getZ());
        int dist = (int) Math.sqrt(dx * dx + dz * dz);

        if (dist < MIN_RESORT_DISTANCE) {
            score += 10000; // heavily penalize too close
        } else {
            score += (dist - MIN_RESORT_DISTANCE) * 2; // slight preference for farther
        }

        // Penalize water
        if (SurfaceAdaptationHelper.isUnsafeSurface(world, candidate)) {
            score += 5000;
        }

        // Check nearby water columns
        int waterCount = 0;
        int checkRadius = 8;
        for (int ox = -checkRadius; ox <= checkRadius; ox += 2) {
            for (int oz = -checkRadius; oz <= checkRadius; oz += 2) {
                if (CoursePlacementService.isWaterCrossingColumn(world, candidate.getX() + ox, candidate.getZ() + oz)) {
                    waterCount++;
                }
            }
        }
        score += waterCount * 200;

        // Check height variance (flatness)
        int heightVariance = computeHeightVariance(world, candidate.getX(), candidate.getZ(), 6);
        score += heightVariance * 50;

        return score;
    }

    private static int computeHeightVariance(ServerWorld world, int x, int z, int radius) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int samples = 0;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                BlockPos pos = SurfaceResolver.resolveSurfacePos(world, x + dx, z + dz);
                int y = pos.getY();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                samples++;
            }
        }
        if (samples == 0) return 0;
        return maxY - minY;
    }

    private static List<Candidate> pickBestNonOverlapping(List<Candidate> candidates) {
        candidates.sort(Comparator.comparingInt(Candidate::score));

        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (selected.size() >= CANDIDATE_COUNT) {
                break;
            }
            boolean tooClose = false;
            for (Candidate other : selected) {
                int dx = candidate.pos().getX() - other.pos().getX();
                int dz = candidate.pos().getZ() - other.pos().getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                if (dist < MIN_COURSE_SPACING) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                selected.add(candidate);
            }
        }

        return selected;
    }

    public record Candidate(BlockPos pos, int score, double angle) {}
}
