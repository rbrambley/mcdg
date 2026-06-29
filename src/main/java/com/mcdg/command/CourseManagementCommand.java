package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import com.mcdg.game.ScorecardManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class CourseManagementCommand {

    private CourseManagementCommand() {
    }

    static int executeResumeCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (course == null || placed == null) {
            source.sendError(Text.literal("No stale placed course found. Use /mcdg startround or /mcdg practicecourse first."));
            return 0;
        }
        Course normalizedCourse = CommandUtils.ensureSingleSignatureHole(course);
        if (normalizedCourse != course) {
            course = normalizedCourse;
            courseManager.setActiveCourse(course);
        }

        if (courseManager.isRoundActive()) {
            source.sendError(Text.literal("Round is already active."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Placed course world is unavailable."));
            return 0;
        }

        if (courseManager.isLegacyPracticeSnapshot()) {
            source.sendFeedback(() -> Text.literal(
                    "Warning: this practice course came from a legacy snapshot format. If anything looks off, run /mcdg cleanupcourse then rebuild with /mcdg practicecourse."
            ), false);
        }

        SavedCourseIntegrity integrity = validateSavedCourseIntegrity(world, course, placed);
        if (!integrity.valid()) {
            source.sendError(Text.literal(
                    "Saved course data is stale/unplayable: " + integrity.reason()
                            + ". Rebuild with /mcdg startround or choose another /mcdg playcourse entry."
            ));
            return 0;
        }

        int totalHoles = course.holes().size();
        List<ServerPlayerEntity> participants = CommandUtils.resolveRoundParticipants(
                source,
                world,
                selectedPlayers,
                "resumecourse"
        );
        if (participants.isEmpty()) {
            source.sendError(Text.literal("No eligible participants selected for this world."));
            return 0;
        }

        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        CommandUtils.removeTemporaryRoundItemsFromPlayers(participants);

        List<UUID> participantIds = new ArrayList<>();
        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Saved course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
            return 0;
        }

        RoundChunkLoader.loadCourseChunks(world, placed);
        RoundWindService.onRoundStart(world, course.seed());

        for (ServerPlayerEntity player : participants) {
            BlockPos safeTee = CommandUtils.resolveSafeFeetNear(world, firstTee);
            roundStateManager.startRoundForPlayer(player, safeTee);
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            CommandUtils.prepareRoundInventory(player);
            ScorecardManager.initializeScorecard(player, course, placed);
            participantIds.add(player.getUuid());
            player.sendMessage(Text.literal("Round resumed on existing course. Moved to Hole 1 tee."), true);
        }

        int initializedPlayers = participantIds.size();
        courseManager.setActiveParticipantIds(participantIds);

        final int trackedPlayers = initializedPlayers;
        CommandUtils.announceSignatureHole(source, course, participantIds);
        if (skipRoundPresentation) {
            courseManager.setRoundActive(true);
            CommandUtils.teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
            source.sendFeedback(() -> Text.literal(
                    "Round resumed. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
            ), true);
            // Send running scoreboard to all participants after round is active
            for (ServerPlayerEntity player : participants) {
                HoleProgressTracker.sendRunningScoreboardToPlayer(player, courseManager, roundStateManager);
            }
            return 1;
        }

        int totalPar = CommandUtils.totalCoursePar(course);
        roundPresentationService.startCountdown(
                source.getServer(),
                participantIds,
                course.name(),
                totalHoles,
                totalPar,
                () -> {
                    courseManager.setRoundActive(true);
                    CommandUtils.teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                    source.sendFeedback(() -> Text.literal(
                            "Round live on existing course. Players=" + trackedPlayers + "."
                    ), true);
                    // Send running scoreboard to all participants after round is active
                    for (ServerPlayerEntity player : participants) {
                        HoleProgressTracker.sendRunningScoreboardToPlayer(player, courseManager, roundStateManager);
                    }
                }
        );

        source.sendFeedback(() -> Text.literal(
                "Round resume presentation started. Players=" + trackedPlayers + "."
        ), true);
        return 1;
    }

    static int executeListCourses(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage
    ) {
        Set<Integer> staleIndices = new HashSet<>();
        List<PracticeCourseStorage.ReusableCourseEntry> initialEntries = practiceCourseStorage.listReusable(source.getServer());
        for (PracticeCourseStorage.ReusableCourseEntry entry : initialEntries) {
            Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded =
                    practiceCourseStorage.loadReusableByIndex(source.getServer(), entry.index());
            if (loaded.isEmpty()) {
                staleIndices.add(entry.index());
                continue;
            }

            PracticeCourseStorage.LoadedPracticeCourse reusable = loaded.get();
            ServerWorld world = source.getServer().getWorld(reusable.placedCourseState().worldKey());
            if (world == null) {
                staleIndices.add(entry.index());
                continue;
            }

            SavedCourseIntegrity integrity = validateSavedCourseIntegrity(world, reusable.course(), reusable.placedCourseState());
            if (!integrity.valid()) {
                staleIndices.add(entry.index());
            }
        }

        if (!staleIndices.isEmpty()) {
            int removed = practiceCourseStorage.pruneReusableByIndices(source.getServer(), staleIndices);
            if (removed > 0) {
                final int pruned = removed;
                source.sendFeedback(() -> Text.literal(
                        "Pruned " + pruned + " stale/unplayable reusable entries from catalog."
                ), false);
            }
        }

        List<PracticeCourseStorage.ReusableCourseEntry> entries = practiceCourseStorage.listReusable(source.getServer());
        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No reusable courses are saved yet."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Reusable courses (newest first): " + entries.size()), false);
        source.sendFeedback(() -> Text.literal("Tip: use /mcdg playcourse <index> to select and start a saved course in one step."), false);
        for (PracticeCourseStorage.ReusableCourseEntry entry : entries) {
            source.sendFeedback(() -> Text.literal(
                    "#" + entry.index()
                            + " " + entry.name()
                            + " seed=" + entry.seed()
                            + " holes=" + entry.holeCount()
                            + " world=" + entry.worldKey()
                            + " source=" + entry.sourceTag()
                            + " compact=" + (entry.compactPreferred() ? "yes" : "no")
            ), false);
        }
        return 1;
    }

    static int executeUseCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage,
            int oneBasedIndex
    ) {
        if (courseManager.isRoundActive()) {
            source.sendError(Text.literal("Round is active. End the round before switching reusable courses."));
            return 0;
        }

        Optional<PracticeCourseStorage.LoadedPracticeCourse> selected =
                practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
        if (selected.isEmpty()) {
            source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " was not found."));
            return 0;
        }

        PracticeCourseStorage.LoadedPracticeCourse loaded = selected.get();
        if (source.getServer().getWorld(loaded.placedCourseState().worldKey()) == null) {
            source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " points to an unavailable world."));
            return 0;
        }

        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        courseManager.setActiveCourse(CommandUtils.ensureSingleSignatureHole(loaded.course()));
        courseManager.setPlacedCourseState(loaded.placedCourseState());
        courseManager.setPersistentPlacedCourse(true);
        courseManager.setLegacyPracticeSnapshot(loaded.legacyFormat());
        courseManager.setRoundActive(false);

        Course active = courseManager.getActiveCourse().orElse(null);
        int holes = active == null ? 0 : active.holes().size();
        source.sendFeedback(() -> Text.literal(
                "Reusable course #" + oneBasedIndex + " activated: "
                        + (active == null ? "unknown" : active.name())
                        + " (holes=" + holes + "). Use /mcdg resumecourse to play this saved placement, or /mcdg startround to generate a new placement."
        ), true);
        return 1;
    }

    static int executePlayCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation,
            PracticeCourseStorage practiceCourseStorage,
            int oneBasedIndex,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        int activated = executeUseCourse(
                source,
                courseManager,
                roundStateManager,
                practiceCourseStorage,
                oneBasedIndex
        );
        if (activated == 0) {
            return 0;
        }

        return executeResumeCourse(
                source,
                courseManager,
                roundStateManager,
                roundPresentationService,
                skipRoundPresentation,
                selectedPlayers
        );
    }

    private static SavedCourseIntegrity validateSavedCourseIntegrity(ServerWorld world, Course course, PlacedCourseState placed) {
        if (placed.holeTees().isEmpty() || placed.holeBaskets().isEmpty()) {
            return new SavedCourseIntegrity(false, "missing tee/basket placement maps");
        }

        BlockPos holeOneTee = placed.holeTees().get(1);
        if (holeOneTee == null) {
            return new SavedCourseIntegrity(false, "hole 1 tee is missing");
        }

        for (Hole hole : course.holes()) {
            int holeIndex = hole.index();
            BlockPos teePos = placed.holeTees().get(holeIndex);
            if (teePos == null) {
                return new SavedCourseIntegrity(false, "hole " + holeIndex + " tee is missing");
            }

            BlockPos basketPos = placed.holeBaskets().get(holeIndex);
            if (basketPos == null) {
                return new SavedCourseIntegrity(false, "hole " + holeIndex + " basket is missing");
            }
        }

        return new SavedCourseIntegrity(true, "ok");
    }

    static int executePruneCourses(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            int keepCount
    ) {
        int removed = practiceCourseStorage.pruneReusable(source.getServer(), keepCount);
        if (removed <= 0) {
            source.sendFeedback(() -> Text.literal("No reusable courses were pruned. Keep count=" + keepCount + "."), false);
            return 1;
        }

        final int finalRemoved = removed;
        source.sendFeedback(() -> Text.literal(
                "Pruned " + finalRemoved + " reusable courses. Keep count=" + keepCount + "."
        ), true);
        return 1;
    }
}
