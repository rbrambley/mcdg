package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.rules.TournamentRulesetManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Builds and sends minimap sync payloads to clients.
 * Tracks per-player payload hashes to avoid redundant network sends.
 */
public final class MiniMapSyncService {
    private static final int HUD_LINGER_TICKS = 600;
    private static final Map<UUID, Integer> LAST_MINIMAP_HOLE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MINIMAP_PAYLOAD_HASH = new HashMap<>();
    private static final Map<UUID, Long> PENDING_INACTIVE_TICK = new HashMap<>();
    private static boolean ACTIVE_SENT = false;

    private MiniMapSyncService() {
    }

    public static int hudLingerTicks() {
        return HUD_LINGER_TICKS;
    }

    public static void reset() {
        LAST_MINIMAP_HOLE.clear();
        LAST_MINIMAP_PAYLOAD_HASH.clear();
        PENDING_INACTIVE_TICK.clear();
        ACTIVE_SENT = false;
    }

    public static void scheduleInactiveForPlayer(UUID playerId, long atTick) {
        PENDING_INACTIVE_TICK.put(playerId, atTick);
    }

    public static void tickPendingInactive(MinecraftServer server) {
        if (PENDING_INACTIVE_TICK.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        HoleMiniMapSync.Payload inactive = HoleMiniMapSync.Payload.inactive();
        PENDING_INACTIVE_TICK.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                ServerPlayNetworking.send(player, inactive);
            }
            return true;
        });
    }

    public static void sendInactive(MinecraftServer server) {
        if (!ACTIVE_SENT) {
            return;
        }
        HoleMiniMapSync.Payload inactive = HoleMiniMapSync.Payload.inactive();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, inactive);
        }
        ACTIVE_SENT = false;
    }

    public static Integer lastHoleForPlayer(UUID playerId) {
        return LAST_MINIMAP_HOLE.get(playerId);
    }

    public static void sync(
            MinecraftServer server,
            ServerPlayerEntity player,
            ActiveCourseManager courseManager,
            Course course,
            PlacedCourseState placed,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth,
            int cumulativeParDelta,
            int lastThrowDistanceFeet,
            boolean strictFlowDebug
    ) {
        UUID playerId = player.getUuid();
        BlockPos mapFocus = player.getBlockPos();
        int[] corridorEntry = tee != null
                ? OutOfBoundsClassifier.nearestForwardCorridorEntry(state.lie(), tee, basket, alternateAnchor, corridorHalfWidth)
                : null;
        int corridorEntryFeet = corridorEntry != null ? corridorEntry[0] : 0;
        int corridorEntryBearing = corridorEntry != null ? corridorEntry[1] : 0;

        HoleMiniMapSync.Payload miniMapPayload = buildMiniMapPayload(
                (ServerWorld) player.getWorld(),
                courseManager,
                course,
                placed,
                state.currentHole(),
                currentHole.par(),
                Math.max(1, state.holeStrokes() + 1),
                state.totalStrokes(),
                cumulativeParDelta,
                tee == null ? state.lie() : tee,
                basket,
                state.lie(),
                mapFocus,
                rulesetManager.isStrict(),
                rulesetManager.getStrictSurfacePreset().ordinal(),
                corridorHalfWidth,
                alternateAnchor,
                lastThrowDistanceFeet,
                corridorEntryFeet,
                corridorEntryBearing
        );

        if (strictFlowDebug && (server.getTicks() % 20) == 0) {
            HoleMiniMapSync.Payload payload = miniMapPayload;
            BlockPos playerFeet = player.getBlockPos();
            McdgMod.LOGGER.info(
                    "MINIMAP DEBUG | player={} hole={} feet=({}, {}) lie=({}, {}) tee=({}, {}) basket=({}, {}) span={} dFeetLie=({}, {})",
                    player.getGameProfile().getName(),
                    state.currentHole(),
                    playerFeet.getX(),
                    playerFeet.getZ(),
                    payload.lieX(),
                    payload.lieZ(),
                    payload.teeX(),
                    payload.teeZ(),
                    payload.basketX(),
                    payload.basketZ(),
                    payload.mapSpan(),
                    playerFeet.getX() - payload.lieX(),
                    playerFeet.getZ() - payload.lieZ()
            );
        }

        int miniMapHash = miniMapPayload.hashCode();
        if (!LAST_MINIMAP_PAYLOAD_HASH.containsKey(playerId) || LAST_MINIMAP_PAYLOAD_HASH.get(playerId) != miniMapHash) {
            ServerPlayNetworking.send(player, miniMapPayload);
            LAST_MINIMAP_PAYLOAD_HASH.put(playerId, miniMapHash);
            LAST_MINIMAP_HOLE.put(playerId, state.currentHole());
        }
        ACTIVE_SENT = true;
    }

    public static void forceSync(
            MinecraftServer server,
            ServerPlayerEntity player,
            ActiveCourseManager courseManager,
            Course course,
            PlacedCourseState placed,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth,
            int cumulativeParDelta,
            int lastThrowDistanceFeet,
            boolean strictFlowDebug
    ) {
        UUID playerId = player.getUuid();
        BlockPos mapFocus = player.getBlockPos();
        int[] corridorEntry = tee != null
                ? OutOfBoundsClassifier.nearestForwardCorridorEntry(state.lie(), tee, basket, alternateAnchor, corridorHalfWidth)
                : null;
        int corridorEntryFeet = corridorEntry != null ? corridorEntry[0] : 0;
        int corridorEntryBearing = corridorEntry != null ? corridorEntry[1] : 0;

        HoleMiniMapSync.Payload miniMapPayload = buildMiniMapPayload(
                (ServerWorld) player.getWorld(),
                courseManager,
                course,
                placed,
                state.currentHole(),
                currentHole.par(),
                state.holeStrokes(),
                state.totalStrokes(),
                cumulativeParDelta,
                tee == null ? state.lie() : tee,
                basket,
                state.lie(),
                mapFocus,
                rulesetManager.isStrict(),
                rulesetManager.getStrictSurfacePreset().ordinal(),
                corridorHalfWidth,
                alternateAnchor,
                lastThrowDistanceFeet,
                corridorEntryFeet,
                corridorEntryBearing
        );

        ServerPlayNetworking.send(player, miniMapPayload);
        LAST_MINIMAP_PAYLOAD_HASH.put(playerId, miniMapPayload.hashCode());
        LAST_MINIMAP_HOLE.put(playerId, state.currentHole());
        ACTIVE_SENT = true;
    }

    private static HoleMiniMapSync.Payload buildMiniMapPayload(
            ServerWorld world,
            ActiveCourseManager courseManager,
            Course course,
            PlacedCourseState placed,
            int holeIndex,
            int par,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            BlockPos tee,
            BlockPos basket,
            BlockPos lie,
            BlockPos mapFocus,
            boolean strictMode,
            int strictSurfacePresetOrdinal,
            int corridorHalfWidth,
            BlockPos alternateAnchor,
            int lastThrowDistanceFeet,
            int corridorEntryFeet,
            int corridorEntryBearing
    ) {
        int[] waterGap = OutOfBoundsClassifier.findLongestWaterGap(world, lie, basket);
        int waterGapStartFeet = waterGap[2] > 0 ? Math.round(waterGap[0] * 3.28084f) : 0;
        int waterGapEndFeet = waterGap[2] > 0 ? Math.round(waterGap[1] * 3.28084f) : 0;
        boolean hasWaterGap = waterGap[2] > 0;

        int span;
        int minX = Math.min(Math.min(tee.getX(), basket.getX()), mapFocus.getX());
        int maxX = Math.max(Math.max(tee.getX(), basket.getX()), mapFocus.getX());
        int minZ = Math.min(Math.min(tee.getZ(), basket.getZ()), mapFocus.getZ());
        int maxZ = Math.max(Math.max(tee.getZ(), basket.getZ()), mapFocus.getZ());
        if (alternateAnchor != null) {
            minX = Math.min(minX, alternateAnchor.getX());
            maxX = Math.max(maxX, alternateAnchor.getX());
            minZ = Math.min(minZ, alternateAnchor.getZ());
            maxZ = Math.max(maxZ, alternateAnchor.getZ());
        }
        int baseSpan = Math.max(Math.max(1, maxX - minX), Math.max(1, maxZ - minZ)) + 10;
        int maxLieDelta = maxLieDelta(mapFocus, tee, basket, alternateAnchor);
        int rawSpan = Math.max(baseSpan, (maxLieDelta * 2) + 24);
        span = Math.max(120, Math.round(rawSpan * HoleMiniMapSync.MAP_OVERSCAN_FACTOR));

        String courseWaypointName = resolveCourseWaypointName(courseManager, course);
        BlockPos courseAnchor = resolveTournamentCentralAnchor(
                placed.holeTees().get(1),
                placed.holeBaskets().get(1),
                tee,
                basket
        );
        int courseWaypointX = courseAnchor.getX();
        int courseWaypointZ = courseAnchor.getZ();

        List<Integer> holeTeeXs = new ArrayList<>();
        List<Integer> holeTeeZs = new ArrayList<>();
        int totalHoles = course.holes().size();
        for (int i = 1; i <= totalHoles; i++) {
            BlockPos holeTee = placed.holeTees().get(i);
            BlockPos holeBasket = placed.holeBaskets().get(i);
            BlockPos holeAnchor = resolveWaypointAnchor(holeTee, holeBasket, tee, basket);
            holeTeeXs.add(holeAnchor.getX());
            holeTeeZs.add(holeAnchor.getZ());
        }

        return HoleMiniMapSync.Payload.active(
                holeIndex,
                tee.getX(),
                tee.getZ(),
                basket.getX(),
                basket.getZ(),
                lie.getX(),
                lie.getZ(),
                par,
                throwNumber,
                totalStrokes,
                cumulativeParDelta,
                strictMode,
                strictSurfacePresetOrdinal,
                corridorHalfWidth,
                alternateAnchor != null,
                alternateAnchor == null ? 0 : alternateAnchor.getX(),
                alternateAnchor == null ? 0 : alternateAnchor.getZ(),
                span,
                courseWaypointName,
                courseWaypointX,
                courseWaypointZ,
                totalHoles,
                holeTeeXs,
                holeTeeZs,
                lastThrowDistanceFeet,
                corridorEntryFeet,
                corridorEntryBearing,
                waterGapStartFeet,
                waterGapEndFeet,
                hasWaterGap
        );
    }

    private static String resolveCourseWaypointName(ActiveCourseManager courseManager, Course course) {
        return course.name() + " " + course.seed();
    }

    private static BlockPos resolveTournamentCentralAnchor(BlockPos preferredTee, BlockPos preferredBasket, BlockPos fallbackTee, BlockPos fallbackBasket) {
        BlockPos teeAnchor = preferredTee == null ? fallbackTee : preferredTee;
        if (teeAnchor == null) {
            return fallbackBasket == null ? BlockPos.ORIGIN : fallbackBasket;
        }

        BlockPos basketAnchor = preferredBasket == null ? fallbackBasket : preferredBasket;
        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0] * 12, 0, back[1] * 12);
    }

    private static BlockPos resolveWaypointAnchor(BlockPos preferredTee, BlockPos preferredBasket, BlockPos fallbackTee, BlockPos fallbackBasket) {
        BlockPos teeAnchor = preferredTee == null ? fallbackTee : preferredTee;
        if (teeAnchor == null) {
            return fallbackBasket == null ? BlockPos.ORIGIN : fallbackBasket;
        }

        BlockPos basketAnchor = preferredBasket == null ? fallbackBasket : preferredBasket;
        if (basketAnchor == null) {
            return teeAnchor;
        }

        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0], 0, back[1]);
    }

    private static int[] resolveBackCardinal(BlockPos teeAnchor, BlockPos basketAnchor) {
        if (teeAnchor == null || basketAnchor == null) {
            return new int[] { 0, -1 };
        }

        int dx = basketAnchor.getX() - teeAnchor.getX();
        int dz = basketAnchor.getZ() - teeAnchor.getZ();
        if (dx == 0 && dz == 0) {
            return new int[] { 0, -1 };
        }

        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { -Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, -Integer.compare(dz, 0) };
    }

    private static int maxLieDelta(BlockPos lie, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        int maxDelta = Math.max(
                Math.max(Math.abs(tee.getX() - lie.getX()), Math.abs(tee.getZ() - lie.getZ())),
                Math.max(Math.abs(basket.getX() - lie.getX()), Math.abs(basket.getZ() - lie.getZ()))
        );
        if (alternateAnchor != null) {
            maxDelta = Math.max(
                    maxDelta,
                    Math.max(Math.abs(alternateAnchor.getX() - lie.getX()), Math.abs(alternateAnchor.getZ() - lie.getZ()))
            );
        }
        return maxDelta;
    }
}
