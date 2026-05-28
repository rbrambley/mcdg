package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.game.PlacedCourseState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.block.entity.SignText;
import net.minecraft.world.biome.Biome;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

public final class CoursePlacementService {
    private static final int FAIRWAY_SEARCH_RADIUS = 3;
    private static final int HOLE_SEARCH_RADIUS = 40;
    private static final int ANCHOR_SEARCH_RADIUS = 120;
    private static final int FAIRWAY_CLEAR_BOTTOM_PADDING = 10;
    private static final int FAIRWAY_CLEAR_TOP_PADDING = 4;
    private static final int FAIRWAY_LOG_SWEEP_EXTRA_RADIUS = 1;
    private static final int TREE_CLUSTER_CLEAR_LIMIT = 1400;
    private static final int WATER_LANDING_PATCH_INTERVAL = 35;
    private static final int WATER_LANDING_PATCH_RADIUS = 6;
    private static final int WATER_LANDING_PATCH_MAX_CARRY = 24;
    private static final int WATER_LANDING_ENFORCE_SCAN_RADIUS = 6;
    private static final int WATER_LANDING_ENFORCE_MAX_GAP = 20;
    private static final int TEE_LAUNCH_CLEAR_DISTANCE = 22;
    private static final int TEE_LAUNCH_CLEAR_HALF_WIDTH = 3;
    private static final int TEE_RELOCATION_RADIUS = 12;
    private static final int BASKET_RELOCATION_RADIUS = 16;
    private static final int TEE_EXIT_Y_TOLERANCE = 1;
    private static final int TEE_MIN_NEARBY_EXITS = 5;
    private static final int TEE_WALL_SCAN_RADIUS = 6;
    private static final int TEE_MAX_ENCLOSURE_SCORE = 9;
    private static final int TEE_PIT_DEPTH_THRESHOLD = 4;
    private static final int SURFACE_SEARCH_DEPTH_LIMIT = 24;

    public PlacedCourseState placeCourse(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback) {
        // Current MVP behavior: place relative to the player's surface location.
        BlockPos anchor = findPreferredSurfacePos(world, origin.getX(), origin.getZ(), true, ANCHOR_SEARCH_RADIUS);

        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        Map<Integer, BlockPos> holeTees = new HashMap<>();
        Map<Integer, BlockPos> holeBaskets = new HashMap<>();
        Set<BlockPos> protectedPositions = new HashSet<>();

        int[] min = findCourseMinBounds(course);
        int offsetX = anchor.getX() - min[0] + 12;
        int offsetZ = anchor.getZ() - min[1] + 12;

        // Phase 1: resolve all tee/basket surfaces and prepare no-overwrite protection zones.
        for (Hole hole : course.holes()) {
            int teeX = hole.tee().x() + offsetX;
            int teeZ = hole.tee().z() + offsetZ;
            int basketX = hole.basket().x() + offsetX;
            int basketZ = hole.basket().z() + offsetZ;

                // Build islands if unsafe and use normalized top-surface positions for reliable tee/basket visibility.
                BlockPos teeSurface = ensureLandIslandSurface(
                    world,
                    normalizePlayableSurface(world, findPreferredSurfacePos(world, teeX, teeZ, true, HOLE_SEARCH_RADIUS)),
                    2,
                    originalBlocks,
                    protectedPositions
                );
                BlockPos basketSurface = ensureLandIslandSurface(
                    world,
                    normalizePlayableSurface(world, findPreferredSurfacePos(world, basketX, basketZ, true, HOLE_SEARCH_RADIUS)),
                    2,
                    originalBlocks,
                    protectedPositions
                );

            basketSurface = relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
            teeSurface = relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);

            holeTees.put(hole.index(), teeSurface.toImmutable());
            holeBaskets.put(hole.index(), basketSurface.up().toImmutable());

            // Protect tee pad area, basket green area, and tee lantern area from later fairway/island writes.
            addProtectedColumnArea(protectedPositions, teeSurface, 2, 6);
            addProtectedColumnArea(protectedPositions, basketSurface, 2, 8);
            int[] teeForward = teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            addProtectedColumnArea(protectedPositions, teeLampGround, 1, 6);
        }

