package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.SafePositionFinder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.mcdg.net.ThrowTrailCompleteSync;

/**
 * Resolves throw landings, tracks pearl flight, and enforces strict landing penalties.
 */
public final class ThrowResolver {
    private static final int MAX_THROW_RESOLUTION_WAIT_TICKS = 320;
    private static final int THROW_RELEASE_GRACE_TICKS = 8;
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final boolean ENABLE_STRICT_LANDING_PENALTIES = true;
    // Near-pin radius for skill progression: within ~10ft (3 blocks) horizontally of the basket.
    private static final int NEAR_PIN_RADIUS_BLOCKS = 3;
    private static final int NEAR_PIN_HEIGHT_TOLERANCE = 4;
    private static final Map<UUID, Integer> LAST_PROCESSED_THROW_TOTAL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_PENDING_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LAST_THROW_PEARL_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_THROW_RELEASE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> LAST_RESOLUTION_REASON = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_DISTANCE_FEET = new ConcurrentHashMap<>();

    // New: Track calculated throws (trajectory-based, no pearl entity)
    private static final Map<UUID, CalculatedThrowData> CALCULATED_THROWS = new ConcurrentHashMap<>();

    // Cached last throw stats for HUD sync on round resume
    private static final Map<UUID, LastThrowStats> LAST_THROW_STATS = new ConcurrentHashMap<>();

    // Data class for calculated throws
    private static record CalculatedThrowData(
            Vec3d landingPos,
            int flightTicks,
            long releaseWorldTime,
            Vec3d[] pathPoints,
            double totalDistanceFt,
            double lateralDriftFt,
            double apexHeightFt,
            ThrowStance stance,
            ReleaseAngle angle
    ) {}

    public record LastThrowStats(
            double totalDistanceFt,
            double lateralDriftFt,
            double apexHeightFt,
            ThrowStance stance,
            ReleaseAngle angle,
            int flightTicks,
            StrictPenaltyType penaltyType,
            int penaltyStrokes,
            String penaltyReason,
            int obCrossingFeet,
            int returnedToFeet
    ) {}

    private ThrowResolver() {
    }

    public static int lastThrowDistanceFeetForPlayer(UUID playerId) {
        return LAST_THROW_DISTANCE_FEET.getOrDefault(playerId, 0);
    }

