package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;

/**
 * Standalone HUD overlays that don't depend on round or minimap state.
 */
public final class HudOverlays {
    private static final int POWER_BAR_HEIGHT = 72;
    private static final int POWER_BAR_WIDTH = 8;
    private static final String[] COMPASS_8 = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    private HudOverlays() {
    }

    public static void renderCompass(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        float yaw = client.player.getYaw();
        int dirIndex = Math.floorMod(Math.round(yaw / 45.0f), COMPASS_8.length);
        int prev2 = Math.floorMod(dirIndex - 2, COMPASS_8.length);
        int prev1 = Math.floorMod(dirIndex - 1, COMPASS_8.length);
        int next1 = Math.floorMod(dirIndex + 1, COMPASS_8.length);
        int next2 = Math.floorMod(dirIndex + 2, COMPASS_8.length);

        MutableText compassText = Text.empty();
        compassText.append(Text.literal(COMPASS_8[prev2] + " ").formatted(Formatting.DARK_GRAY));
        compassText.append(Text.literal(COMPASS_8[prev1] + " ").formatted(Formatting.GRAY));
        compassText.append(Text.literal("[" + COMPASS_8[dirIndex] + "]").formatted(Formatting.GOLD));
        compassText.append(Text.literal(" " + COMPASS_8[next1] + " ").formatted(Formatting.GRAY));
        compassText.append(Text.literal(COMPASS_8[next2]).formatted(Formatting.DARK_GRAY));
        int width = client.textRenderer.getWidth(compassText);
        int x = (drawContext.getScaledWindowWidth() - width) / 2;
        int y = client.getDebugHud().shouldShowDebugHud() ? 56 : 8;

        drawContext.fill(x - 3, y - 2, x + width + 3, y + 10, 0x70000000);
        drawContext.drawTextWithShadow(client.textRenderer, compassText, x, y, 0xE6E6E6);

        int playerX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerY = net.minecraft.util.math.MathHelper.floor(client.player.getY());
        int playerZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
        String worldCoords = "XYZ " + playerX + " " + playerY + " " + playerZ;
        int coordsWidth = client.textRenderer.getWidth(worldCoords);
        int coordsX = (drawContext.getScaledWindowWidth() - coordsWidth) / 2;
        int coordsY = y + 12;
        drawContext.fill(coordsX - 3, coordsY - 2, coordsX + coordsWidth + 3, coordsY + 10, 0x70000000);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(worldCoords).formatted(Formatting.AQUA), coordsX, coordsY, 0x9BE7FF);
    }

    public static void renderPower(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        if (!ChargedDiscItem.isClientChargeVisible()) {
            return;
        }

        if (!client.player.isUsingItem() || !client.player.getActiveItem().isOf(McdgItems.TRAINING_DISC)) {
            return;
        }

        float charge = ChargedDiscItem.getClientChargePercent();
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();

        boolean rightHandThrow = client.player.getMainArm() == Arm.RIGHT;
        int barX = (width / 2) + (rightHandThrow ? 66 : -74);
        int barBottom = (height / 2) + 54;
        int barTop = barBottom - POWER_BAR_HEIGHT;

        drawContext.fill(barX - 2, barTop - 2, barX + POWER_BAR_WIDTH + 2, barBottom + 2, 0x70000000);
        drawContext.fill(barX, barTop, barX + POWER_BAR_WIDTH, barBottom, 0xAA1B1B1B);

        // Draw distance markers at 25%, 50%, 75%, 100%
        int[] thresholds = {25, 50, 75, 100};
        for (int threshold : thresholds) {
            float thresholdCharge = threshold / 100.0f;
            int markY = barBottom - Math.round(thresholdCharge * POWER_BAR_HEIGHT);
            drawContext.fill(barX - 1, markY, barX + POWER_BAR_WIDTH + 1, markY + 1, 0xFFFFFFFF);

            // Distance estimation using DiscFlightSimulator with current stance
            ThrowStance stance = ThrowPreferenceManager.getSelectedStance();
            int estimatedDistance = com.mcdg.game.DiscFlightSimulator.estimateDistance(thresholdCharge, stance, client.player.getPitch());
            String distanceText = estimatedDistance + "ft";
            int textX = rightHandThrow ? barX + POWER_BAR_WIDTH + 4 : barX - client.textRenderer.getWidth(distanceText) - 4;
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(distanceText).formatted(Formatting.GRAY), textX, markY - 4, 0xAAAAAA);
        }

        int filledPixels = Math.max(0, Math.min(POWER_BAR_HEIGHT, Math.round(charge * POWER_BAR_HEIGHT)));
        if (filledPixels > 0) {
            int fillTop = barBottom - filledPixels;

            // Color changes: green below 50%, yellow 50-100%, red overcharge 100-125%
            int color;
            if (charge > 1.0f) {
                color = 0xFFFF3333; // Red overcharge zone
            } else if (charge < 0.5f) {
                color = 0xFF3AC25B; // Green
            } else {
                color = 0xFFFFC336; // Yellow
            }
            drawContext.fill(barX + 1, fillTop, barX + POWER_BAR_WIDTH - 1, barBottom - 1, color);
        }

        // Draw overcharge zone marker at 100%
        int overchargeMarkY = barBottom - POWER_BAR_HEIGHT;
        drawContext.fill(barX - 1, overchargeMarkY, barX + POWER_BAR_WIDTH + 1, overchargeMarkY + 1, 0xFFFF3333);

        // Percentage text
        int percent = Math.round(charge * 100.0f);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(Integer.toString(percent) + "%").formatted(Formatting.WHITE), barX - 8, barTop - 12, 0xFFFFFF);

        // LOCKED indicator
        if (ChargedDiscItem.isPowerLocked()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal("LOCKED").formatted(Formatting.RED), barX - 8, barTop - 24, 0xFF5555);
        }

        // Phase 2: Stance and Angle display
        renderStanceIndicator(drawContext, client, barX, barTop, rightHandThrow);
    }

    /**
     * Render the stance and angle indicator near the power bar.
     * Shows stance name (Overhand/Backhand/Forehand) and angle arrow.
     */
    private static void renderStanceIndicator(DrawContext drawContext, MinecraftClient client, int barX, int barTop, boolean rightHandThrow) {
        ThrowStance stance = ThrowPreferenceManager.getSelectedStance();
        ReleaseAngle angle = ThrowPreferenceManager.getSelectedAngle();

        // Stance name with appropriate formatting
        Formatting stanceColor = switch (stance) {
            case OVERHAND -> Formatting.GRAY;
            case BACKHAND -> Formatting.AQUA;
            case FOREHAND -> Formatting.GREEN;
        };

        String stanceName = switch (stance) {
            case OVERHAND -> "Overhand";
            case BACKHAND -> "Backhand";
            case FOREHAND -> "Forehand";
        };

        // Angle symbol
        String angleSymbol = switch (angle) {
            case HYZER -> "^";     // Up arrow for hyzer
            case FLAT -> "-";      // Dash for flat
            case ANHYZER -> "v";   // Down arrow for anhyzer
        };

        Formatting angleColor = switch (angle) {
            case HYZER -> Formatting.RED;      // Hyzer = more fade
            case FLAT -> Formatting.WHITE;     // Neutral
            case ANHYZER -> Formatting.YELLOW; // Anhyzer = counteract fade
        };

        // Combine stance and angle
        Text stanceText = Text.literal(stanceName).formatted(stanceColor);
        Text angleText = Text.literal(" " + angleSymbol).formatted(angleColor);

        int stanceWidth = client.textRenderer.getWidth(stanceText) + client.textRenderer.getWidth(angleText);
        int textX = rightHandThrow ? barX + POWER_BAR_WIDTH + 4 : barX - stanceWidth - 4;
        int textY = barTop - 36; // Above LOCKED indicator

        drawContext.drawTextWithShadow(client.textRenderer, stanceText, textX, textY, 0xFFFFFF);
        drawContext.drawTextWithShadow(client.textRenderer, angleText, textX + client.textRenderer.getWidth(stanceText), textY, 0xFFFFFF);
    }

    /**
     * Render after-throw statistics display.
     * Shows distance, drift, stance, and angle for the last throw.
     * Updates after each throw, positioned in top right under round HUD.
     */
    public static void renderThrowStats(DrawContext drawContext, MinecraftClient client) {
        DiscTrailRenderer.ThrowStats stats = DiscTrailRenderer.getStats();
        if (stats == null) {
            return;
        }

        int width = drawContext.getScaledWindowWidth();

        // Build stats text - 2 rows
        String driftDirection = stats.lateralDriftFt() > 0 ? "RIGHT" : "LEFT";
        String row1 = String.format("%dft | %s %dft", (int) stats.totalDistanceFt(), driftDirection, (int) Math.abs(stats.lateralDriftFt()));
        String row2 = String.format("%s | %s", stats.stance(), stats.angle());

        int row1Width = client.textRenderer.getWidth(row1);
        int row2Width = client.textRenderer.getWidth(row2);
        int maxRowWidth = Math.max(row1Width, row2Width);

        // Position in top right, below round HUD (round HUD is at y=8)
        int x = width - maxRowWidth - 8;
        int roundHudBaseHeight = RoundInfoOverlay.getLastPanelHeight();
        int y = 8 + roundHudBaseHeight + 10; // Below round HUD with 10px gap

        // Background box
        drawContext.fill(x - 4, y - 4, x + maxRowWidth + 4, y + 24, 0x70000000);

        // Draw row 1 (distance and drift)
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(row1).formatted(Formatting.WHITE), x, y, 0xFFFFFF);

        // Draw row 2 (stance and angle)
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(row2).formatted(Formatting.GRAY), x, y + 12, 0xAAAAAA);
    }
}
