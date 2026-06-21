package com.mcdg.world;

import net.minecraft.util.math.BlockPos;

/**
 * Serializable resort location metadata.
 */
public final class ResortData {
    public int centerX;
    public int centerY;
    public int centerZ;
    public String dimension;
    public long timestamp;
    public boolean coursesBuilt;

    public ResortData() {}

    public ResortData(int centerX, int centerY, int centerZ, String dimension, long timestamp, boolean coursesBuilt) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.dimension = dimension;
        this.timestamp = timestamp;
        this.coursesBuilt = coursesBuilt;
    }

    public BlockPos centerPos() {
        return new BlockPos(centerX, centerY, centerZ);
    }
}
