package com.mcdg.game;

import net.minecraft.util.Formatting;

/**
 * Skill unlocks earned through player progression.
 */
public enum SkillUnlock {
    POWER_CONTROL("Power Control", "Earn 100 MCDG skill XP", "RED", 100),
    RELEASE_CONTROL("Release Control", "Complete 10 rounds", "AQUA", 10),
    WIND_READING("Wind Reading", "Throw 500 discs", "YELLOW", 500),
    FOCUS("Focus", "Land 50 throws within 10ft of the basket", "GREEN", 50),
    DISC_MASTERY("Disc Mastery", "Throw one of each tiered disc", "LIGHT_PURPLE", 1);

    private final String displayName;
    private final String description;
    private final String colorName;
    private final int requiredCount;

    SkillUnlock(String displayName, String description, String colorName, int requiredCount) {
        this.displayName = displayName;
        this.description = description;
        this.colorName = colorName;
        this.requiredCount = requiredCount;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Formatting color() {
        Formatting formatting = Formatting.byName(colorName);
        return formatting == null ? Formatting.WHITE : formatting;
    }

    public int requiredCount() {
        return requiredCount;
    }

    public String key() {
        return name().toLowerCase();
    }
}