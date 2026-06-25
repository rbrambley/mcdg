package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Manages hazard detection and behavior lookup for disc golf.
 * Provides centralized hazard classification and effect application.
 */
public final class HazardManager {

    private HazardManager() {
        // Utility class
    }

    /**
     * Determines the hazard type at a given position.
     * Returns NONE if no hazard is present.
     */
    public static HazardType getHazardType(ServerWorld world, BlockPos feet) {
        BlockState block = world.getBlockState(feet);
        BlockState below = world.getBlockState(feet.down());

        // Check fluid hazards (water/lava)
        if (isFluidHazard(world, feet)) {
            if (world.getFluidState(feet).isIn(FluidTags.WATER) || world.getFluidState(feet.down()).isIn(FluidTags.WATER)) {
                return HazardType.WATER;
            } else {
                return HazardType.LAVA;
            }
        }

        // Check surface hazards (sand, ice)
        if (block.isOf(Blocks.SAND) || block.isOf(Blocks.RED_SAND) || 
            below.isOf(Blocks.SAND) || below.isOf(Blocks.RED_SAND)) {
            return HazardType.SAND;
        }

        if (block.isOf(Blocks.ICE) || block.isOf(Blocks.PACKED_ICE) || 
            below.isOf(Blocks.ICE) || below.isOf(Blocks.PACKED_ICE)) {
            return HazardType.ICE;
        }

        // Check danger hazards (cactus)
        if (block.isOf(Blocks.CACTUS) || below.isOf(Blocks.CACTUS)) {
            return HazardType.CACTUS;
        }

        // Check dense rough (leaves, logs, vines)
        if (isDenseRoughMaterial(block) || isDenseRoughMaterial(below)) {
            return HazardType.ROUGH;
        }

        // Check swamp (mud, clay, water-adjacent vegetation)
        if (isSwampMaterial(block) || isSwampMaterial(below)) {
            return HazardType.SWAMP;
        }

        // Check cliff (steep elevation drop)
        if (isSteepDrop(world, feet)) {
            return HazardType.CLIFF;
        }

        return HazardType.NONE;
    }

    /**
     * Lightweight hazard detection for HoleMap grid sampling.
     * Performs the same surface/fluid checks as {@link #getHazardType(ServerWorld, BlockPos)}
     * but skips the expensive cliff/elevation drop scan because the grid already has a
     * dedicated slope pass via {@link OutOfBoundsClassifier#isSteepSlopeHazard}.
     */
    public static HazardType getHazardTypeForGrid(ServerWorld world, BlockPos feet) {
        BlockState block = world.getBlockState(feet);
        BlockState below = world.getBlockState(feet.down());

        if (isFluidHazard(world, feet)) {
            if (world.getFluidState(feet).isIn(FluidTags.WATER) || world.getFluidState(feet.down()).isIn(FluidTags.WATER)) {
                return HazardType.WATER;
            } else {
                return HazardType.LAVA;
            }
        }

        if (block.isOf(Blocks.SAND) || block.isOf(Blocks.RED_SAND) ||
            below.isOf(Blocks.SAND) || below.isOf(Blocks.RED_SAND)) {
            return HazardType.SAND;
        }

        if (block.isOf(Blocks.ICE) || block.isOf(Blocks.PACKED_ICE) ||
            below.isOf(Blocks.ICE) || below.isOf(Blocks.PACKED_ICE)) {
            return HazardType.ICE;
        }

        if (block.isOf(Blocks.CACTUS) || below.isOf(Blocks.CACTUS)) {
            return HazardType.CACTUS;
        }

        if (isDenseRoughMaterial(block) || isDenseRoughMaterial(below)) {
            return HazardType.ROUGH;
        }

        if (isSwampMaterial(block) || isSwampMaterial(below)) {
            return HazardType.SWAMP;
        }

        return HazardType.NONE;
    }

