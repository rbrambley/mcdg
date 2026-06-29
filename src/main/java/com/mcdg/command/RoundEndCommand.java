package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class RoundEndCommand {

    private RoundEndCommand() {
    }

    static int executeEndRound(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (!courseManager.isRoundActive()) {
            source.sendError(Text.literal("No active round to end."));
            return 0;
        }

        CommandUtils.removeTemporaryRoundItemsFromCourseWorldPlayers(source, courseManager);
        courseManager.getPlacedCourseState().ifPresent(placed -> {
            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world != null) {
                RoundWindService.onRoundEnd(world);
            }
        });
        courseManager.setRoundActive(false);
        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        source.sendFeedback(() -> Text.literal("Round ended. Use /mcdg resetcourse to restore terrain edits."), true);
        return 1;
    }
}
