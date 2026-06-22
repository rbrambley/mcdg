package com.mcdg.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlacementCleanupHelper {
    private PlacementCleanupHelper() {
    }

    private static final int FAIRWAY_CLEAR_BOTTOM_PADDING = CoursePlacementConfig.Fairway.CLEAR_BOTTOM_PADDING;
    private static final int FAIRWAY_CLEAR_TOP_PADDING = CoursePlacementConfig.Fairway.CLEAR_TOP_PADDING;
    private static final int TEE_LAUNCH_CLEAR_DISTANCE = CoursePlacementConfig.Tee.LAUNCH_CLEAR_DISTANCE;
    private static final int TEE_LAUNCH_CLEAR_HALF_WIDTH = CoursePlacementConfig.Tee.LAUNCH_CLEAR_HALF_WIDTH;

    static void clearFairwaySweepVolume(
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
                BlockPos surface = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, x, z));
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

    static void clearFairwayColumnVegetation(
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
            if (PlacementUtils.isProtected(protectedPositions, target)) {
                continue;
            }

            BlockState state = world.getBlockState(target);
            if (!PlacementUtils.isClearable(state)) {
                continue;
            }

            if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
                FairwayCarver.clearConnectedTreeCluster(
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

            PlacementUtils.setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
            clearedTreeNodes.add(target.toImmutable());
        }
    }

    static String teeHazardNote(ServerWorld world, BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        int[] forward = PlacementUtils.teeForwardUnit(teeCenter, basketSurface);
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

            if (CoursePlacementService.isWaterCrossingColumn(world, sampleX, sampleZ)) {
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
            return "Haz: OB";
        }

        if (mandoGateColumns >= Math.max(3, mandoScanLimit / 5)) {
            return "Haz: Mando";
        }

        if (obEdgeColumns >= Math.max(4, steps / 6)) {
            return "Haz: OB";
        }
        return "";
    }

    static boolean isMandoGapColumn(
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

    static boolean isTunnelObstacle(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        return PlacementUtils.isTallVegetationObstacle(state);
    }

    static int tunedPathRadius(ServerWorld world, BlockPos pos, int baseRadius) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = PlacementUtils.biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Math.max(1, baseRadius - 1);
        }
        if (PlacementUtils.isBiome(biomeId, "desert", "badlands", "eroded_badlands", "wooded_badlands")) {
            return Math.max(1, baseRadius + 1);
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Math.max(1, baseRadius + 1);
        }
        if (PlacementUtils.isBiome(biomeId, "savanna", "windswept_savanna", "plains", "sunflower_plains")) {
            return Math.max(1, baseRadius + 1);
        }

        return baseRadius;
    }

    static BlockState selectPathMaterial(ServerWorld world, BlockPos pos) {
        return selectPathMaterial(world, pos, null);
    }

    static BlockState selectPathMaterial(ServerWorld world, BlockPos pos, BiomeTheme theme) {
        if (theme != null) {
            return theme.fairwayPath();
        }

        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = PlacementUtils.biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Blocks.GRAVEL.getDefaultState();
        }
        if (PlacementUtils.isBiome(biomeId, "desert")) {
            return Blocks.SANDSTONE.getDefaultState();
        }
        if (PlacementUtils.isBiome(biomeId, "badlands", "eroded_badlands", "wooded_badlands")) {
            return Blocks.RED_SANDSTONE.getDefaultState();
        }
        if (PlacementUtils.isBiome(
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

    static void clearTeeLaunchLane(
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
                        if (PlacementUtils.isProtected(protectedPositions, obstructionPos)) {
                            continue;
                        }
                        BlockState obstruction = world.getBlockState(obstructionPos);
                        if (isTeeLaunchObstruction(obstruction)) {
                            PlacementUtils.setTrackedBlock(world, obstructionPos, Blocks.AIR.getDefaultState(), originalBlocks);
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
        if (PlacementUtils.isTallVegetationObstacle(state)) {
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

}
