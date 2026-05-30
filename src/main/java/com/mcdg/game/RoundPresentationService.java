package com.mcdg.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RoundPresentationService {
    private static final int COUNTDOWN_SECONDS = 30;
    private static final int TICKS_PER_SECOND = 20;

    private final List<PendingCountdown> pendingCountdowns = new ArrayList<>();

    public void startCountdown(
            MinecraftServer server,
            List<UUID> participantIds,
            String courseName,
            int holeCount,
            int totalPar,
            Runnable onRoundLive
    ) {
        if (participantIds.isEmpty()) {
            onRoundLive.run();
            return;
        }

        Set<UUID> idSet = new HashSet<>(participantIds);
        PendingCountdown pending = new PendingCountdown(
                idSet,
                courseName,
                COUNTDOWN_SECONDS * TICKS_PER_SECOND,
                onRoundLive
        );
        pendingCountdowns.add(pending);

        String layoutLine = courseName + "  |  " + holeCount + " holes  |  Par " + totalPar;
        forEachParticipant(server, idSet, player -> {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 40, 10));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round starts in " + COUNTDOWN_SECONDS + "...").formatted(Formatting.WHITE)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(layoutLine).formatted(Formatting.GRAY)));
        });
    }

    public void tick(MinecraftServer server) {
        if (pendingCountdowns.isEmpty()) {
            return;
        }

        Iterator<PendingCountdown> iterator = pendingCountdowns.iterator();
        while (iterator.hasNext()) {
            PendingCountdown pending = iterator.next();
            if (pending.ticksRemaining > 0 && (pending.ticksRemaining % TICKS_PER_SECOND) == 0) {
                int seconds = pending.ticksRemaining / TICKS_PER_SECOND;
                forEachParticipant(server, pending.participantIds, player -> {
                    player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 25, 5));
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round starts in " + seconds + "...").formatted(Formatting.WHITE)));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(pending.courseName).formatted(Formatting.GRAY)));
                });
            }

            pending.ticksRemaining--;
            if (pending.ticksRemaining >= 0) {
                continue;
            }

            forEachParticipant(server, pending.participantIds, player -> {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round Live!").formatted(Formatting.GREEN, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(pending.courseName + "  |  Good luck.").formatted(Formatting.WHITE)));
            });
            pending.onRoundLive.run();
            iterator.remove();
        }
    }

    private static void forEachParticipant(MinecraftServer server, Set<UUID> participantIds, java.util.function.Consumer<ServerPlayerEntity> action) {
        for (UUID id : participantIds) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    private static final class PendingCountdown {
        private final Set<UUID> participantIds;
        private final String courseName;
        private int ticksRemaining;
        private final Runnable onRoundLive;

        private PendingCountdown(
                Set<UUID> participantIds,
                String courseName,
                int ticksRemaining,
                Runnable onRoundLive
        ) {
            this.participantIds = participantIds;
            this.courseName = courseName;
            this.ticksRemaining = ticksRemaining;
            this.onRoundLive = onRoundLive;
        }
    }
}
