package com.mcdg.world;

import com.mcdg.data.BasketPoint;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.data.TeePoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SeededCourseGenerator implements CourseGenerator {
    private static final int MIN_DISTANCE_FEET = 180;
    private static final int MAX_DISTANCE_FEET = 1200;
    private static final int PAR3_MAX_FEET = 400;
    private static final int PAR4_MAX_FEET = 700;
    private static final int PAR5_MIN_FEET = 701;
    private static final int PAR5_MAX_FEET = 1200;
    private static final int PAR4_MIN_FEET = 401;
    private static final int HOLE_X_SPACING = 220;
    private static final int HOLE_Z_SPACING = 180;
    private static final int TEE_JITTER = 80;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;
    private static final int MAX_HOLE_ATTEMPTS = 25;
    private static final int MAX_FORCED_PAR5_ATTEMPTS = 90;
    private static final int BASE_HEIGHT = 64;

    private final HoleLayoutValidator validator = new HoleLayoutValidator();

    @Override
    public Course generate(long seed, int holeCount) {
        if (holeCount < 1) {
            throw new IllegalArgumentException("holeCount must be >= 1");
        }

        Random random = new Random(seed);
        List<Hole> holes = new ArrayList<>(holeCount);

        int baseX = random.nextInt(800) - 400;
        int baseZ = random.nextInt(800) - 400;
        int forcedPar5Index = random.nextInt(holeCount) + 1;

        for (int i = 1; i <= holeCount; i++) {
            boolean forcePar5 = i == forcedPar5Index;
            Hole hole = generateHoleWithRetries(random, baseX, baseZ, i, holes, forcePar5);
            holes.add(hole);
        }

        // Pick exactly one signature hole per course, seeded for determinism.
        // Keep v1 explicit: always use Island Green so the signature identity is obvious in play.
        if (holeCount >= 1) {
            int sigIndex = random.nextInt(holeCount);
            SignatureHoleType sigType = SignatureHoleType.ISLAND_GREEN;
            Hole original = holes.get(sigIndex);
            holes.set(sigIndex, new Hole(
                original.index(),
                original.par(),
                original.distanceFeet(),
                original.tee(),
                original.basket(),
                original.fairwaySegments(),
                sigType
            ));
        }

        String name = generateCourseName(random);
        return new Course(seed, name, holes);
    }

    private static int computePar(int distanceFeet) {
        if (distanceFeet <= PAR3_MAX_FEET) {
            return 3;
        }
        if (distanceFeet <= PAR4_MAX_FEET) {
            return 4;
        }
        return 5;
    }

    private static int randomRange(Random random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt((maxInclusive - minInclusive) + 1);
    }

    private Hole generateHoleWithRetries(Random random, int baseX, int baseZ, int holeIndex, List<Hole> placedHoles, boolean forcePar5) {
        int maxAttempts = forcePar5 ? MAX_FORCED_PAR5_ATTEMPTS : MAX_HOLE_ATTEMPTS;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            int teeX = baseX + (holeIndex * HOLE_X_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;
            int teeZ = baseZ + (holeIndex * HOLE_Z_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;

            int targetDistanceFeet = forcePar5
                    ? randomRange(random, PAR5_MIN_FEET, PAR5_MAX_FEET)
                    : randomRange(random, MIN_DISTANCE_FEET, MAX_DISTANCE_FEET);
            int targetDistanceBlocks = Math.max(1, Math.round(targetDistanceFeet / 3.0f));
            double angle = random.nextDouble() * Math.PI * 2.0;
            int basketX = teeX + (int) Math.round(Math.cos(angle) * targetDistanceBlocks);
            int basketZ = teeZ + (int) Math.round(Math.sin(angle) * targetDistanceBlocks);

            int fairwayWidth = randomRange(random, MIN_FAIRWAY_WIDTH, MAX_FAIRWAY_WIDTH);
            int basketHeight = 1 + random.nextInt(2);
            int actualDistanceFeet = validator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);

            if (!validator.isDistanceValid(actualDistanceFeet, MIN_DISTANCE_FEET, MAX_DISTANCE_FEET)) {
                continue;
            }

            if (forcePar5 && actualDistanceFeet < PAR5_MIN_FEET) {
                continue;
            }

            Hole candidate = new Hole(
                    holeIndex,
                    computePar(actualDistanceFeet),
                    actualDistanceFeet,
                    new TeePoint(teeX, BASE_HEIGHT, teeZ),
                    new BasketPoint(basketX, BASE_HEIGHT, basketZ, basketHeight),
                    List.of(new FairwaySegment(teeX, teeZ, basketX, basketZ, fairwayWidth)),
                    SignatureHoleType.NONE
            );

            if (validator.isNonOverlapping(candidate, placedHoles)) {
                return candidate;
            }
        }

        // Last resort for forced Par 5 slot: generate a stable Par 4 instead of failing the whole round.
        if (forcePar5) {
            for (int attempt = 1; attempt <= MAX_HOLE_ATTEMPTS; attempt++) {
                int teeX = baseX + (holeIndex * HOLE_X_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;
                int teeZ = baseZ + (holeIndex * HOLE_Z_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;

                int targetDistanceFeet = randomRange(random, PAR4_MIN_FEET, PAR4_MAX_FEET);
                int targetDistanceBlocks = Math.max(1, Math.round(targetDistanceFeet / 3.0f));
                double angle = random.nextDouble() * Math.PI * 2.0;
                int basketX = teeX + (int) Math.round(Math.cos(angle) * targetDistanceBlocks);
                int basketZ = teeZ + (int) Math.round(Math.sin(angle) * targetDistanceBlocks);

                int fairwayWidth = randomRange(random, MIN_FAIRWAY_WIDTH, MAX_FAIRWAY_WIDTH);
                int basketHeight = 1 + random.nextInt(2);
                int actualDistanceFeet = validator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);

                if (actualDistanceFeet < PAR4_MIN_FEET || actualDistanceFeet > PAR4_MAX_FEET) {
                    continue;
                }

                Hole fallback = new Hole(
                        holeIndex,
                        4,
                        actualDistanceFeet,
                        new TeePoint(teeX, BASE_HEIGHT, teeZ),
                        new BasketPoint(basketX, BASE_HEIGHT, basketZ, basketHeight),
                        List.of(new FairwaySegment(teeX, teeZ, basketX, basketZ, fairwayWidth)),
                        SignatureHoleType.NONE
                );

                if (validator.isNonOverlapping(fallback, placedHoles)) {
                    return fallback;
                }
            }
        }

        throw new IllegalStateException("Failed to generate valid hole layout for hole " + holeIndex + " after " + maxAttempts + " attempts.");
    }

    private static String generateCourseName(Random random) {
        String[] first = {"Cedar", "Granite", "Maple", "Pine", "Redwood", "Summit"};
        String[] second = {"Ridge", "Run", "Grove", "Valley", "Meadow", "Loop"};
        return first[random.nextInt(first.length)] + " " + second[random.nextInt(second.length)];
    }
}
