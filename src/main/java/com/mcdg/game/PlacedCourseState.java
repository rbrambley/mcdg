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
        Map<Integer, BlockPos> holeBaskets,
    Map<Integer, BlockPos> holeAlternateAnchors,
    Map<Integer, Integer> effectiveHolePars
) {
    public PlacedCourseState(
            RegistryKey<World> worldKey,
            Map<BlockPos, BlockState> originalBlocks,
            Map<Integer, BlockPos> holeTees,
            Map<Integer, BlockPos> holeBaskets
    ) {
        this(worldKey, originalBlocks, holeTees, holeBaskets, Map.of(), Map.of());
    }

    public PlacedCourseState(
            RegistryKey<World> worldKey,
            Map<BlockPos, BlockState> originalBlocks,
            Map<Integer, BlockPos> holeTees,
            Map<Integer, BlockPos> holeBaskets,
            Map<Integer, BlockPos> holeAlternateAnchors
    ) {
        this(worldKey, originalBlocks, holeTees, holeBaskets, holeAlternateAnchors, Map.of());
    }

    public PlacedCourseState {
        if (worldKey == null) {
            throw new IllegalArgumentException("worldKey is required");
        }
        if (originalBlocks == null) {
            throw new IllegalArgumentException("originalBlocks is required");
        }
        if (holeTees == null || holeBaskets == null || holeAlternateAnchors == null || effectiveHolePars == null) {
            throw new IllegalArgumentException("holeTees, holeBaskets, holeAlternateAnchors, and effectiveHolePars are required");
        }

        originalBlocks = Map.copyOf(new HashMap<>(originalBlocks));
        holeTees = copyPosMap(holeTees);
        holeBaskets = copyPosMap(holeBaskets);
        holeAlternateAnchors = copyPosMap(holeAlternateAnchors);
        effectiveHolePars = Map.copyOf(new HashMap<>(effectiveHolePars));
    }

    private static Map<Integer, BlockPos> copyPosMap(Map<Integer, BlockPos> source) {
        Map<Integer, BlockPos> copy = new HashMap<>();
        for (Map.Entry<Integer, BlockPos> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().toImmutable());
        }
        return Map.copyOf(copy);
    }
}
