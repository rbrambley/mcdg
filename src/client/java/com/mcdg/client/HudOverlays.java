package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import java.util.Set;

/**
 * Standalone HUD overlays that don't depend on round or minimap state.
 */
public final class HudOverlays {
    private static final int POWER_BAR_WIDTH = 24; // 3x wider for better visibility
    private static final String[] COMPASS_8 = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
    
    // Power bar sizing: 2/3 of screen height with 1/6 buffers at top and bottom
    private static final float POWER_BAR_SCREEN_RATIO = 2.0f / 3.0f;

    private HudOverlays() {
    }

    public static void renderCompass(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);

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
        int width = Math.round(client.textRenderer.getWidth(compassText) * scale);
        int x = (drawContext.getScaledWindowWidth() - width) / 2;
        int y = client.getDebugHud().shouldShowDebugHud() ? Math.round(56 * scale) : Math.round(8 * scale);

        int margin = Math.round(3 * scale);
        int padding = Math.round(2 * scale);
        int textHeight = Math.round(10 * scale);

        drawContext.fill(x - margin, y - padding, x + width + margin, y + textHeight, 0x70000000);
        HudUtil.drawScaledText(drawContext, client.textRenderer, compassText, x, y, 0xE6E6E6, scale);

        int playerX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerY = net.minecraft.util.math.MathHelper.floor(client.player.getY());
        int playerZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
        String worldCoords = "XYZ " + playerX + " " + playerY + " " + playerZ;
        int coordsWidth = Math.round(client.textRenderer.getWidth(worldCoords) * scale);
        int coordsX = (drawContext.getScaledWindowWidth() - coordsWidth) / 2;
        int rowSpacing = Math.round(12 * scale);
        int coordsY = y + rowSpacing;
        drawContext.fill(coordsX - margin, coordsY - padding, coordsX + coordsWidth + margin, coordsY + textHeight, 0x70000000);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(worldCoords).formatted(Formatting.AQUA), coordsX, coordsY, 0x9BE7FF, scale);

        // Add wind indicator
        if (WindManagerClient.isWindSignificant()) {
            String windText = "WIND: " + WindManagerClient.getWindDirectionText() + " " + Math.round(WindManagerClient.getCurrentWind().speed() * 100) + "%";
            int windWidth = Math.round(client.textRenderer.getWidth(windText) * scale);
            int windX = (drawContext.getScaledWindowWidth() - windWidth) / 2;
            int windY = coordsY + rowSpacing;
            
            Formatting windColor = WindManagerClient.getCurrentWind().speed() > 0.5 ? Formatting.RED : 
                                   (WindManagerClient.getCurrentWind().speed() > 0.3 ? Formatting.YELLOW : Formatting.GREEN);
            
            drawContext.fill(windX - margin, windY - padding, windX + windWidth + margin, windY + textHeight, 0x70000000);
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(windText).formatted(windColor), windX, windY, 0xFFFFFF, scale);
        }
    }

    public static void renderPower(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        if (!ChargedDiscItem.isClientChargeVisible()) {
            return;
        }

        ItemStack activeItem = client.player.getActiveItem();
        boolean isDiscItem = McdgItems.isDisc(activeItem);
        if (!client.player.isUsingItem() || !isDiscItem) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);
        float charge = ChargedDiscItem.getClientChargePercent();
        float nextThrowMultiplier = McdgClientMod.getClientNextThrowPowerMultiplier();
        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();

        boolean rightHandThrow = client.player.getMainArm() == Arm.RIGHT;
        int barX = (width / 2) + (rightHandThrow ? Math.round(66 * scale) : Math.round(-74 * scale));
        
        // Calculate power bar height: 2/3 of screen height
        int scaledPowerBarHeight = Math.round(height * POWER_BAR_SCREEN_RATIO);
        int scaledPowerBarWidth = Math.round(POWER_BAR_WIDTH * scale);
        
        // Center vertically: top buffer = (height - barHeight) / 2
        int topBuffer = (height - scaledPowerBarHeight) / 2;
        int barBottom = topBuffer + scaledPowerBarHeight;
        int barTop = topBuffer;

        int barMargin = Math.round(2 * scale);
        drawContext.fill(barX - barMargin, barTop - barMargin, barX + scaledPowerBarWidth + barMargin, barBottom + barMargin, 0x70000000);
        drawContext.fill(barX, barTop, barX + scaledPowerBarWidth, barBottom, 0xAA1B1B1B);

        // Draw distance markers at 25%, 50%, 75%, 100%
        int[] thresholds = {25, 50, 75, 100};
        for (int threshold : thresholds) {
            float thresholdCharge = threshold / 100.0f;
            int markY = barBottom - Math.round(thresholdCharge * scaledPowerBarHeight);
            drawContext.fill(barX - 1, markY, barX + scaledPowerBarWidth + 1, markY + 1, 0xFFFFFFFF);

            // Distance estimation using DiscFlightSimulator with current stance
            ThrowStance stance = ThrowPreferenceManager.getSelectedStance();
            int estimatedDistance = com.mcdg.game.DiscFlightSimulator.estimateDistance(thresholdCharge, stance, client.player.getPitch());
            String distanceText = estimatedDistance + "ft";
            int textOffset = Math.round(4 * scale);
            int textYOffset = Math.round(4 * scale);
            int scaledTextWidth = Math.round(client.textRenderer.getWidth(distanceText) * scale);
            int textX = rightHandThrow ? barX + scaledPowerBarWidth + textOffset : barX - scaledTextWidth - textOffset;
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(distanceText).formatted(Formatting.GRAY), textX, markY - textYOffset, 0xAAAAAA, scale);
        }

        // Cap the displayed power when a hazard penalty is active
        float effectiveCharge = Math.min(charge, nextThrowMultiplier);
        int filledPixels = Math.max(0, Math.min(scaledPowerBarHeight, Math.round(effectiveCharge * scaledPowerBarHeight)));
        if (filledPixels > 0) {
            int fillTop = barBottom - filledPixels;

            // Color changes: green below 50%, yellow 50-100%, red overcharge 100-125%
            // Hazard penalty active: override with amber/orange so the reduced cap is obvious
            int color;
            if (nextThrowMultiplier < 1.0f) {
                color = 0xFFFFA500; // Amber penalty color
            } else if (charge > 1.0f) {
                color = 0xFFFF3333; // Red overcharge zone
            } else if (charge < 0.5f) {
                color = 0xFF3AC25B; // Green
            } else {
                color = 0xFFFFC336; // Yellow
            }
            drawContext.fill(barX + 1, fillTop, barX + scaledPowerBarWidth - 1, barBottom - 1, color);
        }

        // Draw overcharge zone marker at 100%
        int overchargeMarkY = barBottom - scaledPowerBarHeight;
        drawContext.fill(barX - 1, overchargeMarkY, barX + scaledPowerBarWidth + 1, overchargeMarkY + 1, 0xFFFF3333);

        // Draw hazard penalty cap marker when active
        if (nextThrowMultiplier < 1.0f) {
            int capMarkY = barBottom - Math.round(nextThrowMultiplier * scaledPowerBarHeight);
            drawContext.fill(barX - 2, capMarkY, barX + scaledPowerBarWidth + 2, capMarkY + 2, 0xFFFFA500);
        }

        // Percentage text
        int percent = Math.round(charge * 100.0f);
        int percentTextXOffset = Math.round(8 * scale);
        int percentTextYOffset = Math.round(12 * scale);
        String percentText = Integer.toString(percent) + "%";
        int percentTextWidth = Math.round(client.textRenderer.getWidth(percentText) * scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(percentText).formatted(Formatting.WHITE), barX - percentTextXOffset - percentTextWidth, barTop - percentTextYOffset, 0xFFFFFF, scale);

        // Hazard penalty cap text
        if (nextThrowMultiplier < 1.0f) {
            int capTextYOffset = Math.round(48 * scale);
            String capText = "CAP: " + Math.round(nextThrowMultiplier * 100.0f) + "%";
            int capTextWidth = Math.round(client.textRenderer.getWidth(capText) * scale);
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(capText).formatted(Formatting.GOLD), barX - percentTextXOffset - capTextWidth, barTop - capTextYOffset, 0xFFA500, scale);
        }

        // LOCKED indicator
        if (ChargedDiscItem.isPowerLocked()) {
            int lockedTextYOffset = Math.round(24 * scale);
            String lockedText = "LOCKED";
            int lockedTextWidth = Math.round(client.textRenderer.getWidth(lockedText) * scale);
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(lockedText).formatted(Formatting.RED), barX - percentTextXOffset - lockedTextWidth, barTop - lockedTextYOffset, 0xFF5555, scale);
        }

        // Phase 2: Stance and Angle display
        renderStanceIndicator(drawContext, client, barX, barTop, rightHandThrow, scale);
        
        // Wind arrow indicator
        renderWindIndicator(drawContext, client, barX, barTop, rightHandThrow, scale);
    }

    /**
     * Render the stance and angle indicator near the power bar.
     * Shows stance name (Overhand/Backhand/Forehand) and angle arrow.
     */
    private static void renderStanceIndicator(DrawContext drawContext, MinecraftClient client, int barX, int barTop, boolean rightHandThrow, float scale) {
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

        int scaledPowerBarWidth = Math.round(POWER_BAR_WIDTH * scale);
        int textOffset = Math.round(4 * scale);
        int stanceWidth = Math.round((client.textRenderer.getWidth(stanceText) + client.textRenderer.getWidth(angleText)) * scale);
        int textX = rightHandThrow ? barX + scaledPowerBarWidth + textOffset : barX - stanceWidth - textOffset;
        int textY = barTop - Math.round(36 * scale); // Above LOCKED indicator

        HudUtil.drawScaledText(drawContext, client.textRenderer, stanceText, textX, textY, 0xFFFFFF, scale);
        int angleTextX = textX + Math.round(client.textRenderer.getWidth(stanceText) * scale);
        HudUtil.drawScaledText(drawContext, client.textRenderer, angleText, angleTextX, textY, 0xFFFFFF, scale);

        // Phase 4: Ruleset display (only show non-default rulesets)
        HoleMapState holeMapState = McdgClientMod.getHoleMapState();
        if (holeMapState != null && holeMapState.rulesetName != null && holeMapState.presetName != null) {
            String rulesetText = "";
            boolean showRuleset = false;

            if ("casual".equalsIgnoreCase(holeMapState.rulesetName)) {
                // Casual is default for casual, don't show
            } else if ("strict".equalsIgnoreCase(holeMapState.rulesetName)) {
                if ("balanced".equalsIgnoreCase(holeMapState.presetName)) {
                    // Strict (Balanced) is default, don't show
                } else {
                    // Show non-default surface presets
                    rulesetText = holeMapState.rulesetName + " (" + holeMapState.presetName + ")";
                    showRuleset = true;
                }
            }

            if (showRuleset) {
                int rulesetY = textY + Math.round(12 * scale);
                int rulesetWidth = Math.round(client.textRenderer.getWidth(rulesetText) * scale);
                int rulesetX = rightHandThrow ? barX + scaledPowerBarWidth + textOffset : barX - rulesetWidth - textOffset;
                HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(rulesetText).formatted(Formatting.GRAY), rulesetX, rulesetY, 0xAAAAAA, scale);
            }
        }
    }

    /**
     * Render prominent wind arrow indicator near the power bar.
     * Shows wind direction as a large graphical arrow with speed and direction text.
     */
    private static void renderWindIndicator(DrawContext drawContext, MinecraftClient client, int barX, int barTop, boolean rightHandThrow, float scale) {
        if (!ChargedDiscItem.isClientChargeVisible()) {
            return;
        }
        
        if (!WindManagerClient.isWindSignificant()) {
            return;
        }
        
        float windSpeed = (float) WindManagerClient.getCurrentWind().speed();
        float windDirection = WindManagerClient.getCurrentWind().directionDegrees();
        float playerYaw = client.player.getYaw();
        
        // Convert Minecraft yaw to compass convention: 0=North, 90=East, 180=South, 270=West
        float compassYaw = Math.floorMod(Math.round(180.0f - playerYaw), 360);
        // Calculate wind direction relative to where the player is facing
        float relativeWind = Math.floorMod(Math.round(windDirection - compassYaw), 360);
        // Rotation to apply to the up-pointing arrow; previous version used this sign
        float rotationAngle = -relativeWind;
        
        // Determine color based on wind speed
        int arrowColor;
        if (windSpeed > 0.5) {
            arrowColor = 0xFFFF5555; // Red for strong wind
        } else if (windSpeed > 0.3) {
            arrowColor = 0xFFFFFF55; // Yellow for moderate wind
        } else {
            arrowColor = 0xFF55FF55; // Green for light wind
        }
        
        // Position wind indicator centered on screen, just above the crosshair
        int centerX = drawContext.getScaledWindowWidth() / 2;
        int indicatorY = drawContext.getScaledWindowHeight() / 2 - Math.round(24 * scale);
        
        // Draw background box for emphasis
        int boxWidth = Math.round(72 * scale);
        int boxHeight = Math.round(64 * scale);
        int boxX = centerX - boxWidth / 2;
        int boxY = indicatorY - boxHeight / 2;
        
        drawContext.fill(boxX - 2, boxY - 2, boxX + boxWidth + 2, boxY + boxHeight + 2, 0xFF000000); // Black border
        drawContext.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xDD000000); // Dark background
        
        // Draw a large rotated arrow using simple polygons
        int arrowCenterX = centerX;
        int arrowCenterY = indicatorY - Math.round(5 * scale);
        int shaftLength = Math.round(28 * scale);
        int shaftWidth = Math.round(8 * scale);
        int headLength = Math.round(14 * scale);
        int headWidth = Math.round(28 * scale);
        int tailLength = Math.round(6 * scale);
        int tailWidth = Math.round(12 * scale);
        
        drawContext.getMatrices().push();
        drawContext.getMatrices().translate(arrowCenterX, arrowCenterY, 0);
        drawContext.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationAngle));
        
        // Shaft (centered vertically around origin)
        int halfShaft = shaftLength / 2;
        int halfWidth = shaftWidth / 2;
        drawContext.fill(-halfWidth, -halfShaft, halfWidth, halfShaft, arrowColor);
        
        // Arrow head (triangle pointing up from top of shaft)
        int headBaseY = -halfShaft;
        int headTipY = headBaseY - headLength;
        int halfHead = headWidth / 2;
        drawFilledTriangle(drawContext, -halfHead, headBaseY, halfHead, headBaseY, 0, headTipY, arrowColor);
        
        // Tail/base marker (small rectangle at bottom of shaft)
        int tailBaseY = halfShaft;
        int tailTipY = tailBaseY + tailLength;
        int halfTail = tailWidth / 2;
        drawContext.fill(-halfTail, tailBaseY, halfTail, tailTipY, arrowColor);
        
        drawContext.getMatrices().pop();
        
        // Draw speed and direction text below arrow
        String directionText = WindManagerClient.getWindDirectionText();
        String speedText = Math.round(windSpeed * 100) + "%";
        String fullText = directionText + " " + speedText;
        
        int textY = indicatorY + Math.round(20 * scale);
        int textWidth = Math.round(client.textRenderer.getWidth(fullText) * scale);
        int textX = centerX - textWidth / 2;
        
        Text windText = Text.literal(fullText).formatted(Formatting.WHITE);
        HudUtil.drawScaledText(drawContext, client.textRenderer, windText, textX, textY, 0xFFFFFF, scale);
    }
    
    /**
     * Draw a filled triangle using horizontal scanlines.
     * Vertices are in the current matrix coordinate space.
     */
    private static void drawFilledTriangle(DrawContext drawContext, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        int[] xs = {x1, x2, x3};
        int[] ys = {y1, y2, y3};
        
        // Sort vertices by y ascending
        for (int i = 0; i < 2; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (ys[j] < ys[i]) {
                    int tmp = ys[i]; ys[i] = ys[j]; ys[j] = tmp;
                    tmp = xs[i]; xs[i] = xs[j]; xs[j] = tmp;
                }
            }
        }
        
        int totalHeight = ys[2] - ys[0];
        if (totalHeight <= 0) {
            return;
        }
        
        for (int y = ys[0]; y <= ys[2]; y++) {
            boolean upper = y > ys[1];
            int segmentHeight = upper ? ys[2] - ys[1] : ys[1] - ys[0];
            if (segmentHeight <= 0) {
                segmentHeight = 1;
            }
            float alpha = (float) (y - (upper ? ys[1] : ys[0])) / segmentHeight;
            int xA = upper ? Math.round(xs[1] + (xs[2] - xs[1]) * alpha) : Math.round(xs[0] + (xs[1] - xs[0]) * alpha);
            int xB = Math.round(xs[0] + (xs[2] - xs[0]) * (float) (y - ys[0]) / totalHeight);
            if (xA > xB) {
                int tmp = xA; xA = xB; xB = tmp;
            }
            drawContext.fill(xA, y, xB + 1, y + 1, color);
        }
    }

    private static final int THROW_ROW_SPACING = 10;
    private static final int HUD_CARD_SPACING = 10;
    private static int lastThrowStatsPanelHeight = 38;
    private static int lastThrowStatsPanelWidth = 120;
    private static int lastStanceSettingsPanelHeight = 0;
    private static boolean throwStatsRenderedThisFrame = false;

    /**
     * Render after-throw statistics display.
     * Shows distance, drift, stance, angle, and penalty feedback for the last throw.
     * Panel width is shared with Round HUD so both boxes stay aligned.
     */
    public static void renderThrowStats(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        throwStatsRenderedThisFrame = true;
        
        // Only render if a round is active
        if (McdgClientMod.getHoleMapState() == null) {
            throwStatsRenderedThisFrame = false;
            lastThrowStatsPanelHeight = 0;
            lastStanceSettingsPanelHeight = 0;
            return;
        }
        
        DiscTrailRenderer.ThrowStats stats = DiscTrailRenderer.getStats();
        if (stats == null) {
            // Render empty panel when no throw stats yet
            renderEmptyThrowStatsPanel(drawContext, client, hudAlpha);
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);
        int width = drawContext.getScaledWindowWidth();

        // Compact abbreviations
        String driftDir = stats.lateralDriftFt() > 0 ? "R" : "L";
        String row1 = (int) stats.totalDistanceFt() + "ft " + driftDir + (int) Math.abs(stats.lateralDriftFt());
        String row2 = stanceAbbrev(stats.stance()) + " " + angleAbbrev(stats.angle());

        boolean hasPenalty = stats.penaltyType() != StrictPenaltyType.NONE;
        int contentRows = hasPenalty ? 3 : 3; // always 3 content rows, penalty merges into row 3
        int scaledRowSpacing = Math.round(THROW_ROW_SPACING * scale);
        int panelH = Math.round(16 * scale) + (contentRows * scaledRowSpacing) + Math.round(6 * scale);
        lastThrowStatsPanelHeight = panelH;

        // Compute throw text width (scaled) and share with Round HUD
        int row1W = Math.round(client.textRenderer.getWidth(row1) * scale);
        int row2W = Math.round(client.textRenderer.getWidth(row2) * scale);
        int maxThrowTextW = Math.max(row1W, row2W);

        String penaltyRow = buildPenaltyRow(stats);
        if (penaltyRow != null) {
            int penaltyW = Math.round(client.textRenderer.getWidth(penaltyRow) * scale);
            maxThrowTextW = Math.max(maxThrowTextW, penaltyW);
        }

        int throwPanelW = maxThrowTextW + Math.round(16 * scale);
        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), throwPanelW);
        RoundInfoOverlay.setSharedPanelWidth(panelW);
        lastThrowStatsPanelWidth = panelW;

        int x = width - panelW - Math.round(8 * scale);
        int roundHudBaseHeight = RoundInfoOverlay.getLastPanelHeight();
        int y = Math.round(8 * scale) + roundHudBaseHeight + Math.round(HUD_CARD_SPACING * scale);

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Throw", hudAlpha);

        int drawX = x + Math.round(6 * scale);
        int row = y + Math.round(16 * scale);
        int colorWhite = HudUtil.withAlpha(0xFFFFFF, hudAlpha);
        int colorGray = HudUtil.withAlpha(0xAAAAAA, hudAlpha);

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(row1).formatted(Formatting.WHITE), drawX, row, colorWhite, scale);
        row += scaledRowSpacing;

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(row2).formatted(Formatting.GRAY), drawX, row, colorGray, scale);
        row += scaledRowSpacing;

        if (penaltyRow != null) {
            int penaltyColor = switch (stats.penaltyType()) {
                case OB -> HudUtil.withAlpha(0xFF5555, hudAlpha);
                case HAZARD -> HudUtil.withAlpha(0xFFCC44, hudAlpha);
                default -> HudUtil.withAlpha(0x55FF55, hudAlpha);
            };
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(penaltyRow).formatted(Formatting.BOLD), drawX, row, penaltyColor, scale);
        } else {
            HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("In Bounds").formatted(Formatting.BOLD), drawX, row, HudUtil.withAlpha(0x55FF55, hudAlpha), scale);
        }
    }

    private static void renderEmptyThrowStatsPanel(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        float scale = HudUtil.getScaleFactor(drawContext);
        int width = drawContext.getScaledWindowWidth();
        
        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), Math.round(100 * scale));
        int panelH = Math.round(22 * scale) + Math.round(12 * scale);
        lastThrowStatsPanelHeight = panelH;
        lastThrowStatsPanelWidth = panelW;
        
        int x = width - panelW - Math.round(8 * scale);
        int roundHudBaseHeight = RoundInfoOverlay.getLastPanelHeight();
        int y = Math.round(8 * scale) + roundHudBaseHeight + Math.round(HUD_CARD_SPACING * scale);
        
        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Throw", hudAlpha);
        
        int drawX = x + Math.round(6 * scale);
        int row = y + Math.round(16 * scale);
        int colorGray = HudUtil.withAlpha(0xAAAAAA, hudAlpha);
        
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal("No throw data yet").formatted(Formatting.GRAY), drawX, row, colorGray, scale);
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

    public static boolean isThrowStatsRenderedThisFrame() {
        return throwStatsRenderedThisFrame;
    }

    public static int getLastStanceSettingsPanelHeight() {
        return lastStanceSettingsPanelHeight;
    }

    /**
     * Render current stance and release angle settings as a compact card beneath the Throw HUD.
     * Uses the same shared panel width, scaling, and card style as the Throw HUD.
     */
    public static void renderStanceSettings(DrawContext drawContext, MinecraftClient client, float hudAlpha) {
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }
        // Only render if a round is active
        if (McdgClientMod.getHoleMapState() == null) {
            return;
        }

        float scale = HudUtil.getScaleFactor(drawContext);
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

        int stanceW = Math.round(client.textRenderer.getWidth(stanceName) * scale);
        int angleW = Math.round(client.textRenderer.getWidth(angleName) * scale);
        int stanceHintW = Math.round(client.textRenderer.getWidth(stanceHint) * scale);
        int angleHintW = Math.round(client.textRenderer.getWidth(angleHint) * scale);

        int maxTextW = Math.max(stanceW, angleW);

        int panelW = Math.max(RoundInfoOverlay.getSharedPanelWidth(), maxTextW + Math.round(16 * scale));
        RoundInfoOverlay.setSharedPanelWidth(panelW);

        int contentRows = 3; // stance, angle, skills
        int scaledRowSpacing = Math.round(THROW_ROW_SPACING * scale);
        int panelH = Math.round(16 * scale) + (contentRows * scaledRowSpacing) + Math.round(4 * scale);

        int x = drawContext.getScaledWindowWidth() - panelW - Math.round(8 * scale);
        int roundHudBottom = Math.round(8 * scale) + RoundInfoOverlay.getLastPanelHeight();
        int scaledCardSpacing = Math.round(HUD_CARD_SPACING * scale);
        int y = throwStatsRenderedThisFrame
                ? (roundHudBottom + scaledCardSpacing + getLastThrowStatsPanelHeight() + scaledCardSpacing)
                : (roundHudBottom + scaledCardSpacing);

        HudUtil.drawCard(drawContext, client, x, y, panelW, panelH, "Setup", hudAlpha);

        int drawX = x + Math.round(6 * scale);
        int row = y + Math.round(16 * scale);
        int stanceTextColor = HudUtil.withAlpha(colorFromFormatting(stanceColor), hudAlpha);
        int angleTextColor = HudUtil.withAlpha(colorFromFormatting(angleColor), hudAlpha);
        int hintColor = HudUtil.withAlpha(0xAAAAAA, hudAlpha);

        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(stanceName).formatted(stanceColor), drawX, row, stanceTextColor, scale);
        int stanceHintX = x + panelW - Math.round(6 * scale) - stanceHintW;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(stanceHint).formatted(Formatting.DARK_GRAY), stanceHintX, row, hintColor, scale);
        row += scaledRowSpacing;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(angleName).formatted(angleColor), drawX, row, angleTextColor, scale);
        int angleHintX = x + panelW - Math.round(6 * scale) - angleHintW;
        HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(angleHint).formatted(Formatting.DARK_GRAY), angleHintX, row, hintColor, scale);

        // Render skill icons
        row += scaledRowSpacing;
        renderSkillIcons(drawContext, client, x, row, panelW, scale, hudAlpha);

        // Track panel height for scorecard positioning
        lastStanceSettingsPanelHeight = panelH;
    }

    private static void renderSkillIcons(DrawContext drawContext, MinecraftClient client, int x, int y, int panelW, float scale, float hudAlpha) {
        Set<String> unlockedSkills = McdgClientMod.getUnlockedSkills();
        if (unlockedSkills.isEmpty()) {
            // Adjust panel height if no skills unlocked
            lastStanceSettingsPanelHeight = Math.round(16 * scale) + (2 * Math.round(THROW_ROW_SPACING * scale)) + Math.round(4 * scale);
            return;
        }

        // Skill icons and their colors
        String[] skillKeys = {"power_control", "release_control", "wind_reading", "focus", "disc_mastery"};
        String[] skillIcons = {"⚡", "🎯", "💨", "🎯", "🏆"};
        Formatting[] skillColors = {Formatting.RED, Formatting.AQUA, Formatting.YELLOW, Formatting.GREEN, Formatting.LIGHT_PURPLE};

        int iconSpacing = Math.round(12 * scale);
        int startX = x + Math.round(6 * scale);
        int iconY = y;

        for (int i = 0; i < skillKeys.length; i++) {
            if (unlockedSkills.contains(skillKeys[i])) {
                int iconColor = HudUtil.withAlpha(colorFromFormatting(skillColors[i]), hudAlpha);
                HudUtil.drawScaledText(drawContext, client.textRenderer, Text.literal(skillIcons[i]), startX + (i * iconSpacing), iconY, iconColor, scale);
            }
        }
    }

    private static int colorFromFormatting(Formatting fmt) {
        return switch (fmt) {
            case GRAY -> 0xAAAAAA;
            case AQUA -> 0x55FFFF;
            case GREEN -> 0x55FF55;
            case RED -> 0xFF5555;
            case WHITE -> 0xFFFFFF;
            case YELLOW -> 0xFFFF55;
            case LIGHT_PURPLE -> 0xFF55FF;
            default -> 0xFFFFFF;
        };
    }
}
