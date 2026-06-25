package com.mcdg.world.cave;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Map;

/**
 * Validates if a player is in a suitable cave environment for course placement.
 * Checks Y-level, sky access, surrounding blocks, and available space.
 */
public final class CaveEnvironmentValidator {
    private static final int CAVE_Y_THRESHOLD = 40;
    private static final int SCAN_RADIUS = 16;
    // Headroom and floor space constants removed - builder creates tunnels and clears space

    private CaveEnvironmentValidator() {}

    /**
     * Result of cave environment validation.
     */
    public record ValidationResult(boolean isValid, String errorMessage) {
        public static final ValidationResult VALID = new ValidationResult(true, null);
    }

    /**
     * Validates if the player is in a suitable cave environment for course placement.
     * Simplified validation for cave mode - only checks basic safety conditions since the builder creates tunnels.
     */
    public static ValidationResult validateCaveEnvironment(ServerPlayerEntity player) {
        if (player == null) {
            return new ValidationResult(false, "Player cannot be null");
        }

        BlockPos playerPos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();

        // Check Y-level threshold
        if (playerPos.getY() >= CAVE_Y_THRESHOLD) {
            return new ValidationResult(false, 
                "Must be below Y=" + CAVE_Y_THRESHOLD + " to build a cave course (current Y: " + playerPos.getY() + ")");
        }

        // Check for sky access (should not have direct sky access in a cave)
        if (hasSkyAccess(world, playerPos)) {
            return new ValidationResult(false, 
                "Location has sky access - not a suitable cave environment");
        }

        // Check if player is in lava or water (basic safety check)
        BlockState playerBlockState = world.getBlockState(playerPos);
        if (!playerBlockState.getFluidState().isEmpty()) {
            return new ValidationResult(false, 
                "Cannot build course while standing in liquid (lava/water)");
        }

        // Headroom check removed - cave builder creates tunnels and clears space
        // Stone ratio check removed - cave builder works with various cave compositions
        // Floor space check removed - cave builder clears vegetation and obstacles

        return ValidationResult.VALID;
    }

    /**
     * Checks if the position has direct sky access.
     */
    private static boolean hasSkyAccess(ServerWorld world, BlockPos pos) {
        // Check if the position can see the sky
        return world.isSkyVisible(pos.up());
    }

    /**
     * Gets the Y-level threshold for cave mode.
     */
    public static int getCaveYThreshold() {
        return CAVE_Y_THRESHOLD;
    }

    /**
     * Prepares the cave area for course placement by clearing vegetation and uneven terrain.
     * This is called before course placement to ensure suitable floor space.
     */
    public static void prepareCaveArea(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
        int clearRadius = 8;
        
        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos target = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(target);
                    
                    // Clear vegetation and small obstacles
                    if (isClearableBlock(state)) {
                        if (!originalBlocks.containsKey(target.toImmutable())) {
                            originalBlocks.put(target.toImmutable(), state);
                        }
                        world.setBlockState(target, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Checks if a block should be cleared during cave preparation.
     */
    private static boolean isClearableBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        
        // Clear vegetation
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        
        // Clear small obstacles
        if (state.isOf(Blocks.SHORT_GRASS) || state.isOf(Blocks.TALL_GRASS)) {
            return true;
        }
        
        // Clear vines and hanging vegetation
        if (state.isOf(Blocks.VINE) || state.isOf(Blocks.CAVE_VINES) || 
            state.isOf(Blocks.CAVE_VINES_PLANT) || state.isOf(Blocks.WEEPING_VINES) ||
            state.isOf(Blocks.WEEPING_VINES_PLANT) || state.isOf(Blocks.TWISTING_VINES) ||
            state.isOf(Blocks.TWISTING_VINES_PLANT)) {
            return true;
        }
        
        // Clear mushrooms and fungi
        if (state.isOf(Blocks.BROWN_MUSHROOM) || state.isOf(Blocks.RED_MUSHROOM) ||
            state.isOf(Blocks.MUSHROOM_STEM) || state.isOf(Blocks.CRIMSON_FUNGUS) ||
            state.isOf(Blocks.WARPED_FUNGUS)) {
            return true;
        }
        
        // Clear small gravel patches (not large deposits)
        if (state.isOf(Blocks.GRAVEL)) {
            return true;
        }
        
        return false;
    }
}