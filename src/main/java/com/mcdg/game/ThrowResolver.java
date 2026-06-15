package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.SafePositionFinder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

/**
 * Resolves throw landings, tracks pearl flight, and enforces strict landing penalties.
 */
public final class ThrowResolver {
    private static final int MAX_THROW_RESOLUTION_WAIT_TICKS = 320;
    private static final int THROW_RELEASE_GRACE_TICKS = 8;
    // Proximity make radius: flat putts within this distance that hit the basket column count as makes
    private static final int PROXIMITY_MAKE_RADIUS_BLOCKS = 3;
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final boolean ENABLE_STRICT_LANDING_PENALTIES = true;
    private static final Map<UUID, Integer> LAST_PROCESSED_THROW_TOTAL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_PENDING_TICKS = new HashMap<>();
    private static final Map<UUID, UUID> LAST_THROW_PEARL_UUID = new HashMap<>();
    private static final Map<UUID, Long> LAST_THROW_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, String> LAST_RESOLUTION_REASON = new HashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_DISTANCE_FEET = new HashMap<>();

    private ThrowResolver() {
    }

    public static int lastThrowDistanceFeetForPlayer(UUID playerId) {
        return LAST_THROW_DISTANCE_FEET.getOrDefault(playerId, 0);
    }

    public static void recordResolutionReason(UUID playerId, String reason) {
        LAST_RESOLUTION_REASON.put(playerId, reason);
    }

    public static void reset() {
        LAST_PROCESSED_THROW_TOTAL.clear();
        LAST_THROW_PENDING_TICKS.clear();
        LAST_THROW_PEARL_UUID.clear();
        LAST_THROW_RELEASE_TICK.clear();
        LAST_RESOLUTION_REASON.clear();
        LAST_THROW_DISTANCE_FEET.clear();
    }


