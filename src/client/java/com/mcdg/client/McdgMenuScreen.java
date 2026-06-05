package com.mcdg.client;

import com.mcdg.net.MenuScreenSync;
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

    private static final int BG_COLOR         = 0xF0111820;
    private static final int HEADER_COLOR      = 0xFF1B2D42;
    private static final int ACCENT_COLOR      = 0xFF2A4A6A;
    private static final int BORDER_COLOR      = 0xFF3A5A7A;
    private static final int TEXT_TITLE        = 0xFFD4E8FF;
    private static final int TEXT_MUTED        = 0xFF8AAABB;
    private static final int TEXT_WHITE        = 0xFFFFFFFF;
    private static final int TEXT_GREEN        = 0xFF57D163;
    private static final int TEXT_GOLD         = 0xFFFFCC33;
    private static final int TEXT_RED          = 0xFFFF5555;

    private static final int PANEL_W  = 320;
    private static final int PANEL_H  = 260;
    private static final int NAV_W    = 80;
    private static final int CONTENT_X_OFFSET = NAV_W + 8;
    private static final int BTN_H    = 20;
    private static final int BTN_GAP  = 4;

    private enum Page { DASHBOARD, PLAY, BUILD, RULES, ADMIN }

    private final MenuScreenSync.Payload state;
    private Page currentPage = Page.DASHBOARD;
    private final List<ButtonWidget> navButtons = new ArrayList<>();
    private final List<ButtonWidget> contentButtons = new ArrayList<>();

    public McdgMenuScreen(MenuScreenSync.Payload state) {
        super(Text.literal("MCDG"));
        this.state = state;
    }

    @Override
    protected void init() {
        navButtons.clear();
        contentButtons.clear();

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        buildNavButtons(panelX, panelY);
        buildContentButtons(panelX, panelY);
    }

    private void buildNavButtons(int panelX, int panelY) {
        int navX = panelX + 4;
        int navStartY = panelY + 36;
        int btnW = NAV_W - 8;

        addNavButton("Dashboard", Page.DASHBOARD, navX, navStartY, btnW);
        addNavButton("Play",      Page.PLAY,      navX, navStartY + 26, btnW);
        if (state.isAdmin()) {
            addNavButton("Build", Page.BUILD, navX, navStartY + 52, btnW);
        }
        addNavButton("Rules",     Page.RULES,     navX, navStartY + 78, btnW);
        if (state.isAdmin()) {
            addNavButton("Admin", Page.ADMIN, navX, navStartY + 104, btnW);
        }
    }

    private void addNavButton(String label, Page page, int x, int y, int w) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = page;
            clearAndRebuild();
        }).dimensions(x, y, w, BTN_H).build();
        navButtons.add(btn);
        addDrawableChild(btn);
    }

    private void buildContentButtons(int panelX, int panelY) {
        int cx = panelX + CONTENT_X_OFFSET;
        int cy = panelY + 36;
        int bw = PANEL_W - CONTENT_X_OFFSET - 8;

        switch (currentPage) {
            case DASHBOARD -> buildDashboardPage(cx, cy, bw);
            case PLAY      -> buildPlayPage(cx, cy, bw);
            case BUILD     -> buildBuildPage(cx, cy, bw);
            case RULES     -> buildRulesPage(cx, cy, bw);
            case ADMIN     -> buildAdminPage(cx, cy, bw);
        }
    }

    private void buildDashboardPage(int cx, int cy, int bw) {
        int y = cy + 16;

        if (state.hasSavedSession()) {
            addContentButton(
                    "▶ Resume: " + state.savedCourseName() + " H" + state.savedHole(),
                    "/mcdg resumesession", cx, y, bw, TEXT_GREEN);
            y += BTN_H + BTN_GAP;
        }

        if (state.roundActive()) {
            addContentButton("Go to Lie",          "/mcdg gotolie",     cx, y, bw, TEXT_WHITE); y += BTN_H + BTN_GAP;
            addContentButton("End Round",          "/mcdg endround",    cx, y, bw, TEXT_GOLD);  y += BTN_H + BTN_GAP;
            addContentButton("Save & Leave Round", "/mcdg savesession", cx, y, bw, TEXT_MUTED); y += BTN_H + BTN_GAP;
        } else {
            addContentButton("List Courses", "/mcdg listcourses", cx, y, bw, TEXT_WHITE); y += BTN_H + BTN_GAP;
            addContentButton("Join Round",   "/mcdg joinround",   cx, y, bw, TEXT_GREEN); y += BTN_H + BTN_GAP;
            if (state.isAdmin()) {
                addContentButton("Auto Build Course", "/mcdg autocourse",   cx, y, bw, TEXT_GOLD); y += BTN_H + BTN_GAP;
            }
        }
    }

    private void buildPlayPage(int cx, int cy, int bw) {
        int y = cy + 16;

        if (state.courses().isEmpty()) {
            return;
        }

        for (MenuScreenSync.CourseEntry entry : state.courses()) {
            String label = entry.name() + "  (" + entry.holeCount() + "H)";
            int idx = entry.index();
            addContentButton("[PLAY] " + label, "/mcdg playcourse " + idx, cx, y, bw, TEXT_GREEN);
            y += BTN_H + BTN_GAP;
            if (y > cy + PANEL_H - 50) break;
        }
    }

    private void buildBuildPage(int cx, int cy, int bw) {
        int y = cy + 16;
        addContentButton("Auto Build Course",   "/mcdg autocourse",  cx, y, bw, TEXT_GOLD);  y += BTN_H + BTN_GAP;
        addContentButton("Manual Build Course", "/mcdg buildcourse", cx, y, bw, TEXT_WHITE);
    }

    private void buildRulesPage(int cx, int cy, int bw) {
        int y = cy + 16;
        addContentButton("Show Ruleset",         "/mcdg ruleset",          cx, y, bw, TEXT_WHITE); y += BTN_H + BTN_GAP;
        addContentButton("Set Casual",           "/mcdg ruleset casual",   cx, y, bw, TEXT_GREEN); y += BTN_H + BTN_GAP;
        addContentButton("Set Strict",           "/mcdg ruleset strict",   cx, y, bw, TEXT_GOLD);  y += BTN_H + BTN_GAP;
        addContentButton("Strict Surface Preset","/mcdg ruleset surface",  cx, y, bw, TEXT_WHITE);
    }

    private void buildAdminPage(int cx, int cy, int bw) {
        int y = cy + 16;
        addContentButton("Clear Waypoints",      "/mcdg waypoint clear",                          cx, y, bw, TEXT_MUTED); y += BTN_H + BTN_GAP;
        addContentButton("Cleanup Course",       "/mcdg menu confirm-request cleanupcourse",      cx, y, bw, TEXT_RED);   y += BTN_H + BTN_GAP;
        addContentButton("Crash Recovery Status","/mcdg roundsession status",                     cx, y, bw, TEXT_MUTED); y += BTN_H + BTN_GAP;
        addContentButton("Clear Crash Recovery", "/mcdg roundsession clear",                      cx, y, bw, TEXT_MUTED);
    }

    private void addContentButton(String label, String command, int x, int y, int w, int textColor) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> runCommand(command))
                .dimensions(x, y, w, BTN_H)
                .build();
        contentButtons.add(btn);
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

    private void clearAndRebuild() {
        clearChildren();
        navButtons.clear();
        contentButtons.clear();
        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;
        buildNavButtons(panelX, panelY);
        buildContentButtons(panelX, panelY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        renderBackground(context, mouseX, mouseY, delta);

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        context.fill(panelX, panelY, panelX + 1, panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, BORDER_COLOR);
        context.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, BORDER_COLOR);

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 28, HEADER_COLOR);

        String title = "⛳ MCDG";
        context.drawTextWithShadow(textRenderer, Text.literal(title).formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 8, panelY + 9, TEXT_TITLE);

        String statusText = state.roundActive() ? "● Round Active" : state.courseLoaded() ? "Course Loaded" : "No Course";
        int statusColor = state.roundActive() ? TEXT_GREEN : TEXT_MUTED;
        context.drawTextWithShadow(textRenderer, Text.literal(statusText), panelX + PANEL_W - 8 - textRenderer.getWidth(statusText), panelY + 9, statusColor);

        context.fill(panelX, panelY + 28, panelX + PANEL_W, panelY + 29, BORDER_COLOR);
        context.fill(panelX + NAV_W, panelY + 28, panelX + NAV_W + 1, panelY + PANEL_H, BORDER_COLOR);
        context.fill(panelX, panelY + 28, panelX + NAV_W, panelY + PANEL_H, ACCENT_COLOR);

        String pageTitle = currentPage.name().substring(0, 1) + currentPage.name().substring(1).toLowerCase();
        context.drawTextWithShadow(textRenderer, Text.literal(pageTitle).formatted(Formatting.WHITE),
                panelX + CONTENT_X_OFFSET, panelY + 33, TEXT_TITLE);

        if (state.roundActive() && !state.courseName().isBlank()) {
            String courseLabel = state.courseName() + "  ·  " + state.rulesetName();
            context.drawTextWithShadow(textRenderer, Text.literal(courseLabel),
                    panelX + CONTENT_X_OFFSET,
                    panelY + PANEL_H - 14,
                    TEXT_MUTED);
        } else if (!state.rulesetName().isBlank()) {
            String ruleLabel = "Ruleset: " + state.rulesetName() + " / " + state.presetName();
            context.drawTextWithShadow(textRenderer, Text.literal(ruleLabel),
                    panelX + CONTENT_X_OFFSET,
                    panelY + PANEL_H - 14,
                    TEXT_MUTED);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
