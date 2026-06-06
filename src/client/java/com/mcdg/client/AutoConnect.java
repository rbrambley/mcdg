package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Handles optional automatic server reconnect for dev/testing.
 * Activated via the {@code MCDG_AUTOCONNECT_SERVER} environment variable.
 */
public final class AutoConnect {
    private static final String AUTOCONNECT_SERVER_ENV = "MCDG_AUTOCONNECT_SERVER";
    private static final long AUTOCONNECT_RETRY_DELAY_MS = 3000L;

    private static long nextAttemptAt = 0L;
    private static boolean satisfied = false;
    private static String targetServer = readTargetServer();

    private AutoConnect() {
    }

    public static void tick(MinecraftClient client) {
        if (satisfied) {
            return;
        }

        if (targetServer == null || client == null) {
            return;
        }

        if (client.player != null) {
            satisfied = true;
            return;
        }

        Screen currentScreen = client.currentScreen;
        if (!(currentScreen instanceof TitleScreen) && !(currentScreen instanceof DisconnectedScreen)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAttemptAt) {
            return;
        }

        nextAttemptAt = now + AUTOCONNECT_RETRY_DELAY_MS;
        Screen parent = client.currentScreen == null ? new TitleScreen(false) : client.currentScreen;
        ServerAddress address = ServerAddress.parse(targetServer);
        ServerInfo serverInfo = new ServerInfo("MCDG Dev Server", address.toString(), ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, address, serverInfo, false, null);
    }

    public static void reset() {
        satisfied = false;
        nextAttemptAt = 0L;
        targetServer = readTargetServer();
    }

    private static String readTargetServer() {
        String value = System.getenv(AUTOCONNECT_SERVER_ENV);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
