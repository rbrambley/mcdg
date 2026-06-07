package com.mcdg.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;

/**
 * Carves fairways, clears vegetation, and enforces landing zones.
 */
final class FairwayCarver {
    private FairwayCarver() {}

    static void carveFairway(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            int width,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            boolean placeLanterns
    ) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps < 1) {
            steps = 1;
        }
        int stepStride = 1;

        int radius = Math.max(1, width / 2);
        int lastLanternStep = Integer.MIN_VALUE;
        int lastWaterPatchStep = Integer.MIN_VALUE;
        int waterCarryStreak = 0;
        Set<BlockPos> clearedTreeNodes = new HashSet<>();
        int dirX = Integer.compare(endX, startX);
        int dirZ = Integer.compare(endZ, startZ);
        int sideX = -dirZ;
        int sideZ = dirX;
        if (sideX == 0 && sideZ == 0) {
            sideX = 1;
            sideZ = 0;
        }

        for (int i = 0; i <= steps; i += stepStride) {
            double t = i / (double) steps;
            int x = (int) Math.round(startX + (endX - startX) * t);
            int z = (int) Math.round(startZ + (endZ - startZ) * t);

            boolean waterColumn = CoursePlacementService.isWaterCrossingColumn(world, x, z);
            if (waterColumn) {
                waterCarryStreak++;
            } else {
                waterCarryStreak = 0;
            }

            if (i > 0
                    && i < steps
                    && waterColumn
                    && (waterCarryStreak >= CoursePlacementConfig.WaterLanding.PATCH_MAX_CARRY || i - lastWaterPatchStep >= CoursePlacementConfig.WaterLanding.PATCH_INTERVAL)) {
                BlockPos waterSurface = SurfaceResolver.resolveSurfacePos(world, x, z);
                BlockPos landingCenter = CoursePlacementService.ensureWaterLandingSurface(
                        world,
                        waterSurface,
                        CoursePlacementConfig.WaterLanding.PATCH_RADIUS,
                        originalBlocks,
                        protectedPositions
                );
                CoursePlacementService.clearHeadroom(world, landingCenter, CoursePlacementConfig.WaterLanding.PATCH_RADIUS, 5, originalBlocks, protectedPositions);
                CoursePlacementService.addProtectedColumnArea(protectedPositions, landingCenter, CoursePlacementConfig.WaterLanding.PATCH_RADIUS, 5);
                lastWaterPatchStep = i;
                waterCarryStreak = 0;
            }

            BlockPos center = SurfaceResolver.findPreferredSurfacePos(world, x, z, true, CoursePlacementConfig.SearchRadii.FAIRWAY);
            int tunedRadius = Math.min(2, CoursePlacementService.tunedPathRadius(world, center, radius));
            if (steps - i <= CoursePlacementConfig.FinishGreen.APPROACH_WIDEN_DISTANCE
                    && CoursePlacementService.isWaterAdjacentArea(world, center, CoursePlacementConfig.WaterLanding.ENFORCE_SCAN_RADIUS, CoursePlacementConfig.WaterLanding.ADJACENT_MIN_COLUMNS)) {
                tunedRadius = Math.max(tunedRadius, Math.min(3, radius + 1));
            }
            BlockState pathState = CoursePlacementService.selectPathMaterial(world, center);

            for (int dx = -tunedRadius; dx <= tunedRadius; dx++) {
                for (int dz = -tunedRadius; dz <= tunedRadius; dz++) {
                    if ((dx * dx) + (dz * dz) > (tunedRadius * tunedRadius)) {
                        continue;
                    }

                    int sampleX = center.getX() + dx;
                    int sampleZ = center.getZ() + dz;
                    BlockPos surface = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, sampleX, sampleZ));
                    int pathY = surface.getY();

                    // Keep fairway natural: only remove vegetation/tree material up to local canopy height.
                    CoursePlacementService.clearFairwayColumnVegetation(
                            world,
                            sampleX,
                            sampleZ,
                            pathY,
                            originalBlocks,
                            protectedPositions,
                            clearedTreeNodes
                    );

                    BlockPos pathPos = new BlockPos(sampleX, pathY, sampleZ);
                    if (CoursePlacementService.isProtected(protectedPositions, pathPos)) {
                        continue;
                    }
                    if (world.getBlockState(pathPos).equals(pathState)) {
                        continue;
                    }

                    PlacementUtils.setTrackedBlock(world, pathPos, pathState, originalBlocks);
                }
            }

                    // Run an explicit sweep around the step center so trunk columns adjacent to the path do not survive.
                    CoursePlacementService.clearFairwaySweepVolume(
                        world,
                        center,
                        tunedRadius + CoursePlacementConfig.Fairway.LOG_SWEEP_EXTRA_RADIUS,
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                    );

            // Add occasional light posts for navigation, keeping spacing so paths do not feel cluttered.
            if (placeLanterns && i - lastLanternStep >= 12 && shouldPlaceFairwayLantern(center.getX(), center.getZ())) {
                int lanternSide = (coordinateNoise(center.getX() * 31, center.getZ() * 17) & 1) == 0 ? 1 : -1;
                int lanternX = center.getX() + (sideX * (tunedRadius + 1) * lanternSide);
                int lanternZ = center.getZ() + (sideZ * (tunedRadius + 1) * lanternSide);
                BlockPos lanternBase = CoursePlacementService.ensureLandIslandSurface(world,
                        SurfaceResolver.findPreferredSurfacePos(world, lanternX, lanternZ, true, CoursePlacementConfig.SearchRadii.FAIRWAY), 1, originalBlocks, protectedPositions);
                if (CoursePlacementService.isProtected(protectedPositions, lanternBase.up())) {
                    continue;
                }
                CoursePlacementService.placeLanternPost(world, lanternBase, 2, originalBlocks);
                lastLanternStep = i;
            }
        }

        enforceWaterLandingContinuity(
                world,
                startX,
                startZ,
                endX,
                endZ,
                originalBlocks,
                protectedPositions
        );
    }

    static void clearConnectedTreeCluster(
            ServerWorld world,
            BlockPos root,
            int minY,
            int maxY,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        if (clearedTreeNodes.contains(root)) {
            return;
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root.toImmutable());

        int cleared = 0;
        while (!queue.isEmpty() && cleared < CoursePlacementConfig.Fairway.TREE_CLUSTER_CLEAR_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!clearedTreeNodes.add(pos)) {
                continue;
            }
            if (pos.getY() < minY || pos.getY() > maxY) {
                continue;
            }
            if (CoursePlacementService.isProtected(protectedPositions, pos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            boolean treeMaterial = state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
            if (!treeMaterial) {
                continue;
            }

            PlacementUtils.setTrackedBlock(world, pos, Blocks.AIR.getDefaultState(), originalBlocks);
            cleared++;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = pos.add(dx, dy, dz).toImmutable();
                        if (!clearedTreeNodes.contains(next)) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
    }

    static void enforceBasketApproachLandingZone(
            ServerWorld world,
            BlockPos approachStart,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int approachGap = CoursePlacementService.computeLongestWaterCarryGap(world, approachStart, basketSurface);
        if (approachGap <= CoursePlacementConfig.Tee.MAX_DIRECT_CARRY_GAP) {
            return;
        }

        int dx = basketSurface.getX() - approachStart.getX();
        int dz = basketSurface.getZ() - approachStart.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int zoneSteps = Math.min(steps, CoursePlacementConfig.Basket.APPROACH_ENFORCE_DISTANCE);
        int zoneStartStep = Math.max(0, steps - zoneSteps);
        double startT = zoneStartStep / (double) steps;
        int zoneStartX = (int) Math.round(approachStart.getX() + (dx * startT));
        int zoneStartZ = (int) Math.round(approachStart.getZ() + (dz * startT));

        carveFairway(
                world,
                zoneStartX,
                zoneStartZ,
                basketSurface.getX(),
                basketSurface.getZ(),
                Math.max(fairwayWidth, CoursePlacementConfig.Basket.APPROACH_MIN_WIDTH),
                originalBlocks,
                protectedPositions,
                false
        );
    }

    static void enforceWaterLandingContinuity(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int currentGap = 0;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(startX + (dx * t));
            int z = (int) Math.round(startZ + (dz * t));

            if (!CoursePlacementService.isWaterCrossingColumn(world, x, z)) {
                currentGap = 0;
                continue;
            }

            if (CoursePlacementService.hasAnyWalkableLandingNearby(world, x, z, CoursePlacementConfig.WaterLanding.ENFORCE_SCAN_RADIUS)) {
                currentGap = 0;
                continue;
            }

            currentGap++;
            if (currentGap <= CoursePlacementConfig.WaterLanding.ENFORCE_MAX_GAP) {
                continue;
            }

            BlockPos landingCenter = CoursePlacementService.ensureLandIslandSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, x, z),
                    CoursePlacementConfig.WaterLanding.PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
            );
                landingCenter = CoursePlacementService.ensureWaterLandingSurface(
                    world,
                    landingCenter,
                    CoursePlacementConfig.WaterLanding.PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
                );
            CoursePlacementService.clearHeadroom(world, landingCenter, CoursePlacementConfig.WaterLanding.PATCH_RADIUS, 5, originalBlocks, protectedPositions);
            CoursePlacementService.addProtectedColumnArea(protectedPositions, landingCenter, CoursePlacementConfig.WaterLanding.PATCH_RADIUS, 5);
            currentGap = 0;
        }
    }

    static void createSafeFairwayLandingZone(
            ServerWorld world,
            BlockPos anchor,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int halfWidth = CoursePlacementConfig.WaterLanding.SAFE_FAIRWAY_HALF_WIDTH;
        int minLength = CoursePlacementConfig.WaterLanding.SAFE_FAIRWAY_MIN_LENGTH;
        int sandThickness = 1;

        int dx = basketSurface.getX() - anchor.getX();
        int dz = basketSurface.getZ() - anchor.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 1) { return; }

        double dirX = dx / distance;
        double dirZ = dz / distance;
        double perpX = -dirZ;
        double perpZ = dirX;

        // Leave a clear margin before the basket so the landing zone never overwrites basket blocks.
        int basketMargin = halfWidth + 2;
        int actualLength = (int) Math.min(minLength, Math.max(0, distance - basketMargin));
        int surfaceY = SurfaceResolver.resolveSurfacePos(world, anchor.getX(), anchor.getZ()).getY();
        int platformY = Math.max(surfaceY, world.getSeaLevel());

        // Protect the basket column so fill never clobbers the hopper/bars/lantern placed later.
        CoursePlacementService.addProtectedColumnArea(protectedPositions, basketSurface, halfWidth + 1, 6);

        for (int step = -halfWidth; step <= actualLength + halfWidth; step++) {
            int centerX = (int) Math.round(anchor.getX() + dirX * step);
            int centerZ = (int) Math.round(anchor.getZ() + dirZ * step);

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx = (int) Math.round(centerX + perpX * w);
                int wz = (int) Math.round(centerZ + perpZ * w);

                boolean isEdge = (Math.abs(w) >= halfWidth - sandThickness) || (step < 0) || (step > actualLength);

                int seabedY = Math.max(world.getBottomY() + 1, world.getTopY(Heightmap.Type.OCEAN_FLOOR, wx, wz) - 1);

                for (int y = seabedY; y < platformY; y++) {
                    BlockPos fillPos = new BlockPos(wx, y, wz);
                    if (CoursePlacementService.isProtected(protectedPositions, fillPos)) { continue; }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (CoursePlacementService.isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }

                BlockPos surfacePos = new BlockPos(wx, platformY, wz);
                if (CoursePlacementService.isProtected(protectedPositions, surfacePos)) { continue; }
                BlockState surfaceState = world.getBlockState(surfacePos);
                if (CoursePlacementService.isFillReplaceable(surfaceState)) {
                    BlockState newSurface = isEdge ? Blocks.SAND.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                    PlacementUtils.setTrackedBlock(world, surfacePos, newSurface, originalBlocks);
                }
            }
        }

        BlockPos fairwayCenter = new BlockPos(
                (int) Math.round(anchor.getX() + dirX * (actualLength / 2.0)),
                platformY,
                (int) Math.round(anchor.getZ() + dirZ * (actualLength / 2.0)));
        int clearRadius = halfWidth + 2;
        CoursePlacementService.clearHeadroom(world, fairwayCenter, clearRadius, 8, originalBlocks, protectedPositions);
        CoursePlacementService.addProtectedColumnArea(protectedPositions, fairwayCenter, clearRadius, 8);
    }

    static BlockPos ensureBasketGreenLandingZone(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos expanded = basketSurface;
        int targetRadius = Math.max(CoursePlacementConfig.WaterLanding.ADJACENT_BASKET_GREEN_RADIUS, CoursePlacementService.resolveFinishGreenRadius(0, fairwayWidth));
        int currentRadius = Math.max(1, targetRadius);

        for (int pass = 0; pass < 5; pass++) {
            boolean waterAdjacent = CoursePlacementService.isWaterAdjacentArea(
                world,
                expanded,
                CoursePlacementConfig.WaterLanding.ADJACENT_SCAN_RADIUS,
                CoursePlacementConfig.WaterLanding.ADJACENT_MIN_COLUMNS
            );
            if (waterAdjacent) {
            expanded = CoursePlacementService.ensureWaterLandingSurface(
                world,
                expanded,
                currentRadius,
                originalBlocks,
                protectedPositions
            );
            } else {
            expanded = CoursePlacementService.ensureLandIslandSurface(
                world,
                expanded,
                currentRadius,
                originalBlocks,
                protectedPositions
            );
            }

            CoursePlacementService.clearHeadroom(
                    world,
                    expanded,
                    currentRadius,
                    6,
                    originalBlocks,
                    protectedPositions
            );
            CoursePlacementService.shapePlayableFinishApproach(
                    world,
                    finishOrigin,
                    expanded,
                    fairwayWidth,
                    currentRadius,
                    CoursePlacementService.countFinishHazardColumns(world, finishOrigin, expanded),
                    originalBlocks,
                    protectedPositions
            );

            if (CoursePlacementService.countSafeLandingColumns(world, expanded, 8) >= CoursePlacementConfig.FinishGreen.MIN_SAFE_COLUMNS) {
                if (CoursePlacementService.isDeeplyEnclosedBasketSurface(world, expanded)) {
                    BlockPos recovered = CoursePlacementService.tryRecoverEnclosedBasketSurface(
                            world,
                            teeSurface,
                            expanded,
                            originalBlocks,
                            protectedPositions
                    );
                    if (recovered != null && !CoursePlacementService.isDeeplyEnclosedBasketSurface(world, recovered)) {
                        return recovered;
                    }

                    BlockPos relocated = CoursePlacementService.relocateBasketSurfaceIfNeeded(world, teeSurface, expanded);
                    if (!CoursePlacementService.isDeeplyEnclosedBasketSurface(world, relocated)) {
                        return relocated;
                    }
                } else {
                    return expanded;
                }
            }

            currentRadius = Math.min(CoursePlacementConfig.FinishGreen.MAX_RADIUS, currentRadius + 2);
            if (currentRadius >= CoursePlacementConfig.FinishGreen.MAX_RADIUS) {
                break;
            }
        }

        return expanded;
    }

    static int resolveHoleFairwayWidth(Hole hole) {
        int width = 4;
        for (FairwaySegment segment : hole.fairwaySegments()) {
            width = Math.max(width, segment.width());
        }
        return Math.max(3, Math.min(5, width));
    }

    static boolean shouldPlaceFairwayLantern(int x, int z) {
        int noise = coordinateNoise(x, z);
        return Math.floorMod(noise, 100) < 17;
    }

    static int coordinateNoise(int x, int z) {
        int hash = x * 73428767;
        hash ^= z * 912673;
        hash ^= (hash >>> 13);
        hash *= 1274126177;
        return hash;
    }

}
