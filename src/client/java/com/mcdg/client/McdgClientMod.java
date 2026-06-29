package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.McdgItems;
import com.mcdg.net.HoleMapSync;
import com.mcdg.net.ThrowStanceSync;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.RoundRunningScoresSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;

import net.minecraft.util.Identifier;
import com.mcdg.game.McdgScreenHandlers;


public final class McdgClientMod implements ClientModInitializer {

    private static final Identifier TRAINING_DISC_CHARGED_PREDICATE = new Identifier("mcdg", "charged");

    private static final Map<UUID, RunningRoundScoreState> RUNNING_ROUND_SCORE_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, HoleMapState> HOLE_MAP_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HOLE_MAP_STATE_RECEIVED_AT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> HUD_HIDE_SINCE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ROUND_ENDED = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> UNLOCKED_SKILLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> CLIENT_NEXT_THROW_POWER_MULTIPLIER = new ConcurrentHashMap<>();

    private static UUID localPlayerUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getUuid() : null;
    }

    public static float getClientNextThrowPowerMultiplier() {
        UUID uuid = localPlayerUuid();
        return uuid == null ? 1.0f : CLIENT_NEXT_THROW_POWER_MULTIPLIER.getOrDefault(uuid, 1.0f);
    }

    public static void setClientNextThrowPowerMultiplier(UUID playerUuid, float multiplier) {
        if (playerUuid != null) {
            CLIENT_NEXT_THROW_POWER_MULTIPLIER.put(playerUuid, Math.max(0.0f, Math.min(1.0f, multiplier)));
        }
    }

    // Cached left-side layout inputs — recomputed only when inputs change
    private static final Map<UUID, RunningRoundScoreState> LAST_LAYOUT_SCORE_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_LAYOUT_SCREEN_HEIGHT = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_LAYOUT_SCALE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CACHED_SCOREBOARD_REQUIRED = new ConcurrentHashMap<>();

    private static void registerDiscChargePredicate(net.minecraft.item.Item item) {
        ModelPredicateProviderRegistry.register(
                item,
                TRAINING_DISC_CHARGED_PREDICATE,
                (stack, world, entity, seed) -> {
                    if (entity == null) {
                        return 0.0f;
                    }
                    if (!ChargedDiscItem.isClientChargeVisible(entity.getUuid())) {
                        return 0.0f;
                    }
                    return ChargedDiscItem.getClientChargePercent(entity.getUuid()) >= 0.15f ? 1.0f : 0.0f;
                }
        );
    }

    @Override
    public void onInitializeClient() {
        registerDiscChargePredicate(McdgItems.TRAINING_DISC);
        registerDiscChargePredicate(McdgItems.WOODEN_DISC);
        registerDiscChargePredicate(McdgItems.STONE_DISC);
        registerDiscChargePredicate(McdgItems.IRON_DISC);
        registerDiscChargePredicate(McdgItems.GOLD_DISC);
        registerDiscChargePredicate(McdgItems.DIAMOND_DISC);
        registerDiscChargePredicate(McdgItems.NETHERITE_DISC);

        ClientKeybinds.register();
        HandledScreens.register(McdgScreenHandlers.DISC_WORKBENCH, DiscWorkbenchScreen::new);
        HandledScreens.register(McdgScreenHandlers.DISC_BAG, DiscBagScreen::new);

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.options.getChatScale().setValue(0.65);
            client.options.getChatHeightUnfocused().setValue(0.25);
            client.options.write();
            // Calculate Xaero's minimap width on client start
            HudUtil.recalculateXaeroWidth();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientKeybinds.forEachOpenMenuPress(() -> {
                if (client.player != null && client.getNetworkHandler() != null && client.currentScreen == null) {
                    client.getNetworkHandler().sendChatCommand("mcdg");
                }
            });
            ClientKeybinds.forEachLockPowerPress(() -> {
                if (client.player != null && ChargedDiscItem.isClientChargeVisible(client.player.getUuid())) {
                    // Only allow locking if not already locked (final lock - no toggle)
                    if (!ChargedDiscItem.isPowerLocked(client.player.getUuid())) {
                        ChargedDiscItem.setPowerLocked(client.player.getUuid(), true);
                        ClientPlayNetworking.send(new com.mcdg.net.ThrowPowerLockSync.Payload(true, ChargedDiscItem.getClientChargePercent(client.player.getUuid())));
                    }
                }
            });
            // Phase 2: Stance cycling with R key
            ClientKeybinds.forEachStanceCyclePress(() -> {
                if (client.player != null) {
                    UUID playerUuid = client.player.getUuid();
                    ThrowPreferenceManager.cycleStance(playerUuid);
                    // Send to server so it's available at throw time
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(playerUuid),
                        ThrowPreferenceManager.getSelectedAngle(playerUuid)
                    ));
                    // Show feedback to player
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("Stance: ")
                            .append(net.minecraft.text.Text.literal(ThrowPreferenceManager.getSelectedStance(playerUuid).toString())
                                .formatted(net.minecraft.util.Formatting.AQUA)),
                        true
                    );
                }
            });
            // Phase 3: Release angle adjustment with Left/Right arrow keys
            ClientKeybinds.forEachAngleLeftPress(() -> {
                if (client.player != null) {
                    UUID playerUuid = client.player.getUuid();
                    // Left arrow = cycle backwards through angles
                    // Since next() goes Hyzer -> Flat -> Anhyzer -> Hyzer,
                    // calling next() twice moves backwards
                    ThrowPreferenceManager.cycleAngle(playerUuid);
                    ThrowPreferenceManager.cycleAngle(playerUuid);
                    // Send updated stance/angle to server
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(playerUuid),
                        ThrowPreferenceManager.getSelectedAngle(playerUuid)
                    ));
                    showAngleFeedback(client);
                }
            });
            ClientKeybinds.forEachAngleRightPress(() -> {
                if (client.player != null) {
                    UUID playerUuid = client.player.getUuid();
                    // Right arrow = cycle forward through angles
                    ThrowPreferenceManager.cycleAngle(playerUuid);
                    // Send updated stance/angle to server
                    ClientPlayNetworking.send(new ThrowStanceSync.Payload(
                        ThrowPreferenceManager.getSelectedStance(playerUuid),
                        ThrowPreferenceManager.getSelectedAngle(playerUuid)
                    ));
                    showAngleFeedback(client);
                }
            });
            AutoConnect.tick(client);
            handleHoleMapToggle(client);
            CinematicOverlay.tick(client);
            DiscTrailRenderer.tick();
            RoundInfoOverlay.updateTweens(getHoleMapState());

            // If movement detected during round complete cinematic, clear cinematic immediately
            if (CinematicOverlay.isRoundCompleteActive() && CinematicOverlay.checkMovementSkip(client)) {
                // Cinematic movement skip - let HUDs continue their independent fade
            }

            // If HUDs are fading out after round end, keep state fresh so
            // the stale timeout doesn't cut the fade short. Once 30 seconds pass, clear state.
            UUID localUuid = localPlayerUuid();
            if (localUuid != null) {
                Long hideSince = HUD_HIDE_SINCE_MS.get(localUuid);
                if (hideSince != null && hideSince > 0L) {
                    long elapsed = System.currentTimeMillis() - hideSince;
                    if (elapsed >= 30000L) {
                        HOLE_MAP_STATE.remove(localUuid);
                        HOLE_MAP_STATE_RECEIVED_AT_MS.remove(localUuid);
                        HUD_HIDE_SINCE_MS.remove(localUuid);
                        DiscTrailRenderer.clearStats();
                        RUNNING_ROUND_SCORE_STATE.remove(localUuid);
                        ROUND_ENDED.remove(localUuid);
                    } else {
                        HOLE_MAP_STATE_RECEIVED_AT_MS.put(localUuid, System.currentTimeMillis());
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

                // GUI scale advisory — scales 1 and 2 produce small text and layout issues
                int guiScale = client.options.getGuiScale().getValue();
                if (guiScale == 1 || guiScale == 2) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("MCDG tip: ")
                            .formatted(net.minecraft.util.Formatting.GOLD)
                            .append(net.minecraft.text.Text.literal("GUI Scale " + guiScale + " may cause small text and HUD layout issues. ")
                                .formatted(net.minecraft.util.Formatting.YELLOW))
                            .append(net.minecraft.text.Text.literal("GUI Scale 3 or Auto is recommended for the best experience.")
                                .formatted(net.minecraft.util.Formatting.GRAY)),
                        false
                    );
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            UUID playerUuid = client.player != null ? client.player.getUuid() : localPlayerUuid();
            if (playerUuid != null) {
                HOLE_MAP_STATE.remove(playerUuid);
                HOLE_MAP_STATE_RECEIVED_AT_MS.remove(playerUuid);
                HUD_HIDE_SINCE_MS.remove(playerUuid);
                RUNNING_ROUND_SCORE_STATE.remove(playerUuid);
                ROUND_ENDED.remove(playerUuid);
                UNLOCKED_SKILLS.remove(playerUuid);
                CLIENT_NEXT_THROW_POWER_MULTIPLIER.remove(playerUuid);
                LAST_LAYOUT_SCORE_STATE.remove(playerUuid);
                LAST_LAYOUT_SCREEN_HEIGHT.remove(playerUuid);
                LAST_LAYOUT_SCALE.remove(playerUuid);
                CACHED_SCOREBOARD_REQUIRED.remove(playerUuid);
                HudOverlays.clearPlayerState(playerUuid);
                ChargedDiscItem.clearClientState(playerUuid);
                ThrowPreferenceManager.reset(playerUuid);
            }
            DiscTrailRenderer.clearAllStats();
            ScorecardOverlay.setThrowStatsRenderedThisFrame(false);
        });
        ClientNetworking.registerReceivers();
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            HoleMapState currentHoleMapState = getHoleMapState();
            RunningRoundScoreState currentRunningScoreState = getRunningRoundScoreState();
            UUID localUuid = localPlayerUuid();

            RoundInfoOverlay.updateTweens(currentHoleMapState);
            float hudAlpha = hudFadeAlpha();

            // Right-side HUDs (all use same fade logic)
            RoundInfoOverlay.render(drawContext, currentHoleMapState, hudAlpha);
            HudOverlays.renderThrowStats(drawContext, MinecraftClient.getInstance(), hudAlpha);
            ScorecardOverlay.setThrowStatsRenderedThisFrame(HudOverlays.isThrowStatsRenderedThisFrame());
            HudOverlays.renderStanceSettings(drawContext, MinecraftClient.getInstance(), hudAlpha);
            ScorecardOverlay.render(drawContext, currentHoleMapState, getHoleMapStateReceivedAtMs(), hudAlpha);
            
            // Left-side HUDs - use shared layout manager for vertical coordination
            float scale = HudUtil.getScaleFactor(drawContext);
            int screenHeight = drawContext.getScaledWindowHeight();

            // Recompute scoreboard height only when state or screen dimensions change.
            // State changes on server packet; screen changes on resize or GUI scale change.
            RunningRoundScoreState lastScoreState = localUuid != null ? LAST_LAYOUT_SCORE_STATE.get(localUuid) : null;
            Integer lastScreenHeight = localUuid != null ? LAST_LAYOUT_SCREEN_HEIGHT.get(localUuid) : null;
            Float lastScale = localUuid != null ? LAST_LAYOUT_SCALE.get(localUuid) : null;
            if (currentRunningScoreState != lastScoreState
                    || screenHeight != (lastScreenHeight != null ? lastScreenHeight : -1)
                    || scale != (lastScale != null ? lastScale : -1f)) {
                if (localUuid != null) {
                    LAST_LAYOUT_SCORE_STATE.put(localUuid, currentRunningScoreState);
                    LAST_LAYOUT_SCREEN_HEIGHT.put(localUuid, screenHeight);
                    LAST_LAYOUT_SCALE.put(localUuid, scale);
                    CACHED_SCOREBOARD_REQUIRED.put(localUuid,
                            RunningScoreboardOverlay.computeRequiredHeight(currentRunningScoreState, scale));
                }
            }

            LeftSideHudLayout layout = LeftSideHudLayout.withXaeroOffset(screenHeight, scale);
            int cachedScoreboardRequired = localUuid != null ? CACHED_SCOREBOARD_REQUIRED.getOrDefault(localUuid, 0) : 0;
            if (cachedScoreboardRequired > 0) {
                layout.reserveBottom(cachedScoreboardRequired + Math.round(8 * scale)); // 8 = HUD_SPACING
            }

            HoleMapOverlay.render(drawContext, MinecraftClient.getInstance(), hudAlpha, layout);
            RunningScoreboardOverlay.render(drawContext, currentRunningScoreState, hudAlpha, layout);
            
            // Center HUDs
            HudOverlays.renderCompass(drawContext);
            HudOverlays.renderPower(drawContext);
            
            // Cinematics
            CinematicOverlay.render(drawContext);

            // Scorecard-in-frame tooltip
            ScorecardFrameTooltipRenderer.render(drawContext, MinecraftClient.getInstance(), hudAlpha);
        });
    }

    public static HoleMapState getHoleMapState() {
        UUID uuid = localPlayerUuid();
        return uuid != null ? HOLE_MAP_STATE.get(uuid) : null;
    }

    public static long getHoleMapStateReceivedAtMs() {
        UUID uuid = localPlayerUuid();
        return uuid != null ? HOLE_MAP_STATE_RECEIVED_AT_MS.getOrDefault(uuid, 0L) : 0L;
    }

    public static RunningRoundScoreState getRunningRoundScoreState() {
        UUID uuid = localPlayerUuid();
        return uuid != null ? RUNNING_ROUND_SCORE_STATE.get(uuid) : null;
    }

    public static java.util.Set<String> getUnlockedSkills() {
        UUID uuid = localPlayerUuid();
        return uuid != null ? UNLOCKED_SKILLS.getOrDefault(uuid, java.util.Set.of()) : java.util.Set.of();
    }

    public static void updateUnlockedSkills(UUID playerUuid, java.util.Set<String> skills) {
        if (playerUuid != null) {
            UNLOCKED_SKILLS.put(playerUuid, skills);
        }
    }

    private static float hudFadeAlpha() {
        UUID uuid = localPlayerUuid();
        Long hideSince = uuid != null ? HUD_HIDE_SINCE_MS.get(uuid) : null;
        if (hideSince != null && hideSince > 0L) {
            long elapsed = System.currentTimeMillis() - hideSince;
            if (elapsed >= 30000L) {
                return 0.0f;
            }
            return Math.max(0.0f, 1.0f - (elapsed / 30000.0f));
        }
        return 1.0f;
    }

    private static void handleHoleMapToggle(MinecraftClient client) {
        HoleMapState current = getHoleMapState();
        ClientKeybinds.forEachHoleMapTogglePress(() -> {
            if (current == null || !current.isActive()) {
                return;
            }
            HoleMapOverlay.toggle();
        });
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
        UUID playerUuid = client.player != null ? client.player.getUuid() : null;
        if (playerUuid == null) {
            return;
        }

        if (!payload.active()) {
            // Start 30-second fade timer immediately when round ends
            HUD_HIDE_SINCE_MS.put(playerUuid, System.currentTimeMillis());
            ROUND_ENDED.put(playerUuid, true);
            // Clear throw stats when round ends
            DiscTrailRenderer.clearStats();
            ChargedDiscItem.clearClientState(playerUuid);
            return;
        }

        // New round starting — cancel any pending hide and show immediately
        HUD_HIDE_SINCE_MS.remove(playerUuid);
        ROUND_ENDED.remove(playerUuid);
        HOLE_MAP_STATE.put(playerUuid, new HoleMapState(payload));
        HOLE_MAP_STATE_RECEIVED_AT_MS.put(playerUuid, System.currentTimeMillis());

        // Recalculate all cached values when round starts/resumes
        HudUtil.recalculateAll();

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
    }

    public static void onRoundRunningScoresSync(RoundRunningScoresSync.Payload payload, MinecraftClient client) {
        if (!payload.active()) {
            return;
        }
        UUID playerUuid = client.player != null ? client.player.getUuid() : null;
        if (playerUuid == null) {
            return;
        }

        List<RunningRoundScoreRow> rows = new ArrayList<>();
        for (RoundRunningScoresSync.PlayerRow row : payload.rows()) {
            rows.add(new RunningRoundScoreRow(row.playerName(), row.online(), row.holeScores(), row.runningTotal()));
        }
        RUNNING_ROUND_SCORE_STATE.put(playerUuid, new RunningRoundScoreState(payload.totalHoles(), payload.focusHole(), payload.courseName(), rows));
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

        UUID playerUuid = client.player.getUuid();
        String angleSymbol = switch (ThrowPreferenceManager.getSelectedAngle(playerUuid)) {
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
