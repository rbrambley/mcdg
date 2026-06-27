package com.mcdg.client;

import com.mcdg.game.McdgItems;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Renders the scorecard item tooltip when the player is looking at an item frame
 * that holds a scorecard. This lets mounted souvenir scorecards be inspected by
 * any player just by putting their crosshair on the frame.
 */
public final class ScorecardFrameTooltipRenderer {
    private ScorecardFrameTooltipRenderer() {
    }

    public static void render(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        if (client.player == null || client.options.hudHidden || client.textRenderer == null || client.world == null) {
            return;
        }

        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHitResult = (EntityHitResult) hitResult;
        if (!(entityHitResult.getEntity() instanceof ItemFrameEntity itemFrame)) {
            return;
        }

        ItemStack held = itemFrame.getHeldItemStack();
        if (held.isEmpty() || !held.isOf(McdgItems.SCORECARD)) {
            return;
        }

        List<Text> tooltip = held.getTooltip(
                Item.TooltipContext.create(client.world),
                client.player,
                TooltipType.BASIC
        );

        if (tooltip.isEmpty()) {
            return;
        }

        int centerX = drawContext.getScaledWindowWidth() / 2;
        int centerY = drawContext.getScaledWindowHeight() / 2;
        int offset = Math.round(8 * HudUtil.getScaleFactor(drawContext));
        drawContext.drawTooltip(client.textRenderer, tooltip, centerX + offset, centerY + offset);
    }
}