    public static LastThrowStats lastThrowStatsForPlayer(UUID playerId) {
        return LAST_THROW_STATS.get(playerId);
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
        CALCULATED_THROWS.clear();
        LAST_THROW_STATS.clear();
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
            ActiveCourseManager courseManager,
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

        // Check for calculated throw landing position (trajectory-based system)
        // Peek at data without removing so pathPoints survive for basket detection
        CalculatedThrowData calc = CALCULATED_THROWS.get(player.getUuid());
        Vec3d calcLanding = null;
        Vec3d[] pathPoints = null;
        if (calc != null) {
            long elapsedTicks = world.getTime() - calc.releaseWorldTime();
            if (elapsedTicks >= calc.flightTicks()) {
                calcLanding = calc.landingPos();
                pathPoints = calc.pathPoints();
                CALCULATED_THROWS.remove(player.getUuid());
            }
        }

        BlockPos rawCalcFeet = null;
        if (calcLanding != null) {
            rawCalcFeet = new BlockPos((int) Math.round(calcLanding.x), (int) Math.round(calcLanding.y), (int) Math.round(calcLanding.z));

            // Do NOT teleport here yet. We classify the raw landing first so water
            // landings are resolved to the surface instead of being snapped to a cave.
            currentFeet = rawCalcFeet;

            // Remove temporary landing ticket now that the player is here
            RoundChunkLoader.removeThrowLandingTicket(world, rawCalcFeet);

            McdgMod.LOGGER.info(
                    "Calculated landing resolved | player={} pos={},{},{} distFromThrow={}ft source=CALCULATED_THROW",
                    player.getGameProfile().getName(),
                    rawCalcFeet.getX(), rawCalcFeet.getY(), rawCalcFeet.getZ(),
                    String.format("%.1f", (double) DistanceUtils.distanceFeet(throwLie, rawCalcFeet))
            );
        }

        // The raw landing position is the authoritative point for classification.
        // For calculated throws this is the computed landing; for pearl throws it is
        // the player's position after the pearl teleport.
        BlockPos rawLandingFeet = rawCalcFeet != null ? rawCalcFeet : currentFeet;

        // Made shot detection: check BEFORE applying any penalties so a successful
        // basket shot is never penalized regardless of landing terrain.
        boolean madeShot = isDiscThroughBasket(pathPoints, throwLie, currentFeet, basket)
                || isCloseProximityMake(throwLie, rawCalcFeet, currentFeet, basket);

        BlockPos resultingLie;
        BlockPos firstOutCrossing = null;
        StrictPenaltyType landingPenalty = StrictPenaltyType.NONE;
        String penaltyReason = "In Bounds";
        int penaltyStrokes = 0;

        if (madeShot) {
            // Successful basket shot - no penalties apply, lie is set to basket
            resultingLie = basket.up();
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY(), resultingLie.getZ() + 0.5);
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
            McdgMod.LOGGER.info("Made shot detected | player={} hole={}", player.getGameProfile().getName(), state.currentHole());
        } else if (OutOfBoundsClassifier.isFluidPenaltyZone(world, rawLandingFeet)) {
            // Water landing: resolve to the last in-bounds position on the surface.
            resultingLie = resolveFluidLandingToSurface(
                    world, throwLie, rawLandingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = rulesetManager.isStrict() ? StrictPenaltyType.OB : StrictPenaltyType.NONE;
            penaltyReason = "Water";
            penaltyStrokes = landingPenalty == StrictPenaltyType.OB
                    ? rulesetManager.strictObPenaltyStrokes()
                    : 0;
            if (penaltyStrokes > 0) {
                roundStateManager.applyPenaltyStrokes(player.getUuid(), penaltyStrokes);
            }
            state = roundStateManager.markLastThrowPenalty(
                    player.getUuid(), landingPenalty != StrictPenaltyType.NONE).orElse(state);

            // Apply hazard effects if the resolved surface lie is itself a hazard.
            HazardType hazardType = HazardManager.getHazardType(world, resultingLie);
            if (hazardType != HazardType.NONE) {
                HazardBehavior behavior = HazardManager.getHazardBehavior(hazardType);
                HazardManager.applyHazardEffects(player, behavior, hazardType, roundStateManager);
            }

            // Find safe position before teleporting to avoid suffocation
            BlockPos safePos = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
            player.teleport(
                    safePos.getX() + 0.5,
                    safePos.getY(),
                    safePos.getZ() + 0.5
            );

            if (strictFlowDebug) {
                McdgMod.LOGGER.info(
                        "Fluid landing resolved | player={} hole={} total={} throwLie={} rawLanding={} resultingLie={} penalty={} reason={}",
                        player.getGameProfile().getName(),
                        state.currentHole(),
                        state.totalStrokes(),
                        OutOfBoundsClassifier.formatPos(throwLie),
                        OutOfBoundsClassifier.formatPos(rawLandingFeet),
                        OutOfBoundsClassifier.formatPos(resultingLie),
                        landingPenalty.name(),
                        penaltyReason
                );
            }
            if (hudScoringDebug) {
                player.sendMessage(Text.literal(
                        "Strict dbg | landing=" + landingPenalty.name()
                                + " | firstOut=" + OutOfBoundsClassifier.formatPos(firstOutCrossing)
                                + " | safeLie=" + OutOfBoundsClassifier.formatPos(resultingLie)
                ), false);
            }
        } else if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            // Classify the raw landing position (same for both calculated throws and pearls)
            BlockPos landingFeet = SafePositionFinder.findNearestStandableFeet(world, rawLandingFeet);
            OutOfBoundsClassifier.PenaltyDetail currentFeetDetail = OutOfBoundsClassifier.classifyWithDetail(
                    world, rawLandingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            OutOfBoundsClassifier.PenaltyDetail standableFeetDetail = OutOfBoundsClassifier.classifyWithDetail(
                    world, landingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
            landingPenalty = combinePenalty(currentFeetDetail.type(), standableFeetDetail.type());
            penaltyReason = currentFeetDetail.reason();
            if (landingPenalty != StrictPenaltyType.NONE) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing classified | player={} hole={} total={} throwLie={} rawLanding={} landingFeet={} currentPenalty={} standablePenalty={} penalty={} reason={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            OutOfBoundsClassifier.formatPos(throwLie),
                            OutOfBoundsClassifier.formatPos(rawLandingFeet),
                            OutOfBoundsClassifier.formatPos(landingFeet),
                            currentFeetDetail.type().name(),
                            standableFeetDetail.type().name(),
                            landingPenalty.name(),
                            penaltyReason
                    );
                }
                if (landingPenalty == StrictPenaltyType.OB) {
                    // Find last in-bounds position (same for both calculated throws and pearls)
                    CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                            world,
                            throwLie,
                            rawLandingFeet,
                            currentHole,
                            tee,
                            basket,
                            alternateAnchor,
                            rulesetManager
                    );
                    resultingLie = crossing.safeLie();
                    firstOutCrossing = crossing.firstOutCrossing();
                } else {
                    resultingLie = rawLandingFeet.toImmutable();
                }

                penaltyStrokes = landingPenalty == StrictPenaltyType.OB
                        ? rulesetManager.strictObPenaltyStrokes()
                        : rulesetManager.strictHazardPenaltyStrokes();
                if (penaltyStrokes > 0) {
                    roundStateManager.applyPenaltyStrokes(player.getUuid(), penaltyStrokes);
                }

                // Determine hazard type for effect application.
                // For HAZARD penalty, resultingLie is rawLandingFeet, so reuse the classification result.
                // For OB penalty, resultingLie is the safe lie from crossing resolution, so scan it.
                BlockPos finalResultingLie = resultingLie;
                HazardType hazardType;
                if (landingPenalty == StrictPenaltyType.HAZARD) {
                    hazardType = currentFeetDetail.hazardType()
                            .or(() -> standableFeetDetail.hazardType())
                            .orElseGet(() -> HazardManager.getHazardType(world, finalResultingLie));
                } else if (landingPenalty == StrictPenaltyType.OB) {
                    hazardType = HazardManager.getHazardType(world, finalResultingLie);
                } else {
                    hazardType = HazardType.NONE;
                }

                if (hazardType != HazardType.NONE) {
                    HazardBehavior behavior = HazardManager.getHazardBehavior(hazardType);
                    HazardManager.applyHazardEffects(player, behavior, hazardType, roundStateManager);
                }
            } else {
                resultingLie = landingFeet;
            }

