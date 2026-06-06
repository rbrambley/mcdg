package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared HUD rendering utilities used by multiple overlay renderers.
 */
public final class HudUtil {
    private static final int HUD_CARD_BG = 0xA5121822;
    private static final int HUD_CARD_BORDER = 0xA63A4E66;
    private static final int HUD_CARD_HEADER_BG = 0xB01B2638;

    private HudUtil() {
    }

    public static int withAlpha(int argb, float alphaFactor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int appliedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaFactor)));
        return (argb & 0x00FFFFFF) | (appliedAlpha << 24);
    }

    public static void drawCard(DrawContext drawContext, MinecraftClient client, int x, int y, int w, int h, String title, float alpha) {
        drawContext.fill(x, y, x + w, y + h, withAlpha(HUD_CARD_BG, alpha));
        drawContext.fill(x, y, x + w, y + 12, withAlpha(HUD_CARD_HEADER_BG, alpha));
        drawContext.fill(x, y, x + w, y + 1, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y + h - 1, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y, x + 1, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x + w - 1, y, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        if (title != null && client.textRenderer != null) {
            drawContext.drawTextWithShadow(client.textRenderer, net.minecraft.text.Text.literal(title), x + 4, y + 2, withAlpha(0xE8EEF7, alpha));
        }
    }
}
