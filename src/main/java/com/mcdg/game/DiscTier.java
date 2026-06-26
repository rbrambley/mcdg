package com.mcdg.game;

/**
 * Crafting progression tiers for disc golf discs.
 * Each tier defines flight modifiers and durability.
 */
public enum DiscTier {
    TRAINING("Training", 1.0, 1.0, 50, 1.0, 0.0, "YELLOW", 3, 4, 0, 1),
    WOODEN("Wooden", 0.8, 0.8, 50, 1.0, 0.0, "GOLD", 4, 3, -1, 1),
    STONE("Stone", 0.9, 0.9, 100, 1.0, 0.0, "GRAY", 5, 3, 0, 1),
    IRON("Iron", 1.0, 1.0, 200, 1.0, 0.0, "WHITE", 6, 4, 0, 2),
    GOLD("Gold", 1.1, 0.9, 150, 1.1, 0.0, "GOLD", 7, 5, -1, 1),
    DIAMOND("Diamond", 1.2, 1.2, 400, 1.0, 0.5, "AQUA", 9, 6, 0, 3),
    NETHERITE("Netherite", 1.3, 1.3, 600, 1.0, 0.75, "DARK_GRAY", 11, 7, 1, 4);

    private final String displayName;
    private final double glideMultiplier;
    private final double stabilityMultiplier;
    private final int durability;
    private final double throwSpeedMultiplier;
    private final double windResistance;
    private final String colorName;
    private final DiscStats stats;
    private final int flightSpeed;
    private final int flightGlide;
    private final int flightTurn;
    private final int flightFade;

    DiscTier(
            String displayName,
            double glideMultiplier,
            double stabilityMultiplier,
            int durability,
            double throwSpeedMultiplier,
            double windResistance,
            String colorName,
            int flightSpeed,
            int flightGlide,
            int flightTurn,
            int flightFade
    ) {
        this.displayName = displayName;
        this.glideMultiplier = glideMultiplier;
        this.stabilityMultiplier = stabilityMultiplier;
        this.durability = durability;
        this.throwSpeedMultiplier = throwSpeedMultiplier;
        this.windResistance = windResistance;
        this.colorName = colorName;
        this.stats = new DiscStats(glideMultiplier, stabilityMultiplier, throwSpeedMultiplier, windResistance);
        this.flightSpeed = flightSpeed;
        this.flightGlide = flightGlide;
        this.flightTurn = flightTurn;
        this.flightFade = flightFade;
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

    public int flightSpeed() {
        return flightSpeed;
    }

    public int flightGlide() {
        return flightGlide;
    }

    public int flightTurn() {
        return flightTurn;
    }

    public int flightFade() {
        return flightFade;
    }
}
