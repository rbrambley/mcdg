package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Hole;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class RoutePolicyHelper {
    private RoutePolicyHelper() {
    }

    private static final int PAR5_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR5_MAX_WATER_CARRY;
    private static final int PAR34_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR34_MAX_WATER_CARRY;
    private static final int ROUTE_POLICY_MAX_RETRIES = CoursePlacementConfig.RoutePolicy.MAX_RETRIES;
    private static final int TEE_MAX_DIRECT_CARRY_GAP = CoursePlacementConfig.Tee.MAX_DIRECT_CARRY_GAP;
    private static final int ALT_FAIRWAY_TARGET_ROUTE_GAP = CoursePlacementConfig.AltFairway.TARGET_ROUTE_GAP;
    private static final int ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ALT_FAIRWAY_ANCHOR;
    private static final int ALT_FAIRWAY_FIRST_LEG_MAX_GAP = CoursePlacementConfig.AltFairway.FIRST_LEG_MAX_GAP;
    private static final int ALT_FAIRWAY_FIRST_LEG_MAX_GAP_FALLBACK = CoursePlacementConfig.AltFairway.FIRST_LEG_MAX_GAP_FALLBACK;
    private static final int ALT_FAIRWAY_MIN_ADVANCE = CoursePlacementConfig.AltFairway.MIN_ADVANCE;
    private static final int ALT_FAIRWAY_MAX_FIRST_LEG = CoursePlacementConfig.AltFairway.MAX_FIRST_LEG;
    private static final int ALT_FAIRWAY_EMERGENCY_ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ALT_FAIRWAY_EMERGENCY_ANCHOR;
    private static final int ALT_FAIRWAY_EMERGENCY_MAX_FIRST_LEG = CoursePlacementConfig.AltFairway.EMERGENCY_MAX_FIRST_LEG;
    private static final int BASKET_RELOCATION_RADIUS = CoursePlacementConfig.Basket.RELOCATION_RADIUS;
    private static final String ALT_ROUTE_DIAG_ENV = CoursePlacementConfig.EnvVars.ALT_ROUTE_DIAG;

    static HoleRoutePolicyResult enforceHoleRoutePolicy(
            ServerWorld world,
            Hole hole,
            BlockPos initialTee,
            BlockPos initialBasket,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos teeSurface = initialTee;
        BlockPos basketSurface = initialBasket;

        if (hole.par() >= 5) {
            for (int attempt = 1; attempt <= ROUTE_POLICY_MAX_RETRIES; attempt++) {
                BlockPos alternateAnchor = findAlternateFairwayAnchor(world, teeSurface, basketSurface, true);
                int routeGap = routeLongestCarryGap(world, teeSurface, basketSurface, alternateAnchor);
                if (routeGap <= PAR5_ROUTE_MAX_WATER_CARRY) {
                    basketSurface = FairwayCarver.ensureBasketGreenLandingZone(
                            world,
                            teeSurface,
                            teeSurface,
                            basketSurface,
                            FairwayCarver.resolveHoleFairwayWidth(hole),
                            originalBlocks,
                            protectedPositions
                    );
                    return new HoleRoutePolicyResult(teeSurface, basketSurface, alternateAnchor, 5, "");
                }

                basketSurface = chooseLowerCarryBasketSurface(world, teeSurface, basketSurface, PAR5_ROUTE_MAX_WATER_CARRY);
                basketSurface = FairwayCarver.ensureBasketGreenLandingZone(
                        world,
                        teeSurface,
                    teeSurface,
                        basketSurface,
                        FairwayCarver.resolveHoleFairwayWidth(hole),
                        originalBlocks,
                        protectedPositions
                );
                teeSurface = SurfaceAdaptationHelper.relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
            }

            McdgMod.LOGGER.info(
                    "Par 5 route policy fallback applied | hole={} seedPar={} effectivePar=4 reason=water_carry_over_limit",
                    hole.index(),
                    hole.par()
            );
        }

        for (int attempt = 1; attempt <= ROUTE_POLICY_MAX_RETRIES; attempt++) {
            BlockPos alternateAnchor = findAlternateFairwayAnchor(world, teeSurface, basketSurface, false);
            int routeGap = routeLongestCarryGap(world, teeSurface, basketSurface, alternateAnchor);
            if (routeGap <= PAR34_ROUTE_MAX_WATER_CARRY) {
                basketSurface = FairwayCarver.ensureBasketGreenLandingZone(
                        world,
                        teeSurface,
                    teeSurface,
                        basketSurface,
                        FairwayCarver.resolveHoleFairwayWidth(hole),
                        originalBlocks,
                        protectedPositions
                );
                String note = hole.par() >= 5 ? "Par 5 fallback: safe par 4 layout" : "";
                return new HoleRoutePolicyResult(teeSurface, basketSurface, alternateAnchor, Math.min(hole.par(), 4), note);
            }

            basketSurface = chooseLowerCarryBasketSurface(world, teeSurface, basketSurface, PAR34_ROUTE_MAX_WATER_CARRY);
            basketSurface = FairwayCarver.ensureBasketGreenLandingZone(
                    world,
                    teeSurface,
                    teeSurface,
                    basketSurface,
                    FairwayCarver.resolveHoleFairwayWidth(hole),
                    originalBlocks,
                    protectedPositions
            );
            teeSurface = SurfaceAdaptationHelper.relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
        }

        McdgMod.LOGGER.warn(
            "Route policy could not fully satisfy carry target after {} attempts; using best land-safe fallback | hole={} seedPar={} bestAllowedCarry={}",
            ROUTE_POLICY_MAX_RETRIES,
            hole.index(),
            hole.par(),
            PAR34_ROUTE_MAX_WATER_CARRY
        );
        basketSurface = FairwayCarver.ensureBasketGreenLandingZone(
            world,
            teeSurface,
            teeSurface,
            basketSurface,
            FairwayCarver.resolveHoleFairwayWidth(hole),
            originalBlocks,
            protectedPositions
        );
        return new HoleRoutePolicyResult(teeSurface, basketSurface, null, Math.min(hole.par(), 4), "Land-safe fallback");
    }

    private static int routeLongestCarryGap(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            BlockPos alternateAnchor
    ) {
        int directGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, basketSurface);
        if (alternateAnchor == null) {
            return directGap;
        }

        int firstGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, alternateAnchor);
        int secondGap = CoursePlacementService.computeLongestWaterCarryGap(world, alternateAnchor, basketSurface);
        return Math.max(firstGap, secondGap);
    }

    private static BlockPos chooseLowerCarryBasketSurface(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos baselineBasket,
            int targetCarryGap
    ) {
        BlockPos best = baselineBasket;
        int bestGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, baselineBasket);
        int bestScore = Integer.MAX_VALUE;
        int scanRadius = BASKET_RELOCATION_RADIUS;

        for (int dx = -scanRadius; dx <= scanRadius; dx += 4) {
            for (int dz = -scanRadius; dz <= scanRadius; dz += 4) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (scanRadius * scanRadius)) {
                    continue;
                }

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, baselineBasket.getX() + dx, baselineBasket.getZ() + dz)
                );
                if (!SurfaceResolver.isPlayableBasketSurface(world, candidate)) {
                    continue;
                }

                int candidateGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, candidate);
                int score = (candidateGap * 1000) + distSq + Math.abs(candidate.getY() - baselineBasket.getY()) * 8;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestGap = candidateGap;
                    if (candidateGap <= targetCarryGap) {
                        return best;
                    }
                }
            }
        }

        return bestGap < CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, baselineBasket) ? best : baselineBasket;
    }

    static BlockPos findAlternateFairwayAnchor(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface, boolean forceAlternateRoute) {
        boolean routeDiag = isAltRouteDiagEnabled();
        int directCarryGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, basketSurface);
        if (routeDiag) {
            McdgMod.LOGGER.info(
                    "AltRouteDiag start tee=({}, {}, {}) basket=({}, {}, {}) directCarryGap={} threshold={}",
                    teeSurface.getX(), teeSurface.getY(), teeSurface.getZ(),
                    basketSurface.getX(), basketSurface.getY(), basketSurface.getZ(),
                    directCarryGap,
                    TEE_MAX_DIRECT_CARRY_GAP
            );
        }
        if (!forceAlternateRoute && directCarryGap <= ALT_FAIRWAY_TARGET_ROUTE_GAP) {
            if (routeDiag) {
                McdgMod.LOGGER.info("AltRouteDiag skipped: direct carry does not exceed threshold.");
            }
            return null;
        }

        AlternateAnchorSearchResult strictResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            ALT_FAIRWAY_FIRST_LEG_MAX_GAP,
            ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("strict", strictResult);
        }
        if (strictResult.anchor() != null) {
            return strictResult.anchor();
        }

        AlternateAnchorSearchResult fallbackResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            ALT_FAIRWAY_FIRST_LEG_MAX_GAP_FALLBACK,
            ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("fallback", fallbackResult);
        }
        if (fallbackResult.anchor() != null) {
            return fallbackResult.anchor();
        }

        AlternateAnchorSearchResult emergencyResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            TEE_MAX_DIRECT_CARRY_GAP,
            ALT_FAIRWAY_EMERGENCY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_EMERGENCY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("emergency", emergencyResult);
        }
        return emergencyResult.anchor();
    }

    private static AlternateAnchorSearchResult searchAlternateFairwayAnchor(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            int firstLegGapLimit,
            int anchorSearchRadius,
            int maxFirstLeg
    ) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        int candidatesChecked = 0;
        int rejectedUnwalkable = 0;
        int rejectedFirstLeg = 0;
        int rejectedNoAdvance = 0;
        int rejectedFirstGap = 0;
        int viableCandidates = 0;
        int bestFirstGap = -1;
        int bestSecondGap = -1;

        for (int dx = -anchorSearchRadius; dx <= anchorSearchRadius; dx += 2) {
            for (int dz = -anchorSearchRadius; dz <= anchorSearchRadius; dz += 2) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (anchorSearchRadius * anchorSearchRadius)) {
                    continue;
                }
                candidatesChecked++;

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, teeSurface.getX() + dx, teeSurface.getZ() + dz)
                );
                if (!SurfaceAdaptationHelper.isWalkableGround(world, candidate) || SurfaceAdaptationHelper.isLikelyPitSurface(world, candidate)) {
                    rejectedUnwalkable++;
                    continue;
                }

                int firstLeg = Math.max(
                        Math.abs(candidate.getX() - teeSurface.getX()),
                        Math.abs(candidate.getZ() - teeSurface.getZ())
                );
                if (firstLeg < ALT_FAIRWAY_MIN_ADVANCE || firstLeg > maxFirstLeg) {
                    rejectedFirstLeg++;
                    continue;
                }

                int distTeeToBasket = Math.abs(basketSurface.getX() - teeSurface.getX()) + Math.abs(basketSurface.getZ() - teeSurface.getZ());
                int distAnchorToBasket = Math.abs(basketSurface.getX() - candidate.getX()) + Math.abs(basketSurface.getZ() - candidate.getZ());
                if (distAnchorToBasket > (distTeeToBasket + ALT_FAIRWAY_MIN_ADVANCE)) {
                    rejectedNoAdvance++;
                    continue;
                }

                int firstGap = CoursePlacementService.computeLongestWaterCarryGap(world, teeSurface, candidate);
                if (firstGap > firstLegGapLimit) {
                    rejectedFirstGap++;
                    continue;
                }

                int secondGap = CoursePlacementService.computeLongestWaterCarryGap(world, candidate, basketSurface);
                if (secondGap > ALT_FAIRWAY_TARGET_ROUTE_GAP) {
                    continue;
                }
                viableCandidates++;

                int score = distSq;
                score += firstGap * 600;
                score += secondGap * 40;
                score += Math.max(0, Math.abs(candidate.getY() - teeSurface.getY()) - 2) * 20;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestFirstGap = firstGap;
                    bestSecondGap = secondGap;
                }
            }
        }

        return new AlternateAnchorSearchResult(
                best,
                candidatesChecked,
                viableCandidates,
                rejectedUnwalkable,
                rejectedFirstLeg,
                rejectedNoAdvance,
                rejectedFirstGap,
                bestScore,
                bestFirstGap,
                bestSecondGap
        );
    }

    static void logAltRouteDiagResult(String passName, AlternateAnchorSearchResult result) {
        BlockPos anchor = result.anchor();
        McdgMod.LOGGER.info(
                "AltRouteDiag {} anchorFound={} checked={} viable={} rejectUnwalkable={} rejectFirstLeg={} rejectNoAdvance={} rejectFirstGap={} bestScore={} bestFirstGap={} bestSecondGap={} anchor=({}, {}, {})",
                passName,
                anchor != null,
                result.candidatesChecked(),
                result.viableCandidates(),
                result.rejectedUnwalkable(),
                result.rejectedFirstLeg(),
                result.rejectedNoAdvance(),
                result.rejectedFirstGap(),
                result.bestScore(),
                result.bestFirstGap(),
                result.bestSecondGap(),
                anchor == null ? 0 : anchor.getX(),
                anchor == null ? 0 : anchor.getY(),
                anchor == null ? 0 : anchor.getZ()
        );
    }

    private record AlternateAnchorSearchResult(
            BlockPos anchor,
            int candidatesChecked,
            int viableCandidates,
            int rejectedUnwalkable,
            int rejectedFirstLeg,
            int rejectedNoAdvance,
            int rejectedFirstGap,
            int bestScore,
            int bestFirstGap,
            int bestSecondGap
    ) {
    }

    static boolean isAltRouteDiagEnabled() {
        String value = System.getenv(ALT_ROUTE_DIAG_ENV);
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim();
        return normalized.equalsIgnoreCase("1")
                || normalized.equalsIgnoreCase("true")
                || normalized.equalsIgnoreCase("yes")
                || normalized.equalsIgnoreCase("on");
    }

    static String alternateRouteNote(BlockPos teeSurface, BlockPos basketSurface, BlockPos anchor) {
        int dx = basketSurface.getX() - teeSurface.getX();
        int dz = basketSurface.getZ() - teeSurface.getZ();
        int ax = anchor.getX() - teeSurface.getX();
        int az = anchor.getZ() - teeSurface.getZ();
        int cross = (dx * az) - (dz * ax);

        if (Math.abs(cross) < 16) {
            return "Alt fairway";
        }
        return cross > 0 ? "Alt lane: right" : "Alt lane: left";
    }





        record HoleRoutePolicyResult(
            BlockPos teeSurface,
            BlockPos basketSurface,
            BlockPos alternateAnchor,
            int effectivePar,
            String routingNote
        ) {
        }
}
