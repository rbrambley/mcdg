package com.mcdg.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class WaypointAddScreen extends Screen {

    private static final int[] WAYPOINT_COLORS = {
            0xFFFF4D4D, 0xFF57D163, 0xFF4D9DFF, 0xFFFFD247, 0xFFC76CFF, 0xFFF2F5FF
    };
    private static final String[] COLOR_NAMES = { "Red", "Green", "Blue", "Yellow", "Purple", "White" };

    private final int pendingX;
    private final int pendingY;
    private final int pendingZ;
    private final String dimensionId;
    private final Runnable onCancel;
    private final WaypointAddCallback onConfirm;

    private TextFieldWidget nameField;
    private int selectedColorIndex = 0;

    public interface WaypointAddCallback {
        void accept(String name, int color, int x, int y, int z, String dimensionId);
    }

    public WaypointAddScreen(int x, int y, int z, String dimensionId, WaypointAddCallback onConfirm, Runnable onCancel) {
        super(Text.literal("Add Waypoint"));
        this.pendingX = x;
        this.pendingY = y;
        this.pendingZ = z;
        this.dimensionId = dimensionId;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void init() {
        int panelW = 220;
        int panelH = 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        nameField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 28, panelW - 20, 20, Text.literal(""));
        nameField.setMaxLength(24);
        nameField.setPlaceholder(Text.literal("Waypoint name...").formatted(Formatting.DARK_GRAY));
        nameField.setFocused(true);
        this.addSelectableChild(nameField);

        int colorBtnW = 28;
        int colorStartX = panelX + 10;
        int colorY = panelY + 62;
        for (int i = 0; i < WAYPOINT_COLORS.length; i++) {
            final int idx = i;
            int btnX = colorStartX + i * (colorBtnW + 4);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(COLOR_NAMES[i]), btn -> selectedColorIndex = idx)
                    .dimensions(btnX, colorY, colorBtnW, 16)
                    .build());
        }

        int confirmY = panelY + 96;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), btn -> confirm())
                .dimensions(panelX + 10, confirmY, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> cancel())
                .dimensions(panelX + panelW - 90, confirmY, 80, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelW = 220;
        int panelH = 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101820);
        context.drawBorder(panelX, panelY, panelW, panelH, 0xFF334455);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Add Waypoint").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 10, panelY + 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Color:").formatted(Formatting.GRAY),
                panelX + 10, panelY + 52, 0xAAAAAA);

        int colorBtnW = 28;
        int colorStartX = panelX + 10;
        int colorY = panelY + 62;
        for (int i = 0; i < WAYPOINT_COLORS.length; i++) {
            if (i == selectedColorIndex) {
                int btnX = colorStartX + i * (colorBtnW + 4);
                context.drawBorder(btnX - 2, colorY - 2, colorBtnW + 4, 20, 0xFFFFFFFF);
            }
        }

        nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        if (keyCode == 256) {
            cancel();
            return true;
        }
        return nameField.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return nameField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    private void confirm() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            name = "WP";
        }
        onConfirm.accept(name, WAYPOINT_COLORS[selectedColorIndex], pendingX, pendingY, pendingZ, dimensionId);
        if (this.client != null) this.client.setScreen(null);
    }

    private void cancel() {
        onCancel.run();
        if (this.client != null) this.client.setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
