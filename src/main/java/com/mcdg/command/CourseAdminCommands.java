package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.CourseFireProtection;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.McdgMod;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortBuilder;
import com.mcdg.world.ResortWaypointManager;
import com.mcdg.world.ResortCoursePlacement;
import com.mcdg.world.WorldSpawnHandler;
import com.mcdg.world.ResortData;
import com.mcdg.world.SafePositionFinder;
import com.mcdg.net.WaypointRemovedSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class CourseAdminCommands {
    private CourseAdminCommands() {
    }

    public static int executeCreateCourse(
            ServerCommandSource source,
            CourseGenerator generator,
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

    public static int executeStartRound(
                    ServerCommandSource source,
                    ActiveCourseManager courseManager,
                CoursePlacementService placementService,
                                    CoursePlacementValidator placementValidator,
                                    RoundStateManager roundStateManager,
                                    RoundPresentationService roundPresentationService,
                                    boolean skipRoundPresentation,
                                    PracticeCourseStorage practiceCourseStorage,
                                    boolean persistentCourse,
                                    boolean allowReusableFallback,
                                    Collection<ServerPlayerEntity> selectedPlayers
    ) {
            Course course = courseManager.getActiveCourse().orElse(null);
            if (course == null) {
                    source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> first."));
                    return 0;
            }
            Course normalizedCourse = ensureSingleSignatureHole(course);
            if (normalizedCourse != course) {
                    course = normalizedCourse;
                    courseManager.setActiveCourse(course);
            }

            if (courseManager.isRoundActive()) {
                    source.sendError(Text.literal("Round is already active."));
                    return 0;
            }

            PlacedCourseState existingPlaced = courseManager.getPlacedCourseState().orElse(null);
            net.minecraft.registry.RegistryKey<net.minecraft.world.World> existingWorldKey = existingPlaced != null ? existingPlaced.worldKey() : null;
            if (existingPlaced != null) {
                    ServerWorld existingWorld = source.getServer().getWorld(existingPlaced.worldKey());
                    if (existingWorld != null) {
                            placementService.resetPlacedCourse(existingWorld, existingPlaced);
                    }
                    courseManager.clearPlacedCourseState();
                    practiceCourseStorage.clear(source.getServer());
                    CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
            }

            ServerWorld world = source.getWorld();
            int totalHoles = course.holes().size();
            long requestedSeed = course.seed();
            boolean startedFromFallback = false;

            for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                    if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                            sendCourseBuildProgressOverlay(barPlayer, 0, totalHoles, 1, 1);
                    }
            }
            source.sendFeedback(() -> Text.literal("Starting course placement near your current surface position..."), false);

            try {
                    BlockPos baseOrigin = BlockPos.ofFloored(source.getPosition());
                    PlacedCourseState placed = null;
                    final int maxPlacementAttempts = 9;

                    for (int attempt = 1; attempt <= maxPlacementAttempts; attempt++) {
                            BlockPos attemptOrigin = offsetOriginForAttempt(baseOrigin, attempt);
                            final int displayAttempt = attempt;
                            try {
                                    placed = placementService.placeCourse(world, attemptOrigin, course, holesDone -> {
                                            for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                                    if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                                            sendCourseBuildProgressOverlay(barPlayer, holesDone, totalHoles, displayAttempt, maxPlacementAttempts);
                                                    }
                                            }
                                    });
                            } catch (RuntimeException placementEx) {
                                    if (attempt < maxPlacementAttempts) {
                                            final int nextAttempt = attempt + 1;
                                            source.sendFeedback(() -> Text.literal(
                                                    "Placement policy rejected this anchor (" + placementEx.getMessage()
                                                            + "). Retrying nearby (attempt " + nextAttempt + "/" + maxPlacementAttempts + ")..."
                                            ), false);
                                            continue;
                                    }
                                    throw placementEx;
                            }

                            CoursePlacementValidator.ValidationReport attemptReport = placementValidator.validatePlacedCourse(
                                    world,
                                    course,
                                    placed,
                                    "start-round-attempt-" + attempt
                            );

                            if (!hasRetryablePlacementIssue(attemptReport)) {
                                    break;
                            }

                            placementService.resetPlacedCourse(world, placed);
                            placed = null;

                            if (attempt < maxPlacementAttempts) {
                                    final int nextAttempt = attempt + 1;
                                    source.sendFeedback(() -> Text.literal(
                                            "Detected retryable placement issue (enclosure/route gap). Retrying at a nearby surface anchor (attempt "
                                                    + nextAttempt + "/" + maxPlacementAttempts + ")..."
                                    ), false);
                            }
                    }

                    if (placed == null && allowReusableFallback) {
                            Optional<PracticeCourseStorage.LoadedPracticeCourse> reusableFallback =
                                    practiceCourseStorage.loadMostRecentReusable(source.getServer(), world.getRegistryKey());
                            if (reusableFallback.isPresent()) {
                                    PracticeCourseStorage.LoadedPracticeCourse fallback = reusableFallback.get();
                                    course = ensureSingleSignatureHole(fallback.course());
                                    placed = fallback.placedCourseState();
                                    startedFromFallback = true;
                                    source.sendFeedback(() -> Text.literal(
                                            "Placement retries exhausted. Reusing the most recent recoverable course snapshot in this world."
                                    ), false);
                            }
                    }

                    if (placed == null) {
                            for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                    if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                            clearCourseBuildProgressOverlay(barPlayer);
                                    }
                            }
                            source.sendError(Text.literal(
                                    "Failed to place a surface-playable course after multiple attempts (enclosure/route issue persisted)."
                            ));
                            return 0;
                    }

                    for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                            if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                    clearCourseBuildProgressOverlay(barPlayer);
                            }
                    }

                    List<ServerPlayerEntity> participants = CommandUtils.resolveRoundParticipants(
                            source,
                            world,
                            selectedPlayers,
                            persistentCourse ? "practicecourse" : "startround"
                    );
                    if (participants.isEmpty()) {
                            source.sendError(Text.literal("No eligible participants selected for this world."));
                            return 0;
                    }

                    CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                    CommandUtils.removeRoundThrowItemsFromPlayers(participants);

                    List<UUID> participantIds = new ArrayList<>();
                    BlockPos firstTee = placed.holeTees().get(1);
                    if (firstTee == null) {
                            source.sendError(Text.literal("Placed course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
                            return 0;
                    }
                    for (ServerPlayerEntity player : participants) {
                            BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
                            if (safeTee == null) {
                                player.sendMessage(Text.literal("Failed to find safe tee position. Skipping."), false);
                                continue;
                            }
                            roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                            CommandUtils.ensureSingleRoundThrowItem(player);
                            ScorecardManager.initializeScorecard(player, course, placed);
                            participantIds.add(player.getUuid());
                            player.sendMessage(Text.literal("Round staging. Moved to Hole 1 tee."), true);
                    }

                    int initializedPlayers = participantIds.size();
                    courseManager.setActiveParticipantIds(participantIds);

                    final int trackedPlayers = initializedPlayers;
                    announceSignatureHole(source, course, participantIds);

                    courseManager.setActiveCourse(course);
                    courseManager.setPlacedCourseState(placed);
                    if (existingWorldKey != null && !existingWorldKey.equals(placed.worldKey())) {
                            ServerWorld oldWorld = source.getServer().getWorld(existingWorldKey);
                            if (oldWorld != null) {
                                    CourseFireProtection.remove(oldWorld);
                            }
                    }
                    CourseFireProtection.apply(world);
                    courseManager.setPersistentPlacedCourse(persistentCourse);
                    courseManager.setLegacyPracticeSnapshot(false);
                    courseManager.setActiveCourseCatalogIndex(null);
                    if (persistentCourse) {
                            practiceCourseStorage.save(source.getServer(), course, placed);
                    }
                    if (!startedFromFallback) {
                            int catalogIndex = practiceCourseStorage.saveReusable(
                                    source.getServer(),
                                    course,
                                    placed,
                                    persistentCourse ? "practicecourse" : "startround",
                                    true
                            );
                            if (catalogIndex > 0) {
                                    courseManager.setActiveCourseCatalogIndex(catalogIndex);
                            }
                    }

                    if (startedFromFallback) {
                            long fallbackSeed = course.seed();
                            source.sendFeedback(() -> Text.literal(
                                    "Started fallback course seed=" + fallbackSeed + " (requested seed=" + requestedSeed + ")."
                            ), false);
                    }

                    if (skipRoundPresentation) {
                            courseManager.setRoundActive(true);
                            teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                            source.sendFeedback(() -> Text.literal(
                                            (persistentCourse ? "Practice course started" : "Round started")
                                                    + ". Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
                            ), true);
                            return 1;
                    }

                    int totalPar = totalCoursePar(course);
                    roundPresentationService.startCountdown(
                            source.getServer(),
                            participantIds,
                            course.name(),
                            course.holes().size(),
                            totalPar,
                            () -> {
                                    courseManager.setRoundActive(true);
                                    teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                                    source.sendFeedback(() -> Text.literal(
                                            (persistentCourse ? "Practice course live" : "Round live")
                                                    + ". Players=" + trackedPlayers + "."
                                    ), true);
                            }
                    );

                    source.sendFeedback(() -> Text.literal(
                                    (persistentCourse ? "Practice course presentation started" : "Round presentation started")
                                            + ". Players=" + trackedPlayers + "."
                    ), true);
                    return 1;
            } catch (RuntimeException ex) {
                    source.sendError(Text.literal("Failed to start round: " + ex.getMessage()));
                    return 0;
            }
    }

    public static int executeResumeCourse(
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
            Course normalizedCourse = ensureSingleSignatureHole(course);
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
            CourseFireProtection.apply(world);

            if (courseManager.isLegacyPracticeSnapshot()) {
                    source.sendFeedback(() -> Text.literal(
                            "Warning: this practice course came from a legacy snapshot format. If anything looks off, run /mcdg cleanupcourse then rebuild with /mcdg practicecourse."
                    ), false);
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
            CommandUtils.removeRoundThrowItemsFromPlayers(participants);

            List<UUID> participantIds = new ArrayList<>();
            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee == null) {
                    source.sendError(Text.literal("Saved course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
                    return 0;
            }
            for (ServerPlayerEntity player : participants) {
                    BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
                    roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                    player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                    CommandUtils.ensureSingleRoundThrowItem(player);
                    ScorecardManager.initializeScorecard(player, course, placed);
                    participantIds.add(player.getUuid());
                    player.sendMessage(Text.literal("Round resumed on existing course. Moved to Hole 1 tee."), true);
            }

            int initializedPlayers = participantIds.size();
            courseManager.setActiveParticipantIds(participantIds);

            final int trackedPlayers = initializedPlayers;
            announceSignatureHole(source, course, participantIds);
            if (skipRoundPresentation) {
                    courseManager.setRoundActive(true);
                    teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                    source.sendFeedback(() -> Text.literal(
                            "Round resumed. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
                    ), true);
                    return 1;
            }

            int totalPar = totalCoursePar(course);
            roundPresentationService.startCountdown(
                    source.getServer(),
                    participantIds,
                    course.name(),
                    totalHoles,
                    totalPar,
                    () -> {
                            courseManager.setRoundActive(true);
                            teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                            source.sendFeedback(() -> Text.literal(
                                    "Round live on existing course. Players=" + trackedPlayers + "."
                            ), true);
                    }
            );

            source.sendFeedback(() -> Text.literal(
                    "Round resume presentation started. Players=" + trackedPlayers + "."
            ), true);
            return 1;
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
            CourseFireProtection.apply(world);
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
                    TournamentRulesetManager rulesetManager,
                    boolean forceStrict,
                    TournamentRulesetManager.StrictSurfacePreset strictPreset,
                    PracticeCourseStorage practiceCourseStorage,
                    int oneBasedIndex,
                    Collection<ServerPlayerEntity> selectedPlayers
    ) {
            if (forceStrict) {
                    rulesetManager.setActiveRuleset(TournamentRulesetManager.Ruleset.STRICT);
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

            return executeResumeCourse(
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
                    com.mcdg.game.PlayerRoundSessionStorage playerRoundSessionStorage,
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
                    courseManager.setActiveCourse(null);
                    courseManager.clearPlacedCourseState();
                    courseManager.setActiveCourseCatalogIndex(null);
                    courseManager.setRoundActive(false);
                    CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                    for (UUID playerId : participantsToClear) {
                    	playerRoundSessionStorage.clearPlayer(source.getServer(), playerId, com.mcdg.McdgMod.LOGGER);
                    }
                    practiceCourseStorage.clear(source.getServer());
                    if (activePlaced != null) {
                            ServerWorld activeWorld = source.getServer().getWorld(activePlaced.worldKey());
                            if (activeWorld != null) {
                                    CourseFireProtection.remove(activeWorld);
                            }
                    }
            }
            HoleProgressTracker.resetAllState(source.getServer());

            source.sendFeedback(() -> Text.literal("Removed reusable course #" + oneBasedIndex + "."), true);
            return 1;
    }

    public static int executeCleanupCourse(
                    ServerCommandSource source,
                    ActiveCourseManager courseManager,
                CoursePlacementService placementService,
                                    RoundStateManager roundStateManager,
                                    PracticeCourseStorage practiceCourseStorage
    ) {
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (placed == null) {
                    source.sendError(Text.literal("No placed course state to reset."));
                    return 0;
            }

            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world == null) {
                    source.sendError(Text.literal("Original world for placed course is not available."));
                    return 0;
            }

            if (ResortAdminCommands.isCourseOverlappingResort(source.getServer(), placed)) {
                    source.sendError(Text.literal("Course overlaps with the resort area. Cleanup blocked to protect the resort."));
                    return 0;
            }
            evacuatePlayersBeforeCleanup(source, world, placed);
            placementService.resetPlacedCourse(world, placed);
            CourseFireProtection.remove(world);
            CommandUtils.removeJunkDropsNearCourse(world, placed);
            CommandUtils.removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);

            Integer activeCatalogIndex = courseManager.getActiveCourseCatalogIndex().orElse(null);
            if (activeCatalogIndex != null) {
                    practiceCourseStorage.pruneReusableByIndices(source.getServer(), Set.of(activeCatalogIndex));
            }

            courseManager.clearPlacedCourseState();
            courseManager.setActiveCourseCatalogIndex(null);
            courseManager.setRoundActive(false);
            CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
            practiceCourseStorage.clear(source.getServer());
            HoleProgressTracker.resetAllState(source.getServer());

            source.sendFeedback(() -> Text.literal("Course cleanup complete. Original blocks restored."), true);
            return 1;
    }

    public static int executeCleanupCourseByIndex(ServerCommandSource source, PracticeCourseStorage practiceCourseStorage, CoursePlacementService placementService, RoundStateManager roundStateManager, ActiveCourseManager courseManager, int oneBasedIndex) {
            Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
            if (loaded.isEmpty()) {
                    source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
                    return 0;
            }
            PlacedCourseState placedState = loaded.get().placedCourseState();
            if (ResortAdminCommands.isCourseOverlappingResort(source.getServer(), placedState)) {
                    source.sendError(Text.literal("Course overlaps with the resort area. Cleanup blocked to protect the resort."));
                    return 0;
            }

            PlacedCourseState placed = loaded.get().placedCourseState();
            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world == null) {
                    source.sendError(Text.literal("World for course #" + oneBasedIndex + " is not available."));
                    return 0;
            }

            evacuatePlayersBeforeCleanup(source, world, placed);
            placementService.resetPlacedCourse(world, placed);
            CommandUtils.removeJunkDropsNearCourse(world, placed);

            Integer activeCatalogIndex = courseManager.getActiveCourseCatalogIndex().orElse(null);
            boolean clearingActive = activeCatalogIndex != null && activeCatalogIndex == oneBasedIndex;
            boolean activeInSameWorld = !clearingActive && courseManager.getPlacedCourseState()
                    .map(p -> p.worldKey().equals(placed.worldKey()))
                    .orElse(false);
            if (clearingActive) {
                    courseManager.clearPlacedCourseState();
                    courseManager.setActiveCourseCatalogIndex(null);
                    courseManager.setRoundActive(false);
                    CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                    practiceCourseStorage.clear(source.getServer());
            }
            if (!activeInSameWorld) {
                    CourseFireProtection.remove(world);
            }
            HoleProgressTracker.resetAllState(source.getServer());

            CommandUtils.broadcastCourseWaypointRemoval(source.getServer(), loaded.get().course().name());
            practiceCourseStorage.pruneReusableByIndices(source.getServer(), Set.of(oneBasedIndex));

            source.sendFeedback(() -> Text.literal("Course #" + oneBasedIndex + " cleaned up and removed from catalog."), true);
            return 1;
    }

    public static int executeGotoCourse(ServerCommandSource source, ActiveCourseManager courseManager) {
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (placed == null) {
                    source.sendError(Text.literal("No placed course found. Run /mcdg startround first."));
                    return 0;
            }

            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee == null) {
                    source.sendError(Text.literal("Hole 1 tee location is unavailable."));
                    return 0;
            }

            try {
                    var player = source.getPlayerOrThrow();
                    ServerWorld world = source.getServer().getWorld(placed.worldKey());
                    BlockPos safeTee = world == null ? firstTee : SafePositionFinder.resolveSafeFeetNear(world, firstTee);
                    player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                    source.sendFeedback(() -> Text.literal("Teleported to Hole 1 tee."), false);
                    return 1;
            } catch (Exception ex) {
                    source.sendError(Text.literal("This command must be run by a player."));
                    return 0;
            }
    }

    public static int executeGotoCourseByIndex(ServerCommandSource source, PracticeCourseStorage practiceCourseStorage, int oneBasedIndex) {
            Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
            if (loaded.isEmpty()) {
                    source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
                    return 0;
            }

            PlacedCourseState placed = loaded.get().placedCourseState();
            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee == null) {
                    source.sendError(Text.literal("Hole 1 tee location is unavailable for course #" + oneBasedIndex + "."));
                    return 0;
            }

            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world == null) {
                    source.sendError(Text.literal("World for course #" + oneBasedIndex + " is not available."));
                    return 0;
            }

            try {
                    var player = source.getPlayerOrThrow();
                    BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
                    player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                    source.sendFeedback(() -> Text.literal("Teleported to Hole 1 of course #" + oneBasedIndex + "."), false);
                    return 1;
            } catch (Exception ex) {
                    source.sendError(Text.literal("This command must be run by a player."));
                    return 0;
            }
    }

    private static int totalCoursePar(Course course) {
            int par = 0;
            for (var hole : course.holes()) {
                    par += hole.par();
            }
            return par;
    }

    private static void sendCourseBuildProgressOverlay(
                    ServerPlayerEntity player,
                    int holesDone,
                    int totalHoles,
                    int attempt,
                    int maxAttempts
    ) {
            int clampedDone = Math.max(0, Math.min(totalHoles, holesDone));
            int percent = totalHoles <= 0 ? 0 : Math.round((clampedDone * 100.0f) / totalHoles);
            String title = "Building Course " + percent + "%";
            String subtitle = clampedDone + "/" + totalHoles + " holes  |  attempt " + attempt + "/" + maxAttempts;

            int stayTicks = clampedDone == 0 ? 240 : 18;
            int fadeOutTicks = clampedDone == 0 ? 0 : 5;
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(2, stayTicks, fadeOutTicks));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(Formatting.GOLD, Formatting.BOLD)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle).formatted(Formatting.WHITE)));
    }

    private static void clearCourseBuildProgressOverlay(ServerPlayerEntity player) {
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
    }

    private static void announceSignatureHole(ServerCommandSource source, Course course, List<java.util.UUID> participantIds) {
            var signatureHole = course.holes().stream().filter(hole -> hole.isSignature()).findFirst();
            if (signatureHole.isEmpty()) {
                    if (source.getEntity() instanceof ServerPlayerEntity player) {
                            player.sendMessage(Text.literal("Signature Hole: none detected on this layout."), false);
                    } else {
                            source.sendFeedback(() -> Text.literal("Signature Hole: none detected on this layout."), false);
                    }
                    return;
            }

            var hole = signatureHole.get();
            String message = "Signature Hole: H" + hole.index() + " | " + hole.signatureType().displayName();
            if (source.getEntity() instanceof ServerPlayerEntity player) {
                    showSignatureHoleOverlay(player, hole);
            } else {
                    source.sendFeedback(() -> Text.literal(message), false);
            }

            for (java.util.UUID participantId : participantIds) {
                    var player = source.getServer().getPlayerManager().getPlayer(participantId);
                    if (player != null) {
                            showSignatureHoleOverlay(player, hole);
                    }
            }
    }

    private static void showSignatureHoleOverlay(ServerPlayerEntity player, Hole hole) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(6, 60, 12));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Signature Hole: H" + hole.index()).formatted(Formatting.GOLD, Formatting.BOLD)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(hole.signatureType().displayName()).formatted(Formatting.WHITE)));
    }

    private static Course ensureSingleSignatureHole(Course generated) {
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

    private static boolean hasRetryablePlacementIssue(CoursePlacementValidator.ValidationReport report) {
            for (CoursePlacementValidator.ValidationIssue issue : report.issues()) {
                    if ("basket_deeply_enclosed".equals(issue.code())
                            || "tee_deeply_enclosed".equals(issue.code())
                            || "par5_alternate_route_missing".equals(issue.code())
                            || "alternate_route_missing".equals(issue.code())
                            || "landing_gap_too_long".equals(issue.code())) {
                            return true;
                    }
            }
            return false;
    }

    private static BlockPos offsetOriginForAttempt(BlockPos baseOrigin, int attempt) {
            if (attempt <= 1) {
                    return baseOrigin;
            }

            return switch (attempt) {
                    case 2 -> baseOrigin.add(48, 0, 0);
                    case 3 -> baseOrigin.add(-48, 0, 0);
                    case 4 -> baseOrigin.add(0, 0, 48);
                    case 5 -> baseOrigin.add(0, 0, -48);
                    case 6 -> baseOrigin.add(72, 0, 72);
                    case 7 -> baseOrigin.add(-72, 0, 72);
                    case 8 -> baseOrigin.add(72, 0, -72);
                    case 9 -> baseOrigin.add(-72, 0, -72);
                    default -> baseOrigin;
            };
    }

    private static void evacuatePlayersBeforeCleanup(ServerCommandSource source, ServerWorld world, PlacedCourseState placed) {
            ServerPlayerEntity sourcePlayer = source.getPlayer();
            BlockPos sourceAnchorSafeFeet = sourcePlayer != null && sourcePlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())
                    ? SafePositionFinder.resolveSafeFeetNear(world, sourcePlayer.getBlockPos())
                    : SafePositionFinder.resolveSafeFeetNear(world, world.getSpawnPos());
            if (isWithinPlacedCourseBuffer(placed, sourceAnchorSafeFeet, 28)) {
                    sourceAnchorSafeFeet = findNearestSafeOutsideCourse(world, placed, sourceAnchorSafeFeet, 28);
            }

            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                    if (!player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                            continue;
                    }

                    BlockPos targetFeet = SafePositionFinder.resolveSafeFeetNear(world, player.getBlockPos());
                    String relocationReason = "nearby";

                    if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                            targetFeet = findNearestSafeOutsideCourse(world, placed, targetFeet, 28);
                            relocationReason = "nearby-safe";
                    }
                    if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                            targetFeet = sourceAnchorSafeFeet;
                            relocationReason = "admin";
                    }

                    player.teleport(targetFeet.getX() + 0.5, targetFeet.getY() + 1.0, targetFeet.getZ() + 0.5);
                    if ("nearby".equals(relocationReason) || "nearby-safe".equals(relocationReason)) {
                            player.sendMessage(Text.literal("Course cleanup in progress. Relocated to a nearby safe location."), true);
                    } else {
                            player.sendMessage(Text.literal("Course cleanup in progress. Relocated to an admin safe zone."), true);
                    }
            }
    }

    private static BlockPos findNearestSafeOutsideCourse(ServerWorld world, PlacedCourseState placed, BlockPos originFeet, int bufferBlocks) {
            BlockPos safeOrigin = SafePositionFinder.resolveSafeFeetNear(world, originFeet);
            if (!isWithinPlacedCourseBuffer(placed, safeOrigin, bufferBlocks)) {
                    return safeOrigin;
            }

            for (int radius = 12; radius <= 144; radius += 12) {
                    for (int dx = -radius; dx <= radius; dx += 4) {
                            for (int dz = -radius; dz <= radius; dz += 4) {
                                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                                            continue;
                                    }
                                    BlockPos candidate = SafePositionFinder.resolveSafeFeetNear(world, safeOrigin.add(dx, 0, dz));
                                    if (!isWithinPlacedCourseBuffer(placed, candidate, bufferBlocks)) {
                                            return candidate;
                                    }
                            }
                    }
            }

            return safeOrigin;
    }

    private static boolean isWithinPlacedCourseBuffer(PlacedCourseState placed, BlockPos pos, int bufferBlocks) {
            if (placed == null || pos == null || placed.holeTees().isEmpty()) {
                    return false;
            }

            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (BlockPos tee : placed.holeTees().values()) {
                    minX = Math.min(minX, tee.getX());
                    maxX = Math.max(maxX, tee.getX());
                    minZ = Math.min(minZ, tee.getZ());
                    maxZ = Math.max(maxZ, tee.getZ());
            }
            for (BlockPos basket : placed.holeBaskets().values()) {
                    minX = Math.min(minX, basket.getX());
                    maxX = Math.max(maxX, basket.getX());
                    minZ = Math.min(minZ, basket.getZ());
                    maxZ = Math.max(maxZ, basket.getZ());
            }
            for (BlockPos alternate : placed.holeAlternateAnchors().values()) {
                    minX = Math.min(minX, alternate.getX());
                    maxX = Math.max(maxX, alternate.getX());
                    minZ = Math.min(minZ, alternate.getZ());
                    maxZ = Math.max(maxZ, alternate.getZ());
            }

            int expandedMinX = minX - bufferBlocks;
            int expandedMaxX = maxX + bufferBlocks;
            int expandedMinZ = minZ - bufferBlocks;
            int expandedMaxZ = maxZ + bufferBlocks;
            return pos.getX() >= expandedMinX
                    && pos.getX() <= expandedMaxX
                    && pos.getZ() >= expandedMinZ
                    && pos.getZ() <= expandedMaxZ;
    }

    private static void teleportSourcePlayerToHoleOne(
                    ServerCommandSource source,
                    ActiveCourseManager courseManager,
                    RoundStateManager roundStateManager
    ) {
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (placed == null) {
                    return;
            }

            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee == null) {
                    return;
            }

            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world == null) {
                    return;
            }

            BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);

            ServerPlayerEntity sourcePlayer = source.getPlayer();
            if (sourcePlayer == null) {
                    return;
            }

            if (!courseManager.getActiveParticipantIds().contains(sourcePlayer.getUuid())) {
                    return;
            }

            if (roundStateManager.getState(sourcePlayer.getUuid()).isEmpty()) {
                    roundStateManager.startRoundForPlayer(sourcePlayer.getUuid(), safeTee);
            }

            sourcePlayer.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
    }
}
