package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.RoundStateManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class RoundStatusCommand {

    private RoundStatusCommand() {
    }

    static int executeRoundStatus(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        Set<UUID> participantIds = courseManager.getActiveParticipantIds();
        if (participantIds.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Round status: no tracked participants."), false);
            return 1;
        }

        int onlineCount = 0;
        int withStateCount = 0;
        for (UUID participantId : participantIds) {
            if (source.getServer().getPlayerManager().getPlayer(participantId) != null) {
                onlineCount++;
            }
            if (roundStateManager.getState(participantId).isPresent()) {
                withStateCount++;
            }
        }

        final int totalParticipants = participantIds.size();
        final int totalOnline = onlineCount;
        final int totalWithState = withStateCount;
        final boolean roundActive = courseManager.isRoundActive();
        final String worldLabel = courseManager.getPlacedCourseState()
                .map(placed -> placed.worldKey().getValue().toString())
                .orElse("none");

        source.sendFeedback(() -> Text.literal(
                "Round status: active=" + roundActive
                        + ", participants=" + totalParticipants
                        + ", online=" + totalOnline
                        + ", withState=" + totalWithState
                        + ", world=" + worldLabel
        ), false);

        int listed = 0;
        for (UUID participantId : participantIds) {
            if (listed >= 10) {
                break;
            }

            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(participantId);
            String playerLabel = onlinePlayer == null
                    ? participantId.toString().substring(0, 8)
                    : onlinePlayer.getName().getString();
            var state = roundStateManager.getState(participantId).orElse(null);
            String stateLabel = state == null
                    ? "no-state"
                    : ("H" + state.currentHole() + " strokes=" + state.totalStrokes());
            String presence = onlinePlayer == null ? "offline" : "online";

            source.sendFeedback(() -> Text.literal(
                    " - " + playerLabel + " | " + presence + " | " + stateLabel
            ), false);
            listed++;
        }

        if (participantIds.size() > listed) {
            final int remaining = participantIds.size() - listed;
            source.sendFeedback(() -> Text.literal(" - ... and " + remaining + " more participant(s)."), false);
        }
        return 1;
    }
}
