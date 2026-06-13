package com.mcdg.game;

import com.mcdg.data.Hole;
import net.minecraft.util.math.BlockPos;

public final class BuiltHoleSnapshot {
    int index;
    Hole hole;
    PlacedCourseStateSnapshot placed;
    String worldId;
    long holeSeed;
    BlockPosSnapshot originalTeeAnchor;

    static BuiltHoleSnapshot from(BuildCourseSessionManager.BuiltHole hole) {
        BuiltHoleSnapshot snapshot = new BuiltHoleSnapshot();
        snapshot.index = hole.index();
        snapshot.hole = hole.hole();
        snapshot.placed = hole.placed();
        snapshot.worldId = hole.worldId();
        snapshot.holeSeed = hole.holeSeed();
        snapshot.originalTeeAnchor = BlockPosSnapshot.of(hole.originalTeeAnchor());
        return snapshot;
    }

    BuildCourseSessionManager.BuiltHole toBuiltHole() {
        return new BuildCourseSessionManager.BuiltHole(index, hole, placed, worldId, holeSeed, originalTeeAnchor.toBlockPos());
    }
}
