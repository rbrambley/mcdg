package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.net.HoleMapSync;
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
 * Builds and sends schematic hole map sync payloads to clients.
 * Much lighter than the old MiniMapSyncService: no terrain sampling,
 * no texture building, no map span calculation.
 */
public final class HoleMapSyncService {
    private static final Map<UUID, Integer> LAST_HOLE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_PAYLOAD_HASH = new HashMap<>();
    private static boolean ACTIVE_SENT = false;

    private static final Map<UUID, CachedExtras> EXTRAS_CACHE = new HashMap<>();

    private record CachedExtras(
            BlockPos lie,
            int corridorHalfWidth,
            int[] corridorEntry,
            int[] waterGap
    ) {}

    private HoleMapSyncService() {
    }

    public static void reset() {
        LAST_HOLE.clear();
        LAST_PAYLOAD_HASH.clear();
        EXTRAS_CACHE.clear();
        ACTIVE_SENT = false;
    }

    public static Integer lastHoleForPlayer(UUID playerId) {
        return LAST_HOLE.get(playerId);
    }

    public static void onPlayerDisconnect(UUID playerId) {
        EXTRAS_CACHE.remove(playerId);
        LAST_HOLE.remove(playerId);
        LAST_PAYLOAD_HASH.remove(playerId);
    }

