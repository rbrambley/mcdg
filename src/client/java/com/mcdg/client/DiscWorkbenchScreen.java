package com.mcdg.client;

import com.mcdg.game.DiscWorkbenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DiscWorkbenchScreen extends HandledScreen<DiscWorkbenchScreenHandler> {

    private static final Identifier SLOT_BACKGROUND = Identifier.of("minecraft", "container/slot");

    private ButtonWidget applyButton;

    public DiscWorkbenchScreen(DiscWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = this.x + 70;
        int buttonY = this.y + 60;
        applyButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), btn -> {
            this.client.interactionManager.clickButton(this.handler.syncId, 0);
        }).dimensions(buttonX, buttonY, 50, 20).build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, 0xFFC6C6C6);
        context.fill(x + 1, y + 1, x + this.backgroundWidth - 1, y + this.backgroundHeight - 1, 0xFF8B8B8B);
        context.fill(x + 2, y + 2, x + this.backgroundWidth - 2, y + this.backgroundHeight - 2, 0xFFC6C6C6);

        for (Slot slot : this.handler.slots) {
            context.drawGuiTexture(SLOT_BACKGROUND, x + slot.x, y + slot.y, 18, 18);
        }

        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.disc"), x + 44 + 9, y + 55);
        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.book"), x + 80 + 9, y + 55);
        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.result"), x + 116 + 9, y + 55);
    }

    private void drawSlotLabel(DrawContext context, Text label, int centerX, int y) {
        int width = this.textRenderer.getWidth(label);
        context.drawTextWithShadow(this.textRenderer, label, centerX - width / 2, y, 0x404040);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (applyButton != null) {
            applyButton.active = this.handler.canApply();
        }
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