    public static PlayerRoundState resolve(
            ServerPlayerEntity player,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
                boolean hudScoringDebug,
                boolean strictFlowDebug
    ) {
        Integer processedTotalObj = LAST_PROCESSED_THROW_TOTAL.get(player.getUuid());
        int processedTotal;
        if (processedTotalObj == null) {
            // First tracker tick for this player in the active round.
            // If no throws yet, initialize and wait. If throws already happened,
            // process the latest throw instead of skipping it.
            if (state.totalStrokes() == 0) {
                LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), 0);
                return state;
            }
            processedTotal = state.totalStrokes() - 1;
        } else {
            processedTotal = processedTotalObj;
        }

        if (state.totalStrokes() <= processedTotal) {
            LAST_RESOLUTION_REASON.put(player.getUuid(), "NO_NEW_THROW");
            return state;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos throwLie = state.lie();
        BlockPos currentFeet = player.getBlockPos();

        // Wait for the exact throw pearl (plus a short release grace window) before resolving lie.
        // This avoids stale, older pearls from keeping resolution pinned at the tee.
        boolean trackedPearlInFlight = hasTrackedPearlInFlight(world, player, throwLie);
        boolean withinReleaseGrace = isWithinThrowReleaseGrace(world, player.getUuid());
        if (trackedPearlInFlight || withinReleaseGrace) {
            int pendingTicks = LAST_THROW_PENDING_TICKS.merge(player.getUuid(), 1, Integer::sum);
            if (pendingTicks <= MAX_THROW_RESOLUTION_WAIT_TICKS) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing wait | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} inFlightPearl={} releaseGrace={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            OutOfBoundsClassifier.formatPos(throwLie),
                            OutOfBoundsClassifier.formatPos(currentFeet),
                            pendingTicks,
                            trackedPearlInFlight,
                            withinReleaseGrace
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), trackedPearlInFlight ? "WAITING_TRACKED_PEARL" : "WAITING_RELEASE_GRACE");
                return state;
            }

            // If the exact throw pearl is still in flight after timeout, keep waiting instead of
            // force-resolving to the throw lie. This avoids false strict blocks on very long throws.
            if (trackedPearlInFlight) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.warn(
                            "Strict landing long-flight wait extension | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} trackedPearlStillInFlight=true",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            OutOfBoundsClassifier.formatPos(throwLie),
                            OutOfBoundsClassifier.formatPos(currentFeet),
                            pendingTicks
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), "WAITING_TRACKED_PEARL_LONG_FLIGHT");
                return state;
            }

            McdgMod.LOGGER.warn(
                    "Forcing throw landing resolution after {} ticks with in-flight pearl still present | player={} hole={} throwLie={} {} {}",
                    pendingTicks,
                    player.getGameProfile().getName(),
                    state.currentHole(),
                    throwLie.getX(),
                    throwLie.getY(),
                    throwLie.getZ()
            );
        }
        LAST_THROW_PENDING_TICKS.remove(player.getUuid());

        BlockPos landingFeet = SafePositionFinder.findNearestStandableFeet(world, currentFeet);

        BlockPos resultingLie = landingFeet;
        BlockPos firstOutCrossing = null;
        StrictPenaltyType landingPenalty = StrictPenaltyType.NONE;
        if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            StrictPenaltyType currentFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, currentFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            StrictPenaltyType standableFeetPenalty = OutOfBoundsClassifier.classifyOutType(world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = combinePenalty(currentFeetPenalty, standableFeetPenalty);
            if (landingPenalty != StrictPenaltyType.NONE) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing classified | player={} hole={} total={} throwLie={} currentFeet={} landingFeet={} currentPenalty={} standablePenalty={} penalty={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            OutOfBoundsClassifier.formatPos(throwLie),
                            OutOfBoundsClassifier.formatPos(currentFeet),
                            OutOfBoundsClassifier.formatPos(landingFeet),
                            currentFeetPenalty.name(),
                            standableFeetPenalty.name(),
                            landingPenalty.name()
                    );
                }
                if (landingPenalty == StrictPenaltyType.OB) {
                    CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                            world,
                            throwLie,
                            currentFeet,
                            currentHole,
                            tee,
                            basket,
                                alternateAnchor,
                            rulesetManager
                    );
                    resultingLie = crossing.safeLie();
                    firstOutCrossing = crossing.firstOutCrossing();
                } else {
                    resultingLie = currentFeet.toImmutable();
                }

                int penaltyStrokes = landingPenalty == StrictPenaltyType.OB
                        ? rulesetManager.strictObPenaltyStrokes()
                        : rulesetManager.strictHazardPenaltyStrokes();
                if (penaltyStrokes > 0) {
                    roundStateManager.applyPenaltyStrokes(player.getUuid(), penaltyStrokes);
                }

                player.teleport(
                        resultingLie.getX() + 0.5,
                        resultingLie.getY() + 1.0,
                        resultingLie.getZ() + 0.5
                );

                String label = landingPenalty == StrictPenaltyType.OB ? "OB" : "Hazard";
                String penaltyText = landingPenalty == StrictPenaltyType.OB
                        ? "Returned to last in-bounds solid block."
                        : "Play next throw from hazard lie.";
                player.sendMessage(
                    Text.literal(label + " landing in strict mode: +" + penaltyStrokes + " stroke. " + penaltyText),
                    true
                );
                GolfTitleMessenger.sendStrictPenaltyTitle(player, landingPenalty, penaltyStrokes);
                state = roundStateManager.markLastThrowPenalty(player.getUuid(), true).orElse(state);
                }

                if (hudScoringDebug) {
                player.sendMessage(Text.literal(
                        "Strict dbg | landing=" + landingPenalty.name()
                                + " | firstOut=" + OutOfBoundsClassifier.formatPos(firstOutCrossing)
                                + " | safeLie=" + OutOfBoundsClassifier.formatPos(resultingLie)
                ), false);
            }
        }

        if (landingPenalty == StrictPenaltyType.NONE) {
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
        }

        // Made shot: pearl path passed through the hopper (Y+1) at the basket column.
        // Made shot: pearl path passed through the hopper (Y+1) at the basket column,
        // or flat putt from very close range that lands on the basket column.
        boolean madeShot = isDiscThroughBasket(throwLie, currentFeet, basket)
                || isCloseProximityMake(throwLie, currentFeet, basket);
        if (madeShot) {
            resultingLie = basket.up();
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);
        }

        // Basket body hits (above the make-zone) should bounce to the ring with a CLANK cue.
        if (!madeShot && shouldBounceOffBasketStructure(resultingLie, basket)) {
            BlockPos bounced = basketBouncePosition(world, basket);
            resultingLie = bounced;
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY() + 1.0, resultingLie.getZ() + 0.5);
            GolfTitleMessenger.sendClankTitle(player);
        }

        if (!madeShot) {
            resultingLie = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
            if (!SafePositionFinder.isStandableFeet(world, resultingLie)) {
                resultingLie = SafePositionFinder.findNearestStandableFeet(world, throwLie);
            }
        }

        roundStateManager.updateLie(player.getUuid(), resultingLie);
        LAST_THROW_DISTANCE_FEET.put(player.getUuid(), DistanceUtils.distanceFeet(throwLie, resultingLie));
        LieMarkerService.updateLieMarker(player, resultingLie);
        PlayerRoundState updated = roundStateManager.getState(player.getUuid()).orElse(state);
        if (strictFlowDebug) {
            McdgMod.LOGGER.info(
                    "Strict landing resolved | player={} hole={} totalBefore={} totalAfter={} throwLie={} resultingLie={} penalty={} lastPenalty={}",
                    player.getGameProfile().getName(),
                    updated.currentHole(),
                    state.totalStrokes(),
                    updated.totalStrokes(),
                    OutOfBoundsClassifier.formatPos(throwLie),
                    OutOfBoundsClassifier.formatPos(resultingLie),
                    landingPenalty.name(),
                    updated.lastThrowPenalty()
            );
        }
        LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), updated.totalStrokes());
        LAST_THROW_PEARL_UUID.remove(player.getUuid());
        LAST_THROW_RELEASE_TICK.remove(player.getUuid());
        LAST_RESOLUTION_REASON.put(player.getUuid(), "RESOLVED");
        return updated;
    }

    static void registerThrowRelease(UUID playerId, UUID pearlId, long worldTime) {
        LAST_THROW_PEARL_UUID.put(playerId, pearlId);
        LAST_THROW_RELEASE_TICK.put(playerId, worldTime);
        LAST_THROW_PENDING_TICKS.remove(playerId);
    }

    /**
     * Force clear the tracked pearl for a player. Called by DiscFlightSimulator when max flight time is exceeded.
     * This allows ThrowResolver to proceed with resolution even if the pearl is in unloaded chunks.
     */
    public static void forceClearTrackedPearl(UUID playerId) {
        if (LAST_THROW_PEARL_UUID.containsKey(playerId)) {
            McdgMod.LOGGER.info("Force clearing tracked pearl for player {} (flight timeout)", playerId);
            LAST_THROW_PEARL_UUID.remove(playerId);
        }
    }

    static boolean isThrowResolutionPending(UUID playerId, int totalStrokes) {
        if (totalStrokes <= 0) {
            return false;
        }

        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        if (processedTotal == null) {
            return true;
        }

        return processedTotal < totalStrokes;
    }

    static String strictThrowGateDebugSnapshot(UUID playerId, int totalStrokes) {
        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        Integer pendingTicks = LAST_THROW_PENDING_TICKS.get(playerId);
        String reason = LAST_RESOLUTION_REASON.getOrDefault(playerId, "UNKNOWN");
        boolean pending = totalStrokes > 0 && (processedTotal == null || processedTotal < totalStrokes);
        return "pending=" + pending
                + " totalStrokes=" + totalStrokes
                + " processedTotal=" + (processedTotal == null ? "-" : processedTotal)
                + " pendingTicks=" + (pendingTicks == null ? "-" : pendingTicks)
                + " lastReason=" + reason;
    }

    private static StrictPenaltyType combinePenalty(StrictPenaltyType first, StrictPenaltyType second) {
        if (first == StrictPenaltyType.OB || second == StrictPenaltyType.OB) {
            return StrictPenaltyType.OB;
        }
        if (first == StrictPenaltyType.HAZARD || second == StrictPenaltyType.HAZARD) {
            return StrictPenaltyType.HAZARD;
        }
        return StrictPenaltyType.NONE;
    }

    private static boolean hasTrackedPearlInFlight(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        UUID trackedPearlId = LAST_THROW_PEARL_UUID.get(player.getUuid());
        if (trackedPearlId == null) {
            return false;
        }

        Box search = new Box(origin).expand(384.0, 192.0, 384.0);
        return !world.getEntitiesByClass(
                EnderPearlEntity.class,
                search,
                pearl -> trackedPearlId.equals(pearl.getUuid()) && !pearl.isRemoved()
        ).isEmpty();
    }

    private static boolean isWithinThrowReleaseGrace(ServerWorld world, UUID playerId) {
        Long releaseTick = LAST_THROW_RELEASE_TICK.get(playerId);
        if (releaseTick == null) {
            return false;
        }
        return (world.getTime() - releaseTick) <= THROW_RELEASE_GRACE_TICKS;
    }

    private static CrossingResolution findLastSolidBeforeOutCrossing(
            ServerWorld world,
            BlockPos throwLie,
            BlockPos landingFeet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        BlockPos start = SafePositionFinder.findNearestStandableFeet(world, throwLie);
        BlockPos end = landingFeet;
        BlockPos lastInBoundsSolid = start;
        BlockPos firstOut = null;

        int distance = Math.max(1, DistanceUtils.manhattanDistance(start, end));
        int samples = Math.max(24, distance * 4);
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) Math.floor((start.getX() + 0.5) + ((end.getX() + 0.5 - (start.getX() + 0.5)) * t));
            int y = (int) Math.floor((start.getY() + 0.5) + ((end.getY() + 0.5 - (start.getY() + 0.5)) * t));
            int z = (int) Math.floor((start.getZ() + 0.5) + ((end.getZ() + 0.5 - (start.getZ() + 0.5)) * t));
            BlockPos probeRaw = new BlockPos(x, y, z);

            if (OutOfBoundsClassifier.classifyOutType(world, probeRaw, currentHole, tee, basket, alternateAnchor, rulesetManager) != StrictPenaltyType.NONE) {
                if (firstOut == null) {
                    firstOut = probeRaw.toImmutable();
                }
                continue;
            }

            BlockPos standableProbe = SafePositionFinder.findNearestStandableFeet(world, probeRaw);
            if (SafePositionFinder.isStandableFeet(world, standableProbe)
                    && OutOfBoundsClassifier.classifyOutType(world, standableProbe, currentHole, tee, basket, alternateAnchor, rulesetManager) == StrictPenaltyType.NONE) {
                lastInBoundsSolid = standableProbe;
            }
        }

        return new CrossingResolution(lastInBoundsSolid.toImmutable(), firstOut);
    }

    private static boolean isDiscThroughBasket(BlockPos throwLie, BlockPos landingFeet, BlockPos basket) {
        int targetX = basket.getX();
        int targetZ = basket.getZ();
        int targetY = basket.getY() + 1;
        int distance = Math.max(1, DistanceUtils.manhattanDistance(throwLie, landingFeet));
        int samples = Math.max(24, distance * 4);
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) Math.floor((throwLie.getX() + 0.5) + ((landingFeet.getX() + 0.5 - (throwLie.getX() + 0.5)) * t));
            int y = (int) Math.floor((throwLie.getY() + 0.5) + ((landingFeet.getY() + 0.5 - (throwLie.getY() + 0.5)) * t));
            int z = (int) Math.floor((throwLie.getZ() + 0.5) + ((landingFeet.getZ() + 0.5 - (throwLie.getZ() + 0.5)) * t));
            if (x == targetX && z == targetZ && y == targetY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCloseProximityMake(BlockPos throwLie, BlockPos landingFeet, BlockPos basket) {
        // Must land on the basket column (same X/Z as basket)
        if (landingFeet.getX() != basket.getX() || landingFeet.getZ() != basket.getZ()) {
            return false;
        }
        // Check if throw started within proximity radius (horizontal distance only)
        int dx = throwLie.getX() - basket.getX();
        int dz = throwLie.getZ() - basket.getZ();
        int horizontalDistSq = dx * dx + dz * dz;
        int radiusSq = PROXIMITY_MAKE_RADIUS_BLOCKS * PROXIMITY_MAKE_RADIUS_BLOCKS;
        return horizontalDistSq <= radiusSq;
    }

    private static boolean shouldBounceOffBasketStructure(BlockPos liePos, BlockPos basketPos) {
        if (liePos == null || basketPos == null) {
            return false;
        }

        int dx = Math.abs(liePos.getX() - basketPos.getX());
        int dz = Math.abs(liePos.getZ() - basketPos.getZ());
        if (dx != 0 || dz != 0) {
            return false;
        }

        // Made basket remains hopper + one block above; upper basket structure should bounce.
        return liePos.getY() >= (basketPos.getY() + 2);
    }

    private static BlockPos basketBouncePosition(ServerWorld world, BlockPos basket) {
        int[] offsets = {1, -1, 2, -2};
        for (int dz : offsets) {
            for (int dx : offsets) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist < 1 || dist > 3) continue;
                BlockPos candidate = SafePositionFinder.findNearestStandableFeet(world,
                        new BlockPos(basket.getX() + dx, basket.getY(), basket.getZ() + dz));
                if (candidate != null && DistanceUtils.manhattanDistance(candidate, basket) >= 1) {
                    return candidate;
                }
            }
        }
        // Absolute fallback: one block north at basket height.
        return basket.north();
    }

}
