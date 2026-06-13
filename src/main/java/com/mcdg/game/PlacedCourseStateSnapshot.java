package com.mcdg.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PlacedCourseStateSnapshot {
    String worldId;
    List<BlockStateSnapshot> originals;
    Map<Integer, BlockPosSnapshot> tees;
    Map<Integer, BlockPosSnapshot> baskets;
    Map<Integer, BlockPosSnapshot> alternates;
    Map<Integer, Integer> effectivePars;

    static PlacedCourseStateSnapshot from(PlacedCourseState placed) {
        PlacedCourseStateSnapshot snapshot = new PlacedCourseStateSnapshot();
        snapshot.worldId = placed.worldKey().getValue().toString();
        snapshot.originals = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : placed.originalBlocks().entrySet()) {
            snapshot.originals.add(new BlockStateSnapshot(BlockPosSnapshot.of(entry.getKey()), Block.getRawIdFromState(entry.getValue())));
        }
        snapshot.tees = toPosSnapshotMap(placed.holeTees());
        snapshot.baskets = toPosSnapshotMap(placed.holeBaskets());
        snapshot.alternates = toPosSnapshotMap(placed.holeAlternateAnchors());
        snapshot.effectivePars = new HashMap<>(placed.effectiveHolePars());
        return snapshot;
    }

    static Map<Integer, BlockPosSnapshot> toPosSnapshotMap(Map<Integer, BlockPos> source) {
        Map<Integer, BlockPosSnapshot> map = new HashMap<>();
        for (Map.Entry<Integer, BlockPos> entry : source.entrySet()) {
            map.put(entry.getKey(), BlockPosSnapshot.of(entry.getValue()));
        }
        return map;
    }

    PlacedCourseState toPlacedCourseState(String fallbackWorldId) {
        String resolvedWorld = worldId == null || worldId.isBlank() ? fallbackWorldId : worldId;
        Map<BlockPos, BlockState> originalsMap = new HashMap<>();
        if (originals != null) {
            for (BlockStateSnapshot snapshot : originals) {
                originalsMap.put(snapshot.pos().toBlockPos(), Block.getStateFromRawId(snapshot.stateId()));
            }
        }

        Map<Integer, BlockPos> teesMap = toPosMap(tees);
        Map<Integer, BlockPos> basketsMap = toPosMap(baskets);
        Map<Integer, BlockPos> alternatesMap = toPosMap(alternates);
        Map<Integer, Integer> effectiveParsMap = effectivePars == null ? Map.of() : new HashMap<>(effectivePars);

        RegistryKey<World> key = BuildCourseSessionManager.resolveWorldKey(resolvedWorld);
        return new PlacedCourseState(key, originalsMap, teesMap, basketsMap, alternatesMap, effectiveParsMap);
    }

    static Map<Integer, BlockPos> toPosMap(Map<Integer, BlockPosSnapshot> source) {
        Map<Integer, BlockPos> map = new HashMap<>();
        if (source == null) {
            return map;
        }
        for (Map.Entry<Integer, BlockPosSnapshot> entry : source.entrySet()) {
            map.put(entry.getKey(), entry.getValue().toBlockPos());
        }
        return map;
    }
}
