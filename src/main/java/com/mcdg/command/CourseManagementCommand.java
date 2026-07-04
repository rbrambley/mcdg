package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class CourseManagementCommand {

    private CourseManagementCommand() {
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
        int activated = CourseAdminCommands.executeUseCourse(
                source,
                courseManager,
                roundStateManager,
                practiceCourseStorage,
                oneBasedIndex
        );
        if (activated == 0) {
            return 0;
        }

        return RoundLifecycleCommands.executeResumeCourse(
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
