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
    private static final String[] COLOR_NAMES = { "Red", "Green", "Blue", "Gold", "Purple", "White" };
    private static final int SWATCH_SIZE = 20;
    private static final int SWATCH_GAP = 6;

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
        int panelW = 260;
        int panelH = 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        nameField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 28, panelW - 20, 20, Text.literal(""));
        nameField.setMaxLength(24);
        nameField.setPlaceholder(Text.literal("Waypoint name...").formatted(Formatting.DARK_GRAY));
        nameField.setFocused(true);
        this.addSelectableChild(nameField);

        int confirmY = panelY + 96;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), btn -> confirm())
                .dimensions(panelX + 10, confirmY, 90, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> cancel())
                .dimensions(panelX + panelW - 100, confirmY, 90, 20)
                .build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xB0000000, 0xB0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelW = 260;
        int panelH = 130;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101820);
        context.drawBorder(panelX, panelY, panelW, panelH, 0xFF334455);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Add Waypoint").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 10, panelY + 10, 0xFFFFFF);
        int swatchY = panelY + 56;
        int swatchStartX = panelX + 10;
        for (int i = 0; i < WAYPOINT_COLORS.length; i++) {
            int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
            int color = WAYPOINT_COLORS[i] | 0xFF000000;
            context.fill(sx, swatchY, sx + SWATCH_SIZE, swatchY + SWATCH_SIZE, color);
            if (i == selectedColorIndex) {
                context.drawBorder(sx - 2, swatchY - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, 0xFFFFFFFF);
            }
        }

        for (int i = 0; i < WAYPOINT_COLORS.length; i++) {
            int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
            if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) {
                context.drawTooltip(this.textRenderer, Text.literal(COLOR_NAMES[i]), (int) mouseX, (int) mouseY);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (this.width - 260) / 2;
        int panelY = (this.height - 130) / 2;
        int swatchY = panelY + 56;
        int swatchStartX = panelX + 10;
        for (int i = 0; i < WAYPOINT_COLORS.length; i++) {
            int sx = swatchStartX + i * (SWATCH_SIZE + SWATCH_GAP);
            if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) {
                selectedColorIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
