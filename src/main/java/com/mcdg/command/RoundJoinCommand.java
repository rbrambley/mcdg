package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class RoundJoinCommand {

    private RoundJoinCommand() {
    }

    static int executeJoinRound(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (course == null || placed == null) {
            source.sendError(Text.literal("No active placed course. Run /mcdg startround first."));
            return 0;
        }
        if (!courseManager.isRoundActive()) {
            source.sendError(Text.literal("Round is not live. Wait for presentation to finish before joining."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Placed course world is unavailable."));
            return 0;
        }

        List<ServerPlayerEntity> participants = CommandUtils.resolveRoundParticipants(source, world, selectedPlayers, "joinround");
        if (participants.isEmpty()) {
            source.sendError(Text.literal("No eligible participants selected for this world."));
            return 0;
        }

        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Hole 1 tee location is unavailable."));
            return 0;
        }

        int joinedCount = 0;
        int alreadyJoinedCount = 0;
        List<UUID> joinedIds = new ArrayList<>();
        for (ServerPlayerEntity player : participants) {
            UUID playerId = player.getUuid();
            boolean alreadyTracked = courseManager.getActiveParticipantIds().contains(playerId);
            boolean hasRoundState = roundStateManager.getState(playerId).isPresent();
            if (alreadyTracked && hasRoundState) {
                RoundInventoryCleaner.prepareRoundInventory(player);
                alreadyJoinedCount++;
                continue;
            }

            BlockPos safeTee = CommandUtils.resolveSafeFeetNear(world, firstTee);
            roundStateManager.startRoundForPlayer(player, safeTee);
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            RoundInventoryCleaner.prepareRoundInventory(player);
            ScorecardManager.initializeScorecard(player, course, placed);
            player.sendMessage(Text.literal("Joined current round. Teleported to Hole 1 tee."), true);
            joinedIds.add(playerId);
            joinedCount++;
        }

        if (!joinedIds.isEmpty()) {
            courseManager.addActiveParticipantIds(joinedIds);
        }

        final int finalJoinedCount = joinedCount;
        final int finalAlreadyJoinedCount = alreadyJoinedCount;
        source.sendFeedback(() -> Text.literal(
                "Join round complete. Added=" + finalJoinedCount + ", already active=" + finalAlreadyJoinedCount + "."
        ), true);
        return finalJoinedCount > 0 ? 1 : 0;
    }
}
