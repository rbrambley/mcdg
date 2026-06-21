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
    
    // Baseline resolution for scaling calculations (1920x1080)
    private static final float BASELINE_WIDTH = 1920f;
    private static final float BASELINE_HEIGHT = 1080f;
    
    // Scale factor bounds to ensure readability
    private static final float MIN_SCALE = 0.6f;
    private static final float MAX_SCALE = 1.5f;
    
    // Xaero's Minimap integration
    private static final int DEFAULT_PANEL_WIDTH = 120;
    private static int cachedXaeroWidth = -1;
    private static boolean xaeroWidthCalculated = false;

    private HudUtil() {
    }

    public static int withAlpha(int argb, float alphaFactor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int appliedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaFactor)));
        return (argb & 0x00FFFFFF) | (appliedAlpha << 24);
    }

    public static float tween(float current, float target, float factor) {
        if (Float.isNaN(current)) {
            return target;
        }
        return current + ((target - current) * factor);
    }

    public static void drawCard(DrawContext drawContext, MinecraftClient client, int x, int y, int w, int h, String title, float alpha) {
        float scale = getScaleFactor(drawContext);
        int headerHeight = Math.round(12 * scale);
        drawContext.fill(x, y, x + w, y + h, withAlpha(HUD_CARD_BG, alpha));
        drawContext.fill(x, y, x + w, y + headerHeight, withAlpha(HUD_CARD_HEADER_BG, alpha));
        drawContext.fill(x, y, x + w, y + 1, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y + h - 1, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y, x + 1, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x + w - 1, y, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        if (title != null && client.textRenderer != null) {
            int textMargin = Math.round(4 * scale);
            int textYOffset = Math.round(2 * scale);
            drawScaledText(drawContext, client.textRenderer, net.minecraft.text.Text.literal(title), x + textMargin, y + textYOffset, withAlpha(0xE8EEF7, alpha), scale);
        }
    }

    /**
     * Draw scaled text using matrix transformations.
     * Text is rendered at (x, y) with the given scale factor applied.
     */
    public static void drawScaledText(DrawContext drawContext, net.minecraft.client.font.TextRenderer renderer, net.minecraft.text.Text text, int x, int y, int color, float scale) {
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(x, y, 0);
        drawContext.getMatrices().scale(scale, scale, 1.0f);
        drawContext.drawTextWithShadow(renderer, text, 0, 0, color);
        drawContext.getMatrices().pop();
    }
    
    /**
     * Calculate the global scale factor based on current screen resolution.
     * Uses the larger dimension to provide better scaling for tall/wide screens.
     * Clamped between MIN_SCALE and MAX_SCALE to ensure readability.
     */
    public static float getScaleFactor(DrawContext drawContext) {
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        
        float widthScale = width / BASELINE_WIDTH;
        float heightScale = height / BASELINE_HEIGHT;
        
        // Use the larger scale to provide better scaling for tall/wide screens
        float scale = Math.max(widthScale, heightScale);
        
        // Clamp to bounds
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
    
    /**
     * Scale a dimension by the current scale factor.
     */
    public static int scale(DrawContext drawContext, int value) {
        return Math.round(value * getScaleFactor(drawContext));
    }
    
    /**
     * Scale a dimension by the current scale factor, returning a float.
     */
    public static float scaleFloat(DrawContext drawContext, float value) {
        return value * getScaleFactor(drawContext);
    }
    
    /**
     * Get the width of Xaero's Minimap, or a reasonable default if not available.
     * This is calculated once and cached for the session.
     */
    public static int getXaeroMinimapWidth() {
        if (!xaeroWidthCalculated) {
            cachedXaeroWidth = estimateXaeroWidth();
            xaeroWidthCalculated = true;
        }
        return cachedXaeroWidth;
    }
    
    /**
     * Force recalculation of Xaero's Minimap width.
     * Call this when screen resolution changes or a round starts/resumes.
     */
    public static void recalculateXaeroWidth() {
        xaeroWidthCalculated = false;
    }
    
    /**
     * Estimate Xaero's Minimap width based on screen resolution.
     * Xaero's minimap is typically 10-15% of screen width in default settings.
     */
    private static int estimateXaeroWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            int screenWidth = client.getWindow().getScaledWidth();
            // Xaero's minimap is typically 10-15% of screen width
            // Use 12% as a reasonable default
            return Math.round(screenWidth * 0.12f);
        }
        
        return DEFAULT_PANEL_WIDTH;
    }
}
