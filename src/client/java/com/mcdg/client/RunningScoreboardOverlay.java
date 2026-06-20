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

    private RunningScoreboardOverlay() {
    }

    public static int getLastPanelWidth() {
        return lastPanelWidth;
    }

    public static void render(DrawContext drawContext, McdgClientMod.RunningRoundScoreState state, float hudAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        if (state == null || state.rows().isEmpty()) {
            return;
        }

        int focusHole = Math.max(1, Math.min(state.totalHoles(), state.focusHole()));
        int startHole = Math.max(1, focusHole - 2);
        int endHole = focusHole;
        int visibleHoleCount = Math.max(1, endHole - startHole + 1);
        int nameColW = client.textRenderer.getWidth("Player");
        int totalColW = client.textRenderer.getWidth("Tot");
        for (McdgClientMod.RunningRoundScoreRow row : state.rows()) {
            String displayName = row.online() ? row.playerName() : (row.playerName() + " (off)");
            nameColW = Math.max(nameColW, client.textRenderer.getWidth(displayName));
            totalColW = Math.max(totalColW, client.textRenderer.getWidth(Integer.toString(row.runningTotal())));
        }

        int holeColW = 12;
        int colGap = 6;
        int rowHeight = 10;
        int panelW = 8 + nameColW + colGap + (visibleHoleCount * (holeColW + 2)) + colGap + totalColW + 8;
        int panelH = 22 + ((state.rows().size() + 1) * rowHeight);
        lastPanelWidth = panelW;
        int x = 8;
        
        // Position in bottom third, anchored near bottom of screen
        int screenHeight = drawContext.getScaledWindowHeight();
        int bottomThirdStart = (2 * screenHeight) / 3;
        int y = screenHeight - panelH - HUD_SPACING;
        
        // Ensure it stays within bottom third
        if (y < bottomThirdStart + HUD_SPACING) {
            y = bottomThirdStart + HUD_SPACING;
        }

        String panelTitle = (state.courseName() != null && !state.courseName().isBlank()) ? state.courseName() : "Round Scores";
        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, panelTitle, hudAlpha);

        int cursorX = x + 6;
        int headerY = y + 14;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Player"), cursorX, headerY, HudUtil.withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));
        cursorX += nameColW + colGap;

        for (int hole = startHole; hole <= endHole; hole++) {
            String label = Integer.toString(hole);
            int color = hole == focusHole ? 0xFFEAC26F : HUD_CARD_MUTED_TEXT;
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(label),
                    cursorX + rightAlign(0, holeColW, client.textRenderer.getWidth(label)),
                    headerY,
                    HudUtil.withAlpha(color, hudAlpha)
            );
            cursorX += holeColW + 2;
        }

        cursorX += colGap;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Tot"), cursorX, headerY, HudUtil.withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));

        for (int rowIndex = 0; rowIndex < state.rows().size(); rowIndex++) {
            McdgClientMod.RunningRoundScoreRow row = state.rows().get(rowIndex);
            int rowY = y + 24 + (rowIndex * rowHeight);
            int rowColor = row.online() ? HUD_CARD_TEXT : HUD_CARD_MUTED_TEXT;

            String displayName = row.online() ? row.playerName() : (row.playerName() + " (off)");
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(displayName), x + 6, rowY, HudUtil.withAlpha(rowColor, hudAlpha));

            int rowCursorX = x + 6 + nameColW + colGap;
            for (int hole = startHole; hole <= endHole; hole++) {
                int value = (hole - 1) < row.holeScores().size() ? row.holeScores().get(hole - 1) : -1;
                String text = value < 0 ? "-" : Integer.toString(value);
                int valueColor = hole == focusHole ? 0xFFF5D684 : rowColor;
                drawContext.drawTextWithShadow(
                        client.textRenderer,
                        Text.literal(text),
                        rowCursorX + rightAlign(0, holeColW, client.textRenderer.getWidth(text)),
                        rowY,
                        HudUtil.withAlpha(valueColor, hudAlpha)
                );
                rowCursorX += holeColW + 2;
            }

            rowCursorX += colGap;
            String totalText = Integer.toString(row.runningTotal());
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(totalText), rowCursorX, rowY, HudUtil.withAlpha(0xFFB5F7B5, hudAlpha));
        }
    }

    private static int rightAlign(int startX, int width, int textWidth) {
        return startX + Math.max(0, width - textWidth);
    }
}
