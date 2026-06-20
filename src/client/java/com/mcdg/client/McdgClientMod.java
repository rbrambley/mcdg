package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.client.ThrowPreferenceManager;
import com.mcdg.game.McdgItems;
import com.mcdg.net.HoleMapSync;
import com.mcdg.net.ThrowStanceSync;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.RoundRunningScoresSync;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;

import net.minecraft.util.Identifier;


public final class McdgClientMod implements ClientModInitializer {

    private static final Identifier TRAINING_DISC_CHARGED_PREDICATE = new Identifier("mcdg", "charged");

    private static RunningRoundScoreState runningRoundScoreState;
    private static HoleMapState holeMapState;
    private static long holeMapStateReceivedAtMs;
    private static long hudHideSinceMs;
    private static int basketBeamTick = 0;

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
            ClientKeybinds.forEachOpenMenuPress(() -> {
                if (client.player != null && client.getNetworkHandler() != null && client.currentScreen == null) {
                    client.getNetworkHandler().sendChatCommand("mcdg");
                }
            });
            ClientKeybinds.forEachLockPowerPress(() -> {
                if (client.player != null && ChargedDiscItem.isClientChargeVisible()) {
                    // Only allow locking if not already locked (final lock - no toggle)
                    if (!ChargedDiscItem.isPowerLocked()) {
                        ChargedDiscItem.setPowerLocked(true);
                        ClientPlayNetworking.send(new com.mcdg.net.ThrowPowerLockSync.Payload(true, ChargedDiscItem.getClientChargePercent()));
                    }
                }
            });
            // Phase 2: Stance cycling with R key
            ClientKeybinds.forEachStanceCyclePress(() -> {
                if (client.player != null) {
                    ThrowPreferenceManager.cycleStance();
                    // Send to server so it's available at throw time
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(),
                        ThrowPreferenceManager.getSelectedAngle()
                    ));
                    // Show feedback to player
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("Stance: ")
                            .append(net.minecraft.text.Text.literal(ThrowPreferenceManager.getSelectedStance().toString())
                                .formatted(net.minecraft.util.Formatting.AQUA)),
                        true
                    );
                }
            });
            // Phase 3: Release angle adjustment with Left/Right arrow keys
            ClientKeybinds.forEachAngleLeftPress(() -> {
                if (client.player != null) {
                    // Left arrow = cycle backwards through angles
                    // Since next() goes Hyzer -> Flat -> Anhyzer -> Hyzer,
                    // calling next() twice moves backwards
                    ThrowPreferenceManager.cycleAngle();
                    ThrowPreferenceManager.cycleAngle();
                    // Send updated stance/angle to server
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(),
                        ThrowPreferenceManager.getSelectedAngle()
                    ));
                    showAngleFeedback(client);
                }
            });
            ClientKeybinds.forEachAngleRightPress(() -> {
                if (client.player != null) {
                    // Right arrow = cycle forward through angles
                    ThrowPreferenceManager.cycleAngle();
                    // Send updated stance/angle to server
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(),
                        ThrowPreferenceManager.getSelectedAngle()
                    ));
                    showAngleFeedback(client);
                }
            });
            AutoConnect.tick(client);
            handleHoleMapToggle(client);
            CinematicOverlay.tick(client);
            DiscTrailRenderer.tick();
            RoundInfoOverlay.updateTweens(MiniMapRenderer.getMiniMapState());

            // If HUDs are fading out after round end, keep state fresh so
            // the stale timeout doesn't cut the fade short. Once 30 seconds pass, clear state.
            if (hudHideSinceMs > 0) {
                long elapsed = System.currentTimeMillis() - hudHideSinceMs;
                if (elapsed >= 30000L) {
                    holeMapState = null;
                    holeMapStateReceivedAtMs = 0L;
                    hudHideSinceMs = 0L;
                    MiniMapRenderer.setMiniMapState(null);
                    MiniMapRenderer.setMiniMapReceivedAtMs(0L);
                    MiniMapRenderer.setHudHideSinceMs(0L);
                    MiniMapRenderer.setLastMiniMapRenderAtMs(0L);
                    DiscTrailRenderer.clearStats();
                } else {
                    holeMapStateReceivedAtMs = System.currentTimeMillis();
                    MiniMapRenderer.setMiniMapReceivedAtMs(System.currentTimeMillis());
                }
            }

            // Spawn lime beacon beam above active basket during rounds
            if (client.world != null && holeMapState != null) {
                basketBeamTick++;
                if (basketBeamTick % 10 == 0) {
                    int bx = holeMapState.basketX;
                    int bz = holeMapState.basketZ;
                    int by = client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    for (int j = 0; j < 6; j++) {
                        double py = by + 1.0 + j * 3.0;
                        client.world.addParticle(
                                new DustParticleEffect(new Vector3f(0.5f, 1.0f, 0.2f), 0.5f),
                                bx + 0.5, py, bz + 0.5,
                                0.0, 0.0, 0.0
                        );
                    }
                }
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("Welcome to MCDG! Press ")
                        .formatted(net.minecraft.util.Formatting.GRAY)
                        .append(ClientKeybinds.getOpenMenuKeyText().copy().formatted(net.minecraft.util.Formatting.AQUA, net.minecraft.util.Formatting.BOLD))
                        .append(net.minecraft.text.Text.literal(" or type ").formatted(net.minecraft.util.Formatting.GRAY))
                        .append(net.minecraft.text.Text.literal("/mcdg")
                            .styled(s -> s
                                .withColor(net.minecraft.util.Formatting.AQUA)
                                .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/mcdg"))
                                .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, net.minecraft.text.Text.literal("Open the MCDG menu")))
                            ))
                        .append(net.minecraft.text.Text.literal(" to open the menu.").formatted(net.minecraft.util.Formatting.GRAY)),
                    false
                );
                
                // Xaero's Minimap integration
                if (FabricLoader.getInstance().isModLoaded("xaerominimap")) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("Xaero's Minimap detected! ")
                            .append(net.minecraft.text.Text.literal("For best experience with MCDG, set minimap position to top-left in Xaero's settings.")
                                .formatted(net.minecraft.util.Formatting.YELLOW)),
                        false
                    );
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            holeMapState = null;
            holeMapStateReceivedAtMs = 0L;
            hudHideSinceMs = 0L;
            MiniMapRenderer.setMiniMapState(null);
            MiniMapRenderer.setMiniMapReceivedAtMs(0L);
            MiniMapRenderer.setHudHideSinceMs(0L);
            MiniMapRenderer.clearMiniMapRenderCache(client);
            DiscTrailRenderer.clearAllStats();
        });
        ClientNetworking.registerReceivers();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            RoundInfoOverlay.updateTweens(MiniMapRenderer.getMiniMapState());
            float hudAlpha = hudFadeAlpha();
            RoundInfoOverlay.render(drawContext, MiniMapRenderer.getMiniMapState(), hudAlpha);
            ScorecardOverlay.render(drawContext, MiniMapRenderer.getMiniMapState(), MiniMapRenderer.getMiniMapReceivedAtMs(), hudAlpha);
            HoleMapOverlay.render(drawContext, MinecraftClient.getInstance(), hudAlpha);
            RunningScoreboardOverlay.render(drawContext, runningRoundScoreState, hudAlpha);
            HudOverlays.renderCompass(drawContext);
            HudOverlays.renderPower(drawContext);
            HudOverlays.renderThrowStats(drawContext, MinecraftClient.getInstance(), hudAlpha);
            HudOverlays.renderStanceSettings(drawContext, MinecraftClient.getInstance(), hudAlpha);
            CinematicOverlay.render(drawContext);
        });
    }

    public static boolean isRoundWaypointModeActive() {
        return MiniMapRenderer.isRoundWaypointModeActive();
    }

    public static HoleMapState getHoleMapState() {
        return holeMapState;
    }

    public static long getHoleMapStateReceivedAtMs() {
        return holeMapStateReceivedAtMs;
    }

    private static float hudFadeAlpha() {
        if (hudHideSinceMs > 0L) {
            long elapsed = System.currentTimeMillis() - hudHideSinceMs;
            if (elapsed >= 30000L) {
                return 0.0f;
            }
            return Math.max(0.0f, 1.0f - (elapsed / 30000.0f));
        }
        return 1.0f;
    }

    private static void handleHoleMapToggle(MinecraftClient client) {
        ClientKeybinds.forEachHoleMapTogglePress(() -> {
            if (holeMapState == null || !holeMapState.isActive()) {
                return;
            }
            HoleMapOverlay.toggle();
        });
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
            int lastThrowDistanceFeet,
            int corridorEntryFeet,
            int corridorEntryBearing,
            int waterGapStartFeet,
            int waterGapEndFeet,
            boolean hasWaterGap
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

    public static void onHoleMapSync(HoleMapSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            hudHideSinceMs = System.currentTimeMillis();
            holeMapState = null;
            HoleMapOverlay.setVisible(false);
            return;
        }

        // New round starting — cancel any pending hide and show immediately
        hudHideSinceMs = 0L;
        holeMapState = new HoleMapState(payload);
        holeMapStateReceivedAtMs = System.currentTimeMillis();

        // Show hole map by default when round starts
        HoleMapOverlay.setVisible(true);

        if (payload.hasLastThrowStats()) {
            DiscTrailRenderer.setStats(
                    payload.lastThrowTotalDistanceFt(),
                    payload.lastThrowLateralDriftFt(),
                    payload.lastThrowStance(),
                    payload.lastThrowAngle(),
                    payload.lastThrowFlightTicks(),
                    payload.lastThrowPenaltyType(),
                    payload.lastThrowPenaltyStrokes(),
                    payload.lastThrowPenaltyReason(),
                    payload.lastThrowObCrossingFeet(),
                    payload.lastThrowReturnedToFeet()
            );
        }

        // Keep MiniMapState populated for overlay compatibility
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
                false, // strictMode — not used by overlays
                0,     // strictSurfacePresetOrdinal — not used by overlays
                payload.corridorHalfWidth(),
                false, 0, 0, // hasAlternateAnchor, alternateAnchorX, alternateAnchorZ — not used
                0,     // mapSpan — not used by overlays
                payload.lastThrowDistanceFeet(),
                payload.corridorEntryFeet(),
                payload.corridorEntryBearing(),
                payload.waterGapStartFeet(),
                payload.waterGapEndFeet(),
                payload.hasWaterGap()
        ));
        MiniMapRenderer.setMiniMapReceivedAtMs(System.currentTimeMillis());
    }

    public static void onRoundRunningScoresSync(RoundRunningScoresSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            runningRoundScoreState = null;
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


    /**
     * Phase 3: Helper method to show angle change feedback to player
     */
    private static void showAngleFeedback(MinecraftClient client) {
        if (client.player == null) return;

        String angleSymbol = switch (ThrowPreferenceManager.getSelectedAngle()) {
            case HYZER -> "^ Hyzer";
            case FLAT -> "- Flat";
            case ANHYZER -> "v Anhyzer";
        };
        client.player.sendMessage(
            net.minecraft.text.Text.literal("Angle: ")
                .append(net.minecraft.text.Text.literal(angleSymbol)
                    .formatted(net.minecraft.util.Formatting.YELLOW)),
            true
        );
    }
}
