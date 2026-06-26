package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseCatalog;
import com.mcdg.game.ChallengeCourseBuilder;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.rules.TournamentRulesetManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
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
        } else {
            source.sendFeedback(() -> Text.literal("─ Play ─").formatted(Formatting.GREEN), false);
            source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
            source.sendFeedback(() -> menuButton("Challenge Courses", "/mcdg menu challenge", Formatting.LIGHT_PURPLE, true), false);
            source.sendFeedback(() -> menuButton("Join Round", "/mcdg joinround", Formatting.GREEN, true), false);
        }

        if (isAdmin && !roundActive) {
            source.sendFeedback(() -> Text.literal("─ Build ─").formatted(Formatting.YELLOW), false);
            source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse ", Formatting.YELLOW, false), false);
            source.sendFeedback(() -> menuButton("Manual Build Course", "/mcdg buildcourse", Formatting.YELLOW, true), false);
        }

        if (courseLoaded) {
            source.sendFeedback(() -> menuButton("Leaderboard", "/mcdg leaderboard", Formatting.GOLD, true), false);
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
        source.sendFeedback(() -> menuButton("Rules", "/mcdg menu rules", Formatting.BLUE, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuAdmin(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Admin").formatted(Formatting.RED, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Remove Resort Courses", "/mcdg removesurroundcourses", Formatting.DARK_RED, true), false);
        source.sendFeedback(() -> Text.literal("─ Stuck Round ─").formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> menuButton("Stuck Round Status", "/mcdg roundsession status", Formatting.DARK_GRAY, true), false);
        source.sendFeedback(() -> menuButton("Force Clear Stuck Round", "/mcdg roundsession clear", Formatting.DARK_RED, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuRound(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Round").formatted(Formatting.GREEN, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse ", Formatting.YELLOW, false), false);
        source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
        source.sendFeedback(() -> menuButton("Join Round", "/mcdg joinround", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("End Round", "/mcdg endround", Formatting.GOLD, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuCourses(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Courses").formatted(Formatting.GOLD, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Auto Build Course", "/mcdg autocourse ", Formatting.YELLOW, false), false);
        source.sendFeedback(() -> menuButton("Manual Build Course", "/mcdg buildcourse", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("List Courses", "/mcdg listcourses", Formatting.AQUA, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuRules(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        TournamentRulesetManager.Ruleset ruleset = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        source.sendFeedback(() -> Text.literal("Rules").formatted(Formatting.BLUE, Formatting.BOLD), false);
        source.sendFeedback(() -> Text.literal("Current: " + ruleset.name().toLowerCase() + " / " + preset.name().toLowerCase()).formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> menuButton("Casual", "/mcdg ruleset casual", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("Standard", "/mcdg ruleset standard", Formatting.YELLOW, true), false);
        source.sendFeedback(() -> menuButton("Strict", "/mcdg ruleset strict", Formatting.RED, true), false);
        source.sendFeedback(() -> Text.literal("─ Strict Surface ─").formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> menuButton("Fast (forgiving)", "/mcdg ruleset surface fast", Formatting.GREEN, true), false);
        source.sendFeedback(() -> menuButton("Balanced (default)", "/mcdg ruleset surface balanced", Formatting.YELLOW, true), false);
        source.sendFeedback(() -> menuButton("Tournament (hardest)", "/mcdg ruleset surface tournament", Formatting.RED, true), false);
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuChallenge(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Challenge Courses").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No challenge courses discovered yet.").formatted(Formatting.GRAY), false);
            source.sendFeedback(() -> Text.literal("Explore the world to find lost course entrances!").formatted(Formatting.DARK_GRAY), false);
        } else {
            var courses = catalog.get().getAllCourses();
            source.sendFeedback(() -> Text.literal("Discovered Courses: " + courses.size()).formatted(Formatting.LIGHT_PURPLE), false);
            
            for (var entry : courses) {
                // Course name and type
                source.sendFeedback(() -> Text.literal("• " + entry.name() + " [" + entry.type().getDisplayName() + "]")
                    .formatted(Formatting.GOLD), false);
                
                // Discovery date
                String discoveryDate = entry.discoveredAt().toString().substring(0, 10);
                source.sendFeedback(() -> Text.literal("  Discovered: " + discoveryDate)
                    .formatted(Formatting.DARK_GRAY), false);
                
                // Best score
                catalog.get().getBestScore(entry.courseId()).ifPresent(bestScore -> {
                    source.sendFeedback(() -> Text.literal("  Best Score: " + bestScore)
                        .formatted(Formatting.GREEN), false);
                });
                
                // Players who completed
                var completions = catalog.get().getPlayersWhoCompleted(entry.courseId());
                if (!completions.isEmpty()) {
                    source.sendFeedback(() -> Text.literal("  Completed by: " + completions.size() + " player(s)")
                        .formatted(Formatting.AQUA), false);
                }
                
                // Start button
                source.sendFeedback(() -> menuButton("  Start " + entry.name(), "/mcdg startchallenge " + entry.courseId(), Formatting.GREEN, true), false);
            }
        }
        
        sendBackToMenu(source);
        return 1;
    }

    public static int executeMenuSession(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        source.sendFeedback(() -> Text.literal("Stuck Round (Admin)").formatted(Formatting.DARK_GRAY, Formatting.BOLD), false);
        source.sendFeedback(() -> menuButton("Stuck Round Status", "/mcdg roundsession status", Formatting.DARK_GRAY, true), false);
        source.sendFeedback(() -> menuButton("Force Clear Stuck Round", "/mcdg roundsession clear", Formatting.DARK_RED, true), false);
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
        if ("prunecourses".equalsIgnoreCase(action)) {
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

    public static int sendMenuScreen(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            TournamentRulesetManager rulesetManager,
            PracticeCourseStorage practiceCourseStorage
    ) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return MenuCommands.executeMenuDashboard(source, courseManager, playerRoundSessionStorage, rulesetManager);
        }

        boolean roundActive = courseManager.isRoundActive();
        boolean courseLoaded = courseManager.getPlacedCourseState().isPresent();
        com.mcdg.data.Course activeCourse = courseManager.getActiveCourse().orElse(null);
        String courseName = activeCourse != null && activeCourse.name() != null ? activeCourse.name() : "";
        int activeCatalogIndex = courseManager.getActiveCourseCatalogIndex().orElse(-1);
        int activeHoleCount = activeCourse != null ? activeCourse.holes().size() : 0;
        boolean isAdmin = canUseAdminCommands(source);

        boolean hasSavedSession = false;
        String savedCourseName = "";
        int savedHole = 0;
        int savedStrokes = 0;
        if (!roundActive && playerRoundSessionStorage != null) {
            var saved = playerRoundSessionStorage.loadPlayer(source.getServer(), player.getUuid(), null).orElse(null);
            if (saved != null) {
                hasSavedSession = true;
                savedCourseName = saved.courseName() != null ? saved.courseName() : "";
                savedHole = saved.state().currentHole();
                savedStrokes = saved.state().totalStrokes();
            }
        }

        TournamentRulesetManager.Ruleset ruleset = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();

        // Auto-save current course if placed but not in catalog
        if (courseLoaded && activeCatalogIndex < 0 && activeCourse != null) {
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (placed != null) {
                int savedIndex = practiceCourseStorage.saveReusable(source.getServer(), activeCourse, placed, "autosave/menu", false);
                if (savedIndex > 0) {
                    courseManager.setActiveCourseCatalogIndex(savedIndex);
                    activeCatalogIndex = savedIndex;
                }
            }
        }

        List<MenuScreenSync.CourseEntry> courses = new ArrayList<>();
        for (PracticeCourseStorage.ReusableCourseEntry entry : practiceCourseStorage.listReusable(source.getServer())) {
            courses.add(new MenuScreenSync.CourseEntry(entry.index(), entry.name(), entry.holeCount(), entry.sourceTag()));
        }

        if (hasSavedSession) {
            boolean courseStillExists = false;
            for (MenuScreenSync.CourseEntry entry : courses) {
                if (savedCourseName.equalsIgnoreCase(entry.name())) {
                    courseStillExists = true;
                    break;
                }
            }
            if (!courseStillExists) {
                hasSavedSession = false;
                savedCourseName = "";
                savedHole = 0;
                savedStrokes = 0;
            }
        }
        boolean caveMode = player != null && player.getBlockPos().getY() < 40;
        MenuScreenSync.Payload payload = new MenuScreenSync.Payload(
                roundActive, courseLoaded, courseName,
                activeCatalogIndex, activeHoleCount,
                hasSavedSession, savedCourseName, savedHole, savedStrokes,
                isAdmin,
                ruleset.name().toLowerCase(),
                preset.name().toLowerCase(),
                courses,
                caveMode
        );
        ServerPlayNetworking.send(player, payload);
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
