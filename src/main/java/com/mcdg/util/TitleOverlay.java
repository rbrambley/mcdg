package com.mcdg.util;

import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TitleOverlay {
    private TitleOverlay() {
    }

    public static void send(ServerPlayerEntity player, int fadeIn, int stay, int fadeOut, Text title, Text subtitle) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        if (title != null) {
            player.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
        if (subtitle != null) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        }
    }

    public static void clear(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
    }
}
