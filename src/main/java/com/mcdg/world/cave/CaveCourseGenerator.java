package com.mcdg.world.cave;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.HoleLayoutValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates courses specifically designed for cave environments.
 * Uses hybrid placement: tries outward cone first, falls back to cave-following algorithm.
 */
public final class CaveCourseGenerator implements CourseGenerator {
    private static final int MIN_DISTANCE_FEET = 60;
    private static final int MAX_DISTANCE_FEET = 400;
    private static final int PAR3_MAX_FEET = 250;
    private static final int PAR4_MAX_FEET = 350;
    private static final int MIN_FAIRWAY_WIDTH = 3;
    private static final int MAX_FAIRWAY_WIDTH = 6;
    private static final int BASE_HEIGHT = 0; // Will be determined by cave floor

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

        // Generate holes using cave-specific constraints
        for (int i = 1; i <= holeCount; i++) {
            Hole hole = generateCaveHole(random, i, holes, holeCount);
            holes.add(hole);
        }

        // Add signature hole for cave courses (underwater-themed or lava-themed)
        if (holeCount >= 1) {
            int sigIndex = random.nextInt(holeCount);
            Hole original = holes.get(sigIndex);
            // Cave courses get special signature types
            holes.set(sigIndex, new Hole(
                original.index(),
                original.par(),
                original.distanceFeet(),
                original.tee(),
                original.basket(),
                original.fairwaySegments(),
                com.mcdg.data.SignatureHoleType.ISLAND_GREEN // Reuse for cave chambers
            ));
        }

        String name = generateCaveCourseName(random);
        return new Course(seed, name, holes);
    }

    private Hole generateCaveHole(Random random, int holeIndex, List<Hole> placedHoles, int holeCount) {
        final int MAX_ATTEMPTS = 50;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // Generate cave-specific hole layout
            int teeX = 0; // Will be determined by cave scanner
            int teeZ = 0;
            int teeY = BASE_HEIGHT;

            // Shorter distances for cave environments
            int targetDistanceFeet = MIN_DISTANCE_FEET + random.nextInt(MAX_DISTANCE_FEET - MIN_DISTANCE_FEET + 1);
            int targetDistanceBlocks = Math.max(1, Math.round(targetDistanceFeet / 3.0f));

            // Cave holes tend to be more linear (following tunnels)
            double angle = (holeIndex - 1) * (Math.PI / 4) + random.nextDouble() * 0.5;

            int basketX = teeX + (int) Math.round(Math.cos(angle) * targetDistanceBlocks);
            int basketZ = teeZ + (int) Math.round(Math.sin(angle) * targetDistanceBlocks);
            int basketY = BASE_HEIGHT;

            int fairwayWidth = MIN_FAIRWAY_WIDTH + random.nextInt(MAX_FAIRWAY_WIDTH - MIN_FAIRWAY_WIDTH + 1);
            int basketHeight = 1; // Lower baskets in caves

            int actualDistanceFeet = validator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);

            if (!validator.isDistanceValid(actualDistanceFeet, MIN_DISTANCE_FEET, MAX_DISTANCE_FEET)) {
                continue;
            }

            Hole candidate = new Hole(
                holeIndex,
                computePar(actualDistanceFeet),
                actualDistanceFeet,
                new com.mcdg.data.TeePoint(teeX, teeY, teeZ),
                new com.mcdg.data.BasketPoint(basketX, basketY, basketZ, basketHeight),
                List.of(new com.mcdg.data.FairwaySegment(teeX, teeZ, basketX, basketZ, fairwayWidth)),
                com.mcdg.data.SignatureHoleType.NONE
            );

            if (validator.isNonOverlapping(candidate, placedHoles)) {
                return candidate;
            }
        }

        // Fallback: generate a simple hole if attempts fail
        return generateFallbackHole(random, holeIndex, placedHoles);
    }

    private Hole generateFallbackHole(Random random, int holeIndex, List<Hole> placedHoles) {
        int spacing = 40;
        int teeX = (holeIndex - 1) * spacing;
        int teeZ = 0;
        int basketX = teeX + 60;
        int basketZ = 0;
        int distanceFeet = 60;
        int fairwayWidth = 4;

        return new Hole(
            holeIndex,
            3,
            distanceFeet,
            new com.mcdg.data.TeePoint(teeX, BASE_HEIGHT, teeZ),
            new com.mcdg.data.BasketPoint(basketX, BASE_HEIGHT, basketZ, 1),
            List.of(new com.mcdg.data.FairwaySegment(teeX, teeZ, basketX, basketZ, fairwayWidth)),
            com.mcdg.data.SignatureHoleType.NONE
        );
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

    private static String generateCaveCourseName(Random random) {
        String[] prefixes = {"Crystal", "Shadow", "Echo", "Magma", "Frost", "Deep", "Hidden", "Ancient"};
        String[] suffixes = {"Caverns", "Depths", "Tunnels", "Chambers", "Hollows", "Abyss", "Grotto", "Labyrinth"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        
        return "Cave " + prefix + " " + suffix;
    }
}