package com.mcdg.game.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Custom AI goal for mobs to patrol along the fairway between tee and basket.
 * Mobs move between waypoints and can potentially block disc flight paths.
 */
public class PatrolFairwayGoal extends Goal {
    private static final double PATROL_SPEED = 0.6;
    private static final double CHASE_SPEED = 1.0;
    private static final double DETECTION_RADIUS = 12.0;
    private static final int CHASE_COOLDOWN_TICKS = 60;
    private static final double WAYPOINT_REACHED_DISTANCE_SQUARED = 4.0;
    private static final int INTERMEDIATE_POINTS_MIN = 2;
    private static final int INTERMEDIATE_POINTS_EXTRA = 2;
    private static final int PERPENDICULAR_OFFSET_RANGE = 3;

    private final MobEntity mob;
    private final ServerWorld world;
    private final BlockPos teePosition;
    private final BlockPos basketPosition;
    private final List<BlockPos> waypoints;
    private final Random random;
    private int currentWaypointIndex;
    private final double patrolSpeed;
    private final double chaseSpeed;
    private PlayerEntity targetPlayer;
    private boolean isChasing;
    private int chaseCooldown;

    public PatrolFairwayGoal(MobEntity mob, ServerWorld world, Random random, BlockPos teePosition, BlockPos basketPosition) {
        this.mob = mob;
        this.world = world;
        this.random = random;
        this.teePosition = teePosition;
        this.basketPosition = basketPosition;
        this.waypoints = generateWaypoints(world, teePosition, basketPosition);
        this.currentWaypointIndex = 0;
        this.patrolSpeed = PATROL_SPEED;
        this.chaseSpeed = CHASE_SPEED;
        this.isChasing = false;
        this.chaseCooldown = 0;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        // Always active for patrolling behavior
        if (chaseCooldown > 0) {
            chaseCooldown--;
            return false;
        }

        // Check for nearby players to chase
        targetPlayer = findNearestPlayer(DETECTION_RADIUS);
        if (targetPlayer != null) {
            isChasing = true;
            return true;
        }

        // Continue patrolling
        isChasing = false;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (isChasing) {
            // Continue chasing if target is alive and within range
            if (targetPlayer != null && targetPlayer.isAlive() && targetPlayer.squaredDistanceTo(mob) < DETECTION_RADIUS * DETECTION_RADIUS) {
                return true;
            }
            // Lost target, return to patrolling
            isChasing = false;
            chaseCooldown = CHASE_COOLDOWN_TICKS;
            return true; // Continue to return to patrol
        }

        // Continue patrolling
        return true;
    }

    @Override
    public void start() {
        if (isChasing && targetPlayer != null) {
            mob.setTarget(targetPlayer);
        }
    }

    @Override
    public void stop() {
        targetPlayer = null;
        mob.setTarget(null);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (isChasing && targetPlayer != null && targetPlayer.isAlive()) {
            // Chase target player
            mob.getLookControl().lookAt(targetPlayer);
            mob.getNavigation().startMovingTo(targetPlayer, chaseSpeed);
        } else {
            // Patrol between waypoints
            if (chaseCooldown > 0) {
                chaseCooldown--;
            }
            patrol();
        }
    }

    /**
     * Patrols between waypoints along the fairway.
     */
    private void patrol() {
        if (waypoints.isEmpty()) {
            return;
        }

        BlockPos targetWaypoint = waypoints.get(currentWaypointIndex);
        double distance = mob.squaredDistanceTo(
                targetWaypoint.getX() + 0.5,
                targetWaypoint.getY(),
                targetWaypoint.getZ() + 0.5
        );

        if (distance < WAYPOINT_REACHED_DISTANCE_SQUARED) {
            // Move to next waypoint
            currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
        }

        // Move towards current waypoint
        BlockPos currentTarget = waypoints.get(currentWaypointIndex);
        mob.getLookControl().lookAt(
                currentTarget.getX() + 0.5,
                currentTarget.getY(),
                currentTarget.getZ() + 0.5
        );
        mob.getNavigation().startMovingTo(
                currentTarget.getX() + 0.5,
                currentTarget.getY(),
                currentTarget.getZ() + 0.5,
                patrolSpeed
        );
    }

    /**
     * Generates waypoints along the fairway between tee and basket.
     * Waypoints are sampled at the local surface height so mobs don't float or get buried.
     */
    private List<BlockPos> generateWaypoints(ServerWorld world, BlockPos tee, BlockPos basket) {
        List<BlockPos> points = new ArrayList<>();

        // Add tee as starting point
        points.add(tee);

        // Add intermediate points along the fairway
        int intermediatePoints = INTERMEDIATE_POINTS_MIN + random.nextInt(INTERMEDIATE_POINTS_EXTRA);
        for (int i = 1; i <= intermediatePoints; i++) {
            double t = (double) i / (intermediatePoints + 1);
            int x = (int) MathHelper.lerp(t, tee.getX(), basket.getX());
            int z = (int) MathHelper.lerp(t, tee.getZ(), basket.getZ());

            // Add some perpendicular offset for variety
            int perpendicularOffset = random.nextInt(PERPENDICULAR_OFFSET_RANGE * 2 + 1) - PERPENDICULAR_OFFSET_RANGE;
            double dx = basket.getX() - tee.getX();
            double dz = basket.getZ() - tee.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0) {
                x += (int) Math.round((-dz / length) * perpendicularOffset);
                z += (int) Math.round((dx / length) * perpendicularOffset);
            }

            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            points.add(new BlockPos(x, surfaceY + 1, z));
        }

        // Add basket as final point
        points.add(basket);

        return points;
    }

    /**
     * Finds the nearest player within detection radius.
     */
    private PlayerEntity findNearestPlayer(double radius) {
        PlayerEntity nearest = null;
        double nearestDistance = radius * radius;

        for (PlayerEntity player : mob.getWorld().getPlayers()) {
            if (!player.isSpectator() && player.isAlive()) {
                double distance = player.squaredDistanceTo(mob);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
        }

        return nearest;
    }
}
