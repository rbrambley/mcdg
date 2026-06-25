package com.mcdg.game;

/**
 * Crafting progression tiers for disc golf discs.
 * Each tier defines flight modifiers and durability.
 */
public enum DiscTier {
    TRAINING("Training", 1.0, 1.0, 0, 1.0, 0.0, "YELLOW"),
    WOODEN("Wooden", 0.8, 0.8, 50, 1.0, 0.0, "GOLD"),
    STONE("Stone", 0.9, 0.9, 100, 1.0, 0.0, "GRAY"),
    IRON("Iron", 1.0, 1.0, 200, 1.0, 0.0, "WHITE"),
    GOLD("Gold", 1.1, 0.9, 150, 1.1, 0.0, "GOLD"),
    DIAMOND("Diamond", 1.2, 1.2, 400, 1.0, 0.5, "AQUA"),
    NETHERITE("Netherite", 1.3, 1.3, 600, 1.0, 0.75, "DARK_GRAY");

    private final String displayName;
    private final double glideMultiplier;
    private final double stabilityMultiplier;
    private final int durability;
    private final double throwSpeedMultiplier;
    private final double windResistance;
    private final String colorName;
    private final DiscStats stats;

    DiscTier(
            String displayName,
            double glideMultiplier,
            double stabilityMultiplier,
            int durability,
            double throwSpeedMultiplier,
            double windResistance,
            String colorName
    ) {
        this.displayName = displayName;
        this.glideMultiplier = glideMultiplier;
        this.stabilityMultiplier = stabilityMultiplier;
        this.durability = durability;
        this.throwSpeedMultiplier = throwSpeedMultiplier;
        this.windResistance = windResistance;
        this.colorName = colorName;
        this.stats = new DiscStats(glideMultiplier, stabilityMultiplier, throwSpeedMultiplier, windResistance);
    }

    public String displayName() {
        return displayName;
    }

    public double glideMultiplier() {
        return glideMultiplier;
    }

    public double stabilityMultiplier() {
        return stabilityMultiplier;
    }

    public int durability() {
        return durability;
    }

    public double throwSpeedMultiplier() {
        return throwSpeedMultiplier;
    }

    public double windResistance() {
        return windResistance;
    }

    public String colorName() {
        return colorName;
    }

    public DiscStats stats() {
        return stats;
    }
}
