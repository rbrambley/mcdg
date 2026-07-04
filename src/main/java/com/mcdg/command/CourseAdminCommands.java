package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;

import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundStateManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class CourseAdminCommands {
    private CourseAdminCommands() {
    }

    public static int executeCreateCourse(
            ServerCommandSource source,
            com.mcdg.world.CourseGenerator generator,
            ActiveCourseManager courseManager,
            long seed
        ) {
        int holeCount = 9;

        try {
            float facingYaw = source.getPlayer() != null ? source.getPlayer().getYaw() : 0.0f;
            Course generated = generator.generate(seed, holeCount, facingYaw);
            Course course = ensureSingleSignatureHole(generated);
            courseManager.setActiveCourse(course);
            courseManager.setActiveCourseCatalogIndex(null);

            Hole signatureHole = course.holes().stream().filter(Hole::isSignature).findFirst().orElse(null);
            String signatureSuffix = signatureHole == null
                    ? ""
                    : " Signature: H" + signatureHole.index() + " (" + signatureHole.signatureType().displayName() + ").";

            source.sendFeedback(() -> Text.literal(
                    "Created active course '" + course.name() + "' with " + course.holes().size() + " holes (seed=" + seed + "). Use /mcdg startround to place it near you on the surface."
                            + signatureSuffix
            ), false);
            return 1;
        } catch (RuntimeException ex) {
            source.sendError(Text.literal("Course generation failed: " + ex.getMessage()));
            return 0;
        }
    }

    public static int executeUseCourse(
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
            ServerWorld world = source.getServer().getWorld(loaded.placedCourseState().worldKey());
            if (world == null) {
                    source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " points to an unavailable world."));
                    return 0;
            }

            practiceCourseStorage.touchReusableByIndex(source.getServer(), oneBasedIndex);

            CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
            courseManager.setActiveCourse(ensureSingleSignatureHole(loaded.course()));
            courseManager.setActiveCourseCatalogIndex(oneBasedIndex);
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

    public static int executeRemoveCourse(
                    ServerCommandSource source,
                    ActiveCourseManager courseManager,
                    RoundStateManager roundStateManager,
                    PracticeCourseStorage practiceCourseStorage,
                    PlayerRoundSessionStorage playerRoundSessionStorage,
                    int oneBasedIndex
    ) {
            Optional<PracticeCourseStorage.LoadedPracticeCourse> courseToRemove = practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
            String removedCourseName = courseToRemove.map(c -> c.course().name()).orElse(null);
            int removed = practiceCourseStorage.pruneReusableByIndices(source.getServer(), Set.of(oneBasedIndex));
            if (removed <= 0) {
                    source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " was not found."));
                    return 0;
            }
            // Course waypoint removal removed (player waypoints replaced by Xaero's Minimap)

            Integer activeCatalogIndex = courseManager.getActiveCourseCatalogIndex().orElse(null);
            boolean wasActiveMatch = activeCatalogIndex != null && activeCatalogIndex == oneBasedIndex;
            PlacedCourseState activePlaced = courseManager.getPlacedCourseState().orElse(null);
            if (wasActiveMatch || courseManager.isRoundActive()) {
            	java.util.List<UUID> participantsToClear = new java.util.ArrayList<>(courseManager.getActiveParticipantIds());
                    if (activePlaced != null) {
                        ServerWorld worldToUnload = source.getServer().getWorld(activePlaced.worldKey());
                        if (worldToUnload != null) {
                            RoundChunkLoader.unloadAll(worldToUnload);
                        }
                    }
                    courseManager.setActiveCourse(null);
                    courseManager.clearPlacedCourseState();
                    courseManager.setActiveCourseCatalogIndex(null);
                    courseManager.setRoundActive(false);
                    CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                    for (UUID playerId : participantsToClear) {
                    	playerRoundSessionStorage.clearPlayer(source.getServer(), playerId, com.mcdg.McdgMod.LOGGER);
                    }
                    practiceCourseStorage.clear(source.getServer());

            }
            HoleProgressTracker.resetAllState(source.getServer());

            source.sendFeedback(() -> Text.literal("Removed reusable course #" + oneBasedIndex + "."), true);
            return 1;
    }

    static Course ensureSingleSignatureHole(Course generated) {
            return CommandUtils.ensureSingleSignatureHole(generated);
    }
}
