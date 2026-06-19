package com.mcdg.client;

import com.mcdg.data.SignatureHoleType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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

    private static final int PANEL_H = 260;
    private static final int HEADER_H = 20;
    private static final int FOOTER_H = 36;
    private static final int MAP_MARGIN = 3;
    private static final int DEFAULT_PANEL_W = 160;

    private static boolean visible = false;

    private HoleMapOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void toggle() {
        visible = !visible;
    }

    public static void setVisible(boolean v) {
        visible = v;
    }

    public static void render(DrawContext ctx, MinecraftClient client, float hudAlpha) {
        if (!visible || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        HoleMapState state = McdgClientMod.getHoleMapState();
        if (state == null || !state.isActive()) {
            return;
        }

        // Match width to running scoreboard so the two left-side HUDs align visually.
        int panelW = RunningScoreboardOverlay.getLastPanelWidth();
        if (panelW <= 0) {
            panelW = DEFAULT_PANEL_W;
        }

        int panelX = 8;
        int panelY = (ctx.getScaledWindowHeight() - PANEL_H) / 2;
        int safeBottom = ctx.getScaledWindowHeight() - 90;
        if (panelY + PANEL_H > safeBottom) {
            panelY = safeBottom - PANEL_H;
        }
        panelY = Math.max(8, panelY);

        // Panel background
        ctx.fill(panelX, panelY, panelX + panelW, panelY + PANEL_H, HudUtil.withAlpha(BG_COLOR, hudAlpha));
        ctx.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, HudUtil.withAlpha(HEADER_COLOR, hudAlpha));
        ctx.drawBorder(panelX, panelY, panelW, PANEL_H, HudUtil.withAlpha(BORDER_COLOR, hudAlpha));

        // Header text
        String title = "Hole " + state.holeIndex;
        ctx.drawTextWithShadow(client.textRenderer, title, panelX + 6, panelY + 5, HudUtil.withAlpha(TEXT_TITLE, hudAlpha));

        String parDist = "P" + state.par + "  " + state.distanceFeet + "ft";
        int pdw = client.textRenderer.getWidth(parDist);
        ctx.drawTextWithShadow(client.textRenderer, parDist, panelX + panelW - pdw - 6, panelY + 5, HudUtil.withAlpha(TEXT_GREEN, hudAlpha));

        // Signature label (only if there is room)
        SignatureHoleType sig = SignatureHoleType.values()[Math.max(0, Math.min(SignatureHoleType.values().length - 1, state.signatureTypeOrdinal))];
        if (sig != SignatureHoleType.NONE) {
            String sigText = sig.displayName();
            int sigW = client.textRenderer.getWidth(sigText);
            int titleW = client.textRenderer.getWidth(title);
            if (sigW + 12 + titleW + 12 + pdw < panelW) {
                ctx.drawTextWithShadow(client.textRenderer, sigText, panelX + (panelW - sigW) / 2, panelY + 5, HudUtil.withAlpha(TEXT_GOLD, hudAlpha));
            }
        }

        // Map canvas
        float mapX = panelX + MAP_MARGIN;
        float mapY = panelY + HEADER_H + 2;
        float mapW = panelW - MAP_MARGIN * 2;
        float mapH = PANEL_H - HEADER_H - FOOTER_H - 4;

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
        int footerTop = panelY + PANEL_H - FOOTER_H;
        int row = footerTop + 4;

        String throwLine = "Throw " + state.throwNumber + "  Strokes " + state.totalStrokes;
        if (state.cumulativeParDelta != 0) {
            String deltaStr = state.cumulativeParDelta > 0 ? "+" + state.cumulativeParDelta : String.valueOf(state.cumulativeParDelta);
            throwLine += " (" + deltaStr + ")";
        }
        int tlw = client.textRenderer.getWidth(throwLine);
        ctx.drawTextWithShadow(client.textRenderer, throwLine, panelX + (panelW - tlw) / 2, row, HudUtil.withAlpha(TEXT_WHITE, hudAlpha));
        row += 10;

        if (state.hasWaterGap && state.waterGapStartFeet > 0) {
            String waterLine = "Water " + state.waterGapStartFeet + "-" + state.waterGapEndFeet + "ft";
            int wlw = client.textRenderer.getWidth(waterLine);
            ctx.drawTextWithShadow(client.textRenderer, waterLine, panelX + (panelW - wlw) / 2, row, HudUtil.withAlpha(0xFF66CCFF, hudAlpha));
            row += 10;
        }

        String hint = "Press " + ClientKeybinds.getHoleMapKeyText().getString() + " to close";
        int hw = client.textRenderer.getWidth(hint);
        ctx.drawTextWithShadow(client.textRenderer, hint, panelX + (panelW - hw) / 2, row, HudUtil.withAlpha(TEXT_MUTED, hudAlpha));
    }
}
