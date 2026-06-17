package com.mcdg.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Calculates disc golf throw trajectories without spawning entities.
 * Simulates flight physics mathematically to determine landing position,
 * flight duration, and path points for visual trails.
 *
 * Terrain-aware: respects block collisions (walls, trees) and lands on actual ground.
 * Phase 4: Supports visual trail rendering along calculated path.
 */
public final class TrajectoryCalculator {

    // Physics constants (match DiscFlightSimulator)
    private static final double UPWARD_IMPULSE = 0.06;       // Lift must be less than GRAVITY (0.08) for a natural arc
    private static final double GLIDE_TAPER_START = 0.6;   // Start reducing lift at 60% of glide
    private static final double BASE_CURVE_STRENGTH = 0.06; // Lateral deflection per tick
    private static final double GRAVITY = 0.08;            // Vanilla Minecraft gravity

    // Maximum simulation ticks (safety limit, not timeout)
    private static final int MAX_SIMULATION_TICKS = 400;

    // Ticks to skip collision checks near the thrower (avoids self-collision)
    private static final int THROW_COLLISION_GRACE_TICKS = 3;

    // Release height offset from player feet (shoulder height)
    private static final double RELEASE_HEIGHT_OFFSET = 1.5;

    private TrajectoryCalculator() {
        // Utility class
    }

    /**
     * Result of trajectory calculation containing landing position and flight data.
     */
    public record TrajectoryResult(
            Vec3d landingPosition,    // Where the disc will land
            int flightTicks,          // How many ticks until landing
            double totalDistanceFt,   // Total horizontal distance in feet
            double lateralDriftFt,    // Left/right drift from aim line in feet
            Vec3d[] pathPoints        // Points along trajectory for visual trail (Phase 4)
    ) {}

    /**
     * Calculate complete throw trajectory from launch to landing.
     *
     * @param world The server world for terrain/collision lookups
     * @param startPos Initial position (player's throwing position / feet)
     * @param initialVelocity Initial velocity vector
     * @param launchYawDegrees Player's facing direction when throwing
     * @param charge Power level (0.0 - 1.25)
     * @param stance Throw stance (affects curve direction)
     * @param angle Release angle (affects curve strength)
     * @return TrajectoryResult with landing position and flight data
     */
    public static TrajectoryResult calculateTrajectory(
            ServerWorld world,
            Vec3d startPos,
            Vec3d initialVelocity,
            float launchYawDegrees,
            float charge,
            ThrowStance stance,
            ReleaseAngle angle
    ) {
        // Current position and velocity (simulation state)
        // Offset upward to shoulder height so the disc doesn't immediately collide with the ground
        Vec3d pos = new Vec3d(startPos.x, startPos.y + RELEASE_HEIGHT_OFFSET, startPos.z);
        Vec3d prevPos = pos;
        Vec3d vel = initialVelocity;

        // Glide duration based on charge (only for stances with glide)
        float normalizedCharge = Math.min(1.0f, charge);
        boolean hasGlide = stance.hasGlide();
        int glideTicks = hasGlide ? 10 + Math.round(normalizedCharge * 40) : 0;

        // Stance/angle curve calculation
        // HYZER exaggerates natural fade (2x), FLAT keeps natural (1x), ANHYZER neutralizes (0x)
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias); // HYZER(-1) = 2x, FLAT(0) = 1x, ANHYZER(+1) = 0x
        double curveMultiplier = 1.0 + (1.0 - normalizedCharge) * 1.5; // Smart scaling

        // Path points for visual trail (sample every 5 ticks to save memory)
        java.util.List<Vec3d> pathList = new java.util.ArrayList<>();
        pathList.add(pos);

