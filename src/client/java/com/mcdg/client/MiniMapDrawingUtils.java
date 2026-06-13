package com.mcdg.client;

import net.minecraft.client.gui.DrawContext;

public final class MiniMapDrawingUtils {
    private MiniMapDrawingUtils() {
    }

    public static boolean isPointInsideCircle(int x, int y, float centerX, float centerY, float radiusSq) {
        float dx = x - centerX;
        float dy = y - centerY;
        return ((dx * dx) + (dy * dy)) <= radiusSq;
    }

    public static void fillRectClipped(
            DrawContext drawContext,
            int x,
            int y,
            int width,
            int height,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadiusSq
    ) {
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) {
                if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                    drawContext.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    public static void drawLineClipped(
            DrawContext drawContext,
            int x1, int y1, int x2, int y2,
            int color,
            int thickness,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float clipRadiusSq = clipRadius * clipRadius;
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            // Draw thickness
            for (int tx = -thickness / 2; tx <= thickness / 2; tx++) {
                for (int ty = -thickness / 2; ty <= thickness / 2; ty++) {
                    int px = x1 + tx;
                    int py = y1 + ty;
                    if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                        drawContext.fill(px, py, px + 1, py + 1, color);
                    }
                }
            }

            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    public static void drawPixelClipped(
            DrawContext drawContext,
            int px,
            int py,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        if (!isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadius * clipRadius)) {
            return;
        }
        drawContext.fill(px, py, px + 1, py + 1, color);
    }

    public static void drawCircleOutlineClipped(
            DrawContext drawContext,
            float centerX,
            float centerY,
            float radius,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float clipRadiusSq = clipRadius * clipRadius;
        for (int degrees = 0; degrees < 360; degrees += 2) {
            double radians = Math.toRadians(degrees);
            int px = Math.round(centerX + (float) Math.cos(radians) * radius);
            int py = Math.round(centerY + (float) Math.sin(radians) * radius);
            if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                drawContext.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    public static void drawCircleBandClipped(
            DrawContext drawContext,
            float centerX,
            float centerY,
            float radius,
            int thickness,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        int half = Math.max(0, thickness / 2);
        for (int offset = -half; offset <= half; offset++) {
            drawCircleOutlineClipped(
                    drawContext,
                    centerX,
                    centerY,
                    Math.max(1.0f, radius + offset),
                    color,
                    clipCenterX,
                    clipCenterY,
                    clipRadius
            );
        }
    }

    public static void drawCircleOutline(DrawContext drawContext, float centerX, float centerY, float radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 8) {
            double radians = Math.toRadians(degrees);
            int px = Math.round(centerX + (float) Math.cos(radians) * radius);
            int py = Math.round(centerY + (float) Math.sin(radians) * radius);
            drawContext.fill(px, py, px + 1, py + 1, color);
        }
    }

    public static void drawFilledCircle(DrawContext drawContext, float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0f) {
            return;
        }

        int minY = (int) Math.ceil(centerY - radius);
        int maxY = (int) Math.floor(centerY + radius);
        float radiusSq = radius * radius;
        for (int py = minY; py <= maxY; py++) {
            float dy = py - centerY;
            int span = (int) Math.floor(Math.sqrt(Math.max(0.0f, radiusSq - (dy * dy))));
            int left = (int) Math.floor(centerX - span);
            int right = (int) Math.ceil(centerX + span) + 1;
            drawContext.fill(left, py, right, py + 1, color);
        }
    }

    public static void drawHeadingTriangleClipped(
            DrawContext drawContext,
            float centerX,
            float centerY,
            float angleDegrees,
            float tipDistance,
            float halfBaseWidth,
            int fillColor,
            int outlineColor,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        double radians = Math.toRadians(angleDegrees);
        float dirX = (float) Math.cos(radians);
        float dirY = (float) Math.sin(radians);
        float perpX = -dirY;
        float perpY = dirX;

        float tipX = centerX + (dirX * tipDistance);
        float tipY = centerY + (dirY * tipDistance);
        float baseCenterX = centerX - (dirX * (tipDistance * 0.85f));
        float baseCenterY = centerY - (dirY * (tipDistance * 0.85f));
        float leftX = baseCenterX + (perpX * halfBaseWidth);
        float leftY = baseCenterY + (perpY * halfBaseWidth);
        float rightX = baseCenterX - (perpX * halfBaseWidth);
        float rightY = baseCenterY - (perpY * halfBaseWidth);

        drawFilledTriangleClipped(drawContext, tipX, tipY, leftX, leftY, rightX, rightY, outlineColor, clipCenterX, clipCenterY, clipRadius + 0.75f);
        drawFilledTriangleClipped(
                drawContext,
                centerX + ((tipX - centerX) * 0.88f),
                centerY + ((tipY - centerY) * 0.88f),
                centerX + ((leftX - centerX) * 0.82f),
                centerY + ((leftY - centerY) * 0.82f),
                centerX + ((rightX - centerX) * 0.82f),
                centerY + ((rightY - centerY) * 0.82f),
                fillColor,
                clipCenterX,
                clipCenterY,
                clipRadius
        );
    }

    public static void drawFilledTriangleClipped(
            DrawContext drawContext,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float clipRadiusSq = clipRadius * clipRadius;
        int minX = (int) Math.floor(Math.min(x1, Math.min(x2, x3)));
        int maxX = (int) Math.ceil(Math.max(x1, Math.max(x2, x3)));
        int minY = (int) Math.floor(Math.min(y1, Math.min(y2, y3)));
        int maxY = (int) Math.ceil(Math.max(y1, Math.max(y2, y3)));

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (!isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                    continue;
                }
                if (isPointInTriangle(px + 0.5f, py + 0.5f, x1, y1, x2, y2, x3, y3)) {
                    drawContext.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    public static boolean isPointInTriangle(
            float px,
            float py,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3
    ) {
        float d1 = crossSign(px, py, x1, y1, x2, y2);
        float d2 = crossSign(px, py, x2, y2, x3, y3);
        float d3 = crossSign(px, py, x3, y3, x1, y1);
        boolean hasNeg = (d1 < 0.0f) || (d2 < 0.0f) || (d3 < 0.0f);
        boolean hasPos = (d1 > 0.0f) || (d2 > 0.0f) || (d3 > 0.0f);
        return !(hasNeg && hasPos);
    }

    public static float crossSign(float px, float py, float ax, float ay, float bx, float by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }

}
