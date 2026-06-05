package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MenuCommands {
    private static final long MENU_CONFIRM_TIMEOUT_MS = 15_000L;
    private static final Map<UUID, PendingMenuConfirm> PENDING_MENU_CONFIRMS = new ConcurrentHashMap<>();

    private MenuCommands() {
    }

    private static boolean canUseAdminCommands(ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) {
            return true;
        }
        return !source.getServer().isDedicated();
    }

    public static int executeMenuDashboard(ServerCommandSource source, ActiveCourseManager courseManager, PlayerRoundSessionStorage playerRoundSessionStorage, TournamentRulesetManager rulesetManager) {
        boolean roundActive = courseManager.isRoundActive();
        boolean courseLoaded = courseManager.getPlacedCourseState().isPresent();
        boolean isAdmin = canUseAdminCommands(source);

        TournamentRulesetManager.Ruleset activeRuleset = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        String courseStatus = roundActive ? "Round active" : courseLoaded ? "Course loaded" : "No course";

        source.sendFeedback(() -> Text.literal("═══ MCDG ═══").formatted(Formatting.AQUA, Formatting.BOLD), false);
        source.sendFeedback(() -> Text.literal(courseStatus + "  " + activeRuleset.name().toLowerCase() + " / " + preset.name().toLowerCase()).formatted(Formatting.DARK_GRAY), false);

        ServerPlayerEntity player = source.getPlayer();
        if (!roundActive && player != null && playerRoundSessionStorage != null) {
            var saved = playerRoundSessionStorage.loadPlayer(source.getServer(), player.getUuid(), null).orElse(null);
            if (saved != null) {
                String banner = "► Saved round: " + saved.courseName() + ", hole " + saved.state().currentHole() + ", " + saved.state().totalStrokes() + " strokes";
                source.sendFeedback(() -> Text.literal(banner).formatted(Formatting.GREEN, Formatting.BOLD), false);
                source.sendFeedback(() -> menuButton("Resume Saved Round", "/mcdg resumesession", Formatting.GREEN, true), false);
            }
        }

        if (roundActive) {
            source.sendFeedback(() -> Text.literal("─ Round ─").formatted(Formatting.GREEN), false);
            source.sendFeedback(() -> menuButton("End Round", "/mcdg endround", Formatting.GOLD, true), false);
            source.sendFeedback(() -> menuButton("Go to Lie", "/mcdg gotolie", Formatting.AQUA, true), false);
            source.sendFeedback(() -> menuButton("Save & Leave Round", "/mcdg savesession", Formatting.GRAY, true), false);
            source.sendFeedback(() -> menuButton("Waypoints", "/mcdg waypoint tp", Formatting.LIGHT_PURPLE, true), false);
        } else {
            source.sendFeedback(() -> Text.literal("─ Play ─").formatted(Formatting.GREEN), false);
            source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
            source.sendFeedback(() -> menuButton("Join Round", "/mcdg joinround", Formatting.GREEN, true), false);
        }

        if (isAdmin && !roundActive) {
            source.sendFeedback(() -> Text.literal("─ Build ─").formatted(Formatting.YELLOW), false);
            source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse", Formatting.YELLOW, true), false);
            source.sendFeedback(() -> menuButton("Manual Build Course", "/mcdg buildcourse", Formatting.YELLOW, true), false);
        }

        if (isAdmin) {
            source.sendFeedback(() -> menuButton("Rules", "/mcdg menu rules", Formatting.BLUE, true), false);
            source.sendFeedback(() -> menuButton("Admin", "/mcdg menu admin", Formatting.RED, true), false);
        } else {
            source.sendFeedback(() -> menuButton("Rules", "/mcdg menu rules", Formatting.BLUE, true), false);
        }

        return 1;
    }

    public static void sendBackToMenu(ServerCommandSource source) {
        source.sendFeedback(() -> menuButton("[ ← MENU ]", "/mcdg", Formatting.AQUA, true), false);
    }

    public static int executeMenuPlayer(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Player Menu").formatted(Formatting.GREEN, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Round", "/mcdg menu round", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("Waypoints", "/mcdg waypoint tp", Formatting.LIGHT_PURPLE, true), false);
        source.sendFeedback(() -> menuButton("Rules", "/mcdg menu rules", Formatting.BLUE, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuAdmin(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Admin").formatted(Formatting.RED, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Clear Waypoints", "/mcdg waypoint clear", Formatting.DARK_GRAY, true), false);
        source.sendFeedback(() -> menuButton("Cleanup Course", "/mcdg menu confirm-request cleanupcourse", Formatting.DARK_RED, true), false);
        source.sendFeedback(() -> Text.literal("─ Crash Recovery ─").formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> menuButton("Crash Recovery Status", "/mcdg roundsession status", Formatting.DARK_GRAY, true), false);
        source.sendFeedback(() -> menuButton("Clear Crash Recovery", "/mcdg roundsession clear", Formatting.DARK_GRAY, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuRound(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Round").formatted(Formatting.GREEN, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse", Formatting.YELLOW, true), false);
        source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
        source.sendFeedback(() -> menuButton("Join Round", "/mcdg joinround", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("End Round", "/mcdg endround", Formatting.GOLD, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuCourses(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Courses").formatted(Formatting.GOLD, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse", Formatting.YELLOW, true), false);
        source.sendFeedback(() -> menuButton("Manual Build Course", "/mcdg buildcourse", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuWaypoints(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Waypoints").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("List & Teleport", "/mcdg waypoint tp", Formatting.LIGHT_PURPLE, true), false);
        source.sendFeedback(() -> menuButton("Clear Stale Waypoints", "/mcdg waypoint clear", Formatting.DARK_GRAY, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuRules(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Rules").formatted(Formatting.BLUE, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Show Ruleset", "/mcdg ruleset", Formatting.BLUE, true), false);
        source.sendFeedback(() -> menuButton("Set Casual", "/mcdg ruleset casual", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("Set Strict", "/mcdg ruleset strict", Formatting.GOLD, true), false);
        source.sendFeedback(() -> menuButton("Strict Surface Preset", "/mcdg ruleset surface", Formatting.AQUA, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuSession(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Crash Recovery (Admin)").formatted(Formatting.DARK_GRAY, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Crash Recovery Status", "/mcdg roundsession status", Formatting.DARK_GRAY, true), false);
        source.sendFeedback(() -> menuButton("Clear Crash Recovery", "/mcdg roundsession clear", Formatting.DARK_GRAY, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuConfirmRequest(ServerCommandSource source, String action) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Menu confirmations require a player source."));
            return 0;
        }

        String command;
        String label;
        if ("cleanupcourse".equalsIgnoreCase(action)) {
            command = "/mcdg cleanupcourse";
            label = "Cleanup active course";
        } else if ("prunecourses".equalsIgnoreCase(action)) {
            command = "/mcdg prunecourses";
            label = "Prune reusable catalog to keep 6";
        } else {
            source.sendError(Text.literal("Unknown confirm action: " + action));
            return 0;
        }

        long token = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        long expiresAtMs = System.currentTimeMillis() + MENU_CONFIRM_TIMEOUT_MS;
        PENDING_MENU_CONFIRMS.put(player.getUuid(), new PendingMenuConfirm(token, expiresAtMs, command, label));

        String run = "/mcdg menu confirm-run " + token;
        source.sendFeedback(() -> Text.literal("Confirm action (expires in 15s): " + label).formatted(Formatting.RED), false);
        source.sendFeedback(() -> menuButton("CONFIRM", run, Formatting.DARK_RED, true), false);
        source.sendFeedback(() -> menuButton("CANCEL", "/mcdg menu confirm-cancel", Formatting.GRAY, true), false);
        source.sendFeedback(() -> menuButton("BACK", "/mcdg menu", Formatting.GRAY, true), false);
        return 1;
    }

    public static int executeMenuConfirmRun(ServerCommandSource source, long token) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Menu confirmations require a player source."));
            source.sendFeedback(() -> menuButton("BACK", "/mcdg menu", Formatting.GRAY, true), false);
            return 0;
        }

        PendingMenuConfirm pending = PENDING_MENU_CONFIRMS.get(player.getUuid());
        if (pending == null || pending.token() != token) {
            source.sendError(Text.literal("No matching confirmation found."));
            source.sendFeedback(() -> menuButton("BACK", "/mcdg menu", Formatting.GRAY, true), false);
            return 0;
        }
        if (System.currentTimeMillis() > pending.expiresAtMs()) {
            PENDING_MENU_CONFIRMS.remove(player.getUuid());
            source.sendError(Text.literal("Confirmation expired. Run the action again."));
            source.sendFeedback(() -> menuButton("BACK", "/mcdg menu", Formatting.GRAY, true), false);
            return 0;
        }

        PENDING_MENU_CONFIRMS.remove(player.getUuid());
        source.getServer().getCommandManager().executeWithPrefix(source, pending.command());
        return 1;
    }

    public static int executeMenuConfirmCancel(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            PENDING_MENU_CONFIRMS.remove(player.getUuid());
        }
        source.sendFeedback(() -> Text.literal("Pending menu confirmation canceled."), false);
        source.sendFeedback(() -> menuButton("BACK", "/mcdg menu", Formatting.GRAY, true), false);
        return 1;
    }

    private static Text menuButton(String label, String command, Formatting color, boolean runNow) {
        ClickEvent.Action action = runNow ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        return Text.literal("[" + label + "]").styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal((runNow ? "Run: " : "Fill chat: ") + command)))
        );
    }

    public record PendingMenuConfirm(long token, long expiresAtMs, String command, String label) {
    }
}
