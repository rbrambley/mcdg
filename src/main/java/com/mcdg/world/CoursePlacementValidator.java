package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.PlacedCourseState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

public final class CoursePlacementValidator {
    private static final int LANDING_GAP_WARNING_BLOCKS = 80;
    // Keep strict landing-gap validation, but avoid one-block false fails from surface sampling variance.
    private static final int LANDING_GAP_FAIL_BLOCKS = 91;
    private static final int ALT_ROUTE_REQUIRED_CARRY_BLOCKS = 91;
    private static final int LANDING_SCAN_RADIUS = 6;
    private static final int TEE_LAUNCH_CHECK_DISTANCE = 22;
    private static final int TEE_LAUNCH_CHECK_HALF_WIDTH = 3;
    private static final int BASKET_ENCLOSURE_SCAN_RADIUS = 6;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_FAIL = 18;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_CHECK = 12;
    private static final int BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD = 8;
    private static final double BASKET_ENCLOSURE_HIGH_WALL_RATIO = 0.82;
    private static final int FINISH_APPROACH_SCAN_DISTANCE = 32;
    private static final int FINISH_HAZARD_SCAN_HALF_WIDTH = 7;
    private static final int FINISH_HAZARD_MIN_COLUMNS = 18;
    private static final int FINISH_GREEN_SAFE_SCAN_RADIUS = 8;
    private static final int FINISH_GREEN_MIN_SAFE_COLUMNS = 36;
    private static final int FINISH_APPROACH_SAMPLE_INTERVAL = 10;
    private static final int FINISH_APPROACH_SAFE_RADIUS = 5;
    private static final int FINISH_APPROACH_MIN_SAFE_SAMPLES = 1;