    public static void sendInactive(MinecraftServer server) {
        if (!ACTIVE_SENT) {
            return;
        }
        HoleMapSync.Payload inactive = HoleMapSync.Payload.inactive();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, inactive);
        }
        ACTIVE_SENT = false;
    }

    public static void sync(
            MinecraftServer server,
            ServerPlayerEntity player,
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
            ThrowResolver.LastThrowStats lastThrowStats,
            boolean strictFlowDebug
    ) {
        HoleMapSync.Payload payload = resolveAndBuildPayload(
                player, course, placed, state, currentHole, tee, basket, alternateAnchor,
                corridorHalfWidth, cumulativeParDelta, lastThrowDistanceFeet, lastThrowStats,
                rulesetManager
        );
        UUID playerId = player.getUuid();

        if (strictFlowDebug && (server.getTicks() % 20) == 0) {
            McdgMod.LOGGER.info(
                    "HOLEMAP DEBUG | player={} hole={} lie=({}, {}) tee=({}, {}) basket=({}, {})",
                    player.getGameProfile().getName(),
                    state.currentHole(),
                    payload.lieX(), payload.lieZ(),
                    payload.teeX(), payload.teeZ(),
                    payload.basketX(), payload.basketZ()
            );
        }

        int payloadHash = payload.hashCode();
        if (!LAST_PAYLOAD_HASH.containsKey(playerId) || LAST_PAYLOAD_HASH.get(playerId) != payloadHash) {
            ServerPlayNetworking.send(player, payload);
            LAST_PAYLOAD_HASH.put(playerId, payloadHash);
            LAST_HOLE.put(playerId, state.currentHole());
        }
        ACTIVE_SENT = true;
    }

    public static void forceSync(
            MinecraftServer server,
            ServerPlayerEntity player,
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
            ThrowResolver.LastThrowStats lastThrowStats,
            boolean strictFlowDebug
    ) {
        HoleMapSync.Payload payload = resolveAndBuildPayload(
                player, course, placed, state, currentHole, tee, basket, alternateAnchor,
                corridorHalfWidth, cumulativeParDelta, lastThrowDistanceFeet, lastThrowStats,
                rulesetManager
        );
        UUID playerId = player.getUuid();

        ServerPlayNetworking.send(player, payload);
        LAST_PAYLOAD_HASH.put(playerId, payload.hashCode());
        LAST_HOLE.put(playerId, state.currentHole());
        ACTIVE_SENT = true;
    }

    private static HoleMapSync.Payload resolveAndBuildPayload(
            ServerPlayerEntity player,
            Course course,
            PlacedCourseState placed,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            int corridorHalfWidth,
            int cumulativeParDelta,
            int lastThrowDistanceFeet,
            ThrowResolver.LastThrowStats lastThrowStats,
            TournamentRulesetManager rulesetManager
    ) {
        UUID playerId = player.getUuid();
        ServerWorld world = (ServerWorld) player.getWorld();

        CachedExtras extras = resolveCachedExtras(
                playerId, state.lie(), corridorHalfWidth, tee, basket, alternateAnchor, world
        );
        int[] corridorEntry = extras.corridorEntry();
        int corridorEntryFeet = corridorEntry != null ? corridorEntry[0] : 0;
        int corridorEntryBearing = corridorEntry != null ? corridorEntry[1] : 0;
        int waterGapStartFeet = extras.waterGap()[2] > 0 ? Math.round(extras.waterGap()[0] * 3.28084f) : 0;
        int waterGapEndFeet = extras.waterGap()[2] > 0 ? Math.round(extras.waterGap()[1] * 3.28084f) : 0;
        boolean hasWaterGap = extras.waterGap()[2] > 0;

        float heading = player.getYaw();
        int headingYaw = Math.round(((heading % 360) + 360) % 360);

        return buildPayload(
                course,
                placed,
                currentHole,
                state,
                tee,
                basket,
                corridorHalfWidth,
                cumulativeParDelta,
                lastThrowDistanceFeet,
                corridorEntryFeet,
                corridorEntryBearing,
                waterGapStartFeet,
                waterGapEndFeet,
                hasWaterGap,
                headingYaw,
                lastThrowStats,
                player,
                rulesetManager
        );
    }

    private static CachedExtras resolveCachedExtras(
            UUID playerId,
            BlockPos lie,
            int corridorHalfWidth,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            ServerWorld world
    ) {
        CachedExtras cached = EXTRAS_CACHE.get(playerId);
        if (cached != null && cached.lie().equals(lie) && cached.corridorHalfWidth() == corridorHalfWidth) {
            return cached;
        }
        int[] corridorEntry = tee != null
                ? OutOfBoundsClassifier.nearestForwardCorridorEntry(lie, tee, basket, alternateAnchor, corridorHalfWidth)
                : null;
        int[] waterGap = OutOfBoundsClassifier.findLongestWaterGap(world, lie, basket);
        CachedExtras extras = new CachedExtras(lie, corridorHalfWidth, corridorEntry, waterGap);
        EXTRAS_CACHE.put(playerId, extras);
        return extras;
    }

    private static HoleMapSync.Payload buildPayload(
            Course course,
            PlacedCourseState placed,
            Hole currentHole,
            PlayerRoundState state,
            BlockPos tee,
            BlockPos basket,
            int corridorHalfWidth,
            int cumulativeParDelta,
            int lastThrowDistanceFeet,
            int corridorEntryFeet,
            int corridorEntryBearing,
            int waterGapStartFeet,
            int waterGapEndFeet,
            boolean hasWaterGap,
            int headingYaw,
            ThrowResolver.LastThrowStats lastThrowStats,
            ServerPlayerEntity player,
            TournamentRulesetManager rulesetManager
    ) {
        BlockPos placedTee = placed.holeTees().get(state.currentHole());
        int offsetX = (placedTee != null ? placedTee.getX() : 0) - currentHole.tee().x();
        int offsetZ = (placedTee != null ? placedTee.getZ() : 0) - currentHole.tee().z();

        List<HoleMapSync.FairwaySegmentEntry> segments = new ArrayList<>();
        for (FairwaySegment seg : currentHole.fairwaySegments()) {
            segments.add(new HoleMapSync.FairwaySegmentEntry(
                    seg.startX() + offsetX,
                    seg.startZ() + offsetZ,
                    seg.endX() + offsetX,
                    seg.endZ() + offsetZ,
                    seg.width()
            ));
        }

        String courseWaypointName = course.name() + " " + course.seed();
        BlockPos courseAnchor = resolveTournamentCentralAnchor(
                placed.holeTees().get(1),
                placed.holeBaskets().get(1),
                tee,
                basket
        );

        int displayDistanceFeet = (int) Math.round(
                Math.hypot(basket.getX() - tee.getX(), basket.getZ() - tee.getZ()) * 3.28084
        );

        // Get pre-computed hazard grid (must exist from course placement)
        String courseKey = HoleHazardGridService.courseKey(course.name(), course.seed());
        HoleHazardGridService.CachedHazardGrid hazardGrid = HoleHazardGridService.getCachedGrid(courseKey, state.currentHole());
        if (hazardGrid == null) {
            // On-demand computation for existing courses that were placed before hazard grid computation was added
            McdgMod.LOGGER.info(
                "Computing hazard grid on-demand for course {} hole {} — this is a one-time cost for existing courses.",
                courseKey, state.currentHole()
            );
            hazardGrid = HoleHazardGridService.computeGrid(
                    (ServerWorld) player.getWorld(),
                    currentHole,
                    tee,
                    basket,
                    rulesetManager
            );
            HoleHazardGridService.cacheGrid(courseKey, state.currentHole(), hazardGrid);
        }

        return new HoleMapSync.Payload(
                true,
                state.currentHole(),
                currentHole.par(),
                displayDistanceFeet,
                tee.getX(),
                tee.getZ(),
                basket.getX(),
                basket.getZ(),
                segments,
                corridorHalfWidth,
                currentHole.signatureType().ordinal(),
                hazardGrid.minX(),
                hazardGrid.minZ(),
                hazardGrid.width(),
                hazardGrid.height(),
                hazardGrid.gridData(),
                state.lie().getX(),
                state.lie().getZ(),
                headingYaw,
                Math.max(1, state.holeStrokes() + 1),
                state.totalStrokes(),
                cumulativeParDelta,
                lastThrowDistanceFeet,
                corridorEntryFeet,
                corridorEntryBearing,
                waterGapStartFeet,
                waterGapEndFeet,
                hasWaterGap,
                courseWaypointName,
                courseAnchor.getX(),
                courseAnchor.getZ(),
                lastThrowStats != null,
                lastThrowStats != null ? lastThrowStats.totalDistanceFt() : 0.0,
                lastThrowStats != null ? lastThrowStats.lateralDriftFt() : 0.0,
                lastThrowStats != null ? lastThrowStats.stance() : ThrowStance.OVERHAND,
                lastThrowStats != null ? lastThrowStats.angle() : ReleaseAngle.FLAT,
                lastThrowStats != null ? lastThrowStats.flightTicks() : 0,
                lastThrowStats != null ? lastThrowStats.penaltyType() : StrictPenaltyType.NONE,
                lastThrowStats != null ? lastThrowStats.penaltyStrokes() : 0,
                lastThrowStats != null ? lastThrowStats.penaltyReason() : "",
                lastThrowStats != null ? lastThrowStats.obCrossingFeet() : 0,
                lastThrowStats != null ? lastThrowStats.returnedToFeet() : 0
        );
    }

    private static BlockPos resolveTournamentCentralAnchor(BlockPos preferredTee, BlockPos preferredBasket, BlockPos fallbackTee, BlockPos fallbackBasket) {
        BlockPos teeAnchor = preferredTee == null ? fallbackTee : preferredTee;
        if (teeAnchor == null) {
            return fallbackBasket == null ? BlockPos.ORIGIN : fallbackBasket;
        }
        BlockPos basketAnchor = preferredBasket == null ? fallbackBasket : preferredBasket;
        if (basketAnchor == null) {
            return teeAnchor;
        }
        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0] * 12, 0, back[1] * 12);
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
}