        // Phase 2: build one contiguous fairway per hole (faster and more reliable than many segment passes).
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()).down();

            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            int fairwayWidth = resolveHoleFairwayWidth(hole);
            carveFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false
            );

                    progressCallback.accept(Math.max(1, hole.index() / 2));
        }

        // Phase 3: place all tee pads, baskets, and tee lanterns after fairways so they remain visible.
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()) == null ? null : holeBaskets.get(hole.index()).down();
            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            clearHeadroom(world, teeSurface, 2, 5, originalBlocks, null);
            clearHeadroom(world, basketSurface, 2, 6, originalBlocks, null);
            clearTeeLaunchLane(world, teeSurface, basketSurface, originalBlocks, protectedPositions);

            placeTeePad(world, teeSurface, originalBlocks);
            int[] teeForward = teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            placeLanternPost(world, teeLampGround, 2, originalBlocks);
                placeTeeHoleBanner(
                    world,
                    teeSurface,
                    basketSurface,
                    hole.index(),
                    hole.par(),
                    hole.distanceFeet(),
                    originalBlocks
                );
            placeBasketMarker(world, basketSurface, originalBlocks, hole.basket().basketHeight());

            progressCallback.accept(hole.index());
        }

        // Phase 4 intentionally disabled for now (fairway lantern pass) to avoid long generation stalls.

        return new PlacedCourseState(world.getRegistryKey(), originalBlocks, holeTees, holeBaskets);
    }

    public void resetPlacedCourse(ServerWorld world, PlacedCourseState placedCourseState) {
        for (Map.Entry<BlockPos, BlockState> entry : placedCourseState.originalBlocks().entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
        }
    }

    private static int[] findCourseMinBounds(Course course) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        for (Hole hole : course.holes()) {
            minX = Math.min(minX, Math.min(hole.tee().x(), hole.basket().x()));
            minZ = Math.min(minZ, Math.min(hole.tee().z(), hole.basket().z()));
        }

        return new int[] { minX, minZ };
    }

    private static BlockPos findPreferredSurfacePos(ServerWorld world, int x, int z, boolean preferLand, int searchRadius) {
        BlockPos best = resolveSurfacePos(world, x, z);
        int bestScore = scoreSurface(world, best, x, z, preferLand);

        int ringStep = searchRadius >= 128 ? 8 : (searchRadius >= 48 ? 4 : 1);

        for (int radius = ringStep; radius <= searchRadius; radius += ringStep) {
            for (int sx = x - radius; sx <= x + radius; sx += ringStep) {
                BlockPos north = resolveSurfacePos(world, sx, z - radius);
                int northScore = scoreSurface(world, north, x, z, preferLand);
                if (northScore < bestScore) {
                    bestScore = northScore;
                    best = north;
                }

                BlockPos south = resolveSurfacePos(world, sx, z + radius);
                int southScore = scoreSurface(world, south, x, z, preferLand);
                if (southScore < bestScore) {
                    bestScore = southScore;
                    best = south;
                }
            }

            for (int sz = z - radius + ringStep; sz <= z + radius - ringStep; sz += ringStep) {
                BlockPos west = resolveSurfacePos(world, x - radius, sz);
                int westScore = scoreSurface(world, west, x, z, preferLand);
                if (westScore < bestScore) {
                    bestScore = westScore;
                    best = west;
                }

                BlockPos east = resolveSurfacePos(world, x + radius, sz);
                int eastScore = scoreSurface(world, east, x, z, preferLand);
                if (eastScore < bestScore) {
                    bestScore = eastScore;
                    best = east;
                }
            }
        }

        return preferLand ? refineLandCandidate(world, best, x, z) : best;
    }

    private static BlockPos refineLandCandidate(ServerWorld world, BlockPos base, int targetX, int targetZ) {
        BlockPos best = base;
        int bestScore = scoreSurface(world, best, targetX, targetZ, true) + localWaterPenalty(world, best);

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos candidate = resolveSurfacePos(world, base.getX() + dx, base.getZ() + dz);
                int score = scoreSurface(world, candidate, targetX, targetZ, true) + localWaterPenalty(world, candidate);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        return best;
    }

    private static BlockPos resolveSurfacePos(ServerWorld world, int x, int z) {
        // Force chunk generation/loading so heightmap values are valid.
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        world.getChunk(chunkPos.x, chunkPos.z);

        // Start near the top, then walk down to a truly walkable ground block.
        int solidY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (solidY <= world.getBottomY()) {
            solidY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
        }
        if (solidY <= world.getBottomY()) {
            solidY = world.getSeaLevel();
        }

        int minY = world.getBottomY() + 2;
        int boundedMinY = Math.max(minY, solidY - SURFACE_SEARCH_DEPTH_LIMIT);
        BlockPos cursor = new BlockPos(x, solidY, z);
        while (cursor.getY() >= boundedMinY) {
            // Pick stable terrain, not canopy/headspace, then clear headroom later where needed.
            if (isStableGround(world, cursor) && hasPlayableHeadspace(world, cursor)) {
                return cursor;
            }
            cursor = cursor.down();
        }

        int floorY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
        if (floorY > world.getBottomY()) {
            return new BlockPos(x, floorY, z);
        }

        return new BlockPos(x, Math.max(world.getSeaLevel(), solidY), z);
    }

    private static BlockPos normalizePlayableSurface(ServerWorld world, BlockPos pos) {
        // Keep playable anchors on stable ground at this X/Z and never promote into canopy height.
        BlockPos resolved = resolveSurfacePos(world, pos.getX(), pos.getZ());
        if (isUnsafeSurface(world, resolved)) {
            return pos;
        }
        return resolved;
    }

    private static int scoreSurface(ServerWorld world, BlockPos candidate, int targetX, int targetZ, boolean preferLand) {
        int dx = Math.abs(candidate.getX() - targetX);
        int dz = Math.abs(candidate.getZ() - targetZ);
        int score = (dx + dz) * 8;

        if (preferLand && isUnsafeSurface(world, candidate)) {
            score += 10000;
        }

        if (preferLand) {
            if (isWaterBiome(world, candidate)) {
                score += 5000;
            }
        }

        BlockState above = world.getBlockState(candidate.up());
        if (!above.isAir()) {
            score += 250;
        }

        return score;
    }

    private static void placeTeePad(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                setTrackedBlock(world, pos, Blocks.SMOOTH_STONE.getDefaultState(), originalBlocks);
            }
        }
        setTrackedBlock(world, center, Blocks.LIME_CONCRETE.getDefaultState(), originalBlocks);
    }

    private static void placeBasketMarker(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks, int basketHeight) {
        BlockState ground = world.getBlockState(center);
        if (!isBasketGroundSafe(ground)) {
            setTrackedBlock(world, center, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
        }

        BlockPos base = center.up();
        setTrackedBlock(world, base, Blocks.HOPPER.getDefaultState(), originalBlocks);

        for (int i = 1; i <= basketHeight + 1; i++) {
            setTrackedBlock(world, base.up(i), Blocks.IRON_BARS.getDefaultState(), originalBlocks);
        }

        setTrackedBlock(world, base.up(basketHeight + 2), Blocks.LANTERN.getDefaultState(), originalBlocks);
    }

    private static boolean isBasketGroundSafe(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return false;
        }
        return !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES);
    }

    private static void carveFairway(
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

            boolean waterColumn = isWaterCrossingColumn(world, x, z);
            if (waterColumn) {
                waterCarryStreak++;
            } else {
                waterCarryStreak = 0;
            }

            if (i > 0
                    && i < steps
                    && waterColumn
                    && (waterCarryStreak >= WATER_LANDING_PATCH_MAX_CARRY || i - lastWaterPatchStep >= WATER_LANDING_PATCH_INTERVAL)) {
                BlockPos waterSurface = resolveSurfacePos(world, x, z);
                BlockPos landingCenter = ensureWaterLandingSurface(
                        world,
                        waterSurface,
                        WATER_LANDING_PATCH_RADIUS,
                        originalBlocks,
                        protectedPositions
                );
                clearHeadroom(world, landingCenter, WATER_LANDING_PATCH_RADIUS, 5, originalBlocks, protectedPositions);
                addProtectedColumnArea(protectedPositions, landingCenter, WATER_LANDING_PATCH_RADIUS, 5);
                lastWaterPatchStep = i;
                waterCarryStreak = 0;
            }

            BlockPos center = findPreferredSurfacePos(world, x, z, true, FAIRWAY_SEARCH_RADIUS);
            int tunedRadius = Math.min(2, tunedPathRadius(world, center, radius));
            BlockState pathState = selectPathMaterial(world, center);

            for (int dx = -tunedRadius; dx <= tunedRadius; dx++) {
                for (int dz = -tunedRadius; dz <= tunedRadius; dz++) {
                    if ((dx * dx) + (dz * dz) > (tunedRadius * tunedRadius)) {
                        continue;
                    }

                    int sampleX = center.getX() + dx;
                    int sampleZ = center.getZ() + dz;
                    BlockPos surface = normalizePlayableSurface(world, resolveSurfacePos(world, sampleX, sampleZ));
                    int pathY = surface.getY();

                    // Keep fairway natural: only remove vegetation/tree material up to local canopy height.
                    clearFairwayColumnVegetation(
                            world,
                            sampleX,
                            sampleZ,
                            pathY,
                            originalBlocks,
                            protectedPositions,
                            clearedTreeNodes
                    );

                    BlockPos pathPos = new BlockPos(sampleX, pathY, sampleZ);
                    if (isProtected(protectedPositions, pathPos)) {
                        continue;
                    }
                    if (world.getBlockState(pathPos).equals(pathState)) {
                        continue;
                    }

                    setTrackedBlock(world, pathPos, pathState, originalBlocks);
                }
            }

                    // Run an explicit sweep around the step center so trunk columns adjacent to the path do not survive.
                    clearFairwaySweepVolume(
                        world,
                        center,
                        tunedRadius + FAIRWAY_LOG_SWEEP_EXTRA_RADIUS,
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                    );

            // Add occasional light posts for navigation, keeping spacing so paths do not feel cluttered.
            if (placeLanterns && i - lastLanternStep >= 12 && shouldPlaceFairwayLantern(center.getX(), center.getZ())) {
                int lanternSide = (coordinateNoise(center.getX() * 31, center.getZ() * 17) & 1) == 0 ? 1 : -1;
                int lanternX = center.getX() + (sideX * (tunedRadius + 1) * lanternSide);
                int lanternZ = center.getZ() + (sideZ * (tunedRadius + 1) * lanternSide);
                BlockPos lanternBase = ensureLandIslandSurface(world,
                        findPreferredSurfacePos(world, lanternX, lanternZ, true, FAIRWAY_SEARCH_RADIUS), 1, originalBlocks, protectedPositions);
                if (isProtected(protectedPositions, lanternBase.up())) {
                    continue;
                }
                placeLanternPost(world, lanternBase, 2, originalBlocks);
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

    private static void enforceWaterLandingContinuity(
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

            if (!isWaterCrossingColumn(world, x, z)) {
                currentGap = 0;
                continue;
            }

            if (hasAnyWalkableLandingNearby(world, x, z, WATER_LANDING_ENFORCE_SCAN_RADIUS)) {
                currentGap = 0;
                continue;
            }

            currentGap++;
            if (currentGap <= WATER_LANDING_ENFORCE_MAX_GAP) {
                continue;
            }

            BlockPos landingCenter = ensureLandIslandSurface(
                    world,
                    resolveSurfacePos(world, x, z),
                    WATER_LANDING_PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
            );
                landingCenter = ensureWaterLandingSurface(
                    world,
                    landingCenter,
                    WATER_LANDING_PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
                );
            clearHeadroom(world, landingCenter, WATER_LANDING_PATCH_RADIUS, 5, originalBlocks, protectedPositions);
            addProtectedColumnArea(protectedPositions, landingCenter, WATER_LANDING_PATCH_RADIUS, 5);
            currentGap = 0;
        }
    }

    private static void clearFairwaySweepVolume(
            ServerWorld world,
            BlockPos center,
            int sweepRadius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        int radius = Math.max(1, sweepRadius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                BlockPos surface = normalizePlayableSurface(world, resolveSurfacePos(world, x, z));
                clearFairwayColumnVegetation(
                        world,
                        x,
                        z,
                        surface.getY(),
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                );
            }
        }
    }

    private static void clearFairwayColumnVegetation(
            ServerWorld world,
            int x,
            int z,
            int pathY,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        int floorY = Math.max(world.getBottomY(), pathY - FAIRWAY_CLEAR_BOTTOM_PADDING);
        int ceilingY = Math.max(pathY, topY) + FAIRWAY_CLEAR_TOP_PADDING;

        for (int y = floorY; y <= ceilingY; y++) {
            BlockPos target = new BlockPos(x, y, z);
            if (isProtected(protectedPositions, target)) {
                continue;
            }

            BlockState state = world.getBlockState(target);
            if (!isClearable(state)) {
                continue;
            }

            if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
                clearConnectedTreeCluster(
                        world,
                        target,
                        floorY - 8,
                        ceilingY + 16,
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                );
                continue;
            }

            setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
            clearedTreeNodes.add(target.toImmutable());
        }
    }

    private static void clearConnectedTreeCluster(
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
        while (!queue.isEmpty() && cleared < TREE_CLUSTER_CLEAR_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!clearedTreeNodes.add(pos)) {
                continue;
            }
            if (pos.getY() < minY || pos.getY() > maxY) {
                continue;
            }
            if (isProtected(protectedPositions, pos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            boolean treeMaterial = state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
            if (!treeMaterial) {
                continue;
            }

            setTrackedBlock(world, pos, Blocks.AIR.getDefaultState(), originalBlocks);
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

    private static void placeFairwayLanterns(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            int width,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps < 1) {
            steps = 1;
        }
        int stepStride = Math.max(2, steps / 80);

        int radius = Math.max(1, width / 2);
        int lastLanternStep = Integer.MIN_VALUE;
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
            BlockPos center = findPreferredSurfacePos(world, x, z, true, FAIRWAY_SEARCH_RADIUS);
            int tunedRadius = tunedPathRadius(world, center, radius);

            if (i - lastLanternStep < 12 || !shouldPlaceFairwayLantern(center.getX(), center.getZ())) {
                continue;
            }

            int lanternSide = (coordinateNoise(center.getX() * 31, center.getZ() * 17) & 1) == 0 ? 1 : -1;
            int lanternX = center.getX() + (sideX * (tunedRadius + 1) * lanternSide);
            int lanternZ = center.getZ() + (sideZ * (tunedRadius + 1) * lanternSide);
            BlockPos lanternBase = findPreferredSurfacePos(world, lanternX, lanternZ, true, FAIRWAY_SEARCH_RADIUS);
            if (isProtected(protectedPositions, lanternBase.up())) {
                continue;
            }

            placeLanternPost(world, lanternBase, 2, originalBlocks);
            lastLanternStep = i;
        }
    }

    private static int resolveHoleFairwayWidth(Hole hole) {
        int width = 4;
        for (FairwaySegment segment : hole.fairwaySegments()) {
            width = Math.max(width, segment.width());
        }
        return Math.max(3, Math.min(5, width));
    }

    private static boolean shouldPlaceFairwayLantern(int x, int z) {
        int noise = coordinateNoise(x, z);
        return Math.floorMod(noise, 100) < 17;
    }

    private static int coordinateNoise(int x, int z) {
        int hash = x * 73428767;
        hash ^= z * 912673;
        hash ^= (hash >>> 13);
        hash *= 1274126177;
        return hash;
    }

    private static void placeLanternPost(ServerWorld world, BlockPos ground, int postHeight, Map<BlockPos, BlockState> originalBlocks) {
        int height = Math.max(1, postHeight);
        clearHeadroom(world, ground, 1, height + 2, originalBlocks, null);
        for (int i = 1; i <= height; i++) {
            setTrackedBlock(world, ground.up(i), Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
        }
        setTrackedBlock(world, ground.up(height + 1), Blocks.LANTERN.getDefaultState(), originalBlocks);
    }

    private static void placeTeeHoleBanner(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketSurface,
            int holeNumber,
            int par,
            int distanceFeet,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        int[] forward = teeForwardUnit(teeCenter, basketSurface);
        int[] left = new int[] { -forward[1], forward[0] };
        int[] right = new int[] { -left[0], -left[1] };

        BlockPos signGround = teeCenter.add(forward[0] + left[0], 0, forward[1] + left[1]);
        BlockPos bannerGround = teeCenter.add(forward[0] + right[0], 0, forward[1] + right[1]);

        clearHeadroom(world, bannerGround, 1, 4, originalBlocks, null);
        setTrackedBlock(world, bannerGround.up(1), Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
        BlockPos bannerPos = bannerGround.up(2);
        setTrackedBlock(world, bannerPos, Blocks.WHITE_BANNER.getDefaultState(), originalBlocks);
        String hazardNote = teeHazardNote(world, teeCenter, basketSurface);
        placeTeeHoleSign(
            world,
            signGround,
            -forward[0],
            -forward[1],
            holeNumber,
            par,
            distanceFeet,
            hazardNote,
            originalBlocks
        );
    }

    private static int[] teeForwardUnit(BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, Integer.compare(dz, 0) };
    }

    private static void placeTeeHoleSign(
            ServerWorld world,
            BlockPos signGround,
            int faceDirX,
            int faceDirZ,
            int holeNumber,
            int par,
            int distanceFeet,
            String hazardNote,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        clearHeadroom(world, signGround, 0, 3, originalBlocks, null);
        BlockPos signPos = signGround.up(1);
        BlockState signState = Blocks.OAK_SIGN
                .getDefaultState()
            .with(Properties.ROTATION, standingSignRotationForCardinal(faceDirX, faceDirZ));
        setTrackedBlock(world, signPos, signState, originalBlocks);

        if (world.getBlockEntity(signPos) instanceof SignBlockEntity signBlockEntity) {
            SignText front = signBlockEntity.getFrontText();
            SignText updated = front
                    .withMessage(0, Text.literal("Hole " + holeNumber))
                    .withMessage(1, Text.literal("Par " + par))
                    .withMessage(2, Text.literal(distanceFeet + " ft"))
                    .withMessage(3, Text.literal(hazardNote));
            signBlockEntity.setText(updated, true);
            signBlockEntity.setText(updated, false);
            signBlockEntity.markDirty();
        }
    }

    private static String teeHazardNote(ServerWorld world, BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        int[] forward = teeForwardUnit(teeCenter, basketSurface);
        int[] left = new int[] { -forward[1], forward[0] };
        int[] right = new int[] { -left[0], -left[1] };

        int waterColumns = 0;
        int maxWaterRun = 0;
        int currentWaterRun = 0;
        int obEdgeColumns = 0;
        int mandoGateColumns = 0;
        int mandoScanLimit = Math.max(8, (int) (steps * 0.45f));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int sampleX = (int) Math.round(teeCenter.getX() + (dx * t));
            int sampleZ = (int) Math.round(teeCenter.getZ() + (dz * t));

            if (isWaterCrossingColumn(world, sampleX, sampleZ)) {
                waterColumns++;
                currentWaterRun++;
                maxWaterRun = Math.max(maxWaterRun, currentWaterRun);
            } else {
                currentWaterRun = 0;
            }

            int pathY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
            int leftY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX + (left[0] * 3), sampleZ + (left[1] * 3)) - 1;
            int rightY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX + (right[0] * 3), sampleZ + (right[1] * 3)) - 1;
            if (Math.abs(pathY - leftY) >= 6 || Math.abs(pathY - rightY) >= 6) {
                obEdgeColumns++;
            }

            if (i <= mandoScanLimit && isMandoGapColumn(world, sampleX, sampleZ, pathY, left, right)) {
                mandoGateColumns++;
            }
        }

        if (maxWaterRun >= 6 || waterColumns >= 10) {
            return "Haz: Water";
        }

        if (mandoGateColumns >= Math.max(3, mandoScanLimit / 5)) {
            return "Haz: Mando";
        }

        if (obEdgeColumns >= Math.max(4, steps / 6)) {
            return "Haz: OB";
        }
        return "";
    }

    private static boolean isMandoGapColumn(
            ServerWorld world,
            int x,
            int z,
            int pathY,
            int[] left,
            int[] right
    ) {
        int eyeY = pathY + 2;
        int leftX = x + (left[0] * 2);
        int leftZ = z + (left[1] * 2);
        int rightX = x + (right[0] * 2);
        int rightZ = z + (right[1] * 2);

        boolean leftBlocked = false;
        boolean rightBlocked = false;
        for (int y = eyeY; y <= eyeY + 2; y++) {
            BlockState leftState = world.getBlockState(new BlockPos(leftX, y, leftZ));
            BlockState rightState = world.getBlockState(new BlockPos(rightX, y, rightZ));
            if (isTunnelObstacle(leftState)) {
                leftBlocked = true;
            }
            if (isTunnelObstacle(rightState)) {
                rightBlocked = true;
            }
        }

        boolean centerClear = true;
        for (int y = eyeY; y <= eyeY + 2; y++) {
            BlockState center = world.getBlockState(new BlockPos(x, y, z));
            if (!center.isAir() && center.getFluidState().isEmpty()) {
                centerClear = false;
                break;
            }
        }

        return centerClear && leftBlocked && rightBlocked;
    }

    private static boolean isTunnelObstacle(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        return isTallVegetationObstacle(state);
    }

    private static int standingSignRotationForCardinal(int dirX, int dirZ) {
        if (dirX == 0 && dirZ == 0) {
            return 0;
        }

        if (dirX == 0 && dirZ > 0) {
            return 0; // south
        }
        if (dirX < 0 && dirZ == 0) {
            return 4; // west
        }
        if (dirX == 0 && dirZ < 0) {
            return 8; // north
        }
        if (dirX > 0 && dirZ == 0) {
            return 12; // east
        }

        // Fallback for unexpected non-cardinal vectors.
        return 0;
    }

    private static int tunedPathRadius(ServerWorld world, BlockPos pos, int baseRadius) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Math.max(1, baseRadius - 1);
        }
        if (isBiome(biomeId, "desert", "badlands", "eroded_badlands", "wooded_badlands")) {
            return Math.max(1, baseRadius + 1);
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Math.max(1, baseRadius + 1);
        }
        if (isBiome(biomeId, "savanna", "windswept_savanna", "plains", "sunflower_plains")) {
            return Math.max(1, baseRadius + 1);
        }

        return baseRadius;
    }

    private static BlockState selectPathMaterial(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Blocks.GRAVEL.getDefaultState();
        }
        if (isBiome(biomeId, "desert")) {
            return Blocks.SANDSTONE.getDefaultState();
        }
        if (isBiome(biomeId, "badlands", "eroded_badlands", "wooded_badlands")) {
            return Blocks.RED_SANDSTONE.getDefaultState();
        }
        if (isBiome(
                biomeId,
                "snowy_plains",
                "snowy_taiga",
                "snowy_slopes",
                "ice_spikes",
                "frozen_river",
                "frozen_peaks",
                "jagged_peaks"
        )) {
            return Blocks.PACKED_ICE.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_NETHER)) {
            return Blocks.BLACKSTONE.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Blocks.COARSE_DIRT.getDefaultState();
        }

        return Blocks.DIRT_PATH.getDefaultState();
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "";
        }
        return key.getValue().getPath();
    }

    private static boolean isBiome(String biomeId, String... names) {
        for (String name : names) {
            if (biomeId.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures the given position is on safe, walkable land.
     * If the position is underwater or in fluid, builds a grass island up to sea level.
     * Returns the BlockPos of the actual safe surface (may be higher than the input center).
     */
    private static BlockPos ensureLandIslandSurface(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos groundedCenter = resolveSurfacePos(world, center.getX(), center.getZ());
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
                    if (isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }
                // Grass on top.
                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

    private static BlockPos ensureWaterLandingSurface(
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
                    if (isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }

                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

    private static boolean isUnsafeSurface(ServerWorld world, BlockPos pos) {
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

    private static boolean isWalkableGround(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (isUnsafeSurface(world, pos)) {
            return false;
        }
        if (!ground.isSolidBlock(world, pos)) {
            return false;
        }

        BlockState above = world.getBlockState(pos.up());
        BlockState above2 = world.getBlockState(pos.up(2));
        return isOpenHeadspace(above) && isOpenHeadspace(above2);
    }

    private static BlockPos relocateTeeSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (isPlayableTeeSurface(world, teeSurface)) {
            return teeSurface;
        }

        BlockPos best = teeSurface;
        int bestScore = Integer.MAX_VALUE;
        int step = 2;

        for (int dx = -TEE_RELOCATION_RADIUS; dx <= TEE_RELOCATION_RADIUS; dx += step) {
            for (int dz = -TEE_RELOCATION_RADIUS; dz <= TEE_RELOCATION_RADIUS; dz += step) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (TEE_RELOCATION_RADIUS * TEE_RELOCATION_RADIUS)) {
                    continue;
                }

                BlockPos candidate = normalizePlayableSurface(
                        world,
                        resolveSurfacePos(world, teeSurface.getX() + dx, teeSurface.getZ() + dz)
                );
                if (!isPlayableTeeSurface(world, candidate)) {
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

        return best;
    }

    private static BlockPos relocateBasketSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (isPlayableBasketSurface(world, basketSurface)) {
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

                BlockPos candidate = normalizePlayableSurface(
                        world,
                        resolveSurfacePos(world, basketSurface.getX() + dx, basketSurface.getZ() + dz)
                );
                if (!isPlayableBasketSurface(world, candidate)) {
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

    private static boolean isPlayableBasketSurface(ServerWorld world, BlockPos pos) {
        if (!isWalkableGround(world, pos)) {
            return false;
        }
        if (isLikelyPitSurface(world, pos)) {
            return false;
        }
        return !hasExcessiveTeeEnclosure(world, pos);
    }

    private static boolean isPlayableTeeSurface(ServerWorld world, BlockPos pos) {
        if (!isWalkableGround(world, pos)) {
            return false;
        }
        if (isLikelyPitSurface(world, pos)) {
            return false;
        }
        if (hasExcessiveTeeEnclosure(world, pos)) {
            return false;
        }

        int nearbyExits = 0;
        int[][] directions = {
                { 2, 0 },
                { -2, 0 },
                { 0, 2 },
                { 0, -2 },
                { 2, 2 },
                { 2, -2 },
                { -2, 2 },
                { -2, -2 },
                { 4, 0 },
                { -4, 0 },
                { 0, 4 },
                { 0, -4 }
        };

        for (int[] direction : directions) {
            BlockPos sample = normalizePlayableSurface(
                    world,
                    resolveSurfacePos(world, pos.getX() + direction[0], pos.getZ() + direction[1])
            );
            if (!isWalkableGround(world, sample)) {
                continue;
            }
            if (Math.abs(sample.getY() - pos.getY()) <= TEE_EXIT_Y_TOLERANCE) {
                nearbyExits++;
            }
        }

        return nearbyExits >= TEE_MIN_NEARBY_EXITS;
    }

    private static boolean isLikelyPitSurface(ServerWorld world, BlockPos center) {
        int total = 0;
        int count = 0;
        int minNeighborY = Integer.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                BlockPos sample = normalizePlayableSurface(
                        world,
                        resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
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

    private static boolean hasExcessiveTeeEnclosure(ServerWorld world, BlockPos center) {
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

                BlockPos sample = normalizePlayableSurface(
                        world,
                        resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
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

    private static boolean isStableGround(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (isUnsafeSurface(world, pos)) {
            return false;
        }
        return ground.isSolidBlock(world, pos);
    }

    private static boolean hasPlayableHeadspace(ServerWorld world, BlockPos groundPos) {
        BlockState above = world.getBlockState(groundPos.up());
        if (!above.getFluidState().isEmpty()) {
            return false;
        }
        // Reject enclosed underground picks while allowing natural grass/leaf canopy around ground level.
        return !above.isSolidBlock(world, groundPos.up());
    }

    private static boolean isOpenHeadspace(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.getBlock() instanceof PlantBlock;
    }

    private static int localWaterPenalty(ServerWorld world, BlockPos center) {
        int penalty = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos sample = resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                if (isUnsafeSurface(world, sample) || isWaterBiome(world, sample)) {
                    penalty += 220;
                }
            }
        }
        return penalty;
    }

    private static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        String id = biomeId(world.getBiome(pos));
        return id.contains("ocean") || id.contains("river") || id.contains("beach");
    }

    private static boolean isWaterCrossingColumn(ServerWorld world, int x, int z) {
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

        return isWaterBiome(world, resolveSurfacePos(world, x, z));
    }

    private static boolean hasAnyWalkableLandingNearby(ServerWorld world, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = x + dx;
                int sampleZ = z + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (isWalkableGround(world, sample)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isFillReplaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.getBlock() instanceof PlantBlock;
    }

    private static void clearHeadroom(
            ServerWorld world,
            BlockPos center,
            int radius,
            int height,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx * dx) + (dz * dz) > (r * r + 1)) {
                    continue;
                }

                for (int y = 1; y <= h; y++) {
                    BlockPos target = center.add(dx, y, dz);
                    if (isProtected(protectedPositions, target)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(target);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }
    }

    private static void clearTeeLaunchLane(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basketSurface.getX() - teeSurface.getX();
        int dz = basketSurface.getZ() - teeSurface.getZ();
        int total = Math.max(Math.abs(dx), Math.abs(dz));
        if (total < 1) {
            return;
        }

        int laneLength = Math.min(TEE_LAUNCH_CLEAR_DISTANCE, Math.max(8, total / 3));
        Set<BlockPos> clearedTreeNodes = new HashSet<>();

        for (int step = 1; step <= laneLength; step++) {
            double t = step / (double) total;
            int lineX = (int) Math.round(teeSurface.getX() + (dx * t));
            int lineZ = (int) Math.round(teeSurface.getZ() + (dz * t));

            for (int wx = -TEE_LAUNCH_CLEAR_HALF_WIDTH; wx <= TEE_LAUNCH_CLEAR_HALF_WIDTH; wx++) {
                for (int wz = -TEE_LAUNCH_CLEAR_HALF_WIDTH; wz <= TEE_LAUNCH_CLEAR_HALF_WIDTH; wz++) {
                    int sampleX = lineX + wx;
                    int sampleZ = lineZ + wz;
                    clearFairwayColumnVegetation(
                            world,
                            sampleX,
                            sampleZ,
                            teeSurface.getY(),
                            originalBlocks,
                            null,
                            clearedTreeNodes
                    );

                    for (int y = teeSurface.getY() + 1; y <= teeSurface.getY() + 4; y++) {
                        BlockPos obstructionPos = new BlockPos(sampleX, y, sampleZ);
                        if (isProtected(protectedPositions, obstructionPos)) {
                            continue;
                        }
                        BlockState obstruction = world.getBlockState(obstructionPos);
                        if (isTeeLaunchObstruction(obstruction)) {
                            setTrackedBlock(world, obstructionPos, Blocks.AIR.getDefaultState(), originalBlocks);
                        }
                    }
                }
            }
        }
    }

    private static boolean isTeeLaunchObstruction(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        if (isTallVegetationObstacle(state)) {
            return true;
        }
        return state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.MUD)
                || state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.SAND)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.SNOW_BLOCK)
                || state.isOf(Blocks.POWDER_SNOW);
    }

    private static void addProtectedColumnArea(Set<BlockPos> protectedPositions, BlockPos center, int radius, int height) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx * dx) + (dz * dz) > (r * r + 1)) {
                    continue;
                }
                for (int y = 0; y <= h; y++) {
                    protectedPositions.add(center.add(dx, y, dz).toImmutable());
                }
            }
        }
    }

    private static boolean isProtected(Set<BlockPos> protectedPositions, BlockPos pos) {
        return protectedPositions != null && protectedPositions.contains(pos);
    }

    private static boolean isClearable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        return isTallVegetationObstacle(state);
    }

    private static boolean isTallVegetationObstacle(BlockState state) {
        return state.isOf(Blocks.BAMBOO)
                || state.isOf(Blocks.BAMBOO_SAPLING)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.BIG_DRIPLEAF)
                || state.isOf(Blocks.BIG_DRIPLEAF_STEM)
                || state.isOf(Blocks.SMALL_DRIPLEAF)
                || state.isOf(Blocks.MANGROVE_ROOTS)
                || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.isOf(Blocks.NETHER_SPROUTS)
                || state.isOf(Blocks.CRIMSON_ROOTS)
                || state.isOf(Blocks.WARPED_ROOTS)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.CAVE_VINES)
                || state.isOf(Blocks.CAVE_VINES_PLANT)
                || state.isOf(Blocks.WEEPING_VINES)
                || state.isOf(Blocks.WEEPING_VINES_PLANT)
                || state.isOf(Blocks.TWISTING_VINES)
                || state.isOf(Blocks.TWISTING_VINES_PLANT);
    }

    private static void setTrackedBlock(ServerWorld world, BlockPos pos, BlockState newState, Map<BlockPos, BlockState> originalBlocks) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState current = world.getBlockState(immutablePos);
        if (current.equals(newState)) {
            return;
        }

        originalBlocks.computeIfAbsent(immutablePos, p -> current);
        world.setBlockState(immutablePos, newState, Block.NOTIFY_ALL);
    }
}
