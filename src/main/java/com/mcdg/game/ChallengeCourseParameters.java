package com.mcdg.game;

/**
 * Parameters for generating challenge-specific courses.
 * Each challenge type has unique characteristics that affect course generation.
 */
public record ChallengeCourseParameters(
    int holeCount,
    int minDistanceFeet,
    int maxDistanceFeet,
    int minPar,
    int maxPar,
    int minFairwayWidth,
    int maxFairwayWidth,
    double hazardMultiplier,
    double elevationMultiplier,
    boolean forceSinglePar,
    int forcedPar
) {
    public ChallengeCourseParameters {
        if (holeCount < 1) {
            throw new IllegalArgumentException("holeCount must be >= 1");
        }
        if (minDistanceFeet < 1) {
            throw new IllegalArgumentException("minDistanceFeet must be >= 1");
        }
        if (maxDistanceFeet < minDistanceFeet) {
            throw new IllegalArgumentException("maxDistanceFeet must be >= minDistanceFeet");
        }
        if (minPar < 2) {
            throw new IllegalArgumentException("minPar must be >= 2");
        }
        if (maxPar < minPar) {
            throw new IllegalArgumentException("maxPar must be >= minPar");
        }
        if (minFairwayWidth < 1) {
            throw new IllegalArgumentException("minFairwayWidth must be >= 1");
        }
        if (maxFairwayWidth < minFairwayWidth) {
            throw new IllegalArgumentException("maxFairwayWidth must be >= minFairwayWidth");
        }
        if (hazardMultiplier < 0) {
            throw new IllegalArgumentException("hazardMultiplier must be >= 0");
        }
        if (elevationMultiplier < 0) {
            throw new IllegalArgumentException("elevationMultiplier must be >= 0");
        }
        if (forceSinglePar && (forcedPar < minPar || forcedPar > maxPar)) {
            throw new IllegalArgumentException("forcedPar must be within par range");
        }
    }
    /**
     * Standard parameters for Lost Courses (9-hole standard courses).
     */
    public static ChallengeCourseParameters lostCourse() {
        return new ChallengeCourseParameters(
            9,              // holeCount
            180,            // minDistanceFeet
            1200,           // maxDistanceFeet
            3,              // minPar
            5,              // maxPar
            4,              // minFairwayWidth
            10,             // maxFairwayWidth
            1.0,            // hazardMultiplier
            1.0,            // elevationMultiplier
            false,          // forceSinglePar
            0               // forcedPar
        );
    }

    /**
     * Parameters for Boss Holes (single challenging hole).
     */
    public static ChallengeCourseParameters bossHole() {
        return new ChallengeCourseParameters(
            1,              // holeCount
            600,            // minDistanceFeet
            1200,           // maxDistanceFeet
            4,              // minPar
            5,              // maxPar
            8,              // minFairwayWidth (wider for accessibility)
            12,             // maxFairwayWidth
            1.5,            // hazardMultiplier (enhanced hazards)
            1.2,            // elevationMultiplier
            true,           // forceSinglePar
            5               // forcedPar (always Par 5)
        );
    }

    /**
     * Parameters for Time Trials (3-hole speed courses).
     */
    public static ChallengeCourseParameters timeTrial() {
        return new ChallengeCourseParameters(
            3,              // holeCount
            180,            // minDistanceFeet
            400,            // maxDistanceFeet (shorter for speed)
            3,              // minPar
            3,              // maxPar (all Par 3)
            4,              // minFairwayWidth
            10,             // maxFairwayWidth
            0.5,            // hazardMultiplier (minimal hazards)
            0.8,            // elevationMultiplier (flatter for speed)
            false,          // forceSinglePar
            0               // forcedPar
        );
    }

    /**
     * Parameters for Accuracy Challenges (5-hole precision courses).
     */
    public static ChallengeCourseParameters accuracyChallenge() {
        return new ChallengeCourseParameters(
            5,              // holeCount
            150,            // minDistanceFeet
            300,            // maxDistanceFeet (very short)
            3,              // minPar
            3,              // maxPar (all Par 3)
            5,              // minFairwayWidth (narrower for precision)
            6,              // maxFairwayWidth
            2.0,            // hazardMultiplier (dense hazards)
            1.0,            // elevationMultiplier
            false,          // forceSinglePar
            0               // forcedPar
        );
    }

    /**
     * Parameters for Distance Challenges (single maximum distance hole).
     */
    public static ChallengeCourseParameters distanceChallenge() {
        return new ChallengeCourseParameters(
            1,              // holeCount
            1000,           // minDistanceFeet
            1500,           // maxDistanceFeet (extreme distance)
            5,              // minPar
            5,              // maxPar
            10,             // minFairwayWidth (wide for forgiveness)
            14,             // maxFairwayWidth
            0.3,            // hazardMultiplier (minimal for pure distance)
            0.5,            // elevationMultiplier (flatter)
            true,           // forceSinglePar
            5               // forcedPar (always Par 5)
        );
    }

    /**
     * Gets parameters for a specific challenge type.
     */
    public static ChallengeCourseParameters forType(ChallengeCourseType type) {
        return switch (type) {
            case LOST_COURSE -> lostCourse();
            case BOSS_HOLE -> bossHole();
            case TIME_TRIAL -> timeTrial();
            case ACCURACY_CHALLENGE -> accuracyChallenge();
            case DISTANCE_CHALLENGE -> distanceChallenge();
        };
    }
}