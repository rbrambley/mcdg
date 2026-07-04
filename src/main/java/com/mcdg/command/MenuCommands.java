package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseCatalog;
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

public final class MenuCommands {

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
        boolean isAdmin = canUseAdminCommands(source);

        TournamentRulesetManager.Ruleset activeRuleset = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        String courseStatus = roundActive ? "Round active" : courseManager.getPlacedCourseState().isPresent() ? "Course loaded" : "No course";

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

        source.sendFeedback(() -> menuButton("Rules", "/mcdg menu rules", Formatting.BLUE, true), false);

        return 1;
    }

    public static void sendBackToMenu(ServerCommandSource source) {
        source.sendFeedback(() -> menuButton("[ ← MENU ]", "/mcdg", Formatting.AQUA, true), false);
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

        List<MenuScreenSync.ChallengeCourseEntry> challengeCourses = new ArrayList<>();
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isPresent()) {
            for (ChallengeCourseCatalog.CatalogEntry entry : catalog.get().getAllCourses()) {
                int bestScore = catalog.get().getBestScore(entry.courseId()).orElse(0);
                int completions = catalog.get().getPlayersWhoCompleted(entry.courseId()).size();
                challengeCourses.add(new MenuScreenSync.ChallengeCourseEntry(
                        entry.courseId().toString(),
                        entry.name(),
                        entry.type().getDisplayName(),
                        entry.isPlaced(),
                        bestScore,
                        completions
                ));
            }
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
                caveMode,
                challengeCourses
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
}
