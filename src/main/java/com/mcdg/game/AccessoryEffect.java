package com.mcdg.game;

import net.minecraft.util.Formatting;

/**
 * Effects granted by disc golf accessories.
 */
public enum AccessoryEffect {
    GRIP_STABILITY("Grip Stability", "DARK_GREEN", 0.08f),
    DURABILITY_PRESERVE("Durability Preserve", "AQUA", 0.15f),
    WIND_SENSE("Wind Sense", "YELLOW", 0.10f),
    RANGE_FINDER("Range Finder", "GOLD", 0.0f);

    private final String displayName;
    private final String colorName;
    private final float perLevelMultiplier;

    AccessoryEffect(String displayName, String colorName, float perLevelMultiplier) {
        this.displayName = displayName;
        this.colorName = colorName;
        this.perLevelMultiplier = perLevelMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public Formatting color() {
        Formatting formatting = Formatting.byName(colorName);
        return formatting == null ? Formatting.WHITE : formatting;
    }

    public float perLevelMultiplier() {
        return perLevelMultiplier;
    }

    public int maxLevel() {
        return 1;
    }
}