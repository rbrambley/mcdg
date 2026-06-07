package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;

/**
 * Shared placement utility methods for course construction.
 */
final class PlacementUtils {
    private PlacementUtils() {}

    static void setTrackedBlock(ServerWorld world, BlockPos pos, BlockState newState, Map<BlockPos, BlockState> originalBlocks) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState current = world.getBlockState(immutablePos);
        if (current.equals(newState)) {
            return;
        }

        originalBlocks.computeIfAbsent(immutablePos, p -> current);
        world.setBlockState(immutablePos, newState, Block.NOTIFY_ALL);
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

}
