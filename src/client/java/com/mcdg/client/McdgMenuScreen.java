package com.mcdg.client;

import com.mcdg.net.LeaderboardRequest;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.net.SkillsScreenRequest;
import com.mcdg.net.SkillsScreenSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class McdgMenuScreen extends Screen {

    private static final int BG_COLOR          = 0xFF111820;
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
    private static final int DESC_SPACING      = 10;
    private static final int CONTENT_MAX_H     = PANEL_H - 28 - 20 - 16;
    private static final int ROWS_VISIBLE      = CONTENT_MAX_H / (BTN_H + BTN_GAP);

    private static final int SKILL_CARD_H      = 48;
    private static final int SKILL_CARD_GAP    = 4;
    private static final float SKILL_TEXT_SCALE = 0.75f;
    private static final int SKILL_CONTENT_TOP = 44;
    private static final int SKILL_CONTENT_BOTTOM_PAD = 8;

    private enum Page { DASHBOARD, COURSES, BUILD, RULES, ADMIN, COURSE_MAINTENANCE, SKILLS }

    private final MenuScreenSync.Payload state;
    private SkillsScreenSync.Payload skillsData;
    private boolean skillsDataRequested = false;
    private Page currentPage = Page.DASHBOARD;
    private int playScrollOffset = 0;
    private int skillsScrollOffset = 0;

    private boolean confirmPending = false;
    private String confirmLabel = "";
    private String confirmCommand = "";

    private final List<ButtonWidget> navButtons = new ArrayList<>();
    private final List<ButtonWidget> contentButtons = new ArrayList<>();
    private final List<int[]> buttonTints = new ArrayList<>();
    private final List<ButtonDescription> buttonDescriptions = new ArrayList<>();
    private final List<SkillCardData> skillCards = new ArrayList<>();
    private ButtonDescription hoveredDescription = null;

    public McdgMenuScreen(MenuScreenSync.Payload state) {
        super(Text.literal("MCDG"));
        this.state = state;
    }

    public McdgMenuScreen(MenuScreenSync.Payload state, SkillsScreenSync.Payload skillsData) {
        super(Text.literal("MCDG"));
        this.state = state;
        this.skillsData = skillsData;
        this.currentPage = Page.SKILLS;
    }

    public static void openSkillsPage(SkillsScreenSync.Payload skillsData) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> {
                MenuScreenSync.Payload dummyState = new MenuScreenSync.Payload(
                    false, false, "", 0, 0, false, "", 0, 0, false, "", "", List.of(), false
                );
                client.setScreen(new McdgMenuScreen(dummyState, skillsData));
            });
        }
    }

    public void updateSkillsData(SkillsScreenSync.Payload data) {
        this.skillsData = data;
        this.skillsDataRequested = true;
        rebuild();
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
        buttonDescriptions.clear();
        skillCards.clear();

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
        addNavButton("Skills",    Page.SKILLS,    navX, navStartY + (slot++ * 26), btnW);
        if (state.isAdmin()) {
            addNavButton("Admin", Page.ADMIN, navX, navStartY + (slot * 26), btnW);
        }
    }

    private void addNavButton(String label, Page page, int x, int y, int w) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = page;
            confirmPending = false;
            playScrollOffset = 0;
            skillsScrollOffset = 0;
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
            case SKILLS    -> buildSkillsPage(cx, cy, bw, panelX, panelY);
            case ADMIN     -> buildAdminPage(cx, cy, bw);
            case COURSE_MAINTENANCE -> buildCourseMaintenancePage(cx, cy, bw, panelX, panelY);
        }
    }

    private void buildDashboardPage(int cx, int cy, int bw) {
        int y = cy;

        // Permanent Resort TP button (always visible)
        addBtn("🏨 Teleport to Resort", "/mcdg resort", cx, y, bw, TEXT_GOLD, BTN_TINT_GOLD); y += BTN_H + BTN_GAP;

        if (state.roundActive()) {
            // ── Active round ──
            if (!state.courseName().isBlank()) {
                addBtn("⛳ Go to Lie  [" + state.courseName() + "]", "/mcdg gotolie", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
            } else {
                addBtn("⛳ Go to Lie", "/mcdg gotolie", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + BTN_GAP;
            }
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
                addAutoCourseBtn("Build New Course", cx, y, bw, TEXT_GOLD, BTN_TINT_GOLD); y += BTN_H + BTN_GAP;
            }
            addPageSwitchBtn("Browse Saved Courses →", Page.COURSES, cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);
        }
    }

    private void addAutoCourseBtn(String label, int x, int y, int w, int textColor, int tint) {
        boolean caveMode = state.caveMode();
        String finalLabel = caveMode ? "🕳️ " + label : label;
        int finalTint = caveMode ? BTN_TINT_RED : tint;
        int finalTextColor = caveMode ? TEXT_RED : textColor;
        
        ButtonWidget btn = ButtonWidget.builder(Text.literal(finalLabel), b -> {
            if (client != null) {
                client.setScreen(new AutoCourseNameScreen(this));
            }
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, finalTint});
        addDrawableChild(btn);
    }

    private void addPageSwitchBtn(String label, Page page, int x, int y, int w, int textColor, int tint) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = page;
            confirmPending = false;
            playScrollOffset = 0;
            skillsScrollOffset = 0;
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
        int playW = 34;
        int inviteW = 34;
        int gap = 3;
        int scoresW = 18;
        int totalBtnsW = tpW + scoresW + playW + inviteW + (gap * 3);
        int visibleRows = Math.min(ROWS_VISIBLE, courses.size());
        int maxOffset = Math.max(0, courses.size() - visibleRows);
        playScrollOffset = Math.max(0, Math.min(playScrollOffset, maxOffset));

        int y = cy;
        for (int i = playScrollOffset; i < playScrollOffset + visibleRows && i < courses.size(); i++) {
            MenuScreenSync.CourseEntry entry = courses.get(i);
            boolean isActive = entry.index() == state.activeCatalogIndex();
            String prefix = isActive ? "▶ " : "";
            String resortTag = "resort-surround".equals(entry.sourceTag()) ? "[RESORT] " : "";
            String label = prefix + resortTag + entry.name() + "  (" + entry.holeCount() + "H)";
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
            // Invite button: open player picker for any non-active course
            if (state.roundActive() && isActive) {
                addBtn("[INV]", null, x, y, inviteW, TEXT_MUTED, BTN_TINT_MUTED);
            } else {
                int finalIdx = idx;
                String finalCourseName = entry.name();
                ButtonWidget inviteBtn = ButtonWidget.builder(Text.literal("[INV]"), b -> {
                    if (client != null) {
                        client.setScreen(new PlayerPickerScreen(this, finalIdx, finalCourseName));
                    }
                }).dimensions(x, y, inviteW, BTN_H).build();
                contentButtons.add(inviteBtn);
                buttonTints.add(new int[]{x, y, inviteW, BTN_H, BTN_TINT_GOLD});
                addDrawableChild(inviteBtn);
            }
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
        addAutoCourseBtn("Auto Build Course", cx, y, bw, TEXT_GOLD, BTN_TINT_GOLD);  y += BTN_H + BTN_GAP;
        addBtn("Manual Build Course", "/mcdg buildcourse", cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);
    }

    private void buildRulesPage(int cx, int cy, int bw) {
        int y = cy;
        boolean isStrict = isRulesetStrict(state.rulesetName());

        addBtnWithDesc("Set Casual", "5-block lie tolerance, forgiving", "/mcdg ruleset casual", cx, y, bw, TEXT_GREEN, BTN_TINT_GREEN); y += BTN_H + DESC_SPACING + BTN_GAP;
        addBtnWithDesc("Set Strict", "2-block lie tolerance, tournament-style", "/mcdg ruleset strict", cx, y, bw, TEXT_GOLD, BTN_TINT_GOLD); y += BTN_H + DESC_SPACING + BTN_GAP;

        if (state.isAdmin() && isStrict) {
            int indent = 12;
            addBtnWithDesc("└ Surface: Fast", "Widest corridors, no slope/rough hazards", "/mcdg ruleset surface fast", cx + indent, y, bw - indent, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + DESC_SPACING + BTN_GAP;
            addBtnWithDesc("└ Surface: Balanced", "Moderate corridors, slope hazards enabled", "/mcdg ruleset surface balanced", cx + indent, y, bw - indent, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + DESC_SPACING + BTN_GAP;
            addBtnWithDesc("└ Surface: Tournament", "Narrowest corridors, all hazards enabled", "/mcdg ruleset surface tournament", cx + indent, y, bw - indent, TEXT_MUTED, BTN_TINT_MUTED);
        }
    }

    private void buildSkillsPage(int cx, int cy, int bw, int panelX, int panelY) {
        if (skillsData == null) {
            if (!skillsDataRequested) {
                ClientPlayNetworking.send(new SkillsScreenRequest.Payload());
                skillsDataRequested = true;
            }
            addBtn(Text.translatable("gui.mcdg.skills.loading").getString(), null, cx, cy, bw, TEXT_MUTED, BTN_TINT_NONE);
            return;
        }

        List<SkillsScreenSync.SkillEntry> skills = new ArrayList<>(skillsData.skills().values());
        skills.sort(Comparator.comparing(SkillsScreenSync.SkillEntry::key));

        int skillsAreaH = PANEL_H - SKILL_CONTENT_TOP - SKILL_CONTENT_BOTTOM_PAD;
        int visibleSkills = Math.max(1, skillsAreaH / (SKILL_CARD_H + SKILL_CARD_GAP));
        int maxOffset = Math.max(0, skills.size() - visibleSkills);
        skillsScrollOffset = Math.max(0, Math.min(skillsScrollOffset, maxOffset));

        int y = cy;
        for (int i = skillsScrollOffset; i < skillsScrollOffset + visibleSkills && i < skills.size(); i++) {
            SkillsScreenSync.SkillEntry skill = skills.get(i);
            int skillColor = Formatting.byName(skill.colorName()) == null ? TEXT_WHITE : Formatting.byName(skill.colorName()).getColorValue();
            int statusColor = skill.unlocked() ? TEXT_GREEN : TEXT_RED;
            String statusText = skill.unlocked() ? Text.translatable("chat.mcdg.skill_status_unlocked").getString() : Text.translatable("chat.mcdg.skill_status_locked").getString();

            String progressText = skill.unlocked() ? "✓" : skill.currentProgress() + "/" + skill.requiredCount();
            float progress = skill.unlocked() ? 1.0f : (float) skill.currentProgress() / skill.requiredCount();

            addSkillCard(skill.displayName(), skill.benefitDescription(), skill.description(), progressText, progress, skill.unlocked(), skillColor, statusColor, statusText, cx, y, bw);
            y += SKILL_CARD_H + SKILL_CARD_GAP;
        }

        if (skills.size() > visibleSkills) {
            int scrollBarX = panelX + PANEL_W - 12;
            int scrollAreaY = panelY + SKILL_CONTENT_TOP;
            int scrollAreaH = visibleSkills * (SKILL_CARD_H + SKILL_CARD_GAP);
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> {
                skillsScrollOffset = Math.max(0, skillsScrollOffset - 1);
                rebuild();
            }).dimensions(scrollBarX, scrollAreaY, 10, 12).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> {
                skillsScrollOffset = Math.min(maxOffset, skillsScrollOffset + 1);
                rebuild();
            }).dimensions(scrollBarX, scrollAreaY + scrollAreaH - 12, 10, 12).build());
        }
    }

    private void addSkillCard(String name, String benefit, String requirement, String progressText, float progress, boolean unlocked, int nameColor, int statusColor, String statusText, int x, int y, int w) {
        int cardH = SKILL_CARD_H;
        int barW = w - 16;
        int barH = 5;

        skillCards.add(new SkillCardData(x, y, w, cardH, name, benefit, requirement, progressText, progress, unlocked, nameColor, statusColor, statusText, barW, barH));
    }

    private void addBtnWithDesc(String label, String description, String command, int x, int y, int w, int textColor, int tint) {
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

        // Calculate truncated description for compact display
        String truncatedDesc = truncateDescription(description, w);
        // Store description for rendering (shown below button)
        buttonDescriptions.add(new ButtonDescription(x, y + BTN_H + 2, truncatedDesc, description, false));
    }

    private void addPageSwitchBtnWithDesc(String label, String description, Page targetPage, int x, int y, int w, int textColor, int tint) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            currentPage = targetPage;
            playScrollOffset = 0;
            skillsScrollOffset = 0;
            confirmPending = false;
            rebuild();
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, tint});
        addDrawableChild(btn);

        // Calculate truncated description for compact display
        String truncatedDesc = truncateDescription(description, w);
        // Store description for rendering (shown below button)
        buttonDescriptions.add(new ButtonDescription(x, y + BTN_H + 2, truncatedDesc, description, false));
    }

    private static boolean isRulesetStrict(String rulesetName) {
        return rulesetName != null && "strict".equalsIgnoreCase(rulesetName);
    }

    private void buildAdminPage(int cx, int cy, int bw) {
        int y = cy;
        addPageSwitchBtnWithDesc("Course Maintenance", "Manage course catalog and world cleanup", Page.COURSE_MAINTENANCE, cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + DESC_SPACING + BTN_GAP;
        addBtnWithDesc("Remove Resort Courses", "Remove all 3 auto-built resort surround courses from world", "/mcdg removesurroundcourses", cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + DESC_SPACING + BTN_GAP;

        addSectionHeader("Crash Recovery", cx, y, bw); y += 14 + BTN_GAP;
        addBtnWithDesc("Crash Recovery Status", "Show crash recovery data status", "/mcdg roundsession status", cx, y, bw, TEXT_MUTED, BTN_TINT_MUTED); y += BTN_H + DESC_SPACING + BTN_GAP;
        addConfirmBtnWithDesc("Clear Crash Recovery", "Clear all crash recovery data (destructive)", "/mcdg roundsession clear", cx, y, bw, TEXT_RED, BTN_TINT_RED); y += BTN_H + DESC_SPACING + BTN_GAP;
    }

    private void buildCourseMaintenancePage(int cx, int cy, int bw, int panelX, int panelY) {
        List<MenuScreenSync.CourseEntry> courses = state.courses();

        addPageSwitchBtn("← Back to Admin", Page.ADMIN, cx, cy, bw, TEXT_MUTED, BTN_TINT_NONE);
        cy += BTN_H + BTN_GAP;

        if (courses.isEmpty()) {
            addBtn("No courses in catalog", null, cx, cy, bw, TEXT_MUTED, BTN_TINT_MUTED);
            return;
        }

        int delW = 30;
        int clrW = 30;
        int bothW = 30;
        int gap = 3;
        int totalBtnsW = delW + clrW + bothW + (gap * 2);
        int maintRowHeight = (BTN_H + BTN_GAP) + (BTN_H + DESC_SPACING + BTN_GAP);
        int backBtnHeight = BTN_H + BTN_GAP;
        int maintVisibleRows = Math.max(1, (CONTENT_MAX_H - backBtnHeight) / maintRowHeight);
        int visibleRows = Math.min(maintVisibleRows, courses.size());
        int maxOffset = Math.max(0, courses.size() - visibleRows);
        playScrollOffset = Math.max(0, Math.min(playScrollOffset, maxOffset));

        int y = cy;
        for (int i = playScrollOffset; i < playScrollOffset + visibleRows && i < courses.size(); i++) {
            MenuScreenSync.CourseEntry entry = courses.get(i);
            int idx = entry.index();
            String resortTag = "resort-surround".equals(entry.sourceTag()) ? "[RESORT] " : "";
            String label = "#" + idx + " " + resortTag + entry.name() + "  (" + entry.holeCount() + "H)";

            addBtn(label, null, cx, y, bw, TEXT_WHITE, BTN_TINT_NONE);
            y += BTN_H + BTN_GAP;

            int x = cx;
            addConfirmBtnWithDesc("[DEL]", "Delete from catalog only", "/mcdg removecourse " + idx, x, y, delW, TEXT_RED, BTN_TINT_RED);
            x += delW + gap;
            addBtnWithDesc("[CLR]", "Remove from world only", "/mcdg cleanupcoursebyindex " + idx, x, y, clrW, TEXT_GOLD, BTN_TINT_GOLD);
            x += clrW + gap;
            addConfirmBtnWithDesc("[X]", "Remove from both catalog and world", "/mcdg removecourseboth " + idx, x, y, bothW, TEXT_RED, BTN_TINT_RED);
            y += BTN_H + DESC_SPACING + BTN_GAP;
        }

        if (courses.size() > visibleRows) {
            int scrollBarX = panelX + PANEL_W - 12;
            int scrollAreaY = cy;
            int scrollAreaH = visibleRows * maintRowHeight;
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

    private void addConfirmBtnWithDesc(String label, String description, String command, int x, int y, int w, int textColor, int tint) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
            confirmLabel = label;
            confirmCommand = command;
            confirmPending = true;
            rebuild();
        }).dimensions(x, y, w, BTN_H).build();
        contentButtons.add(btn);
        buttonTints.add(new int[]{x, y, w, BTN_H, tint});
        addDrawableChild(btn);

        // Calculate truncated description for compact display
        String truncatedDesc = truncateDescription(description, w);
        // Store description for rendering (shown below button)
        buttonDescriptions.add(new ButtonDescription(x, y + BTN_H + 2, truncatedDesc, description, false));
    }

    private void addSectionHeader(String text, int x, int y, int w) {
        buttonDescriptions.add(new ButtonDescription(x, y, text, text, true));
    }

    /**
     * Truncates a description to fit within a single line at 0.75f scale.
     * Adds "..." if truncation occurs.
     */
    private String truncateDescription(String description, int maxWidth) {
        if (textRenderer == null) {
            return description;
        }

        float descScale = 0.75f;
        int scaledWidth = (int) (maxWidth / descScale);
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);

        // If the full text fits, return it as-is
        if (textRenderer.getWidth(description) <= scaledWidth) {
            return description;
        }

        // Binary search for the truncation point
        int low = 0;
        int high = description.length();
        String bestTruncated = description;

        while (low <= high) {
            int mid = (low + high) / 2;
            String truncated = description.substring(0, mid) + ellipsis;
            int width = textRenderer.getWidth(truncated);

            if (width <= scaledWidth) {
                bestTruncated = truncated;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return bestTruncated;
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
        if (currentPage == Page.SKILLS && !confirmPending && skillsData != null) {
            int skillsAreaH = PANEL_H - SKILL_CONTENT_TOP - SKILL_CONTENT_BOTTOM_PAD;
            int visibleSkills = Math.max(1, skillsAreaH / (SKILL_CARD_H + SKILL_CARD_GAP));
            int skillCount = skillsData.skills().size();
            int maxOffset = Math.max(0, skillCount - visibleSkills);
            skillsScrollOffset = Math.max(0, Math.min(maxOffset, skillsScrollOffset - (int) Math.signum(verticalAmount)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void applyBlur(float delta) {
        // Intentionally suppressed — blur bleeds into custom-drawn text and boxes
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Reset hover state at start of each render
        hoveredDescription = null;

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

        int contentWidth = PANEL_W - CONTENT_X_OFFSET - 8;
        int contentTop = panelY + SKILL_CONTENT_TOP;
        int contentBottom = panelY + PANEL_H - SKILL_CONTENT_BOTTOM_PAD;
        context.enableScissor(panelX + CONTENT_X_OFFSET, contentTop, panelX + PANEL_W - 8, contentBottom);
        for (SkillCardData card : skillCards) {
            renderSkillCard(context, card);
        }
        context.disableScissor();
        for (ButtonDescription desc : buttonDescriptions) {
            if (desc.isHeader) {
                context.fill(desc.x, desc.y - 3, desc.x + contentWidth, desc.y - 2, BORDER_COLOR);
                context.drawTextWithShadow(textRenderer,
                        Text.literal(desc.fullText).formatted(Formatting.BOLD),
                        desc.x, desc.y, TEXT_TITLE);
            } else {
                // Check if mouse is hovering over the description area
                boolean isHovering = mouseX >= desc.x && mouseX <= desc.x + contentWidth &&
                                    mouseY >= desc.y && mouseY <= desc.y + Math.round(textRenderer.fontHeight * 0.75f);

                // Track hovered description for tooltip rendering
                if (isHovering) {
                    hoveredDescription = desc;
                }

                // Always render truncated text
                float descScale = 0.75f;
                int scaledWidth = (int) (contentWidth / descScale);
                List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(
                        Text.literal(desc.truncatedText), scaledWidth);
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(desc.x, desc.y, 0);
                matrices.scale(descScale, descScale, 1.0f);
                for (int li = 0; li < lines.size(); li++) {
                    context.drawTextWithShadow(textRenderer, lines.get(li),
                            0, (int) (li * textRenderer.fontHeight), TEXT_WHITE);
                }
                matrices.pop();
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


            if (state.roundActive() && !state.courseName().isBlank()) {
                String label;
                String rulesetName = state.rulesetName() != null ? state.rulesetName() : "";
                String presetName = state.presetName() != null ? state.presetName() : "";
                if (isRulesetStrict(rulesetName)) {
                    label = state.courseName() + "  ·  " + rulesetName + " (" + presetName + ")";
                } else {
                    label = state.courseName() + "  ·  " + rulesetName;
                }
                context.drawTextWithShadow(textRenderer, Text.literal(label),
                        panelX + CONTENT_X_OFFSET, panelY + PANEL_H - 14, TEXT_MUTED);
            } else if (state.rulesetName() != null && !state.rulesetName().isBlank()) {
                String label;
                String rulesetName = state.rulesetName() != null ? state.rulesetName() : "";
                String presetName = state.presetName() != null ? state.presetName() : "";
                if (isRulesetStrict(rulesetName)) {
                    label = "Ruleset: " + rulesetName + " (" + presetName + ")";
                } else {
                    label = "Ruleset: " + rulesetName;
                }
                context.drawTextWithShadow(textRenderer, Text.literal(label),
                        panelX + CONTENT_X_OFFSET, panelY + PANEL_H - 14, TEXT_MUTED);
            }
        }

        super.render(context, mouseX, mouseY, delta);

        // Render tooltip for hovered description (rendered after super.render to appear on top of all child elements)
        if (hoveredDescription != null && !hoveredDescription.isHeader) {
            renderDescriptionTooltip(context, textRenderer, hoveredDescription, contentWidth, panelX, panelY);
        }
    }

    private Page navPageFor(String label) {
        return switch (label) {
            case "Courses"   -> Page.COURSES;
            case "Build"     -> Page.BUILD;
            case "Rules"     -> Page.RULES;
            case "Skills"    -> Page.SKILLS;
            case "Admin"     -> Page.ADMIN;
            default          -> Page.DASHBOARD;
        };
    }

    /**
     * Render a tooltip popup box for a hovered description.
     * The tooltip is rendered as an opaque box that floats above all content.
     */
    private void renderDescriptionTooltip(DrawContext context, net.minecraft.client.font.TextRenderer textRenderer,
                                          ButtonDescription desc, int contentWidth, int panelX, int panelY) {
        float descScale = 0.75f;
        int scaledWidth = (int) (contentWidth / descScale);
        List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(
                Text.literal(desc.fullText), scaledWidth);

        // Calculate tooltip dimensions
        int tooltipWidth = contentWidth;
        int tooltipHeight = Math.round((lines.size() * textRenderer.fontHeight + 8) * descScale);
        int padding = Math.round(4 * descScale);

        // Calculate tooltip position (above the description if possible, below if not)
        int tooltipX = desc.x;
        int tooltipY = desc.y - tooltipHeight - padding;

        // Check if tooltip would go above the panel, if so position below
        if (tooltipY < panelY) {
            tooltipY = desc.y + Math.round(textRenderer.fontHeight * descScale) + padding;
        }

        // Render opaque background
        int tooltipBgColor = 0xFF000000; // Black with full opacity
        int tooltipBorderColor = 0xFF3A5A7A; // Match panel border color
        context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, tooltipBgColor);
        // Draw border manually (top, bottom, left, right)
        context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, tooltipBorderColor);
        context.fill(tooltipX, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth, tooltipY + tooltipHeight, tooltipBorderColor);
        context.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, tooltipBorderColor);
        context.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, tooltipBorderColor);

        // Render wrapped text inside tooltip
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(tooltipX + padding, tooltipY + padding, 0);
        matrices.scale(descScale, descScale, 1.0f);
        for (int li = 0; li < lines.size(); li++) {
            context.drawTextWithShadow(textRenderer, lines.get(li),
                    0, (int) (li * textRenderer.fontHeight), TEXT_WHITE);
        }
        matrices.pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static record ButtonDescription(int x, int y, String truncatedText, String fullText, boolean isHeader) {
        ButtonDescription(int x, int y, String description) {
            this(x, y, description, description, false);
        }
    }

    private static record SkillCardData(int x, int y, int w, int h, String name, String benefit, String requirement, String progressText, float progress, boolean unlocked, int nameColor, int statusColor, String statusText, int barW, int barH) {}

    private void renderSkillCard(DrawContext context, SkillCardData card) {
        boolean unlocked = card.unlocked;
        int cardBg = unlocked ? 0xFF1A1A2E : 0xFF0F0F1A;
        int cardBorder = card.nameColor;

        context.fill(card.x, card.y, card.x + card.w, card.y + card.h, cardBg);
        context.drawBorder(card.x, card.y, card.w, card.h, cardBorder);

        int padX = 6;
        int padY = 5;
        int lineH = 8;
        int textX = card.x + padX;
        int textY = card.y + padY;
        int textMaxW = card.w - padX * 2;
        int scaledMaxW = (int) (textMaxW / SKILL_TEXT_SCALE);

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(textX, textY, 0);
        matrices.scale(SKILL_TEXT_SCALE, SKILL_TEXT_SCALE, 1.0f);

        String progressStr = card.progressText();
        int progressWidth = textRenderer.getWidth(progressStr);
        int nameScaledMaxW = scaledMaxW - progressWidth - 4;
        List<net.minecraft.text.OrderedText> nameLines = textRenderer.wrapLines(
                Text.literal(card.name()).formatted(Formatting.BOLD), nameScaledMaxW);
        if (!nameLines.isEmpty()) {
            context.drawTextWithShadow(textRenderer, nameLines.get(0), 0, 0, card.nameColor);
        }

        int progressLogicalX = scaledMaxW - progressWidth;
        context.drawTextWithShadow(textRenderer, Text.literal(progressStr), progressLogicalX, 0, card.statusColor);

        matrices.pop();

        textY += lineH + 1;
        matrices.push();
        matrices.translate(textX, textY, 0);
        matrices.scale(SKILL_TEXT_SCALE, SKILL_TEXT_SCALE, 1.0f);
        List<net.minecraft.text.OrderedText> benefitLines = textRenderer.wrapLines(
                Text.literal(card.benefit()), scaledMaxW);
        if (!benefitLines.isEmpty()) {
            context.drawTextWithShadow(textRenderer, benefitLines.get(0), 0, 0, TEXT_WHITE);
        }
        matrices.pop();

        textY += lineH;
        matrices.push();
        matrices.translate(textX, textY, 0);
        matrices.scale(SKILL_TEXT_SCALE, SKILL_TEXT_SCALE, 1.0f);
        List<net.minecraft.text.OrderedText> requirementLines = textRenderer.wrapLines(
                Text.literal(card.requirement()), scaledMaxW);
        if (!requirementLines.isEmpty()) {
            context.drawTextWithShadow(textRenderer, requirementLines.get(0), 0, 0, TEXT_MUTED);
        }
        matrices.pop();

        int barX = card.x + padX;
        int barY = card.y + card.h - card.barH - padY;
        context.fill(barX, barY, barX + card.barW, barY + card.barH, 0xFF000000);
        int filledWidth = (int) (card.barW * card.progress);
        int barColor = unlocked ? TEXT_GREEN : 0xFF4A90A4;
        context.fill(barX, barY, barX + filledWidth, barY + card.barH, barColor);
    }
}
