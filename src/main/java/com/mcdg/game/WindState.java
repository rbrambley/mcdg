package com.mcdg.game;

import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Wind state for a world, containing velocity, speed, direction, and mode.
 * Used by WindManager to track and apply wind effects to disc flight.
 */
public record WindState(
    Vec3d velocity,           // Wind velocity vector (blocks/tick)
    double speed,             // Magnitude (0.0 - 1.0 scale)
    float directionDegrees,  // 0-360 compass direction (0 = North, 90 = East)
    WindMode mode,           // CALM, NATURAL, FIXED, TOURNAMENT
    boolean isGusting,        // Variable wind conditions
    long lastUpdated,        // Tick timestamp for wind changes
    UUID tournamentId         // Optional: associated tournament for consistency
) {
    /**
     * Creates a calm wind state (no wind).
     */
    public static WindState calm() {
        return new WindState(Vec3d.ZERO, 0.0, 0.0f, WindMode.CALM, false, 0, null);
    }
    
    /**
     * Creates a fixed wind state with given speed and direction.
     */
    public static WindState fixed(double speed, float directionDegrees) {
        Vec3d velocity = calculateVelocity(speed, directionDegrees);
        return new WindState(velocity, speed, directionDegrees, WindMode.FIXED, false, 0, null);
    }
    
    /**
     * Calculates velocity vector from speed and direction.
     * Direction: 0 = North (-Z), 90 = East (+X), 180 = South (+Z), 270 = West (-X)
     */
    static Vec3d calculateVelocity(double speed, float directionDegrees) {
        double radians = Math.toRadians(directionDegrees);
        // Convert compass direction to Minecraft coordinates
        // North (0°) = -Z, East (90°) = +X, South (180°) = +Z, West (270°) = -X
        double x = Math.sin(radians) * speed;
        double z = -Math.cos(radians) * speed;
        return new Vec3d(x, 0, z);
    }
}