        // Simulate flight tick by tick
        int tick = 0;
        while (tick < MAX_SIMULATION_TICKS) {
            tick++;
            prevPos = pos;

            // Calculate glide progress (0.0 to 1.0) - only for glide stances
            float glideProgress = hasGlide ? Math.min(1.0f, tick / (float) glideTicks) : 1.0f;

            // Apply upward impulse (glide phase) - only for stances with glide
            double upwardImpulse = 0.0;
            if (hasGlide) {
                if (glideProgress > GLIDE_TAPER_START) {
                    double taperProgress = (glideProgress - GLIDE_TAPER_START) / (1.0f - GLIDE_TAPER_START);
                    upwardImpulse = UPWARD_IMPULSE * (1.0 - taperProgress);
                } else {
                    upwardImpulse = UPWARD_IMPULSE;
                }
            }

            // Apply gravity
            double velY = vel.y + upwardImpulse - GRAVITY;

            // Apply time-based fade curve (fade intensifies in latter part of glide)
            double currentSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            double curveFactor;
            if (!hasGlide) {
                curveFactor = 0.0;
            } else {
                // Fade window is 40% of glide duration (proportional for all throw lengths)
                int fadeWindowTicks = (int) Math.round(glideTicks * 0.4);
                int fadeStartTick = glideTicks - fadeWindowTicks;
                if (tick < fadeStartTick) {
                    curveFactor = 0.0;
                } else if (tick >= glideTicks) {
                    curveFactor = 1.0;
                } else {
                    curveFactor = (double) (tick - fadeStartTick) / fadeWindowTicks;
                }
            }

            double curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor;

            // Calculate perpendicular direction for curve (left of facing direction)
            float yawRad = (float) Math.toRadians(launchYawDegrees);
            double leftX = -Math.cos(yawRad);
            double leftZ = -Math.sin(yawRad);

            double velX = vel.x + leftX * curveStrength;
            double velZ = vel.z + leftZ * curveStrength;

            // Update velocity
            vel = new Vec3d(velX, velY, velZ);

            // Update position
            pos = pos.add(vel);

            // Record path point every 5 ticks
            if (tick % 5 == 0) {
                pathList.add(pos);
            }

            // Terrain-aware collision checks (skip grace period near thrower)
            if (tick > THROW_COLLISION_GRACE_TICKS) {
                BlockPos blockPos = new BlockPos(
                        MathHelper.floor(pos.x),
                        MathHelper.floor(pos.y),
                        MathHelper.floor(pos.z)
                );

                // Obstacle collision: disc inside a solid block (wall, tree, building)
                if (!world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) {
                    // Hit something solid — roll back to previous position and stop
                    pos = prevPos;
                    break;
                }

                // Ground landing: after glide phase, when descending, check if block below is solid
                if (glideProgress >= 1.0f && velY < 0) {
                    BlockPos groundPos = blockPos.down();
                    if (!world.getBlockState(groundPos).getCollisionShape(world, groundPos).isEmpty()) {
                        // Landed on actual terrain — snap to standable height
                        pos = new Vec3d(pos.x, groundPos.getY() + 1.0, pos.z);
                        break;
                    }
                }
            }

            // Check for velocity threshold (disc stopped moving horizontally)
            if (currentSpeed < 0.01) {
                break;
            }
        }

        // Calculate final statistics (distance measured from original feet position)
        double dx = pos.x - startPos.x;
        double dz = pos.z - startPos.z;
        double distanceBlocks = Math.sqrt(dx * dx + dz * dz);
        double distanceFeet = distanceBlocks * 3.0;

        // Calculate lateral drift
        double lateralDrift = calculateLateralDrift(startPos, launchYawDegrees, pos);

        // Convert path list to array
        Vec3d[] pathPoints = pathList.toArray(new Vec3d[0]);

        return new TrajectoryResult(pos, tick, distanceFeet, lateralDrift, pathPoints);
    }

    /**
     * Calculate lateral drift (left/right displacement from aim line).
     * Positive = right, Negative = left
     */
    private static double calculateLateralDrift(Vec3d launchPos, float launchYawDegrees, Vec3d landingPos) {
        float yawRad = (float) Math.toRadians(launchYawDegrees);
        double aimX = -Math.sin(yawRad);
        double aimZ = Math.cos(yawRad);

        double dx = landingPos.x - launchPos.x;
        double dz = landingPos.z - launchPos.z;

        double lateralRightX = aimZ;
        double lateralRightZ = -aimX;

        double lateralDist = dx * lateralRightX + dz * lateralRightZ;
        return lateralDist * 3.0; // Convert to feet
    }
}
