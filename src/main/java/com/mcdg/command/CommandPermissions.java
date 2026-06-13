package com.mcdg.command;

import net.minecraft.server.command.ServerCommandSource;

public final class CommandPermissions {
    private static final String ADVANCED_COMMANDS_ENV = "MCDG_SHOW_ADVANCED_COMMANDS";
    private static final String ADVANCED_COMMANDS_PROPERTY = "mcdg.showAdvancedCommands";
    public static final boolean SHOW_ADVANCED_COMMANDS = readAdvancedCommandVisibility();

    private CommandPermissions() {
    }

    private static boolean readAdvancedCommandVisibility() {
        String value = System.getProperty(ADVANCED_COMMANDS_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ADVANCED_COMMANDS_ENV);
        }
        return value != null && value.equalsIgnoreCase("true");
    }

    public static boolean canUseAdminCommands(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) {
            return true;
        }
        return !source.getServer().isDedicated();
    }

    public static boolean canUseAdvancedCommands(ServerCommandSource source) {
        return canUseAdminCommands(source) && SHOW_ADVANCED_COMMANDS;
    }
}
