package com.mcdg.game;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable flight statistics for a disc tier.
 * Applied during trajectory calculation to modify glide, fade, throw speed, and wind resistance.
 */
public record DiscStats(
        double glideMultiplier,
        double stabilityMultiplier,
        double throwSpeedMultiplier,
        double windResistance
) {
    public static final DiscStats DEFAULT = new DiscStats(1.0, 1.0, 1.0, 0.0);

    public DiscStats {
        if (glideMultiplier < 0.0) {
            throw new IllegalArgumentException("glideMultiplier must be non-negative");
        }
        if (stabilityMultiplier < 0.0) {
            throw new IllegalArgumentException("stabilityMultiplier must be non-negative");
        }
        if (throwSpeedMultiplier < 0.0) {
            throw new IllegalArgumentException("throwSpeedMultiplier must be non-negative");
        }
        if (windResistance < 0.0 || windResistance > 1.0) {
            throw new IllegalArgumentException("windResistance must be between 0.0 and 1.0");
        }
    }

    /**
     * Returns a wind velocity reduced by this disc's wind resistance.
     * 0.0 resistance means full wind effect; 1.0 means no wind effect.
     */
    public Vec3d applyWindResistance(Vec3d wind) {
        double factor = 1.0 - windResistance;
        return new Vec3d(wind.x * factor, wind.y * factor, wind.z * factor);
    }
}
