package com.mcdg.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class RoundPresentationService {
    private static final int COUNTDOWN_SECONDS = 5;
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
                holeCount,
                totalPar,
                COUNTDOWN_SECONDS * TICKS_PER_SECOND,
                onRoundLive
        );
        pendingCountdowns.add(pending);

        String layoutLine = "Layout Card | " + courseName + " | Holes " + holeCount + " | Par " + totalPar;
        String countdownLine = "Round starts in " + COUNTDOWN_SECONDS + "...";
        forEachParticipant(server, idSet, player -> {
            player.sendMessage(Text.literal(layoutLine), false);
            player.sendMessage(Text.literal(countdownLine), true);
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
                forEachParticipant(server, pending.participantIds, player ->
                        player.sendMessage(Text.literal("Round starts in " + seconds + "..."), true));
            }

            pending.ticksRemaining--;
            if (pending.ticksRemaining >= 0) {
                continue;
            }

            forEachParticipant(server, pending.participantIds, player -> {
                player.sendMessage(Text.literal("Round Live!"), true);
                player.sendMessage(Text.literal("Round Live | " + pending.courseName + " | Good luck."), false);
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
        private final int holeCount;
        private final int totalPar;
        private int ticksRemaining;
        private final Runnable onRoundLive;

        private PendingCountdown(
                Set<UUID> participantIds,
                String courseName,
                int holeCount,
                int totalPar,
                int ticksRemaining,
                Runnable onRoundLive
        ) {
            this.participantIds = participantIds;
            this.courseName = courseName;
            this.holeCount = holeCount;
            this.totalPar = totalPar;
            this.ticksRemaining = ticksRemaining;
            this.onRoundLive = onRoundLive;
        }
    }
}
