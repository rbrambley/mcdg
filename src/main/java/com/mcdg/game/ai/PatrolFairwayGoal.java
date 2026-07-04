package com.mcdg.game.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Custom AI goal for mobs to patrol along the fairway between tee and basket.
 * Mobs move between waypoints and can potentially block disc flight paths.
 */
public class PatrolFairwayGoal extends Goal {
    private final MobEntity mob;
    private final BlockPos teePosition;
    private final BlockPos basketPosition;
    private final List<BlockPos> waypoints;
    private int currentWaypointIndex;
    private final double patrolSpeed;
    private final double chaseSpeed;
    private PlayerEntity targetPlayer;
    private boolean isChasing;
    private int chaseCooldown;

    public PatrolFairwayGoal(MobEntity mob, BlockPos teePosition, BlockPos basketPosition) {
        this.mob = mob;
        this.teePosition = teePosition;
        this.basketPosition = basketPosition;
        this.waypoints = generateWaypoints(teePosition, basketPosition);
        this.currentWaypointIndex = 0;
        this.patrolSpeed = 0.6; // Slower patrol speed
        this.chaseSpeed = 1.0; // Normal chase speed
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
        targetPlayer = findNearestPlayer(12.0); // 12 block detection radius
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
            if (targetPlayer != null && targetPlayer.isAlive() && targetPlayer.squaredDistanceTo(mob) < 144.0) { // 12 blocks
                return true;
            }
            // Lost target, return to patrolling
            isChasing = false;
            chaseCooldown = 60; // 3 second cooldown before chasing again
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

        if (distance < 4.0) { // Within 2 blocks of waypoint
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
     */
    private List<BlockPos> generateWaypoints(BlockPos tee, BlockPos basket) {
        List<BlockPos> points = new ArrayList<>();

        // Add tee as starting point
        points.add(tee);

        // Add 2-3 intermediate points along the fairway
        int intermediatePoints = 2 + (int) (Math.random() * 2); // 2-3 points
        for (int i = 1; i <= intermediatePoints; i++) {
            double t = (double) i / (intermediatePoints + 1);
            int x = MathHelper.lerp(t, tee.getX(), basket.getX());
            int z = MathHelper.lerp(t, tee.getZ(), basket.getZ());

            // Add some perpendicular offset for variety
            int perpendicularOffset = (int) (Math.random() * 6) - 3; // -3 to +3
            double dx = basket.getX() - tee.getX();
            double dz = basket.getZ() - tee.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0) {
                x += (int) Math.round((-dz / length) * perpendicularOffset);
                z += (int) Math.round((dx / length) * perpendicularOffset);
            }

            points.add(new BlockPos(x, tee.getY(), z));
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