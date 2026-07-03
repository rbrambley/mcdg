package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.ChallengeCourseBuildTracker;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.LostCourseStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import java.util.List;
import java.util.Optional;
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

        // If the course is already placed, load its persisted placed state and resume it.
        if (catalogEntry.get().isPlaced()) {
            Optional<PlacedCourseState> storedPlaced = LostCourseStorage.loadPlacedState(source.getServer(), UUID.fromString(courseIdString));
            if (storedPlaced.isPresent()) {
                source.sendFeedback(() -> Text.literal("Challenge course already built. Starting round...")
                        .formatted(Formatting.YELLOW), false);

                courseManager.setActiveCourse(course);
                courseManager.setPlacedCourseState(storedPlaced.get());
                courseManager.setActiveChallengeCourseId(UUID.fromString(courseIdString));
                return RoundLifecycleCommands.executeResumeCourse(
                        source,
                        courseManager,
                        roundStateManager,
                        roundPresentationService,
                        skipRoundPresentation,
                        List.of(player)
                );
            }
        }

        // Queue an incremental, non-blocking build for this challenge course.
        // One hole is placed per server tick so the server thread never freezes.
        ChallengeCourseBuildTracker.startBuild(
                UUID.fromString(courseIdString),
                world,
                anchor,
                course,
                player,
                source,
                courseManager,
                roundStateManager,
                roundPresentationService,
                skipRoundPresentation
        );
        return 1;
    }
}
