package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.SurfaceResolver;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * Builds challenge courses with special blocks and integrates with autobuild system.
 */
public final class ChallengeCourseBuilder {
    private static final Set<UUID> BUILDING_COURSES = new HashSet<>();

    private ChallengeCourseBuilder() {}

    /**
     * Builds a challenge course at the specified anchor position.
     */
    public static CompletableFuture<PlacedCourseState> buildChallengeCourse(
            ServerWorld world,
            BlockPos anchor,
            Course course,
            ChallengeCourseType type,
            IntConsumer progressCallback
    ) {
        UUID courseId = UUID.nameUUIDFromBytes(("challenge-" + course.seed()).getBytes());
        
        if (BUILDING_COURSES.contains(courseId)) {
            McdgMod.LOGGER.warn("Challenge course {} is already being built", courseId);
            return CompletableFuture.completedFuture(null);
        }

        BUILDING_COURSES.add(courseId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Resolve surface position
                BlockPos surfaceAnchor = SurfaceResolver.resolveSurfacePos(world, anchor.getX(), anchor.getZ());
                
                // Place course using standard placement service
                CoursePlacementService placementService = new CoursePlacementService();
                PlacedCourseState placedState = placementService.placeCourseAtFixedOrigin(
                    world,
                    surfaceAnchor,
                    course,
                    progressCallback,
                    new HashSet<>(), // external protected positions
                    true, // skip hub (challenge courses don't need hub)
                    false // skip water estimation
                );

                if (placedState == null) {
                    McdgMod.LOGGER.error("Failed to place challenge course {}", courseId);
                    return null;
                }

                // Apply special blocks for challenge course
                applySpecialBlocks(world, placedState, type);

                McdgMod.LOGGER.info("Successfully built challenge course {} at ({}, {}, {})", 
                    courseId, surfaceAnchor.getX(), surfaceAnchor.getY(), surfaceAnchor.getZ());

                return placedState;
            } catch (Exception e) {
                McdgMod.LOGGER.error("Error building challenge course {}", courseId, e);
                return null;
            } finally {
                BUILDING_COURSES.remove(courseId);
            }
        });
    }

    /**
     * Applies special blocks to a placed challenge course.
     */
    private static void applySpecialBlocks(ServerWorld world, PlacedCourseState placedState, ChallengeCourseType type) {
        // For now, just place a simple banner at the first tee to mark it as a challenge course
        if (!placedState.holeTees().isEmpty()) {
            BlockPos firstTee = placedState.holeTees().get(1);
            if (firstTee != null) {
                BlockPos bannerPos = firstTee.add(0, 2, 0);
                world.setBlockState(bannerPos, Blocks.WHITE_BANNER.getDefaultState());
            }
        }

        McdgMod.LOGGER.info("Applied special blocks for {} challenge course", type.getDisplayName());
    }

    /**
     * Checks if a challenge course is currently being built.
     */
    public static boolean isBuilding(UUID courseId) {
        return BUILDING_COURSES.contains(courseId);
    }

    /**
     * Removes a challenge course from the world.
     */
    public static void removeChallengeCourse(ServerWorld world, PlacedCourseState placedState) {
        if (placedState == null) {
            return;
        }

        CoursePlacementService placementService = new CoursePlacementService();
        placementService.resetPlacedCourse(world, placedState);

        McdgMod.LOGGER.info("Removed challenge course");
    }
}