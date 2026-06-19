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
 */
public final class DiscTrailRenderer {
    private static final int TRAIL_DURATION_TICKS = 40; // 2 seconds

    private static class TrailData {
        Vec3d[] pathPoints;
        double totalDistanceFt;
        double lateralDriftFt;
        ThrowStance stance;
        ReleaseAngle angle;
        int flightTicks;
        StrictPenaltyType penaltyType;
        int penaltyStrokes;
        String penaltyReason;
        int obCrossingFeet;
        int returnedToFeet;
        int trailStartTick;
        boolean trailActive;
        boolean particlesSpawned;
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
     * Start a new trail for the given player with the provided trajectory data.
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

        trail.trailStartTick = (int) MinecraftClient.getInstance().world.getTime();
        trail.trailActive = true;
        trail.particlesSpawned = false;
        trail.statsActive = true;

        TRAILS.put(throwerId, trail);
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

            if (!trail.trailActive) {
                continue;
            }

            int elapsed = currentTick - trail.trailStartTick;
            if (elapsed >= TRAIL_DURATION_TICKS) {
                trail.trailActive = false;
            } else if (!trail.particlesSpawned) {
                renderTrail(client, trail);
                trail.particlesSpawned = true;
            }
        }
    }

    /**
     * Render particles along the trajectory path once per trail.
     */
    private static void renderTrail(MinecraftClient client, TrailData trail) {
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
     * Get trail color based on throw stance.
     */
    private static int getTrailColor(ThrowStance stance) {
        return switch (stance) {
            case OVERHAND -> 0xAAAAAA; // Gray (no glide)
            case BACKHAND -> 0x00FFFF; // Aqua
            case FOREHAND -> 0x00FF00; // Green
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
            trail.trailActive = false;
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
