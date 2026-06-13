package com.mcdg.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

public final class MiniMapRenderer {
    public static final int PASSIVE_MINIMAP_SPAN_BLOCKS = 96;
    public static final long MINIMAP_STALE_TIMEOUT_MS = 15000L;
    private static final int MINIMAP_PADDING = 8;
    static final int MINIMAP_COLOR_UNSET = Integer.MIN_VALUE;
    public static final int MINIMAP_JOIN_PRIME_TICKS = 100;
    private static final int MINIMAP_TEXTURE_SIZE = 128;
    private static final int[] MINIMAP_SIZES = { 84, 104, 126 };
    private static final int[] MINIMAP_SURFACE_ALPHA = { 0xD0, 0xB8, 0x9A };
    private static final int HUD_CARD_BORDER = 0xA63A4E66;
    private static final int HUD_CARD_MUTED_TEXT = 0xAAB8CC;
    private static final Identifier MINIMAP_TEE_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_tee_marker.png");
    private static final Identifier MINIMAP_BASKET_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_basket_marker.png");
    private static final Identifier MINIMAP_LIE_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_lie_marker.png");

    private static McdgClientMod.MiniMapState miniMapState;
    private static long miniMapReceivedAtMs;
    private static int miniMapStyleIndex = 1;
    private static MiniMapRenderCache miniMapRenderCache;
    private static long hudVisibleSinceMs;
    private static MiniMapRenderDebug miniMapRenderDebug = MiniMapRenderDebug.empty();
    private static long lastMiniMapRenderAtMs = 0L;
    private static boolean miniMapJoinWarmupPending;
    private static int miniMapJoinPrimeTicksRemaining;

    private MiniMapRenderer() {}

    public static void setMiniMapState(McdgClientMod.MiniMapState state) {
        miniMapState = state;
    }

    public static McdgClientMod.MiniMapState getMiniMapState() {
        return miniMapState;
    }

    public static void setMiniMapReceivedAtMs(long timestamp) {
        miniMapReceivedAtMs = timestamp;
    }

    public static long getMiniMapReceivedAtMs() {
        return miniMapReceivedAtMs;
    }

    public static void setMiniMapStyleIndex(int index) {
        miniMapStyleIndex = index;
    }

    public static int getMiniMapStyleIndex() {
        return miniMapStyleIndex;
    }

    public static void setHudVisibleSinceMs(long timestamp) {
        hudVisibleSinceMs = timestamp;
    }

    public static long getHudVisibleSinceMs() {
        return hudVisibleSinceMs;
    }

    public static void setHudVisibleSinceMsFromSync(long timestamp) {
        hudVisibleSinceMs = timestamp;
    }

    public static void setMiniMapJoinWarmupPending(boolean pending) {
        miniMapJoinWarmupPending = pending;
    }

    public static boolean isMiniMapJoinWarmupPending() {
        return miniMapJoinWarmupPending;
    }

    public static void setMiniMapJoinPrimeTicksRemaining(int ticks) {
        miniMapJoinPrimeTicksRemaining = ticks;
    }

    public static int getMiniMapJoinPrimeTicksRemaining() {
        return miniMapJoinPrimeTicksRemaining;
    }

    public static void setLastMiniMapRenderAtMs(long timestamp) {
        lastMiniMapRenderAtMs = timestamp;
    }

    public static long getLastMiniMapRenderAtMs() {
        return lastMiniMapRenderAtMs;
    }

    public static void clearMiniMapState() {
        miniMapState = null;
        miniMapReceivedAtMs = 0L;
    }

    public static void clearMiniMapRenderCacheState() {
        miniMapRenderCache = null;
    }

    public static boolean isRoundWaypointModeActive() {
        return (System.currentTimeMillis() - miniMapReceivedAtMs) <= MINIMAP_STALE_TIMEOUT_MS;
    }

    private record MiniMapRenderCache(
            Identifier textureId,
            NativeImageBackedTexture texture,
            int mapSpan,
            int centerX,
            int centerZ
    ) {
        private boolean matches(int activeMapSpan, int playerFeetX, int playerFeetZ) {
            return mapSpan == activeMapSpan
                    && centerX == playerFeetX
                    && centerZ == playerFeetZ;
        }
    }

