package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import com.mcdg.game.ScorecardManager;
import com.mcdg.world.SafePositionFinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
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
            source.sendError(Text.literal("No stale placed course found. Use /mcdg startround first."));
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
                    "Warning: this practice course came from a legacy snapshot format. If anything looks off, run /mcdg cleanupcourse then rebuild with /mcdg startround."
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
        CommandUtils.removeTemporaryRoundItemsFromPlayers(participants);
        RoundChunkLoader.loadCourseChunks(world, placed);
        RoundWindService.onRoundStart(world, course.seed());

        List<UUID> participantIds = new ArrayList<>();
        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Saved course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
            return 0;
        }
        for (ServerPlayerEntity player : participants) {
            BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
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
        announceSignatureHole(source, course, participantIds);
        if (skipRoundPresentation) {
            courseManager.setRoundActive(true);
            teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
            source.sendFeedback(() -> Text.literal(
                    "Round resumed. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
            ), true);
            // Send running scoreboard to all participants after round is active
            for (ServerPlayerEntity player : participants) {
                HoleProgressTracker.sendRunningScoreboardToPlayer(player, courseManager, roundStateManager);
            }
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

    private static int totalCoursePar(Course course) {
        int par = 0;
        for (var hole : course.holes()) {
            par += hole.par();
        }
        return par;
    }

    private static void announceSignatureHole(ServerCommandSource source, Course course, List<UUID> participantIds) {
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

        for (UUID participantId : participantIds) {
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
            roundStateManager.startRoundForPlayer(sourcePlayer, safeTee);
        }

        sourcePlayer.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
    }
}
