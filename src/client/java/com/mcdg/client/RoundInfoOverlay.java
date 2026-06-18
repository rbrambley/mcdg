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

    private RoundInfoOverlay() {
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

    public static void updateTweens(McdgClientMod.MiniMapState state) {
        if (state == null) {
            return;
        }
        int dx = state.basketX() - state.lieX();
        int dz = state.basketZ() - state.lieZ();
        float targetMeters = Math.max(0, Math.round((float) Math.sqrt((dx * dx) + (dz * dz))));
        float targetFeet = Math.max(0, Math.round(targetMeters * 3.28084f));
        displayedDistanceFeet = HudUtil.tween(displayedDistanceFeet, targetFeet, 0.18f);
        displayedTotalStrokes = HudUtil.tween(displayedTotalStrokes, state.totalStrokes(), 0.22f);
        displayedCumulativeDelta = HudUtil.tween(displayedCumulativeDelta, state.cumulativeParDelta(), 0.22f);
    }

    public static void render(DrawContext drawContext, McdgClientMod.MiniMapState state, float hudAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        if (state == null) {
            return;
        }

        String deltaText;
        if (state.cumulativeParDelta() == 0) {
            deltaText = "E";
        } else if (state.cumulativeParDelta() > 0) {
            deltaText = "+" + state.cumulativeParDelta();
        } else {
            deltaText = Integer.toString(state.cumulativeParDelta());
        }

        String line1 = "Round";
        String line2 = "H" + state.holeIndex() + "  P" + state.par() + "  T" + state.throwNumber();
        int animatedDistanceFeet = Math.max(0, Math.round(displayedDistanceFeet));
        String line3 = animatedDistanceFeet + "ft";
        String line4 = state.lastThrowDistanceFeet() > 0 ? "Last " + state.lastThrowDistanceFeet() + "ft" : "";
        String line5 = buildCorridorEntryLine(state.corridorEntryFeet(), state.corridorEntryBearing());
        String line5b = buildWaterGapLine(state.waterGapStartFeet(), state.waterGapEndFeet(), state.hasWaterGap());
        String line6 = "Total " + state.totalStrokes() + "  " + deltaText;
        int maxTextWidth = Math.max(
                Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2)),
                Math.max(client.textRenderer.getWidth(line3),
                        Math.max(client.textRenderer.getWidth(line4),
                                Math.max(client.textRenderer.getWidth(line5),
                                        Math.max(client.textRenderer.getWidth(line5b), client.textRenderer.getWidth(line6)))))
        );

        int extraRows = (line4.isEmpty() ? 0 : 1) + (line5.isEmpty() ? 0 : 1) + (line5b.isEmpty() ? 0 : 1);
        int panelW = maxTextWidth + 16;
        int panelH = 54 + (extraRows * 12);
        lastPanelHeight = panelH;
        lastPanelWidth = panelW;
        int x = drawContext.getScaledWindowWidth() - panelW - 8;
        int y = client.getDebugHud().shouldShowDebugHud() ? 76 : 8;

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Round", hudAlpha);

        int animatedTotal = Math.max(0, Math.round(displayedTotalStrokes));
        int animatedDelta = Math.round(displayedCumulativeDelta);
        String animatedDeltaText = animatedDelta == 0 ? "E" : (animatedDelta > 0 ? "+" + animatedDelta : Integer.toString(animatedDelta));
        String animatedLine6 = "Total " + animatedTotal + "  " + animatedDeltaText;
        int row = y + 16;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line2), x + 6, row, HudUtil.withAlpha(0xFFFFFF, hudAlpha));
        row += 12;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line3), x + 6, row, HudUtil.withAlpha(0xCFE8FF, hudAlpha));
        row += 12;
        if (!line4.isEmpty()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line4), x + 6, row, HudUtil.withAlpha(0x99BBDD, hudAlpha));
            row += 12;
        }
        if (!line5.isEmpty()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line5), x + 6, row, HudUtil.withAlpha(0xFFCC44, hudAlpha));
            row += 12;
        }
        if (!line5b.isEmpty()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line5b), x + 6, row, HudUtil.withAlpha(0x66CCFF, hudAlpha));
            row += 12;
        }
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(animatedLine6), x + 6, row, HudUtil.withAlpha(0xB5F7B5, hudAlpha));
    }

    public static int getLastPanelHeight() {
        return lastPanelHeight;
    }

    public static int getLastPanelWidth() {
        return lastPanelWidth;
    }
}
