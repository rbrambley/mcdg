package com.mcdg.game;

import com.mcdg.McdgMod;

import com.mcdg.data.Hole;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.SafePositionFinder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Optional;

/**
 * Classifies whether a player lie is in-bounds, out-of-bounds, or in a hazard.
 */
public final class OutOfBoundsClassifier {
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 14;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;

    // Runtime toggle for debug logging (controlled via /mcdg debug obclassifier command)
    private static volatile boolean debugLoggingEnabled = false;

    private OutOfBoundsClassifier() {}

    /**
     * Detailed penalty classification including human-readable reason and the resolved hazard type.
     */
    public record PenaltyDetail(StrictPenaltyType type, String reason, Optional<HazardType> hazardType) {
        public static final PenaltyDetail NONE = new PenaltyDetail(StrictPenaltyType.NONE, "In Bounds", Optional.empty());
    }

    /**
     * Enables or disables debug logging for out-of-bounds classification.
     * When enabled, each classification decision is logged with details.
     */
    public static void setDebugLogging(boolean enabled) {
        debugLoggingEnabled = enabled;
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLoggingEnabled;
    }

    public static StrictPenaltyType classifyOutType(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        int corridorHalfWidth = strictCorridorHalfWidth(currentHole, world, tee, basket, rulesetManager);
        return classifyOutTypeWithCorridor(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth);
    }

    public static PenaltyDetail classifyWithDetail(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager
    ) {
        int corridorHalfWidth = strictCorridorHalfWidth(currentHole, world, tee, basket, rulesetManager);
        return classifyWithDetailAndCorridor(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth);
    }

    public static StrictPenaltyType classifyOutTypeWithCorridor(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth
    ) {
        return classifyOutTypeWithCorridorDebug(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth, debugLoggingEnabled);
    }

    public static PenaltyDetail classifyWithDetailAndCorridor(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth
    ) {
        return classifyWithDetailAndCorridorDebug(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth, debugLoggingEnabled);
    }

    public static StrictPenaltyType classifyOutTypeWithCorridorDebug(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth,
            boolean debug
    ) {
        return classifyWithDetailAndCorridorDebug(world, feet, currentHole, tee, basket, alternateAnchor, rulesetManager, corridorHalfWidth, debug).type();
    }

    public static PenaltyDetail classifyWithDetailAndCorridorDebug(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            BlockPos alternateAnchor,
            TournamentRulesetManager rulesetManager,
            int corridorHalfWidth,
            boolean debug
    ) {
        // Basket green is a fully safe zone -- no penalties for any landing within it.
        if (isBasketGreenSafe(feet, basket.down())) {
            if (debug) {
                McdgMod.LOGGER.info("[OBClassifier] feet={} basket={} -> BASKET_GREEN_SAFE", formatPos(feet), formatPos(basket));
            }
            return PenaltyDetail.NONE;
        }

        // Basket column (hopper, pole, lantern) is always safe.
        if (feet.getX() == basket.getX() && feet.getZ() == basket.getZ()) {
            if (debug) {
                McdgMod.LOGGER.info("[OBClassifier] feet={} basket={} -> BASKET_COLUMN_SAFE", formatPos(feet), formatPos(basket));
            }
            return PenaltyDetail.NONE;
        }

        if (isFluidPenaltyZone(world, feet)) {
            if (debug) {
                McdgMod.LOGGER.info("[OBClassifier] feet={} -> FLUID_OB", formatPos(feet));
            }
            return new PenaltyDetail(StrictPenaltyType.OB, "Water", Optional.empty());
        }

        if (rulesetManager.strictEnableSlopeHazard() && isSteepSlopeHazard(world, feet, rulesetManager.strictSlopeHazardDeltaY())) {
            if (debug) {
                McdgMod.LOGGER.info("[OBClassifier] feet={} -> SLOPE_HAZARD", formatPos(feet));
            }
            return new PenaltyDetail(StrictPenaltyType.HAZARD, "Slope", Optional.empty());
        }

        if (rulesetManager.strictEnableRoughHazard() && isDenseRoughHazard(world, feet, rulesetManager.strictRoughHazardLeafLogThreshold())) {
            if (debug) {
                McdgMod.LOGGER.info("[OBClassifier] feet={} -> ROUGH_HAZARD", formatPos(feet));
            }
            return new PenaltyDetail(StrictPenaltyType.HAZARD, "Rough", Optional.empty());
        }

        // Check for expanded hazards via HazardManager
        HazardType expandedHazard = HazardManager.getHazardType(world, feet);
        if (expandedHazard != HazardType.NONE && expandedHazard != HazardType.WATER && expandedHazard != HazardType.LAVA) {
            // Skip water/lava as they're already handled above
            HazardBehavior behavior = HazardManager.getHazardBehavior(expandedHazard);
            if (behavior.addsPenaltyStroke() || behavior.destroysDisc()) {
                if (debug) {
                    McdgMod.LOGGER.info("[OBClassifier] feet={} -> EXPANDED_HAZARD: {}", formatPos(feet), expandedHazard.displayName());
                }
                // Disc destruction hazards still count as HAZARD for penalty purposes
                return new PenaltyDetail(StrictPenaltyType.HAZARD, behavior.penaltyReason(), Optional.of(expandedHazard));
            }
        }

        if (debug) {
            McdgMod.LOGGER.info("[OBClassifier] feet={} -> NONE (in bounds)", formatPos(feet));
        }
        return PenaltyDetail.NONE;
    }

