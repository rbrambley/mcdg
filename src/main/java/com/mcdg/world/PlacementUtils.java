package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;

/**
 * Shared placement utility methods for course construction.
 */
public final class PlacementUtils {
    private PlacementUtils() {}

    static void setTrackedBlock(ServerWorld world, BlockPos pos, BlockState newState, Map<BlockPos, BlockState> originalBlocks) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState current = world.getBlockState(immutablePos);
        if (current.equals(newState)) {
            return;
        }

        originalBlocks.computeIfAbsent(immutablePos, p -> current);
        world.setBlockState(immutablePos, newState, Block.NOTIFY_LISTENERS);
    }

    static BlockPos orientedOffset(BlockPos origin, int[] side, int[] forward, int sideSteps, int forwardSteps, int yOffset) {
        int x = origin.getX() + (side[0] * sideSteps) + (forward[0] * forwardSteps);
        int z = origin.getZ() + (side[1] * sideSteps) + (forward[1] * forwardSteps);
        return new BlockPos(x, origin.getY() + yOffset, z);
    }

    static Direction cardinalDirectionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    static int directionToSideStep(Direction direction) {
        return switch (direction) {
            case WEST -> -4;
            case EAST -> 3;
            default -> 0;
        };
    }

    static int directionToForwardStep(Direction direction) {
        return switch (direction) {
            case NORTH -> -4;
            case SOUTH -> 3;
            default -> 0;
        };
    }

    static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "";
        }
        return key.getValue().getPath();
    }

    static boolean isBiome(String biomeId, String... names) {
        for (String name : names) {
            if (biomeId.equals(name)) {
                return true;
            }
        }
        return false;
    }


    static void clearHeadroom(
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

    public static void addProtectedColumnArea(Set<BlockPos> protectedPositions, BlockPos center, int radius, int height) {
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

    static boolean isProtected(Set<BlockPos> protectedPositions, BlockPos pos) {
        return protectedPositions != null && protectedPositions.contains(pos);
    }

    static boolean isClearable(BlockState state) {
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

    static boolean isTallVegetationObstacle(BlockState state) {
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
    static int[] teeForwardUnit(BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, Integer.compare(dz, 0) };
    }

    static int placedDistanceFeet(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return 0;
        }

        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        int meters = Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        return Math.max(0, Math.round(meters * 3.28084f));
    }

}
