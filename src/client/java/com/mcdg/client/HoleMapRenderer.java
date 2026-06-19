package com.mcdg.client;

import com.mcdg.net.HoleMapSync;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

/**
 * Schematic hole map drawing utilities.
 * Transforms world coordinates to screen coordinates and renders
 * fairways, hazards, tee, basket, and player position.
 */
public final class HoleMapRenderer {

    // Colors
    public static final int COLOR_ROUGH = 0xFF2D4A2D;
    public static final int COLOR_FAIRWAY = 0xFF4A8C4A;
    public static final int COLOR_GREEN = 0xFF5A9E5A;
    public static final int COLOR_WATER = 0xFF3A7A9E;
    public static final int COLOR_TEE = 0xFFD4E8FF;
    public static final int COLOR_BASKET = 0xFFFF5555;
    public static final int COLOR_PLAYER = 0xFF57D163;
    public static final int COLOR_CORRIDOR = 0xCCFFD4A0;
    public static final int COLOR_OB = 0xFF8B3A3A;

    // OB boundary line (outside fairway corridor)
    public static final int COLOR_OB_LINE = 0xFFFF4444;

    private HoleMapRenderer() {
    }

    public record MapTransform(
            float mapX,
            float mapY,
            float mapW,
            float mapH,
            double rotateCenterX,
            double rotateCenterZ,
            float sinTheta,
            float cosTheta,
            double rMinX,
            double rMinZ,
            float scale,
            float rotationDegrees
    ) {
        public float worldToScreenX(double worldX, double worldZ) {
            double dx = worldX - rotateCenterX;
            double dz = worldZ - rotateCenterZ;
            double rx = dx * cosTheta - dz * sinTheta;
            return mapX + (float) ((rx - rMinX) * scale);
        }

        public float worldToScreenY(double worldX, double worldZ) {
            double dx = worldX - rotateCenterX;
            double dz = worldZ - rotateCenterZ;
            double rz = dx * sinTheta + dz * cosTheta;
            return mapY + mapH - (float) ((rz - rMinZ) * scale);
        }
    }

