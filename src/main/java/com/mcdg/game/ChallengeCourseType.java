package com.mcdg.game;

/**
 * Types of challenge courses that can be discovered and played.
 */
public enum ChallengeCourseType {
    LOST_COURSE("Lost Course", "Hidden course with treasure chest reward"),
    BOSS_HOLE("Boss Hole", "Single challenging hole guarded by mobs"),
    TIME_TRIAL("Time Trial", "Complete under time limit for bonus"),
    ACCURACY_CHALLENGE("Accuracy Challenge", "Hit small targets for points"),
    DISTANCE_CHALLENGE("Distance Challenge", "Throw for maximum distance");

    private final String displayName;
    private final String description;

    ChallengeCourseType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}