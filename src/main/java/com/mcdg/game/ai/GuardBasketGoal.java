package com.mcdg.game.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Custom AI goal for mobs to guard the basket area.
 * Mobs will stay within a radius of the basket and attack approaching players.
 */
public class GuardBasketGoal extends Goal {
    private static final double GUARD_RADIUS = 8.0;
    private static final double DETECTION_RADIUS = 16.0;
    private static final double RETURN_THRESHOLD = 2.0;
    private static final double CHASE_SPEED = 1.0;
    private static final double RETURN_SPEED = 0.8;

    private final MobEntity mob;
    private final BlockPos basketPosition;
    private final double guardRadius;
    private final double detectionRadius;
    private PlayerEntity targetPlayer;

    public GuardBasketGoal(MobEntity mob, BlockPos basketPosition) {
        this.mob = mob;
        this.basketPosition = basketPosition;
        this.guardRadius = GUARD_RADIUS;
        this.detectionRadius = DETECTION_RADIUS;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        // Only activate if mob is outside guard radius or player detected
        double distanceToBasket = mob.squaredDistanceTo(
                basketPosition.getX() + 0.5,
                basketPosition.getY(),
                basketPosition.getZ() + 0.5
        );

        if (distanceToBasket > guardRadius * guardRadius) {
            return true; // Return to basket if too far
        }

        // Check for nearby players
        targetPlayer = findNearestPlayer();
        return targetPlayer != null;
    }

    @Override
    public boolean shouldContinue() {
        if (targetPlayer != null && targetPlayer.isAlive() && targetPlayer.squaredDistanceTo(mob) < detectionRadius * detectionRadius) {
            return true; // Continue chasing target player
        }

        // Continue if returning to basket
        double distanceToBasket = mob.squaredDistanceTo(
                basketPosition.getX() + 0.5,
                basketPosition.getY(),
                basketPosition.getZ() + 0.5
        );
        return distanceToBasket > RETURN_THRESHOLD;
    }

    @Override
    public void start() {
        if (targetPlayer != null) {
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
        if (targetPlayer != null && targetPlayer.isAlive()) {
            // Chase target player
            mob.getLookControl().lookAt(targetPlayer);
            mob.getNavigation().startMovingTo(targetPlayer, CHASE_SPEED);
        } else {
            // Return to basket position
            mob.getLookControl().lookAt(
                    basketPosition.getX() + 0.5,
                    basketPosition.getY(),
                    basketPosition.getZ() + 0.5
            );
            mob.getNavigation().startMovingTo(
                    basketPosition.getX() + 0.5,
                    basketPosition.getY(),
                    basketPosition.getZ() + 0.5,
                    RETURN_SPEED
            );
        }
    }

    /**
     * Finds the nearest player within detection radius.
     */
    private PlayerEntity findNearestPlayer() {
        PlayerEntity nearest = null;
        double nearestDistance = detectionRadius * detectionRadius;

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