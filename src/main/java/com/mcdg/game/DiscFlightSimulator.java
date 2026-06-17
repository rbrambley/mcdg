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
 * Simulates disc golf flight physics for ender pearl throws (auto-test only).
 *
 * NOTE: Player throws use TrajectoryCalculator (calculated trajectory, no pearl).
 * - Glide phase: upward impulse to counteract gravity for flat flight
 * - Glide taper: gradual reduction of upward impulse at end of glide
 *
 * Features:
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
            Vec3d launchPos,        // Starting position for distance tracking
            double initialSpeed     // Initial velocity magnitude for curve calculation
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
         * Returns time-based curve factor (0.0 to 1.0).
         * Fade intensifies in latter part of glide phase (last 40%).
         * No glide stances return 0.0.
         */
        public float curveFactor(int currentServerTick) {
            if (!stance.hasGlide()) {
                return 0.0f;
            }
            int elapsed = ticksSinceLaunch(currentServerTick);
            int total = glideTicks();
            // Fade window is 40% of glide duration (proportional for all throw lengths)
            int fadeWindowTicks = (int) Math.round(total * 0.4);
            int fadeStartTick = total - fadeWindowTicks;
            if (elapsed < fadeStartTick) {
                return 0.0f;
            } else if (elapsed >= total) {
                return 1.0f;
            } else {
                return (float) (elapsed - fadeStartTick) / fadeWindowTicks;
            }
        }
    }

    // Active flights keyed by pearl UUID
    private static final Map<UUID, FlightState> ACTIVE_FLIGHTS = new ConcurrentHashMap<>();

    // Physics constants - Aligned with TrajectoryCalculator for consistent flight physics
    private static final double UPWARD_IMPULSE = 0.06;      // Aligned with TrajectoryCalculator - must be less than GRAVITY (0.08)
    private static final double GLIDE_TAPER_START = 0.6;  // Start taper at 60% for earlier descent
    private static final double BASE_CURVE_STRENGTH = 0.06; // Base lateral deflection per tick during fade
    private static final int MAX_FLIGHT_TICKS = 300; // Safety timeout (15 seconds) - physics should handle landing naturally

    // Distance validation target
    private static final float TARGET_MIN_DISTANCE_FT = 400;
    private static final float TARGET_MAX_DISTANCE_FT = 600;

    private DiscFlightSimulator() {
        // Utility class
    }

    /**
     * Register a new throw with the flight simulator.
     * Called from ThrowAutoTestService for auto-test throws (pearl-based).
     *
     * Note: Player throws via ChargedDiscItem use calculated trajectory instead (no pearl).
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
                launchPos,
                0.0  // initialSpeed - captured on first physics tick
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
                    // Calculate lateral drift (how far left/right from aim line)
                    double lateralDrift = calculateLateralDrift(state.launchPos(), state.launchYawDegrees(), pearl.getPos());
                    String driftDirection = lateralDrift > 0 ? "RIGHT" : "LEFT";
                    McdgMod.LOGGER.info(
                            "DiscFlightSimulator flight complete | pearl={} reason={} distance={}ft drift={}ft {} stance={} angle={} charge={} ticks={}",
                            state.pearlUuid(),
                            reason,
                            String.format("%.1f", distance),
                            String.format("%.1f", Math.abs(lateralDrift)),
                            driftDirection,
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

        // Apply time-based lateral curve (fade intensifies in latter part of glide)
        double currentSpeed = velocity.horizontalLength();
        float curveFactor = state.curveFactor(currentTick);
        if (curveFactor > 0.0f) {
            // Calculate deflection based on stance + angle
            // naturalFade: -1 = left (backhand), +1 = right (forehand), 0 = none (overhand)
            // angleBias: -1 = hyzer (exaggerate fade), +1 = anhyzer (counteract fade), 0 = flat
            int naturalFade = state.stance().naturalFadeDirection();
            int angleBias = state.angle().angleBias();

            // Combined deflection: HYZER exaggerates natural fade (2x), FLAT keeps natural (1x), ANHYZER neutralizes (0x)
            int totalBias = naturalFade * (1 - angleBias);

            // Smart curve scaling: short throws curve more, max power stays reasonable
            // At charge=1.0: multiplier=1.0 (normal curve)
            // At charge=0.0: multiplier=2.5 (stronger curve for short throws)
            double curveMultiplier = 1.0 + (1.0 - Math.min(1.0f, state.charge())) * 1.5;

            // Time-based curve: applies during fade phase (60-100% of glide)
            double curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor;

            // Calculate perpendicular direction for curve (left of facing direction)
            float yawRad = (float) Math.toRadians(state.launchYawDegrees());
            double leftX = -Math.cos(yawRad);
            double leftZ = -Math.sin(yawRad);

            // Apply lateral nudge (positive curveStrength = left, negative = right)
            newVelX += leftX * curveStrength;
            newVelZ += leftZ * curveStrength;

            // Log curve for debugging (occasional, time-driven)
            if (currentTick % 20 == 0) {
                McdgMod.LOGGER.debug(
                        "DiscFlightSimulator curve | pearl={} speed={:.2f} factor={:.2f} bias={} strength={:.4f}",
                        state.pearlUuid(),
                        currentSpeed,
                        curveFactor,
                        totalBias,
                        curveStrength
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
     * Calculate lateral drift (left/right displacement from aim line) in feet.
     * Positive = right of aim line, Negative = left of aim line
     */
    private static double calculateLateralDrift(Vec3d launchPos, float launchYawDegrees, Vec3d landingPos) {
        // Calculate aim direction vector from yaw
        float yawRad = (float) Math.toRadians(launchYawDegrees);
        double aimX = -Math.sin(yawRad); // Minecraft yaw: 0 = south (positive Z), so x = -sin(yaw)
        double aimZ = Math.cos(yawRad);  // z = cos(yaw)

        // Calculate throw displacement vector
        double dx = landingPos.x - launchPos.x;
        double dz = landingPos.z - launchPos.z;

        // Project displacement onto aim direction to get forward distance
        double forwardDist = dx * aimX + dz * aimZ;

        // Calculate perpendicular (lateral) component
        // Perpendicular vector to aim is (aimZ, -aimX) for rightward
        double lateralRightX = aimZ;
        double lateralRightZ = -aimX;

        double lateralDist = dx * lateralRightX + dz * lateralRightZ;

        return lateralDist * 3.0; // Convert to feet
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
     * Mirrors TrajectoryCalculator.calculateTrajectory() physics exactly so the
     * HUD preview matches what the disc actually does in-game.
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
            // Per-tick upward impulse (glide phase only) - mirrors TrajectoryCalculator lines 86-94
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

            // Apply gravity (0.08) and lift - mirrors TrajectoryCalculator line 97
            vy = vy + upwardImpulse - 0.08;

            x += vx;
            y += vy;

            // Termination mirrors TrajectoryCalculator lines 130-143:
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
