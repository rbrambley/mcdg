package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.game.ScorecardManager;
import com.mcdg.net.HoleMiniMapSync;
import java.util.HashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class McdgClientMod implements ClientModInitializer {
    private static final String AUTOCONNECT_SERVER_ENV = "MCDG_AUTOCONNECT_SERVER";
    private static final long AUTOCONNECT_RETRY_DELAY_MS = 3000L;
    private static final int POWER_BAR_HEIGHT = 72;
    private static final int POWER_BAR_WIDTH = 8;
    private static final int MINIMAP_PADDING = 8;
    private static final long MINIMAP_STALE_TIMEOUT_MS = 15000L;
    private static final int MINIMAP_TEXTURE_SIZE = 256;
    private static final int[] MINIMAP_SIZES = { 84, 104, 126 };
    private static final int[] MINIMAP_PANEL_ALPHA = { 0x8A, 0x6F, 0x58 };
    private static final int[] MINIMAP_SURFACE_ALPHA = { 0xD0, 0xB8, 0x9A };
    private static final int HUD_CARD_BG = 0xA5121822;
    private static final int HUD_CARD_BORDER = 0xA63A4E66;
    private static final int HUD_CARD_HEADER_BG = 0xB01B2638;
    private static final int HUD_CARD_TEXT = 0xE8EEF7;
    private static final int HUD_CARD_MUTED_TEXT = 0xAAB8CC;
    private static final String[] COMPASS_8 = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    private static long nextAutoconnectAttemptAt = 0L;
    private static boolean autoconnectSatisfied = false;
    private static String autoconnectServer = readAutoconnectServer();
    private static MiniMapState miniMapState;
    private static long miniMapReceivedAtMs;
    private static boolean miniMapVisible = true;
    private static int miniMapStyleIndex = 0;
    private static MiniMapRenderCache miniMapRenderCache;
    private static KeyBinding toggleMiniMapKey;
    private static KeyBinding cycleMiniMapStyleKey;
    private static long hudVisibleSinceMs;
    private static float displayedDistanceFeet = Float.NaN;
    private static float displayedDistanceMeters = Float.NaN;
    private static float displayedTotalStrokes = Float.NaN;
    private static float displayedCumulativeDelta = Float.NaN;


    @Override
    public void onInitializeClient() {
        toggleMiniMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.toggle_minimap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.mcdg"
        ));
        cycleMiniMapStyleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.cycle_minimap_style",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.mcdg"
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.options.getChatScale().setValue(0.65);
            client.options.getChatHeightUnfocused().setValue(0.25);
            client.options.write();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            maybeAutoConnect(client);
            handleMiniMapHotkeys(client);
        });
        ClientPlayNetworking.registerGlobalReceiver(HoleMiniMapSync.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.active()) {
                    miniMapState = null;
                    miniMapReceivedAtMs = 0L;
                    hudVisibleSinceMs = 0L;
                    displayedDistanceFeet = Float.NaN;
                    displayedDistanceMeters = Float.NaN;
                    displayedTotalStrokes = Float.NaN;
                    displayedCumulativeDelta = Float.NaN;
                    clearMiniMapRenderCache(context.client());
                    return;
                }

                if (miniMapState == null) {
                    hudVisibleSinceMs = System.currentTimeMillis();
                }

                byte[] terrainCells = resolveTerrainCells(payload, miniMapState);

                miniMapState = new MiniMapState(
                        payload.holeIndex(),
                        payload.teeX(),
                        payload.teeZ(),
                        payload.basketX(),
                        payload.basketZ(),
                        payload.lieX(),
                        payload.lieZ(),
                        payload.par(),
                        payload.throwNumber(),
                        payload.totalStrokes(),
                        payload.cumulativeParDelta(),
                        payload.miniMapQualityPreset(),
                        payload.strictMode(),
                        payload.hasAlternateAnchor(),
                        payload.alternateAnchorX(),
                        payload.alternateAnchorZ(),
                        payload.mapOriginX(),
                        payload.mapOriginZ(),
                        payload.mapSpan(),
                        terrainCells
                );
                miniMapReceivedAtMs = System.currentTimeMillis();
                refreshMiniMapRenderCache(context.client(), miniMapState, terrainCells);
            });
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            updateHudTweens();
            renderHoleMiniMapOverlay(drawContext);
            renderRoundInfoOverlay(drawContext);
            renderScorecardOverlay(drawContext);
            renderCompassOverlay(drawContext);
            renderPowerOverlay(drawContext);
        });
    }

    private static void maybeAutoConnect(MinecraftClient client) {
        if (autoconnectSatisfied) {
            return;
        }

        if (autoconnectServer == null || client == null) {
            return;
        }

        if (client.player != null) {
            autoconnectSatisfied = true;
            return;
        }

        Screen currentScreen = client.currentScreen;
        if (!(currentScreen instanceof TitleScreen) && !(currentScreen instanceof DisconnectedScreen)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAutoconnectAttemptAt) {
            return;
        }

        nextAutoconnectAttemptAt = now + AUTOCONNECT_RETRY_DELAY_MS;
        Screen parent = client.currentScreen == null ? new TitleScreen(false) : client.currentScreen;
        ServerAddress address = ServerAddress.parse(autoconnectServer);
        ServerInfo serverInfo = new ServerInfo("MCDG Dev Server", address.toString(), ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, address, serverInfo, false, null);
    }

    private static String readAutoconnectServer() {
        String value = System.getenv(AUTOCONNECT_SERVER_ENV);
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }


    private static void renderCompassOverlay(DrawContext drawContext) {
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
    }

    private static void renderHoleMiniMapOverlay(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null || !miniMapVisible) {
            return;
        }

        MiniMapState state = miniMapState;
        if (state == null) {
            return;
        }

        if ((System.currentTimeMillis() - miniMapReceivedAtMs) > MINIMAP_STALE_TIMEOUT_MS) {
            return;
        }

        int panelX = MINIMAP_PADDING;
        int panelY = client.getDebugHud().shouldShowDebugHud() ? 76 : MINIMAP_PADDING;
        int miniMapSize = MINIMAP_SIZES[Math.max(0, Math.min(MINIMAP_SIZES.length - 1, miniMapStyleIndex))];
        int surfaceAlpha = MINIMAP_SURFACE_ALPHA[Math.max(0, Math.min(MINIMAP_SURFACE_ALPHA.length - 1, miniMapStyleIndex))];
        float hudAlpha = hudFadeAlpha();

        // Header bar — sits above the circle, sized to fit the header text
        String holeLabel = "Hole " + state.holeIndex() + "  " + (state.strictMode() ? "STRICT" : "CASUAL");
        String qualityLabel = miniMapQualityBadge(state.miniMapQualityPreset());
        int headerTextW = client.textRenderer.getWidth(holeLabel) + client.textRenderer.getWidth(qualityLabel) + 16;
        int headerW = Math.max(miniMapSize, headerTextW);
        int headerH = 12;
        drawHudCard(drawContext, client, panelX, panelY, headerW, headerH, null, hudAlpha);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(holeLabel).formatted(Formatting.GRAY), panelX + 4, panelY + 2, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));
        int qualityTextWidth = client.textRenderer.getWidth(qualityLabel);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(qualityLabel).formatted(Formatting.AQUA), panelX + headerW - qualityTextWidth - 4, panelY + 2, withAlpha(0x9AE6FF, hudAlpha));

        // Circular map area — standalone, no surrounding rectangle
        int mapX = panelX;
        int mapY = panelY + headerH + 2;
        float mapScale = miniMapSize / Math.max(1.0f, (float) state.mapSpan());
        int mapOriginScreenX = mapX;
        int mapOriginScreenY = mapY;
        int liePx = mapOriginScreenX + Math.round((state.lieX() - state.mapOriginX()) * mapScale);
        int liePz = mapOriginScreenY + Math.round((state.lieZ() - state.mapOriginZ()) * mapScale);
        drawFilledCircle(
            drawContext,
            mapX + (miniMapSize / 2),
            mapY + (miniMapSize / 2),
            Math.max(2, (miniMapSize / 2) - 1),
            withAlpha((surfaceAlpha << 24) | 0x121212, hudAlpha)
        );

        if (miniMapRenderCache != null && miniMapRenderCache.textureId() != null) {
            int mapCenterX = mapX + (miniMapSize / 2);
            int mapCenterY = mapY + (miniMapSize / 2);
            int mapRadius = Math.max(2, (miniMapSize / 2) - 1);
            float headingRotation = -(client.player.getYaw() + 180.0f);
            int playerPx = mapOriginScreenX + Math.round(((float) client.player.getX() - state.mapOriginX()) * mapScale);
            int playerPz = mapOriginScreenY + Math.round(((float) client.player.getZ() - state.mapOriginZ()) * mapScale);
            drawContext.enableScissor(mapX, mapY, mapX + miniMapSize, mapY + miniMapSize);
            var matrices = drawContext.getMatrices();
            matrices.push();
            matrices.translate(mapCenterX, mapCenterY, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(headingRotation));
            matrices.translate(-mapCenterX, -mapCenterY, 0.0f);
            drawContext.drawTexture(miniMapRenderCache.textureId(), mapX, mapY, 0, 0, miniMapSize, miniMapSize, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE);

            int teePx = mapOriginScreenX + Math.round((state.teeX() - state.mapOriginX()) * mapScale);
            int teePz = mapOriginScreenY + Math.round((state.teeZ() - state.mapOriginZ()) * mapScale);
            int basketPx = mapOriginScreenX + Math.round((state.basketX() - state.mapOriginX()) * mapScale);
            int basketPz = mapOriginScreenY + Math.round((state.basketZ() - state.mapOriginZ()) * mapScale);

            int anchorPx = 0;
            int anchorPz = 0;

            if (state.hasAlternateAnchor()) {
                anchorPx = mapOriginScreenX + Math.round((state.alternateAnchorX() - state.mapOriginX()) * mapScale);
                anchorPz = mapOriginScreenY + Math.round((state.alternateAnchorZ() - state.mapOriginZ()) * mapScale);
                drawLineCircleClipped(drawContext, teePx, teePz, anchorPx, anchorPz, 0xFF4CC9F0, mapCenterX, mapCenterY, mapRadius);
                drawLineCircleClipped(drawContext, anchorPx, anchorPz, basketPx, basketPz, 0xFF4CC9F0, mapCenterX, mapCenterY, mapRadius);
                drawDotCircleClipped(drawContext, anchorPx, anchorPz, 1, 0xFF4CC9F0, mapCenterX, mapCenterY, mapRadius);
            } else {
                drawLineCircleClipped(drawContext, teePx, teePz, basketPx, basketPz, 0xFF3AC25B, mapCenterX, mapCenterY, mapRadius);
            }

            drawDotCircleClipped(drawContext, teePx, teePz, 2, 0xFF28A745, mapCenterX, mapCenterY, mapRadius);
            drawDotCircleClipped(drawContext, basketPx, basketPz, 2, 0xFFFFCC33, mapCenterX, mapCenterY, mapRadius);
            drawDotCircleClipped(drawContext, playerPx, playerPz, 2, 0xFFFFFFFF, mapCenterX, mapCenterY, mapRadius);
            drawMiniMapDistanceRings(drawContext, client, basketPx, basketPz, mapX, mapY, miniMapSize, mapScale, hudAlpha, mapCenterX, mapCenterY, mapRadius);
            drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, hudAlpha);
            matrices.pop();
            drawContext.disableScissor();

            int arrowLen = Math.max(7, Math.round(8.0f * mapScale));
            int arrowX = playerPx;
            int arrowZ = playerPz - arrowLen;
            drawLineCircleClipped(drawContext, playerPx, playerPz, arrowX, arrowZ, withAlpha(0xFFDDEEFF, hudAlpha), mapCenterX, mapCenterY, mapRadius);
            drawDotCircleClipped(drawContext, arrowX, arrowZ, 1, withAlpha(0xFFFFFFFF, hudAlpha), mapCenterX, mapCenterY, mapRadius);
        }

        // Circle border only — corners are transparent because they are baked into the texture
        drawCircleOutline(drawContext, mapX + (miniMapSize / 2), mapY + (miniMapSize / 2), Math.max(2, (miniMapSize / 2) - 1), withAlpha(HUD_CARD_BORDER, hudAlpha));

        // Separate info card below the circle
        int distFeet = Math.max(0, Math.round(displayedDistanceFeet));
        int distMeters = Math.max(0, Math.round(displayedDistanceMeters));
        String distLine = distFeet + "ft / " + distMeters + "m";
        String ringsLine = "Rings: 50 / 100 / 150ft";
        int legendWidth = 16 + 84; // 6 swatches * ~14px each
        int infoW = Math.max(legendWidth, Math.max(client.textRenderer.getWidth(distLine), client.textRenderer.getWidth(ringsLine))) + 12;
        int infoX = panelX;
        int infoY = mapY + miniMapSize + 3;
        int infoH = 40;
        drawHudCard(drawContext, client, infoX, infoY, infoW, infoH, null, hudAlpha);
        drawMiniMapLegend(drawContext, client, infoX + 6, infoY + 4, hudAlpha);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(ringsLine), infoX + 6, infoY + 16, withAlpha(0xA9D8FF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(distLine), infoX + 6, infoY + 27, withAlpha(0xCFE8FF, hudAlpha));
        if (state.hasAlternateAnchor()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Alt route"), infoX + 6, infoY + infoH - 11, withAlpha(0x9AE6FF, hudAlpha));
        }
    }

    private static void renderRoundInfoOverlay(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        MiniMapState state = miniMapState;
        if (state == null) {
            return;
        }

        int distMeters = Math.max(0, Math.round((float) Math.sqrt(
                ((state.basketX() - state.lieX()) * (state.basketX() - state.lieX()))
                        + ((state.basketZ() - state.lieZ()) * (state.basketZ() - state.lieZ()))
        )));
        int distFeet = Math.max(0, Math.round(distMeters * 3.28084f));

        String deltaText;
        if (state.cumulativeParDelta() == 0) {
            deltaText = "E";
        } else if (state.cumulativeParDelta() > 0) {
            deltaText = "+" + state.cumulativeParDelta();
        } else {
            deltaText = Integer.toString(state.cumulativeParDelta());
        }

        String line1 = "Round";
        String line2 = "H" + state.holeIndex() + "  P" + state.par() + "  T" + state.throwNumber();
        String line3 = distFeet + "ft / " + distMeters + "m";
        String line4 = "Total " + state.totalStrokes() + "  " + deltaText;
        int maxTextWidth = Math.max(
                Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2)),
                Math.max(client.textRenderer.getWidth(line3), client.textRenderer.getWidth(line4))
        );

        int panelW = maxTextWidth + 16;
        int panelH = 54;
        int x = drawContext.getScaledWindowWidth() - panelW - 8;
        int y = client.getDebugHud().shouldShowDebugHud() ? 76 : 8;
        float hudAlpha = hudFadeAlpha();

        drawHudCard(drawContext, client, x, y, panelW, panelH, "Round", hudAlpha);

        int animatedTotal = Math.max(0, Math.round(displayedTotalStrokes));
        int animatedDelta = Math.round(displayedCumulativeDelta);
        String animatedDeltaText = animatedDelta == 0 ? "E" : (animatedDelta > 0 ? "+" + animatedDelta : Integer.toString(animatedDelta));
        String animatedLine4 = "Total " + animatedTotal + "  " + animatedDeltaText;
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line2), x + 6, y + 16, withAlpha(0xFFFFFF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line3), x + 6, y + 28, withAlpha(0xCFE8FF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(animatedLine4), x + 6, y + 40, withAlpha(0xB5F7B5, hudAlpha));
    }

    private static void renderScorecardOverlay(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        // Only show the on-screen scorecard while round HUD data is actively streaming.
        MiniMapState state = miniMapState;
        if (state == null || (System.currentTimeMillis() - miniMapReceivedAtMs) > MINIMAP_STALE_TIMEOUT_MS) {
            return;
        }

        NbtCompound scorecardRoot = findScorecardRoot(client);
        if (scorecardRoot == null) {
            return;
        }

        NbtList holes = scorecardRoot.getList(ScorecardManager.KEY_HOLES, NbtElement.COMPOUND_TYPE);
        if (holes.isEmpty()) {
            return;
        }

        int visibleRows = holes.size();
        int holeColW = Math.max(client.textRenderer.getWidth("H"), client.textRenderer.getWidth(Integer.toString(holes.size())));
        int distColW = client.textRenderer.getWidth("Dist");
        int parColW = client.textRenderer.getWidth("Par");
        int scoreColW = client.textRenderer.getWidth("Score");
        for (int i = 0; i < visibleRows; i++) {
            NbtCompound row = holes.getCompound(i);
            int dist = row.getInt(ScorecardManager.KEY_DISTANCE_FEET);
            int score = row.getInt(ScorecardManager.KEY_SCORE);
            distColW = Math.max(distColW, client.textRenderer.getWidth(dist + "ft"));
            scoreColW = Math.max(scoreColW, client.textRenderer.getWidth(score < 0 ? "-" : Integer.toString(score)));
        }

        int colGap = 10;
        int colHoleX = 6;
        int colDistX = colHoleX + holeColW + colGap;
        int colParX = colDistX + distColW + colGap;
        int colScoreX = colParX + parColW + colGap;
        int panelW = colScoreX + scoreColW + 6;
        int panelH = 22 + (visibleRows * 10);
        int x = drawContext.getScaledWindowWidth() - panelW - 8;
        int preferredY = Math.max((drawContext.getScaledWindowHeight() / 2) + 14, drawContext.getScaledWindowHeight() - panelH - 8);
        int y = Math.max(8, Math.min(preferredY, drawContext.getScaledWindowHeight() - panelH - 8));
        float hudAlpha = hudFadeAlpha();

        drawHudCard(drawContext, client, x, y, panelW, panelH, "Scorecard", hudAlpha);

        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("H"), x + colHoleX, y + 14, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Dist"), x + colDistX, y + 14, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Par"), x + colParX, y + 14, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Score"), x + colScoreX, y + 14, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));

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
            int rowY = y + 24 + (i * 10);
            int rowColor = hole == state.holeIndex() ? 0xFFF4D37A : HUD_CARD_TEXT;

            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(holeText),
                    x + rightAlign(colHoleX, holeColW, client.textRenderer.getWidth(holeText)),
                    rowY,
                    withAlpha(rowColor, hudAlpha)
            );
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(distText),
                    x + rightAlign(colDistX, distColW, client.textRenderer.getWidth(distText)),
                    rowY,
                        withAlpha(rowColor, hudAlpha)
            );
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(parText),
                    x + rightAlign(colParX, parColW, client.textRenderer.getWidth(parText)),
                    rowY,
                        withAlpha(rowColor, hudAlpha)
            );
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    Text.literal(scoreText),
                    x + rightAlign(colScoreX, scoreColW, client.textRenderer.getWidth(scoreText)),
                    rowY,
                    withAlpha(rowColor, hudAlpha)
            );
        }
    }

    private static void updateHudTweens() {
        MiniMapState state = miniMapState;
        if (state == null) {
            return;
        }

        int dx = state.basketX() - state.lieX();
        int dz = state.basketZ() - state.lieZ();
        float targetMeters = Math.max(0, Math.round((float) Math.sqrt((dx * dx) + (dz * dz))));
        float targetFeet = Math.max(0, Math.round(targetMeters * 3.28084f));
        displayedDistanceMeters = tween(displayedDistanceMeters, targetMeters, 0.18f);
        displayedDistanceFeet = tween(displayedDistanceFeet, targetFeet, 0.18f);
        displayedTotalStrokes = tween(displayedTotalStrokes, state.totalStrokes(), 0.22f);
        displayedCumulativeDelta = tween(displayedCumulativeDelta, state.cumulativeParDelta(), 0.22f);
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

    private static void renderMiniMapTerrain(
            DrawContext drawContext,
            MiniMapState state,
            int mapX,
            int mapY,
            int miniMapSize,
            int playerMapX,
            int playerMapY,
            float rightX,
            float rightZ,
            float forwardX,
            float forwardZ,
            float mapScale
    ) {
        // No-op: terrain is drawn through the cached texture path.
    }

    private static float computeAdaptiveMapScale(int miniMapSize, int mapSpan) {
        return miniMapSize / Math.max(1.0f, (float) mapSpan);
    }

    private static int[] projectMiniMapPoint(
            int worldX,
            int worldZ,
            MiniMapState state,
            int playerMapX,
            int playerMapY,
            float rightX,
            float rightZ,
            float forwardX,
            float forwardZ,
            float mapScale
    ) {
        int mapPointX = playerMapX + Math.round((worldX - state.lieX()) * mapScale);
        int mapPointY = playerMapY + Math.round((worldZ - state.lieZ()) * mapScale);
        return new int[] { mapPointX, mapPointY };
    }

    private static int mapPlayerCenterX(int panelX, int miniMapSize) {
        return panelX + 8 + (miniMapSize / 2);
    }

    private static int mapPlayerBottomY(int panelY, int miniMapSize) {
        return panelY + 14 + miniMapSize - 8;
    }

    private static int miniMapTerrainColor(int terrainClass) {
        return switch (terrainClass) {
            case 1 -> 0xFF2F6FDB;
            case 2 -> 0xFFD8C27A;
            case 3 -> 0xFF466A3A;
            case 4 -> 0xFF22472A;
            case 5 -> 0xFF7A7A7A;
            case 6 -> 0xFFDCE6F5;
            case 7 -> 0xFF8FD2F0;
            case 8 -> 0xFF8A6A47;
            case 9 -> 0xFF9B8B63;
            case 10 -> 0xFFE46E2A;
            default -> 0;
        };
    }

    private static void drawMiniMapDistanceRings(
            DrawContext drawContext,
            MinecraftClient client,
            int centerX,
            int centerY,
            int mapX,
            int mapY,
            int miniMapSize,
            float mapScale,
            float hudAlpha,
            int clipCenterX,
            int clipCenterY,
            int clipRadius
    ) {
        int[] ringFeet = { 50, 100, 150 };
        for (int feet : ringFeet) {
            float radiusBlocks = feet / 3.28084f;
            int radiusPx = Math.max(2, Math.round(radiusBlocks * mapScale));
            int color = withAlpha(0x66BFD5E9, hudAlpha);
            drawCircleOutlineClipped(drawContext, centerX, centerY, radiusPx, color, clipCenterX, clipCenterY, clipRadius);
        }
    }

    private static void drawCircleOutline(DrawContext drawContext, int centerX, int centerY, int radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 8) {
            double radians = Math.toRadians(degrees);
            int px = centerX + (int) Math.round(Math.cos(radians) * radius);
            int py = centerY + (int) Math.round(Math.sin(radians) * radius);
            drawContext.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawFilledCircle(DrawContext drawContext, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }

        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.floor(Math.sqrt(Math.max(0, (radius * radius) - (y * y))));
            int left = centerX - span;
            int right = centerX + span + 1;
            int py = centerY + y;
            drawContext.fill(left, py, right, py + 1, color);
        }
    }

    private static void drawMiniMapCardinalLabels(DrawContext drawContext, MinecraftClient client, int centerX, int centerY, int miniMapSize, float hudAlpha) {
        int radius = Math.max(8, (miniMapSize / 2) - 8);
        drawCardinalLabel(drawContext, client, "N", centerX, centerY - radius, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "E", centerX + radius, centerY, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "S", centerX, centerY + radius, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "W", centerX - radius, centerY, 0xFFDDEEFF, hudAlpha);
    }

    private static void drawCardinalLabel(DrawContext drawContext, MinecraftClient client, String label, int x, int y, int color, float hudAlpha) {
        int textWidth = client.textRenderer.getWidth(label);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(label), x - (textWidth / 2), y - 4, withAlpha(color, hudAlpha));
    }

    private static void drawMiniMapCircularMask(DrawContext drawContext, int left, int top, int size, int fillColor) {
        float center = (size - 1) / 2.0f;
        float radius = Math.max(1.0f, center - 1.0f);
        for (int y = 0; y < size; y++) {
            float dy = y - center;
            float chord = (float) Math.sqrt(Math.max(0.0f, (radius * radius) - (dy * dy)));
            int innerLeft = Math.max(0, (int) Math.floor(center - chord));
            int innerRight = Math.min(size, (int) Math.ceil(center + chord) + 1);
            if (innerLeft > 0) {
                drawContext.fill(left, top + y, left + innerLeft, top + y + 1, fillColor);
            }
            if (innerRight < size) {
                drawContext.fill(left + innerRight, top + y, left + size, top + y + 1, fillColor);
            }
        }
    }

    private static void drawMiniMapLegend(DrawContext drawContext, MinecraftClient client, int startX, int y, float hudAlpha) {
        drawLegendSwatch(drawContext, client, startX, y, 0xFF2F6FDB, "W", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 16, y, 0xFF22472A, "T", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 32, y, 0xFF7A7A7A, "R", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 48, y, 0xFFDCE6F5, "S", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 64, y, 0xFFCC8D32, "Hz", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 84, y, 0xFFC2433A, "OB", hudAlpha);
    }

    private static void drawLegendSwatch(DrawContext drawContext, MinecraftClient client, int x, int y, int color, String label, float hudAlpha) {
        drawContext.fill(x, y + 2, x + 4, y + 6, withAlpha(color, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(label), x + 6, y, withAlpha(0xD3E7FF, hudAlpha));
    }

    private static int sampleTerrainColorBilinear(byte[] terrain, int grid, float fx, float fz) {
        float clampedX = Math.max(0.0f, Math.min(grid - 1, fx));
        float clampedZ = Math.max(0.0f, Math.min(grid - 1, fz));
        int x0 = (int) Math.floor(clampedX);
        int z0 = (int) Math.floor(clampedZ);
        int x1 = Math.min(grid - 1, x0 + 1);
        int z1 = Math.min(grid - 1, z0 + 1);
        float tx = clampedX - x0;
        float tz = clampedZ - z0;

        int c00 = terrainColorOrFallback(terrain[(z0 * grid) + x0]);
        int c10 = terrainColorOrFallback(terrain[(z0 * grid) + x1]);
        int c01 = terrainColorOrFallback(terrain[(z1 * grid) + x0]);
        int c11 = terrainColorOrFallback(terrain[(z1 * grid) + x1]);

        int cx0 = lerpColor(c00, c10, tx);
        int cx1 = lerpColor(c01, c11, tx);
        return lerpColor(cx0, cx1, tz);
    }

    private static int terrainColorOrFallback(byte terrainType) {
        int packed = terrainType & 0xFF;
        int terrainClass = packed & 0x0F;
        int riskCode = (packed >>> 4) & 0x03;
        int elevationBand = (packed >>> 6) & 0x03;

        int baseColor = miniMapTerrainColor(terrainClass);
        if (baseColor == 0) {
            baseColor = 0xFF1A1F27;
        }

        if (riskCode == 1) {
            return blendRgb(baseColor, 0xFFCC8D32, 0.30f);
        }
        if (riskCode == 2) {
            return blendRgb(baseColor, 0xFFC2433A, 0.40f);
        }
        return baseColor;
    }

    private static int scaleColor(int argb, float multiplier) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, Math.min(255, Math.round(((argb >>> 16) & 0xFF) * multiplier)));
        int g = Math.max(0, Math.min(255, Math.round(((argb >>> 8) & 0xFF) * multiplier)));
        int b = Math.max(0, Math.min(255, Math.round((argb & 0xFF) * multiplier)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendRgb(int fromArgb, int toArgb, float weight) {
        float t = Math.max(0.0f, Math.min(1.0f, weight));
        int a = (fromArgb >>> 24) & 0xFF;
        int r = lerpChannel((fromArgb >>> 16) & 0xFF, (toArgb >>> 16) & 0xFF, t);
        int g = lerpChannel((fromArgb >>> 8) & 0xFF, (toArgb >>> 8) & 0xFF, t);
        int b = lerpChannel(fromArgb & 0xFF, toArgb & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerpChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerpChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerpChannel(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + ((to - from) * t))));
    }

    private static void drawHudCard(DrawContext drawContext, MinecraftClient client, int x, int y, int w, int h, String title, float alpha) {
        drawContext.fill(x, y, x + w, y + h, withAlpha(HUD_CARD_BG, alpha));
        drawContext.fill(x, y, x + w, y + 12, withAlpha(HUD_CARD_HEADER_BG, alpha));
        drawContext.fill(x, y, x + w, y + 1, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y + h - 1, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x, y, x + 1, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        drawContext.fill(x + w - 1, y, x + w, y + h, withAlpha(HUD_CARD_BORDER, alpha));
        if (title != null && !title.isBlank()) {
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(title).formatted(Formatting.GRAY), x + 6, y + 2, withAlpha(HUD_CARD_MUTED_TEXT, alpha));
        }
    }

    private static int rightAlign(int startX, int width, int textWidth) {
        return startX + Math.max(0, width - textWidth);
    }

    private static float hudFadeAlpha() {
        if (hudVisibleSinceMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - hudVisibleSinceMs;
        return Math.max(0.0f, Math.min(1.0f, elapsed / 180.0f));
    }

    private static int withAlpha(int argb, float alphaFactor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int appliedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaFactor)));
        return (argb & 0x00FFFFFF) | (appliedAlpha << 24);
    }

    private static float tween(float current, float target, float factor) {
        if (Float.isNaN(current)) {
            return target;
        }
        return current + ((target - current) * factor);
    }

    private static String miniMapQualityBadge(int qualityPresetCode) {
        return switch (Math.max(0, Math.min(2, qualityPresetCode))) {
            case 0 -> "MM PERF";
            case 2 -> "MM ULTRA";
            default -> "MM BAL";
        };
    }

    private static byte[] resolveTerrainCells(HoleMiniMapSync.Payload payload, MiniMapState previous) {
        int expectedLength = HoleMiniMapSync.TERRAIN_GRID_SIZE * HoleMiniMapSync.TERRAIN_GRID_SIZE;
        byte[] incoming = payload.terrainCells();
        if (incoming.length == expectedLength) {
            return incoming;
        }

        if (previous == null) {
            return incoming;
        }

        boolean sameMapWindow = previous.holeIndex() == payload.holeIndex()
                && previous.mapOriginX() == payload.mapOriginX()
                && previous.mapOriginZ() == payload.mapOriginZ()
                && previous.mapSpan() == payload.mapSpan();
        if (sameMapWindow && previous.terrainCells().length == expectedLength) {
            return previous.terrainCells();
        }

        return incoming;
    }

    private static void handleMiniMapHotkeys(MinecraftClient client) {
        while (toggleMiniMapKey.wasPressed()) {
            miniMapVisible = !miniMapVisible;
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Mini-map " + (miniMapVisible ? "shown" : "hidden")).formatted(Formatting.GRAY), true);
            }
        }

        while (cycleMiniMapStyleKey.wasPressed()) {
            miniMapStyleIndex = (miniMapStyleIndex + 1) % MINIMAP_SIZES.length;
            if (client.player != null) {
                int style = miniMapStyleIndex + 1;
                client.player.sendMessage(Text.literal("Mini-map style " + style + " (size/transparency)").formatted(Formatting.GRAY), true);
            }
        }
    }

    private static void drawLine(DrawContext drawContext, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int x = x0;
        int y = y0;
        while (true) {
            drawContext.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void drawDot(DrawContext drawContext, int x, int y, int radius, int color) {
        drawContext.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
    }

    private static void drawLineCircleClipped(
            DrawContext drawContext,
            int x0,
            int y0,
            int x1,
            int y1,
            int color,
            int clipCenterX,
            int clipCenterY,
            int clipRadius
    ) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int radiusSq = clipRadius * clipRadius;

        int x = x0;
        int y = y0;
        while (true) {
            if (isPointInsideCircle(x, y, clipCenterX, clipCenterY, radiusSq)) {
                drawContext.fill(x, y, x + 1, y + 1, color);
            }
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void drawDotCircleClipped(
            DrawContext drawContext,
            int x,
            int y,
            int radius,
            int color,
            int clipCenterX,
            int clipCenterY,
            int clipRadius
    ) {
        int clipRadiusSq = clipRadius * clipRadius;
        for (int py = y - radius; py <= y + radius; py++) {
            for (int px = x - radius; px <= x + radius; px++) {
                if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                    drawContext.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    private static void drawCircleOutlineClipped(
            DrawContext drawContext,
            int centerX,
            int centerY,
            int radius,
            int color,
            int clipCenterX,
            int clipCenterY,
            int clipRadius
    ) {
        int clipRadiusSq = clipRadius * clipRadius;
        for (int degrees = 0; degrees < 360; degrees += 8) {
            double radians = Math.toRadians(degrees);
            int px = centerX + (int) Math.round(Math.cos(radians) * radius);
            int py = centerY + (int) Math.round(Math.sin(radians) * radius);
            if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                drawContext.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static boolean isPointInsideCircle(int x, int y, int centerX, int centerY, int radiusSq) {
        int dx = x - centerX;
        int dy = y - centerY;
        return ((dx * dx) + (dy * dy)) <= radiusSq;
    }

    private static void renderPowerOverlay(DrawContext drawContext) {
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

        int filledPixels = Math.max(0, Math.min(POWER_BAR_HEIGHT, Math.round(charge * POWER_BAR_HEIGHT)));
        if (filledPixels > 0) {
            int fillTop = barBottom - filledPixels;
            int color = charge < 0.5f ? 0xFF3AC25B : 0xFFFFC336;
            drawContext.fill(barX + 1, fillTop, barX + POWER_BAR_WIDTH - 1, barBottom - 1, color);
        }

        int normalMarkY = barBottom - (POWER_BAR_HEIGHT / 2);
        drawContext.fill(barX - 1, normalMarkY, barX + POWER_BAR_WIDTH + 1, normalMarkY + 1, 0xFFFFFFFF);

        int percent = Math.round(charge * 100.0f);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(Integer.toString(percent) + "%"), barX - 8, barTop - 12, 0x66E3FF);
    }

    private static void refreshMiniMapRenderCache(MinecraftClient client, MiniMapState state, byte[] terrainCells) {
        if (client == null || state == null) {
            return;
        }

        int expectedLength = HoleMiniMapSync.TERRAIN_GRID_SIZE * HoleMiniMapSync.TERRAIN_GRID_SIZE;
        if (terrainCells.length != expectedLength) {
            if (miniMapRenderCache != null && miniMapRenderCache.matches(state)) {
                return;
            }
            return;
        }

        if (miniMapRenderCache != null && miniMapRenderCache.matches(state)) {
            clearMiniMapRenderCache(client);
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, false);
        try {
            int grid = HoleMiniMapSync.TERRAIN_GRID_SIZE;
            int denominator = Math.max(1, MINIMAP_TEXTURE_SIZE - 1);
            float center = (MINIMAP_TEXTURE_SIZE - 1) / 2.0f;
            float radiusSq = (center - 1.0f) * (center - 1.0f);
            for (int py = 0; py < MINIMAP_TEXTURE_SIZE; py++) {
                float terrainFz = (py / (float) denominator) * (grid - 1);
                float dy = py - center;
                for (int px = 0; px < MINIMAP_TEXTURE_SIZE; px++) {
                    float dx = px - center;
                    if ((dx * dx + dy * dy) > radiusSq) {
                        image.setColor(px, py, 0);
                        continue;
                    }
                    float terrainFx = (px / (float) denominator) * (grid - 1);
                    int color = sampleTerrainColorBilinear(terrainCells, grid, terrainFx, terrainFz);
                    image.setColor(px, py, argbToNativeRgba(color));
                }
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier textureId = client.getTextureManager().registerDynamicTexture("mcdg_minimap", texture);
            texture.upload();
            miniMapRenderCache = new MiniMapRenderCache(textureId, texture, state.holeIndex(), state.mapOriginX(), state.mapOriginZ(), state.mapSpan(), state.miniMapQualityPreset());
        } catch (RuntimeException ex) {
            image.close();
            throw ex;
        }
    }

    private static void clearMiniMapRenderCache(MinecraftClient client) {
        if (miniMapRenderCache == null) {
            return;
        }

        if (client != null) {
            client.getTextureManager().destroyTexture(miniMapRenderCache.textureId());
        }

        miniMapRenderCache.texture().close();
        miniMapRenderCache = null;
    }

    private static int argbToNativeRgba(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }

    private record MiniMapState(
            int holeIndex,
            int teeX,
            int teeZ,
            int basketX,
            int basketZ,
            int lieX,
            int lieZ,
            int par,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            int miniMapQualityPreset,
            boolean strictMode,
            boolean hasAlternateAnchor,
            int alternateAnchorX,
            int alternateAnchorZ,
            int mapOriginX,
            int mapOriginZ,
            int mapSpan,
            byte[] terrainCells
    ) {
    }

    private record MiniMapRenderCache(
            Identifier textureId,
            NativeImageBackedTexture texture,
            int holeIndex,
            int mapOriginX,
            int mapOriginZ,
            int mapSpan,
            int miniMapQualityPreset
    ) {
        private boolean matches(MiniMapState state) {
            return state != null
                    && holeIndex == state.holeIndex()
                    && mapOriginX == state.mapOriginX()
                    && mapOriginZ == state.mapOriginZ()
                    && mapSpan == state.mapSpan()
                    && miniMapQualityPreset == state.miniMapQualityPreset();
        }
    }
}
