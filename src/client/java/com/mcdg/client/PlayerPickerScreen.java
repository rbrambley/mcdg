package com.mcdg.client;

import com.mcdg.net.RoundInviteRequest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen that lets the player choose which online players to invite to a round.
 */
public final class PlayerPickerScreen extends Screen {

    private static final int BG_COLOR = 0xF0111820;
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 240;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;
    private static final int CHECK_SIZE = 14;

    private final Screen parent;
    private final int catalogIndex;
    private final String courseId;
    private final boolean isChallengeCourse;
    private final String courseName;
    private final List<PlayerEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxVisibleRows;

    public PlayerPickerScreen(Screen parent, int catalogIndex, String courseName) {
        super(Text.literal("Invite Players"));
        this.parent = parent;
        this.catalogIndex = catalogIndex;
        this.courseId = null;
        this.isChallengeCourse = false;
        this.courseName = courseName;
    }

    public PlayerPickerScreen(Screen parent, String courseId, String courseName, boolean isChallengeCourse) {
        super(Text.literal("Invite Players"));
        this.parent = parent;
        this.catalogIndex = 0;
        this.courseId = courseId;
        this.isChallengeCourse = isChallengeCourse;
        this.courseName = courseName;
    }

    @Override
    protected void init() {
        entries.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.networkHandler != null) {
            UUID selfId = client.player.getUuid();
            for (PlayerListEntry entry : client.player.networkHandler.getPlayerList()) {
                UUID id = entry.getProfile().getId();
                String name = entry.getProfile().getName();
                if (id == null || name == null || id.equals(selfId)) {
                    continue; // don't show self; initiator is always included
                }
                entries.add(new PlayerEntry(id, name));
            }
        }
        entries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int contentY = panelY + 48;
        int contentH = PANEL_H - 48 - BTN_H - 24;
        maxVisibleRows = Math.max(1, contentH / ROW_H);

        // Send / Cancel buttons
        int btnY = panelY + PANEL_H - BTN_H - 10;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Send Invites"), b -> sendInvites())
                .dimensions(panelX + 10, btnY, 100, BTN_H).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> cancel())
                .dimensions(panelX + PANEL_W - 110, btnY, 100, BTN_H).build());

        // Scroll buttons (if needed)
        if (entries.size() > maxVisibleRows) {
            int scrollX = panelX + PANEL_W - 22;
            int scrollTop = contentY;
            int scrollH = maxVisibleRows * ROW_H;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> scroll(-1))
                    .dimensions(scrollX, scrollTop, 18, 16).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> scroll(1))
                    .dimensions(scrollX, scrollTop + scrollH - 16, 18, 16).build());
        }
    }

    private void scroll(int delta) {
        int maxOffset = Math.max(0, entries.size() - maxVisibleRows);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int contentX = panelX + 14;
        int contentY = panelY + 48;
        int listW = PANEL_W - 40;

        for (int i = 0; i < maxVisibleRows && scrollOffset + i < entries.size(); i++) {
            int rowY = contentY + i * ROW_H;
            int checkX = contentX + listW - CHECK_SIZE - 4;
            int checkY = rowY + (ROW_H - CHECK_SIZE) / 2;
            if (mouseX >= checkX && mouseX <= checkX + CHECK_SIZE + 4
                    && mouseY >= checkY && mouseY <= checkY + CHECK_SIZE + 4) {
                PlayerEntry entry = entries.get(scrollOffset + i);
                entry.selected = !entry.selected;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xB0000000, 0xB0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        context.drawBorder(panelX, panelY, PANEL_W, PANEL_H, 0xFF3A5A7A);

        // Title
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Invite Players").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 12, panelY + 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(courseName).formatted(Formatting.GRAY),
                panelX + 12, panelY + 26, 0xAAAAAA);

        // Player list
        int contentX = panelX + 14;
        int contentY = panelY + 48;
        int listW = PANEL_W - 40;

        for (int i = 0; i < maxVisibleRows && scrollOffset + i < entries.size(); i++) {
            PlayerEntry entry = entries.get(scrollOffset + i);
            int rowY = contentY + i * ROW_H;

            // Hover highlight
            if (mouseY >= rowY && mouseY < rowY + ROW_H
                    && mouseX >= contentX && mouseX <= contentX + listW) {
                context.fill(contentX, rowY, contentX + listW, rowY + ROW_H, 0x22FFFFFF);
            }

            // Name
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(entry.name),
                    contentX + 4, rowY + 5, 0xFFFFFF);

            // Checkbox
            int checkX = contentX + listW - CHECK_SIZE - 4;
            int checkY = rowY + (ROW_H - CHECK_SIZE) / 2;
            context.drawBorder(checkX, checkY, CHECK_SIZE, CHECK_SIZE, 0xFF888888);
            if (entry.selected) {
                context.fill(checkX + 2, checkY + 2, checkX + CHECK_SIZE - 2, checkY + CHECK_SIZE - 2, 0xFF57D163);
            }
        }

        // Empty state
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No other players online.").formatted(Formatting.GRAY),
                    this.width / 2, contentY + 20, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void sendInvites() {
        List<UUID> selected = new ArrayList<>();
        for (PlayerEntry entry : entries) {
            if (entry.selected) {
                selected.add(entry.id);
            }
        }
        if (selected.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Select at least one player to invite."), true);
            }
            return;
        }
        ClientPlayNetworking.send(new RoundInviteRequest.Payload(selected, catalogIndex, courseId, isChallengeCourse));
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void cancel() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static final class PlayerEntry {
        final UUID id;
        final String name;
        boolean selected = false;

        PlayerEntry(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
