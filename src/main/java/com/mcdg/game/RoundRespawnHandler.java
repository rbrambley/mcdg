package com.mcdg.game;

import com.mcdg.rules.TournamentRulesetManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class RoundRespawnHandler {
    private RoundRespawnHandler() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            int strictRespawnPenaltyStrokes
    ) {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!courseManager.isRoundActive()) {
                return;
            }

            PlayerRoundState state = roundStateManager.getState(newPlayer.getUuid()).orElse(null);
            if (state == null) {
                return;
            }

            boolean applyPenalty = rulesetManager.isStrict() && strictRespawnPenaltyStrokes > 0;
            if (applyPenalty) {
                state = roundStateManager.applyPenaltyStrokes(newPlayer.getUuid(), strictRespawnPenaltyStrokes).orElse(state);
            }

            BlockPos lie = state.lie();
            ServerWorld targetWorld = newPlayer.getServerWorld();
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (placed != null) {
                ServerWorld courseWorld = newPlayer.server.getWorld(placed.worldKey());
                if (courseWorld != null) {
                    targetWorld = courseWorld;
                }
            }

            newPlayer.teleport(targetWorld, lie.getX() + 0.5, lie.getY() + 1.0, lie.getZ() + 0.5, newPlayer.getYaw(), newPlayer.getPitch());
            RoundInventoryCleaner.prepareRoundInventory(newPlayer);
            if (applyPenalty) {
                newPlayer.sendMessage(
                        Text.literal("Respawned at last lie. +" + strictRespawnPenaltyStrokes + " penalty stroke(s). Inventory restored."),
                        true
                );
            } else {
                newPlayer.sendMessage(Text.literal("Respawned at last lie. Round inventory restored."), true);
            }
        });
    }
}
