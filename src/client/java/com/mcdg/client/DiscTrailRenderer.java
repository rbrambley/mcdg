package com.mcdg.client;

import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side renderer for disc throw particle trails and after-throw stats.
 * Receives trajectory data from server and renders particles along the path.
 */
public final class DiscTrailRenderer {
    private static final int TRAIL_DURATION_TICKS = 40; // 2 seconds

    // Active trail data
    private static Vec3d[] pathPoints;
    private static double totalDistanceFt;
    private static double lateralDriftFt;
    private static ThrowStance stance;
    private static ReleaseAngle angle;
    private static int flightTicks;
    private static StrictPenaltyType penaltyType;
    private static int penaltyStrokes;
    private static String penaltyReason;
    private static int obCrossingFeet;
    private static int returnedToFeet;

    // Timing
    private static int trailStartTick;
    private static boolean trailActive = false;

    // Stats display - persists for entire round
    private static boolean statsActive = false;

    private DiscTrailRenderer() {
    }

    /**
     * Start a new trail with the given trajectory data.
     */
    public static void startTrail(
            Vec3d[] pathPoints,
            double totalDistanceFt,
            double lateralDriftFt,
            ThrowStance stance,
            ReleaseAngle angle,
            int flightTicks,
            StrictPenaltyType penaltyType,
            int penaltyStrokes,
            String penaltyReason,
            int obCrossingFeet,
            int returnedToFeet
    ) {
        DiscTrailRenderer.pathPoints = pathPoints;
        DiscTrailRenderer.totalDistanceFt = totalDistanceFt;
        DiscTrailRenderer.lateralDriftFt = lateralDriftFt;
        DiscTrailRenderer.stance = stance;
        DiscTrailRenderer.angle = angle;
        DiscTrailRenderer.flightTicks = flightTicks;
        DiscTrailRenderer.penaltyType = penaltyType;
        DiscTrailRenderer.penaltyStrokes = penaltyStrokes;
        DiscTrailRenderer.penaltyReason = penaltyReason;
        DiscTrailRenderer.obCrossingFeet = obCrossingFeet;
        DiscTrailRenderer.returnedToFeet = returnedToFeet;

        trailStartTick = (int) MinecraftClient.getInstance().world.getTime();
        trailActive = true;
        statsActive = true;
    }

    /**
     * Tick handler - called every client tick from McdgClientMod.
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        var world = client.world;
        int currentTick = (int) world.getTime();

        // Render trail particles
        if (trailActive) {
            int trailElapsed = currentTick - trailStartTick;
            if (trailElapsed >= TRAIL_DURATION_TICKS) {
                trailActive = false;
            } else {
                renderTrail(client, trailElapsed);
            }
        }
    }

    /**
     * Render particles along the trajectory path.
     */
    private static void renderTrail(MinecraftClient client, int elapsedTicks) {
        if (pathPoints == null || pathPoints.length < 2) {
            return;
        }

        ParticleManager particleManager = client.particleManager;

        // Calculate trail color based on stance
        int color = getTrailColor();

        // Determine how many points to show based on elapsed time
        // Show full trail immediately, then fade out
        float alpha = 1.0f - (elapsedTicks / (float) TRAIL_DURATION_TICKS);
        if (alpha <= 0) {
            return;
        }

        // Render particles at each path point
        for (int i = 0; i < pathPoints.length; i++) {
            Vec3d point = pathPoints[i];

            // Skip points that are too far from player (optimization)
            if (client.player.squaredDistanceTo(point.x, point.y, point.z) > 256 * 256) {
                continue;
            }

            // Render particle with fading alpha
            if (alpha > 0.5f || i % 2 == 0) { // Skip some particles when fading
                Particle particle = particleManager.addParticle(
                        ParticleTypes.END_ROD,
                        point.x,
                        point.y,
                        point.z,
                        0.0, 0.0, 0.0
                );
                if (particle != null) {
                    particle.setColor(
                        ((color >> 16) & 0xFF) / 255.0f,
                        ((color >> 8) & 0xFF) / 255.0f,
                        (color & 0xFF) / 255.0f
                    );
                }
            }
        }
    }

    /**
     * Get trail color based on throw stance.
     */
    private static int getTrailColor() {
        return switch (stance) {
            case OVERHAND -> 0xAAAAAA; // Gray (no glide)
            case BACKHAND -> 0x00FFFF; // Aqua
            case FOREHAND -> 0x00FF00; // Green
        };
    }

    /**
     * Check if stats are currently being displayed.
     */
    public static boolean isStatsActive() {
        return statsActive;
    }

    /**
     * Get the current stats for display.
     */
    public static ThrowStats getStats() {
        if (!statsActive) {
            return null;
        }
        return new ThrowStats(totalDistanceFt, lateralDriftFt, stance, angle, flightTicks,
                penaltyType != null ? penaltyType : StrictPenaltyType.NONE,
                penaltyStrokes,
                penaltyReason != null ? penaltyReason : "In Bounds",
                obCrossingFeet,
                returnedToFeet);
    }

    /**
     * Clear stats when round ends.
     */
    public static void clearStats() {
        statsActive = false;
        pathPoints = null;
    }

    /**
     * Record for throw statistics display.
     */
    public record ThrowStats(
            double totalDistanceFt,
            double lateralDriftFt,
            ThrowStance stance,
            ReleaseAngle angle,
            int flightTicks,
            StrictPenaltyType penaltyType,
            int penaltyStrokes,
            String penaltyReason,
            int obCrossingFeet,
            int returnedToFeet
    ) {}
}
