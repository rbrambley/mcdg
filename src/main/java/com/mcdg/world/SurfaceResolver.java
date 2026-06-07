package com.mcdg.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Resolves ground surfaces for tee and basket placement.
 */
final class SurfaceResolver {
    private static final int SURFACE_SEARCH_DEPTH_LIMIT = CoursePlacementConfig.Surface.SEARCH_DEPTH_LIMIT;

    private SurfaceResolver() {}

    static BlockPos resolveSurfacePos(ServerWorld world, int x, int z) {
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

    static BlockPos normalizePlayableSurface(ServerWorld world, BlockPos pos) {
        // Keep playable anchors on stable ground at this X/Z and never promote into canopy height.
        BlockPos resolved = resolveSurfacePos(world, pos.getX(), pos.getZ());
        if (CoursePlacementService.isUnsafeSurface(world, resolved)) {
            return pos;
        }
        return resolved;
    }

    static BlockPos findPreferredSurfacePos(ServerWorld world, int x, int z, boolean preferLand, int searchRadius) {
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

    static BlockPos resolveWorldSurfaceGround(ServerWorld world, int x, int z) {
        world.getChunk(x >> 4, z >> 4);
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        if (surfaceY <= world.getBottomY()) {
            surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        }
        return new BlockPos(x, surfaceY, z);
    }

    static boolean isValidHeightmapRuleGround(ServerWorld world, BlockPos groundPos) {
        BlockState ground = world.getBlockState(groundPos);
        if (!ground.isSolidBlock(world, groundPos)) {
            return false;
        }
        if (!ground.getFluidState().isEmpty()) {
            return false;
        }
        if (ground.isIn(BlockTags.LEAVES) || ground.getBlock() instanceof PlantBlock) {
            return false;
        }

        BlockState feet = world.getBlockState(groundPos.up());
        BlockState head = world.getBlockState(groundPos.up(2));
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        if (!feet.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) {
            return false;
        }

        return world.isSkyVisible(groundPos.up());
    }

    static BlockPos enforceMinimumSurfaceY(
            ServerWorld world,
            BlockPos surface,
            int minY,
            int searchRadius,
            boolean requirePlayableTee
    ) {
        if (surface.getY() >= minY) {
            return surface;
        }

        BlockPos best = surface;
        int bestScore = Integer.MAX_VALUE;
        int step = 4;

        for (int radius = step; radius <= searchRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidate = normalizePlayableSurface(
                        world,
                        resolveSurfacePos(world, surface.getX() + dx, surface.getZ() + dz)
                    );
                    if (candidate.getY() < minY || CoursePlacementService.isUnsafeSurface(world, candidate)) {
                        continue;
                    }
                    if (requirePlayableTee && !isPlayableTeeSurface(world, candidate)) {
                        continue;
                    }

                    int score = Math.abs(dx) + Math.abs(dz);
                    score += Math.abs(candidate.getY() - minY) * 2;
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

        return surface;
    }

    static BlockPos refineLandCandidate(ServerWorld world, BlockPos base, int targetX, int targetZ) {
        BlockPos best = base;
        int bestScore = scoreSurface(world, best, targetX, targetZ, true) + CoursePlacementService.localWaterPenalty(world, best);

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos candidate = resolveSurfacePos(world, base.getX() + dx, base.getZ() + dz);
                int score = scoreSurface(world, candidate, targetX, targetZ, true) + CoursePlacementService.localWaterPenalty(world, candidate);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        return best;
    }

    static int scoreSurface(ServerWorld world, BlockPos candidate, int targetX, int targetZ, boolean preferLand) {
        int dx = Math.abs(candidate.getX() - targetX);
        int dz = Math.abs(candidate.getZ() - targetZ);
        int score = (dx + dz) * 8;

        if (preferLand && CoursePlacementService.isUnsafeSurface(world, candidate)) {
            score += 10000;
        }

        if (preferLand) {
            if (CoursePlacementService.isWaterBiome(world, candidate)) {
                score += 5000;
            }
        }

        BlockState above = world.getBlockState(candidate.up());
        if (!above.isAir()) {
            score += 250;
        }

        return score;
    }

    static BlockPos findArcHubPosition(ServerWorld world, BlockPos playerPos, float playerYaw, int maxRadius) {
        double yawRad = Math.toRadians(playerYaw);
        int[] arcAngles = {0, 22, -22, 45, -45, 67, -67, 90, -90};
        for (int r = 3; r <= maxRadius; r += 2) {
            for (int angleOffset : arcAngles) {
                double angle = yawRad + Math.toRadians(angleOffset);
                int x = playerPos.getX() + (int) Math.round(-Math.sin(angle) * r);
                int z = playerPos.getZ() + (int) Math.round(Math.cos(angle) * r);
                BlockPos pos = resolveSurfacePos(world, x, z);
                if (isStableGround(world, pos) && hasPlayableHeadspace(world, pos)) {
                    return pos;
                }
            }
        }
        return resolveSurfacePos(world, playerPos.getX(), playerPos.getZ());
    }

    static boolean isBasketGroundSafe(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return false;
        }
        return !state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES);
    }

    static boolean isStableGround(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (CoursePlacementService.isUnsafeSurface(world, pos)) {
            return false;
        }
        return ground.isSolidBlock(world, pos);
    }

    static boolean hasPlayableHeadspace(ServerWorld world, BlockPos groundPos) {
        BlockState above = world.getBlockState(groundPos.up());
        if (!above.getFluidState().isEmpty()) {
            return false;
        }
        // Reject enclosed underground picks while allowing natural grass/leaf canopy around ground level.
        return !above.isSolidBlock(world, groundPos.up());
    }

    static boolean isOpenHeadspace(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.getBlock() instanceof PlantBlock;
    }

    static boolean isPlayableTeeSurface(ServerWorld world, BlockPos pos) {
        if (!CoursePlacementService.isWalkableGround(world, pos)) {
            return false;
        }
        if (CoursePlacementService.isDeeplyEnclosedTeeSurface(world, pos)) {
            return false;
        }
        if (CoursePlacementService.isLikelyPitSurface(world, pos)) {
            return false;
        }
        if (CoursePlacementService.hasExcessiveTeeEnclosure(world, pos)) {
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
            if (!CoursePlacementService.isWalkableGround(world, sample)) {
                continue;
            }
            if (Math.abs(sample.getY() - pos.getY()) <= CoursePlacementConfig.Tee.EXIT_Y_TOLERANCE) {
                nearbyExits++;
            }
        }

        return nearbyExits >= CoursePlacementConfig.Tee.MIN_NEARBY_EXITS;
    }

    static boolean isPlayableBasketSurface(ServerWorld world, BlockPos pos) {
        if (!CoursePlacementService.isWalkableGround(world, pos)) {
            return false;
        }
        if (CoursePlacementService.hasFluidInBasketMarkerColumn(world, pos, CoursePlacementConfig.Basket.DRY_COLUMN_CHECK_HEIGHT)) {
            return false;
        }
        if (CoursePlacementService.isLikelyPitSurface(world, pos)) {
            return false;
        }
        if (CoursePlacementService.isDeeplyEnclosedBasketSurface(world, pos)) {
            return false;
        }
        return !CoursePlacementService.hasExcessiveTeeEnclosure(world, pos);
    }

}
