package com.mcdg.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import com.mcdg.game.ScorecardManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;

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

public final class RoundStartCommand {
    private RoundStartCommand() {
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
            boolean allowReusableFallback,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        Course course = courseManager.getActiveCourse().orElse(null);
        if (course == null) {
            source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> first."));
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

        // Clean up any previously placed course edits so repeated test runs start from a fresh world state.
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
        long requestedSeed = course.seed();
        boolean startedFromFallback = false;

        // Show a center-screen progress title before terrain generation starts.
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
                    course = CommandUtils.ensureSingleSignatureHole(fallback.course());
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

            // Course is placed — clear the center-screen progress title.
            for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                    clearCourseBuildProgressOverlay(barPlayer);
                }
            }

            List<ServerPlayerEntity> participants = CommandUtils.resolveRoundParticipants(
                    source,
                    world,
                    selectedPlayers,
                    "startround"
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
                source.sendError(Text.literal("Placed course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
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
                player.sendMessage(Text.literal("Round staging. Moved to Hole 1 tee."), true);
            }

            int initializedPlayers = participantIds.size();
            courseManager.setActiveParticipantIds(participantIds);

            final int trackedPlayers = initializedPlayers;
            CommandUtils.announceSignatureHole(source, course, participantIds);

            courseManager.setActiveCourse(course);
            courseManager.setPlacedCourseState(placed);
            courseManager.setPersistentPlacedCourse(false);
            courseManager.setLegacyPracticeSnapshot(false);
            if (!startedFromFallback) {
                // Compact is the default placement target; persist successful placements for reuse/recovery.
                practiceCourseStorage.saveReusable(
                        source.getServer(),
                        course,
                        placed,
                        "startround",
                        true
                );
            }

            if (startedFromFallback) {
                long fallbackSeed = course.seed();
                source.sendFeedback(() -> Text.literal(
                        "Started fallback course seed=" + fallbackSeed + " (requested seed=" + requestedSeed + ")."
                ), false);
            }

            if (skipRoundPresentation) {
                courseManager.setRoundActive(true);
                // LAN safety: force source-player teleport using the same path as /mcdg gotocourse.
                CommandUtils.teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                source.sendFeedback(() -> Text.literal(
                                "Round started. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
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
                    course.holes().size(),
                    totalPar,
                    () -> {
                        courseManager.setRoundActive(true);
                        CommandUtils.teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                        source.sendFeedback(() -> Text.literal(
                                "Round live. Players=" + trackedPlayers + "."
                        ), true);
                        // Send running scoreboard to all participants after round is active
                        for (ServerPlayerEntity player : participants) {
                            HoleProgressTracker.sendRunningScoreboardToPlayer(player, courseManager, roundStateManager);
                        }
                    }
            );

            source.sendFeedback(() -> Text.literal(
                            "Round presentation started. Players=" + trackedPlayers + "."
            ), true);
            return 1;
        } catch (RuntimeException ex) {
            source.sendError(Text.literal("Failed to start round: " + ex.getMessage()));
            return 0;
        }
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
}
