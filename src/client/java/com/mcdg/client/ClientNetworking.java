package com.mcdg.client;

import com.mcdg.net.AceCinematicSync;
import com.mcdg.net.CourseRemovedSync;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.net.RoundRunningScoresSync;
import com.mcdg.net.WaypointSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Registers all client-side networking packet receivers.
 */
public final class ClientNetworking {
    private ClientNetworking() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HoleMiniMapSync.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onHoleMiniMapSync(payload, context.client()))
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
        ClientPlayNetworking.registerGlobalReceiver(RoundRunningScoresSync.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onRoundRunningScoresSync(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(LeaderboardResponse.ID, (payload, context) ->
            context.client().execute(() -> McdgClientMod.onLeaderboardResponse(payload, context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(CourseRemovedSync.ID, (payload, context) ->
            context.client().execute(() -> WaypointManager.removePermanentCourseWaypoint(context.client(), payload.courseName()))
        );
        ClientPlayNetworking.registerGlobalReceiver(WaypointSync.ID, (payload, context) ->
            context.client().execute(() -> WaypointManager.mergeWaypoints(payload.waypoints()))
        );
    }
}
