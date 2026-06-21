package com.mcdg.client;

import com.mcdg.game.McdgItems;
import com.mcdg.game.ScorecardManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

/**
 * Renders the on-screen scorecard panel drawn from the player's scorecard item NBT.
 */
public final class ScorecardOverlay {
    private static final long STALE_TIMEOUT_MS = 15000L;
    private static boolean throwStatsRenderedThisFrame = false;

    private ScorecardOverlay() {
    }

    public static void setThrowStatsRenderedThisFrame(boolean rendered) {
        throwStatsRenderedThisFrame = rendered;
    }

    public static void render(DrawContext drawContext, HoleMapState state, long holeMapStateReceivedAtMs, float hudAlpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        // Only render if a round is active
        if (state == null || !state.isActive()) {
            return;
        }
        if ((System.currentTimeMillis() - holeMapStateReceivedAtMs) > STALE_TIMEOUT_MS) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);
        NbtCompound scorecardRoot = findScorecardRoot(client);
        if (scorecardRoot == null) {
            return;
        }

        NbtList holes = scorecardRoot.getList(ScorecardManager.KEY_HOLES, NbtElement.COMPOUND_TYPE);
        if (holes.isEmpty()) {
            return;
        }

        int visibleRows = holes.size();
        int holeColW = Math.max(Math.round(client.textRenderer.getWidth("H") * scale), Math.round(client.textRenderer.getWidth(Integer.toString(holes.size())) * scale));
        int distColW = Math.round(client.textRenderer.getWidth("Dist") * scale);
        int parColW = Math.round(client.textRenderer.getWidth("Par") * scale);
        int scoreColW = Math.round(client.textRenderer.getWidth("Score") * scale);
        for (int i = 0; i < visibleRows; i++) {
            NbtCompound row = holes.getCompound(i);
            int dist = row.getInt(ScorecardManager.KEY_DISTANCE_FEET);
            int score = row.getInt(ScorecardManager.KEY_SCORE);
            distColW = Math.max(distColW, Math.round(client.textRenderer.getWidth(dist + "ft") * scale));
            scoreColW = Math.max(scoreColW, Math.round(client.textRenderer.getWidth(score < 0 ? "-" : Integer.toString(score)) * scale));
        }

        int colGap = Math.round(10 * scale);
        int colHoleX = Math.round(6 * scale);
        int colDistX = colHoleX + holeColW + colGap;
        int colParX = colDistX + distColW + colGap;
        int colScoreX = colParX + parColW + colGap;
        
        // Calculate content width
        int contentWidth = colScoreX + scoreColW + Math.round(6 * scale);
        
        // Use shared panel width from other right-side HUDs for alignment
        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), contentWidth);
        RoundInfoOverlay.setSharedPanelWidth(panelW);
        
        int panelH = Math.round(22 * scale) + (visibleRows * Math.round(10 * scale));
        int x = drawContext.getScaledWindowWidth() - panelW - Math.round(8 * scale);
        
        // Position below other right-side HUDs (ThrowStats and StanceSettings)
        int throwStatsHeight = throwStatsRenderedThisFrame ? HudOverlays.getLastThrowStatsPanelHeight() : 0;
        int stanceSettingsHeight = HudOverlays.getLastStanceSettingsPanelHeight();
        int cardSpacing = Math.round(10 * scale);
        int roundHudBottom = Math.round(8 * scale) + RoundInfoOverlay.getLastPanelHeight();
        int y = roundHudBottom + cardSpacing + throwStatsHeight + cardSpacing + stanceSettingsHeight + cardSpacing;

        String courseName = scorecardRoot.getString(ScorecardManager.KEY_COURSE_NAME);
        String panelTitle = (courseName != null && !courseName.isBlank()) ? courseName : "Scorecard";
        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, panelTitle, hudAlpha);

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("H"), x + colHoleX, y + Math.round(14 * scale), HudUtil.withAlpha(0xAAB8CC, hudAlpha), scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("Dist"), x + colDistX, y + Math.round(14 * scale), HudUtil.withAlpha(0xAAB8CC, hudAlpha), scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("Par"), x + colParX, y + Math.round(14 * scale), HudUtil.withAlpha(0xAAB8CC, hudAlpha), scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("Score"), x + colScoreX, y + Math.round(14 * scale), HudUtil.withAlpha(0xAAB8CC, hudAlpha), scale);

        for (int i = 0; i < visibleRows; i++) {
            NbtCompound row = holes.getCompound(i);
            int hole = row.getInt(ScorecardManager.KEY_HOLE_INDEX);
            int dist = row.getInt(ScorecardManager.KEY_DISTANCE_FEET);
            int par = row.getInt(ScorecardManager.KEY_PAR);
            int score = row.getInt(ScorecardManager.KEY_SCORE);
            String holeText = Integer.toString(hole);
            String distText = dist + "ft";
            String parText = Integer.toString(par);
            String scoreText = score < 0 ? "-" : Integer.toString(score);
            int rowY = y + Math.round(24 * scale) + (i * Math.round(10 * scale));
            int rowColor = hole == state.holeIndex ? 0xFFF4D37A : 0xE8EEF7;

            HudUtil.drawScaledText(
                    drawContext,
                    client.textRenderer,
                    Text.literal(holeText),
                    x + rightAlign(colHoleX, holeColW, Math.round(client.textRenderer.getWidth(holeText) * scale)),
                    rowY,
                    HudUtil.withAlpha(rowColor, hudAlpha),
                    scale
            );
            HudUtil.drawScaledText(
                    drawContext,
                    client.textRenderer,
                    Text.literal(distText),
                    x + rightAlign(colDistX, distColW, Math.round(client.textRenderer.getWidth(distText) * scale)),
                    rowY,
                    HudUtil.withAlpha(rowColor, hudAlpha),
                    scale
            );
            HudUtil.drawScaledText(
                    drawContext,
                    client.textRenderer,
                    Text.literal(parText),
                    x + rightAlign(colParX, parColW, Math.round(client.textRenderer.getWidth(parText) * scale)),
                    rowY,
                    HudUtil.withAlpha(rowColor, hudAlpha),
                    scale
            );
            HudUtil.drawScaledText(
                    drawContext,
                    client.textRenderer,
                    Text.literal(scoreText),
                    x + rightAlign(colScoreX, scoreColW, Math.round(client.textRenderer.getWidth(scoreText) * scale)),
                    rowY,
                    HudUtil.withAlpha(rowColor, hudAlpha),
                    scale
            );
        }
    }

    private static NbtCompound findScorecardRoot(MinecraftClient client) {
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            ItemStack stack = client.player.getInventory().getStack(slot);
            if (!stack.isOf(McdgItems.SCORECARD)) {
                continue;
            }
            NbtCompound root = ScorecardManager.getScorecardRoot(stack);
            if (root != null) {
                return root;
            }
        }
        return null;
    }

    private static int rightAlign(int startX, int width, int textWidth) {
        return startX + Math.max(0, width - textWidth);
    }
}
