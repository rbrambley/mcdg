package com.mcdg.client;

import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side renderer for disc throw particle trails and after-throw stats.
 * Receives trajectory data from server and renders particles along the path.
 * Supports multiple concurrent player trails for multiplayer visibility.
 *
 * NEW: Progressive trail rendering - particles appear over time matching actual flight duration.
 */
public final class DiscTrailRenderer {
    private static final int TRAIL_DURATION_TICKS = 40; // 2 seconds (legacy)
    private static final int TRAIL_EXTRA_DISPLAY_TICKS = 60; // 3 seconds extra for stats visibility

    private static class TrailData {
        Vec3d[] pathPoints;
        int flightTicks;              // Total flight duration
        int startTick;                // When trail started
        int currentPathIndex;         // Current position in path (for progressive rendering)
        boolean isProgressive;        // Using new progressive rendering
        boolean isComplete;           // Flight complete flag
        
        ThrowStance stance;
        ReleaseAngle angle;
        
        // Stats (filled when complete packet arrives or legacy startTrail)
        Double totalDistanceFt;
        Double lateralDriftFt;
        StrictPenaltyType penaltyType;
        Integer penaltyStrokes;
        String penaltyReason;
        Integer obCrossingFeet;
        Integer returnedToFeet;
        boolean statsActive;
    }

    private static final Map<UUID, TrailData> TRAILS = new HashMap<>();

    private DiscTrailRenderer() {
    }

