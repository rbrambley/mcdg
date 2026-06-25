package com.mcdg.world.cave;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

/**
 * Helper class for cave-specific course placement operations.
 * Handles ceiling constraints, wall detection, and auto-lighting for cave courses.
 */
public final class CavePlacementHelper {
    private static final int MIN_CEILING_HEIGHT = 4;
    private static final int LIGHTING_INTERVAL = 8;
    private static final int LIGHTING_RADIUS = 6;

    private CavePlacementHelper() {}

    /**
     * Checks if there's sufficient ceiling height for course placement.
     */
    public static boolean hasSufficientCeilingHeight(ServerWorld world, BlockPos pos, int requiredHeight) {
        int height = 0;
        BlockPos checkPos = pos.up();

        while (height < requiredHeight + 2) {
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir() && !state.getFluidState().isEmpty()) {
                break;
            }
            height++;
            checkPos = checkPos.up();
        }

        return height >= requiredHeight;
    }

    /**
     * Finds the ceiling height above a position.
     */
    public static int findCeilingHeight(ServerWorld world, BlockPos pos) {
        int height = 0;
        BlockPos checkPos = pos.up();

        while (height < 20) { // Max ceiling check to avoid infinite loops
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir() && !state.getFluidState().isEmpty()) {
                return height;
            }
            height++;
            checkPos = checkPos.up();
        }

        return height; // No ceiling found within 20 blocks
    }

    /**
     * Places auto-lighting (torches/lanterns) along a path for cave courses.
     */
    public static void placeCaveLighting(
            ServerWorld world,
            BlockPos startPos,
            BlockPos endPos,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int distance = (int) Math.sqrt(
            Math.pow(endPos.getX() - startPos.getX(), 2) +
            Math.pow(endPos.getZ() - startPos.getZ(), 2)
        );

        int steps = distance / LIGHTING_INTERVAL;
        if (steps < 1) {
            steps = 1;
        }

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(startPos.getX() + (endPos.getX() - startPos.getX()) * t);
            int z = (int) Math.round(startPos.getZ() + (endPos.getZ() - startPos.getZ()) * t);
            
            // Find the ground level at this position
            BlockPos groundPos = findGroundLevel(world, new BlockPos(x, startPos.getY(), z));
            
            // Place torch on wall or ceiling if possible
            placeLightSource(world, groundPos, originalBlocks, protectedPositions);
        }
    }

    /**
     * Places a light source (glowstone) at a suitable position near the ground.
     * Uses glowstone instead of torches because torches require wall attachment.
     */
    private static void placeLightSource(
            ServerWorld world,
            BlockPos groundPos,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Place glowstone on the ground next to the path
        BlockPos[] sidePositions = {
            groundPos.north(),
            groundPos.south(),
            groundPos.east(),
            groundPos.west()
        };

        for (BlockPos sidePos : sidePositions) {
            if (canPlaceLightOn(world, sidePos)) {
                placeLightBlock(world, sidePos, originalBlocks, protectedPositions);
                return;
            }
        }

        // Fallback: place on ceiling
        int ceilingHeight = findCeilingHeight(world, groundPos);
        if (ceilingHeight > 1 && ceilingHeight < 6) {
            BlockPos lightPos = groundPos.up(ceilingHeight);
            BlockState ceilingState = world.getBlockState(lightPos);
            if (ceilingState.isSolidBlock(world, lightPos)) {
                BlockPos placePos = lightPos.down();
                if (canPlaceLightOn(world, placePos)) {
                    placeLightBlock(world, placePos, originalBlocks, protectedPositions);
                }
            }
        }
    }

    /**
     * Checks if a light source can be placed at the given position.
     */
    private static boolean canPlaceLightOn(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        // Can place if the position is air and the block below is solid
        if (!state.isAir()) {
            return false;
        }
        BlockState below = world.getBlockState(pos.down());
        return below.isSolidBlock(world, pos.down()) && below.getFluidState().isEmpty();
    }

    /**
     * Places a light block (glowstone) at the given position.
     */
    private static void placeLightBlock(
            ServerWorld world,
            BlockPos pos,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        if (protectedPositions != null && protectedPositions.contains(pos)) {
            return;
        }

        BlockState currentState = world.getBlockState(pos);
        if (!currentState.equals(Blocks.GLOWSTONE.getDefaultState())) {
            originalBlocks.put(pos.toImmutable(), currentState);
            world.setBlockState(pos, Blocks.GLOWSTONE.getDefaultState(), 3);
            if (protectedPositions != null) {
                protectedPositions.add(pos.toImmutable());
            }
        }
    }

    /**
     * Finds the ground level at a given X/Z position.
     */
    private static BlockPos findGroundLevel(ServerWorld world, BlockPos pos) {
        BlockPos checkPos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        
        // Search downward for solid ground
        for (int i = 0; i < 10; i++) {
            BlockState state = world.getBlockState(checkPos);
            if (state.isSolidBlock(world, checkPos) && state.getFluidState().isEmpty()) {
                return checkPos;
            }
            checkPos = checkPos.down();
        }
        
        return pos; // Fallback to original position
    }

    /**
     * Checks if a position is too close to a wall for fairway placement.
     */
    public static boolean isTooCloseToWall(ServerWorld world, BlockPos pos, int minDistance) {
        for (int dx = -minDistance; dx <= minDistance; dx++) {
            for (int dz = -minDistance; dz <= minDistance; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) > minDistance) {
                        continue;
                    }
                    
                    BlockPos checkPos = pos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(checkPos);
                    
                    // If we hit a solid block at head level, it's too close to wall
                    if (dy >= 1 && dy <= 2 && state.isSolidBlock(world, checkPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}