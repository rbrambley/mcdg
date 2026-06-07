package com.mcdg.client;

import com.mcdg.net.LeaderboardResponse;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class LeaderboardScreen extends Screen {
    private static final int BG_COLOR = 0xF0111820;
    private static final int HEADER_COLOR = 0xFF1B2D42;
    private static final int BORDER_COLOR = 0xFF3A5A7A;
    private static final int ROW_ALT_COLOR = 0x181B2D42;
    private static final int TEXT_TITLE = 0xFFD4E8FF;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFF8AAABB;
    private static final int TEXT_GOLD = 0xFFFFCC33;
    private static final int TEXT_GREEN = 0xFF57D163;
    private static final int TEXT_RED = 0xFFFF5555;

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 220;
    private static final int ROW_H = 20;

    private final String courseName;
    private final int totalPar;
    private final List<LeaderboardResponse.Entry> entries;

    public LeaderboardScreen(String courseName, int totalPar, List<LeaderboardResponse.Entry> entries) {
        super(Text.literal("Leaderboard"));
        this.courseName = courseName;
        this.totalPar = totalPar;
        this.entries = entries != null ? entries : List.of();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // Background
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        // Header
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + 28, HEADER_COLOR);
        // Border
        context.drawBorder(panelX, panelY, PANEL_W, PANEL_H, BORDER_COLOR);

        // Title
        String title = courseName.isEmpty() ? "Leaderboard" : courseName + " Leaderboard";
        context.drawTextWithShadow(textRenderer, title, panelX + 10, panelY + 9, TEXT_TITLE);

        // Par label
        String parLabel = "Par " + totalPar;
        int parWidth = textRenderer.getWidth(parLabel);
        context.drawTextWithShadow(textRenderer, parLabel, panelX + PANEL_W - parWidth - 10, panelY + 9, TEXT_GREEN);

        int contentY = panelY + 36;
        int contentX = panelX + 10;
        int rowWidth = PANEL_W - 20;

        if (entries.isEmpty()) {
            String emptyText = "No scores recorded yet.";
            int emptyWidth = textRenderer.getWidth(emptyText);
            context.drawTextWithShadow(textRenderer, emptyText, panelX + (PANEL_W - emptyWidth) / 2, contentY + 60, TEXT_MUTED);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardResponse.Entry entry = entries.get(i);
                int rowY = contentY + i * ROW_H;
                if (i % 2 == 1) {
                    context.fill(contentX, rowY, contentX + rowWidth, rowY + ROW_H, ROW_ALT_COLOR);
                }

                int rankColor = switch (i) {
                    case 0 -> TEXT_GOLD;
                    case 1 -> 0xFFCCCCCC;
                    case 2 -> 0xFFCD7F32;
                    default -> TEXT_WHITE;
                };

                String rankText = "#" + (i + 1);
                context.drawTextWithShadow(textRenderer, rankText, contentX + 6, rowY + 6, rankColor);

                int nameX = contentX + 36;
                context.drawTextWithShadow(textRenderer, entry.playerName(), nameX, rowY + 6, TEXT_WHITE);

                int deltaScore = entry.score() - totalPar;
                String scoreText = entry.score() + " (" + (deltaScore == 0 ? "E" : (deltaScore > 0 ? "+" + deltaScore : String.valueOf(deltaScore))) + ")";
                int scoreWidth = textRenderer.getWidth(scoreText);
                int scoreColor = deltaScore == 0 ? TEXT_WHITE : (deltaScore < 0 ? TEXT_GREEN : TEXT_RED);
                context.drawTextWithShadow(textRenderer, scoreText, contentX + rowWidth - scoreWidth - 6, rowY + 6, scoreColor);
            }
        }

        // Close hint
        String closeHint = "Press ESC to close";
        int hintWidth = textRenderer.getWidth(closeHint);
        context.drawTextWithShadow(textRenderer, closeHint, panelX + (PANEL_W - hintWidth) / 2, panelY + PANEL_H - 16, TEXT_MUTED);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void open(String courseName, int totalPar, List<LeaderboardResponse.Entry> entries) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(() -> client.setScreen(new LeaderboardScreen(courseName, totalPar, entries)));
        }
    }
}
