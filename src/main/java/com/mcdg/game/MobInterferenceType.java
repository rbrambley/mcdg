package com.mcdg.game;

/**
 * Types of mob interference behaviors in boss holes.
 */
public enum MobInterferenceType {
    GUARDING_BASKET("Mob guards basket area"),
    PATROL_FAIRWAY("Mob patrols along fairway"),
    HARASS_TEE("Mob harasses from near tee area");

    private final String description;

    MobInterferenceType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}