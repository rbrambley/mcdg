package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.RoundRunningScoresSync;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;

import net.minecraft.util.Identifier;


public final class McdgClientMod implements ClientModInitializer {

    private static final Identifier TRAINING_DISC_CHARGED_PREDICATE = new Identifier("mcdg", "charged");

    private static RunningRoundScoreState runningRoundScoreState;

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
            WaypointManager.tick(client);
            AutoConnect.tick(client);
            MiniMapRenderer.handleMiniMapHotkeys(client);
            MiniMapRenderer.tickMiniMapJoinPrime(client);
            CinematicOverlay.tick(client);
            RoundInfoOverlay.updateTweens(MiniMapRenderer.getMiniMapState());
        });
        // When a chunk arrives from the server, reset the minimap rebuild timer so the
        // next render frame picks up the newly loaded terrain rather than waiting up to
        // 350 ms.  This fixes the gray minimap seen on initial server join while chunks
        // are still streaming in.
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            MiniMapRenderer.setLastMiniMapRenderAtMs(0L);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            MiniMapRenderer.setMiniMapJoinWarmupPending(true);
            MiniMapRenderer.setMiniMapJoinPrimeTicksRemaining(MiniMapRenderer.MINIMAP_JOIN_PRIME_TICKS);
            MiniMapRenderer.setLastMiniMapRenderAtMs(0L);
            MiniMapRenderer.clearMiniMapRenderCache(client);
            WaypointManager.onClientJoin(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WaypointManager.onClientDisconnect(client);
            MiniMapRenderer.setMiniMapJoinWarmupPending(false);
            MiniMapRenderer.setMiniMapJoinPrimeTicksRemaining(0);
            MiniMapRenderer.setMiniMapState(null);
            MiniMapRenderer.setMiniMapReceivedAtMs(0L);
            MiniMapRenderer.clearMiniMapRenderCache(client);
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> WaypointManager.handleChatInput(message));
        ClientNetworking.registerReceivers();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            RoundInfoOverlay.updateTweens(MiniMapRenderer.getMiniMapState());
            float hudAlpha = MiniMapRenderer.hudFadeAlpha();
            MiniMapRenderer.renderHoleMiniMapOverlay(drawContext);
            RoundInfoOverlay.render(drawContext, MiniMapRenderer.getMiniMapState(), hudAlpha);
            ScorecardOverlay.render(drawContext, MiniMapRenderer.getMiniMapState(), MiniMapRenderer.getMiniMapReceivedAtMs(), hudAlpha);
            RunningScoreboardOverlay.render(drawContext, runningRoundScoreState, hudAlpha);
            HudOverlays.renderCompass(drawContext);
            HudOverlays.renderPower(drawContext);
            CinematicOverlay.render(drawContext);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointManager::renderWaypointWorldLabels);
    }

    public static boolean isRoundWaypointModeActive() {
        return MiniMapRenderer.isRoundWaypointModeActive();
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
            int mapSpan,
            int lastThrowDistanceFeet
    ) {
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
            String courseToRemove = WaypointManager.getActiveRoundCourseWaypointName();
            MiniMapRenderer.setMiniMapState(null);
            MiniMapRenderer.setMiniMapReceivedAtMs(0L);
            WaypointManager.setActiveRoundCourseWaypointName("");
            WaypointManager.removePermanentCourseWaypoint(client, courseToRemove);
            MiniMapRenderer.setHudVisibleSinceMsFromSync(0L);
            MiniMapRenderer.setMiniMapJoinWarmupPending(true);
            MiniMapRenderer.setMiniMapJoinPrimeTicksRemaining(MiniMapRenderer.MINIMAP_JOIN_PRIME_TICKS);
            MiniMapRenderer.setLastMiniMapRenderAtMs(0L);
            return;
        }

        if (MiniMapRenderer.getMiniMapState() == null) {
            MiniMapRenderer.setHudVisibleSinceMsFromSync(System.currentTimeMillis());
        }

        MiniMapRenderer.setMiniMapState(new MiniMapState(
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
                payload.mapSpan(),
                payload.lastThrowDistanceFeet()
        ));
        WaypointManager.setActiveRoundCourseWaypointName(payload.courseWaypointName());
        WaypointManager.upsertPermanentCourseWaypoint(
                client,
                payload.courseWaypointName(),
                payload.courseWaypointX(),
                payload.courseWaypointZ()
        );
        WaypointManager.syncRoundHoleWaypointsFromPayload(payload);
        MiniMapRenderer.setMiniMapReceivedAtMs(System.currentTimeMillis());
        MiniMapRenderer.refreshMiniMapRenderCache(client, MiniMapRenderer.PASSIVE_MINIMAP_SPAN_BLOCKS);
    }

    public static void onRoundRunningScoresSync(RoundRunningScoresSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            WaypointManager.removePermanentCourseWaypoint(client, WaypointManager.getActiveRoundCourseWaypointName());
            runningRoundScoreState = null;
            WaypointManager.setActiveRoundCourseWaypointName("");
            return;
        }

        List<RunningRoundScoreRow> rows = new ArrayList<>();
        for (RoundRunningScoresSync.PlayerRow row : payload.rows()) {
            rows.add(new RunningRoundScoreRow(row.playerName(), row.online(), row.holeScores(), row.runningTotal()));
        }
        runningRoundScoreState = new RunningRoundScoreState(payload.totalHoles(), payload.focusHole(), payload.courseName(), rows);
    }

    public static void onLeaderboardResponse(LeaderboardResponse.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            return;
        }
        LeaderboardScreen.open(payload.courseName(), payload.totalPar(), payload.entries());
    }

}