            // Find safe position before teleporting to avoid suffocation
            BlockPos safePos = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
            player.teleport(
                    safePos.getX() + 0.5,
                    safePos.getY(),
                    safePos.getZ() + 0.5
            );

            state = roundStateManager.markLastThrowPenalty(
                    player.getUuid(), landingPenalty != StrictPenaltyType.NONE).orElse(state);

            if (hudScoringDebug) {
                player.sendMessage(Text.literal(
                        "Strict dbg | landing=" + landingPenalty.name()
                                + " | firstOut=" + OutOfBoundsClassifier.formatPos(firstOutCrossing)
                                + " | safeLie=" + OutOfBoundsClassifier.formatPos(resultingLie)
                ), false);
            }
        } else {
            // Non-strict, non-fluid landing
            landingPenalty = StrictPenaltyType.NONE;
            penaltyReason = "In Bounds";
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
            McdgMod.LOGGER.info("Non-strict landing resolved | player={} hole={}", player.getGameProfile().getName(), state.currentHole());
            resultingLie = SafePositionFinder.findNearestStandableFeet(world, rawLandingFeet);

            // Calculated throws need to be teleported to the resolved landing position.
            if (rawCalcFeet != null) {
                BlockPos safePos = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
                player.teleport(
                        safePos.getX() + 0.5,
                        safePos.getY(),
                        safePos.getZ() + 0.5
                );
            }
        }

        // Basket make already handled above; this block removed as part of Option A refactor.

        // Basket body hits (above the make-zone) should bounce to the ring with a CLANK cue.
        if (!madeShot && shouldBounceOffBasketStructure(resultingLie, basket)) {
            BlockPos bounced = basketBouncePosition(world, basket);
            resultingLie = bounced;
            player.teleport(resultingLie.getX() + 0.5, resultingLie.getY(), resultingLie.getZ() + 0.5);
            GolfTitleMessenger.sendClankTitle(player);
        }

        if (!madeShot) {
            resultingLie = SafePositionFinder.findNearestStandableFeet(world, resultingLie);
            if (!SafePositionFinder.isStandableFeet(world, resultingLie)) {
                resultingLie = SafePositionFinder.findNearestStandableFeet(world, throwLie);
            }
        }

        roundStateManager.updateLie(player.getUuid(), resultingLie);

        // Record near-pin for skill progression if the throw landed close to the basket but did not make it.
        if (!madeShot && isNearPin(resultingLie, basket)) {
            PlayerSkillManager.recordNearPin(player);
        }

        // Use calculated trajectory distance when available so Throw HUD and Round HUD "Last" match.
        // Falls back to block-to-block distance for legacy pearl throws.
        int lastThrowDistance = calc != null
                ? (int) Math.round(calc.totalDistanceFt())
                : DistanceUtils.distanceFeet(throwLie, resultingLie);
        LAST_THROW_DISTANCE_FEET.put(player.getUuid(), lastThrowDistance);

        int obCrossingFeet = firstOutCrossing != null ? DistanceUtils.distanceFeet(throwLie, firstOutCrossing) : 0;
        int returnedToFeet = DistanceUtils.distanceFeet(throwLie, resultingLie);

        // Send trail complete packet to all active participants for final stats
        if (calc != null) {
            ThrowTrailCompleteSync.Payload completePayload = new ThrowTrailCompleteSync.Payload(
                    player.getUuid(),
                    calc.totalDistanceFt(),
                    calc.lateralDriftFt(),
                    calc.apexHeightFt(),
                    landingPenalty,
                    penaltyStrokes,
                    penaltyReason,
                    obCrossingFeet,
                    returnedToFeet
            );
            for (UUID participantId : courseManager.getActiveParticipantIds()) {
                ServerPlayerEntity participant = player.getServer().getPlayerManager().getPlayer(participantId);
                if (participant != null) {
                    ServerPlayNetworking.send(participant, completePayload);
                }
            }
        }
        LAST_THROW_STATS.put(player.getUuid(), new LastThrowStats(
                calc != null ? calc.totalDistanceFt() : lastThrowDistance,
                calc != null ? calc.lateralDriftFt() : 0.0,
                calc != null ? calc.apexHeightFt() : 0.0,
                calc != null ? calc.stance() : ThrowStance.OVERHAND,
                calc != null ? calc.angle() : ReleaseAngle.FLAT,
                calc != null ? calc.flightTicks() : 0,
                landingPenalty,
                penaltyStrokes,
                penaltyReason,
                obCrossingFeet,
                returnedToFeet
        ));
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
     * Register a calculated throw (trajectory-based, no pearl entity).
     * Used by the new trajectory calculation system.
     */
    static void registerCalculatedThrow(UUID playerId, long worldTime, Vec3d landingPos, int flightTicks, Vec3d[] pathPoints, double totalDistanceFt, double lateralDriftFt, double apexHeightFt, ThrowStance stance, ReleaseAngle angle) {
        CALCULATED_THROWS.put(playerId, new CalculatedThrowData(landingPos, flightTicks, worldTime, pathPoints, totalDistanceFt, lateralDriftFt, apexHeightFt, stance, angle));
        LAST_THROW_RELEASE_TICK.put(playerId, worldTime);
        LAST_THROW_PENDING_TICKS.remove(playerId);
        // No pearl UUID for calculated throws
        LAST_THROW_PEARL_UUID.remove(playerId);

        McdgMod.LOGGER.info(
                "Calculated throw registered | player={} flightTicks={} landing={},{},{} dist={}ft",
                playerId,
                flightTicks,
                String.format("%.1f", landingPos.x),
                String.format("%.1f", landingPos.y),
                String.format("%.1f", landingPos.z),
                String.format("%.1f", landingPos.distanceTo(new Vec3d(landingPos.x, landingPos.y, landingPos.z)) * 3.0)
        );
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

    private static boolean isNearPin(BlockPos lie, BlockPos basket) {
        int dx = Math.abs(lie.getX() - basket.getX());
        int dz = Math.abs(lie.getZ() - basket.getZ());
        int dy = Math.abs(lie.getY() - basket.getY());
        return dx <= NEAR_PIN_RADIUS_BLOCKS && dz <= NEAR_PIN_RADIUS_BLOCKS && dy <= NEAR_PIN_HEIGHT_TOLERANCE;
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
        // Check for calculated throws first (new trajectory system)
        CalculatedThrowData calc = CALCULATED_THROWS.get(player.getUuid());
        if (calc != null) {
            long elapsedTicks = world.getTime() - calc.releaseWorldTime();
            boolean inFlight = elapsedTicks < calc.flightTicks();
            if (inFlight) {
                return true; // Calculated throw is still "flying"
            }
            // Flight complete - calculated throw is ready for resolution
            return false;
        }

        // Legacy: Check for pearl entity (old system, backward compatibility)
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

    /**
     * Resolves a water/fluid landing to the nearest in-bounds land on the surface.
     * Traces the flight path back toward the throw lie to find the last in-bounds
     * position before the water, then snaps that position to a standable block.
     */
    private static BlockPos resolveFluidLandingToSurface(
            ServerWorld world,
            BlockPos throwLie,
            BlockPos rawLandingFeet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        CrossingResolution crossing = findLastSolidBeforeOutCrossing(
                world, throwLie, rawLandingFeet, currentHole, tee, basket, alternateAnchor, rulesetManager);
        BlockPos surfaceLie = crossing.safeLie();

        // If the throw started over water (throwLie itself was in water), pull to the nearest surface land.
        if (OutOfBoundsClassifier.isFluidPenaltyZone(world, surfaceLie)) {
            surfaceLie = findNearestSurfaceLand(world, surfaceLie, 6);
        }

        BlockPos standable = SafePositionFinder.findNearestStandableFeet(world, surfaceLie);
        if (SafePositionFinder.isStandableFeet(world, standable)) {
            return standable;
        }

        // Absolute fallback: return the throw lie if no surface land can be found.
        return SafePositionFinder.findNearestStandableFeet(world, throwLie);
    }

    /**
     * Finds the nearest standable land position at or above the given underwater position.
     * Scans upward to the top of the fluid column, then searches outward horizontally.
     */
    private static BlockPos findNearestSurfaceLand(ServerWorld world, BlockPos underwaterPos, int maxRadius) {
        BlockPos.Mutable probe = underwaterPos.mutableCopy();
        int maxUp = world.getTopY() - underwaterPos.getY();
        for (int dy = 0; dy <= maxUp; dy++) {
            probe.setY(underwaterPos.getY() + dy);
            if (!world.getFluidState(probe.toImmutable()).isIn(FluidTags.WATER)
                    && !world.getFluidState(probe.toImmutable()).isIn(FluidTags.LAVA)) {
                break;
            }
        }
        BlockPos surfacePos = probe.toImmutable();

        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = surfacePos.add(dx, 0, dz);
                    if (SafePositionFinder.isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                }
            }
        }

        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = surfacePos.up(dy);
            if (SafePositionFinder.isStandableFeet(world, up)) {
                return up;
            }
        }

        return surfacePos;
    }

    private static boolean isDiscThroughBasket(Vec3d[] pathPoints, BlockPos throwLie, BlockPos landingFeet, BlockPos basket) {
        // If we have the actual curved trajectory path, check it against the basket volume.
        if (pathPoints != null && pathPoints.length >= 2) {
            int targetX = basket.getX();
            int targetZ = basket.getZ();
            int minY = basket.getY();
            int maxY = basket.getY() + 2;
            for (int i = 1; i < pathPoints.length; i++) {
                Vec3d p0 = pathPoints[i - 1];
                Vec3d p1 = pathPoints[i];
                if (segmentHitsBasketVolume(p0, p1, targetX, minY, maxY, targetZ)) {
                    return true;
                }
            }
            return false;
        }

        // Fallback: straight-line check for legacy pearl throws
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

    private static boolean segmentHitsBasketVolume(Vec3d p0, Vec3d p1, int targetX, int minY, int maxY, int targetZ) {
        int samples = Math.max(4, (int) Math.ceil(p0.distanceTo(p1) * 2.0));
        for (int s = 1; s <= samples; s++) {
            double t = s / (double) samples;
            double x = p0.x + (p1.x - p0.x) * t;
            double y = p0.y + (p1.y - p0.y) * t;
            double z = p0.z + (p1.z - p0.z) * t;
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            if (bx == targetX && bz == targetZ && by >= minY && by <= maxY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCloseProximityMake(BlockPos throwLie, BlockPos rawCalcFeet, BlockPos currentFeet, BlockPos basket) {
        // If the disc lands on the basket column within the make-zone, it is a make.
        // Check raw calculated landing first (before SafePositionFinder nudges away).
        if (rawCalcFeet != null) {
            int dx = rawCalcFeet.getX() - basket.getX();
            int dz = rawCalcFeet.getZ() - basket.getZ();
            if (dx == 0 && dz == 0) {
                int dy = rawCalcFeet.getY() - basket.getY();
                if (dy >= 0 && dy <= 2) {
                    return true;
                }
            }
        }
        // Fallback: check actual player position (for pearl throws or already-teleported state).
        int dx = currentFeet.getX() - basket.getX();
        int dz = currentFeet.getZ() - basket.getZ();
        if (dx != 0 || dz != 0) {
            return false;
        }
        int dy = currentFeet.getY() - basket.getY();
        return dy >= 0 && dy <= 2;
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

        // Make-zone is basket.y through basket.y + 2; upper basket structure should bounce.
        return liePos.getY() >= (basketPos.getY() + 3);
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