    static boolean isBasketGreenSafe(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }

    static boolean isFluidPenaltyZone(ServerWorld world, BlockPos feet) {
        return world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA);
    }

    static int strictCorridorHalfWidth(Hole hole, ServerWorld world, BlockPos tee, BlockPos basket, TournamentRulesetManager rulesetManager) {
        int maxSegmentWidth = 0;
        for (var segment : hole.fairwaySegments()) {
            maxSegmentWidth = Math.max(maxSegmentWidth, segment.width());
        }
        int baseHalf = Math.max(3, maxSegmentWidth / 2);
        int baseline = Math.max(rulesetManager.strictCorridorMinimumHalfWidthBlocks(), baseHalf + rulesetManager.strictCorridorBasePaddingBlocks());

        int directCarryGap = findLongestWaterGap(world, tee, basket)[2];
        if (directCarryGap > rulesetManager.strictAltRouteCarryTriggerBlocks()) {
            return Math.max(baseline, rulesetManager.strictAltRouteHalfWidthBlocks());
        }

        return baseline;
    }

    /**
     * Finds the longest continuous water gap along the line from start to end.
     * Returns int[3] = { startDistanceBlocks, endDistanceBlocks, maxGapLengthBlocks }
     * where distances are measured from the start position.
     */
    static int[] findLongestWaterGap(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int dominant = Math.max(Math.abs(dx), Math.abs(dz));
        if (dominant == 0) {
            return new int[]{0, 0, 0};
        }

        int stepX = Integer.signum(dx);
        int stepZ = Integer.signum(dz);

        int longest = 0;
        int current = 0;
        int currentStart = 0;
        int bestStart = 0;
        int bestEnd = 0;

        int x = start.getX();
        int z = start.getZ();
        int errX = 0;
        int errZ = 0;

        for (int i = 0; i <= dominant; i++) {
            if (isWaterCarryColumn(world, x, z)) {
                if (current == 0) {
                    currentStart = i;
                }
                current++;
                if (current > longest) {
                    longest = current;
                    bestStart = currentStart;
                    bestEnd = i;
                }
            } else if (current > 0) {
                current = 0;
            }

            errX += Math.abs(dx);
            if (errX >= dominant && stepX != 0) {
                x += stepX;
                errX -= dominant;
            }
            errZ += Math.abs(dz);
            if (errZ >= dominant && stepZ != 0) {
                z += stepZ;
                errZ -= dominant;
            }
        }

        return new int[]{bestStart, bestEnd, longest};
    }

    static boolean isWaterCarryColumn(ServerWorld world, int x, int z) {
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY < world.getBottomY()) {
            return false;
        }

        BlockPos surface = new BlockPos(x, surfaceY, z);
        if (!world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return false;
        }

        return !hasAnySafeLandingNearby(world, surface);
    }

    static boolean hasAnySafeLandingNearby(ServerWorld world, BlockPos center) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if ((dx * dx) + (dz * dz) > 36) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos feet = new BlockPos(x, y + 1, z);
                if (SafePositionFinder.isStandableFeet(world, feet)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double nx = sx + (t * dx);
        double nz = sz + (t * dz);
        double mx = px - nx;
        double mz = pz - nz;
        return Math.sqrt(mx * mx + mz * mz);
    }

    private static double distanceFromPlayableRouteXZ(BlockPos point, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        if (alternateAnchor == null) {
            return distanceFromPointToSegmentXZ(point, tee, basket);
        }

        double firstLeg = distanceFromPointToSegmentXZ(point, tee, alternateAnchor);
        double secondLeg = distanceFromPointToSegmentXZ(point, alternateAnchor, basket);
        return Math.min(firstLeg, secondLeg);
    }

    /**
     * Finds the nearest point on the forward corridor boundary from the given lie.
     * "Forward" means the half of the corridor between the lie's projection along the
     * tee->basket axis and the basket (so we never point the player backward).
     *
     * Returns int[2] = { distanceFeet, bearingDegrees } where bearingDegrees is a
     * geographic bearing (0=N, 90=E, 180=S, 270=W), or null if the lie is already
     * inside the corridor (lateral distance <= corridorHalfWidth).
     */
    static int[] nearestForwardCorridorEntry(BlockPos lie, BlockPos tee, BlockPos basket, BlockPos alternateAnchor, int corridorHalfWidth) {
        // Choose the relevant segment: if there is an alternate anchor, use the leg
        // whose projected t-value places the lie further forward (closer to the basket).
        BlockPos segStart = tee;
        BlockPos segEnd = basket;
        if (alternateAnchor != null) {
            double tToAlt = projectionT(lie, tee, alternateAnchor);
            double tToBasket = projectionT(lie, alternateAnchor, basket);
            if (tToBasket > tToAlt) {
                segStart = alternateAnchor;
                segEnd = basket;
            }
        }

        double lateralDist = distanceFromPointToSegmentXZ(lie, segStart, segEnd);
        if (lateralDist <= corridorHalfWidth) {
            return null;
        }

        // Find the nearest point on the segment to the lie.
        double bestX = segStart.getX() + 0.5;
        double bestZ = segStart.getZ() + 0.5;
        double bestDistSq = Double.MAX_VALUE;
        int segDx = segEnd.getX() - segStart.getX();
        int segDz = segEnd.getZ() - segStart.getZ();
        int segSteps = Math.max(Math.abs(segDx), Math.abs(segDz));
        if (segSteps < 1) {
            segSteps = 1;
        }
        for (int i = 0; i <= segSteps; i++) {
            double t = i / (double) segSteps;
            double sx = segStart.getX() + 0.5 + (segDx * t);
            double sz = segStart.getZ() + 0.5 + (segDz * t);
            double dx = (lie.getX() + 0.5) - sx;
            double dz = (lie.getZ() + 0.5) - sz;
            double distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestX = sx;
                bestZ = sz;
            }
        }

        // Offset from the nearest point toward the lie by exactly the corridor half-width.
        double lieCx = lie.getX() + 0.5;
        double lieCz = lie.getZ() + 0.5;
        double toLieDx = lieCx - bestX;
        double toLieDz = lieCz - bestZ;
        double toLieLen = Math.sqrt(toLieDx * toLieDx + toLieDz * toLieDz);
        if (toLieLen < 0.001) {
            toLieDx = 0;
            toLieDz = 1;
            toLieLen = 1;
        }
        double normDx = toLieDx / toLieLen;
        double normDz = toLieDz / toLieLen;
        double entryX = bestX + normDx * corridorHalfWidth;
        double entryZ = bestZ + normDz * corridorHalfWidth;

        // Distance from entry point to basket in feet.
        double basketCx = basket.getX() + 0.5;
        double basketCz = basket.getZ() + 0.5;
        double distToBasket = Math.sqrt((entryX - basketCx) * (entryX - basketCx) + (entryZ - basketCz) * (entryZ - basketCz));
        int distFeet = (int) Math.round(distToBasket * 3.28084);

        // Bearing from entry point to basket (geographic: 0=N, 90=E, 180=S, 270=W).
        double bearingRad = Math.atan2(basketCx - entryX, basketCz - entryZ);
        int bearingDeg = (int) Math.round(Math.toDegrees(bearingRad));
        bearingDeg = ((bearingDeg % 360) + 360) % 360;

        return new int[]{distFeet, bearingDeg};
    }

    private static double projectionT(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double lsq = dx * dx + dz * dz;
        if (lsq < 1.0e-6) return 0.0;
        return ((px - sx) * dx + (pz - sz) * dz) / lsq;
    }

    static boolean isSteepSlopeHazard(ServerWorld world, BlockPos feet, int slopeDeltaThreshold) {
        int centerY = feet.getY() - 1;
        int[] offsets = {-1, 0, 1};
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int sampleX = feet.getX() + dx;
                int sampleZ = feet.getZ() + dz;
                int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                if (Math.abs(sampleY - centerY) >= slopeDeltaThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isDenseRoughHazard(ServerWorld world, BlockPos feet, int threshold) {
        int roughHits = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surface = new BlockPos(x, y, z);
                BlockPos head = surface.up(2);
                BlockState surfaceState = world.getBlockState(surface);
                BlockState headState = world.getBlockState(head);
                if (isRoughMaterial(surfaceState) || isRoughMaterial(headState)) {
                    roughHits++;
                    if (roughHits >= threshold) {
                        return true;
                    }
                }
            }
        }
        return roughHits >= threshold;
    }

    private static boolean isRoughMaterial(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CACTUS);
    }

    static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "-";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
