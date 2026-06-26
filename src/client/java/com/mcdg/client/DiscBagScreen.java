package com.mcdg.client;

import com.mcdg.game.DiscBagScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * Client-side GUI screen for the disc bag inventory.
 */
public class DiscBagScreen extends HandledScreen<DiscBagScreenHandler> {

    public DiscBagScreen(DiscBagScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 140;
        this.titleY = 6;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        
        // Draw main background
        context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, 0xFFC6C6C6);
        context.fill(x + 1, y + 1, x + this.backgroundWidth - 1, y + this.backgroundHeight - 1, 0xFF8B8B8B);
        context.fill(x + 2, y + 2, x + this.backgroundWidth - 2, y + this.backgroundHeight - 2, 0xFFC6C6C6);
        
        // Draw bag inventory section label
        context.drawText(this.textRenderer, Text.literal("Disc Storage"), x + 8, y + 6, 0x404040, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}