    public ValidationReport validatePlacedCourse(ServerWorld world, Course course, PlacedCourseState placedCourseState, String scenarioName) {
        List<ValidationIssue> issues = new ArrayList<>();
        int blockedTeeLanes = 0;
        int unsafeTees = 0;
        int unsafeBaskets = 0;
        int deeplyEnclosedBaskets = 0;
        int warningLandingGaps = 0;
        int invalidHoleCount = 0;
        int maxLandingGapObserved = 0;

        for (Hole hole : course.holes()) {
            boolean holeFailed = false;
            int holeIndex = hole.index();
            BlockPos teePos = placedCourseState.holeTees().get(holeIndex);
            BlockPos basketPos = placedCourseState.holeBaskets().get(holeIndex);

            if (teePos == null) {
                issues.add(new ValidationIssue(holeIndex, "tee_missing", "No tee position stored for hole.", null));
                invalidHoleCount++;
                continue;
            }
            if (basketPos == null) {
                issues.add(new ValidationIssue(holeIndex, "basket_missing", "No basket position stored for hole.", null));
                invalidHoleCount++;
                continue;
            }

            if (!validateTeePadBlocks(world, teePos)) {
                issues.add(new ValidationIssue(holeIndex, "tee_pad_invalid", "Tee pad blocks are not in expected 3x3 shape/material.", teePos));
                holeFailed = true;
            }

            if (!isSafeLandingSurface(world, teePos)) {
                issues.add(new ValidationIssue(holeIndex, "tee_unsafe", "Tee center is unsafe (fluid/air/canopy-like position).", teePos));
                unsafeTees++;
                holeFailed = true;
            }

            if (hasTeeLaunchBlockers(world, teePos, basketPos.down())) {
                issues.add(new ValidationIssue(holeIndex, "tee_launch_blocked", "Tree material blocks initial launch lane from tee.", teePos));
                blockedTeeLanes++;
                holeFailed = true;
            }

            if (!validateBasketMarker(world, hole, basketPos)) {
                issues.add(new ValidationIssue(holeIndex, "basket_structure_invalid", "Basket marker blocks are missing or malformed.", basketPos));
                holeFailed = true;
            }

            if (!isSafeBasketBase(world, basketPos.down())) {
                issues.add(new ValidationIssue(holeIndex, "basket_unsafe", "Basket base is unsafe (water/air/unstable ground).", basketPos.down()));
                unsafeBaskets++;
                holeFailed = true;
            }

            if (isBasketDeeplyEnclosed(world, basketPos.down())) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "basket_deeply_enclosed",
                        "Basket is deeply enclosed below surrounding terrain; relocate to surface-playable area.",
                        basketPos.down()
                ));
                deeplyEnclosedBaskets++;
                holeFailed = true;
            }

            BlockPos finishOrigin = alternateFinishOrigin(teePos, basketPos.down(), placedCourseState.holeAlternateAnchors().get(holeIndex));
            FinishPlayability finishPlayability = evaluateFinishPlayability(world, finishOrigin, basketPos.down());
            if (finishPlayability.hazardColumns() >= FINISH_HAZARD_MIN_COLUMNS
                    && finishPlayability.greenSafeColumns() < FINISH_GREEN_MIN_SAFE_COLUMNS) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "finish_green_too_small",
                        "Hazard-heavy finish lacks enough in-bounds green area around the basket.",
                        basketPos.down()
                ));
                holeFailed = true;
            }
            if (finishPlayability.hazardColumns() >= FINISH_HAZARD_MIN_COLUMNS
                    && finishPlayability.approachSafeSamples() < FINISH_APPROACH_MIN_SAFE_SAMPLES) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "finish_approach_too_hard",
                        "Hazard-heavy finish lacks conservative in-bounds landing options on approach.",
                        midpoint(finishOrigin, basketPos.down())
                ));
                holeFailed = true;
            }

            int directLongestGap = computeLongestWaterCarryGap(world, teePos, basketPos.down());
            BlockPos alternateAnchor = placedCourseState.holeAlternateAnchors().get(holeIndex);
            int routeLongestGap = directLongestGap;
            if (alternateAnchor != null) {
                int teeToAnchorGap = computeLongestWaterCarryGap(world, teePos, alternateAnchor);
                int anchorToBasketGap = computeLongestWaterCarryGap(world, alternateAnchor, basketPos.down());
                routeLongestGap = Math.max(teeToAnchorGap, anchorToBasketGap);
            }
            maxLandingGapObserved = Math.max(maxLandingGapObserved, routeLongestGap);
            if (hole.par() >= 5 && alternateAnchor == null) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "par5_alternate_route_missing",
                        "Par 5 holes require an alternate fairway anchor.",
                        midpoint(teePos, basketPos.down())
                ));
                holeFailed = true;
            }
            if (directLongestGap > ALT_ROUTE_REQUIRED_CARRY_BLOCKS && alternateAnchor == null) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "alternate_route_missing",
                        "Long water carry (" + directLongestGap + " blocks) requires alternate fairway anchor.",
                        midpoint(teePos, basketPos.down())
                ));
                holeFailed = true;
            }
            if (routeLongestGap > LANDING_GAP_FAIL_BLOCKS) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "landing_gap_too_long",
                        "Longest no-landing gap on playable route is " + routeLongestGap + " blocks (max " + LANDING_GAP_FAIL_BLOCKS + ").",
                        midpoint(teePos, basketPos.down())
                ));
                holeFailed = true;
            } else if (routeLongestGap > LANDING_GAP_WARNING_BLOCKS) {
                warningLandingGaps++;
            }

            if (holeFailed) {
                invalidHoleCount++;
            }
        }

        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("total_holes", course.holes().size());
        metrics.put("invalid_holes", invalidHoleCount);
        metrics.put("issue_count", issues.size());
        metrics.put("blocked_tee_lanes", blockedTeeLanes);
        metrics.put("unsafe_tees", unsafeTees);
        metrics.put("unsafe_baskets", unsafeBaskets);
        metrics.put("deeply_enclosed_baskets", deeplyEnclosedBaskets);
        metrics.put("warning_landing_gaps", warningLandingGaps);
        metrics.put("landing_gap_warning_threshold", LANDING_GAP_WARNING_BLOCKS);
        metrics.put("landing_gap_fail_threshold", LANDING_GAP_FAIL_BLOCKS);
        metrics.put("max_landing_gap", maxLandingGapObserved);

        String biome = "unknown";
        for (BlockPos teePos : placedCourseState.holeTees().values()) {
            biome = biomeId(world.getBiome(teePos));
            break;
        }
        return new ValidationReport(scenarioName, course.seed(), biome, issues, Map.copyOf(metrics));
    }

    private static boolean validateTeePadBlocks(ServerWorld world, BlockPos teeCenter) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos sample = teeCenter.add(dx, 0, dz);
                BlockState state = world.getBlockState(sample);
                if (dx == 0 && dz == 0) {
                    if (!state.isOf(Blocks.LIME_CONCRETE)) {
                        return false;
                    }
                } else if (!state.isOf(Blocks.SMOOTH_STONE)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validateBasketMarker(ServerWorld world, Hole hole, BlockPos basketStoredPos) {
        if (!world.getBlockState(basketStoredPos).isOf(Blocks.HOPPER)) {
            return false;
        }

        int basketHeight = Math.max(1, hole.basket().basketHeight());
        for (int i = 1; i <= basketHeight + 1; i++) {
            if (!world.getBlockState(basketStoredPos.up(i)).isOf(Blocks.IRON_BARS)) {
                return false;
            }
        }

        return world.getBlockState(basketStoredPos.up(basketHeight + 2)).isOf(Blocks.LANTERN);
    }

    private static boolean hasTeeLaunchBlockers(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeSurface.getX();
        int dz = basketSurface.getZ() - teeSurface.getZ();
        int total = Math.max(Math.abs(dx), Math.abs(dz));
        if (total < 1) {
            return false;
        }

        int laneLength = Math.min(TEE_LAUNCH_CHECK_DISTANCE, Math.max(8, total / 3));
        for (int step = 1; step <= laneLength; step++) {
            double t = step / (double) total;
            int lineX = (int) Math.round(teeSurface.getX() + (dx * t));
            int lineZ = (int) Math.round(teeSurface.getZ() + (dz * t));

            for (int wx = -TEE_LAUNCH_CHECK_HALF_WIDTH; wx <= TEE_LAUNCH_CHECK_HALF_WIDTH; wx++) {
                for (int wz = -TEE_LAUNCH_CHECK_HALF_WIDTH; wz <= TEE_LAUNCH_CHECK_HALF_WIDTH; wz++) {
                    int sampleX = lineX + wx;
                    int sampleZ = lineZ + wz;

                    for (int y = teeSurface.getY(); y <= teeSurface.getY() + 5; y++) {
                        BlockState state = world.getBlockState(new BlockPos(sampleX, y, sampleZ));
                        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return 0;
        }

        int longestGap = 0;
        int currentGap = 0;

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(start.getX() + (dx * t));
            int z = (int) Math.round(start.getZ() + (dz * t));

            if (!isWaterColumn(world, x, z)) {
                currentGap = 0;
                continue;
            }

            if (hasAnySafeLandingNearby(world, x, z, LANDING_SCAN_RADIUS)) {
                currentGap = 0;
            } else {
                currentGap++;
                longestGap = Math.max(longestGap, currentGap);
            }
        }

        return longestGap;
    }

    private static boolean isWaterColumn(ServerWorld world, int x, int z) {
        int worldSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos worldSurface = new BlockPos(x, worldSurfaceY, z);
        if (!world.getBlockState(worldSurface).getFluidState().isEmpty()) {
            return true;
        }

        int seaY = world.getSeaLevel();
        for (int y = seaY - 2; y <= seaY + 1; y++) {
            BlockPos sample = new BlockPos(x, y, z);
            if (!world.getBlockState(sample).getFluidState().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnySafeLandingNearby(ServerWorld world, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = x + dx;
                int sampleZ = z + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (isSafeLandingSurface(world, sample)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSafeLandingSurface(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }
        if (ground.getBlock() instanceof PlantBlock || ground.isIn(BlockTags.LOGS) || ground.isIn(BlockTags.LEAVES)) {
            return false;
        }
        if (!ground.isSolidBlock(world, pos)) {
            return false;
        }

        BlockState above = world.getBlockState(pos.up());
        BlockState above2 = world.getBlockState(pos.up(2));
        if (!isOpenSpace(above) || !isOpenSpace(above2)) {
            return false;
        }

        return above.getFluidState().isEmpty() && above2.getFluidState().isEmpty();
    }

    private static boolean isSafeBasketBase(ServerWorld world, BlockPos groundPos) {
        BlockState ground = world.getBlockState(groundPos);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }

        // Basket marker occupies headroom by design (hopper + bars), so only reject fluid above.
        BlockState above = world.getBlockState(groundPos.up());
        return above.getFluidState().isEmpty();
    }

    private static boolean isBasketDeeplyEnclosed(ServerWorld world, BlockPos basketBase) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, basketBase.getX(), basketBase.getZ()) - 1;
        int centerDepth = centerSurfaceY - basketBase.getY();
        if (centerDepth >= BASKET_ENCLOSURE_CENTER_DEPTH_FAIL) {
            return true;
        }

        // Shallow baskets can sit in natural bowls/cliffs but still play correctly.
        if (centerDepth < BASKET_ENCLOSURE_CENTER_DEPTH_CHECK) {
            return false;
        }

        int highWallSamples = 0;
        int totalSamples = 0;
        for (int dx = -BASKET_ENCLOSURE_SCAN_RADIUS; dx <= BASKET_ENCLOSURE_SCAN_RADIUS; dx += 2) {
            for (int dz = -BASKET_ENCLOSURE_SCAN_RADIUS; dz <= BASKET_ENCLOSURE_SCAN_RADIUS; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int distSq = dx * dx + dz * dz;
                if (distSq > (BASKET_ENCLOSURE_SCAN_RADIUS * BASKET_ENCLOSURE_SCAN_RADIUS)) {
                    continue;
                }

                int sampleSurfaceY = world.getTopY(
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        basketBase.getX() + dx,
                        basketBase.getZ() + dz
                ) - 1;
                if ((sampleSurfaceY - basketBase.getY()) >= BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD) {
                    highWallSamples++;
                }
                totalSamples++;
            }
        }

        return totalSamples > 0
                && highWallSamples >= Math.max(12, (int) Math.ceil(totalSamples * BASKET_ENCLOSURE_HIGH_WALL_RATIO));
    }

    private static FinishPlayability evaluateFinishPlayability(ServerWorld world, BlockPos finishOrigin, BlockPos basketBase) {
        int hazardColumns = countFinishHazardColumns(world, finishOrigin, basketBase);
        int greenSafeColumns = countSafeLandingColumns(world, basketBase, FINISH_GREEN_SAFE_SCAN_RADIUS);
        int approachSafeSamples = countApproachSafeSamples(world, finishOrigin, basketBase);
        return new FinishPlayability(hazardColumns, greenSafeColumns, approachSafeSamples);
    }

    private static int countFinishHazardColumns(ServerWorld world, BlockPos finishOrigin, BlockPos basketBase) {
        int dx = basketBase.getX() - finishOrigin.getX();
        int dz = basketBase.getZ() - finishOrigin.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return 0;
        }

        int startStep = Math.max(0, steps - FINISH_APPROACH_SCAN_DISTANCE);
        double length = Math.max(1.0d, Math.sqrt((dx * (double) dx) + (dz * (double) dz)));
        double sideX = -dz / length;
        double sideZ = dx / length;
        int waterColumns = 0;

        for (int i = startStep; i <= steps; i += 2) {
            double t = i / (double) steps;
            double centerX = finishOrigin.getX() + (dx * t);
            double centerZ = finishOrigin.getZ() + (dz * t);

            for (int offset = -FINISH_HAZARD_SCAN_HALF_WIDTH; offset <= FINISH_HAZARD_SCAN_HALF_WIDTH; offset += 2) {
                int sampleX = (int) Math.round(centerX + (sideX * offset));
                int sampleZ = (int) Math.round(centerZ + (sideZ * offset));
                if (isWaterColumn(world, sampleX, sampleZ)) {
                    waterColumns++;
                }
            }
        }

        return waterColumns;
    }

    private static int countSafeLandingColumns(ServerWorld world, BlockPos center, int radius) {
        int safeColumns = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = center.getX() + dx;
                int sampleZ = center.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (isSafeLandingSurface(world, sample)) {
                    safeColumns++;
                }
            }
        }
        return safeColumns;
    }

    private static int countApproachSafeSamples(ServerWorld world, BlockPos finishOrigin, BlockPos basketBase) {
        int dx = basketBase.getX() - finishOrigin.getX();
        int dz = basketBase.getZ() - finishOrigin.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return 0;
        }

        int startStep = Math.max(0, steps - FINISH_APPROACH_SCAN_DISTANCE);
        int safeSamples = 0;
        for (int i = startStep; i < steps; i += FINISH_APPROACH_SAMPLE_INTERVAL) {
            double t = i / (double) steps;
            int sampleX = (int) Math.round(finishOrigin.getX() + (dx * t));
            int sampleZ = (int) Math.round(finishOrigin.getZ() + (dz * t));
            if (hasAnySafeLandingNearby(world, sampleX, sampleZ, FINISH_APPROACH_SAFE_RADIUS)) {
                safeSamples++;
            }
        }
        return safeSamples;
    }

    private static BlockPos alternateFinishOrigin(BlockPos teePos, BlockPos basketBase, BlockPos alternateAnchor) {
        if (alternateAnchor == null) {
            return teePos;
        }
        return alternateAnchor.getSquaredDistance(basketBase) < teePos.getSquaredDistance(basketBase)
                ? alternateAnchor
                : teePos;
    }

    private static boolean isOpenSpace(BlockState state) {
        return state.isAir() || state.getBlock() instanceof PlantBlock;
    }

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        int x = (a.getX() + b.getX()) / 2;
        int y = (a.getY() + b.getY()) / 2;
        int z = (a.getZ() + b.getZ()) / 2;
        return new BlockPos(x, y, z);
    }

    private record FinishPlayability(int hazardColumns, int greenSafeColumns, int approachSafeSamples) {
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "unknown";
        }
        return key.getValue().getPath();
    }

    public record ValidationIssue(int holeIndex, String code, String message, BlockPos position) {
    }

    public record ValidationReport(
            String scenario,
            long seed,
            String biome,
            List<ValidationIssue> issues,
            Map<String, Integer> metrics
    ) {
        public ValidationReport {
            issues = List.copyOf(issues);
            metrics = Map.copyOf(metrics);
        }

        public boolean passed() {
            return issues.isEmpty();
        }

        public int issueCount() {
            return issues.size();
        }
    }
}
