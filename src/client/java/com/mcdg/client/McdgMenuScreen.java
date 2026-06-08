package com.mcdg.client;

import com.mcdg.net.LeaderboardRequest;
import com.mcdg.net.MenuScreenSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class McdgMenuScreen extends Screen {

    private static final int BG_COLOR          = 0xF0111820;
    private static final int HEADER_COLOR      = 0xFF1B2D42;
    private static final int ACCENT_COLOR      = 0xFF2A4A6A;
    private static final int NAV_ACTIVE_COLOR  = 0xFF1E3D5C;
    private static final int BORDER_COLOR      = 0xFF3A5A7A;
    private static final int BTN_TINT_GREEN    = 0x2257D163;
    private static final int BTN_TINT_GOLD     = 0x22FFCC33;
    private static final int BTN_TINT_RED      = 0x22FF5555;
    private static final int BTN_TINT_MUTED    = 0x11445566;
    private static final int BTN_TINT_NONE     = 0x00000000;
    private static final int TEXT_TITLE        = 0xFFD4E8FF;
    private static final int TEXT_MUTED        = 0xFF8AAABB;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GREEN        = 0xFF57D163;
    private static final int TEXT_GOLD         = 0xFFFFCC33;
    private static final int TEXT_RED          = 0xFFFF5555;
    private static final int CONFIRM_BG        = 0xF0200B0B;
    private static final int CONFIRM_BORDER    = 0xFFAA3333;

    private static final int PANEL_W           = 320;
    private static final int PANEL_H           = 260;
    private static final int NAV_W             = 80;
    private static final int CONTENT_X_OFFSET  = NAV_W + 8;
    private static final int BTN_H             = 20;
    private static final int BTN_GAP           = 4;
    private static final int CONTENT_MAX_H     = PANEL_H - 28 - 20 - 16;
    private static final int ROWS_VISIBLE      = CONTENT_MAX_H / (BTN_H + BTN_GAP);

    private enum Page { DASHBOARD, COURSES, BUILD, RULES, ADMIN }

    private final MenuScreenSync.Payload state;
    private Page currentPage = Page.DASHBOARD;
    private int playScrollOffset = 0;

    private boolean confirmPending = false;
    private String confirmLabel = "";
    private String confirmCommand = "";

    private final List<ButtonWidget> navButtons = new ArrayList<>();
    private final List<ButtonWidget> contentButtons = new ArrayList<>();
    private final List<int[]> buttonTints = new ArrayList<>();

    public McdgMenuScreen(MenuScreenSync.Payload state) {
        super(Text.literal("MCDG"));
        this.state = state;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        navButtons.clear();
        contentButtons.clear();
        buttonTints.clear();

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        buildNavButtons(panelX, panelY);

        if (confirmPending) {
            buildConfirmDialog(panelX, panelY);
        } else {
            buildContentButtons(panelX, panelY);
        }
    }

    private void buildNavButtons(int panelX, int panelY) {
        int navX = panelX + 4;
        int navStartY = panelY + 36;
        int btnW = NAV_W - 8;
        int slot = 0;

        addNavButton("Dashboard", Page.DASHBOARD, navX, navStartY + (slot++ * 26), btnW);
        addNavButton("Courses",   Page.COURSES,   navX, navStartY + (slot++ * 26), btnW);
        if (state.isAdmin()) {
            addNavButton("Build", Page.BUILD, navX, navStartY + (slot++ * 26), btnW);
        }
        addNavButton("Rules",     Page.RULES,     navX, navStartY + (slot++ * 26), btnW);
        if (state.isAdmin()) {
            addNavButton("Admin", Page.ADMIN, navX, navStartY + (slot * 26), btnW);
        }
    }

    private void addNavButton(String label, Page page, int x, int y, int w) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = page;
            confirmPending = false;
            playScrollOffset = 0;
            rebuild();
        }).dimensions(x, y, w, BTN_H).build();
        navButtons.add(btn);
        addDrawableChild(btn);
    }

    private void buildContentButtons(int panelX, int panelY) {
        int cx = panelX + CONTENT_X_OFFSET;
        int cy = panelY + 44;
        int bw = PANEL_W - CONTENT_X_OFFSET - 8;

        switch (currentPage) {
            case DASHBOARD -> buildDashboardPage(cx, cy, bw);
            case COURSES   -> buildCoursesPage(cx, cy, bw, panelX, panelY);
            case BUILD     -> buildBuildPage(cx, cy, bw);
            case RULES     -> buildRulesPage(cx, cy, bw);
            case ADMIN     -> buildAdminPage(cx, cy, bw);
        }
    }

    private void buildDashboardPage(int cx, int cy, int bw) {
        int y = cy;

        if (state.roundActive()) {
            // ── Active round ──
            if (!state.courseName().isBlank()) {
                addBtn("⛳ Go to Lie  [" + state.courseName() + "]", "/mcdg gotolie", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
            } else {
                addBtn("⛳ Go to Lie", "/mcdg gotolie", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
            }
            addBtn("Waypoints",          "/mcdg waypoint tp", cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);  y += BTN_H + BTN_GAP;
            addBtn("Join Round",         "/mcdg joinround",   cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);  y += BTN_H + BTN_GAP;
            addBtn("End Round",          "/mcdg endround",    cx, y, bw, TEXT_GOLD,  BTN_TINT_GOLD);  y += BTN_H + BTN_GAP;
            addBtn("Save & Leave Round", "/mcdg savesession", cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED);

        } else if (state.courseLoaded()) {
            // ── Course placed, no active round ──
            if (state.hasSavedSession()) {
                addBtn("▶ Resume: " + state.savedCourseName() + "  H" + state.savedHole() + "  (" + state.savedStrokes() + " strokes)",
                        "/mcdg resumesession", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
            }
            addPageSwitchBtn("Manage Courses →", Page.COURSES, cx, y, bw, TEXT_MUTED, BTN_TINT_NONE);

        } else {
            // ── Nothing loaded ──
            if (state.hasSavedSession()) {
                addBtn("▶ Resume: " + state.savedCourseName() + "  H" + state.savedHole() + "  (" + state.savedStrokes() + " strokes)",
                        "/mcdg resumesession", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN);
                y += BTN_H + BTN_GAP;
            }
            if (state.isAdmin()) {
                addBtn("Build New Course", "/mcdg autocourse", cx, y, bw, TEXT_GOLD, BTN_TINT_GOLD); y += BTN_H + BTN_GAP;
            }
            addPageSwitchBtn("Browse Saved Courses →", Page.COURSES, cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);
        }
    }

    private void addPageSwitchBtn(String label, Page page, int x, int y, int w, int textColor, int tint) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = page;
            confirmPending = false;
            playScrollOffset = 0;
            rebuild();
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, tint});
        addDrawableChild(btn);
    }

    private void buildCoursesPage(int cx, int cy, int bw, int panelX, int panelY) {
        List<MenuScreenSync.CourseEntry> courses = state.courses();

        if (courses.isEmpty()) {
            return;
        }

        int tpW = 28;
        int playW = 42;
        int removeW = 16;
        int gap = 3;
        int scoresW = 20;
        int totalBtnsW = tpW + scoresW + playW + removeW + (gap * 3);
        int visibleRows = Math.min(ROWS_VISIBLE, courses.size());
        int maxOffset = Math.max(0, courses.size() - visibleRows);
        playScrollOffset = Math.max(0, Math.min(playScrollOffset, maxOffset));

        int y = cy;
        for (int i = playScrollOffset; i < playScrollOffset + visibleRows && i < courses.size(); i++) {
            MenuScreenSync.CourseEntry entry = courses.get(i);
            boolean isActive = entry.index() == state.activeCatalogIndex();
            String prefix = isActive ? "▶ " : "";
            String label = prefix + entry.name() + "  (" + entry.holeCount() + "H)";
            int idx = entry.index();
            int textCol = isActive ? TEXT_GREEN : TEXT_WHITE;
            int tintCol = isActive ? BTN_TINT_GREEN : BTN_TINT_NONE;
            int labelW = bw - totalBtnsW;
            int x = cx;
            addBtn(label, "/mcdg playcourse " + idx, x, y, labelW, textCol, tintCol);
            x += labelW + gap;
            addBtn("[TP]", "/mcdg gotocoursebyindex " + idx, x, y, tpW, TEXT_WHITE, BTN_TINT_NONE);
            x += tpW + gap;
            String scoresCourseName = entry.name();
            ButtonWidget scoresBtn = ButtonWidget.builder(Text.literal("[S]"), b -> {
                close();
                ClientPlayNetworking.send(new LeaderboardRequest.Payload(scoresCourseName));
            }).dimensions(x, y, scoresW, BTN_H).build();
            contentButtons.add(scoresBtn);
            buttonTints.add(new int[]{x, y, scoresW, BTN_H, BTN_TINT_NONE});
            addDrawableChild(scoresBtn);
            x += scoresW + gap;
            if (state.roundActive() && isActive) {
                addBtn("[PLAY]", null, x, y, playW, TEXT_MUTED, BTN_TINT_MUTED);
            } else {
                addBtn("[PLAY]", "/mcdg playcourse " + idx, x, y, playW, TEXT_GOLD, BTN_TINT_GOLD);
            }
            x += playW + gap;
            addConfirmBtn("[X]", "/mcdg cleanupcoursebyindex " + idx, x, y, removeW);
            y += BTN_H + BTN_GAP;
        }

        if (courses.size() > visibleRows) {
            int scrollBarX = panelX + PANEL_W - 12;
            int scrollAreaY = panelY + 44;
            int scrollAreaH = visibleRows * (BTN_H + BTN_GAP);
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> {
                playScrollOffset = Math.max(0, playScrollOffset - 1);
                rebuild();
            }).dimensions(scrollBarX, scrollAreaY, 10, 12).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> {
                playScrollOffset = Math.min(maxOffset, playScrollOffset + 1);
                rebuild();
            }).dimensions(scrollBarX, scrollAreaY + scrollAreaH - 12, 10, 12).build());
        }
    }

    private void buildBuildPage(int cx, int cy, int bw) {
        int y = cy;
        addBtn("Auto Build Course",   "/mcdg autocourse",  cx, y, bw, TEXT_GOLD,  BTN_TINT_GOLD);  y += BTN_H + BTN_GAP;
        addBtn("Manual Build Course", "/mcdg buildcourse", cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);
    }

    private void buildRulesPage(int cx, int cy, int bw) {
        int y = cy;
        addBtn("Set Casual",              "/mcdg ruleset casual",             cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
        addBtn("Set Strict",              "/mcdg ruleset strict",             cx, y, bw, TEXT_GOLD,  BTN_TINT_GOLD);  y += BTN_H + BTN_GAP;
        if (state.isAdmin()) {
            addBtn("Surface: Fast",       "/mcdg ruleset surface fast",       cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + BTN_GAP;
            addBtn("Surface: Balanced",   "/mcdg ruleset surface balanced",   cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + BTN_GAP;
            addBtn("Surface: Tournament", "/mcdg ruleset surface tournament", cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED);
        }
    }

    private void buildAdminPage(int cx, int cy, int bw) {
        int y = cy;
        addBtn("Clear Waypoints",       "/mcdg waypoint clear",   cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + BTN_GAP;
        addConfirmBtn("Cleanup Course", "/mcdg cleanupcourse",    cx, y, bw);                              y += BTN_H + BTN_GAP;
        addBtn("Crash Recovery Status", "/mcdg roundsession status", cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + BTN_GAP;
        addConfirmBtn("Clear Crash Recovery", "/mcdg roundsession clear", cx, y, bw);
    }

    private void buildConfirmDialog(int panelX, int panelY) {
        int cx = panelX + CONTENT_X_OFFSET;
        int cy = panelY + 60;
        int bw = PANEL_W - CONTENT_X_OFFSET - 8;
        addBtn("✔ CONFIRM", confirmCommand, cx, cy, bw, TEXT_RED, BTN_TINT_RED);
        addBtn("✘ Cancel",  null,           cx, cy + BTN_H + BTN_GAP + 8, bw, TEXT_MUTED, BTN_TINT_MUTED);
    }

    private void addBtn(String label, String command, int x, int y, int w, int textColor, int tint) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            if (command == null) {
                confirmPending = false;
                rebuild();
            } else {
                runCommand(command);
            }
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, tint});
        addDrawableChild(btn);
    }

    private void addConfirmBtn(String label, String command, int x, int y, int w) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            confirmLabel = label;
            confirmCommand = command;
            confirmPending = true;
            rebuild();
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, BTN_TINT_RED});
        addDrawableChild(btn);
    }

    private void runCommand(String command) {
        close();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            player.networkHandler.sendChatCommand(cmd);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentPage == Page.COURSES && !confirmPending) {
            int maxOffset = Math.max(0, state.courses().size() - ROWS_VISIBLE);
            playScrollOffset = Math.max(0, Math.min(maxOffset, playScrollOffset - (int) Math.signum(verticalAmount)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        renderBackground(context, mouseX, mouseY, delta);

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        context.fill(panelX,              panelY,              panelX + 1,              panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX + PANEL_W - 1,panelY,              panelX + PANEL_W,        panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX,              panelY,              panelX + PANEL_W,        panelY + 1,       BORDER_COLOR);
        context.fill(panelX,              panelY + PANEL_H - 1,panelX + PANEL_W,        panelY + PANEL_H, BORDER_COLOR);

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 28, HEADER_COLOR);

        context.drawTextWithShadow(textRenderer,
                Text.literal("⛳ MCDG").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 8, panelY + 9, TEXT_TITLE);

        String statusText  = state.roundActive() ? "● Round Active" : state.courseLoaded() ? "Course Loaded" : "No Course";
        int    statusColor = state.roundActive() ? TEXT_GREEN : TEXT_MUTED;
        context.drawTextWithShadow(textRenderer, Text.literal(statusText),
                panelX + PANEL_W - 8 - textRenderer.getWidth(statusText), panelY + 9, statusColor);

        context.fill(panelX, panelY + 28, panelX + PANEL_W, panelY + 29, BORDER_COLOR);
        context.fill(panelX + NAV_W, panelY + 28, panelX + NAV_W + 1, panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX, panelY + 28, panelX + NAV_W, panelY + PANEL_H, ACCENT_COLOR);

        for (ButtonWidget nav : navButtons) {
            Page navPage = navPageFor(nav.getMessage().getString());
            if (navPage == currentPage) {
                context.fill(nav.getX() - 1, nav.getY() - 1, nav.getX() + nav.getWidth() + 1, nav.getY() + nav.getHeight() + 1, NAV_ACTIVE_COLOR);
            }
        }

        for (int[] t : buttonTints) {
            if (t[4] != BTN_TINT_NONE) {
                context.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], t[4]);
            }
        }

        if (confirmPending) {
            int cx = panelX + CONTENT_X_OFFSET;
            int cy = panelY + 44;
            int cw = PANEL_W - CONTENT_X_OFFSET - 8;
            context.fill(cx - 4, cy - 4, cx + cw + 4, cy + 80, CONFIRM_BG);
            context.fill(cx - 4, cy - 4, cx + cw + 4, cy - 3,  CONFIRM_BORDER);
            context.fill(cx - 4, cy + 79, cx + cw + 4, cy + 80, CONFIRM_BORDER);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("Confirm: " + confirmLabel).formatted(Formatting.RED, Formatting.BOLD),
                    cx, cy + 4, TEXT_RED);
            context.drawTextWithShadow(textRenderer,
                    Text.literal("This action cannot be undone.").formatted(Formatting.GRAY),
                    cx, cy + 16, TEXT_MUTED);
        } else {
            String pageTitle = currentPage.name().charAt(0) + currentPage.name().substring(1).toLowerCase();
            context.drawTextWithShadow(textRenderer, Text.literal(pageTitle),
                    panelX + CONTENT_X_OFFSET, panelY + 32, TEXT_TITLE);

            if (currentPage == Page.COURSES && state.courses().isEmpty()) {
                context.drawTextWithShadow(textRenderer,
                        Text.literal("No saved courses. Use Build to create one."),
                        panelX + CONTENT_X_OFFSET, panelY + 60, TEXT_MUTED);
            }

            if (currentPage == Page.RULES) {
                String ruleInfo = "Current: " + state.rulesetName() + " / " + state.presetName();
                context.drawTextWithShadow(textRenderer, Text.literal(ruleInfo),
                        panelX + CONTENT_X_OFFSET, panelY + 33, TEXT_MUTED);
            }

            if (state.roundActive() && !state.courseName().isBlank()) {
                String label = state.courseName() + "  ·  " + state.rulesetName();
                context.drawTextWithShadow(textRenderer, Text.literal(label),
                        panelX + CONTENT_X_OFFSET, panelY + PANEL_H - 14, TEXT_MUTED);
            } else if (!state.rulesetName().isBlank()) {
                String label = "Ruleset: " + state.rulesetName() + " / " + state.presetName();
                context.drawTextWithShadow(textRenderer, Text.literal(label),
                        panelX + CONTENT_X_OFFSET, panelY + PANEL_H - 14, TEXT_MUTED);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private Page navPageFor(String label) {
        return switch (label) {
            case "Courses"   -> Page.COURSES;
            case "Build"     -> Page.BUILD;
            case "Rules"     -> Page.RULES;
            case "Admin"     -> Page.ADMIN;
            default          -> Page.DASHBOARD;
        };
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
