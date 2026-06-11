package com.mcdg.client;

import com.mcdg.net.WaypointTeleportSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class WaypointListScreen extends Screen {
    private final Screen parent;
    private List<WaypointManager.ClientWaypoint> waypoints;
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 8;

    public WaypointListScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.waypoints = WaypointManager.getWaypoints();
        refreshButtons();
    }

    private void refreshButtons() {
        clearChildren();
        int startX = this.width / 2 - 100;
        int startY = 50;
        int maxTextWidth = 105; // leave room before Teleport button at startX + 110
        int visibleEnd = Math.min(waypoints.size(), scrollOffset + VISIBLE_ROWS);

        for (int i = scrollOffset; i < visibleEnd; i++) {
            WaypointManager.ClientWaypoint wp = waypoints.get(i);
            final String name = wp.name();
            int y = startY + (i - scrollOffset) * ROW_HEIGHT;

            addDrawableChild(ButtonWidget.builder(Text.literal("Teleport"), btn -> {
                ClientPlayNetworking.send(new WaypointTeleportSync(name));
                this.client.setScreen(null);
            }).dimensions(startX + 110, y, 80, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), btn -> {
                WaypointManager.removeWaypoint(name);
                this.waypoints = WaypointManager.getWaypoints();
                refreshButtons();
            }).dimensions(startX + 200, y, 60, 20).build());
        }

        // Scroll buttons
        if (waypoints.size() > VISIBLE_ROWS) {
            addDrawableChild(ButtonWidget.builder(Text.literal("▲"), btn -> {
                scrollOffset = Math.max(0, scrollOffset - 1);
                refreshButtons();
            }).dimensions(this.width / 2 + 130, startY, 20, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("▼"), btn -> {
                scrollOffset = Math.min(waypoints.size() - VISIBLE_ROWS, scrollOffset + 1);
                refreshButtons();
            }).dimensions(this.width / 2 + 130, startY + (VISIBLE_ROWS - 1) * ROW_HEIGHT, 20, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn ->
                this.client.setScreen(parent)
        ).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        int startX = this.width / 2 - 100;
        int startY = 50;
        int maxTextWidth = 105; // leave room before Teleport button at startX + 110
        int visibleEnd = Math.min(waypoints.size(), scrollOffset + VISIBLE_ROWS);
        for (int i = scrollOffset; i < visibleEnd; i++) {
            WaypointManager.ClientWaypoint wp = waypoints.get(i);
            String fullLabel = wp.name() + " (" + wp.x() + ", " + wp.y() + ", " + wp.z() + ")";
            String label = this.textRenderer.trimToWidth(fullLabel, maxTextWidth);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), startX, startY + (i - scrollOffset) * ROW_HEIGHT + 6, 0xAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
