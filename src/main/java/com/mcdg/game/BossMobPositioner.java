package com.mcdg.game;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles strategic positioning of mobs around boss hole layouts.
 */
public final class BossMobPositioner {
    private static final Random RANDOM = new Random();

    private BossMobPositioner() {
    }

    /**
     * Finds suitable spawn positions for basket guard mobs.
     */
    public static List<BlockPos> findBasketGuardPositions(ServerWorld world, BlockPos basketPos, int count) {
        List<BlockPos> positions = new ArrayList<>();
        int radius = 5; // 3-5 blocks from basket
        int attempts = 20;

        for (int i = 0; i < count && attempts > 0; attempts--) {
            // Random position in circle around basket
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = 3 + RANDOM.nextDouble() * (radius - 3);
            int x = basketPos.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = basketPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos candidate = findValidSpawnY(world, new BlockPos(x, basketPos.getY(), z));

            if (candidate != null && !positions.contains(candidate)) {
                positions.add(candidate);
                i++;
            }
        }

        return positions;
    }

    /**
     * Finds suitable spawn positions for fairway patrol mobs.
     */
    public static List<BlockPos> findFairwayPatrolPositions(ServerWorld world, BlockPos teePos, BlockPos basketPos, int count) {
        List<BlockPos> positions = new ArrayList<>();
        int attempts = 20;

        for (int i = 0; i < count && attempts > 0; attempts--) {
            // Random position along line between tee and basket
            double t = RANDOM.nextDouble(); // 0.0 to 1.0
            int x = (int) MathHelper.lerp(t, teePos.getX(), basketPos.getX());
            int z = (int) MathHelper.lerp(t, teePos.getZ(), basketPos.getZ());

            // Add some perpendicular offset for variety
            int perpendicularOffset = (RANDOM.nextInt(6) - 3); // -3 to +3
            double dx = basketPos.getX() - teePos.getX();
            double dz = basketPos.getZ() - teePos.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 0) {
                x += (int) Math.round((-dz / length) * perpendicularOffset);
                z += (int) Math.round((dx / length) * perpendicularOffset);
            }

            BlockPos candidate = findValidSpawnY(world, new BlockPos(x, teePos.getY(), z));

            if (candidate != null && !positions.contains(candidate)) {
                positions.add(candidate);
                i++;
            }
        }

        return positions;
    }

    /**
     * Finds suitable spawn positions for tee harasser mobs.
     */
    public static List<BlockPos> findTeeHarassPositions(ServerWorld world, BlockPos teePos, int count) {
        List<BlockPos> positions = new ArrayList<>();
        int attempts = 15;

        for (int i = 0; i < count && attempts > 0; attempts--) {
            // Position behind tee, slightly elevated
            int offsetX = (RANDOM.nextInt(10) - 5); // -5 to +5
            int offsetZ = -5 - RANDOM.nextInt(5); // 5-10 blocks behind tee
            int x = teePos.getX() + offsetX;
            int z = teePos.getZ() + offsetZ;
            BlockPos candidate = findValidSpawnY(world, new BlockPos(x, teePos.getY() + 2, z)); // Slightly elevated

            if (candidate != null && !positions.contains(candidate)) {
                positions.add(candidate);
                i++;
            }
        }

        return positions;
    }

    /**
     * Finds a valid Y position for spawning at the given X,Z coordinates.
     * Returns null if no valid position found.
     */
    private static BlockPos findValidSpawnY(ServerWorld world, BlockPos pos) {
        // Search upward and downward from the starting Y
        for (int yOffset = -5; yOffset <= 5; yOffset++) {
            BlockPos candidate = pos.withY(pos.getY() + yOffset);
            if (isValidSpawn(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Checks if a position is valid for mob spawning.
     * Similar to AceCompanionService.isValidSpawn().
     */
    private static boolean isValidSpawn(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos ground = feet.down();

        // Check if feet and head space are clear
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }

        // Check if there's solid ground beneath
        if (world.getBlockState(ground).getCollisionShape(world, ground).isEmpty()) {
            return false;
        }

        // Avoid spawning in dangerous blocks
        if (isDangerousBlock(world, feet) || isDangerousBlock(world, ground)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is dangerous (lava, fire, cactus, magma, etc.).
     */
    private static boolean isDangerousBlock(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.isOf(Blocks.LAVA) ||
               state.isOf(Blocks.FIRE) ||
               state.isOf(Blocks.SOUL_FIRE) ||
               state.isOf(Blocks.CACTUS) ||
               state.isOf(Blocks.MAGMA_BLOCK) ||
               state.isOf(Blocks.SWEET_BERRY_BUSH) ||
               state.isOf(Blocks.WITHER_ROSE) ||
               state.isIn(BlockTags.FIRE);
    }
}