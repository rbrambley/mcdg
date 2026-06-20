package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ActiveCourseManager;

import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.SafePositionFinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class RoundLifecycleCommands {
    private RoundLifecycleCommands() {
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
        Course normalizedCourse = CourseAdminCommands.ensureSingleSignatureHole(course);
        if (normalizedCourse != course) {
            course = normalizedCourse;
            courseManager.setActiveCourse(course);
        }

        if (courseManager.isRoundActive()) {
            source.sendError(Text.literal("Round is already active."));
            return 0;
        }

        PlacedCourseState existingPlaced = courseManager.getPlacedCourseState().orElse(null);
        if (existingPlaced != null) {
            ServerWorld existingWorld = source.getServer().getWorld(existingPlaced.worldKey());
            if (existingWorld != null) {
                placementService.resetPlacedCourse(existingWorld, existingPlaced);
                RoundChunkLoader.unloadAll(existingWorld);
            }
            courseManager.clearPlacedCourseState();
            practiceCourseStorage.clear(source.getServer());
            CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        }

        ServerWorld world = source.getWorld();
        int totalHoles = course.holes().size();
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
                    course = CourseAdminCommands.ensureSingleSignatureHole(fallback.course());
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
                source.sendError(Text.literal("Placed course is missing hole 1 tee position."));
                return 0;
            }

            if (!persistentCourse) {
                practiceCourseStorage.clear(source.getServer());
            }
            courseManager.setPlacedCourseState(placed);
            RoundChunkLoader.loadCourseChunks(world, placed);

            for (ServerPlayerEntity player : participants) {
                BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
                roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                CommandUtils.ensureSingleRoundThrowItem(player);
                ScorecardManager.initializeScorecard(player, course, placed);
                participantIds.add(player.getUuid());
                player.sendMessage(Text.literal("Round started. Moved to Hole 1 tee."), true);
            }

            int initializedPlayers = participantIds.size();
            courseManager.setActiveParticipantIds(participantIds);
            if (persistentCourse) {
                int catalogIndex = practiceCourseStorage.saveReusable(
                        source.getServer(),
                        course,
                        placed,
                        "practicecourse",
                        true
                );
                courseManager.setActiveCourseCatalogIndex(catalogIndex);
                courseManager.setPersistentPlacedCourse(true);
            } else {
                courseManager.setPersistentPlacedCourse(false);
            }

            final int trackedPlayers = initializedPlayers;
            announceSignatureHole(source, course, participantIds);
            if (skipRoundPresentation) {
                courseManager.setRoundActive(true);
                teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                source.sendFeedback(() -> Text.literal(
                        "Round started. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
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
                                "Round live. Players=" + trackedPlayers + "."
                        ), true);
                    }
            );

            if (startedFromFallback) {
                source.sendFeedback(() -> Text.literal(
                        "Round started from reusable fallback. Players=" + trackedPlayers + "."
                ), true);
            } else {
                source.sendFeedback(() -> Text.literal(
                        "Round placement complete. Starting presentation. Players=" + trackedPlayers + "."
                ), true);
            }
            return 1;
        } catch (RuntimeException ex) {
            for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                    clearCourseBuildProgressOverlay(barPlayer);
                }
            }
            source.sendError(Text.literal("Course placement failed: " + ex.getMessage()));
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
        Course normalizedCourse = CourseAdminCommands.ensureSingleSignatureHole(course);
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
        RoundChunkLoader.loadCourseChunks(world, placed);

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
        RoundChunkLoader.unloadAll(world);
        evacuatePlayersBeforeCleanup(source, world, placed);
        placementService.resetPlacedCourse(world, placed);
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

        RoundChunkLoader.unloadAll(world);
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
        HoleProgressTracker.resetAllState(source.getServer());

        // Course waypoint removal removed (player waypoints replaced by Xaero's Minimap)
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