    public static MapTransform computeTransform(
            HoleMapState state,
            float canvasX,
            float canvasY,
            float canvasW,
            float canvasH
    ) {
        double teeX = state.teeX;
        double teeZ = state.teeZ;
        double basketX = state.basketX;
        double basketZ = state.basketZ;

        double dx = basketX - teeX;
        double dz = basketZ - teeZ;
        double theta = Math.atan2(dx, dz);
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float rotationDegrees = (float) Math.toDegrees(theta);

        double centerX = (teeX + basketX) / 2.0;
        double centerZ = (teeZ + basketZ) / 2.0;

        double rMinX = Double.POSITIVE_INFINITY;
        double rMinZ = Double.POSITIVE_INFINITY;
        double rMaxX = Double.NEGATIVE_INFINITY;
        double rMaxZ = Double.NEGATIVE_INFINITY;

        double padBlocks = 18.0;

        for (HoleMapSync.FairwaySegmentEntry seg : state.fairwaySegments) {
            double[] rs = rotated(centerX, centerZ, sinTheta, cosTheta, seg.startX(), seg.startZ());
            rMinX = Math.min(rMinX, rs[0]); rMinZ = Math.min(rMinZ, rs[1]);
            rMaxX = Math.max(rMaxX, rs[0]); rMaxZ = Math.max(rMaxZ, rs[1]);
            rs = rotated(centerX, centerZ, sinTheta, cosTheta, seg.endX(), seg.endZ());
            rMinX = Math.min(rMinX, rs[0]); rMinZ = Math.min(rMinZ, rs[1]);
            rMaxX = Math.max(rMaxX, rs[0]); rMaxZ = Math.max(rMaxZ, rs[1]);
        }

        double[] rs = rotated(centerX, centerZ, sinTheta, cosTheta, teeX, teeZ);
        rMinX = Math.min(rMinX, rs[0]); rMinZ = Math.min(rMinZ, rs[1]);
        rMaxX = Math.max(rMaxX, rs[0]); rMaxZ = Math.max(rMaxZ, rs[1]);

        rs = rotated(centerX, centerZ, sinTheta, cosTheta, basketX, basketZ);
        rMinX = Math.min(rMinX, rs[0]); rMinZ = Math.min(rMinZ, rs[1]);
        rMaxX = Math.max(rMaxX, rs[0]); rMaxZ = Math.max(rMaxZ, rs[1]);

        rs = rotated(centerX, centerZ, sinTheta, cosTheta, state.lieX, state.lieZ);
        rMinX = Math.min(rMinX, rs[0]); rMinZ = Math.min(rMinZ, rs[1]);
        rMaxX = Math.max(rMaxX, rs[0]); rMaxZ = Math.max(rMaxZ, rs[1]);

        rMinX -= padBlocks; rMinZ -= padBlocks;
        rMaxX += padBlocks; rMaxZ += padBlocks;

        double worldW = Math.max(1.0, rMaxX - rMinX);
        double worldH = Math.max(1.0, rMaxZ - rMinZ);

        float scaleX = canvasW / (float) worldW;
        float scaleY = canvasH / (float) worldH;
        float scale = Math.min(scaleX, scaleY);

        float drawnW = (float) (worldW * scale);
        float drawnH = (float) (worldH * scale);

        float offsetX = (canvasW - drawnW) / 2.0f;
        float offsetY = (canvasH - drawnH) / 2.0f;

        return new MapTransform(
                canvasX + offsetX,
                canvasY + offsetY,
                drawnW,
                drawnH,
                centerX, centerZ,
                sinTheta, cosTheta,
                rMinX, rMinZ,
                scale,
                rotationDegrees
        );
    }

    private static double[] rotated(double centerX, double centerZ, float sinTheta, float cosTheta,
                                    double wx, double wz) {
        double ddx = wx - centerX;
        double ddz = wz - centerZ;
        double rx = ddx * cosTheta - ddz * sinTheta;
        double rz = ddx * sinTheta + ddz * cosTheta;
        return new double[] { rx, rz };
    }

    public static void drawMapBackground(DrawContext ctx, MapTransform t) {
        ctx.fill((int) t.mapX(), (int) t.mapY(), (int) (t.mapX() + t.mapW()), (int) (t.mapY() + t.mapH()), COLOR_ROUGH);
    }

