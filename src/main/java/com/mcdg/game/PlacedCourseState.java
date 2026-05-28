package com.mcdg.game;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public record PlacedCourseState(
        RegistryKey<World> worldKey,
        Map<BlockPos, BlockState> originalBlocks,
        Map<Integer, BlockPos> holeTees,
        Map<Integer, BlockPos> holeBaskets
) {
    public PlacedCourseState {
        if (worldKey == null) {
            throw new IllegalArgumentException("worldKey is required");
        }
        if (originalBlocks == null) {
            throw new IllegalArgumentException("originalBlocks is required");
        }
        if (holeTees == null || holeBaskets == null) {
            throw new IllegalArgumentException("holeTees and holeBaskets are required");
        }

        originalBlocks = Map.copyOf(new HashMap<>(originalBlocks));
        holeTees = copyPosMap(holeTees);
        holeBaskets = copyPosMap(holeBaskets);
    }

    private static Map<Integer, BlockPos> copyPosMap(Map<Integer, BlockPos> source) {
        Map<Integer, BlockPos> copy = new HashMap<>();
        for (Map.Entry<Integer, BlockPos> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().toImmutable());
        }
        return Map.copyOf(copy);
    }
}