    private record MiniMapRenderDebug(int chunkUnloadedSourcePixels, int unresolvedSurfacePixels) {
        private static MiniMapRenderDebug empty() {
            return new MiniMapRenderDebug(0, 0);
        }

        private static MiniMapRenderDebug serverOnly() {
            int allPixels = MINIMAP_TEXTURE_SIZE * MINIMAP_TEXTURE_SIZE;
            return new MiniMapRenderDebug(allPixels, allPixels);
        }

        public int unresolvedSurfacePixels() {
            return unresolvedSurfacePixels;
        }
    }


























    public static float[] rotateMiniMapVector(float x, float y, float rotationDegrees) {
        double radians = Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[] {
                (x * cos) - (y * sin),
                (x * sin) + (y * cos)
        };
    }



    private static void drawMiniMapHoleGuides(
            DrawContext drawContext,
            McdgClientMod.MiniMapState state,
            double centerWorldX,
            double centerWorldZ,
            int mapCenterX,
            int mapCenterY,
            float mapScale,
            float mapRotationDegrees,
            float hudAlpha,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float teeDx = (float) (((state.teeX() + 0.5d) - centerWorldX) * mapScale);
        float teeDz = (float) (((state.teeZ() + 0.5d) - centerWorldZ) * mapScale);
        float[] rotatedTee = rotateMiniMapVector(teeDx, teeDz, mapRotationDegrees);
        int teePx = Math.round(mapCenterX + rotatedTee[0]);
        int teePy = Math.round(mapCenterY + rotatedTee[1]);

        float basketDx = (float) (((state.basketX() + 0.5d) - centerWorldX) * mapScale);
        float basketDz = (float) (((state.basketZ() + 0.5d) - centerWorldZ) * mapScale);
        float[] rotatedBasket = rotateMiniMapVector(basketDx, basketDz, mapRotationDegrees);
        float basketPx = mapCenterX + rotatedBasket[0];
        float basketPy = mapCenterY + rotatedBasket[1];

        int ring100Color = HudUtil.withAlpha(0xE6F2D14A, hudAlpha);
        int ring200Color = HudUtil.withAlpha(0xE664D5FF, hudAlpha);
        int ring100RadiusPx = Math.max(2, Math.round((100.0f / 3.28084f) * mapScale));
        int ring200RadiusPx = Math.max(2, Math.round((200.0f / 3.28084f) * mapScale));

        MiniMapDrawingUtils.drawCircleBandClipped(drawContext, basketPx, basketPy, ring200RadiusPx, 1, ring200Color, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawCircleBandClipped(drawContext, basketPx, basketPy, ring100RadiusPx, 1, ring100Color, clipCenterX, clipCenterY, clipRadius);

        drawMiniMapIconClipped(drawContext, MINIMAP_TEE_MARKER_TEXTURE, teePx, teePy, 8, clipCenterX, clipCenterY, clipRadius);
        drawMiniMapIconClipped(drawContext, MINIMAP_BASKET_MARKER_TEXTURE, Math.round(basketPx), Math.round(basketPy), 8, clipCenterX, clipCenterY, clipRadius);

        drawMiniMapBasketFlagClipped(
                drawContext,
                Math.round(basketPx),
                Math.round(basketPy),
                HudUtil.withAlpha(0xFF1E232B, hudAlpha),
                HudUtil.withAlpha(0xFFF2F4F8, hudAlpha),
                HudUtil.withAlpha(0xFF121417, hudAlpha),
                clipCenterX,
                clipCenterY,
                clipRadius
        );

        // Draw corridor boundary lines (left and right edges of the safe fairway)
        drawMiniMapCorridorBoundaries(
                drawContext,
                state,
                centerWorldX,
                centerWorldZ,
                mapCenterX,
                mapCenterY,
                mapScale,
                mapRotationDegrees,
                hudAlpha,
                clipCenterX,
                clipCenterY,
                clipRadius
        );
    }
    private static void drawMiniMapCorridorBoundaries(
            DrawContext drawContext,
            McdgClientMod.MiniMapState state,
            double centerWorldX,
            double centerWorldZ,
            int mapCenterX,
            int mapCenterY,
            float mapScale,
            float mapRotationDegrees,
            float hudAlpha,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        if (state.corridorHalfWidth() <= 0) return;

        int corridorColor = HudUtil.withAlpha(0xE6FFB366, hudAlpha); // Light orange/yellow corridor
        int halfWidth = state.corridorHalfWidth();

        // Calculate tee and basket positions
        double teeX = state.teeX() + 0.5d;
        double teeZ = state.teeZ() + 0.5d;
        double basketX = state.basketX() + 0.5d;
        double basketZ = state.basketZ() + 0.5d;

        // Calculate direction vector from tee to basket
        double dx = basketX - teeX;
        double dz = basketZ - teeZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return;

        // Normalize and get perpendicular vector
        double dirX = dx / length;
        double dirZ = dz / length;
        double perpX = -dirZ; // Perpendicular to direction
        double perpZ = dirX;

        // Calculate left and right corridor edge points at tee and basket
        double leftTeeX = teeX + perpX * halfWidth;
        double leftTeeZ = teeZ + perpZ * halfWidth;
        double rightTeeX = teeX - perpX * halfWidth;
        double rightTeeZ = teeZ - perpZ * halfWidth;

        double leftBasketX = basketX + perpX * halfWidth;
        double leftBasketZ = basketZ + perpZ * halfWidth;
        double rightBasketX = basketX - perpX * halfWidth;
        double rightBasketZ = basketZ - perpZ * halfWidth;

        // Convert to screen coordinates
        float[] leftTeeScreen = rotateMiniMapVector(
            (float) ((leftTeeX - centerWorldX) * mapScale),
            (float) ((leftTeeZ - centerWorldZ) * mapScale),
            mapRotationDegrees
        );
        float[] rightTeeScreen = rotateMiniMapVector(
            (float) ((rightTeeX - centerWorldX) * mapScale),
            (float) ((rightTeeZ - centerWorldZ) * mapScale),
            mapRotationDegrees
        );
        float[] leftBasketScreen = rotateMiniMapVector(
            (float) ((leftBasketX - centerWorldX) * mapScale),
            (float) ((leftBasketZ - centerWorldZ) * mapScale),
            mapRotationDegrees
        );
        float[] rightBasketScreen = rotateMiniMapVector(
            (float) ((rightBasketX - centerWorldX) * mapScale),
            (float) ((rightBasketZ - centerWorldZ) * mapScale),
            mapRotationDegrees
        );

        int x1 = Math.round(mapCenterX + leftTeeScreen[0]);
        int y1 = Math.round(mapCenterY + leftTeeScreen[1]);
        int x2 = Math.round(mapCenterX + leftBasketScreen[0]);
        int y2 = Math.round(mapCenterY + leftBasketScreen[1]);
        int x3 = Math.round(mapCenterX + rightTeeScreen[0]);
        int y3 = Math.round(mapCenterY + rightTeeScreen[1]);
        int x4 = Math.round(mapCenterX + rightBasketScreen[0]);
        int y4 = Math.round(mapCenterY + rightBasketScreen[1]);

        // Draw left boundary line
        MiniMapDrawingUtils.drawLineClipped(drawContext, x1, y1, x2, y2, corridorColor, 1, clipCenterX, clipCenterY, clipRadius);
        // Draw right boundary line
        MiniMapDrawingUtils.drawLineClipped(drawContext, x3, y3, x4, y4, corridorColor, 1, clipCenterX, clipCenterY, clipRadius);
    }
    private static void drawMiniMapBasketFlagClipped(
            DrawContext drawContext,
            int centerX,
            int centerY,
            int poleColor,
            int flagColor,
            int outlineColor,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        for (int y = centerY - 5; y <= centerY + 3; y++) {
            MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX, y, poleColor, clipCenterX, clipCenterY, clipRadius);
        }

        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 1, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 2, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 3, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 1, centerY - 4, outlineColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 2, centerY - 4, outlineColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 1, centerY - 3, outlineColor, clipCenterX, clipCenterY, clipRadius);

        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 1, centerY - 5, flagColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 2, centerY - 5, flagColor, clipCenterX, clipCenterY, clipRadius);
        MiniMapDrawingUtils.drawPixelClipped(drawContext, centerX + 1, centerY - 4, flagColor, clipCenterX, clipCenterY, clipRadius);
    }

    private static void drawMiniMapIconClipped(
            DrawContext drawContext,
            Identifier texture,
            int centerX,
            int centerY,
            int size,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        if (size <= 0 || !MiniMapDrawingUtils.isPointInsideCircle(centerX, centerY, clipCenterX, clipCenterY, clipRadius * clipRadius)) {
            return;
        }

        int half = size / 2;
        drawContext.drawTexture(texture, centerX - half, centerY - half, 0, 0, size, size, size, size);
    }




    public static void renderHoleMiniMapOverlay(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        int mapSpan = resolveActiveMiniMapSpan(client);
        boolean debugHud = client.getDebugHud().shouldShowDebugHud();

        refreshMiniMapRenderCache(client, mapSpan);

        int panelX = MINIMAP_PADDING;
        int panelY = debugHud ? 76 : MINIMAP_PADDING;
        int miniMapSize = MINIMAP_SIZES[Math.max(0, Math.min(MINIMAP_SIZES.length - 1, miniMapStyleIndex))];
        int surfaceAlpha = MINIMAP_SURFACE_ALPHA[Math.max(0, Math.min(MINIMAP_SURFACE_ALPHA.length - 1, miniMapStyleIndex))];
        float hudAlpha = hudFadeAlpha();

        String holeLabel = "Local Navigation";
        int headerTextW = client.textRenderer.getWidth(holeLabel) + 8;
        int headerW = Math.max(miniMapSize, headerTextW);
        int headerH = 12;
        HudUtil.drawCard(drawContext, client, panelX, panelY, headerW, headerH, null, hudAlpha);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(holeLabel).formatted(Formatting.GRAY), panelX + 4, panelY + 2, HudUtil.withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));

        int mapX = panelX;
        int mapY = panelY + headerH + 2;
        int mapCenterX = mapX + (miniMapSize / 2);
        int mapCenterY = mapY + (miniMapSize / 2);
        float mapScale = miniMapSize / Math.max(1.0f, (float) mapSpan);
        double playerWorldX = client.player.getX();
        double playerWorldZ = client.player.getZ();
        int playerFeetX = net.minecraft.util.math.MathHelper.floor(playerWorldX);
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(playerWorldZ);
        double centerBlockX = (miniMapRenderCache != null ? miniMapRenderCache.centerX() : playerFeetX) + 0.5d;
        double centerBlockZ = (miniMapRenderCache != null ? miniMapRenderCache.centerZ() : playerFeetZ) + 0.5d;
        float texScale = (float) miniMapSize / MINIMAP_TEXTURE_SIZE;
        float mapRotationDegrees = resolveMiniMapHeadingRotationDegrees(client);
        float subBlockShiftX = (float) (playerWorldX - centerBlockX) * texScale;
        float subBlockShiftZ = (float) (playerWorldZ - centerBlockZ) * texScale;
        float playerFacingOnMapDegrees = normalizeDegrees((180.0f - client.player.getYaw()) - mapRotationDegrees - 90.0f);
        int playerPx = mapCenterX;
        int playerPz = mapCenterY;
        float mapRadius = (miniMapSize / 2.0f) - 1.0f;
        MiniMapDrawingUtils.drawFilledCircle(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha((surfaceAlpha << 24) | 0x121212, hudAlpha));

        drawMiniMapBiomeFallbackSurface(
            drawContext,
            client,
            mapX,
            mapY,
            miniMapSize,
            mapSpan,
            playerWorldX,
            playerWorldZ,
            mapRotationDegrees,
            hudAlpha
        );

        if (miniMapRenderCache != null && miniMapRenderCache.textureId() != null) {
            drawContext.enableScissor(mapX, mapY, mapX + miniMapSize, mapY + miniMapSize);
            var matrices = drawContext.getMatrices();
            matrices.push();
            matrices.translate(mapCenterX, mapCenterY, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mapRotationDegrees));
            matrices.translate((-miniMapSize / 2.0f) - subBlockShiftX, (-miniMapSize / 2.0f) - subBlockShiftZ, 0);
            matrices.scale(texScale, texScale, 1.0f);
            drawContext.drawTexture(miniMapRenderCache.textureId(), 0, 0, 0, 0, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE);
            matrices.pop();

            McdgClientMod.MiniMapState state = miniMapState;
            if (state != null && (System.currentTimeMillis() - miniMapReceivedAtMs) <= MINIMAP_STALE_TIMEOUT_MS) {
                HazardOverlayRenderer.drawMiniMapStrictHazardOverlay(
                    drawContext,
                    client,
                    state,
                    playerWorldX,
                    playerWorldZ,
                    mapCenterX,
                    mapCenterY,
                    mapScale,
                    mapRotationDegrees,
                    hudAlpha,
                    mapCenterX,
                    mapCenterY,
                    mapRadius
                );

                drawMiniMapHoleGuides(
                        drawContext,
                        state,
                        playerWorldX,
                        playerWorldZ,
                        mapCenterX,
                        mapCenterY,
                        mapScale,
                        mapRotationDegrees,
                        hudAlpha,
                        mapCenterX,
                        mapCenterY,
                        mapRadius
                );
            }

            WaypointManager.drawWaypointsOnMiniMap(drawContext, client, mapCenterX, mapCenterY, playerWorldX, playerWorldZ, mapScale, mapRotationDegrees, hudAlpha, WaypointManager.isWaypointLabelsVisible(), mapCenterX, mapCenterY, mapRadius);

                drawMiniMapIconClipped(
                    drawContext,
                    MINIMAP_LIE_MARKER_TEXTURE,
                    playerPx,
                    playerPz,
                    9,
                    mapCenterX,
                    mapCenterY,
                    mapRadius
                );

            MiniMapDrawingUtils.drawHeadingTriangleClipped(
                    drawContext,
                    playerPx,
                    playerPz,
                    playerFacingOnMapDegrees,
                    8.0f,
                    5.0f,
                    HudUtil.withAlpha(0xFFFF5A3D, hudAlpha),
                    HudUtil.withAlpha(0xFF10161F, hudAlpha),
                    mapCenterX,
                    mapCenterY,
                    mapRadius
            );
                    MiniMapDrawingUtils.drawCircleBandClipped(
                        drawContext,
                        playerPx,
                        playerPz,
                        6,
                        1,
                        HudUtil.withAlpha(0xFF7CFF6B, hudAlpha),
                        mapCenterX,
                        mapCenterY,
                        mapRadius
                    );
            MiniMapDrawingUtils.drawCircleOutline(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha(HUD_CARD_BORDER, hudAlpha));
            drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, mapRotationDegrees, hudAlpha);
            drawContext.disableScissor();
        } else {
            drawMiniMapBiomeFallbackSurface(
                    drawContext,
                    client,
                    mapX,
                    mapY,
                    miniMapSize,
                    mapSpan,
                    playerWorldX,
                    playerWorldZ,
                    mapRotationDegrees,
                    hudAlpha
            );

            WaypointManager.drawWaypointsOnMiniMap(drawContext, client, mapCenterX, mapCenterY, playerWorldX, playerWorldZ, mapScale, mapRotationDegrees, hudAlpha, WaypointManager.isWaypointLabelsVisible(), mapCenterX, mapCenterY, mapRadius);

            drawMiniMapIconClipped(
                    drawContext,
                    MINIMAP_LIE_MARKER_TEXTURE,
                    playerPx,
                    playerPz,
                    9,
                    mapCenterX,
                    mapCenterY,
                    mapRadius
            );

            MiniMapDrawingUtils.drawHeadingTriangleClipped(
                    drawContext,
                    playerPx,
                    playerPz,
                    playerFacingOnMapDegrees,
                    8.0f,
                    5.0f,
                    HudUtil.withAlpha(0xFFFF5A3D, hudAlpha),
                    HudUtil.withAlpha(0xFF10161F, hudAlpha),
                    mapCenterX,
                    mapCenterY,
                    mapRadius
            );

            MiniMapDrawingUtils.drawCircleBandClipped(
                        drawContext,
                        playerPx,
                        playerPz,
                        6,
                        1,
                        HudUtil.withAlpha(0xFF7CFF6B, hudAlpha),
                        mapCenterX,
                        mapCenterY,
                        mapRadius
                    );

            MiniMapDrawingUtils.drawCircleOutline(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha(HUD_CARD_BORDER, hudAlpha));
            drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, mapRotationDegrees, hudAlpha);
        }
    }

    public static void refreshMiniMapRenderCache(MinecraftClient client, int mapSpan) {
        if (client == null || mapSpan <= 0) {
            return;
        }

        int playerFeetX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());

        if (miniMapRenderCache != null && miniMapRenderCache.matches(mapSpan, playerFeetX, playerFeetZ)) {
            if (!miniMapJoinWarmupPending) {
                if (miniMapRenderDebug.unresolvedSurfacePixels() <= 0) {
                    return;
                }
                long now = System.currentTimeMillis();
                if ((now - lastMiniMapRenderAtMs) < 350L) {
                    return;
                }
            }
        }

        MiniMapRenderCache previousCache = miniMapRenderCache;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, false);
        try {
            boolean renderedFromClientWorld = renderMiniMapFromClientWorld(image, client, mapSpan);
            if (!renderedFromClientWorld) {
                miniMapRenderDebug = MiniMapRenderDebug.serverOnly();
                image.close();
                return;
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier textureId = client.getTextureManager().registerDynamicTexture("mcdg_minimap", texture);
            texture.upload();
            miniMapRenderCache = new MiniMapRenderCache(textureId, texture, mapSpan, playerFeetX, playerFeetZ);
            if (previousCache != null && previousCache != miniMapRenderCache) {
                clearMiniMapRenderCache(client, previousCache);
            }
            lastMiniMapRenderAtMs = System.currentTimeMillis();
        } catch (RuntimeException ex) {
            image.close();
            throw ex;
        }
    }

    private static boolean renderMiniMapFromClientWorld(NativeImage image, MinecraftClient client, int mapSpan) {
        if (client.world == null || mapSpan <= 0) {
            return false;
        }

        int playerFeetX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
        float texDenominator = Math.max(1.0f, (float) MINIMAP_TEXTURE_SIZE);
        int centerPx = MINIMAP_TEXTURE_SIZE / 2;
        int centerPy = MINIMAP_TEXTURE_SIZE / 2;
        float textureRadius = Math.max(1.0f, (MINIMAP_TEXTURE_SIZE / 2.0f) - 0.5f);
        float textureRadiusSq = textureRadius * textureRadius;
        double centerWorldX = (playerFeetX + 0.5d);
        double centerWorldZ = (playerFeetZ + 0.5d);
        int chunkUnloadedSourcePixels = 0;
        int unresolvedSurfacePixels = 0;

        for (int py = 0; py < MINIMAP_TEXTURE_SIZE; py++) {
            float dz = (py - centerPy) / texDenominator;
            int worldZ = net.minecraft.util.math.MathHelper.floor(centerWorldZ + (dz * mapSpan));
            for (int px = 0; px < MINIMAP_TEXTURE_SIZE; px++) {
                float textureDx = px - centerPx;
                float textureDy = py - centerPy;
                if (((textureDx * textureDx) + (textureDy * textureDy)) > textureRadiusSq) {
                    image.setColor(px, py, TerrainSampler.argbToAbgr(0x00000000));
                    continue;
                }

                float dx = (px - centerPx) / texDenominator;
                int worldX = net.minecraft.util.math.MathHelper.floor(centerWorldX + (dx * mapSpan));

                TerrainSampler.TerrainSampleResult terrainSample = TerrainSampler.sampleClientWorldTerrain(client.world, worldX, worldZ);
                boolean usedClientSample = terrainSample.color() != MINIMAP_COLOR_UNSET;
                int baseColor = terrainSample.color();
                if (terrainSample.source() == TerrainSampler.MiniMapSampleSource.CHUNK_UNLOADED) {
                    chunkUnloadedSourcePixels++;
                }
                if (terrainSample.source() != TerrainSampler.MiniMapSampleSource.VISIBLE_SURFACE) {
                    unresolvedSurfacePixels++;
                }
                if (!usedClientSample) {
                    baseColor = TerrainSampler.miniMapBiomeFallbackColor(client.world, worldX, worldZ);
                }

                int shadedArgb = TerrainSampler.applyVisibleSurfaceShading(client.world, worldX, worldZ, baseColor);
                image.setColor(px, py, TerrainSampler.argbToAbgr(shadedArgb));
            }
        }

        miniMapRenderDebug = new MiniMapRenderDebug(chunkUnloadedSourcePixels, unresolvedSurfacePixels);

        return true;
    }

    public static void clearMiniMapRenderCache(MinecraftClient client) {
        if (miniMapRenderCache == null) {
            return;
        }

        clearMiniMapRenderCache(client, miniMapRenderCache);
        miniMapRenderCache = null;
    }

    private static void clearMiniMapRenderCache(MinecraftClient client, MiniMapRenderCache cache) {
        if (cache == null) {
            return;
        }

        if (client != null) {
            client.getTextureManager().destroyTexture(cache.textureId());
        }

        cache.texture().close();
    }







    private static void drawMiniMapCardinalLabels(DrawContext drawContext, MinecraftClient client, float centerX, float centerY, int miniMapSize, float mapRotationDegrees, float hudAlpha) {
        float radius = Math.max(8.0f, (miniMapSize / 2.0f) - 12.0f);
        drawCardinalLabelAtAngle(drawContext, client, "N", centerX, centerY, radius, -90.0f + mapRotationDegrees, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabelAtAngle(drawContext, client, "E", centerX, centerY, radius, 0.0f + mapRotationDegrees, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabelAtAngle(drawContext, client, "S", centerX, centerY, radius, 90.0f + mapRotationDegrees, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabelAtAngle(drawContext, client, "W", centerX, centerY, radius, 180.0f + mapRotationDegrees, 0xFFDDEEFF, hudAlpha);
    }

    private static void drawCardinalLabelAtAngle(
            DrawContext drawContext,
            MinecraftClient client,
            String label,
            float centerX,
            float centerY,
            float radius,
            float angleDegrees,
            int color,
            float hudAlpha
    ) {
        double radians = Math.toRadians(angleDegrees);
        float x = centerX + ((float) Math.cos(radians) * radius);
        float y = centerY + ((float) Math.sin(radians) * radius);
        drawCardinalLabel(drawContext, client, label, x, y, color, hudAlpha);
    }

    private static void drawCardinalLabel(DrawContext drawContext, MinecraftClient client, String label, float x, float y, int color, float hudAlpha) {
        int textWidth = client.textRenderer.getWidth(label);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(label), Math.round(x - (textWidth / 2.0f)), Math.round(y - 4.0f), HudUtil.withAlpha(color, hudAlpha));
    }

    private static int resolveActiveMiniMapSpan(MinecraftClient client) {
        int baseSpan = PASSIVE_MINIMAP_SPAN_BLOCKS;
        McdgClientMod.MiniMapState state = miniMapState;
        if (client == null || client.player == null || state == null) {
            return baseSpan;
        }

        double playerToBasket = Math.sqrt(
                ((state.basketX() - client.player.getX()) * (state.basketX() - client.player.getX()))
                        + ((state.basketZ() - client.player.getZ()) * (state.basketZ() - client.player.getZ()))
        );

        double halfSpan = playerToBasket + 20.0d;
        int dynamicSpan = (int) Math.ceil(halfSpan * 2.0d);
        int payloadSpan = Math.max(0, state.mapSpan());
        int resolved = Math.max(baseSpan, Math.max(dynamicSpan, payloadSpan));
        return Math.max(64, Math.min(256, resolved));
    }

    private static float resolveMiniMapHeadingRotationDegrees(MinecraftClient client) {
        if (client.player == null) {
            return 0.0f;
        }

        float lookHeading = normalizeDegrees(180.0f - client.player.getYaw());
        return lookHeading;
    }

    public static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    public static void handleMiniMapHotkeys(MinecraftClient client) {
        ClientKeybinds.forEachMinimapSizeUpPress(() -> {
            miniMapStyleIndex = Math.min(MINIMAP_SIZES.length - 1, miniMapStyleIndex + 1);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Mini-map size: " + MINIMAP_SIZES[miniMapStyleIndex] + "px").formatted(Formatting.GRAY), true);
            }
        });

        ClientKeybinds.forEachMinimapSizeDownPress(() -> {
            miniMapStyleIndex = Math.max(0, miniMapStyleIndex - 1);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Mini-map size: " + MINIMAP_SIZES[miniMapStyleIndex] + "px").formatted(Formatting.GRAY), true);
            }
        });

        WaypointManager.handleKeybinds(client);
    }

    public static void tickMiniMapJoinPrime(MinecraftClient client) {
        if (!miniMapJoinWarmupPending) {
            return;
        }

        if (client == null || client.player == null || client.world == null) {
            return;
        }

        lastMiniMapRenderAtMs = 0L;
        refreshMiniMapRenderCache(client, resolveActiveMiniMapSpan(client));

        if (miniMapJoinPrimeTicksRemaining > 0) {
            miniMapJoinPrimeTicksRemaining--;
        }

        if (miniMapJoinPrimeTicksRemaining <= 0) {
            miniMapJoinWarmupPending = false;
            miniMapJoinPrimeTicksRemaining = 0;
        }
    }

    private static void drawMiniMapBiomeFallbackSurface(
            DrawContext drawContext,
            MinecraftClient client,
            int mapX,
            int mapY,
            int miniMapSize,
            int mapSpan,
            double playerWorldX,
            double playerWorldZ,
            float mapRotationDegrees,
            float hudAlpha
    ) {
        if (client == null || client.world == null || miniMapSize <= 0 || mapSpan <= 0) {
            return;
        }

        int center = miniMapSize / 2;
        float radius = (miniMapSize / 2.0f) - 1.0f;
        float radiusSq = radius * radius;

        for (int py = 0; py < miniMapSize; py++) {
            float localY = (py + 0.5f) - center;
            for (int px = 0; px < miniMapSize; px++) {
                float localX = (px + 0.5f) - center;
                if (((localX * localX) + (localY * localY)) > radiusSq) {
                    continue;
                }

                float[] worldDelta = rotateMiniMapVector(localX, localY, -mapRotationDegrees);
                int worldX = net.minecraft.util.math.MathHelper.floor(playerWorldX + ((worldDelta[0] / Math.max(1.0f, miniMapSize)) * mapSpan));
                int worldZ = net.minecraft.util.math.MathHelper.floor(playerWorldZ + ((worldDelta[1] / Math.max(1.0f, miniMapSize)) * mapSpan));

                int color = TerrainSampler.miniMapBiomeFallbackColor(client.world, worldX, worldZ);
                if (client.world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    color = TerrainSampler.applyVisibleSurfaceShading(client.world, worldX, worldZ, color);
                }

                drawContext.fill(mapX + px, mapY + py, mapX + px + 1, mapY + py + 1, HudUtil.withAlpha(color, hudAlpha));
            }
        }
    }

    public static float hudFadeAlpha() {
        if (hudVisibleSinceMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - hudVisibleSinceMs;
        return Math.max(0.0f, Math.min(1.0f, elapsed / 180.0f));
    }
}
