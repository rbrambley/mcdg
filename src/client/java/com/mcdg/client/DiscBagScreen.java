package com.mcdg.client;

import com.mcdg.game.DiscBagScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Client-side GUI screen for the disc bag inventory.
 * Draws a vanilla-style slot grid for both the bag and player inventories.
 */
public class DiscBagScreen extends HandledScreen<DiscBagScreenHandler> {

    private static final Identifier SLOT_BACKGROUND = Identifier.of("minecraft", "container/slot");

    public DiscBagScreen(DiscBagScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.playerInventoryTitleY = 72;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;

        // Outer panel background and border
        context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, 0xFFC6C6C6);
        context.fill(x + 1, y + 1, x + this.backgroundWidth - 1, y + this.backgroundHeight - 1, 0xFF8B8B8B);

        // Draw the slot grid for both the bag and player inventories
        for (Slot slot : this.handler.slots) {
            context.drawGuiTexture(SLOT_BACKGROUND, x + slot.x, y + slot.y, 18, 18);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
