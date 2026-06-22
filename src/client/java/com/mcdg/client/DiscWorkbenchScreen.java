package com.mcdg.client;

import com.mcdg.game.DiscWorkbenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class DiscWorkbenchScreen extends HandledScreen<DiscWorkbenchScreenHandler> {

    public DiscWorkbenchScreen(DiscWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int buttonX = this.x + 70;
        int buttonY = this.y + 32;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), btn -> {
            this.handler.onButtonClick(this.client.player, 0);
        }).dimensions(buttonX, buttonY, 50, 20).build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, 0xFFC6C6C6);
        context.fill(x + 1, y + 1, x + this.backgroundWidth - 1, y + this.backgroundHeight - 1, 0xFF8B8B8B);
        context.fill(x + 2, y + 2, x + this.backgroundWidth - 2, y + this.backgroundHeight - 2, 0xFFC6C6C6);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
