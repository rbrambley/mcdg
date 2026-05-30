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
    private static final int LANDING_GAP_WARNING_BLOCKS = 95;
    private static final int LANDING_GAP_FAIL_BLOCKS = 115;
    private static final int ALT_ROUTE_REQUIRED_CARRY_BLOCKS = 72;
    private static final int LANDING_SCAN_RADIUS = 6;
    private static final int TEE_LAUNCH_CHECK_DISTANCE = 22;
    private static final int TEE_LAUNCH_CHECK_HALF_WIDTH = 3;
    private static final int BASKET_ENCLOSURE_SCAN_RADIUS = 6;

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

            int longestGap = computeLongestWaterCarryGap(world, teePos, basketPos.down());
            maxLandingGapObserved = Math.max(maxLandingGapObserved, longestGap);
            BlockPos alternateAnchor = placedCourseState.holeAlternateAnchors().get(holeIndex);
            if (longestGap > ALT_ROUTE_REQUIRED_CARRY_BLOCKS && alternateAnchor == null) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "alternate_route_missing",
                        "Long water carry (" + longestGap + " blocks) requires alternate fairway anchor.",
                        midpoint(teePos, basketPos.down())
                ));
                holeFailed = true;
            }
            if (longestGap > LANDING_GAP_FAIL_BLOCKS) {
                issues.add(new ValidationIssue(
                        holeIndex,
                        "landing_gap_too_long",
                        "Longest no-landing gap is " + longestGap + " blocks (max " + LANDING_GAP_FAIL_BLOCKS + ").",
                        midpoint(teePos, basketPos.down())
                ));
                holeFailed = true;
            } else if (longestGap > LANDING_GAP_WARNING_BLOCKS) {
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
        if (centerDepth >= 8) {
            return true;
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
                if ((sampleSurfaceY - basketBase.getY()) >= 6) {
                    highWallSamples++;
                }
                totalSamples++;
            }
        }

        return totalSamples > 0 && highWallSamples >= Math.max(10, (int) Math.ceil(totalSamples * 0.65));
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
