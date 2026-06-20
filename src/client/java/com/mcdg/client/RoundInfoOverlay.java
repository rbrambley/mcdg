package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Renders the top-right round info panel (hole, par, distance, throws, total).
 * Manages its own tweened display values for smooth numeric transitions.
 */
public final class RoundInfoOverlay {
    private static final String[] COMPASS_8 = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

    private static float displayedDistanceFeet = Float.NaN;
    private static float displayedTotalStrokes = Float.NaN;
    private static float displayedCumulativeDelta = Float.NaN;
    private static int lastPanelHeight = 54;
    private static int lastPanelWidth = 120;
    private static int sharedHudPanelWidth = 120;

    private RoundInfoOverlay() {
    }

    public static int getSharedPanelWidth() {
        return sharedHudPanelWidth;
    }

    public static void setSharedPanelWidth(int width) {
        sharedHudPanelWidth = Math.max(80, width);
    }

    private static String buildWaterGapLine(int startFeet, int endFeet, boolean hasGap) {
        if (!hasGap || startFeet < 0 || endFeet <= startFeet) return "";
        return "Water " + startFeet + "-" + endFeet + "ft";
    }

    private static String buildCorridorEntryLine(int feet, int bearingDeg) {
        if (feet <= 0) return "";
        int idx = (int) Math.round(((bearingDeg % 360 + 360) % 360) / 45.0) % 8;
        return "Fairway " + feet + "ft " + COMPASS_8[idx];
    }

    public static void updateTweens(HoleMapState state) {
        if (state == null) {
            return;
        }
        int dx = state.basketX - state.lieX;
        int dz = state.basketZ - state.lieZ;
        float targetMeters = Math.max(0, Math.round((float) Math.sqrt((dx * dx) + (dz * dz))));
        float targetFeet = Math.max(0, Math.round(targetMeters * 3.28084f));
        displayedDistanceFeet = HudUtil.tween(displayedDistanceFeet, targetFeet, 0.18f);
        displayedTotalStrokes = HudUtil.tween(displayedTotalStrokes, state.totalStrokes, 0.22f);
        displayedCumulativeDelta = HudUtil.tween(displayedCumulativeDelta, state.cumulativeParDelta, 0.22f);
    }

    public static void render(DrawContext drawContext, HoleMapState state, float hudAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        if (state == null) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);

        String deltaText;
        if (state.cumulativeParDelta == 0) {
            deltaText = "E";
        } else if (state.cumulativeParDelta > 0) {
            deltaText = "+" + state.cumulativeParDelta;
        } else {
            deltaText = Integer.toString(state.cumulativeParDelta);
        }

        String line1 = "Round";
        String line2 = "H" + state.holeIndex + "  P" + state.par + "  T" + state.throwNumber;
        int animatedDistanceFeet = Math.max(0, Math.round(displayedDistanceFeet));
        String line3 = animatedDistanceFeet + "ft";
        String line4 = state.lastThrowDistanceFeet > 0 ? "Last " + state.lastThrowDistanceFeet + "ft" : "";
        String line5 = buildCorridorEntryLine(state.corridorEntryFeet, state.corridorEntryBearing);
        String line5b = buildWaterGapLine(state.waterGapStartFeet, state.waterGapEndFeet, state.hasWaterGap);
        String line6 = "Total " + state.totalStrokes + "  " + deltaText;
        int maxTextWidth = Math.max(
                Math.max(Math.round(client.textRenderer.getWidth(line1) * scale), Math.round(client.textRenderer.getWidth(line2) * scale)),
                Math.max(Math.round(client.textRenderer.getWidth(line3) * scale),
                        Math.max(Math.round(client.textRenderer.getWidth(line4) * scale),
                                Math.max(Math.round(client.textRenderer.getWidth(line5) * scale),
                                        Math.max(Math.round(client.textRenderer.getWidth(line5b) * scale), Math.round(client.textRenderer.getWidth(line6) * scale)))))
        );

        int extraRows = (line4.isEmpty() ? 0 : 1) + (line5.isEmpty() ? 0 : 1) + (line5b.isEmpty() ? 0 : 1);
        int panelW = Math.max(maxTextWidth + Math.round(16 * scale), sharedHudPanelWidth);
        int panelH = Math.round(54 * scale) + (extraRows * Math.round(12 * scale));
        lastPanelHeight = panelH;
        lastPanelWidth = panelW;
        sharedHudPanelWidth = panelW;
        int x = drawContext.getScaledWindowWidth() - panelW - Math.round(8 * scale);
        int y = client.getDebugHud().shouldShowDebugHud() ? Math.round(76 * scale) : Math.round(8 * scale);

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Round", hudAlpha);

        int animatedTotal = Math.max(0, Math.round(displayedTotalStrokes));
        int animatedDelta = Math.round(displayedCumulativeDelta);
        String animatedDeltaText = animatedDelta == 0 ? "E" : (animatedDelta > 0 ? "+" + animatedDelta : Integer.toString(animatedDelta));
        String animatedLine6 = "Total " + animatedTotal + "  " + animatedDeltaText;
        int row = y + Math.round(16 * scale);
        int rowSpacing = Math.round(12 * scale);
        int textMargin = Math.round(6 * scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(line2), x + textMargin, row, HudUtil.withAlpha(0xFFFFFF, hudAlpha), scale);
        row += rowSpacing;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(line3), x + textMargin, row, HudUtil.withAlpha(0xCFE8FF, hudAlpha), scale);
        row += rowSpacing;
        if (!line4.isEmpty()) {
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(line4), x + textMargin, row, HudUtil.withAlpha(0x99BBDD, hudAlpha), scale);
            row += rowSpacing;
        }
        if (!line5.isEmpty()) {
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(line5), x + textMargin, row, HudUtil.withAlpha(0xFFCC44, hudAlpha), scale);
            row += rowSpacing;
        }
        if (!line5b.isEmpty()) {
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(line5b), x + textMargin, row, HudUtil.withAlpha(0x66CCFF, hudAlpha), scale);
            row += rowSpacing;
        }
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(animatedLine6), x + textMargin, row, HudUtil.withAlpha(0xB5F7B5, hudAlpha), scale);
    }

    public static int getLastPanelHeight() {
        return lastPanelHeight;
    }

    public static int getLastPanelWidth() {
        return lastPanelWidth;
    }
}
