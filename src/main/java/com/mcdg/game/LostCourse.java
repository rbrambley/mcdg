package com.mcdg.game;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Represents a challenge course that can be discovered through exploration.
 */
public record LostCourse(
    UUID courseId,
    String name,
    BlockPos entrancePosition,
    BlockPos courseAnchor,
    List<ItemStack> rewards,
    ChallengeCourseType type,
    boolean isDiscovered
) {
    public LostCourse {
        if (courseId == null) {
            throw new IllegalArgumentException("courseId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (entrancePosition == null) {
            throw new IllegalArgumentException("entrancePosition is required");
        }
        if (courseAnchor == null) {
            throw new IllegalArgumentException("courseAnchor is required");
        }
        if (rewards == null) {
            throw new IllegalArgumentException("rewards is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        rewards = List.copyOf(rewards);
    }

    /**
     * Creates a new LostCourse with the discovered flag set to true.
     */
    public LostCourse markDiscovered() {
        return new LostCourse(courseId, name, entrancePosition, courseAnchor, rewards, type, true);
    }

    /**
     * Creates a new LostCourse with updated rewards.
     */
    public LostCourse withRewards(List<ItemStack> newRewards) {
        return new LostCourse(courseId, name, entrancePosition, courseAnchor, newRewards, type, isDiscovered);
    }
}