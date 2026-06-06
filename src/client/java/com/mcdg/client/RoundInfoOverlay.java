package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Renders the top-right round info panel (hole, par, distance, throws, total).
 * Manages its own tweened display values for smooth numeric transitions.
 */
public final class RoundInfoOverlay {
    private static float displayedDistanceFeet = Float.NaN;
    private static float displayedTotalStrokes = Float.NaN;
    private static float displayedCumulativeDelta = Float.NaN;

    private RoundInfoOverlay() {
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
        String line4 = "Total " + state.totalStrokes() + "  " + deltaText;
        int maxTextWidth = Math.max(
                Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2)),
                Math.max(client.textRenderer.getWidth(line3), client.textRenderer.getWidth(line4))
        );

        int panelW = maxTextWidth + 16;
        int panelH = 54;
        int x = drawContext.getScaledWindowWidth() - panelW - 8;
        int y = client.getDebugHud().shouldShowDebugHud() ? 76 : 8;

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Round", hudAlpha);

        int animatedTotal = Math.max(0, Math.round(displayedTotalStrokes));
        int animatedDelta = Math.round(displayedCumulativeDelta);
        String animatedDeltaText = animatedDelta == 0 ? "E" : (animatedDelta > 0 ? "+" + animatedDelta : Integer.toString(animatedDelta));
        String animatedLine4 = "Total " + animatedTotal + "  " + animatedDeltaText;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line2), x + 6, y + 16, HudUtil.withAlpha(0xFFFFFF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line3), x + 6, y + 28, HudUtil.withAlpha(0xCFE8FF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(animatedLine4), x + 6, y + 40, HudUtil.withAlpha(0xB5F7B5, hudAlpha));
    }
}
