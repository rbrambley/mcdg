package com.mcdg.game;

import net.minecraft.util.math.BlockPos;

public record PlayerRoundState(
        int currentHole,
        BlockPos lie,
        int holeStrokes,
    int totalStrokes,
    boolean lastThrowPenalty
) {
    public PlayerRoundState {
        if (currentHole < 1) {
            throw new IllegalArgumentException("currentHole must be >= 1");
        }
        if (lie == null) {
            throw new IllegalArgumentException("lie is required");
        }
        if (holeStrokes < 0 || totalStrokes < 0) {
            throw new IllegalArgumentException("strokes must be >= 0");
        }

        lie = lie.toImmutable();
    }

    public static PlayerRoundState start(BlockPos lie) {
        return new PlayerRoundState(1, lie, 0, 0, false);
    }

    public PlayerRoundState withLie(BlockPos newLie) {
        return new PlayerRoundState(currentHole, newLie, holeStrokes, totalStrokes, lastThrowPenalty);
    }

    public PlayerRoundState recordThrow(BlockPos throwLie) {
        return new PlayerRoundState(currentHole, throwLie, holeStrokes + 1, totalStrokes + 1, false);
    }

    public PlayerRoundState advanceToNextHole(BlockPos nextTeeLie) {
        return new PlayerRoundState(currentHole + 1, nextTeeLie, 0, totalStrokes, false);
    }

    public PlayerRoundState addPenaltyStrokes(int penaltyStrokes) {
        int penalty = Math.max(0, penaltyStrokes);
        return new PlayerRoundState(currentHole, lie, holeStrokes + penalty, totalStrokes + penalty, lastThrowPenalty);
    }

    public PlayerRoundState markLastThrowPenalty(boolean lastThrowPenalty) {
        return new PlayerRoundState(currentHole, lie, holeStrokes, totalStrokes, lastThrowPenalty);
    }
}
