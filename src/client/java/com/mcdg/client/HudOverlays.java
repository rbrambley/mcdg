package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
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

    private static final float THROW_HUD_SCALE = 0.85f;
    private static final int THROW_ROW_SPACING = 10; // spacing for 0.85x text
    private static final int HUD_CARD_SPACING = 10;
    private static int lastThrowStatsPanelHeight = 38;
    private static int lastThrowStatsPanelWidth = 120;
    private static boolean throwStatsRenderedThisFrame = false;

    /**
     * Render after-throw statistics display.
     * Shows distance, drift, stance, angle, and penalty feedback for the last throw.
     * Compact 0.85x scaled text. Penalty info consolidated into fewer rows.
     * Panel width is shared with Round HUD so both boxes stay aligned.
     */
    public static void renderThrowStats(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        throwStatsRenderedThisFrame = false;
        DiscTrailRenderer.ThrowStats stats = DiscTrailRenderer.getStats();
        if (stats == null) {
            return;
        }
        throwStatsRenderedThisFrame = true;

        int width = drawContext.getScaledWindowWidth();

        // Compact abbreviations
        String driftDir = stats.lateralDriftFt() > 0 ? "R" : "L";
        String row1 = (int) stats.totalDistanceFt() + "ft " + driftDir + (int) Math.abs(stats.lateralDriftFt());
        String row2 = stanceAbbrev(stats.stance()) + " " + angleAbbrev(stats.angle());

        boolean hasPenalty = stats.penaltyType() != StrictPenaltyType.NONE;
        int contentRows = hasPenalty ? 3 : 3; // always 3 content rows, penalty merges into row 3
        int panelH = 16 + (contentRows * THROW_ROW_SPACING) + 6;
        lastThrowStatsPanelHeight = panelH;

        // Compute throw text width (scaled) and share with Round HUD
        int row1W = Math.round(client.textRenderer.getWidth(row1) * THROW_HUD_SCALE);
        int row2W = Math.round(client.textRenderer.getWidth(row2) * THROW_HUD_SCALE);
        int maxThrowTextW = Math.max(row1W, row2W);

        String penaltyRow = buildPenaltyRow(stats);
        if (penaltyRow != null) {
            int penaltyW = Math.round(client.textRenderer.getWidth(penaltyRow) * THROW_HUD_SCALE);
            maxThrowTextW = Math.max(maxThrowTextW, penaltyW);
        }

        int throwPanelW = maxThrowTextW + 16;
        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), throwPanelW);
        RoundInfoOverlay.setSharedPanelWidth(panelW);
        lastThrowStatsPanelWidth = panelW;

        int x = width - panelW - 8;
        int roundHudBaseHeight = RoundInfoOverlay.getLastPanelHeight();
        int y = 8 + roundHudBaseHeight + HUD_CARD_SPACING;

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Throw", hudAlpha);

        int drawX = x + 6;
        int row = y + 16;
        int colorWhite = HudUtil.withAlpha(0xFFFFFF, hudAlpha);
        int colorGray = HudUtil.withAlpha(0xAAAAAA, hudAlpha);

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(row1).formatted(Formatting.WHITE), drawX, row, colorWhite, THROW_HUD_SCALE);
        row += THROW_ROW_SPACING;

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(row2).formatted(Formatting.GRAY), drawX, row, colorGray, THROW_HUD_SCALE);
        row += THROW_ROW_SPACING;

        if (penaltyRow != null) {
            int penaltyColor = switch (stats.penaltyType()) {
                case OB -> HudUtil.withAlpha(0xFF5555, hudAlpha);
                case HAZARD -> HudUtil.withAlpha(0xFFCC44, hudAlpha);
                default -> HudUtil.withAlpha(0x55FF55, hudAlpha);
            };
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(penaltyRow).formatted(Formatting.BOLD), drawX, row, penaltyColor, THROW_HUD_SCALE);
        } else {
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("In Bounds").formatted(Formatting.BOLD), drawX, row, HudUtil.withAlpha(0x55FF55, hudAlpha), THROW_HUD_SCALE);
        }
    }

    private static String stanceAbbrev(ThrowStance stance) {
        return switch (stance) {
            case OVERHAND -> "Overhand";
            case BACKHAND -> "Backhand";
            case FOREHAND -> "Forehand";
        };
    }

    private static String angleAbbrev(ReleaseAngle angle) {
        return switch (angle) {
            case HYZER -> "Hyzer";
            case FLAT -> "Flat";
            case ANHYZER -> "Anhyzer";
        };
    }

    private static String buildPenaltyRow(DiscTrailRenderer.ThrowStats stats) {
        return switch (stats.penaltyType()) {
            case OB -> "OB+" + stats.penaltyStrokes() + " " + stats.penaltyReason() + " " + stats.obCrossingFeet() + "ft -> " + stats.returnedToFeet() + "ft";
            case HAZARD -> "Hazard+" + stats.penaltyStrokes() + " " + stats.penaltyReason() + " " + stats.returnedToFeet() + "ft";
            default -> null;
        };
    }

    public static int getLastThrowStatsPanelHeight() {
        return lastThrowStatsPanelHeight;
    }

    public static int getLastThrowStatsPanelWidth() {
        return lastThrowStatsPanelWidth;
    }

    /**
     * Render current stance and release angle settings as a compact card beneath the Throw HUD.
     * Uses the same shared panel width, scaling, and card style as the Throw HUD.
     */
    public static void renderStanceSettings(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        if (MiniMapRenderer.getMiniMapState() == null) {
            return;
        }

        ThrowStance stance = ThrowPreferenceManager.getSelectedStance();
        ReleaseAngle angle = ThrowPreferenceManager.getSelectedAngle();

        String stanceName = switch (stance) {
            case OVERHAND -> "Overhand";
            case BACKHAND -> "Backhand";
            case FOREHAND -> "Forehand";
        };

        String angleName = switch (angle) {
            case HYZER -> "Hyzer";
            case FLAT -> "Flat";
            case ANHYZER -> "Anhyzer";
        };

        Formatting stanceColor = switch (stance) {
            case OVERHAND -> Formatting.GRAY;
            case BACKHAND -> Formatting.AQUA;
            case FOREHAND -> Formatting.GREEN;
        };

        Formatting angleColor = switch (angle) {
            case HYZER -> Formatting.RED;
            case FLAT -> Formatting.WHITE;
            case ANHYZER -> Formatting.YELLOW;
        };

        String stanceHint = "[" + ClientKeybinds.getCycleStanceKeyText().getString() + "]";
        String angleHint = "[" + ClientKeybinds.getAngleLeftKeyText().getString() + " | " + ClientKeybinds.getAngleRightKeyText().getString() + "]";
        final float HINT_SCALE = 0.65f;

        int stanceW = Math.round(client.textRenderer.getWidth(stanceName) * THROW_HUD_SCALE);
        int angleW = Math.round(client.textRenderer.getWidth(angleName) * THROW_HUD_SCALE);
        int stanceHintW = Math.round(client.textRenderer.getWidth(stanceHint) * HINT_SCALE);
        int angleHintW = Math.round(client.textRenderer.getWidth(angleHint) * HINT_SCALE);

        int maxTextW = Math.max(stanceW, angleW);

        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), maxTextW + 16);
        RoundInfoOverlay.setSharedPanelWidth(panelW);

        int contentRows = 2;
        int panelH = 16 + (contentRows * THROW_ROW_SPACING) + 4;

        int x = drawContext.getScaledWindowWidth() - panelW - 8;
        int roundHudBottom = 8 + RoundInfoOverlay.getLastPanelHeight();
        int y = throwStatsRenderedThisFrame
                ? (roundHudBottom + HUD_CARD_SPACING + getLastThrowStatsPanelHeight() + HUD_CARD_SPACING)
                : (roundHudBottom + HUD_CARD_SPACING);

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Setup", hudAlpha);

        int drawX = x + 6;
        int row = y + 16;
        int stanceTextColor = HudUtil.withAlpha(colorFromFormatting(stanceColor), hudAlpha);
        int angleTextColor = HudUtil.withAlpha(colorFromFormatting(angleColor), hudAlpha);
        int hintColor = HudUtil.withAlpha(0xAAAAAA, hudAlpha);

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(stanceName).formatted(stanceColor), drawX, row, stanceTextColor, THROW_HUD_SCALE);
        int stanceHintX = x + panelW - 6 - stanceHintW;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(stanceHint).formatted(Formatting.DARK_GRAY), stanceHintX, row, hintColor, HINT_SCALE);
        row += THROW_ROW_SPACING;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(angleName).formatted(angleColor), drawX, row, angleTextColor, THROW_HUD_SCALE);
        int angleHintX = x + panelW - 6 - angleHintW;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(angleHint).formatted(Formatting.DARK_GRAY), angleHintX, row, hintColor, HINT_SCALE);
    }

    private static int colorFromFormatting(Formatting fmt) {
        return switch (fmt) {
            case GRAY -> 0xAAAAAA;
            case AQUA -> 0x55FFFF;
            case GREEN -> 0x55FF55;
            case RED -> 0xFF5555;
            case WHITE -> 0xFFFFFF;
            case YELLOW -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }
}
