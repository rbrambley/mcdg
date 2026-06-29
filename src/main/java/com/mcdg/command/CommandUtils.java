package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundStateManager;
import com.mcdg.world.SafePositionFinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class CommandUtils {
    private CommandUtils() {
    }

    public static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
        return SafePositionFinder.resolveSafeFeetNear(world, preferredFeet);
    }

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        return SafePositionFinder.isStandableFeet(world, feet);
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

    public static int totalCoursePar(Course course) {
        int par = 0;
        for (var hole : course.holes()) {
            par += hole.par();
        }
        return par;
    }

    public static void announceSignatureHole(ServerCommandSource source, Course course, List<UUID> participantIds) {
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

    public static Course ensureSingleSignatureHole(Course generated) {
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

    public static void teleportSourcePlayerToHoleOne(
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

        BlockPos safeTee = resolveSafeFeetNear(world, firstTee);

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
