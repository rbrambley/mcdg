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
import com.mcdg.game.RoundPresentationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                    "Created active course '" + course.name() + "' with " + course.holes().size() + " holes (seed=" + seed + "). Use /mcdg startround or /mcdg practicecourse to place it near you on the surface."
                            + signatureSuffix
            ), false);
            return 1;
        } catch (RuntimeException ex) {
            source.sendError(Text.literal("Course generation failed: " + ex.getMessage()));
            return 0;
        }
    }

    public static int executeListCourses(
                    ServerCommandSource source,
                    PracticeCourseStorage practiceCourseStorage
    ) {
            List<PracticeCourseStorage.ReusableCourseEntry> entries = practiceCourseStorage.listReusable(source.getServer());
            if (entries.isEmpty()) {
                    source.sendFeedback(() -> Text.literal("No reusable courses are saved yet."), false);
                    return 1;
            }

            source.sendFeedback(() -> Text.literal("Reusable courses (newest first): " + entries.size()), false);
            for (PracticeCourseStorage.ReusableCourseEntry entry : entries) {
                    String command = "/mcdg playcourse " + entry.index();
                    String strictCommand = "/mcdg playcourse " + entry.index() + " strict";
                    String removeCommand = "/mcdg removecourse " + entry.index();
                    source.sendFeedback(() -> {
                        var line = Text.literal(
                                "#" + entry.index()
                                        + " " + entry.name()
                                        + " seed=" + entry.seed()
                                        + " holes=" + entry.holeCount()
                                        + " world=" + entry.worldKey()
                                        + " source=" + entry.sourceTag()
                                        + " compact=" + (entry.compactPreferred() ? "yes" : "no")
                        ).styled(style -> style
                                .withColor(Formatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + command)))
                        ).append(Text.literal("  [STRICT]")
                                .styled(style -> style
                                        .withColor(Formatting.GOLD)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, strictCommand))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + strictCommand)))
                                ));
                        if ("resort-surround".equals(entry.sourceTag())) {
                            line = line.append(Text.literal("  [RESORT]")
                                    .styled(style -> style
                                            .withColor(Formatting.GREEN)
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Use /mcdg removesurroundcourses to cleanup")))
                                    ));
                        } else {
                            line = line.append(Text.literal("  [REMOVE]")
                                    .styled(style -> style
                                            .withColor(Formatting.RED)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, removeCommand))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Delete from catalog: " + removeCommand + "\nThis does not cleanup world blocks.")))
                                    ));
                        }
                        return line;
                    }, false);
            }
            MenuCommands.sendBackToMenu(source);
            return 1;
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

    public static int executePlayCourse(
                    ServerCommandSource source,
                    ActiveCourseManager courseManager,
                    RoundStateManager roundStateManager,
                    RoundPresentationService roundPresentationService,
                    boolean skipRoundPresentation,
                    com.mcdg.rules.TournamentRulesetManager rulesetManager,
                    boolean forceStrict,
                    com.mcdg.rules.TournamentRulesetManager.StrictSurfacePreset strictPreset,
                    PracticeCourseStorage practiceCourseStorage,
                    int oneBasedIndex,
                    Collection<ServerPlayerEntity> selectedPlayers
    ) {
            if (forceStrict) {
                    rulesetManager.setActiveRuleset(com.mcdg.rules.TournamentRulesetManager.Ruleset.STRICT);
                    if (strictPreset != null) {
                            rulesetManager.setStrictSurfacePreset(strictPreset);
                    }
            }

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

            return RoundLifecycleCommands.executeResumeCourse(
                    source,
                    courseManager,
                    roundStateManager,
                    roundPresentationService,
                    skipRoundPresentation,
                    selectedPlayers
            );
    }

    public static int executePlayCourseStrictPrompt(
                    ServerCommandSource source,
                    PracticeCourseStorage practiceCourseStorage,
                    int oneBasedIndex
    ) {
            Optional<PracticeCourseStorage.LoadedPracticeCourse> selected =
                    practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
            if (selected.isEmpty()) {
                    source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " was not found."));
                    return 0;
            }

            String baseCommand = "/mcdg playcourse " + oneBasedIndex + " strict ";
            source.sendFeedback(() -> Text.literal("Choose strict surface preset for course #" + oneBasedIndex + ":"), false);
            source.sendFeedback(() -> Text.literal("[FAST] forgiving strict profile")
                    .styled(style -> style
                            .withColor(Formatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, baseCommand + "fast"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + baseCommand + "fast" + "\nMost forgiving strict preset.")))
                    ), false);
            source.sendFeedback(() -> Text.literal("[BALANCED] default strict profile")
                    .styled(style -> style
                            .withColor(Formatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, baseCommand + "balanced"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + baseCommand + "balanced" + "\nBalanced strict preset (default).")))
                    ), false);
            source.sendFeedback(() -> Text.literal("[TOURNAMENT] hardest strict profile")
                    .styled(style -> style
                            .withColor(Formatting.GOLD)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, baseCommand + "tournament"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + baseCommand + "tournament" + "\nHardest strict preset for competitive play.")))
                    ), false);
            return 1;
    }

    public static int executePruneCourses(
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
            if (removedCourseName != null) { CommandUtils.broadcastCourseWaypointRemoval(source.getServer(), removedCourseName); }

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
            if (generated == null || generated.holes().isEmpty()) {
                    return generated;
            }

            List<Hole> normalized = new ArrayList<>(generated.holes().size());
            int signatureCount = 0;
            for (Hole hole : generated.holes()) {
                    if (hole.isSignature()) {
                            signatureCount++;
                    }
                    normalized.add(hole);
            }

            if (signatureCount == 1) {
                    return generated;
            }

            for (int i = 0; i < normalized.size(); i++) {
                    Hole hole = normalized.get(i);
                    if (hole.isSignature()) {
                            normalized.set(i, new Hole(
                                    hole.index(),
                                    hole.par(),
                                    hole.distanceFeet(),
                                    hole.tee(),
                                    hole.basket(),
                                    hole.fairwaySegments(),
                                    SignatureHoleType.NONE
                            ));
                    }
            }

            int sigIndex = Math.floorMod((int) generated.seed(), normalized.size());
            Hole selected = normalized.get(sigIndex);
            normalized.set(sigIndex, new Hole(
                    selected.index(),
                    selected.par(),
                    selected.distanceFeet(),
                    selected.tee(),
                    selected.basket(),
                    selected.fairwaySegments(),
                    SignatureHoleType.ISLAND_GREEN
            ));

            return new Course(generated.seed(), generated.name(), normalized);
    }
}
