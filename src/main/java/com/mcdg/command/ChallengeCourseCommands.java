package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.BossMobSpawner;
import com.mcdg.game.ChallengeCourseBuildTracker;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseType;
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

    public static int executeGotoChallenge(
            ServerCommandSource source,
            String courseIdString
    ) {
        UUID courseId;
        try {
            courseId = UUID.fromString(courseIdString);
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid course ID: " + courseIdString));
            return 0;
        }

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be run by a player"));
            return 0;
        }

        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendError(Text.literal("Challenge course catalog not available"));
            return 0;
        }

        var catalogEntry = catalog.get().getCourse(courseId);
        if (catalogEntry.isEmpty()) {
            source.sendError(Text.literal("Challenge course not found: " + courseIdString));
            return 0;
        }

        if (!catalogEntry.get().isPlaced()) {
            source.sendError(Text.literal("Challenge course is not built yet. Use /mcdg startchallenge " + courseIdString + " to build and start it."));
            return 0;
        }

        // Load the placed course state to get the tee box 1 location
        Optional<PlacedCourseState> storedPlaced = LostCourseStorage.loadPlacedState(source.getServer(), courseId);
        if (storedPlaced.isEmpty()) {
            source.sendError(Text.literal("Challenge course placed state not found. The course may need to be rebuilt."));
            return 0;
        }

        PlacedCourseState placed = storedPlaced.get();
        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Hole 1 tee location is unavailable for challenge course: " + catalogEntry.get().name()));
            return 0;
        }

        // Teleport player to tee box 1
        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        BlockPos safeTee = world == null ? firstTee : CommandUtils.resolveSafeFeetNear(world, firstTee);
        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
        source.sendFeedback(() -> Text.literal("Teleported to challenge course: " + catalogEntry.get().name())
                .formatted(Formatting.GREEN), false);

        return 1;
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

                int result = RoundLifecycleCommands.executeResumeCourse(
                        source,
                        courseManager,
                        roundStateManager,
                        roundPresentationService,
                        skipRoundPresentation,
                        List.of(player)
                );

                // Start boss hole mob spawning if this is a boss hole
                if (result == 1 && catalogEntry.get().type() == ChallengeCourseType.BOSS_HOLE) {
                    UUID roundId = courseManager.getActiveChallengeCourseId().orElse(null);
                    if (roundId != null) {
                        BossMobSpawner.startSpawning(roundId, player, storedPlaced.get());
                        player.sendMessage(Text.literal("§cBoss Hole: Mobs will spawn to guard the basket!")
                                .formatted(Formatting.RED));
                    }
                }

                return result;
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

    public static int executeCancelChallenge(ServerCommandSource source, String courseIdString) {
        try {
            UUID courseId = UUID.fromString(courseIdString);
            if (ChallengeCourseBuildTracker.cancelBuild(courseId)) {
                source.sendFeedback(() -> Text.literal("Cancelled challenge course build: " + courseIdString)
                        .formatted(Formatting.GREEN), false);
                return 1;
            }
            source.sendError(Text.literal("No active build found for challenge course: " + courseIdString));
            return 0;
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid course ID: " + courseIdString));
            return 0;
        }
    }
}
