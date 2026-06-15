package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
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
 *
 * Phase 2: Throw Stance Selection
 * - Lateral fade curve based on stance (Backhand = left fade, Forehand = right fade)
 * - Overhand = vanilla physics (no glide, no fade)
 *
 * Target: 400-600 ft at full power with flat/horizontal aim
 */
public final class DiscFlightSimulator {

    // FlightState record tracks per-throw parameters
    public record FlightState(
            UUID pearlUuid,
            UUID playerUuid,        // Track which player threw, for timeout resolution
            int launchTick,
            float launchYawDegrees,
            float charge,           // 0.0 - 1.25 (can overcharge)
            ThrowStance stance,     // OVERHAND, BACKHAND, FOREHAND
            ReleaseAngle angle,     // HYZER, FLAT, ANHYZER
            Vec3d launchPos         // Starting position for distance tracking
    ) {
        /**
         * Calculate glide duration in ticks based on charge.
         * 10 ticks (min) to 80 ticks (full power)
         */
        public int glideTicks() {
            // Base 10 ticks + up to 40 additional ticks based on charge
            // Total 50 ticks max (2.5 seconds) - enough for 600-700 ft
            float normalizedCharge = Math.min(1.0f, charge);
            return 10 + Math.round(normalizedCharge * 40);
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

        /**
         * Returns fade progress (0.0 to 1.0) for the fade phase (last 40% of glide).
         * Returns 0.0 before fade phase begins.
         */
        public float fadeProgress(int currentServerTick) {
            float glideProgress = glideProgress(currentServerTick);
            // Fade phase starts at 60% through glide
            if (glideProgress < 0.6f) {
                return 0.0f;
            }
            // Fade increases from 0 to 1 over last 40% of glide
            return (glideProgress - 0.6f) / 0.4f;
        }
    }

    // Active flights keyed by pearl UUID
    private static final Map<UUID, FlightState> ACTIVE_FLIGHTS = new ConcurrentHashMap<>();

    // Physics constants - Option 4: Natural arc with early taper
    private static final double UPWARD_IMPULSE = 0.018;     // Less lift for natural arc (was 0.025)
    private static final double GLIDE_TAPER_START = 0.6;  // Start taper at 60% for earlier descent (was 80%)
    private static final double BASE_CURVE_STRENGTH = 0.008; // Base lateral deflection per tick during fade
    private static final int MAX_FLIGHT_TICKS = 120; // Maximum flight time (6 seconds) - safety net for natural landing

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
     * Phase 2: Accepts stance and angle parameters from client.
     */
    public static void registerThrow(
            UUID pearlUuid,
            UUID playerUuid,
            int launchTick,
            float launchYawDegrees,
            float charge,
            ThrowStance stance,
            ReleaseAngle angle,
            Vec3d launchPos
    ) {
        FlightState state = new FlightState(
                pearlUuid,
                playerUuid,
                launchTick,
                launchYawDegrees,
                charge,
                stance,
                angle,
                launchPos
        );

        ACTIVE_FLIGHTS.put(pearlUuid, state);

        McdgMod.LOGGER.info(
                "DiscFlightSimulator registered throw | pearl={} charge={} glideTicks={} stance={} angle={}",
                pearlUuid,
                String.format("%.3f", charge),
                state.glideTicks(),
                state.stance(),
                state.angle()
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

            int flightTicks = state.ticksSinceLaunch(currentTick);
            boolean pearlGone = pearl == null || pearl.isRemoved();
            boolean pearlLanded = pearl != null && pearl.isOnGround();
            boolean timedOut = flightTicks > MAX_FLIGHT_TICKS;

            if (pearlGone || pearlLanded || timedOut) {
                // Always force-clear the tracked pearl so ThrowResolver can resolve the throw.
                // Critical when pearl enters unloaded chunks (pearlGone) - without this
                // ThrowResolver keeps waiting indefinitely.
                ThrowResolver.forceClearTrackedPearl(state.playerUuid());

                String reason = pearlGone ? "pearl_unloaded" : pearlLanded ? "landed" : "timeout";
                if (pearl != null && state.stance().hasGlide()) {
                    double distance = calculateDistance(state.launchPos(), pearl.getPos());
                    McdgMod.LOGGER.info(
                            "DiscFlightSimulator flight complete | pearl={} reason={} distance={}ft stance={} angle={} charge={} ticks={}",
                            state.pearlUuid(),
                            reason,
                            String.format("%.1f", distance),
                            state.stance(),
                            state.angle(),
                            String.format("%.3f", state.charge()),
                            flightTicks
                    );
                } else {
                    McdgMod.LOGGER.info(
                            "DiscFlightSimulator flight ended | pearl={} reason={} ticks={}",
                            state.pearlUuid(), reason, flightTicks
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
     * - Fade phase: lateral curve based on stance + angle (last 40% of glide)
     *
     * Phase 2: Added lateral fade curve
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
        double newVelX = velocity.x;
        double newVelY = velocity.y + upwardImpulse;
        double newVelZ = velocity.z;

        // Apply lateral fade curve (Phase 2)
        float fadeProgress = state.fadeProgress(currentTick);
        if (fadeProgress > 0.0f) {
            // Calculate deflection based on stance + angle
            // naturalFade: -1 = left (backhand), +1 = right (forehand), 0 = none (overhand)
            // angleBias: -1 = hyzer (exaggerate fade), +1 = anhyzer (counteract fade), 0 = flat
            int naturalFade = state.stance().naturalFadeDirection();
            int angleBias = state.angle().angleBias();

            // Combined deflection: natural fade + angle bias
            // Hyzer exaggerates natural fade, anhyzer counteracts it
            int totalBias = naturalFade + angleBias;

            // Apply curve that increases through fade phase
            double curveStrength = BASE_CURVE_STRENGTH * totalBias * fadeProgress;

            // Calculate perpendicular direction (left/right of launch yaw)
            float yawRad = (float) Math.toRadians(state.launchYawDegrees);
            double leftX = Math.cos(yawRad + Math.PI / 2); // Perpendicular to facing direction
            double leftZ = Math.sin(yawRad + Math.PI / 2);

            // Apply lateral nudge (negative = left, positive = right)
            newVelX += leftX * curveStrength;
            newVelZ += leftZ * curveStrength;

            // Log fade for debugging (once per throw when fade starts)
            if (fadeProgress < 0.05f && (currentTick - state.launchTick) % 10 == 0) {
                McdgMod.LOGGER.debug(
                        "DiscFlightSimulator fade start | pearl={} stance={} angle={} bias={} strength={}",
                        state.pearlUuid(),
                        state.stance(),
                        state.angle(),
                        totalBias,
                        String.format("%.5f", curveStrength)
                );
            }
        }

        pearl.setVelocity(newVelX, newVelY, newVelZ);
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
