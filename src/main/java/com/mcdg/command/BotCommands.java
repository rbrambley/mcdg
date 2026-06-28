package com.mcdg.command;

import com.mcdg.McdgMod;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.BotSimulator;
import com.mcdg.game.BotSimulator.BotSkill;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundStateManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class BotCommands {
    private BotCommands() {
    }

    // Bot commands for multiplayer testing

    public static int executeBotAdd(ServerCommandSource source, String name, String skillString) {
        BotSkill skill;
        try {
            skill = BotSkill.valueOf(skillString.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid skill level. Use: BEGINNER, INTERMEDIATE, or PRO"));
            return 0;
        }

        UUID botUuid = BotSimulator.addBot(name, skill);
        source.sendFeedback(() -> Text.literal("Bot added: " + name + " (" + skill + ") - UUID: " + botUuid), true);
        return 1;
    }

    public static int executeBotRemove(ServerCommandSource source, RoundStateManager roundStateManager, String uuidString) {
        try {
            UUID botUuid = UUID.fromString(uuidString);
            if (BotSimulator.isBot(botUuid)) {
                BotSimulator.removeBot(botUuid);
                roundStateManager.clearPlayer(botUuid);
                source.sendFeedback(() -> Text.literal("Bot removed: " + uuidString), true);
                return 1;
            } else {
                source.sendError(Text.literal("Bot not found: " + uuidString));
                return 0;
            }
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid UUID format: " + uuidString));
            return 0;
        }
    }

    public static int executeBotList(ServerCommandSource source) {
        var bots = BotSimulator.getBots();
        if (bots.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No bots registered."), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Registered bots (" + bots.size() + "):"), false);
        for (var entry : bots.entrySet()) {
            BotSimulator.BotProfile bot = entry.getValue();
            source.sendFeedback(() -> Text.literal("  - " + bot.name() + " (" + bot.skill() + "): " + bot.uuid()), false);
        }
        return 1;
    }

    public static int executeBotClear(ServerCommandSource source, RoundStateManager roundStateManager) {
        for (UUID botUuid : BotSimulator.getBots().keySet()) {
            roundStateManager.clearPlayer(botUuid);
        }
        BotSimulator.clearAllBots();
        source.sendFeedback(() -> Text.literal("All bots cleared."), true);
        return 1;
    }

    public static int executeBotJoinRound(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        try {
            if (!courseManager.isRoundActive()) {
                source.sendError(Text.literal("No active round. Start a round first."));
                return 0;
            }

            var bots = BotSimulator.getBots();
            if (bots.isEmpty()) {
                source.sendError(Text.literal("No bots registered. Add bots first with /mcdg bot add"));
                return 0;
            }

            Optional<PlacedCourseState> placedOpt = courseManager.getPlacedCourseState();
            if (placedOpt.isEmpty()) {
                source.sendError(Text.literal("No placed course state found."));
                return 0;
            }

            PlacedCourseState placed = placedOpt.get();
            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee == null) {
                source.sendError(Text.literal("No tee position found for hole 1."));
                return 0;
            }

            int joinedCount = 0;
            for (UUID botUuid : bots.keySet()) {
                try {
                    // Add bot to active participants
                    courseManager.addActiveParticipantId(botUuid);
                    
                    // Initialize bot's round state
                    roundStateManager.startRoundForPlayer(botUuid, firstTee);
                    joinedCount++;
                } catch (Exception e) {
                    McdgMod.LOGGER.error("Error adding bot {} to round: {}", botUuid, e.getMessage());
                }
            }

            final int finalJoinedCount = joinedCount;
            source.sendFeedback(() -> Text.literal("Added " + finalJoinedCount + " bots to the round."), true);
            return 1;
        } catch (Exception e) {
            McdgMod.LOGGER.error("Error in bot join round: {}", e.getMessage(), e);
            source.sendError(Text.literal("Error adding bots to round."));
            return 0;
        }
    }

    public static int executeBotLeaveRound(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        try {
            var bots = BotSimulator.getBots();
            if (bots.isEmpty()) {
                source.sendError(Text.literal("No bots registered."));
                return 0;
            }

            int removedCount = 0;
            for (UUID botUuid : bots.keySet()) {
                try {
                    // Remove bot from active participants
                    courseManager.removeActiveParticipantId(botUuid);
                    
                    // Clear bot's round state
                    roundStateManager.clearPlayer(botUuid);
                    removedCount++;
                } catch (Exception e) {
                    McdgMod.LOGGER.error("Error removing bot {}: {}", botUuid, e.getMessage());
                }
            }

            final int finalRemovedCount = removedCount;
            source.sendFeedback(() -> Text.literal("Removed " + finalRemovedCount + " bots from the round."), true);
            return 1;
        } catch (Exception e) {
            McdgMod.LOGGER.error("Error in bot leave round: {}", e.getMessage(), e);
            source.sendError(Text.literal("Error removing bots from round."));
            return 0;
        }
    }
}