    /**
     * Gets the hazard behavior for a given hazard type.
     */
    public static HazardBehavior getHazardBehavior(HazardType type) {
        return switch (type) {
            case NONE -> HazardBehavior.NONE;
            case WATER -> HazardBehavior.WATER;
            case LAVA -> HazardBehavior.LAVA;
            case SAND -> HazardBehavior.SAND;
            case ICE -> HazardBehavior.ICE;
            case CACTUS -> HazardBehavior.CACTUS;
            case ROUGH -> HazardBehavior.ROUGH;
            case SWAMP -> HazardBehavior.SWAMP;
            case CLIFF -> HazardBehavior.CLIFF;
        };
    }

    /**
     * Gets the grid category byte value for a hazard type.
     * Used for minimap encoding (4 types max).
     */
    public static byte getGridCategoryByte(HazardType type) {
        return (byte) type.gridCategory().byteValue();
    }

    /**
     * Checks if a position is in a fluid hazard (water or lava).
     */
    private static boolean isFluidHazard(ServerWorld world, BlockPos pos) {
        return world.getFluidState(pos).isIn(FluidTags.WATER)
                || world.getFluidState(pos).isIn(FluidTags.LAVA)
                || world.getFluidState(pos.down()).isIn(FluidTags.WATER)
                || world.getFluidState(pos.down()).isIn(FluidTags.LAVA);
    }

    /**
     * Checks if a block is dense rough material.
     */
    private static boolean isDenseRoughMaterial(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    /**
     * Checks if a block is swamp material.
     */
    private static boolean isSwampMaterial(BlockState state) {
        return state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.MUD);
    }

    /**
     * Checks if a position is on a steep drop (cliff hazard).
     * Uses a simple height difference check with adjacent blocks.
     */
    private static boolean isSteepDrop(ServerWorld world, BlockPos pos) {
        int currentY = pos.getY();
        int dropCount = 0;
        
        // Check 3x3 area around position
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                BlockPos adjacent = pos.add(dx, 0, dz);
                int adjacentY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 
                                              adjacent.getX(), adjacent.getZ()) - 1;
                
                // If adjacent block is significantly lower, it's a cliff
                if (currentY - adjacentY >= 3) {
                    dropCount++;
                }
            }
        }
        
        // Consider it a cliff if 2+ adjacent blocks have significant drops
        return dropCount >= 2;
    }

    /**
     * Applies hazard effects to a player after disc landing.
     * This should be called from ThrowResolver when a hazard is detected.
     */
    public static void applyHazardEffects(ServerPlayerEntity player, HazardBehavior behavior, HazardType hazardType) {
        if (behavior.damageAmount() > 0) {
            // Use appropriate damage source based on hazard type
            if (hazardType == HazardType.CACTUS) {
                player.damage(player.getServerWorld().getDamageSources().cactus(), behavior.damageAmount());
            } else {
                player.damage(player.getServerWorld().getDamageSources().lava(), behavior.damageAmount());
            }
        }

        if (behavior.slowsRetrieval()) {
            // Apply slowness effect for retrieval
            // This could be enhanced with duration based on hazard type
            player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                100, // 5 seconds
                1,   // Amplifier I
                false, false, true
            ));
        }

        if (behavior.destroysDisc()) {
            // Destroy the disc held by the player (main hand preferred, then off hand).
            ItemStack mainHand = player.getMainHandStack();
            if (mainHand.isOf(McdgItems.TRAINING_DISC)) {
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                ItemStack offHand = player.getOffHandStack();
                if (offHand.isOf(McdgItems.TRAINING_DISC)) {
                    player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                }
            }
            player.sendMessage(Text.literal("Your disc was destroyed by the " + hazardType.displayName() + "!").formatted(Formatting.RED), true);
            McdgMod.LOGGER.info("Disc destroyed by hazard: {} for player {}", hazardType.displayName(), player.getGameProfile().getName());
        }
    }
}
