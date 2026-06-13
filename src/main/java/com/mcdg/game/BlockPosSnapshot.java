package com.mcdg.game;

import net.minecraft.util.math.BlockPos;

public final class BlockPosSnapshot {
    int x;
    int y;
    int z;

    static BlockPosSnapshot of(BlockPos pos) {
        BlockPosSnapshot snapshot = new BlockPosSnapshot();
        snapshot.x = pos.getX();
        snapshot.y = pos.getY();
        snapshot.z = pos.getZ();
        return snapshot;
    }

    BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }
}
