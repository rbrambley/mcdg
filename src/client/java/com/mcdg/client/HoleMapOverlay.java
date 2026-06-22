package com.mcdg.client;

import com.mcdg.data.SignatureHoleType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Left-side hole map HUD overlay.
 * Toggled with the hole map keybind while a round is active.
 * Does NOT steal focus or blur the game background.
 */
public final class HoleMapOverlay {

    private static final int BG_COLOR = 0xF0111820;
    private static final int HEADER_COLOR = 0xFF1B2D42;
    private static final int BORDER_COLOR = 0xFF3A5A7A;
    private static final int TEXT_TITLE = 0xFFD4E8FF;
    private static final int TEXT_MUTED = 0xFF8AAABB;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GREEN = 0xFF57D163;
    private static final int TEXT_GOLD = 0xFFFFCC33;

    // Unscaled constants
    private static final int HEADER_H = 14;
    private static final int FOOTER_H = 22;
    private static final int MAP_MARGIN = 2;
    // Fixed panel width in unscaled pixels — no longer estimated from Xaero
    private static final int PANEL_W = 160;
    private static final int MAX_PANEL_H = 400;
    private static final int MIN_PANEL_H = 120;

    private static boolean visible = false;
    private static int lastRenderedY = -1;
    private static int lastRenderedHeight = -1;

    private HoleMapOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    /**
     * Returns the Y coordinate where the hole map was last rendered, or -1 if not visible.
     */
    public static int getLastRenderedY() {
        return visible ? lastRenderedY : -1;
    }

    /**
     * Returns the height of the last rendered hole map panel.
     */
    public static int getLastRenderedHeight() {
        return visible ? lastRenderedHeight : 0;
    }

    public static void toggle() {
        visible = !visible;
    }

    public static void setVisible(boolean v) {
        visible = v;
    }

    public static void render(DrawContext ctx, MinecraftClient client, float hudAlpha, LeftSideHudLayout layout) {
        if (!visible || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        float scale = layout.getScale();
        HoleMapState state = McdgClientMod.getHoleMapState();
        if (state == null || !state.isActive()) {
            return;
        }

        int panelW = Math.round(PANEL_W * scale);
        int panelX = layout.getLeftX();

        // Available vertical space in the layout (layout already starts at top margin)
        int availableHeight = layout.getRemainingHeight();
        if (availableHeight <= 0) {
            // No room on screen — skip rendering entirely
            lastRenderedY = -1;
            lastRenderedHeight = -1;
            return;
        }

        // Dynamic panel height: fill available space within min/max bounds, never exceed screen
        int maxH = Math.round(MAX_PANEL_H * scale);
        int panelH = Math.min(availableHeight, maxH);

        // Allocate position — pass the already-scaled height
        int panelY = layout.allocateScaled(panelH);

        // Track rendered position for external queries
        lastRenderedY = panelY;
        lastRenderedHeight = panelH;

        // Panel background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, HudUtil.withAlpha(BG_COLOR, hudAlpha));
        int scaledHeaderH = Math.round(HEADER_H * scale);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + scaledHeaderH, HudUtil.withAlpha(HEADER_COLOR, hudAlpha));
        ctx.drawBorder(panelX, panelY, panelW, panelH, HudUtil.withAlpha(BORDER_COLOR, hudAlpha));

        // Header text
        String title = "Hole " + state.holeIndex;
        int headerTextMargin = Math.round(6 * scale);
        int headerTextYOffset = Math.round(4 * scale);
        HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(title), panelX + headerTextMargin, panelY + headerTextYOffset, HudUtil.withAlpha(TEXT_TITLE, hudAlpha), scale);