    private static UUID localPlayerId() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getUuid() : null;
    }

    /**
     * Start a new trail for the given player with the provided trajectory data (legacy method).
     * Kept for backward compatibility with old packet system.
     */
    public static void startTrail(
            UUID throwerId,
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
        TrailData trail = new TrailData();
        trail.pathPoints = pathPoints;
        trail.totalDistanceFt = totalDistanceFt;
        trail.lateralDriftFt = lateralDriftFt;
        trail.stance = stance;
        trail.angle = angle;
        trail.flightTicks = flightTicks;
        trail.penaltyType = penaltyType;
        trail.penaltyStrokes = penaltyStrokes;
        trail.penaltyReason = penaltyReason;
        trail.obCrossingFeet = obCrossingFeet;
        trail.returnedToFeet = returnedToFeet;

        trail.startTick = (int) MinecraftClient.getInstance().world.getTime();
        trail.isProgressive = false;
        trail.isComplete = true;
        trail.currentPathIndex = pathPoints.length; // All particles shown immediately
        trail.statsActive = true;

        TRAILS.put(throwerId, trail);
        
        // Render immediately for legacy behavior
        renderTrailInstant(MinecraftClient.getInstance(), trail);
    }

    /**
     * Start progressive trail rendering (new method).
     * Particles appear over time matching actual flight duration.
     */
    public static void startProgressiveTrail(
            UUID throwerId,
            Vec3d[] pathPoints,
            int flightTicks,
            ThrowStance stance,
            ReleaseAngle angle
    ) {
        System.out.println("START PROGRESSIVE TRAIL: thrower=" + throwerId + 
            " pathPoints=" + (pathPoints != null ? pathPoints.length : "null") + 
            " flightTicks=" + flightTicks +
            " stance=" + stance +
            " angle=" + angle);
        
        TrailData trail = new TrailData();
        trail.pathPoints = pathPoints;
        trail.flightTicks = flightTicks;
        trail.stance = stance;
        trail.angle = angle;
        
        trail.startTick = (int) MinecraftClient.getInstance().world.getTime();
        trail.isProgressive = true;
        trail.isComplete = false;
        trail.currentPathIndex = 0;
        trail.statsActive = false; // Stats not available until complete packet

        TRAILS.put(throwerId, trail);
        System.out.println("TRAIL STORED: total trails=" + TRAILS.size());
    }

    /**
     * Complete trail with final stats (new method).
     * Called after landing resolution to update trail with final statistics.
     */
    public static void completeTrail(
            UUID throwerId,
            double totalDistanceFt,
            double lateralDriftFt,
            StrictPenaltyType penaltyType,
            int penaltyStrokes,
            String penaltyReason,
            int obCrossingFeet,
            int returnedToFeet
    ) {
        TrailData trail = TRAILS.get(throwerId);
        if (trail != null) {
            trail.totalDistanceFt = totalDistanceFt;
            trail.lateralDriftFt = lateralDriftFt;
            trail.penaltyType = penaltyType;
            trail.penaltyStrokes = penaltyStrokes;
            trail.penaltyReason = penaltyReason;
            trail.obCrossingFeet = obCrossingFeet;
            trail.returnedToFeet = returnedToFeet;
            trail.isComplete = true;
            trail.statsActive = true;
        }
    }

    /**
     * Tick handler - called every client tick from McdgClientMod.
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }

        int currentTick = (int) client.world.getTime();

        Iterator<Map.Entry<UUID, TrailData>> iterator = TRAILS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrailData> entry = iterator.next();
            TrailData trail = entry.getValue();

            // Handle progressive trail rendering
            if (trail.isProgressive) {
                int elapsed = currentTick - trail.startTick;
                float progress = Math.min(1.0f, elapsed / (float) trail.flightTicks);
                
                // Debug logging every 10 ticks
                if (elapsed % 10 == 0) {
                    System.out.println("TRAIL TICK: thrower=" + entry.getKey() + 
                        " elapsed=" + elapsed + 
                        " flightTicks=" + trail.flightTicks +
                        " progress=" + progress +
                        " currentIndex=" + trail.currentPathIndex +
                        " totalPoints=" + (trail.pathPoints != null ? trail.pathPoints.length : "null"));
                }
                
                // Calculate how many path points to show
                int pointsToShow = (int) Math.floor(progress * trail.pathPoints.length);
                
                // Ensure at least first particle renders immediately for instant feedback
                if (elapsed == 0 && trail.currentPathIndex == 0 && trail.pathPoints.length > 0) {
                    pointsToShow = Math.max(pointsToShow, 1);
                }
                
                // Render new particles as we progress
                while (trail.currentPathIndex < pointsToShow) {
                    renderNextParticle(client, trail, trail.currentPathIndex);
                    trail.currentPathIndex++;
                }
                
                // Remove completed trails after extra time for stats display
                if (progress > 1.5f) { // 50% extra time for stats visibility
                    iterator.remove();
                }
            } else {
                // Legacy trail handling (instant render)
                int elapsed = currentTick - trail.startTick;
                if (elapsed >= TRAIL_DURATION_TICKS + TRAIL_EXTRA_DISPLAY_TICKS) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Render particles along the trajectory path instantly (legacy method).
     */
    private static void renderTrailInstant(MinecraftClient client, TrailData trail) {
        if (trail.pathPoints == null || trail.pathPoints.length < 2) {
            return;
        }

        ParticleManager particleManager = client.particleManager;
        int color = getTrailColor(trail.stance);

        for (int i = 0; i < trail.pathPoints.length; i++) {
            Vec3d point = trail.pathPoints[i];

            // Skip points that are too far from player (optimization)
            if (client.player.squaredDistanceTo(point.x, point.y, point.z) > 256 * 256) {
                continue;
            }

            // Skip every other point to reduce particle count on long paths
            if (i % 2 != 0) {
                continue;
            }

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

    /**
     * Render next particle in progressive trail (new method).
     * Enhanced with FLAME particles, dual-particle thickness, and extended lifetime.
     */
    private static void renderNextParticle(MinecraftClient client, TrailData trail, int index) {
        if (trail.pathPoints == null || index >= trail.pathPoints.length) {
            System.out.println("RENDER PARTICLE SKIPPED: null points or index out of range");
            return;
        }

        Vec3d point = trail.pathPoints[index];
        UUID localId = localPlayerId();
        UUID throwerId = getCurrentThrowerId(trail);
        
        // Skip points that are too far from player, but always show own throws
        if (throwerId == null || !throwerId.equals(localId)) {
            if (client.player.squaredDistanceTo(point.x, point.y, point.z) > 512 * 512) {
                System.out.println("RENDER PARTICLE SKIPPED: too far from player");
                return;
            }
        }

        System.out.println("RENDER PARTICLE: index=" + index + " point=" + point);

        ParticleManager particleManager = client.particleManager;
        int color = getTrailColor(trail.stance);

        // Render 2 particles per point for thickness with vertical offset
        for (int i = 0; i < 2; i++) {
            double yOffset = (i == 0) ? 0.0 : 0.15; // Slight vertical offset for thickness
            
            Particle particle = particleManager.addParticle(
                    ParticleTypes.FLAME,  // Changed from END_ROD to FLAME for better visibility
                    point.x,
                    point.y + yOffset,
                    point.z,
                    0.0, 0.0, 0.0
            );
            
            if (particle != null) {
                particle.setColor(
                        ((color >> 16) & 0xFF) / 255.0f,
                        ((color >> 8) & 0xFF) / 255.0f,
                        (color & 0xFF) / 255.0f
                );
                particle.setMaxAge(200); // Increased from 100 to 200 (10 seconds)
            }
        }
    }
    
    /**
     * Get the current thrower ID from the trail entry.
     * Helper method for distance culling logic.
     */
    private static UUID getCurrentThrowerId(TrailData trail) {
        // Find the thrower ID by iterating through TRAILS map
        for (Map.Entry<UUID, TrailData> entry : TRAILS.entrySet()) {
            if (entry.getValue() == trail) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get trail color based on throw stance (enhanced for visibility).
     */
    private static int getTrailColor(ThrowStance stance) {
        return switch (stance) {
            case OVERHAND -> 0xFFFFFF;  // Pure white (high contrast)
            case BACKHAND -> 0x00FFFF;  // Bright cyan (very visible)
            case FOREHAND -> 0x00FF00;  // Bright green (very visible)
        };
    }

    // --- Stats API (defaults to local player for backward compatibility) ---

    public static boolean isStatsActive() {
        UUID localId = localPlayerId();
        if (localId == null) {
            return false;
        }
        return isStatsActive(localId);
    }

    public static boolean isStatsActive(UUID playerId) {
        TrailData trail = TRAILS.get(playerId);
        return trail != null && trail.statsActive;
    }

    public static ThrowStats getStats() {
        UUID localId = localPlayerId();
        if (localId == null) {
            return null;
        }
        return getStats(localId);
    }

    public static ThrowStats getStats(UUID playerId) {
        TrailData trail = TRAILS.get(playerId);
        if (trail == null || !trail.statsActive) {
            return null;
        }
        return new ThrowStats(
                trail.totalDistanceFt,
                trail.lateralDriftFt,
                trail.stance,
                trail.angle,
                trail.flightTicks,
                trail.penaltyType != null ? trail.penaltyType : StrictPenaltyType.NONE,
                trail.penaltyStrokes,
                trail.penaltyReason != null ? trail.penaltyReason : "In Bounds",
                trail.obCrossingFeet,
                trail.returnedToFeet
        );
    }

    public static void setStats(
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
        UUID localId = localPlayerId();
        if (localId == null) {
            return;
        }
        setStats(localId, totalDistanceFt, lateralDriftFt, stance, angle, flightTicks,
                penaltyType, penaltyStrokes, penaltyReason, obCrossingFeet, returnedToFeet);
    }

    public static void setStats(
            UUID playerId,
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
        TrailData trail = TRAILS.computeIfAbsent(playerId, k -> new TrailData());
        trail.totalDistanceFt = totalDistanceFt;
        trail.lateralDriftFt = lateralDriftFt;
        trail.stance = stance;
        trail.angle = angle;
        trail.flightTicks = flightTicks;
        trail.penaltyType = penaltyType;
        trail.penaltyStrokes = penaltyStrokes;
        trail.penaltyReason = penaltyReason;
        trail.obCrossingFeet = obCrossingFeet;
        trail.returnedToFeet = returnedToFeet;
        trail.statsActive = true;
    }

    public static void clearStats() {
        UUID localId = localPlayerId();
        if (localId == null) {
            return;
        }
        clearStats(localId);
    }

    public static void clearStats(UUID playerId) {
        TrailData trail = TRAILS.get(playerId);
        if (trail != null) {
            trail.statsActive = false;
        }
    }

    public static void clearAllStats() {
        TRAILS.clear();
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
