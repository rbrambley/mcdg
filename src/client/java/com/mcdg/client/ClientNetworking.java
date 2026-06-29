package com.mcdg.client;

import com.mcdg.game.ChargedDiscItem;
import com.mcdg.net.AceCinematicSync;

import com.mcdg.net.HoleMapSync;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.net.NextThrowModifierSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.net.SkillsScreenSync;
import com.mcdg.net.SkillsStatusSync;
import com.mcdg.net.RoundRunningScoresSync;
import com.mcdg.net.ThrowPowerLockSync;
import com.mcdg.net.ThrowTrailSync;
import com.mcdg.net.ThrowTrailStartSync;
import com.mcdg.net.ThrowTrailCompleteSync;
import com.mcdg.net.RoundInviteNotification;
import com.mcdg.net.WindSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Registers all client-side networking packet receivers.
 */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HoleMapSync.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onHoleMapSync(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(AceCinematicSync.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.active()) {
                    CinematicOverlay.clearAce();
                    return;
                }
                CinematicOverlay.activateAce(payload.holeIndex(), payload.distanceFeet());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(RoundCompleteCinematicSync.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!payload.active()) {
                    CinematicOverlay.clearRoundComplete();
                    return;
                }
                CinematicOverlay.activateRoundComplete(
                        payload.totalPar(),
                        payload.totalPlayers(),
                        payload.firstName(),
                        payload.firstScore(),
                        payload.secondName(),
                        payload.secondScore(),
                        payload.thirdName(),
                        payload.thirdScore(),
                        payload.localRank(),
                        payload.localScore()
                );
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(MenuScreenSync.ID, (payload, context) ->
            context.client().execute(() -> context.client().setScreen(new McdgMenuScreen(payload)))
        );
        ClientPlayNetworking.registerGlobalReceiver(SkillsScreenSync.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof McdgMenuScreen menuScreen) {
                    menuScreen.updateSkillsData(payload);
                } else {
                    McdgMenuScreen.openSkillsPage(payload);
                }
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(SkillsStatusSync.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.updateUnlockedSkills(payload.unlockedSkills()))
        );
        ClientPlayNetworking.registerGlobalReceiver(RoundRunningScoresSync.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onRoundRunningScoresSync(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(LeaderboardResponse.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onLeaderboardResponse(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(ThrowPowerLockSync.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.player() != null) {
                    ChargedDiscItem.setPowerLocked(context.player().getUuid(), payload.locked());
                }
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(ThrowTrailSync.ID, (payload, context) ->
            context.client().execute(() -> {
                DiscTrailRenderer.startTrail(
                        payload.throwerId(),
                        payload.pathPoints(),
                        payload.totalDistanceFt(),
                        payload.lateralDriftFt(),
                        payload.stance(),
                        payload.angle(),
                        payload.flightTicks(),
                        payload.penaltyType(),
                        payload.penaltyStrokes(),
                        payload.penaltyReason(),
                        payload.obCrossingFeet(),
                        payload.returnedToFeet()
                );
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(ThrowTrailStartSync.ID, (payload, context) ->
            context.client().execute(() -> {
                DiscTrailRenderer.startProgressiveTrail(
                        payload.throwerId(),
                        payload.pathPoints(),
                        payload.flightTicks(),
                        payload.stance(),
                        payload.angle()
                );
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(ThrowTrailCompleteSync.ID, (payload, context) ->
            context.client().execute(() -> {
                DiscTrailRenderer.completeTrail(
                        payload.throwerId(),
                        payload.totalDistanceFt(),
                        payload.lateralDriftFt(),
                        payload.penaltyType(),
                        payload.penaltyStrokes(),
                        payload.penaltyReason(),
                        payload.obCrossingFeet(),
                        payload.returnedToFeet()
                );
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(RoundInviteNotification.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof RoundInviteScreen) {
                    return; // already have an invite open
                }
                context.client().setScreen(new RoundInviteScreen(
                        payload.initiatorId(),
                        payload.initiatorName(),
                        payload.courseName(),
                        payload.catalogIndex()
                ));
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(WindSync.ID, (payload, context) ->
            context.client().execute(() -> WindManagerClient.updateWindState(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(NextThrowModifierSync.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.player() != null) {
                    McdgClientMod.setClientNextThrowPowerMultiplier(payload.nextThrowPowerMultiplier());
                }
            })
        );
    }
}
