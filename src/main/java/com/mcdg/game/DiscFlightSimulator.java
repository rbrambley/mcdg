package com.mcdg.game;

/**
 * HUD distance estimation helper.
 *
 * Mirrors TrajectoryCalculator physics so the power-bar preview matches the
 * calculated disc flight. The legacy pearl-based flight simulator has been removed;
 * player throws are now resolved via calculated trajectories only.
 */
public final class DiscFlightSimulator {

    // Physics constants aligned with TrajectoryCalculator for consistent preview distance
    private static final double UPWARD_IMPULSE = 0.06;      // must be less than gravity (0.08)
    private static final double GLIDE_TAPER_START = 0.6;  // start taper at 60% for earlier descent
    private static final int MAX_FLIGHT_TICKS = 300;      // safety cap

    private DiscFlightSimulator() {
        // Utility class
    }

    /**
     * Estimate flight distance for HUD display.
     * Mirrors TrajectoryCalculator physics so the power-bar preview matches what the
     * disc actually does in-game.
     */
    public static int estimateDistance(float charge, ThrowStance stance, float pitch) {
        // Clamp to valid range (same as TrajectoryCalculator)
        float normalizedCharge = Math.min(1.0f, Math.max(0.0f, charge));

        // Compute initial velocity from charge and pitch, mirroring ChargedDiscItem:
        //   velX = -sin(yaw)*cos(pitch)*velocity, velY = -sin(pitch)*velocity
        // For HUD we only care about vx/vy magnitudes; use pitch directly.
        double velocity = 0.7 + normalizedCharge * 1.6;
        double pitchRad = Math.toRadians(pitch);
        double vx = Math.cos(pitchRad) * velocity;
        double vy = -Math.sin(pitchRad) * velocity;
        boolean hasGlide = stance.hasGlide();

        // Glide duration identical to TrajectoryCalculator
        int glideTicks = hasGlide ? 10 + Math.round(normalizedCharge * 40) : 0;

        double x = 0, y = 0;

        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            // Per-tick upward impulse (glide phase only) - mirrors TrajectoryCalculator
            double upwardImpulse = 0.0;
            if (hasGlide) {
                float glideProgress = Math.min(1.0f, tick / (float) glideTicks);
                if (glideProgress > GLIDE_TAPER_START) {
                    double taperProgress = (glideProgress - GLIDE_TAPER_START) / (1.0 - GLIDE_TAPER_START);
                    upwardImpulse = UPWARD_IMPULSE * (1.0 - taperProgress);
                } else {
                    upwardImpulse = UPWARD_IMPULSE;
                }
            }

            // Apply gravity (0.08) and lift - mirrors TrajectoryCalculator
            vy = vy + upwardImpulse - 0.08;

            x += vx;
            y += vy;

            // Termination mirrors TrajectoryCalculator:
            // - Glide stances: only check after glide phase completes, stop when back at launch height
            // - Overhand: stop when 2 blocks below launch height, allowing arc to complete
            if (vy < 0) {
                if (hasGlide) {
                    float glideProgress = Math.min(1.0f, tick / (float) glideTicks);
                    if (glideProgress >= 1.0f && y <= 0) break;
                } else {
                    if (y <= -2.0) break;
                }
            }

            // Safety: stop if horizontal speed negligible
            if (vx < 0.01) break;
        }

        // Convert blocks to feet (1 block = 3.28084 feet)
        return (int) Math.round(x * 3.28084);
    }
}
