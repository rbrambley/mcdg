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
    private static final int HOLE_GRID_COLUMNS = 3;
    private static final int HOLE_X_SPACING = 220;
    private static final int HOLE_Z_SPACING = 200;
    private static final int TEE_JITTER = 32;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;
    private static final int MAX_HOLE_ATTEMPTS = 25;
    private static final int MAX_FORCED_PAR5_ATTEMPTS = 90;
    private static final int BASE_HEIGHT = 64;
    private static final int RETURN_HUB_OFFSET = 48;

    private final HoleLayoutValidator validator = new HoleLayoutValidator();

    @Override
    public Course generate(long seed, int holeCount) {
        return generate(seed, holeCount, 0.0f);
    }

    @Override
    public Course generate(long seed, int holeCount, float facingYaw) {
        if (holeCount < 1) {
            throw new IllegalArgumentException("holeCount must be >= 1");
        }

        Random random = new Random(seed);
        List<Hole> holes = new ArrayList<>(holeCount);

        int baseX = 0;
        int baseZ = 0;
        int forcedPar5Index = random.nextInt(holeCount) + 1;
        // Never force the final hole to be Par 5 so the return-to-hub hole can be any par
        if (forcedPar5Index == holeCount && holeCount >= 9) {
            forcedPar5Index = random.nextInt(holeCount - 1) + 1;
        }

        for (int i = 1; i <= holeCount; i++) {
            boolean forcePar5 = i == forcedPar5Index;
            Hole hole = generateHoleWithRetries(random, baseX, baseZ, i, holes, forcePar5, holeCount);
            holes.add(hole);
        }

        if (facingYaw != 0.0f) {
            holes = rotateHoles(holes, facingYaw);
        }

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

    private Hole generateHoleWithRetries(Random random, int baseX, int baseZ, int holeIndex, List<Hole> placedHoles, boolean forcePar5, int holeCount) {
        int maxAttempts = forcePar5 ? MAX_FORCED_PAR5_ATTEMPTS : MAX_HOLE_ATTEMPTS;
        int columnIndex = (holeIndex - 1) % HOLE_GRID_COLUMNS;
        int rowIndex = (holeIndex - 1) / HOLE_GRID_COLUMNS;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            int teeX = baseX + (columnIndex * HOLE_X_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;
            int teeZ = baseZ + (rowIndex * HOLE_Z_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;

            int targetDistanceFeet = forcePar5
                    ? randomRange(random, PAR5_MIN_FEET, PAR5_MAX_FEET)
                    : randomRange(random, MIN_DISTANCE_FEET, PAR4_MAX_FEET);
            int targetDistanceBlocks = Math.max(1, Math.round(targetDistanceFeet / 3.0f));

            double angle = resolveBasketAngle(random, holeIndex, columnIndex, rowIndex, holeCount);

            int basketX;
            int basketZ;

            if (holeIndex == holeCount && holeCount >= 9) {
                // Final hole: basket placed near course center (hub) so the round loops back
                int centerX = ((HOLE_GRID_COLUMNS - 1) * HOLE_X_SPACING) / 2;
                int centerZ = (((holeCount - 1) / HOLE_GRID_COLUMNS) * HOLE_Z_SPACING) / 2;
                int hubOffsetX = random.nextInt((RETURN_HUB_OFFSET * 2) + 1) - RETURN_HUB_OFFSET;
                int hubOffsetZ = random.nextInt((RETURN_HUB_OFFSET * 2) + 1) - RETURN_HUB_OFFSET;
                basketX = centerX + hubOffsetX;
                basketZ = centerZ + hubOffsetZ;
            } else {
                basketX = teeX + (int) Math.round(Math.cos(angle) * targetDistanceBlocks);
                basketZ = teeZ + (int) Math.round(Math.sin(angle) * targetDistanceBlocks);
            }

            int fairwayWidth = randomRange(random, MIN_FAIRWAY_WIDTH, MAX_FAIRWAY_WIDTH);
            int basketHeight = 1 + random.nextInt(2);
            int actualDistanceFeet = validator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);

            if (!validator.isDistanceValid(actualDistanceFeet, MIN_DISTANCE_FEET, MAX_DISTANCE_FEET)) {
                continue;
            }

            if (forcePar5 && actualDistanceFeet < PAR5_MIN_FEET) {
                continue;
            }

            if (!forcePar5 && holeIndex != holeCount && computePar(actualDistanceFeet) >= 5) {
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
                int teeX = baseX + (columnIndex * HOLE_X_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;
                int teeZ = baseZ + (rowIndex * HOLE_Z_SPACING) + random.nextInt((TEE_JITTER * 2) + 1) - TEE_JITTER;

                int targetDistanceFeet = randomRange(random, PAR4_MIN_FEET, PAR4_MAX_FEET);
                int targetDistanceBlocks = Math.max(1, Math.round(targetDistanceFeet / 3.0f));
                double angle = resolveBasketAngle(random, holeIndex, columnIndex, rowIndex, holeCount);
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

    private static double resolveBasketAngle(Random random, int holeIndex, int columnIndex, int rowIndex, int holeCount) {
        if (holeIndex == holeCount && holeCount >= 9) {
            // Final return hole: angle from tee toward course center (hub)
            int centerX = ((HOLE_GRID_COLUMNS - 1) * HOLE_X_SPACING) / 2;
            int centerZ = (((holeCount - 1) / HOLE_GRID_COLUMNS) * HOLE_Z_SPACING) / 2;
            int col = (holeIndex - 1) % HOLE_GRID_COLUMNS;
            int row = (holeIndex - 1) / HOLE_GRID_COLUMNS;
            int teeX = col * HOLE_X_SPACING;
            int teeZ = row * HOLE_Z_SPACING;
            double baseAngle = Math.atan2(centerZ - teeZ, centerX - teeX);
            return baseAngle + (random.nextDouble() - 0.5) * 0.4;
        }

        // Constrained angles based on hole position to create a flowing loop shape
        double baseAngle;
        switch (holeIndex) {
            case 1: baseAngle = -0.35; break; // left-forward
            case 2: baseAngle = 0.15; break;  // slight right of forward
            case 3: baseAngle = 0.55; break;  // right-forward
            case 4: baseAngle = -0.65; break; // left
            case 5: baseAngle = 0.05; break;  // forward
            case 6: baseAngle = 0.65; break;  // right
            case 7: baseAngle = -0.9; break;  // far left
            case 8: baseAngle = 0.9; break;   // far right
            default: baseAngle = 0.0; break;
        }

        // Add ~±18° jitter
        return baseAngle + (random.nextDouble() - 0.5) * 0.6;
    }

    private static List<Hole> rotateHoles(List<Hole> holes, float facingYaw) {
        double yawRad = Math.toRadians(facingYaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightYaw = facingYaw + 90.0f;
        double rightRad = Math.toRadians(rightYaw);
        double rightX = -Math.sin(rightRad);
        double rightZ = Math.cos(rightRad);

        List<Hole> rotated = new ArrayList<>(holes.size());
        for (Hole hole : holes) {
            TeePoint tee = hole.tee();
            BasketPoint basket = hole.basket();

            int[] rTee = rotate(tee.x(), tee.z(), fwdX, fwdZ, rightX, rightZ);
            int[] rBasket = rotate(basket.x(), basket.z(), fwdX, fwdZ, rightX, rightZ);

            List<FairwaySegment> segments = hole.fairwaySegments().stream()
                    .map(seg -> {
                        int[] a = rotate(seg.startX(), seg.startZ(), fwdX, fwdZ, rightX, rightZ);
                        int[] b = rotate(seg.endX(), seg.endZ(), fwdX, fwdZ, rightX, rightZ);
                        return new FairwaySegment(a[0], a[1], b[0], b[1], seg.width());
                    })
                    .toList();

            rotated.add(new Hole(
                    hole.index(),
                    hole.par(),
                    hole.distanceFeet(),
                    new TeePoint(rTee[0], tee.y(), rTee[1]),
                    new BasketPoint(rBasket[0], basket.y(), rBasket[1], basket.basketHeight()),
                    segments,
                    hole.signatureType()
            ));
        }
        return rotated;
    }

    private static int[] rotate(int x, int z, double fwdX, double fwdZ, double rightX, double rightZ) {
        int rx = (int) Math.round(x * rightX + z * fwdX);
        int rz = (int) Math.round(x * rightZ + z * fwdZ);
        return new int[]{rx, rz};
    }

    private static String generateCourseName(Random random) {
        String[] first = {"Cedar", "Granite", "Maple", "Pine", "Redwood", "Summit"};
        String[] second = {"Ridge", "Run", "Grove", "Valley", "Meadow", "Loop"};
        return first[random.nextInt(first.length)] + " " + second[random.nextInt(second.length)];
    }
}