        String parDist = "P" + state.par + "  " + state.distanceFeet + "ft";
        int pdw = Math.round(client.textRenderer.getWidth(parDist) * scale);
        HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(parDist), panelX + panelW - pdw - headerTextMargin, panelY + headerTextYOffset, HudUtil.withAlpha(TEXT_GREEN, hudAlpha), scale);

        // Signature label (only if there is room)
        SignatureHoleType sig = SignatureHoleType.values()[Math.max(0, Math.min(SignatureHoleType.values().length - 1, state.signatureTypeOrdinal))];
        if (sig != SignatureHoleType.NONE) {
            String sigText = sig.displayName();
            int sigW = Math.round(client.textRenderer.getWidth(sigText) * scale);
            int titleW = Math.round(client.textRenderer.getWidth(title) * scale);
            int sigSpacing = Math.round(10 * scale);
            if (sigW + sigSpacing + titleW + sigSpacing + pdw < panelW) {
                HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(sigText), panelX + (panelW - sigW) / 2, panelY + headerTextYOffset, HudUtil.withAlpha(TEXT_GOLD, hudAlpha), scale);
            }
        }

        // Map canvas
        int scaledMapMargin = Math.round(MAP_MARGIN * scale);
        float mapX = panelX + scaledMapMargin;
        float mapY = panelY + scaledHeaderH + Math.round(2 * scale);
        float mapW = panelW - scaledMapMargin * 2;
        int scaledFooterH = Math.round(FOOTER_H * scale);
        float mapH = panelH - scaledHeaderH - scaledFooterH - Math.round(4 * scale);

        HoleMapRenderer.MapTransform transform = HoleMapRenderer.computeTransform(state, mapX, mapY, mapW, mapH);

        // Clip to map area
        ctx.enableScissor((int) mapX, (int) mapY, (int) (mapX + mapW), (int) (mapY + mapH));

        HoleMapRenderer.drawMapBackground(ctx, transform);
        HoleMapRenderer.drawFairwaySegments(ctx, transform, state.fairwaySegments);
        HoleMapRenderer.drawHazardOverlay(ctx, transform, state);
        HoleMapRenderer.drawWaterGap(ctx, transform, state);
        HoleMapRenderer.drawGreen(ctx, transform, state.basketX, state.basketZ);
        HoleMapRenderer.drawCorridorLine(ctx, transform, state.teeX, state.teeZ, state.basketX, state.basketZ);
        HoleMapRenderer.drawTeeMarker(ctx, transform, state.teeX, state.teeZ);
        HoleMapRenderer.drawBasketMarker(ctx, transform, state.basketX, state.basketZ);
        HoleMapRenderer.drawPlayerMarker(ctx, transform, state.lieX, state.lieZ, state.headingYaw);

        ctx.disableScissor();

        // Map border
        ctx.drawBorder((int) mapX, (int) mapY, (int) mapW, (int) mapH, HudUtil.withAlpha(BORDER_COLOR, hudAlpha));

        // Footer info
        int footerTop = panelY + panelH - scaledFooterH;
        int row = footerTop + Math.round(3 * scale);
        int rowSpacing = Math.round(9 * scale);

        String throwLine = "Throw " + state.throwNumber + "  Strokes " + state.totalStrokes;
        if (state.cumulativeParDelta != 0) {
            String deltaStr = state.cumulativeParDelta > 0 ? "+" + state.cumulativeParDelta : String.valueOf(state.cumulativeParDelta);
            throwLine += " (" + deltaStr + ")";
        }
        int tlw = Math.round(client.textRenderer.getWidth(throwLine) * scale);
        HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(throwLine), panelX + (panelW - tlw) / 2, row, HudUtil.withAlpha(TEXT_WHITE, hudAlpha), scale);
        row += rowSpacing;

        if (state.hasWaterGap && state.waterGapStartFeet > 0) {
            String waterLine = "Water " + state.waterGapStartFeet + "-" + state.waterGapEndFeet + "ft";
            int wlw = Math.round(client.textRenderer.getWidth(waterLine) * scale);
            HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(waterLine), panelX + (panelW - wlw) / 2, row, HudUtil.withAlpha(0xFF66CCFF, hudAlpha), scale);
            row += rowSpacing;
        }

        String hint = "Press " + ClientKeybinds.getHoleMapKeyText().getString() + " to close";
        int hw = Math.round(client.textRenderer.getWidth(hint) * scale);
        HudUtil.drawScaledText(ctx, client.textRenderer, net.minecraft.text.Text.literal(hint), panelX + (panelW - hw) / 2, row, HudUtil.withAlpha(TEXT_MUTED, hudAlpha), scale);
    }
}
