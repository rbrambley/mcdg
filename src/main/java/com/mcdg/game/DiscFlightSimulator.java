package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates disc golf flight physics for ender pearl throws.
 *
 * Phase 1: Core Glide Physics
 * - Glide phase: upward impulse to counteract gravity for flat flight
 * - Glide taper: gradual reduction of upward impulse at end of glide
 * - No lateral curve yet (added in Phase 2)
 *
 * Target: 400-600 ft at full power with flat/horizontal aim
 */
public final class DiscFlightSimulator {

    // FlightState record tracks per-throw parameters
    public record FlightState(
            UUID pearlUuid,
            int launchTick,
            float launchYawDegrees,
            float charge,           // 0.0 - 1.25 (can overcharge)
            ThrowStance stance,     // OVERHAND (default for Phase 1)
            ReleaseAngle angle,     // FLAT (default for Phase 1)
            Vec3d launchPos         // Starting position for distance tracking
    ) {
        /**
         * Calculate glide duration in ticks based on charge.
         * 10 ticks (min) to 80 ticks (full power)
         */
        public int glideTicks() {
            // Base 10 ticks + up to 70 additional ticks based on charge
            float normalizedCharge = Math.min(1.0f, charge); // Cap at 100% for glide duration
            return 10 + Math.round(normalizedCharge * 70);
        }

        /**
         * Returns the current tick relative to launch.
         */
        public int ticksSinceLaunch(int currentServerTick) {
            return currentServerTick - launchTick;
        }

        /**
         * Returns progress through glide phase (0.0 to 1.0)
         */
        public float glideProgress(int currentServerTick) {
            int elapsed = ticksSinceLaunch(currentServerTick);
            int total = glideTicks();
            return Math.min(1.0f, elapsed / (float) total);
        }

        /**
         * Returns true if we're in the glide taper phase (last 20% of glide)
         */
        public boolean isInGlideTaper(int currentServerTick) {
            return glideProgress(currentServerTick) > 0.8f;
        }

        /**
         * Returns true if glide phase is complete
         */
        public boolean isGlideComplete(int currentServerTick) {
            return glideProgress(currentServerTick) >= 1.0f;
        }
    }

    // Active flights keyed by pearl UUID
    private static final Map<UUID, FlightState> ACTIVE_FLIGHTS = new ConcurrentHashMap<>();

    // Physics constants
    private static final double UPWARD_IMPULSE = 0.03;      // Counteracts vanilla gravity (~-0.03/tick)
    private static final double GLIDE_TAPER_START = 0.8;    // Start tapering at 80% of glide

    // Distance validation target
    private static final float TARGET_MIN_DISTANCE_FT = 400;
    private static final float TARGET_MAX_DISTANCE_FT = 600;

    private DiscFlightSimulator() {
        // Utility class
    }

    /**
     * Register a new throw with the flight simulator.
     * Called from ChargedDiscItem.onStoppedUsing() after pearl spawn.
     *
     * Phase 1: Uses default OVERHAND stance and FLAT angle.
     */
    public static void registerThrow(
            UUID pearlUuid,
            int launchTick,
            float launchYawDegrees,
            float charge,
            Vec3d launchPos
    ) {
        // Phase 1: Default to OVERHAND stance and FLAT angle
        FlightState state = new FlightState(
                pearlUuid,
                launchTick,
                launchYawDegrees,
                charge,
                ThrowStance.OVERHAND,
                ReleaseAngle.FLAT,
                launchPos
        );

        ACTIVE_FLIGHTS.put(pearlUuid, state);

        McdgMod.LOGGER.info(
                "DiscFlightSimulator registered throw | pearl={} charge={} glideTicks={} stance={}",
                pearlUuid,
                String.format("%.3f", charge),
                state.glideTicks(),
                state.stance()
        );
    }

