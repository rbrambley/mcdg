package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.net.WaypointSync;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.RoundRunningScoresSync;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.StringHelper;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McdgClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcdg-minimap");
    private static final int MINIMAP_PADDING = 8;
    private static final long MINIMAP_STALE_TIMEOUT_MS = 15000L;
    private static final int MINIMAP_COLOR_UNSET = Integer.MIN_VALUE;
    private static final int PASSIVE_MINIMAP_SPAN_BLOCKS = 96;
    private static final int MINIMAP_JOIN_PRIME_TICKS = 100;
    private static final int WAYPOINT_FLOAT_LABEL_ENTER_BLOCKS = 160;
    private static final int WAYPOINT_EDGE_ARROW_ENTER_BLOCKS = 120;
    private static final int WAYPOINT_FLOAT_LABEL_MAX_BLOCKS = 160;
    private static final int WAYPOINT_EDGE_ARROW_MAX_BLOCKS = 320;
    private static final int WAYPOINT_BEAM_HEIGHT_BLOCKS = 64;
    private static final float WAYPOINT_BEAM_ALPHA = 0.60f;
    private static final int UNKNOWN_WAYPOINT_Y = Integer.MIN_VALUE;
    private static final int WAYPOINT_COURSE_COLOR = 0xFF66CC66;
    private static final int WAYPOINT_HOLE_TEMP_COLOR = 0xFFFFFFFF;
    private static final int MINIMAP_TEXTURE_SIZE = 128; // Higher sample density while keeping a wider world span.
    private static final int[] MINIMAP_SIZES = { 84, 104, 126 };
    private static final int[] MINIMAP_SURFACE_ALPHA = { 0xD0, 0xB8, 0x9A };
    private static final int HUD_CARD_BORDER = 0xA63A4E66;
    private static final int HUD_CARD_MUTED_TEXT = 0xAAB8CC;
    private static final int HAZARD_OVERLAY_ARGB = 0x8CFF9A32;
    private static final int HAZARD_SAMPLE_STEP_PX = 2;
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 7;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;
    private static final Identifier TRAINING_DISC_CHARGED_PREDICATE = new Identifier("mcdg", "charged");
    private static final Identifier MINIMAP_TEE_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_tee_marker.png");
    private static final Identifier MINIMAP_BASKET_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_basket_marker.png");
    private static final Identifier MINIMAP_LIE_MARKER_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_lie_marker.png");
    private static final Identifier MINIMAP_BREADCRUMB_TEXTURE = new Identifier("mcdg", "textures/block/mcdg_breadcrumb.png");
        private static final int[] WAYPOINT_COLORS = {
            0xFFFF4D4D,
            0xFF57D163,
            0xFF4D9DFF,
            0xFFFFD247,
            0xFFC76CFF,
            0xFFF2F5FF
        };
        private static final String[] WAYPOINT_COLOR_NAMES = { "Red", "Green", "Blue", "Yellow", "Purple", "White" };

    private static MiniMapState miniMapState;
    private static long miniMapReceivedAtMs;
    private static int miniMapStyleIndex = 1;
    private static MiniMapRenderCache miniMapRenderCache;
    private static long hudVisibleSinceMs;
    private static MiniMapRenderDebug miniMapRenderDebug = MiniMapRenderDebug.empty();
    private static long lastMiniMapRenderAtMs = 0L;
    private static int nextWaypointIndex = 1;
    private static boolean waypointLabelsVisible = true;
    private static boolean miniMapJoinWarmupPending;
    private static int miniMapJoinPrimeTicksRemaining;
    private static boolean waypointsDirty = false;
    private static String activeRoundCourseWaypointName = "";
    private static String loadedWaypointContextKey = "";
    private static String lastSentWaypointSyncSignature = "";
    private static String loadedWaypointDimensionKey = "";
    private static WaypointPromptStage waypointPromptStage = WaypointPromptStage.NONE;
    private static String pendingWaypointName;
    private static String pendingWaypointContextKey;
    private static int pendingWaypointX;
    private static int pendingWaypointY;
    private static int pendingWaypointZ;
    private static RunningRoundScoreState runningRoundScoreState;
    private static final List<ClientWaypoint> clientWaypoints = new ArrayList<>();
    private static final List<ClientWaypoint> roundHoleWaypoints = new ArrayList<>();
    private static final Map<String, WaypointRenderMode> waypointRenderModes = new HashMap<>();


    @Override
    public void onInitializeClient() {
        ModelPredicateProviderRegistry.register(
                McdgItems.TRAINING_DISC,
                TRAINING_DISC_CHARGED_PREDICATE,
                (stack, world, entity, seed) -> {
                    if (!ChargedDiscItem.isClientChargeVisible()) {
                        return 0.0f;
                    }
                    return ChargedDiscItem.getClientChargePercent() >= 0.15f ? 1.0f : 0.0f;
                }
        );

        ClientKeybinds.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.options.getChatScale().setValue(0.65);
            client.options.getChatHeightUnfocused().setValue(0.25);
            client.options.write();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (waypointsDirty) {
                maybeSyncClientWaypoints(client);
                waypointsDirty = false;
            }
            AutoConnect.tick(client);
            handleMiniMapHotkeys(client);
            tickMiniMapJoinPrime(client);
            CinematicOverlay.tick(client);
            RoundInfoOverlay.updateTweens(miniMapState);
        });
        // When a chunk arrives from the server, reset the minimap rebuild timer so the
        // next render frame picks up the newly loaded terrain rather than waiting up to
        // 350 ms.  This fixes the gray minimap seen on initial server join while chunks
        // are still streaming in.
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            lastMiniMapRenderAtMs = 0L;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            miniMapJoinWarmupPending = true;
            miniMapJoinPrimeTicksRemaining = MINIMAP_JOIN_PRIME_TICKS;
            lastMiniMapRenderAtMs = 0L;
            clearMiniMapRenderCache(client);
            ensureWaypointContextLoaded(client);
            syncClientWaypointsToServer(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            removePermanentCourseWaypoint(client, activeRoundCourseWaypointName);
            miniMapJoinWarmupPending = false;
            miniMapJoinPrimeTicksRemaining = 0;
            miniMapState = null;
            miniMapReceivedAtMs = 0L;
            activeRoundCourseWaypointName = "";
            lastSentWaypointSyncSignature = "";
            roundHoleWaypoints.clear();
            waypointRenderModes.clear();
            loadedWaypointDimensionKey = "";
            clearMiniMapRenderCache(client);
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> handleWaypointPromptInput(message));
        ClientNetworking.registerReceivers();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            RoundInfoOverlay.updateTweens(miniMapState);
            float hudAlpha = hudFadeAlpha();
            renderHoleMiniMapOverlay(drawContext);
            RoundInfoOverlay.render(drawContext, miniMapState, hudAlpha);
            ScorecardOverlay.render(drawContext, miniMapState, miniMapReceivedAtMs, hudAlpha);
            RunningScoreboardOverlay.render(drawContext, runningRoundScoreState, hudAlpha);
            HudOverlays.renderCompass(drawContext);
            HudOverlays.renderPower(drawContext);
            CinematicOverlay.render(drawContext);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(McdgClientMod::renderWaypointWorldLabels);
    }

    private static void tickMiniMapJoinPrime(MinecraftClient client) {
        if (!miniMapJoinWarmupPending) {
            return;
        }

        if (client == null || client.player == null || client.world == null) {
            return;
        }

        // Prime like a movement-triggered refresh for a short post-join window,
        // without actually moving the player.
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


    private static void renderHoleMiniMapOverlay(DrawContext drawContext) {
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
        // mapScale: screen pixels per world block. Texture is 1px/block so this is also the draw scale factor.
        float mapScale = miniMapSize / Math.max(1.0f, (float) mapSpan);
        double playerWorldX = client.player.getX();
        double playerWorldZ = client.player.getZ();
        int playerFeetX = net.minecraft.util.math.MathHelper.floor(playerWorldX);
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(playerWorldZ);
        // Sub-block pixel shift: smooth map scroll as player moves within a block.
        double centerBlockX = (miniMapRenderCache != null ? miniMapRenderCache.centerX() : playerFeetX) + 0.5d;
        double centerBlockZ = (miniMapRenderCache != null ? miniMapRenderCache.centerZ() : playerFeetZ) + 0.5d;
        // texScale: how many screen pixels per texture pixel (= per block).
        float texScale = (float) miniMapSize / MINIMAP_TEXTURE_SIZE;
        // Player-up rotation: map rotates so current movement heading points to the top.
        float mapRotationDegrees = resolveMiniMapHeadingRotationDegrees(client);
        // Sub-block offsets in screen pixels — keeps map scrolling smooth between block positions.
        float subBlockShiftX = (float) (playerWorldX - centerBlockX) * texScale;
        float subBlockShiftZ = (float) (playerWorldZ - centerBlockZ) * texScale;
        // Player icon points to facing direction relative to the rotated map.
        float playerFacingOnMapDegrees = normalizeDegrees((180.0f - client.player.getYaw()) - mapRotationDegrees - 90.0f);
        // With exact player-centered map shift, keep marker fixed at the card center.
        int playerPx = mapCenterX;
        int playerPz = mapCenterY;
        float mapRadius = (miniMapSize / 2.0f) - 1.0f;
        drawFilledCircle(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha((surfaceAlpha << 24) | 0x121212, hudAlpha));

        // Always paint a live biome fallback first so the card is never plain gray
        // if the dynamic texture fails to render during join/reconnect timing.
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
            // Rotate around map center for player-up orientation, then scale 1px/block texture to UI size.
            matrices.translate(mapCenterX, mapCenterY, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mapRotationDegrees));
            matrices.translate((-miniMapSize / 2.0f) - subBlockShiftX, (-miniMapSize / 2.0f) - subBlockShiftZ, 0);
            matrices.scale(texScale, texScale, 1.0f);
            drawContext.drawTexture(miniMapRenderCache.textureId(), 0, 0, 0, 0, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE);
            matrices.pop();

            MiniMapState state = miniMapState;
            if (state != null && (System.currentTimeMillis() - miniMapReceivedAtMs) <= MINIMAP_STALE_TIMEOUT_MS) {
                drawMiniMapStrictHazardOverlay(
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

            drawWaypoints(drawContext, client, mapCenterX, mapCenterY, playerWorldX, playerWorldZ, mapScale, mapRotationDegrees, hudAlpha, waypointLabelsVisible, mapCenterX, mapCenterY, mapRadius);

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

            drawHeadingTriangleClipped(
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
                    // Keep lie visibility even when the player arrow sits on top of the same center pixel.
                    drawCircleBandClipped(
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
            drawCircleOutline(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha(HUD_CARD_BORDER, hudAlpha));
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

            drawWaypoints(drawContext, client, mapCenterX, mapCenterY, playerWorldX, playerWorldZ, mapScale, mapRotationDegrees, hudAlpha, waypointLabelsVisible, mapCenterX, mapCenterY, mapRadius);

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

            drawHeadingTriangleClipped(
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

            drawCircleBandClipped(
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

            drawCircleOutline(drawContext, mapCenterX, mapCenterY, mapRadius, HudUtil.withAlpha(HUD_CARD_BORDER, hudAlpha));
            drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, mapRotationDegrees, hudAlpha);
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

                int color = miniMapBiomeFallbackColor(client.world, worldX, worldZ);
                if (client.world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    color = applyVisibleSurfaceShading(client.world, worldX, worldZ, color);
                }

                drawContext.fill(mapX + px, mapY + py, mapX + px + 1, mapY + py + 1, HudUtil.withAlpha(color, hudAlpha));
            }
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
            float mapRotationDegrees,
            float hudAlpha,
            boolean drawLabels,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        for (ClientWaypoint waypoint : resolveVisibleWaypoints()) {
            double distanceBlocks = Math.hypot(waypoint.x() - centerWorldX, waypoint.z() - centerWorldZ);
            if (distanceBlocks > WAYPOINT_EDGE_ARROW_MAX_BLOCKS) {
                continue;
            }
            WaypointRenderMode mode = resolveWaypointRenderMode(waypoint, distanceBlocks);
            float waypointDx = (float) ((waypoint.x() - centerWorldX) * mapScale);
            float waypointDz = (float) ((waypoint.z() - centerWorldZ) * mapScale);
            float[] rotated = rotateMiniMapVector(waypointDx, waypointDz, mapRotationDegrees);

            if (mode == WaypointRenderMode.MINIMAP_EDGE_ARROW) {
                float len = (float) Math.sqrt((rotated[0] * rotated[0]) + (rotated[1] * rotated[1]));
                if (len < 0.001f) {
                    continue;
                }

                float arrowDistance = Math.max(8.0f, clipRadius - 6.0f);
                float arrowX = mapCenterX + ((rotated[0] / len) * arrowDistance);
                float arrowY = mapCenterY + ((rotated[1] / len) * arrowDistance);
                float angle = (float) Math.toDegrees(Math.atan2(rotated[1], rotated[0]));

                drawHeadingTriangleClipped(
                        drawContext,
                        arrowX,
                        arrowY,
                        angle,
                        7.0f,
                        4.5f,
                        HudUtil.withAlpha(waypoint.color(), hudAlpha),
                        HudUtil.withAlpha(0xFF0D1117, hudAlpha),
                        clipCenterX,
                        clipCenterY,
                        clipRadius
                );
                continue;
            }

            int waypointPx = mapCenterX + Math.round(rotated[0]);
            int waypointPz = mapCenterY + Math.round(rotated[1]);
            drawMiniMapIconClipped(drawContext, MINIMAP_BREADCRUMB_TEXTURE, waypointPx, waypointPz, 7, clipCenterX, clipCenterY, clipRadius);
            if (drawLabels && isPointInsideCircle(waypointPx + 4, waypointPz - 6, clipCenterX, clipCenterY, clipRadius * clipRadius)) {
                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(waypoint.name()), waypointPx + 3, waypointPz - 8, HudUtil.withAlpha(0xE8EEF7, hudAlpha));
            }
        }
    }

    private static WaypointRenderMode resolveWaypointRenderMode(ClientWaypoint waypoint, double distanceBlocks) {
        String key = waypointRenderModeKey(waypoint);
        WaypointRenderMode previous = waypointRenderModes.getOrDefault(key, WaypointRenderMode.FLOATING_LABEL);
        WaypointRenderMode resolved;
        if (distanceBlocks <= WAYPOINT_FLOAT_LABEL_ENTER_BLOCKS) {
            resolved = WaypointRenderMode.FLOATING_LABEL;
        } else if (distanceBlocks >= WAYPOINT_EDGE_ARROW_ENTER_BLOCKS && distanceBlocks <= WAYPOINT_EDGE_ARROW_MAX_BLOCKS) {
            resolved = WaypointRenderMode.MINIMAP_EDGE_ARROW;
        } else {
            resolved = previous;
        }

        waypointRenderModes.put(key, resolved);
        return resolved;
    }

    private static String waypointRenderModeKey(ClientWaypoint waypoint) {
        return waypoint.dimensionId() + ":" + waypoint.name() + "@" + waypoint.x() + ":" + waypoint.z() + ":" + Integer.toHexString(waypoint.color());
    }

    private static List<ClientWaypoint> resolveVisibleWaypoints() {
        List<ClientWaypoint> currentDimensionWaypoints = resolveSavedWaypointsForCurrentDimension(MinecraftClient.getInstance());
        List<ClientWaypoint> visible = new ArrayList<>();
        if (isRoundWaypointModeActive()) {
            if (!activeRoundCourseWaypointName.isBlank()) {
                for (ClientWaypoint waypoint : currentDimensionWaypoints) {
                    if (waypoint.name().equals(activeRoundCourseWaypointName)) {
                        visible.add(waypoint);
                        break;
                    }
                }
            }
            visible.addAll(roundHoleWaypoints);
            return visible;
        }

        visible.addAll(currentDimensionWaypoints);
        return visible;
    }

    public static boolean isRoundWaypointModeActive() {
        if (runningRoundScoreState != null) {
            return true;
        }
        if (miniMapState == null) {
            return false;
        }
        return (System.currentTimeMillis() - miniMapReceivedAtMs) <= MINIMAP_STALE_TIMEOUT_MS;
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

    private static float resolveMiniMapHeadingRotationDegrees(MinecraftClient client) {
        if (client.player == null) {
            return 0.0f;
        }

        // Lock map orientation to look heading for stable cardinals and no movement-lag drift.
        float lookHeading = normalizeDegrees(180.0f - client.player.getYaw());
        return lookHeading;
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

    private static void drawMiniMapStrictHazardOverlay(
            DrawContext drawContext,
            MinecraftClient client,
            MiniMapState state,
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
        if (client.world == null) {
            return;
        }

        ClientWorld world = client.world;
        int overlayColor = HudUtil.withAlpha(HAZARD_OVERLAY_ARGB, hudAlpha);
        int sampleStep = Math.max(2, HAZARD_SAMPLE_STEP_PX);
        float clipRadiusSq = clipRadius * clipRadius;
        int minY = Math.round(mapCenterY - clipRadius);
        int maxY = Math.round(mapCenterY + clipRadius);
        int minX = Math.round(mapCenterX - clipRadius);
        int maxX = Math.round(mapCenterX + clipRadius);

        BlockPos tee = new BlockPos(state.teeX(), 0, state.teeZ());
        BlockPos basket = new BlockPos(state.basketX(), 0, state.basketZ());
        BlockPos basketSurface = basket.down();
        StrictSurfacePresetClient preset = strictPresetFromOrdinal(state.strictSurfacePresetOrdinal());

        for (int py = minY; py <= maxY; py += sampleStep) {
            for (int px = minX; px <= maxX; px += sampleStep) {
                if (!isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                    continue;
                }

                float screenDx = px - mapCenterX;
                float screenDz = py - mapCenterY;
                float[] worldOffsetScaled = rotateMiniMapVector(screenDx, screenDz, -mapRotationDegrees);
                double worldX = centerWorldX + (worldOffsetScaled[0] / mapScale);
                double worldZ = centerWorldZ + (worldOffsetScaled[1] / mapScale);
                int blockX = net.minecraft.util.math.MathHelper.floor(worldX);
                int blockZ = net.minecraft.util.math.MathHelper.floor(worldZ);
                int feetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
                BlockPos feet = new BlockPos(blockX, feetY, blockZ);

                if (!isHazardPenaltyAt(world, feet, tee, basket, basketSurface, state.corridorHalfWidth(), preset)) {
                    continue;
                }

                fillRectClipped(drawContext, px, py, sampleStep, sampleStep, overlayColor, clipCenterX, clipCenterY, clipRadiusSq);
            }
        }
    }

    private static boolean isHazardPenaltyAt(
            ClientWorld world,
            BlockPos feet,
            BlockPos tee,
            BlockPos basket,
            BlockPos basketSurface,
            int corridorHalfWidth,
            StrictSurfacePresetClient preset
    ) {
        // OB logic first: we only draw hazard, not OB.
        if (isFluidPenaltyZoneClient(world, feet)) {
            return false;
        }

        if (distanceFromPointToSegmentXZ(feet, tee, basket) > corridorHalfWidth) {
            return false;
        }

        if (isBasketGreenSafeClient(feet, basketSurface)) {
            return false;
        }

        boolean slopeHazard = preset != StrictSurfacePresetClient.FAST
                && isSteepSlopeHazardClient(world, feet, preset == StrictSurfacePresetClient.TOURNAMENT ? 3 : 4);
        if (slopeHazard) {
            return true;
        }

        return preset == StrictSurfacePresetClient.TOURNAMENT
                && isDenseRoughHazardClient(world, feet, 11);
    }

    private static StrictSurfacePresetClient strictPresetFromOrdinal(int ordinal) {
        return switch (ordinal) {
            case 0 -> StrictSurfacePresetClient.FAST;
            case 2 -> StrictSurfacePresetClient.TOURNAMENT;
            default -> StrictSurfacePresetClient.BALANCED;
        };
    }

    private static boolean isBasketGreenSafeClient(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }

    private static boolean isFluidPenaltyZoneClient(ClientWorld world, BlockPos feet) {
        return world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA);
    }

    private static boolean isSteepSlopeHazardClient(ClientWorld world, BlockPos feet, int slopeDeltaThreshold) {
        int centerY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        int[] offsets = { -2, 0, 2 };
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz) - 1;
                if (Math.abs(sampleY - centerY) >= slopeDeltaThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDenseRoughHazardClient(ClientWorld world, BlockPos feet, int threshold) {
        int roughHits = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surface = new BlockPos(x, topY, z);
                BlockState surfaceState = world.getBlockState(surface);
                BlockState headState = world.getBlockState(surface.up());
                if (isRoughMaterialClient(surfaceState) || isRoughMaterialClient(headState)) {
                    roughHits++;
                }
            }
        }
        return roughHits >= threshold;
    }

    private static boolean isRoughMaterialClient(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CACTUS);
    }

    private static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        double closestX = sx + (t * dx);
        double closestZ = sz + (t * dz);
        double mx = px - closestX;
        double mz = pz - closestZ;
        return Math.sqrt(mx * mx + mz * mz);
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

    private enum StrictSurfacePresetClient {
        FAST,
        BALANCED,
        TOURNAMENT
    }

    private static void drawMiniMapHoleGuides(
            DrawContext drawContext,
            MiniMapState state,
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

        drawCircleBandClipped(drawContext, basketPx, basketPy, ring200RadiusPx, 1, ring200Color, clipCenterX, clipCenterY, clipRadius);
        drawCircleBandClipped(drawContext, basketPx, basketPy, ring100RadiusPx, 1, ring100Color, clipCenterX, clipCenterY, clipRadius);

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
            drawPixelClipped(drawContext, centerX, y, poleColor, clipCenterX, clipCenterY, clipRadius);
        }

        drawPixelClipped(drawContext, centerX + 1, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 2, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 3, centerY - 5, outlineColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 1, centerY - 4, outlineColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 2, centerY - 4, outlineColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 1, centerY - 3, outlineColor, clipCenterX, clipCenterY, clipRadius);

        drawPixelClipped(drawContext, centerX + 1, centerY - 5, flagColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 2, centerY - 5, flagColor, clipCenterX, clipCenterY, clipRadius);
        drawPixelClipped(drawContext, centerX + 1, centerY - 4, flagColor, clipCenterX, clipCenterY, clipRadius);
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
        if (size <= 0 || !isPointInsideCircle(centerX, centerY, clipCenterX, clipCenterY, clipRadius * clipRadius)) {
            return;
        }

        int half = size / 2;
        drawContext.drawTexture(texture, centerX - half, centerY - half, 0, 0, size, size, size, size);
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

    private static void drawMiniMapCardinalLabels(DrawContext drawContext, MinecraftClient client, float centerX, float centerY, int miniMapSize, float mapRotationDegrees, float hudAlpha) {
        // Keep labels inside the minimap square/mask while still orbiting with heading.
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


    public static int scaleColor(int argb, float multiplier) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, Math.min(255, Math.round(((argb >>> 16) & 0xFF) * multiplier)));
        int g = Math.max(0, Math.min(255, Math.round(((argb >>> 8) & 0xFF) * multiplier)));
        int b = Math.max(0, Math.min(255, Math.round((argb & 0xFF) * multiplier)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float hudFadeAlpha() {
        if (hudVisibleSinceMs <= 0L) {
            return 1.0f;
        }
        long elapsed = System.currentTimeMillis() - hudVisibleSinceMs;
        return Math.max(0.0f, Math.min(1.0f, elapsed / 180.0f));
    }

    public static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static void handleMiniMapHotkeys(MinecraftClient client) {
        ensureWaypointContextLoaded(client);

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

        ClientKeybinds.forEachAddWaypointPress(() -> {
            if (client.player == null) {
                return;
            }
            beginWaypointPrompt(client);
        });

        ClientKeybinds.forEachRemoveNearestWaypointPress(() -> {
            if (client.player == null) {
                return;
            }
            List<ClientWaypoint> currentDimensionWaypoints = resolveSavedWaypointsForCurrentDimension(client);
            if (currentDimensionWaypoints.isEmpty()) {
                client.player.sendMessage(Text.literal("No waypoints to remove.").formatted(Formatting.GRAY), true);
                return;
            }
            int x = net.minecraft.util.math.MathHelper.floor(client.player.getX());
            int z = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
            ClientWaypoint nearest = null;
            int nearestDistSq = Integer.MAX_VALUE;
            for (ClientWaypoint waypoint : currentDimensionWaypoints) {
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
                waypointsDirty = true;
                client.player.sendMessage(Text.literal("Waypoint removed: " + nearest.name()).formatted(Formatting.GRAY), true);
            }
        });

        ClientKeybinds.forEachToggleWaypointLabelsPress(() -> {
            waypointLabelsVisible = !waypointLabelsVisible;
            saveWaypointStore(client);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Waypoint labels " + (waypointLabelsVisible ? "ON" : "OFF")).formatted(Formatting.GRAY), true);
            }
        });
    }

    private static void beginWaypointPrompt(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        ensureWaypointContextLoaded(client);
        pendingWaypointX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        pendingWaypointY = net.minecraft.util.math.MathHelper.floor(client.player.getY());
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
        clientWaypoints.add(new ClientWaypoint(name, pendingWaypointX, pendingWaypointY, pendingWaypointZ, color, currentWaypointDimensionKey(client)));
        saveWaypointStore(client);
        waypointsDirty = true;
        client.player.sendMessage(Text.literal("Waypoint added: " + name + " (" + pendingWaypointX + ", " + pendingWaypointY + ", " + pendingWaypointZ + ") " + WAYPOINT_COLOR_NAMES[colorIndex]).formatted(Formatting.LIGHT_PURPLE), false);

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

    private static String currentWaypointDimensionKey(MinecraftClient client) {
        if (client == null || client.world == null) {
            return "";
        }

        return client.world.getRegistryKey().getValue().toString();
    }

    private static List<ClientWaypoint> resolveSavedWaypointsForCurrentDimension(MinecraftClient client) {
        String dimensionKey = currentWaypointDimensionKey(client);
        if (dimensionKey.isBlank()) {
            return List.of();
        }

        List<ClientWaypoint> visible = new ArrayList<>();
        for (ClientWaypoint waypoint : clientWaypoints) {
            if (dimensionKey.equals(waypoint.dimensionId())) {
                visible.add(waypoint);
            }
        }
        return visible;
    }

    private static void maybeSyncClientWaypoints(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        ensureWaypointContextLoaded(client);
        syncClientWaypointsToServer(client);
    }

    private static void syncClientWaypointsToServer(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.getNetworkHandler() == null) {
            return;
        }

        String signature = buildWaypointSyncSignature();
        if (signature.equals(lastSentWaypointSyncSignature)) {
            return;
        }

        List<WaypointSync.WaypointEntry> entries = new ArrayList<>(clientWaypoints.size());
        for (ClientWaypoint waypoint : clientWaypoints) {
            entries.add(new WaypointSync.WaypointEntry(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color(), waypoint.dimensionId()));
        }

        ClientPlayNetworking.send(new WaypointSync.Payload(entries));
        lastSentWaypointSyncSignature = signature;
    }

    private static String buildWaypointSyncSignature() {
        StringBuilder builder = new StringBuilder();
        for (ClientWaypoint waypoint : clientWaypoints) {
            builder.append(waypoint.name())
                    .append('|')
                    .append(waypoint.x())
                    .append('|')
                    .append(waypoint.y())
                    .append('|')
                    .append(waypoint.z())
                    .append('|')
                    .append(waypoint.color())
                    .append('|')
                    .append(waypoint.dimensionId())
                    .append(';');
        }
        return builder.toString();
    }

    private static Path waypointStorePath(MinecraftClient client) {
        return client.runDirectory.toPath()
                .resolve("config")
                .resolve("mcdg-waypoints")
                .resolve(loadedWaypointContextKey + ".txt");
    }

    private static void loadWaypointStore(MinecraftClient client) {
        clientWaypoints.clear();
        waypointRenderModes.clear();
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
                if (parts.length != 4 && parts.length != 5 && parts.length != 6) {
                    continue;
                }

                String name = parts[0].replace("\\n", " ").replace("\\t", " ").trim();
                int x = Integer.parseInt(parts[1]);
                int y;
                int z;
                int color;
                String dimensionId;

                if (parts.length >= 6) {
                    y = Integer.parseInt(parts[2]);
                    z = Integer.parseInt(parts[3]);
                    color = (int) Long.parseLong(parts[4], 16);
                    dimensionId = parts[5].trim();
                } else {
                    y = UNKNOWN_WAYPOINT_Y;
                    z = Integer.parseInt(parts[2]);
                    color = (int) Long.parseLong(parts[3], 16);
                    dimensionId = parts.length >= 5 ? parts[4].trim() : currentWaypointDimensionKey(client);
                }
                if (!name.isEmpty()) {
                    clientWaypoints.add(new ClientWaypoint(StringHelper.truncate(name, 24, false), x, y, z, color, dimensionId));
                }
            }
        } catch (IOException | NumberFormatException ex) {
            LOGGER.warn("Unable to load waypoint store for context {}", loadedWaypointContextKey, ex);
            clientWaypoints.clear();
            nextWaypointIndex = 1;
            waypointLabelsVisible = true;
        }

        waypointsDirty = true;
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
                lines.add("wp=" + safeName + "\t" + waypoint.x() + "\t" + waypoint.y() + "\t" + waypoint.z() + "\t" + String.format("%08X", waypoint.color()) + "\t" + waypoint.dimensionId());
            }
            Files.write(storePath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            LOGGER.warn("Unable to save waypoint store for context {}", loadedWaypointContextKey, ex);
        }

        syncClientWaypointsToServer(client);
    }

    private static void upsertPermanentCourseWaypoint(MinecraftClient client, String name, int x, int z) {
        if (client == null || name == null || name.isBlank()) {
            return;
        }

        ensureWaypointContextLoaded(client);
        String dimensionKey = currentWaypointDimensionKey(client);

        for (int i = 0; i < clientWaypoints.size(); i++) {
            ClientWaypoint existing = clientWaypoints.get(i);
            if (!existing.name().equals(name) || !dimensionKey.equals(existing.dimensionId())) {
                continue;
            }

            if (existing.x() == x && existing.z() == z && existing.color() == WAYPOINT_COURSE_COLOR) {
                return;
            }

            clientWaypoints.set(i, new ClientWaypoint(name, x, existing.y(), z, WAYPOINT_COURSE_COLOR, dimensionKey));
            saveWaypointStore(client);
            waypointsDirty = true;
            return;
        }

        clientWaypoints.add(new ClientWaypoint(StringHelper.truncate(name, 24, false), x, UNKNOWN_WAYPOINT_Y, z, WAYPOINT_COURSE_COLOR, dimensionKey));
        saveWaypointStore(client);
        waypointsDirty = true;
    }

    private static void removePermanentCourseWaypoint(MinecraftClient client, String name) {
        if (client == null || name == null || name.isBlank()) {
            return;
        }
        ensureWaypointContextLoaded(client);
        String dimensionKey = currentWaypointDimensionKey(client);
        clientWaypoints.removeIf(wp -> wp.name().equals(name) && wp.dimensionId().equals(dimensionKey) && wp.color() == WAYPOINT_COURSE_COLOR);
        saveWaypointStore(client);
        waypointsDirty = true;
    }

    private static void syncRoundHoleWaypointsFromPayload(HoleMiniMapSync.Payload payload) {
        roundHoleWaypoints.clear();
        if (payload == null || payload.totalHoles() <= 0) {
            return;
        }

        int count = Math.min(payload.totalHoles(), Math.min(payload.holeTeeXs().size(), payload.holeTeeZs().size()));
        for (int hole = 1; hole <= count; hole++) {
            int idx = hole - 1;
            roundHoleWaypoints.add(new ClientWaypoint("Hole " + hole, payload.holeTeeXs().get(idx), UNKNOWN_WAYPOINT_Y, payload.holeTeeZs().get(idx), WAYPOINT_HOLE_TEMP_COLOR, currentWaypointDimensionKey(MinecraftClient.getInstance())));
        }
    }

    private static void renderWaypointWorldLabels(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.options.hudHidden || client.textRenderer == null) {
            return;
        }

        List<ClientWaypoint> visibleWaypoints = resolveVisibleWaypoints();
        if (visibleWaypoints.isEmpty()) {
            return;
        }

        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer beamConsumer = consumers.getBuffer(RenderLayer.getLines());

        for (ClientWaypoint waypoint : visibleWaypoints) {
            double distanceBlocks = Math.hypot(waypoint.x() - client.player.getX(), waypoint.z() - client.player.getZ());
            WaypointRenderMode mode = resolveWaypointRenderMode(waypoint, distanceBlocks);
            if (mode != WaypointRenderMode.FLOATING_LABEL || distanceBlocks > WAYPOINT_FLOAT_LABEL_MAX_BLOCKS) {
                continue;
            }

            int surfaceY = client.world.getTopY(Heightmap.Type.WORLD_SURFACE, waypoint.x(), waypoint.z());
            if (isWaypointBeamVisible(client, cameraPos, waypoint, surfaceY)) {
                drawWaypointBeamColumn(context, beamConsumer, cameraPos, waypoint, surfaceY);
            }

            double wx = waypoint.x() + 0.5d;
            double wy = surfaceY + 2.3d;
            double wz = waypoint.z() + 0.5d;

            context.matrixStack().push();
            context.matrixStack().translate(wx - cameraPos.x, wy - cameraPos.y, wz - cameraPos.z);
            context.matrixStack().multiply(client.getEntityRenderDispatcher().getRotation());
            context.matrixStack().scale(-0.025f, -0.025f, 0.025f);

            String label = waypoint.name() + " \u2022 " + Math.round(distanceBlocks) + "b";
            float textX = (-client.textRenderer.getWidth(label)) / 2.0f;

            client.textRenderer.draw(
                    label,
                    textX,
                    0.0f,
                    waypoint.color() | 0xFF000000,
                    false,
                    context.matrixStack().peek().getPositionMatrix(),
                    consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH,
                    0,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            context.matrixStack().pop();
        }

        consumers.draw();
    }

    private static boolean isWaypointBeamVisible(MinecraftClient client, Vec3d cameraPos, ClientWaypoint waypoint, int surfaceY) {
        if (client == null || client.world == null || client.player == null) {
            return false;
        }

        Vec3d target = new Vec3d(waypoint.x() + 0.5d, surfaceY + 1.0d, waypoint.z() + 0.5d);
        HitResult hit = client.world.raycast(new RaycastContext(
                cameraPos,
                target,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit.getType() == HitResult.Type.MISS || hit.getPos().squaredDistanceTo(target) <= 1.25d;
    }

    private static void drawWaypointBeamColumn(
            WorldRenderContext context,
            VertexConsumer beamConsumer,
            Vec3d cameraPos,
            ClientWaypoint waypoint,
            int surfaceY
    ) {
        float r = ((waypoint.color() >> 16) & 0xFF) / 255.0f;
        float g = ((waypoint.color() >> 8) & 0xFF) / 255.0f;
        float b = (waypoint.color() & 0xFF) / 255.0f;

        double minX = (waypoint.x() + 0.30d) - cameraPos.x;
        double maxX = (waypoint.x() + 0.70d) - cameraPos.x;
        double minY = (surfaceY + 0.05d) - cameraPos.y;
        double maxY = (surfaceY + WAYPOINT_BEAM_HEIGHT_BLOCKS) - cameraPos.y;
        double minZ = (waypoint.z() + 0.30d) - cameraPos.z;
        double maxZ = (waypoint.z() + 0.70d) - cameraPos.z;

        WorldRenderer.drawBox(
                context.matrixStack(),
                beamConsumer,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                r,
                g,
                b,
                WAYPOINT_BEAM_ALPHA
        );
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

        // Subtle outline first so the marker stays readable over light terrain.
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

    public static boolean isPointInsideCircle(int x, int y, float centerX, float centerY, float radiusSq) {
        float dx = x - centerX;
        float dy = y - centerY;
        return ((dx * dx) + (dy * dy)) <= radiusSq;
    }

    public static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }

    private static void refreshMiniMapRenderCache(MinecraftClient client, int mapSpan) {
        if (client == null || mapSpan <= 0) {
            return;
        }

        int playerFeetX = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int playerFeetZ = net.minecraft.util.math.MathHelper.floor(client.player.getZ());

        if (miniMapRenderCache != null && miniMapRenderCache.matches(mapSpan, playerFeetX, playerFeetZ)) {
            if (!miniMapJoinWarmupPending) {
                // If current texture still has unresolved surface pixels, retry periodically without requiring player movement.
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

    private static int resolveActiveMiniMapSpan(MinecraftClient client) {
        int baseSpan = PASSIVE_MINIMAP_SPAN_BLOCKS;
        MiniMapState state = miniMapState;
        if (client == null || client.player == null || state == null) {
            return baseSpan;
        }

        double playerToBasket = Math.sqrt(
                ((state.basketX() - client.player.getX()) * (state.basketX() - client.player.getX()))
                        + ((state.basketZ() - client.player.getZ()) * (state.basketZ() - client.player.getZ()))
        );

        // Center on player; show basket plus a 20-block buffer for OB/hazards, but no more.
        double halfSpan = playerToBasket + 20.0d;
        int dynamicSpan = (int) Math.ceil(halfSpan * 2.0d);
        int payloadSpan = Math.max(0, state.mapSpan());
        int resolved = Math.max(baseSpan, Math.max(dynamicSpan, payloadSpan));
        return Math.max(64, Math.min(256, resolved));
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
                    image.setColor(px, py, argbToAbgr(0x00000000));
                    continue;
                }

                float dx = (px - centerPx) / texDenominator;
                int worldX = net.minecraft.util.math.MathHelper.floor(centerWorldX + (dx * mapSpan));

                TerrainSampleResult terrainSample = sampleClientWorldTerrain(client.world, worldX, worldZ);
                boolean usedClientSample = terrainSample.color() != MINIMAP_COLOR_UNSET;
                int baseColor = terrainSample.color();
                if (terrainSample.source() == MiniMapSampleSource.CHUNK_UNLOADED) {
                    chunkUnloadedSourcePixels++;
                }
                if (terrainSample.source() != MiniMapSampleSource.VISIBLE_SURFACE) {
                    unresolvedSurfacePixels++;
                }
                if (!usedClientSample) {
                    // Avoid a gray flash on join; use biome-derived fallback when terrain is not yet available.
                    baseColor = miniMapBiomeFallbackColor(client.world, worldX, worldZ);
                }

                int shadedArgb = applyVisibleSurfaceShading(client.world, worldX, worldZ, baseColor);
                image.setColor(px, py, argbToAbgr(shadedArgb));
            }
        }

        miniMapRenderDebug = new MiniMapRenderDebug(chunkUnloadedSourcePixels, unresolvedSurfacePixels);

        return true;
    }

    private static TerrainSampleResult sampleClientWorldTerrain(ClientWorld world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            int fallback = miniMapBiomeFallbackColor(world, x, z);
            return new TerrainSampleResult(
                    fallback,
                    false,
                    MiniMapSampleSource.CHUNK_UNLOADED,
                    MiniMapFluidKind.NONE,
                    worldBottom(world)
            );
        }

        int topSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int startY = topSurfaceY;
        if (startY < world.getBottomY()) {
            int fallback = miniMapBiomeFallbackColor(world, x, z);
            return new TerrainSampleResult(
                fallback,
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

    private static int miniMapBiomeFallbackColor(ClientWorld world, int x, int z) {
        String biomeId = biomeId(world.getBiome(new BlockPos(x, world.getSeaLevel(), z)));
        if (biomeId.contains("ocean") || biomeId.contains("river") || biomeId.contains("beach") || biomeId.contains("shore")) {
            return 0xFF3F76E4;
        }
        if (biomeId.contains("desert") || biomeId.contains("badlands") || biomeId.contains("savanna")) {
            return 0xFFD7BF7A;
        }
        if (biomeId.contains("snow") || biomeId.contains("frozen") || biomeId.contains("ice")) {
            return 0xFFE9F2FF;
        }
        if (biomeId.contains("jungle") || biomeId.contains("forest") || biomeId.contains("taiga") || biomeId.contains("grove")) {
            return 0xFF5EA54A;
        }
        return 0xFF7FB238;
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "unknown";
        }
        return key.getValue().getPath();
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
        if (baseColor == MINIMAP_COLOR_UNSET) {
            return 0xFF5E6F86;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            // Keep biome fallback colors for unloaded chunks to avoid gray flashes while chunks stream in.
            return baseColor;
        }

        int currentY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        int northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
        int southY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z + 1) - 1;
        int westY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z) - 1;
        int eastY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x + 1, z) - 1;

        // Hillshade: directional light from NW to make local relief easier to read.
        int litDelta = (northY + westY) - (southY + eastY);
        float shade = 1.0f + Math.max(-0.30f, Math.min(0.30f, litDelta * 0.08f));

        // Relief contrast: darken concave areas and brighten convex ridges.
        int neighborAvg = (northY + southY + eastY + westY) / 4;
        int localRelief = currentY - neighborAvg;
        shade += Math.max(-0.14f, Math.min(0.14f, localRelief * 0.07f));

        // Contour accent: subtle darkening on height bands improves macro topology readability.
        if (Math.floorMod(currentY, 4) == 0) {
            shade *= 0.93f;
        }
        if (Math.floorMod(currentY, 2) == 0) {
            shade *= 0.97f;
        }

        // Add a small emphasis where the local slope is steeper.
        int slopeStrength = Math.abs(eastY - westY) + Math.abs(southY - northY);
        if (slopeStrength >= 4) {
            shade *= 0.92f;
        }

        if (slopeStrength >= 8) {
            shade *= 0.88f;
        }

        if (Math.abs(localRelief) >= 2) {
            shade *= 0.94f;
        }

        shade = Math.max(0.65f, Math.min(1.35f, shade));
        return scaleColor(baseColor, shade);
    }

    private enum MiniMapSampleSource {
        VISIBLE_SURFACE,
        HEIGHTMAP_FALLBACK,
        CHUNK_UNLOADED
    }

    private enum MiniMapFluidKind {
        NONE,
        WATER,
        LAVA
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

    private record MiniMapRenderDebug(int chunkUnloadedSourcePixels, int unresolvedSurfacePixels) {
        private static MiniMapRenderDebug empty() {
            return new MiniMapRenderDebug(0, 0);
        }

        private static MiniMapRenderDebug serverOnly() {
            int allPixels = MINIMAP_TEXTURE_SIZE * MINIMAP_TEXTURE_SIZE;
            return new MiniMapRenderDebug(allPixels, allPixels);
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

    private enum WaypointPromptStage {
        NONE,
        WAITING_NAME,
        WAITING_COLOR
    }

    private enum WaypointRenderMode {
        FLOATING_LABEL,
        MINIMAP_EDGE_ARROW
    }

    public record MiniMapState(
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
            int strictSurfacePresetOrdinal,
            int corridorHalfWidth,
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

    public record RunningRoundScoreState(
            int totalHoles,
            int focusHole,
            String courseName,
            List<RunningRoundScoreRow> rows
    ) {
    }

    public record RunningRoundScoreRow(
            String playerName,
            boolean online,
            List<Integer> holeScores,
            int runningTotal
    ) {
    }

    public static void onHoleMiniMapSync(HoleMiniMapSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            String courseToRemove = activeRoundCourseWaypointName;
            miniMapState = null;
            miniMapReceivedAtMs = 0L;
            activeRoundCourseWaypointName = "";
            roundHoleWaypoints.clear();
            removePermanentCourseWaypoint(client, courseToRemove);
            hudVisibleSinceMs = 0L;
            miniMapJoinWarmupPending = true;
            miniMapJoinPrimeTicksRemaining = MINIMAP_JOIN_PRIME_TICKS;
            lastMiniMapRenderAtMs = 0L;
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
                payload.strictSurfacePresetOrdinal(),
                payload.corridorHalfWidth(),
                payload.hasAlternateAnchor(),
                payload.alternateAnchorX(),
                payload.alternateAnchorZ(),
                payload.mapSpan()
        );
        activeRoundCourseWaypointName = payload.courseWaypointName();
        upsertPermanentCourseWaypoint(
                client,
                payload.courseWaypointName(),
                payload.courseWaypointX(),
                payload.courseWaypointZ()
        );
        syncRoundHoleWaypointsFromPayload(payload);
        miniMapReceivedAtMs = System.currentTimeMillis();
        refreshMiniMapRenderCache(client, PASSIVE_MINIMAP_SPAN_BLOCKS);
    }

    public static void onRoundRunningScoresSync(RoundRunningScoresSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            removePermanentCourseWaypoint(client, activeRoundCourseWaypointName);
            runningRoundScoreState = null;
            activeRoundCourseWaypointName = "";
            roundHoleWaypoints.clear();
            return;
        }

        List<RunningRoundScoreRow> rows = new ArrayList<>();
        for (RoundRunningScoresSync.PlayerRow row : payload.rows()) {
            rows.add(new RunningRoundScoreRow(row.playerName(), row.online(), row.holeScores(), row.runningTotal()));
        }
        runningRoundScoreState = new RunningRoundScoreState(payload.totalHoles(), payload.focusHole(), payload.courseName(), rows);
    }

    private record ClientWaypoint(String name, int x, int y, int z, int color, String dimensionId) {
    }
}
