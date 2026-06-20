package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Renders the bottom-left running round scoreboard overlay.
 */
public final class RunningScoreboardOverlay {
    private static final int HUD_CARD_TEXT = 0xE8EEF7;
    private static final int HUD_CARD_MUTED_TEXT = 0xAAB8CC;
    private static final int HUD_SPACING = 8;
    private static int lastPanelWidth = 0;
    private static int lastPanelHeight = 0;

    private RunningScoreboardOverlay() {
    }

    public static int getLastPanelWidth() {
        return lastPanelWidth;
    }

    public static int getLastPanelHeight() {
        return lastPanelHeight;
    }

    public static void render(DrawContext drawContext, McdgClientMod.RunningRoundScoreState state, float hudAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        if (state == null || state.rows().isEmpty()) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);
        int focusHole = Math.max(1, Math.min(state.totalHoles(), state.focusHole()));
        int startHole = Math.max(1, focusHole - 2);
        int endHole = focusHole;
        int visibleHoleCount = Math.max(1, endHole - startHole + 1);
        
        // Use Xaero's minimap width for exact alignment
        int panelW = HudUtil.getXaeroMinimapWidth();
        
        // Calculate available width for content (subtract margins)
        int contentMargin = Math.round(16 * scale); // 8px margin on each side
        int availableContentWidth = panelW - contentMargin;
        
        // Distribute width: 40% names, 15% totals, remaining to hole columns
        int nameColW = Math.round(availableContentWidth * 0.4f);
        int totalColW = Math.round(availableContentWidth * 0.15f);
        int colGap = Math.round(6 * scale);
        int remainingWidth = availableContentWidth - nameColW - totalColW - (colGap * 2);
        int holeColW = Math.max(Math.round(12 * scale), remainingWidth / visibleHoleCount);
        int rowHeight = Math.round(10 * scale);
        
        int panelH = Math.round(22 * scale) + ((state.rows().size() + 1) * rowHeight);
        lastPanelWidth = panelW;
        lastPanelHeight = panelH;
        
        // Center horizontally with Xaero's minimap (same left position as Xaero)
        int xaeroMargin = Math.round(8 * scale);
        int x = xaeroMargin;
        
        // Position in bottom third, anchored near bottom of screen
        int screenHeight = drawContext.getScaledWindowHeight();
        int bottomThirdStart = (2 * screenHeight) / 3;
        int scaledSpacing = Math.round(HUD_SPACING * scale);
        int y = screenHeight - panelH - scaledSpacing;
        
        // Ensure it stays within bottom third
        if (y < bottomThirdStart + scaledSpacing) {
            y = bottomThirdStart + scaledSpacing;
        }

        String panelTitle = (state.courseName() != null && !state.courseName().isBlank()) ? state.courseName() : "Round Scores";
        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, panelTitle, hudAlpha);

        int cursorX = x + Math.round(6 * scale);
        int headerY = y + Math.round(14 * scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("Player"), cursorX, headerY, HudUtil.withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha), scale);
        cursorX += nameColW + colGap;

        for (int hole = startHole; hole <= endHole; hole++) {
            String label = Integer.toString(hole);
            int color = hole == focusHole ? 0xFFEAC26F : HUD_CARD_MUTED_TEXT;
            int labelWidth = Math.round(client.textRenderer.getWidth(label) * scale);
            HudUtil.drawScaledText(
                    drawContext,
                    client.textRenderer,
                    Text.literal(label),
                    cursorX + rightAlign(0, holeColW, labelWidth),
                    headerY,
                    HudUtil.withAlpha(color, hudAlpha),
                    scale
            );
            cursorX += holeColW + Math.round(2 * scale);
        }

        cursorX += colGap;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("Tot"), cursorX, headerY, HudUtil.withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha), scale);

        for (int rowIndex = 0; rowIndex < state.rows().size(); rowIndex++) {
            McdgClientMod.RunningRoundScoreRow row = state.rows().get(rowIndex);
            int rowY = y + Math.round(24 * scale) + (rowIndex * rowHeight);
            int rowColor = row.online() ? HUD_CARD_TEXT : HUD_CARD_MUTED_TEXT;

            String displayName = row.online() ? row.playerName() : (row.playerName() + " (off)");
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(displayName), x + Math.round(6 * scale), rowY, HudUtil.withAlpha(rowColor, hudAlpha), scale);

            int rowCursorX = x + Math.round(6 * scale) + nameColW + colGap;
            for (int hole = startHole; hole <= endHole; hole++) {
                int value = (hole - 1) < row.holeScores().size() ? row.holeScores().get(hole - 1) : -1;
                String text = value < 0 ? "-" : Integer.toString(value);
                int valueColor = hole == focusHole ? 0xFFF5D684 : rowColor;
                int textWidth = Math.round(client.textRenderer.getWidth(text) * scale);
                HudUtil.drawScaledText(
                        drawContext,
                        client.textRenderer,
                        Text.literal(text),
                        rowCursorX + rightAlign(0, holeColW, textWidth),
                        rowY,
                        HudUtil.withAlpha(valueColor, hudAlpha),
                        scale
                );
                rowCursorX += holeColW + Math.round(2 * scale);
            }

            rowCursorX += colGap;
            String totalText = Integer.toString(row.runningTotal());
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(totalText), rowCursorX, rowY, HudUtil.withAlpha(0xFFB5F7B5, hudAlpha), scale);
        }
    }

    private static int rightAlign(int startX, int width, int textWidth) {
        return startX + Math.max(0, width - textWidth);
    }
}
