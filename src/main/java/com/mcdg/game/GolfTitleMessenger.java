package com.mcdg.game;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Sends golf-themed title overlays to players.
 */
public final class GolfTitleMessenger {
    private GolfTitleMessenger() {}

    static void sendClankTitle(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 25, 8));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("CLANK!").formatted(Formatting.GRAY, Formatting.ITALIC)));
    }

    static void sendStrictPenaltyTitle(ServerPlayerEntity player, StrictPenaltyType landingPenalty, int penaltyStrokes) {
        String titleText = landingPenalty == StrictPenaltyType.OB ? "OB +" + penaltyStrokes : "Hazard +" + penaltyStrokes;
        String subtitleText = landingPenalty == StrictPenaltyType.OB ? "Returned to lie" : "Penalty applied";

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(Formatting.RED, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(Formatting.WHITE)));
    }

    static void sendHazardPowerPenaltyTitle(ServerPlayerEntity player, float multiplier, String penaltyReason) {
        int percent = Math.round(multiplier * 100.0f);
        String titleText = penaltyReason != null && !penaltyReason.isBlank() ? penaltyReason : "Hazard";

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 12));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(Formatting.RED, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.translatable("mcdg.title.hazard_power_penalty.subtitle", percent).formatted(Formatting.GOLD, Formatting.BOLD)
        ));
    }

    static void sendHoleFinishTitle(ServerPlayerEntity player, int holeScore, int holePar) {
        int holeDelta = holeScore - holePar;
        String resultName = golfResultName(holeScore, holeDelta);
        String deltaText = holeDelta == 0 ? "E" : (holeDelta > 0 ? "+" + holeDelta : Integer.toString(holeDelta));
        Formatting resultColor = holeDelta <= 0
                ? Formatting.GREEN
                : Formatting.RED;

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(resultName).formatted(resultColor, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(deltaText).formatted(resultColor, Formatting.BOLD)));
    }

    static String golfResultName(int holeScore, int holeDelta) {
        if (holeScore == 1) {
            return "Ace";
        }
        if (holeDelta == -3) {
            return "Albatross";
        }
        if (holeDelta <= -4) {
            return "Three or Better";
        }
        if (holeDelta == -2) {
            return "Eagle";
        }
        if (holeDelta == -1) {
            return "Birdie";
        }
        if (holeDelta == 0) {
            return "Par";
        }
        if (holeDelta == 1) {
            return "Bogey";
        }
        if (holeDelta == 2) {
            return "Double Bogey";
        }
        if (holeDelta == 3) {
            return "Triple Bogey";
        }
        return "+" + holeDelta + " Bogey";
    }
}
