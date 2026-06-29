package com.mcdg.command;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.ChallengeCourseBuilder;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseType;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class ChallengeCourseCommands {
    private ChallengeCourseCommands() {
    }

    public static int executeStartChallenge(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation,
            String courseIdString
    ) {
        try {
            UUID courseId = UUID.fromString(courseIdString);
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid course ID: " + courseIdString));
            return 0;
        }

        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendError(Text.literal("Challenge course catalog not available"));
            return 0;
        }

        var catalogEntry = catalog.get().getCourse(UUID.fromString(courseIdString));
        if (catalogEntry.isEmpty()) {
            source.sendError(Text.literal("Challenge course not found: " + courseIdString));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be run by a player"));
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        Course course = catalogEntry.get().generatedCourse();
        BlockPos anchor = catalogEntry.get().courseAnchor();
        ChallengeCourseType type = catalogEntry.get().type();

        // Check if course is already placed
        if (catalogEntry.get().isPlaced()) {
            source.sendFeedback(() -> Text.literal("Challenge course already built. Starting round...")
                    .formatted(Formatting.YELLOW), false);
            
            // Set the active course
            courseManager.setActiveCourse(course);
            
            // Start the round directly
            return RoundStartCommand.executeStartRound(
                    source,
                    courseManager,
                    placementService,
                    placementValidator,
                    roundStateManager,
                    roundPresentationService,
                    skipRoundPresentation,
                    null, // practiceCourseStorage
                    false, // persistentCourse
                    false, // allowReusableFallback
                    List.of(player) // selectedPlayers
            );
        }

        source.sendFeedback(() -> Text.literal("Building challenge course: " + catalogEntry.get().name())
                .formatted(Formatting.LIGHT_PURPLE), false);

        // Build the challenge course
        var buildFuture = ChallengeCourseBuilder.buildChallengeCourse(world, anchor, course, type, progress -> {
                // Progress callback could be used for feedback
        });

        // For now, we'll make it synchronous for simplicity
        try {
            PlacedCourseState placedState = buildFuture.get();
            if (placedState == null) {
                source.sendError(Text.literal("Failed to build challenge course"));
                return 0;
            }

            // Set the active course
            courseManager.setActiveCourse(course);
            courseManager.setPlacedCourseState(placedState);

            // Mark course as placed in catalog
            catalog.get().markCourseAsPlaced(UUID.fromString(courseIdString));

            // Start the round
            return RoundStartCommand.executeStartRound(
                    source,
                    courseManager,
                    placementService,
                    placementValidator,
                    roundStateManager,
                    roundPresentationService,
                    skipRoundPresentation,
                    null, // practiceCourseStorage
                    false, // persistentCourse
                    false, // allowReusableFallback
                    List.of(player) // selectedPlayers
            );
        } catch (Exception e) {
            source.sendError(Text.literal("Error building challenge course: " + e.getMessage()));
            McdgMod.LOGGER.error("Error building challenge course", e);
            return 0;
        }
    }
}
