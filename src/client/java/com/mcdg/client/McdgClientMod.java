package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.game.ScorecardManager;
import com.mcdg.net.HoleMiniMapSync;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.StringHelper;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

public final class McdgClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcdg-minimap");
    private static final String AUTOCONNECT_SERVER_ENV = "MCDG_AUTOCONNECT_SERVER";
    private static final long AUTOCONNECT_RETRY_DELAY_MS = 3000L;
    private static final int POWER_BAR_HEIGHT = 72;
    private static final int POWER_BAR_WIDTH = 8;
    private static final int MINIMAP_PADDING = 8;
    private static final long MINIMAP_STALE_TIMEOUT_MS = 15000L;
    private static final int MINIMAP_COLOR_UNSET = Integer.MIN_VALUE;
    private static final int PASSIVE_MINIMAP_SPAN_BLOCKS = 64;
    private static final int MINIMAP_TEXTURE_SIZE = PASSIVE_MINIMAP_SPAN_BLOCKS; // 1 texture pixel = 1 world block
    private static final int[] MINIMAP_SIZES = { 84, 104, 126 };
    private static final int[] MINIMAP_PANEL_ALPHA = { 0x8A, 0x6F, 0x58 };
    private static final int[] MINIMAP_SURFACE_ALPHA = { 0xD0, 0xB8, 0x9A };
    private static final int HUD_CARD_BG = 0xA5121822;
    private static final int HUD_CARD_BORDER = 0xA63A4E66;
    private static final int HUD_CARD_HEADER_BG = 0xB01B2638;
    private static final int HUD_CARD_TEXT = 0xE8EEF7;
    private static final int HUD_CARD_MUTED_TEXT = 0xAAB8CC;
    private static final String[] COMPASS_8 = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
        private static final int[] WAYPOINT_COLORS = {
            0xFFFF4D4D,
            0xFF57D163,
            0xFF4D9DFF,
            0xFFFFD247,
            0xFFC76CFF,
            0xFFF2F5FF
        };
        private static final String[] WAYPOINT_COLOR_NAMES = { "Red", "Green", "Blue", "Yellow", "Purple", "White" };

    private static long nextAutoconnectAttemptAt = 0L;
    private static boolean autoconnectSatisfied = false;
    private static String autoconnectServer = readAutoconnectServer();
    private static MiniMapState miniMapState;
    private static long miniMapReceivedAtMs;
    private static int miniMapStyleIndex = 1;
    private static MiniMapRenderCache miniMapRenderCache;
    private static KeyBinding increaseMiniMapSizeKey;
    private static KeyBinding decreaseMiniMapSizeKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding removeNearestWaypointKey;
    private static KeyBinding toggleWaypointLabelsKey;
    private static long hudVisibleSinceMs;
    private static float displayedDistanceFeet = Float.NaN;
    private static float displayedDistanceMeters = Float.NaN;
    private static float displayedTotalStrokes = Float.NaN;
    private static float displayedCumulativeDelta = Float.NaN;
    private static MiniMapRenderDebug miniMapRenderDebug = MiniMapRenderDebug.empty();
    private static long nextMiniMapDebugActionBarAtMs = 0L;
    private static long nextMiniMapDebugLogAtMs = 0L;
    private static int nextWaypointIndex = 1;
    private static boolean waypointLabelsVisible = true;
    private static String loadedWaypointContextKey = "";
    private static WaypointPromptStage waypointPromptStage = WaypointPromptStage.NONE;
    private static String pendingWaypointName;
    private static String pendingWaypointContextKey;
    private static int pendingWaypointX;
    private static int pendingWaypointZ;
    private static final List<ClientWaypoint> clientWaypoints = new ArrayList<>();


    @Override
    public void onInitializeClient() {
        increaseMiniMapSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mcdg.minimap_size_up",
                InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
                "category.mcdg"
        ));
        decreaseMiniMapSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mcdg.minimap_size_down",
                InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
                "category.mcdg"
        ));
        addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mcdg.add_waypoint",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "category.mcdg"
        ));
        removeNearestWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mcdg.remove_nearest_waypoint",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "category.mcdg"
        ));
        toggleWaypointLabelsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mcdg.toggle_waypoint_labels",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
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
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> handleWaypointPromptInput(message));
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
                        payload.strictMode(),
                        payload.hasAlternateAnchor(),
                        payload.alternateAnchorX(),
                        payload.alternateAnchorZ(),
                        payload.mapSpan()
                );
                miniMapReceivedAtMs = System.currentTimeMillis();
                refreshMiniMapRenderCache(context.client(), PASSIVE_MINIMAP_SPAN_BLOCKS);
            });
        });
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            updateHudTweens();
            renderHoleMiniMapOverlay(drawContext);
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

    private static void renderHoleMiniMapOverlay(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        int mapSpan = PASSIVE_MINIMAP_SPAN_BLOCKS;
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
        drawHudCard(drawContext, client, panelX, panelY, headerW, headerH, null, hudAlpha);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(holeLabel).formatted(Formatting.GRAY), panelX + 4, panelY + 2, withAlpha(HUD_CARD_MUTED_TEXT, hudAlpha));

        int mapX = panelX;
        int mapY = panelY + headerH + 2;
        int mapCenterX = mapX + (miniMapSize / 2);
        int mapCenterY = mapY + (miniMapSize / 2);
        // mapScale: screen pixels per world block. Texture is 1px/block so this is also the draw scale factor.
        float mapScale = miniMapSize / Math.max(1.0f, (float) mapSpan);
        double playerWorldX = client.player.getX();
        double playerWorldZ = client.player.getZ();
        int playerFeetX = net.minecraft.util.math.MathHelper.floor(playerWorldX);
        int playerFeetY = net.minecraft.util.math.MathHelper.floor(client.player.getY());
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(playerWorldZ);
        // Sub-block pixel shift: smooth map scroll as player moves within a block.
        double centerBlockX = (miniMapRenderCache != null ? miniMapRenderCache.centerX() : playerFeetX) + 0.5d;
        double centerBlockZ = (miniMapRenderCache != null ? miniMapRenderCache.centerZ() : playerFeetZ) + 0.5d;
        // texScale: how many screen pixels per texture pixel (= per block).
        float texScale = (float) miniMapSize / MINIMAP_TEXTURE_SIZE;
        // Sub-block offsets in screen pixels — keeps map scrolling smooth between block positions.
        float subBlockShiftX = (float) (playerWorldX - centerBlockX) * texScale;
        float subBlockShiftZ = (float) (playerWorldZ - centerBlockZ) * texScale;
        // With exact player-centered map shift, keep marker fixed at the card center.
        int playerPx = mapCenterX;
        int playerPz = mapCenterY;
        float mapRadius = (miniMapSize / 2.0f) - 1.0f;
        drawContext.fill(mapX, mapY, mapX + miniMapSize, mapY + miniMapSize, withAlpha((surfaceAlpha << 24) | 0x121212, hudAlpha));

        if (miniMapRenderCache != null && miniMapRenderCache.textureId() != null) {
            drawContext.enableScissor(mapX, mapY, mapX + miniMapSize, mapY + miniMapSize);
            var matrices = drawContext.getMatrices();
            matrices.push();
            // Scale the 64×64 texture (1px/block) up to fill the miniMapSize display,
            // then apply sub-block shift so the player's exact position stays centered.
            matrices.translate(mapX - subBlockShiftX, mapY - subBlockShiftZ, 0);
            matrices.scale(texScale, texScale, 1.0f);
            drawContext.drawTexture(miniMapRenderCache.textureId(), 0, 0, 0, 0, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE);
            matrices.pop();

            drawWaypoints(drawContext, client, mapCenterX, mapCenterY, playerWorldX, playerWorldZ, mapScale, hudAlpha, waypointLabelsVisible, mapCenterX, mapCenterY, mapRadius);

            drawDotCircleClipped(drawContext, playerPx, playerPz, 2, withAlpha(0xFFFFFFFF, hudAlpha), mapCenterX, mapCenterY, mapRadius);
            if (debugHud) {
                drawDotCircleClipped(drawContext, mapCenterX, mapCenterY, 1, withAlpha(0xFF00E5FF, hudAlpha), mapCenterX, mapCenterY, mapRadius);
                drawLine(drawContext, mapCenterX, mapCenterY, playerPx, playerPz, withAlpha(0xFF00E5FF, hudAlpha));
            }
            drawMiniMapCircularMask(drawContext, mapX, mapY, miniMapSize, withAlpha((MINIMAP_PANEL_ALPHA[Math.max(0, Math.min(MINIMAP_PANEL_ALPHA.length - 1, miniMapStyleIndex))] << 24) | 0x080B12, hudAlpha));
            drawCircleOutline(drawContext, mapCenterX, mapCenterY, mapRadius, withAlpha(HUD_CARD_BORDER, hudAlpha));
            drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, hudAlpha);
            drawContext.disableScissor();
        }

        String coordsLine = "XYZ " + playerFeetX + " " + playerFeetY + " " + playerFeetZ;
        String waypointLine = clientWaypoints.isEmpty() ? "Waypoints none" : ("Waypoints " + clientWaypoints.size());
        int centerDx = miniMapRenderDebug.centerWorldX() - playerFeetX;
        int centerDz = miniMapRenderDebug.centerWorldZ() - playerFeetZ;
        String centerLine = "Center " + miniMapRenderDebug.centerWorldX() + " " + miniMapRenderDebug.centerWorldZ()
            + " y " + miniMapRenderDebug.centerSurfaceY()
            + " " + miniMapRenderDebug.centerFluid()
            + " src " + miniMapRenderDebug.centerSource()
            + " vis " + miniMapRenderDebug.visibleSurfaceSourcePixels()
            + " fb " + miniMapRenderDebug.heightmapFallbackSourcePixels()
            + " miss " + miniMapRenderDebug.chunkUnloadedSourcePixels()
            + " d " + centerDx + " " + centerDz;
        String labelLine = "Labels " + (waypointLabelsVisible ? "ON" : "OFF");
        int infoW = Math.max(
                client.textRenderer.getWidth(coordsLine),
            Math.max(
                Math.max(client.textRenderer.getWidth(waypointLine), client.textRenderer.getWidth(labelLine)),
                client.textRenderer.getWidth(centerLine)
            )
        ) + 12;
        int infoX = panelX;
        int infoY = mapY + miniMapSize + 3;
        int infoH = 61;
        drawHudCard(drawContext, client, infoX, infoY, infoW, infoH, null, hudAlpha);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(coordsLine), infoX + 6, infoY + 16, withAlpha(0x9BE7FF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(waypointLine), infoX + 6, infoY + 27, withAlpha(0xD3E7FF, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(centerLine), infoX + 6, infoY + 38, withAlpha(0xF2D98E, hudAlpha));
        if (debugHud) {
            publishMiniMapDebug(client);
        }
    }

    private static void drawWaypoints(
            DrawContext drawContext,
            MinecraftClient client,
            int mapCenterX,
            int mapCenterY,
            double centerWorldX,
            double centerWorldZ,
            float mapScale,
            float hudAlpha,
            boolean drawLabels,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        for (ClientWaypoint waypoint : clientWaypoints) {
            int waypointPx = mapCenterX + Math.round((float) ((waypoint.x() - centerWorldX) * mapScale));
            int waypointPz = mapCenterY + Math.round((float) ((waypoint.z() - centerWorldZ) * mapScale));
            drawDotCircleClipped(drawContext, waypointPx, waypointPz, 2, withAlpha(waypoint.color(), hudAlpha), clipCenterX, clipCenterY, clipRadius);
            if (drawLabels && isPointInsideCircle(waypointPx + 4, waypointPz - 6, clipCenterX, clipCenterY, clipRadius * clipRadius)) {
                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(waypoint.name()), waypointPx + 3, waypointPz - 8, withAlpha(0xE8EEF7, hudAlpha));
            }
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

    private static int miniMapTerrainColor(int terrainClass) {
        return switch (terrainClass) {
            case 1 -> 0xFF3F76E4;
            case 2 -> 0xFFF7E9A3;
            case 3 -> 0xFF7FB238;
            case 4 -> 0xFF4C8E2F;
            case 5 -> 0xFFA0A0A0;
            case 6 -> 0xFFFFFFFF;
            case 7 -> 0xFFA0A0FF;
            case 8 -> 0xFF8B6D4A;
            case 9 -> 0xFFA58F6A;
            case 10 -> 0xFFFF6A00;
            default -> 0;
        };
    }

    private static void drawMiniMapDistanceRings(
            DrawContext drawContext,
            MinecraftClient client,
            float centerX,
            float centerY,
            int mapX,
            int mapY,
            int miniMapSize,
            float mapScale,
            float hudAlpha,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        int[] ringFeet = { 50, 100, 150 };
        for (int feet : ringFeet) {
            float radiusBlocks = feet / 3.28084f;
            int radiusPx = Math.max(2, Math.round(radiusBlocks * mapScale));
            int color = withAlpha(0x66BFD5E9, hudAlpha);
            drawCircleOutline(drawContext, centerX, centerY, radiusPx, color);
        }
    }

    private static void drawCircleOutline(DrawContext drawContext, float centerX, float centerY, float radius, int color) {
        for (int degrees = 0; degrees < 360; degrees += 8) {
            double radians = Math.toRadians(degrees);
            int px = Math.round(centerX + (float) Math.cos(radians) * radius);
            int py = Math.round(centerY + (float) Math.sin(radians) * radius);
            drawContext.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawFilledCircle(DrawContext drawContext, float centerX, float centerY, float radius, int color) {
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

    private static void drawMiniMapCardinalLabels(DrawContext drawContext, MinecraftClient client, float centerX, float centerY, int miniMapSize, float hudAlpha) {
        int radius = Math.max(8, (miniMapSize / 2) - 8);
        drawCardinalLabel(drawContext, client, "N", centerX, centerY - radius, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "E", centerX + radius, centerY, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "S", centerX, centerY + radius, 0xFFDDEEFF, hudAlpha);
        drawCardinalLabel(drawContext, client, "W", centerX - radius, centerY, 0xFFDDEEFF, hudAlpha);
    }

    private static void drawCardinalLabel(DrawContext drawContext, MinecraftClient client, String label, float x, float y, int color, float hudAlpha) {
        int textWidth = client.textRenderer.getWidth(label);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(label), Math.round(x - (textWidth / 2.0f)), Math.round(y - 4.0f), withAlpha(color, hudAlpha));
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
        drawLegendSwatch(drawContext, client, startX, y, 0xFF3F76E4, "W", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 16, y, 0xFF4C8E2F, "T", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 32, y, 0xFFA0A0A0, "R", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 48, y, 0xFFFFFFFF, "S", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 64, y, 0xFFCC8D32, "Hz", hudAlpha);
        drawLegendSwatch(drawContext, client, startX + 84, y, 0xFF3F76E4, "OB", hudAlpha);
    }

    private static void drawLegendSwatch(DrawContext drawContext, MinecraftClient client, int x, int y, int color, String label, float hudAlpha) {
        drawContext.fill(x, y + 2, x + 4, y + 6, withAlpha(color, hudAlpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(label), x + 6, y, withAlpha(0xD3E7FF, hudAlpha));
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

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static float tween(float current, float target, float factor) {
        if (Float.isNaN(current)) {
            return target;
        }
        return current + ((target - current) * factor);
    }

    private static void handleMiniMapHotkeys(MinecraftClient client) {
        ensureWaypointContextLoaded(client);

        while (increaseMiniMapSizeKey.wasPressed()) {
            miniMapStyleIndex = Math.min(MINIMAP_SIZES.length - 1, miniMapStyleIndex + 1);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Mini-map size: " + MINIMAP_SIZES[miniMapStyleIndex] + "px").formatted(Formatting.GRAY), true);
            }
        }

        while (decreaseMiniMapSizeKey.wasPressed()) {
            miniMapStyleIndex = Math.max(0, miniMapStyleIndex - 1);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Mini-map size: " + MINIMAP_SIZES[miniMapStyleIndex] + "px").formatted(Formatting.GRAY), true);
            }
        }

        while (addWaypointKey.wasPressed()) {
            if (client.player == null) {
                continue;
            }
            beginWaypointPrompt(client);
        }

        while (removeNearestWaypointKey.wasPressed()) {
            if (client.player == null) {
                continue;
            }
            if (clientWaypoints.isEmpty()) {
                client.player.sendMessage(Text.literal("No waypoints to remove.").formatted(Formatting.GRAY), true);
                continue;
            }
            int x = net.minecraft.util.math.MathHelper.floor(client.player.getX());
            int z = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
            ClientWaypoint nearest = null;
            int nearestDistSq = Integer.MAX_VALUE;
            for (ClientWaypoint waypoint : clientWaypoints) {
                int dx = waypoint.x() - x;
                int dz = waypoint.z() - z;
                int distSq = (dx * dx) + (dz * dz);
                if (distSq < nearestDistSq) {
                    nearest = waypoint;
                    nearestDistSq = distSq;
                }
            }
            if (nearest != null) {
                clientWaypoints.remove(nearest);
                saveWaypointStore(client);
                client.player.sendMessage(Text.literal("Waypoint removed: " + nearest.name()).formatted(Formatting.GRAY), true);
            }
        }

        while (toggleWaypointLabelsKey.wasPressed()) {
            waypointLabelsVisible = !waypointLabelsVisible;
            saveWaypointStore(client);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Waypoint labels " + (waypointLabelsVisible ? "ON" : "OFF")).formatted(Formatting.GRAY), true);
            }
        }
    }

    private static void beginWaypointPrompt(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        ensureWaypointContextLoaded(client);
        pendingWaypointX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        pendingWaypointZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
        pendingWaypointContextKey = loadedWaypointContextKey;
        pendingWaypointName = null;
        waypointPromptStage = WaypointPromptStage.WAITING_NAME;
        client.player.sendMessage(Text.literal("Waypoint name? Type it in chat and press Enter.").formatted(Formatting.LIGHT_PURPLE), false);
    }

    private static boolean handleWaypointPromptInput(String message) {
        if (waypointPromptStage == WaypointPromptStage.NONE) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            waypointPromptStage = WaypointPromptStage.NONE;
            return true;
        }

        String trimmed = message == null ? "" : message.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            waypointPromptStage = WaypointPromptStage.NONE;
            pendingWaypointName = null;
            pendingWaypointContextKey = null;
            client.player.sendMessage(Text.literal("Waypoint add canceled.").formatted(Formatting.GRAY), false);
            return false;
        }

        if (waypointPromptStage == WaypointPromptStage.WAITING_NAME) {
            String name = trimmed;
            if (name.isEmpty()) {
                name = "WP" + nextWaypointIndex;
            }
            pendingWaypointName = StringHelper.truncate(name, 24, false);
            waypointPromptStage = WaypointPromptStage.WAITING_COLOR;
            client.player.sendMessage(Text.literal("Color? 1-Red 2-Green 3-Blue 4-Yellow 5-Purple 6-White").formatted(Formatting.AQUA), false);
            return false;
        }

        int colorIndex = parseWaypointColorIndex(trimmed);
        if (colorIndex < 0) {
            client.player.sendMessage(Text.literal("Choose color by number/name: Red, Green, Blue, Yellow, Purple, White").formatted(Formatting.RED), false);
            return false;
        }

        ensureWaypointContextLoaded(client);
        if (pendingWaypointContextKey != null && !pendingWaypointContextKey.equals(loadedWaypointContextKey)) {
            client.player.sendMessage(Text.literal("World changed while adding waypoint; try again.").formatted(Formatting.RED), false);
            waypointPromptStage = WaypointPromptStage.NONE;
            pendingWaypointName = null;
            pendingWaypointContextKey = null;
            return false;
        }

        String name = pendingWaypointName == null || pendingWaypointName.isBlank() ? ("WP" + nextWaypointIndex) : pendingWaypointName;
        nextWaypointIndex++;
        int color = WAYPOINT_COLORS[colorIndex];
        clientWaypoints.add(new ClientWaypoint(name, pendingWaypointX, pendingWaypointZ, color));
        saveWaypointStore(client);
        client.player.sendMessage(Text.literal("Waypoint added: " + name + " (" + pendingWaypointX + ", " + pendingWaypointZ + ") " + WAYPOINT_COLOR_NAMES[colorIndex]).formatted(Formatting.LIGHT_PURPLE), false);

        waypointPromptStage = WaypointPromptStage.NONE;
        pendingWaypointName = null;
        pendingWaypointContextKey = null;
        return false;
    }

    private static int parseWaypointColorIndex(String value) {
        if (value == null) {
            return -1;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return -1;
        }

        try {
            int numeric = Integer.parseInt(normalized);
            if (numeric >= 1 && numeric <= WAYPOINT_COLORS.length) {
                return numeric - 1;
            }
        } catch (NumberFormatException ignored) {
        }

        for (int i = 0; i < WAYPOINT_COLOR_NAMES.length; i++) {
            if (WAYPOINT_COLOR_NAMES[i].toLowerCase(Locale.ROOT).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private static void ensureWaypointContextLoaded(MinecraftClient client) {
        String contextKey = currentWaypointContextKey(client);
        if (Objects.equals(contextKey, loadedWaypointContextKey)) {
            return;
        }

        loadedWaypointContextKey = contextKey;
        waypointPromptStage = WaypointPromptStage.NONE;
        pendingWaypointName = null;
        pendingWaypointContextKey = null;
        loadWaypointStore(client);
    }

    private static String currentWaypointContextKey(MinecraftClient client) {
        if (client == null) {
            return "menu";
        }

        if (client.getCurrentServerEntry() != null) {
            return "server_" + sanitizeContextSegment(client.getCurrentServerEntry().address);
        }

        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            Path saveRoot = client.getServer().getSavePath(WorldSavePath.ROOT);
            Path fileName = saveRoot.getFileName();
            String saveName = fileName == null ? saveRoot.toString() : fileName.toString();
            return "save_" + sanitizeContextSegment(saveName);
        }

        if (client.world != null) {
            return "world_" + sanitizeContextSegment(client.world.getRegistryKey().getValue().toString());
        }

        return "menu";
    }

    private static String sanitizeContextSegment(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path waypointStorePath(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("config")
                .resolve("mcdg-waypoints")
                .resolve(loadedWaypointContextKey + ".txt");
    }

    private static void loadWaypointStore(MinecraftClient client) {
        clientWaypoints.clear();
        nextWaypointIndex = 1;
        waypointLabelsVisible = true;

        if (client == null || loadedWaypointContextKey.isBlank()) {
            return;
        }

        Path storePath = waypointStorePath(client);
        if (!Files.exists(storePath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(storePath, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("nextIndex=")) {
                    nextWaypointIndex = Math.max(1, Integer.parseInt(line.substring("nextIndex=".length()).trim()));
                    continue;
                }

                if (line.startsWith("labelsVisible=")) {
                    waypointLabelsVisible = Boolean.parseBoolean(line.substring("labelsVisible=".length()).trim());
                    continue;
                }

                if (!line.startsWith("wp=")) {
                    continue;
                }

                String body = line.substring(3);
                String[] parts = body.split("\\t");
                if (parts.length != 4) {
                    continue;
                }

                String name = parts[0].replace("\\n", " ").replace("\\t", " ").trim();
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                int color = (int) Long.parseLong(parts[3], 16);
                if (!name.isEmpty()) {
                    clientWaypoints.add(new ClientWaypoint(StringHelper.truncate(name, 24, false), x, z, color));
                }
            }
        } catch (IOException | NumberFormatException ex) {
            LOGGER.warn("Unable to load waypoint store for context {}", loadedWaypointContextKey, ex);
            clientWaypoints.clear();
            nextWaypointIndex = 1;
            waypointLabelsVisible = true;
        }
    }

    private static void saveWaypointStore(MinecraftClient client) {
        if (client == null || loadedWaypointContextKey.isBlank()) {
            return;
        }

        Path storePath = waypointStorePath(client);
        try {
            Files.createDirectories(storePath.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# MCDG waypoint store");
            lines.add("nextIndex=" + nextWaypointIndex);
            lines.add("labelsVisible=" + waypointLabelsVisible);
            for (ClientWaypoint waypoint : clientWaypoints) {
                String safeName = waypoint.name().replace("\t", " ").replace("\n", " ").trim();
                lines.add("wp=" + safeName + "\t" + waypoint.x() + "\t" + waypoint.z() + "\t" + String.format("%08X", waypoint.color()));
            }
            Files.write(storePath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            LOGGER.warn("Unable to save waypoint store for context {}", loadedWaypointContextKey, ex);
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

    private static void drawDotCircleClipped(
            DrawContext drawContext,
            int x,
            int y,
            int radius,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float clipRadiusSq = clipRadius * clipRadius;
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
            float centerX,
            float centerY,
            float radius,
            int color,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        float clipRadiusSq = clipRadius * clipRadius;
        for (int degrees = 0; degrees < 360; degrees += 8) {
            double radians = Math.toRadians(degrees);
            int px = Math.round(centerX + (float) Math.cos(radians) * radius);
            int py = Math.round(centerY + (float) Math.sin(radians) * radius);
            if (isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                drawContext.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static boolean isPointInsideCircle(int x, int y, float centerX, float centerY, float radiusSq) {
        float dx = x - centerX;
        float dy = y - centerY;
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

    private static void refreshMiniMapRenderCache(MinecraftClient client, int mapSpan) {
        if (client == null || mapSpan <= 0) {
            return;
        }

        int playerFeetX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());

        if (miniMapRenderCache != null && miniMapRenderCache.matches(mapSpan, playerFeetX, playerFeetZ)) {
            return;
        }

        if (miniMapRenderCache != null) {
            clearMiniMapRenderCache(client);
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, false);
        try {
            boolean renderedFromClientWorld = renderMiniMapFromClientWorld(image, client, mapSpan);
            if (!renderedFromClientWorld) {
                miniMapRenderDebug = MiniMapRenderDebug.serverOnly();
                return;
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier textureId = client.getTextureManager().registerDynamicTexture("mcdg_minimap", texture);
            texture.upload();
            miniMapRenderCache = new MiniMapRenderCache(textureId, texture, mapSpan, playerFeetX, playerFeetZ);
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
        double centerWorldX = (playerFeetX + 0.5d);
        double centerWorldZ = (playerFeetZ + 0.5d);
        String centerSource = "unknown";
        String centerFluid = MiniMapFluidKind.NONE.debugLabel();
        int centerSurfaceY = worldBottom(client.world);
        int centerWorldSampleX = 0;
        int centerWorldSampleZ = 0;
        int clientSamplePixels = 0;
        int serverFallbackPixels = 0;
        int visibleSurfaceSourcePixels = 0;
        int heightmapFallbackSourcePixels = 0;
        int chunkUnloadedSourcePixels = 0;

        for (int py = 0; py < MINIMAP_TEXTURE_SIZE; py++) {
            float dz = (py - centerPy) / texDenominator;
            int worldZ = net.minecraft.util.math.MathHelper.floor(centerWorldZ + (dz * mapSpan));
            for (int px = 0; px < MINIMAP_TEXTURE_SIZE; px++) {
                float dx = (px - centerPx) / texDenominator;
                int worldX = net.minecraft.util.math.MathHelper.floor(centerWorldX + (dx * mapSpan));

                TerrainSampleResult terrainSample = sampleClientWorldTerrain(client.world, worldX, worldZ);
                boolean usedClientSample = terrainSample.color() != MINIMAP_COLOR_UNSET;
                int baseColor = terrainSample.color();
                switch (terrainSample.source()) {
                    case VISIBLE_SURFACE -> visibleSurfaceSourcePixels++;
                    case HEIGHTMAP_FALLBACK -> heightmapFallbackSourcePixels++;
                    case CHUNK_UNLOADED -> chunkUnloadedSourcePixels++;
                }
                if (usedClientSample) {
                    clientSamplePixels++;
                }
                if (!usedClientSample) {
                    serverFallbackPixels++;
                    baseColor = 0xFF5E6F86;
                }

                if (px == centerPx && py == centerPy) {
                    centerSource = terrainSample.source().debugLabel();
                    centerFluid = terrainSample.fluidKind().debugLabel();
                    centerSurfaceY = terrainSample.surfaceY();
                    centerWorldSampleX = worldX;
                    centerWorldSampleZ = worldZ;
                }

                int shadedArgb = applyVisibleSurfaceShading(client.world, worldX, worldZ, baseColor);
                image.setColor(px, py, argbToAbgr(shadedArgb));
            }
        }

        miniMapRenderDebug = new MiniMapRenderDebug(
            centerSource,
            centerFluid,
            centerSurfaceY,
            centerWorldSampleX,
            centerWorldSampleZ,
            clientSamplePixels,
            serverFallbackPixels,
            visibleSurfaceSourcePixels,
            heightmapFallbackSourcePixels,
            chunkUnloadedSourcePixels
        );

        return true;
    }

    private static void publishMiniMapDebug(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String summary = miniMapRenderDebug.summary();

        if (now >= nextMiniMapDebugActionBarAtMs) {
            client.player.sendMessage(Text.literal(summary).formatted(Formatting.YELLOW), true);
            nextMiniMapDebugActionBarAtMs = now + 1500L;
        }

        if (now >= nextMiniMapDebugLogAtMs) {
            LOGGER.info("MinimapDebug {}", summary);
            nextMiniMapDebugLogAtMs = now + 5000L;
        }
    }

    private static TerrainSampleResult sampleClientWorldTerrain(ClientWorld world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return new TerrainSampleResult(
                    MINIMAP_COLOR_UNSET,
                    false,
                    MiniMapSampleSource.CHUNK_UNLOADED,
                    MiniMapFluidKind.NONE,
                    worldBottom(world)
            );
        }

        int topSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int startY = topSurfaceY;
        if (startY < world.getBottomY()) {
            return new TerrainSampleResult(
                    0xFF5E6F86,
                    false,
                    MiniMapSampleSource.HEIGHTMAP_FALLBACK,
                    MiniMapFluidKind.NONE,
                    worldBottom(world)
            );
        }

        SurfaceResolveResult resolvedSurface = resolveVisibleSurfaceForSampling(world, x, z, startY);
        BlockPos surface = resolvedSurface.surface();
        BlockState state = world.getBlockState(surface);
        if (world.getFluidState(surface).isIn(FluidTags.LAVA)) {
            return new TerrainSampleResult(
                    0xFFFF6A00,
                    false,
                    resolvedSurface.source(),
                    MiniMapFluidKind.LAVA,
                    surface.getY()
            );
        }
        if (world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return new TerrainSampleResult(
                    0xFF3F76E4,
                    true,
                    resolvedSurface.source(),
                    MiniMapFluidKind.WATER,
                    surface.getY()
            );
        }

        MapColor mapColor = state.getMapColor(world, surface);
        if (mapColor != null && mapColor != MapColor.CLEAR) {
            return new TerrainSampleResult(
                    0xFF000000 | mapColor.color,
                    false,
                    resolvedSurface.source(),
                    MiniMapFluidKind.NONE,
                    surface.getY()
            );
        }

        int terrainClass = classifyClientMiniMapTerrainClass(world, x, z, surface.getY());
        int color = miniMapTerrainColor(terrainClass);
        if (color == 0) {
            return new TerrainSampleResult(
                    0xFF6B7C93,
                    false,
                    resolvedSurface.source(),
                    MiniMapFluidKind.NONE,
                    surface.getY()
            );
        }
        return new TerrainSampleResult(
                color,
                false,
                resolvedSurface.source(),
                MiniMapFluidKind.NONE,
                surface.getY()
        );
    }

    private static SurfaceResolveResult resolveVisibleSurfaceForSampling(ClientWorld world, int x, int z, int startY) {
        int y = Math.max(world.getBottomY(), Math.min(startY, world.getTopY() - 1));
        int attempts = 0;
        int maxDownChecks = 6;
        boolean usedHeightmapFallback = false;
        while (y > world.getBottomY() && attempts < maxDownChecks) {
            BlockPos probe = new BlockPos(x, y, z);
            if (!world.getBlockState(probe).isAir() || !world.getFluidState(probe).isEmpty()) {
                break;
            }
            y--;
            attempts++;
        }

        BlockPos resolved = new BlockPos(x, y, z);
        if (world.getBlockState(resolved).isAir() && world.getFluidState(resolved).isEmpty()) {
            int fallbackY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            y = Math.max(world.getBottomY(), Math.min(fallbackY, world.getTopY() - 1));
            attempts = 0;
            usedHeightmapFallback = true;
            while (y > world.getBottomY() && attempts < 6) {
                BlockPos probe = new BlockPos(x, y, z);
                if (!world.getBlockState(probe).isAir() || !world.getFluidState(probe).isEmpty()) {
                    break;
                }
                y--;
                attempts++;
            }
            resolved = new BlockPos(x, y, z);
        }

        int noiseSkips = 0;
        while (y > world.getBottomY() && noiseSkips < 3) {
            BlockPos probe = new BlockPos(x, y, z);
            // Never skip through fluid columns; doing so can incorrectly convert
            // shoreline/water centers into solid terrain samples.
            if (!world.getFluidState(probe).isEmpty()) {
                break;
            }
            if (!isVisualNoiseSurface(world.getBlockState(probe))) {
                break;
            }
            y--;
            noiseSkips++;
        }
        resolved = new BlockPos(x, y, z);

        MiniMapSampleSource source = usedHeightmapFallback ? MiniMapSampleSource.HEIGHTMAP_FALLBACK : MiniMapSampleSource.VISIBLE_SURFACE;
        return new SurfaceResolveResult(resolved, source);
    }

    private static int worldBottom(ClientWorld world) {
        return world == null ? 0 : world.getBottomY();
    }

    private static boolean isVisualNoiseSurface(BlockState state) {
        return state.isOf(Blocks.SHORT_GRASS)
                || state.isOf(Blocks.TALL_GRASS)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.LARGE_FERN)
                || state.isOf(Blocks.DEAD_BUSH)
                || state.isOf(Blocks.SEAGRASS)
                || state.isIn(BlockTags.SMALL_FLOWERS)
                || state.isIn(BlockTags.TALL_FLOWERS);
    }

    private static int applyVisibleSurfaceShading(ClientWorld world, int x, int z, int baseColor) {
        if (baseColor == MINIMAP_COLOR_UNSET || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return 0xFF5E6F86;
        }

        int currentY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
        int westY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z) - 1;
        int delta = (currentY - northY) + (currentY - westY);
        float shade = 1.0f + Math.max(-0.18f, Math.min(0.18f, delta * 0.05f));
        return scaleColor(baseColor, shade);
    }

    private enum MiniMapSampleSource {
        VISIBLE_SURFACE("visible-surface"),
        HEIGHTMAP_FALLBACK("heightmap-fallback"),
        CHUNK_UNLOADED("chunk-unloaded");

        private final String debugLabel;

        MiniMapSampleSource(String debugLabel) {
            this.debugLabel = debugLabel;
        }

        private String debugLabel() {
            return debugLabel;
        }
    }

    private enum MiniMapFluidKind {
        NONE("solid"),
        WATER("water"),
        LAVA("lava");

        private final String debugLabel;

        MiniMapFluidKind(String debugLabel) {
            this.debugLabel = debugLabel;
        }

        private String debugLabel() {
            return debugLabel;
        }
    }

    private record SurfaceResolveResult(BlockPos surface, MiniMapSampleSource source) {
    }

    private record TerrainSampleResult(
            int color,
            boolean waterDetected,
            MiniMapSampleSource source,
            MiniMapFluidKind fluidKind,
            int surfaceY
    ) {
    }

    private record MiniMapRenderDebug(
            String centerSource,
            String centerFluid,
            int centerSurfaceY,
            int centerWorldX,
            int centerWorldZ,
            int clientPixels,
            int serverPixels,
            int visibleSurfaceSourcePixels,
            int heightmapFallbackSourcePixels,
            int chunkUnloadedSourcePixels
    ) {
        private static MiniMapRenderDebug empty() {
            return new MiniMapRenderDebug("n/a", "n/a", 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private static MiniMapRenderDebug serverOnly() {
            int allPixels = MINIMAP_TEXTURE_SIZE * MINIMAP_TEXTURE_SIZE;
            return new MiniMapRenderDebug("server-only", "n/a", 0, 0, 0, 0, allPixels, 0, 0, allPixels);
        }

        private String summary() {
            return "MM src=" + centerSource
                    + " y=" + centerSurfaceY
                    + " fluid=" + centerFluid
                    + " vis=" + visibleSurfaceSourcePixels
                    + " fb=" + heightmapFallbackSourcePixels
                    + " miss=" + chunkUnloadedSourcePixels
                    + " c=" + clientPixels
                    + " s=" + serverPixels;
        }
    }

    private static int classifyClientMiniMapTerrainClass(ClientWorld world, int x, int z, int surfaceY) {
        if (surfaceY < world.getBottomY()) {
            return 0;
        }

        BlockPos surface = new BlockPos(x, surfaceY, z);
        BlockState state = world.getBlockState(surface);
        if (world.getFluidState(surface).isIn(FluidTags.LAVA)) {
            return 10;
        }
        if (world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return 1;
        }
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)
                || state.isOf(Blocks.FROSTED_ICE)) {
            return 7;
        }
        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            return 6;
        }
        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.MOSS_BLOCK)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.SHORT_GRASS)) {
            return 3;
        }
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MUD) || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.SOUL_SOIL)) {
            return 8;
        }
        if (state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.FARMLAND)
                || state.isOf(Blocks.CLAY) || state.isOf(Blocks.GRAVEL)) {
            return 9;
        }
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
            return 2;
        }
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) {
            return 4;
        }
        if (state.isOf(Blocks.STONE) || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.CALCITE)) {
            return 5;
        }
        return 8;
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

    private enum WaypointPromptStage {
        NONE,
        WAITING_NAME,
        WAITING_COLOR
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
            boolean strictMode,
            boolean hasAlternateAnchor,
            int alternateAnchorX,
            int alternateAnchorZ,
                int mapSpan
    ) {
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

    private record ClientWaypoint(String name, int x, int z, int color) {
    }
}
