package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundStateManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class CommandUtils {
    private CommandUtils() {
    }

    public static List<ServerPlayerEntity> resolveRoundParticipants(
            ServerCommandSource source,
            ServerWorld world,
            Collection<ServerPlayerEntity> selectedPlayers,
            String commandName
    ) {
        LinkedHashSet<ServerPlayerEntity> participants = new LinkedHashSet<>();
        if (selectedPlayers != null && !selectedPlayers.isEmpty()) {
            participants.addAll(selectedPlayers);
        } else {
            ServerPlayerEntity sourcePlayer = source.getPlayer();
            if (sourcePlayer == null) {
                source.sendError(Text.literal(
                        "Console usage requires explicit players: /mcdg " + commandName + " <players>."
                ));
                return List.of();
            }
            participants.add(sourcePlayer);
        }

        List<ServerPlayerEntity> sameWorldParticipants = new ArrayList<>();
        int skippedDifferentWorld = 0;
        for (ServerPlayerEntity participant : participants) {
            if (participant.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                sameWorldParticipants.add(participant);
            } else {
                skippedDifferentWorld++;
            }
        }

        final int finalSkipped = skippedDifferentWorld;
        if (finalSkipped > 0) {
            source.sendFeedback(() -> Text.literal(
                    "Skipped " + finalSkipped + " player(s) not in the current course world."
            ), false);
        }

        return sameWorldParticipants;
    }

    public static void clearRoundStateForTrackedParticipants(ActiveCourseManager courseManager, RoundStateManager roundStateManager) {
        roundStateManager.clearPlayers(courseManager.getActiveParticipantIds());
        courseManager.clearActiveParticipantIds();
    }

    public static void removeTemporaryRoundItemsFromCourseWorldPlayers(ServerCommandSource source, ActiveCourseManager courseManager) {
        Set<UUID> participantIds = courseManager.getActiveParticipantIds();
        if (!participantIds.isEmpty()) {
            for (UUID playerId : participantIds) {
                ServerPlayerEntity participant = source.getServer().getPlayerManager().getPlayer(playerId);
                if (participant != null) {
                    RoundInventoryCleaner.purgeTemporaryRoundItemsAndJunk(participant);
                }
            }
            return;
        }

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            return;
        }
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey() == placed.worldKey()) {
                RoundInventoryCleaner.purgeTemporaryRoundItemsAndJunk(player);
            }
        }
    }

    public static void removeTemporaryRoundItemsFromPlayers(Collection<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            RoundInventoryCleaner.purgeTemporaryRoundItemsAndJunk(player);
        }
    }

    public static void prepareRoundInventory(ServerPlayerEntity player) {
        RoundInventoryCleaner.prepareRoundInventory(player);
    }

    public static void removeJunkDropsNearCourse(ServerWorld world, PlacedCourseState placed) {
        if (placed.originalBlocks().isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : placed.originalBlocks().keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        Box search = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).expand(8.0);
        for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, search, e -> true)) {
            if (RoundInventoryCleaner.isJunkItem(entity.getStack())) {
                entity.discard();
            }
        }
    }
}
