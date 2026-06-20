package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;

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

public final class RoundAdminCommands {
    private RoundAdminCommands() {
    }

    public static int executeGotoLie(ServerCommandSource source, RoundStateManager roundStateManager) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            Optional<BlockPos> relocated = HoleProgressTracker.relocatePlayerToSafeLie(player, roundStateManager);
            if (relocated.isEmpty()) {
                player.sendMessage(Text.literal("No active lie found to teleport to."), true);
                return 0;
            }

            BlockPos lie = relocated.get();
            player.sendMessage(
                    Text.literal("Teleported to lie: " + lie.getX() + ", " + lie.getY() + ", " + lie.getZ())
                            .formatted(Formatting.GREEN),
                    true
            );
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }

    public static int executeEndRound(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (!courseManager.isRoundActive()) {
            source.sendError(Text.literal("No active round to end."));
            return 0;
        }

        CommandUtils.removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);
        courseManager.setRoundActive(false);
        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        source.sendFeedback(() -> Text.literal("Round ended. Use /mcdg cleanupcourse to restore terrain edits."), true);
        return 1;
    }

    public static int executeJoinRound(
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
                RoundInventoryCleaner.restoreRoundInventory(player);
                alreadyJoinedCount++;
                continue;
            }

            BlockPos safeTee = SafePositionFinder.resolveSafeFeetNear(world, firstTee);
            roundStateManager.startRoundForPlayer(playerId, safeTee);
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            RoundInventoryCleaner.restoreRoundInventory(player);
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

    public static int executeRoundStatus(
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
