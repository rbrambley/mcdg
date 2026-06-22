package com.mcdg.game;

import net.minecraft.util.Formatting;

/**
 * Custom disc enchantments that alter throw flight physics.
 */
public enum DiscEnchantment {
    GLIDE("Glide", Formatting.AQUA, 0.15f),
    FADE_CONTROL("Fade Control", Formatting.GREEN, 0.20f),
    DISTANCE("Distance", Formatting.GOLD, 0.10f);

    private final String displayName;
    private final Formatting color;
    private final float perLevelMultiplier;

    DiscEnchantment(String displayName, Formatting color, float perLevelMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.perLevelMultiplier = perLevelMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public Formatting color() {
        return color;
    }

    /**
     * Returns the multiplier per level. For example:
     * GLIDE: +15% glide duration per level
     * FADE_CONTROL: -20% curve strength per level
     * DISTANCE: +10% velocity per level
     */
    public float perLevelMultiplier() {
        return perLevelMultiplier;
    }

    public int maxLevel() {
        return 3;
    }

    public String key() {
        return name().toLowerCase();
    }
}
