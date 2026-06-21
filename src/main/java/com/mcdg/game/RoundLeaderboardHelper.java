package com.mcdg.game;

import com.mcdg.ui.HudStateFormatter;
import com.mcdg.net.RoundCompleteCinematicSync;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class RoundLeaderboardHelper {
    private RoundLeaderboardHelper() {
    }

    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();

    static void broadcastRoundLeaderboard(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            RoundStateManager roundStateManager,
            int totalPar
    ) {
        Map<UUID, Integer> completed = roundStateManager.snapshotCompletedRounds();
        if (completed.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(completed.entrySet());
        ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == worldKey)
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        Text header = HUD_STATE_FORMATTER.formatRoundSummaryHeader(totalPar, ranked.size());
        for (ServerPlayerEntity viewer : viewers) {
            viewer.sendMessage(header, false);
        }

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            String name = resolveName(server, entry.getKey());
            Text line = HUD_STATE_FORMATTER.formatRoundSummaryEntry(rank, name, entry.getValue(), totalPar);
            for (ServerPlayerEntity viewer : viewers) {
                viewer.sendMessage(line, false);
            }
            rank++;
        }
    }

    static void sendRoundCompleteCinematic(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            RoundStateManager roundStateManager,
            int totalPar
    ) {
        Map<UUID, Integer> completed = roundStateManager.snapshotCompletedRounds();
        if (completed.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(completed.entrySet());
        ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

        String firstName = rankedName(server, ranked, 0);
        int firstScore = rankedScore(ranked, 0);
        String secondName = rankedName(server, ranked, 1);
        int secondScore = rankedScore(ranked, 1);
        String thirdName = rankedName(server, ranked, 2);
        int thirdScore = rankedScore(ranked, 2);

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == worldKey)
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        for (ServerPlayerEntity viewer : viewers) {
            int localRank = -1;
            int localScore = completed.getOrDefault(viewer.getUuid(), 0);
            for (int i = 0; i < ranked.size(); i++) {
                if (ranked.get(i).getKey().equals(viewer.getUuid())) {
                    localRank = i + 1;
                    break;
                }
            }

            ServerPlayNetworking.send(
                    viewer,
                    RoundCompleteCinematicSync.Payload.active(
                            totalPar,
                            ranked.size(),
                            firstName,
                            firstScore,
                            secondName,
                            secondScore,
                            thirdName,
                            thirdScore,
                            localRank,
                            localScore
                    )
            );
        }
    }

    static String rankedName(MinecraftServer server, List<Map.Entry<UUID, Integer>> ranked, int index) {
        if (index < 0 || index >= ranked.size()) {
            return "-";
        }
        return resolveName(server, ranked.get(index).getKey());
    }

    private static String resolveName(MinecraftServer server, UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        return BotSimulator.getBotProfile(playerId)
                .map(BotSimulator.BotProfile::name)
                .orElseGet(() -> playerId.toString().substring(0, 8));
    }

    static int rankedScore(List<Map.Entry<UUID, Integer>> ranked, int index) {
        if (index < 0 || index >= ranked.size()) {
            return 0;
        }
        return ranked.get(index).getValue();
    }

}
