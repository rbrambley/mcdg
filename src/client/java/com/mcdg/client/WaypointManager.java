package com.mcdg.client;

import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.WaypointSync;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringHelper;
import net.minecraft.util.WorldSavePath;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WaypointManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcdg-waypoints");

    private static final int WAYPOINT_FLOAT_LABEL_ENTER_BLOCKS = 160;
    private static final int WAYPOINT_EDGE_ARROW_ENTER_BLOCKS = 160;
    private static final int WAYPOINT_FLOAT_LABEL_MAX_BLOCKS = 160;
    private static final int WAYPOINT_EDGE_ARROW_MAX_BLOCKS = 800;
    private static final int UNKNOWN_WAYPOINT_Y = Integer.MIN_VALUE;
    private static final int WAYPOINT_COURSE_COLOR = 0xFF66CC66;
    private static final int WAYPOINT_HOLE_TEMP_COLOR = 0xFFFFFFFF;
    private static int nextWaypointIndex = 1;
    private static boolean waypointLabelsVisible = true;
    private static boolean waypointsDirty = false;
    private static String activeRoundCourseWaypointName = "";
    private static String loadedWaypointContextKey = "";
    private static String lastSentWaypointSyncSignature = "";
    private static String loadedWaypointDimensionKey = "";
    private static final List<ClientWaypoint> clientWaypoints = new ArrayList<>();
    private static final List<ClientWaypoint> roundHoleWaypoints = new ArrayList<>();
    private static final Map<String, WaypointRenderMode> waypointRenderModes = new HashMap<>();

    private WaypointManager() {}

    public static void setActiveRoundCourseWaypointName(String name) {
        activeRoundCourseWaypointName = name != null ? name : "";
    }

    public static String getActiveRoundCourseWaypointName() { return activeRoundCourseWaypointName; }
    public static List<ClientWaypoint> getWaypoints() {
        return List.copyOf(clientWaypoints);
    }

    public static void removeWaypoint(String name) {
        clientWaypoints.removeIf(wp -> wp.name().equals(name));
        waypointsDirty = true;
        saveWaypointStore(MinecraftClient.getInstance());
    }


    public static void mergeWaypoints(List<WaypointSync.WaypointEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        for (WaypointSync.WaypointEntry entry : entries) {
            if (entry == null) continue;
            clientWaypoints.removeIf(wp -> wp.name().equals(entry.name()));
            clientWaypoints.add(new ClientWaypoint(entry.name(), entry.x(), entry.y(), entry.z(), entry.color(), entry.dimensionId()));
        }
        waypointsDirty = true;
    }

    public static void clearRoundState() {
        activeRoundCourseWaypointName = "";
        lastSentWaypointSyncSignature = "";
        roundHoleWaypoints.clear();
        waypointRenderModes.clear();
        loadedWaypointDimensionKey = "";
        loadedWaypointContextKey = "";
        clientWaypoints.clear();
    }

    public static void tick(MinecraftClient client) {
        if (waypointsDirty) {
            maybeSyncClientWaypoints(client);
            waypointsDirty = false;
        }
    }

    public static void onClientJoin(MinecraftClient client) {
        ensureWaypointContextLoaded(client);
        syncClientWaypointsToServer(client);
    }

    public static void onClientDisconnect(MinecraftClient client) {
        removeWaypoint(activeRoundCourseWaypointName);
        clearRoundState();
    }

    public static boolean isWaypointLabelsVisible() { return waypointLabelsVisible; }

    public static List<ClientWaypoint> resolveVisibleWaypoints() {
        List<ClientWaypoint> currentDimensionWaypoints = resolveSavedWaypointsForCurrentDimension(MinecraftClient.getInstance());
        List<ClientWaypoint> visible = new ArrayList<>();
        if (McdgClientMod.isRoundWaypointModeActive()) {
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

    private static WaypointRenderMode resolveWaypointRenderMode(ClientWaypoint waypoint, double distanceBlocks) {
        String key = waypointRenderModeKey(waypoint);
        // Hysteresis removed — direct distance thresholds now
        WaypointRenderMode resolved;
        if (distanceBlocks <= WAYPOINT_FLOAT_LABEL_ENTER_BLOCKS) {
            resolved = WaypointRenderMode.FLOATING_LABEL;
        } else if (distanceBlocks <= WAYPOINT_EDGE_ARROW_MAX_BLOCKS) {
            resolved = WaypointRenderMode.MINIMAP_EDGE_ARROW;
        } else {
            resolved = WaypointRenderMode.FLOATING_LABEL;
        }
        waypointRenderModes.put(key, resolved);
        return resolved;
    }

    private static String waypointRenderModeKey(ClientWaypoint waypoint) {
        return waypoint.dimensionId() + ":" + waypoint.name() + "@" + waypoint.x() + ":" + waypoint.z() + ":" + Integer.toHexString(waypoint.color());
    }

    public static void drawWaypointsOnMiniMap(
            DrawContext drawContext, MinecraftClient client,
            int mapCenterX, int mapCenterY,
            double centerWorldX, double centerWorldZ,
            float mapScale, float mapRotationDegrees, float hudAlpha,
            boolean drawLabels, float clipCenterX, float clipCenterY, float clipRadius) {
        for (ClientWaypoint waypoint : resolveVisibleWaypoints()) {
            double distanceBlocks = Math.hypot(waypoint.x() - centerWorldX, waypoint.z() - centerWorldZ);
            if (distanceBlocks > WAYPOINT_EDGE_ARROW_MAX_BLOCKS) continue;
            WaypointRenderMode mode = resolveWaypointRenderMode(waypoint, distanceBlocks);
            float waypointDx = (float) ((waypoint.x() - centerWorldX) * mapScale);
            float waypointDz = (float) ((waypoint.z() - centerWorldZ) * mapScale);
            float[] rotated = MiniMapRenderer.rotateMiniMapVector(waypointDx, waypointDz, mapRotationDegrees);

            if (mode == WaypointRenderMode.MINIMAP_EDGE_ARROW) {
                float len = (float) Math.sqrt((rotated[0] * rotated[0]) + (rotated[1] * rotated[1]));
                if (len < 0.001f) continue;
                float arrowDistance = Math.max(8.0f, clipRadius - 6.0f);
                float arrowX = mapCenterX + ((rotated[0] / len) * arrowDistance);
                float arrowY = mapCenterY + ((rotated[1] / len) * arrowDistance);
                float angle = (float) Math.toDegrees(Math.atan2(rotated[1], rotated[0]));
                MiniMapRenderer.drawHeadingTriangleClipped(drawContext, arrowX, arrowY, angle, 6.0f, 4.0f,
                        HudUtil.withAlpha(waypoint.color(), hudAlpha), HudUtil.withAlpha(0xFF10161F, hudAlpha),
                        clipCenterX, clipCenterY, clipRadius);
                continue;
            }

            float waypointPx = mapCenterX + rotated[0];
            float waypointPz = mapCenterY + rotated[1];
            if (!MiniMapRenderer.isPointInsideCircle((int) waypointPx, (int) waypointPz, clipCenterX, clipCenterY, clipRadius * clipRadius)) continue;
            MiniMapRenderer.drawFilledCircle(drawContext, waypointPx, waypointPz, 3.5f, HudUtil.withAlpha(waypoint.color(), hudAlpha));
            MiniMapRenderer.drawCircleOutline(drawContext, waypointPx, waypointPz, 3.5f, HudUtil.withAlpha(0xFF10161F, hudAlpha));
            if (drawLabels && MiniMapRenderer.isPointInsideCircle((int) (waypointPx + 4), (int) (waypointPz - 6), clipCenterX, clipCenterY, clipRadius * clipRadius)) {
                drawContext.drawTextWithShadow(client.textRenderer, Text.literal(waypoint.name()), (int) waypointPx + 3, (int) waypointPz - 8, HudUtil.withAlpha(0xE8EEF7, hudAlpha));
            }
        }
    }

    public static void handleKeybinds(MinecraftClient client) {
        ensureWaypointContextLoaded(client);
        ClientKeybinds.forEachAddWaypointPress(() -> {
            if (client.player == null) return;
            beginWaypointPrompt(client);
        });
        ClientKeybinds.forEachRemoveNearestWaypointPress(() -> {
            if (client.player == null) return;
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
                if (distSq < nearestDistSq) { nearest = waypoint; nearestDistSq = distSq; }
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
        if (client.player == null) return;
        ensureWaypointContextLoaded(client);
        int x = net.minecraft.util.math.MathHelper.floor(client.player.getX());
        int y = net.minecraft.util.math.MathHelper.floor(client.player.getY());
        int z = net.minecraft.util.math.MathHelper.floor(client.player.getZ());
        String dimensionId = currentWaypointDimensionKey(client);
        client.setScreen(new WaypointAddScreen(x, y, z, dimensionId,
                (name, color, wx, wy, wz, dim) -> {
                    String safeName = StringHelper.truncate(name.isBlank() ? ("WP" + nextWaypointIndex) : name, 24, false);
                    nextWaypointIndex++;
                    clientWaypoints.add(new ClientWaypoint(safeName, wx, wy, wz, color, dim));
                    saveWaypointStore(MinecraftClient.getInstance());
                    waypointsDirty = true;
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("Waypoint added: " + safeName).formatted(Formatting.LIGHT_PURPLE), true);
                    }
                },
                () -> {}
        ));
    }

    public static void ensureWaypointContextLoaded(MinecraftClient client) {
        String contextKey = currentWaypointContextKey(client);
        if (Objects.equals(contextKey, loadedWaypointContextKey)) return;
        loadedWaypointContextKey = contextKey;
        loadWaypointStore(client);
    }

    private static String currentWaypointContextKey(MinecraftClient client) {
        if (client == null) return "menu";
        if (client.getCurrentServerEntry() != null) return "server_" + sanitizeContextSegment(client.getCurrentServerEntry().address);
        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            Path saveRoot = client.getServer().getSavePath(WorldSavePath.ROOT);
            Path fileName = saveRoot.getFileName();
            String saveName = fileName == null ? saveRoot.toString() : fileName.toString();
            return "save_" + sanitizeContextSegment(saveName);
        }
        if (client.world != null) return "world_" + sanitizeContextSegment(client.world.getRegistryKey().getValue().toString());
        return "menu";
    }

    private static String sanitizeContextSegment(String value) {
        if (value == null || value.isBlank()) return "default";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String currentWaypointDimensionKey(MinecraftClient client) {
        if (client == null || client.world == null) return "";
        return client.world.getRegistryKey().getValue().toString();
    }

    private static List<ClientWaypoint> resolveSavedWaypointsForCurrentDimension(MinecraftClient client) {
        String dimensionKey = currentWaypointDimensionKey(client);
        if (dimensionKey.isBlank()) return List.of();
        List<ClientWaypoint> visible = new ArrayList<>();
        for (ClientWaypoint waypoint : clientWaypoints) {
            if (dimensionKey.equals(waypoint.dimensionId())) visible.add(waypoint);
        }
        return visible;
    }

    private static void maybeSyncClientWaypoints(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) return;
        ensureWaypointContextLoaded(client);
        syncClientWaypointsToServer(client);
    }

    private static void syncClientWaypointsToServer(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.getNetworkHandler() == null) return;
        String signature = buildWaypointSyncSignature();
        if (signature.equals(lastSentWaypointSyncSignature)) return;
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
            builder.append(waypoint.name()).append('|').append(waypoint.x()).append('|')
                    .append(waypoint.y()).append('|').append(waypoint.z()).append('|')
                    .append(waypoint.color()).append('|').append(waypoint.dimensionId()).append(';');
        }
        return builder.toString();
    }

    private static Path waypointStorePath(MinecraftClient client) {
        return client.runDirectory.toPath().resolve("config").resolve("mcdg-waypoints").resolve(loadedWaypointContextKey + ".txt");
    }

    public static void loadWaypointStore(MinecraftClient client) {
        clientWaypoints.clear();
        waypointRenderModes.clear();
        nextWaypointIndex = 1;
        waypointLabelsVisible = true;
        if (client == null || loadedWaypointContextKey.isBlank()) return;
        Path storePath = waypointStorePath(client);
        if (!Files.exists(storePath)) return;
        try {
            List<String> lines = Files.readAllLines(storePath, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("nextIndex=")) {
                    nextWaypointIndex = Math.max(1, Integer.parseInt(line.substring("nextIndex=".length()).trim()));
                    continue;
                }
                if (line.startsWith("labelsVisible=")) {
                    waypointLabelsVisible = Boolean.parseBoolean(line.substring("labelsVisible=".length()).trim());
                    continue;
                }
                if (!line.startsWith("wp=")) continue;
                String body = line.substring(3);
                String[] parts = body.split("\\t");
                if (parts.length != 4 && parts.length != 5 && parts.length != 6) continue;
                String name = parts[0].replace("\\n", " ").replace("\\t", " ").trim();
                int x = Integer.parseInt(parts[1]);
                int y, z, color;
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
                // Skip course waypoints on load — they are transient and only valid while a round is
                // active. The server re-sends them via HoleMiniMapSync on join if a round is running.
                // Without this guard, a crash or disconnect mid-round leaves stale green labels in the
                // world indefinitely.
                if (!name.isEmpty() && color != WAYPOINT_COURSE_COLOR) {
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

    public static void saveWaypointStore(MinecraftClient client) {
        if (client == null || loadedWaypointContextKey.isBlank()) return;
        Path storePath = waypointStorePath(client);
        try {
            Files.createDirectories(storePath.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# MCDG waypoint store");
            lines.add("nextIndex=" + nextWaypointIndex);
            lines.add("labelsVisible=" + waypointLabelsVisible);
            for (ClientWaypoint waypoint : clientWaypoints) {
                // Never persist course waypoints — they are transient (re-sent by server on join)
                if (waypoint.color() == WAYPOINT_COURSE_COLOR) continue;
                String safeName = waypoint.name().replace("\t", " ").replace("\n", " ").trim();
                lines.add("wp=" + safeName + "\t" + waypoint.x() + "\t" + waypoint.y() + "\t" + waypoint.z() + "\t" + String.format("%08X", waypoint.color()) + "\t" + waypoint.dimensionId());
            }
            Files.write(storePath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            LOGGER.warn("Unable to save waypoint store for context {}", loadedWaypointContextKey, ex);
        }
        syncClientWaypointsToServer(client);
    }

    public static void upsertPermanentCourseWaypoint(MinecraftClient client, String name, int x, int z) {
        if (client == null || name == null || name.isBlank()) return;
        ensureWaypointContextLoaded(client);
        String dimensionKey = currentWaypointDimensionKey(client);
        for (int i = 0; i < clientWaypoints.size(); i++) {
            ClientWaypoint existing = clientWaypoints.get(i);
            if (!existing.name().equals(name) || !dimensionKey.equals(existing.dimensionId())) continue;
            if (existing.x() == x && existing.z() == z && existing.color() == WAYPOINT_COURSE_COLOR) return;
            clientWaypoints.set(i, new ClientWaypoint(name, x, existing.y(), z, WAYPOINT_COURSE_COLOR, dimensionKey));
            saveWaypointStore(client);
            waypointsDirty = true;
            return;
        }
        clientWaypoints.add(new ClientWaypoint(StringHelper.truncate(name, 24, false), x, UNKNOWN_WAYPOINT_Y, z, WAYPOINT_COURSE_COLOR, dimensionKey));
        saveWaypointStore(client);
        waypointsDirty = true;
    }


    public static void syncRoundHoleWaypointsFromPayload(HoleMiniMapSync.Payload payload) {
        roundHoleWaypoints.clear();
        if (payload == null || payload.totalHoles() <= 0) return;
        int count = Math.min(payload.totalHoles(), Math.min(payload.holeTeeXs().size(), payload.holeTeeZs().size()));
        for (int hole = 1; hole <= count; hole++) {
            int idx = hole - 1;
            roundHoleWaypoints.add(new ClientWaypoint("Hole " + hole, payload.holeTeeXs().get(idx), UNKNOWN_WAYPOINT_Y, payload.holeTeeZs().get(idx), WAYPOINT_HOLE_TEMP_COLOR, currentWaypointDimensionKey(MinecraftClient.getInstance())));
        }
    }

    public static void renderWaypointWorldLabels(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || client.options.hudHidden || client.textRenderer == null) return;
        List<ClientWaypoint> visibleWaypoints = resolveVisibleWaypoints();
        if (visibleWaypoints.isEmpty()) return;
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        for (ClientWaypoint waypoint : visibleWaypoints) {
            double distanceBlocks = Math.hypot(waypoint.x() - client.player.getX(), waypoint.z() - client.player.getZ());
            WaypointRenderMode mode = resolveWaypointRenderMode(waypoint, distanceBlocks);
            if (mode != WaypointRenderMode.FLOATING_LABEL || distanceBlocks > WAYPOINT_FLOAT_LABEL_MAX_BLOCKS) continue;
            int surfaceY = client.world.getTopY(Heightmap.Type.WORLD_SURFACE, waypoint.x(), waypoint.z());
            double wx = waypoint.x() + 0.5d;
            double wy = surfaceY + 2.3d;
            double wz = waypoint.z() + 0.5d;
            context.matrixStack().push();
            context.matrixStack().translate(wx - cameraPos.x, wy - cameraPos.y, wz - cameraPos.z);
            context.matrixStack().multiply(client.getEntityRenderDispatcher().getRotation());
            context.matrixStack().scale(-0.025f, -0.025f, 0.025f);
            String label = waypoint.name() + " \u2022 " + Math.round(distanceBlocks) + "b";
            float textX = (-client.textRenderer.getWidth(label)) / 2.0f;
            client.textRenderer.draw(label, textX, 0.0f, waypoint.color() | 0xFF000000, false,
                    context.matrixStack().peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH,
                    0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            context.matrixStack().pop();
        }
        consumers.draw();
    }


    private enum WaypointRenderMode { FLOATING_LABEL, MINIMAP_EDGE_ARROW }

    public record ClientWaypoint(String name, int x, int y, int z, int color, String dimensionId) {}
}
