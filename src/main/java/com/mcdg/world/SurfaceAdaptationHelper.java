package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SurfaceAdaptationHelper {
    private SurfaceAdaptationHelper() {
    }

    private static final int WATER_ADJACENT_BASKET_GREEN_RADIUS = CoursePlacementConfig.WaterLanding.ADJACENT_BASKET_GREEN_RADIUS;
    private static final int FINISH_APPROACH_SCAN_DISTANCE = CoursePlacementConfig.FinishGreen.APPROACH_SCAN_DISTANCE;
    private static final int FINISH_APPROACH_BASE_RADIUS = CoursePlacementConfig.FinishGreen.APPROACH_BASE_RADIUS;
    private static final int FINISH_APPROACH_MAX_EXTRA_RADIUS = CoursePlacementConfig.FinishGreen.APPROACH_MAX_EXTRA_RADIUS;
    private static final int FINISH_APPROACH_SAMPLE_INTERVAL = CoursePlacementConfig.FinishGreen.APPROACH_SAMPLE_INTERVAL;
    private static final int FINISH_HAZARD_SCAN_HALF_WIDTH = CoursePlacementConfig.FinishGreen.HAZARD_SCAN_HALF_WIDTH;
    private static final int TEE_ISLAND_RADIUS = CoursePlacementConfig.Islands.TEE_RADIUS;
    private static final int BASKET_ISLAND_RADIUS = CoursePlacementConfig.Islands.BASKET_RADIUS;
    private static final int TEE_RELOCATION_RADIUS = CoursePlacementConfig.Tee.RELOCATION_RADIUS;
    private static final int BASKET_RELOCATION_RADIUS = CoursePlacementConfig.Basket.RELOCATION_RADIUS;
    private static final int BASKET_ENCLOSURE_SCAN_RADIUS = CoursePlacementConfig.Basket.ENCLOSURE_SCAN_RADIUS;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_FAIL = CoursePlacementConfig.Basket.ENCLOSURE_CENTER_DEPTH_FAIL;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_CHECK = CoursePlacementConfig.Basket.ENCLOSURE_CENTER_DEPTH_CHECK;
    private static final int BASKET_ENCLOSURE_RECOVERY_MIN_DEPTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MIN_DEPTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MAX_DEPTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_WIDTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_WIDTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_HEADROOM = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_HEADROOM;
    private static final int BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_LATERAL_STEP;
    private static final int BASKET_ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS;
    private static final int BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD = CoursePlacementConfig.Basket.ENCLOSURE_WALL_DEPTH_THRESHOLD;
    private static final double BASKET_ENCLOSURE_HIGH_WALL_RATIO = CoursePlacementConfig.Basket.ENCLOSURE_HIGH_WALL_RATIO;
    private static final int TEE_WALL_SCAN_RADIUS = CoursePlacementConfig.Tee.WALL_SCAN_RADIUS;
    private static final int TEE_MAX_ENCLOSURE_SCORE = CoursePlacementConfig.Tee.MAX_ENCLOSURE_SCORE;
    private static final int TEE_PREFILTER_ENCLOSURE_DEPTH_FAIL = CoursePlacementConfig.Tee.PREFILTER_ENCLOSURE_DEPTH_FAIL;
    private static final int TEE_PIT_DEPTH_THRESHOLD = CoursePlacementConfig.Tee.PIT_DEPTH_THRESHOLD;

    static BlockPos ensureLandIslandSurface(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos groundedCenter = SurfaceResolver.resolveSurfacePos(world, center.getX(), center.getZ());
        if (!isUnsafeSurface(world, groundedCenter)) {
            return groundedCenter;
        }

        // Build the island from seabed level so we never create floating canopy dirt.
        int seabedY = Math.max(
                world.getBottomY() + 1,
                world.getTopY(Heightmap.Type.OCEAN_FLOOR, center.getX(), center.getZ()) - 1
        );
        int islandY = Math.max(seabedY, world.getSeaLevel()) + 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int bx = center.getX() + dx;
                int bz = center.getZ() + dz;
                int columnSeabedY = Math.max(
                        world.getBottomY() + 1,
                        world.getTopY(Heightmap.Type.OCEAN_FLOOR, bx, bz) - 1
                );

                // Fill the column upward from the seabed to just below the island surface with dirt.
                for (int y = columnSeabedY; y < islandY; y++) {
                    BlockPos fillPos = new BlockPos(bx, y, bz);
                    if (PlacementUtils.isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }
                // Grass on top.
                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (PlacementUtils.isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    PlacementUtils.setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

    static BlockPos ensureWaterLandingSurface(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int seaLevel = world.getSeaLevel();
        int islandY = Math.max(
                world.getBottomY() + 2,
                Math.min(seaLevel, center.getY())
        );

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 2)) {
                    continue;
                }

                int bx = center.getX() + dx;
                int bz = center.getZ() + dz;
                int columnSeabedY = Math.max(
                        world.getBottomY() + 1,
                        world.getTopY(Heightmap.Type.OCEAN_FLOOR, bx, bz) - 1
                );

                for (int y = columnSeabedY; y < islandY; y++) {
                    BlockPos fillPos = new BlockPos(bx, y, bz);
                    if (PlacementUtils.isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }

                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (PlacementUtils.isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    PlacementUtils.setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

        static BlockPos expandBasketGreenIfWaterNearby(
            ServerWorld world,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        return FairwayCarver.ensureBasketGreenLandingZone(
            world,
            finishOrigin,
            finishOrigin,
            basketSurface,
            fairwayWidth,
            originalBlocks,
            protectedPositions
        );
    }

    static int resolveFinishGreenRadius(int finishHazardColumns, int fairwayWidth) {
        int hazardBonus = Math.min(FINISH_APPROACH_MAX_EXTRA_RADIUS, finishHazardColumns / 20);
        int widthBonus = fairwayWidth >= 5 ? 1 : 0;
        return Math.max(
                WATER_ADJACENT_BASKET_GREEN_RADIUS,
                BASKET_ISLAND_RADIUS + 2 + hazardBonus + widthBonus
        );
    }

    static void shapePlayableFinishApproach(
            ServerWorld world,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            int greenRadius,
            int finishHazardColumns,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basketSurface.getX() - finishOrigin.getX();
        int dz = basketSurface.getZ() - finishOrigin.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int startStep = Math.max(0, steps - FINISH_APPROACH_SCAN_DISTANCE);
        int approachRadius = Math.max(
                FINISH_APPROACH_BASE_RADIUS,
                Math.min(
                        greenRadius,
                        (fairwayWidth / 2) + FINISH_APPROACH_BASE_RADIUS + Math.min(FINISH_APPROACH_MAX_EXTRA_RADIUS, finishHazardColumns / 24)
                )
        );

        for (int i = startStep; i < steps; i += FINISH_APPROACH_SAMPLE_INTERVAL) {
            double t = i / (double) steps;
            int x = (int) Math.round(finishOrigin.getX() + (dx * t));
            int z = (int) Math.round(finishOrigin.getZ() + (dz * t));
            int distanceToBasket = steps - i;
            int radius = Math.max(
                    FINISH_APPROACH_BASE_RADIUS,
                    approachRadius - Math.max(0, distanceToBasket - 12) / 10
            );

                boolean reinforceFinalWindow = distanceToBasket <= 18;
                if (!reinforceFinalWindow
                    && !CoursePlacementService.isWaterCrossingColumn(world, x, z)
                    && CoursePlacementService.hasAnyWalkableLandingNearby(world, x, z, Math.max(3, radius - 1))) {
                continue;
            }

            BlockPos apronCenter = ensureWaterLandingSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, x, z),
                    radius,
                    originalBlocks,
                    protectedPositions
            );
            PlacementUtils.clearHeadroom(world, apronCenter, radius, 5, originalBlocks, protectedPositions);
            PlacementUtils.addProtectedColumnArea(protectedPositions, apronCenter, Math.max(2, radius - 1), 5);
        }
    }

    static int countFinishHazardColumns(ServerWorld world, BlockPos finishOrigin, BlockPos basketSurface) {
        int dx = basketSurface.getX() - finishOrigin.getX();
        int dz = basketSurface.getZ() - finishOrigin.getZ();
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
                if (CoursePlacementService.isWaterCrossingColumn(world, sampleX, sampleZ)) {
                    waterColumns++;
                }
            }
        }

        return waterColumns;
    }

    static int countSafeLandingColumns(ServerWorld world, BlockPos center, int radius) {
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
                if (isWalkableGround(world, sample)) {
                    safeColumns++;
                }
            }
        }
        return safeColumns;
    }

    static boolean isUnsafeSurface(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return true;
        }
        if (state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.WATER)
                || state.isOf(Blocks.KELP)
                || state.isOf(Blocks.SEAGRASS)
                || state.isOf(Blocks.TALL_SEAGRASS)) {
            return true;
        }
        if (state.getBlock() instanceof PlantBlock || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        // Solid ground but submerged — the block above is fluid, so standing here would be underwater.
        return !world.getBlockState(pos.up()).getFluidState().isEmpty();
    }

    static boolean isWalkableGround(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (isUnsafeSurface(world, pos)) {
            return false;
        }
        if (!ground.isSolidBlock(world, pos)) {
            return false;
        }

        BlockState above = world.getBlockState(pos.up());
        BlockState above2 = world.getBlockState(pos.up(2));
        return SurfaceResolver.isOpenHeadspace(above) && SurfaceResolver.isOpenHeadspace(above2);
    }

    static BlockPos relocateTeeSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (SurfaceResolver.isPlayableTeeSurface(world, teeSurface)) {
            return teeSurface;
        }

        BlockPos best = teeSurface;
        int bestScore = Integer.MAX_VALUE;
        for (int searchRadius : new int[] { TEE_RELOCATION_RADIUS, TEE_RELOCATION_RADIUS * 2 }) {
            int step = searchRadius <= TEE_RELOCATION_RADIUS ? 2 : 4;
            for (int dx = -searchRadius; dx <= searchRadius; dx += step) {
                for (int dz = -searchRadius; dz <= searchRadius; dz += step) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq > (searchRadius * searchRadius)) {
                        continue;
                    }

                    BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                            world,
                            SurfaceResolver.resolveSurfacePos(world, teeSurface.getX() + dx, teeSurface.getZ() + dz)
                    );
                    if (!SurfaceResolver.isPlayableTeeSurface(world, candidate)) {
                        continue;
                    }

                    int score = distSq;
                    score += Math.max(0, Math.abs(candidate.getY() - teeSurface.getY()) - 1) * 10;
                    score += Math.abs(candidate.getX() - basketSurface.getX()) + Math.abs(candidate.getZ() - basketSurface.getZ());
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }

            if (bestScore != Integer.MAX_VALUE) {
                return best;
            }
        }

        return best;
    }

    static BlockPos relocateBasketSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (SurfaceResolver.isPlayableBasketSurface(world, basketSurface)) {
            return basketSurface;
        }

        BlockPos best = basketSurface;
        int bestScore = Integer.MAX_VALUE;
        int step = 2;
        int baselineSpan = Math.abs(basketSurface.getX() - teeSurface.getX()) + Math.abs(basketSurface.getZ() - teeSurface.getZ());

        for (int dx = -BASKET_RELOCATION_RADIUS; dx <= BASKET_RELOCATION_RADIUS; dx += step) {
            for (int dz = -BASKET_RELOCATION_RADIUS; dz <= BASKET_RELOCATION_RADIUS; dz += step) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (BASKET_RELOCATION_RADIUS * BASKET_RELOCATION_RADIUS)) {
                    continue;
                }

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, basketSurface.getX() + dx, basketSurface.getZ() + dz)
                );
                if (!SurfaceResolver.isPlayableBasketSurface(world, candidate)) {
                    continue;
                }

                int span = Math.abs(candidate.getX() - teeSurface.getX()) + Math.abs(candidate.getZ() - teeSurface.getZ());
                int score = distSq;
                score += Math.max(0, Math.abs(candidate.getY() - basketSurface.getY()) - 1) * 8;
                score += Math.abs(span - baselineSpan) * 3;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        return best;
    }

    static BlockPos tryRecoverEnclosedBasketSurface(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, basketSurface.getX(), basketSurface.getZ()) - 1;
        int centerDepth = centerSurfaceY - basketSurface.getY();
        if (centerDepth < BASKET_ENCLOSURE_RECOVERY_MIN_DEPTH || centerDepth > BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH) {
            return null;
        }

        int[] lateralOffsets = {
                0,
                BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP,
                -BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP,
                BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP * 2
        };

        int maxAttempts = Math.min(lateralOffsets.length - 1, BASKET_ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS);
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            if (buildBasketRecoveryCorridor(
                    world,
                    teeSurface,
                    basketSurface,
                    lateralOffsets[attempt],
                    originalBlocks,
                    protectedPositions
            )) {
                return basketSurface;
            }
        }

        return null;
    }

    static boolean buildBasketRecoveryCorridor(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            int lateralOffset,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = teeSurface.getX() - basketSurface.getX();
        int dz = teeSurface.getZ() - basketSurface.getZ();
        int stepsToTee = Math.max(Math.abs(dx), Math.abs(dz));
        if (stepsToTee < 4) {
            return false;
        }

        int maxSteps = Math.min(stepsToTee, Math.max(24, BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH * 3));
        int halfWidth = Math.max(1, BASKET_ENCLOSURE_RECOVERY_WIDTH / 2);
        int sideX = -Integer.compare(dz, 0);
        int sideZ = Integer.compare(dx, 0);
        if (sideX == 0 && sideZ == 0) {
            sideX = 1;
            sideZ = 0;
        }

        int currentY = basketSurface.getY();
        int emergedSteps = 0;

        for (int step = 0; step <= maxSteps; step++) {
            double t = step / (double) stepsToTee;
            int rowX = (int) Math.round(basketSurface.getX() + (dx * t)) + (sideX * lateralOffset);
            int rowZ = (int) Math.round(basketSurface.getZ() + (dz * t)) + (sideZ * lateralOffset);
            int localSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, rowX, rowZ) - 1;

            if (currentY < localSurfaceY) {
                int remainingSteps = Math.max(1, maxSteps - step + 1);
                int remainingRise = localSurfaceY - currentY;
                int climb = 1;
                if (remainingRise > remainingSteps) {
                    climb = 2;
                }
                currentY += Math.min(2, climb);
            }

            if (rowHasLava(world, rowX, rowZ, currentY, halfWidth)) {
                return false;
            }

            carveRecoveryRow(
                    world,
                    rowX,
                    rowZ,
                    currentY,
                    halfWidth,
                    originalBlocks,
                    protectedPositions
            );

            if (currentY >= localSurfaceY) {
                emergedSteps++;
            } else {
                emergedSteps = 0;
            }

            if (emergedSteps >= 4) {
                return true;
            }
        }

        return false;
    }

    static boolean rowHasLava(ServerWorld world, int rowX, int rowZ, int rowY, int halfWidth) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = rowX + dx;
                int z = rowZ + dz;
                if (isLavaColumn(world, x, z, rowY)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isLavaColumn(ServerWorld world, int x, int z, int referenceY) {
        int worldSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos worldSurface = new BlockPos(x, worldSurfaceY, z);
        BlockState surfaceState = world.getBlockState(worldSurface);
        if (surfaceState.isOf(Blocks.LAVA) || surfaceState.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA)) {
            return true;
        }

        for (int y = Math.max(world.getBottomY() + 1, referenceY - 2); y <= referenceY + 2; y++) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (state.isOf(Blocks.LAVA) || state.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA)) {
                return true;
            }
        }

        return false;
    }

    static void carveRecoveryRow(
            ServerWorld world,
            int rowX,
            int rowZ,
            int rowY,
            int halfWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = rowX + dx;
                int z = rowZ + dz;

                BlockPos center = new BlockPos(x, rowY, z);
                if (PlacementUtils.isProtected(protectedPositions, center)) {
                    continue;
                }

                if (CoursePlacementService.isWaterCrossingColumn(world, x, z)) {
                    ensureWaterLandingSurface(world, center, 1, originalBlocks, protectedPositions);
                }

                PlacementUtils.setTrackedBlock(world, center, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);

                BlockPos below = center.down();
                if (!world.getBlockState(below).isSolidBlock(world, below) || !world.getBlockState(below).getFluidState().isEmpty()) {
                    PlacementUtils.setTrackedBlock(world, below, Blocks.DIRT.getDefaultState(), originalBlocks);
                }

                int localSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                int clearTop = Math.max(rowY + BASKET_ENCLOSURE_RECOVERY_HEADROOM, localSurfaceY + 2);
                for (int y = rowY + 1; y <= clearTop; y++) {
                    BlockPos clearPos = new BlockPos(x, y, z);
                    if (PlacementUtils.isProtected(protectedPositions, clearPos)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(clearPos);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    PlacementUtils.setTrackedBlock(world, clearPos, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }
    }

    static boolean hasFluidInBasketMarkerColumn(ServerWorld world, BlockPos basketSurface, int height) {
        BlockPos base = basketSurface.up();
        for (int i = 0; i <= Math.max(1, height); i++) {
            if (!world.getFluidState(base.up(i)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean isDeeplyEnclosedBasketSurface(ServerWorld world, BlockPos basketSurface) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, basketSurface.getX(), basketSurface.getZ()) - 1;
        int centerDepth = centerSurfaceY - basketSurface.getY();
        if (centerDepth >= BASKET_ENCLOSURE_CENTER_DEPTH_FAIL) {
            return true;
        }

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
                        basketSurface.getX() + dx,
                        basketSurface.getZ() + dz
                ) - 1;
                if ((sampleSurfaceY - basketSurface.getY()) >= BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD) {
                    highWallSamples++;
                }
                totalSamples++;
            }
        }

        return totalSamples > 0
                && highWallSamples >= Math.max(12, (int) Math.ceil(totalSamples * BASKET_ENCLOSURE_HIGH_WALL_RATIO));
    }

    static boolean isDeeplyEnclosedTeeSurface(ServerWorld world, BlockPos teeSurface) {
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, teeSurface.getX(), teeSurface.getZ()) - 1;
        return (surfaceY - teeSurface.getY()) >= TEE_PREFILTER_ENCLOSURE_DEPTH_FAIL;
    }

    static boolean isLikelyPitSurface(ServerWorld world, BlockPos center) {
        int total = 0;
        int count = 0;
        int minNeighborY = Integer.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                BlockPos sample = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
                );
                total += sample.getY();
                count++;
                minNeighborY = Math.min(minNeighborY, sample.getY());
            }
        }

        if (count == 0) {
            return false;
        }

        int averageNeighborY = total / count;
        int depthFromAverage = averageNeighborY - center.getY();
        int depthFromMin = minNeighborY - center.getY();
        return depthFromAverage >= TEE_PIT_DEPTH_THRESHOLD && depthFromMin >= 2;
    }

    static boolean hasExcessiveTeeEnclosure(ServerWorld world, BlockPos center) {
        int enclosureScore = 0;

        for (int dx = -TEE_WALL_SCAN_RADIUS; dx <= TEE_WALL_SCAN_RADIUS; dx += 2) {
            for (int dz = -TEE_WALL_SCAN_RADIUS; dz <= TEE_WALL_SCAN_RADIUS; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int distSq = dx * dx + dz * dz;
                if (distSq < 12 || distSq > (TEE_WALL_SCAN_RADIUS * TEE_WALL_SCAN_RADIUS)) {
                    continue;
                }

                BlockPos sample = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
                );
                int heightDelta = sample.getY() - center.getY();
                if (heightDelta >= 3) {
                    enclosureScore += 2;
                } else if (heightDelta >= 2) {
                    enclosureScore += 1;
                }
            }
        }

        return enclosureScore >= TEE_MAX_ENCLOSURE_SCORE;
    }

    static int localWaterPenalty(ServerWorld world, BlockPos center) {
        int penalty = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos sample = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                if (isUnsafeSurface(world, sample) || isWaterBiome(world, sample)) {
                    penalty += 220;
                }
            }
        }
        return penalty;
    }

    static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        String id = PlacementUtils.biomeId(world.getBiome(pos));
        return id.contains("ocean") || id.contains("river") || id.contains("beach");
    }

    static boolean isFillReplaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.getBlock() instanceof PlantBlock;
    }

}