    /**
     * Main tick handler - apply physics to all active flights.
     * Registered on ServerTickEvents.END_SERVER_TICK in McdgMod.
     */
    public static void tick(MinecraftServer server) {
        int currentTick = server.getTicks();

        ACTIVE_FLIGHTS.entrySet().removeIf(entry -> {
            FlightState state = entry.getValue();
            EnderPearlEntity pearl = findPearl(server, entry.getKey());

            if (pearl == null || pearl.isRemoved() || pearl.isOnGround()) {
                // Pearl landed or despawned - log final distance
                if (pearl != null && state.stance().hasGlide()) {
                    double distance = calculateDistance(state.launchPos(), pearl.getPos());
                    McdgMod.LOGGER.info(
                            "DiscFlightSimulator flight complete | pearl={} distance={}ft stance={} charge={}",
                            state.pearlUuid(),
                            String.format("%.1f", distance),
                            state.stance(),
                            String.format("%.3f", state.charge())
                    );
                }
                return true; // Remove from tracking
            }

            // Apply physics based on stance
            if (state.stance().hasGlide()) {
                applyGlidePhysics(pearl, state, currentTick);
            }
            // OVERHAND: vanilla physics, no modifications

            return false; // Keep tracking
        });
    }

    /**
     * Apply glide physics to a pearl.
     * - Glide phase: upward impulse to counteract gravity
     * - Glide taper: gradually reduce upward impulse in final 20%
     * Phase 1: No lateral curve (added in Phase 2)
     */
    private static void applyGlidePhysics(EnderPearlEntity pearl, FlightState state, int currentTick) {
        float progress = state.glideProgress(currentTick);

        if (progress >= 1.0f) {
            // Glide complete, let vanilla physics take over
            return;
        }

        // Calculate upward impulse
        double upwardImpulse;

        if (progress > GLIDE_TAPER_START) {
            // Glide taper phase: linearly reduce from full to zero
            double taperProgress = (progress - GLIDE_TAPER_START) / (1.0f - GLIDE_TAPER_START);
            upwardImpulse = UPWARD_IMPULSE * (1.0 - taperProgress);
        } else {
            // Full glide phase
            upwardImpulse = UPWARD_IMPULSE;
        }

        // Apply upward velocity adjustment
        // We add to existing velocity to maintain forward momentum
        Vec3d velocity = pearl.getVelocity();
        pearl.setVelocity(velocity.x, velocity.y + upwardImpulse, velocity.z);
    }

    /**
     * Find an active pearl entity by UUID.
     */
    private static EnderPearlEntity findPearl(MinecraftServer server, UUID pearlUuid) {
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(pearlUuid);
            if (entity instanceof EnderPearlEntity pearl) {
                return pearl;
            }
        }
        return null;
    }

    /**
     * Calculate horizontal distance in feet (Minecraft blocks ≈ 3 feet)
     */
    private static double calculateDistance(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double blocks = Math.sqrt(dx * dx + dz * dz);
        return blocks * 3.0; // Convert to feet
    }

    /**
     * Clear all active flights. Called on round end/reset.
     */
    public static void reset() {
        int count = ACTIVE_FLIGHTS.size();
        ACTIVE_FLIGHTS.clear();
        McdgMod.LOGGER.info("DiscFlightSimulator reset | cleared {} active flights", count);
    }

    /**
     * Check if a pearl is being tracked.
     */
    public static boolean isTracking(UUID pearlUuid) {
        return ACTIVE_FLIGHTS.containsKey(pearlUuid);
    }

    /**
     * Get flight state for a tracked pearl.
     */
    public static FlightState getFlightState(UUID pearlUuid) {
        return ACTIVE_FLIGHTS.get(pearlUuid);
    }

    /**
     * Estimate flight distance for HUD display.
     * Simple estimation based on charge and stance.
     */
    public static int estimateDistance(float charge, ThrowStance stance) {
        // Base distance calculation
        float normalizedCharge = Math.min(1.25f, charge);

        // Phase 1: Simple linear estimate
        // Full charge (100%) = ~500 ft
        double baseDistance = normalizedCharge * 400;

        // Add glide bonus for non-overhand throws (Phase 2+ will refine this)
        if (stance.hasGlide()) {
            baseDistance += normalizedCharge * 100; // Bonus glide distance
        }

        return (int) Math.round(baseDistance);
    }
}
