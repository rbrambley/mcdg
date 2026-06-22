package com.mcdg.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Centralized permission checks for MCDG admin commands.
 */
public final class CommandPermission {
    private static final String ADVANCED_COMMANDS_ENV = "MCDG_SHOW_ADVANCED_COMMANDS";
    private static final String ADVANCED_COMMANDS_PROPERTY = "mcdg.showAdvancedCommands";
    private static final boolean SHOW_ADVANCED_COMMANDS = readAdvancedCommandVisibility();

    private CommandPermission() {}

    private static boolean readAdvancedCommandVisibility() {
        String value = System.getProperty(ADVANCED_COMMANDS_PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ADVANCED_COMMANDS_ENV);
        }
        return value != null && value.equalsIgnoreCase("true");
    }

    /**
     * Returns true if the source is allowed to use admin commands.
     * OP level 2 or local/integrated server (non-dedicated).
     */
    public static boolean canUseAdminCommands(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) {
            return true;
        }
        // Keep local/integrated dev sessions usable even when OP metadata is not applied.
        return !source.getServer().isDedicated();
    }

    /**
     * Returns true if the source is allowed to use advanced (debug) commands.
     * Requires admin command access plus the advanced-commands feature flag.
     */
    public static boolean canUseAdvancedCommands(ServerCommandSource source) {
        return canUseAdminCommands(source) && SHOW_ADVANCED_COMMANDS;
    }

    /**
     * Sends debug permission info back to the source.
     * @return 1 for success
     */
    public static int sendDebugPermissions(ServerCommandSource source) {
        boolean hasPermissionLevelTwo = source.hasPermissionLevel(2);
        boolean dedicated = source.getServer().isDedicated();
        boolean allowedByGate = canUseAdminCommands(source);

        String sourceType = "non-entity";
        String sourceIdentity = source.getName();
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            sourceType = "player";
            sourceIdentity = player.getGameProfile().getName() + " (" + player.getUuid() + ")";
        } else if (source.getEntity() != null) {
            sourceType = "entity";
            sourceIdentity = source.getEntity().getName().getString();
        }

        final String finalSourceType = sourceType;
        final String finalSourceIdentity = sourceIdentity;

        source.sendFeedback(() -> net.minecraft.text.Text.literal(
                "mcdg debug perms -> hasPermissionLevel(2)=" + hasPermissionLevelTwo
                        + ", dedicated=" + dedicated
                        + ", allowedByGate=" + allowedByGate
                        + ", sourceType=" + finalSourceType
                        + ", sourceIdentity=" + finalSourceIdentity
        ), false);
        return 1;
    }
}