    public static void drawFairwaySegments(
            DrawContext ctx,
            MapTransform t,
            List<HoleMapSync.FairwaySegmentEntry> segments
    ) {
        for (HoleMapSync.FairwaySegmentEntry seg : segments) {
            float sx = t.worldToScreenX(seg.startX(), seg.startZ());
            float sy = t.worldToScreenY(seg.startX(), seg.startZ());
            float ex = t.worldToScreenX(seg.endX(), seg.endZ());
            float ey = t.worldToScreenY(seg.endX(), seg.endZ());
            float halfW = (float) (seg.width() * t.scale() / 2.0);

            // Draw a thick line by computing perpendicular offset
            double dx = ex - sx;
            double dz = ey - sy;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001) {
                // Draw a circle at the point
                drawFilledCircle(ctx, sx, sy, halfW, COLOR_FAIRWAY);
                continue;
            }
            double nx = -dz / len;
            double ny = dx / len;

            float x1 = sx + (float) (nx * halfW);
            float y1 = sy + (float) (ny * halfW);
            float x2 = ex + (float) (nx * halfW);
            float y2 = ey + (float) (ny * halfW);
            float x3 = ex - (float) (nx * halfW);
            float y3 = ey - (float) (ny * halfW);
            float x4 = sx - (float) (nx * halfW);
            float y4 = sy - (float) (ny * halfW);

            drawQuad(ctx, x1, y1, x2, y2, x3, y3, x4, y4, COLOR_FAIRWAY);

            // Round caps at segment endpoints to avoid gaps
            drawFilledCircle(ctx, sx, sy, halfW, COLOR_FAIRWAY);
            drawFilledCircle(ctx, ex, ey, halfW, COLOR_FAIRWAY);
        }
    }

    public static void drawGreen(DrawContext ctx, MapTransform t, int basketX, int basketZ) {
        float px = t.worldToScreenX(basketX, basketZ);
        float py = t.worldToScreenY(basketX, basketZ);
        float radius = 14.0f * t.scale();
        drawFilledCircle(ctx, px, py, Math.max(4.0f, radius), COLOR_GREEN);
    }

    public static void drawWaterGap(
            DrawContext ctx,
            MapTransform t,
            HoleMapState state
    ) {
        if (!state.hasWaterGap || state.waterGapStartFeet <= 0 || state.waterGapEndFeet <= state.waterGapStartFeet) {
            return;
        }

        float teeX = t.worldToScreenX(state.teeX, state.teeZ);
        float teeY = t.worldToScreenY(state.teeX, state.teeZ);
        float basketX = t.worldToScreenX(state.basketX, state.basketZ);
        float basketY = t.worldToScreenY(state.basketX, state.basketZ);

        double totalDist = Math.sqrt(
                (state.basketX - state.teeX) * (state.basketX - state.teeX)
                        + (state.basketZ - state.teeZ) * (state.basketZ - state.teeZ)
        );
        if (totalDist < 0.001) return;

        double startRatio = (state.waterGapStartFeet / 3.28084) / totalDist;
        double endRatio = (state.waterGapEndFeet / 3.28084) / totalDist;
        startRatio = Math.max(0.0, Math.min(1.0, startRatio));
        endRatio = Math.max(0.0, Math.min(1.0, endRatio));

        float sx = teeX + (basketX - teeX) * (float) startRatio;
        float sy = teeY + (basketY - teeY) * (float) startRatio;
        float ex = teeX + (basketX - teeX) * (float) endRatio;
        float ey = teeY + (basketY - teeY) * (float) endRatio;

        float bandWidth = Math.max(4.0f, 6.0f * t.scale());
        drawThickLine(ctx, sx, sy, ex, ey, bandWidth, COLOR_WATER);
    }

    public static void drawCorridorLine(
            DrawContext ctx,
            MapTransform t,
            int teeX,
            int teeZ,
            int basketX,
            int basketZ
    ) {
        float sx = t.worldToScreenX(teeX, teeZ);
        float sy = t.worldToScreenY(teeX, teeZ);
        float ex = t.worldToScreenX(basketX, basketZ);
        float ey = t.worldToScreenY(basketX, basketZ);
        drawDashedLine(ctx, sx, sy, ex, ey, 2.0f, 6.0f, COLOR_CORRIDOR);
    }

    public static void drawHazardOverlay(
            DrawContext ctx,
            MapTransform t,
            HoleMapState state
    ) {
        if (state.hazardGridData == null || state.hazardGridData.length == 0) {
            return;
        }

        int gridW = state.hazardGridWidth;
        int gridH = state.hazardGridHeight;
        int gridMinX = state.hazardGridMinX;
        int gridMinZ = state.hazardGridMinZ;
        byte[] grid = state.hazardGridData;

        // Hazard type colors: 0=none, 1=slope/rough (yellow-orange), 2=water (blue), 3=lava (red)
        int slopeRoughColor = 0x8CFF9A32;
        int waterColor = 0x8C3399FF;
        int lavaColor = 0x8CFF3333;

        for (int gz = 0; gz < gridH; gz++) {
            int worldZ = gridMinZ + gz;
            for (int gx = 0; gx < gridW; gx++) {
                int worldX = gridMinX + gx;
                int idx = gz * gridW + gx;
                if (idx >= 0 && idx < grid.length) {
                    byte hazardType = grid[idx];
                    int color;
                    if (hazardType == 1) {
                        color = slopeRoughColor;
                    } else if (hazardType == 2) {
                        color = waterColor;
                    } else if (hazardType == 3) {
                        color = lavaColor;
                    } else {
                        continue;
                    }
                    float px = t.worldToScreenX(worldX, worldZ);
                    float py = t.worldToScreenY(worldX, worldZ);
                    float blockSize = t.scale();
                    ctx.fill((int) px, (int) py, (int) (px + blockSize), (int) (py + blockSize), color);
                }
            }
        }
    }

    public static void drawTeeMarker(DrawContext ctx, MapTransform t, int teeX, int teeZ) {
        float px = t.worldToScreenX(teeX, teeZ);
        float py = t.worldToScreenY(teeX, teeZ);
        drawDiamond(ctx, px, py, 5.0f, COLOR_TEE);
    }

    public static void drawBasketMarker(DrawContext ctx, MapTransform t, int basketX, int basketZ) {
        float px = t.worldToScreenX(basketX, basketZ);
        float py = t.worldToScreenY(basketX, basketZ);
        drawFilledCircle(ctx, px, py, 4.5f, COLOR_BASKET);
        drawCircleOutline(ctx, px, py, 6.5f, 0xFFDDDDDD);
    }

    public static void drawPlayerMarker(DrawContext ctx, MapTransform t, int lieX, int lieZ, int headingYaw) {
        float px = t.worldToScreenX(lieX, lieZ);
        float py = t.worldToScreenY(lieX, lieZ);
        float radius = 2.5f; // fixed small size, proportional to basket (4.5f)

        // Dark outline
        drawFilledCircle(ctx, px, py, radius + 1.5f, 0xFF1A1A1A);
        // White ring
        drawFilledCircle(ctx, px, py, radius + 0.5f, 0xFFFFFFFF);
        // Blue center for visibility against all backgrounds
        drawFilledCircle(ctx, px, py, radius, 0xFF3399FF);

        // Heading triangle — Minecraft facing vector in rotated map coordinates
        // Screen +Y is up (toward basket). dirX is screen-X, dirY is screen-Y (negative = up).
        // Map rotates world by +theta to align tee-to-basket with screen-up, so player heading becomes (headingYaw + theta).
        double effectiveYaw = Math.toRadians(headingYaw + t.rotationDegrees());
        float dirX = (float) -Math.sin(effectiveYaw);
        float dirY = (float) -Math.cos(effectiveYaw);
        float tipX = px + dirX * (radius + 5.0f);
        float tipY = py + dirY * (radius + 5.0f);
        float backX = px - dirX * (radius * 0.5f);
        float backY = py - dirY * (radius * 0.5f);
        float perpX = -dirY;
        float perpY = dirX;
        float halfW = 2.0f;
        float leftX = backX + perpX * halfW;
        float leftY = backY + perpY * halfW;
        float rightX = backX - perpX * halfW;
        float rightY = backY - perpY * halfW;

        // Dark backing outline
        drawTriangle(ctx,
                tipX + dirX * 0.8f, tipY + dirY * 0.8f,
                leftX + perpX * 0.8f, leftY + perpY * 0.8f,
                rightX - perpX * 0.8f, rightY - perpY * 0.8f,
                0xFF1A1A1A);
        // Blue arrow
        drawTriangle(ctx, tipX, tipY, leftX, leftY, rightX, rightY, 0xFF3399FF);
    }

    // --- low-level drawing ---

    private static void drawQuad(DrawContext ctx, float x1, float y1, float x2, float y2,
                                 float x3, float y3, float x4, float y4, int color) {
        // Simple convex quad via two triangles
        drawTriangle(ctx, x1, y1, x2, y2, x3, y3, color);
        drawTriangle(ctx, x1, y1, x3, y3, x4, y4, color);
    }

    private static void drawTriangle(DrawContext ctx, float x1, float y1, float x2, float y2,
                                     float x3, float y3, int color) {
        int minX = (int) Math.floor(Math.min(x1, Math.min(x2, x3)));
        int maxX = (int) Math.ceil(Math.max(x1, Math.max(x2, x3)));
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (isPointInTriangle(px + 0.5f, py + 0.5f, x1, y1, x2, y2, x3, y3)) {
                    ctx.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private static boolean isPointInTriangle(float px, float py, float x1, float y1,
                                              float x2, float y2, float x3, float y3) {
        float d1 = sign(px, py, x1, y1, x2, y2);
        float d2 = sign(px, py, x2, y2, x3, y3);
        float d3 = sign(px, py, x3, y3, x1, y1);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static float sign(float px, float py, float x1, float y1, float x2, float y2) {
        return (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
    }

    public static void drawFilledCircle(DrawContext ctx, float cx, float cy, float radius, int color) {
        if (radius <= 0) return;
        int minY = (int) Math.ceil(cy - radius);
        int maxY = (int) Math.floor(cy + radius);
        float rSq = radius * radius;
        for (int py = minY; py <= maxY; py++) {
            float dy = py - cy;
            int span = (int) Math.floor(Math.sqrt(Math.max(0.0f, rSq - (dy * dy))));
            int left = (int) Math.floor(cx - span);
            int right = (int) Math.ceil(cx + span) + 1;
            ctx.fill(left, py, right, py + 1, color);
        }
    }

    private static void drawCircleOutline(DrawContext ctx, float cx, float cy, float radius, int color) {
        for (int deg = 0; deg < 360; deg += 8) {
            double rad = Math.toRadians(deg);
            int px = Math.round(cx + (float) Math.cos(rad) * radius);
            int py = Math.round(cy + (float) Math.sin(rad) * radius);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawDiamond(DrawContext ctx, float cx, float cy, float size, int color) {
        drawTriangle(ctx, cx, cy - size, cx - size, cy, cx + size, cy, color);
        drawTriangle(ctx, cx, cy + size, cx - size, cy, cx + size, cy, color);
    }

    private static void drawThickLine(DrawContext ctx, float x1, float y1, float x2, float y2,
                                      float thickness, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) {
            drawFilledCircle(ctx, x1, y1, thickness / 2.0f, color);
            return;
        }
        double nx = -dy / len;
        double ny = dx / len;
        float hw = thickness / 2.0f;
        float ax = x1 + (float) (nx * hw);
        float ay = y1 + (float) (ny * hw);
        float bx = x2 + (float) (nx * hw);
        float by = y2 + (float) (ny * hw);
        float cx2 = x2 - (float) (nx * hw);
        float cy2 = y2 - (float) (ny * hw);
        float dx2 = x1 - (float) (nx * hw);
        float dy2 = y1 - (float) (ny * hw);
        drawQuad(ctx, ax, ay, bx, by, cx2, cy2, dx2, dy2, color);
        drawFilledCircle(ctx, x1, y1, hw, color);
        drawFilledCircle(ctx, x2, y2, hw, color);
    }

    private static void drawDashedLine(DrawContext ctx, float x1, float y1, float x2, float y2,
                                       float dashLen, float gapLen, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) return;
        double nx = dx / len;
        double ny = dy / len;
        double pos = 0;
        boolean drawing = true;
        while (pos < len) {
            double segLen = drawing ? dashLen : gapLen;
            if (pos + segLen > len) segLen = len - pos;
            if (drawing) {
                double sx = x1 + nx * pos;
                double sy = y1 + ny * pos;
                double ex = x1 + nx * (pos + segLen);
                double ey = y1 + ny * (pos + segLen);
                drawThickLine(ctx, (float) sx, (float) sy, (float) ex, (float) ey, 1.5f, color);
            }
            pos += segLen;
            drawing = !drawing;
        }
    }
}
