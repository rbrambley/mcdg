package com.mcdg.game;

/**
 * Represents the release angle/hyzer angle of a throw.
 * The angle modifies the curve on top of the stance's natural fade:
 * - HYZER: Exaggerates natural fade direction
 * - FLAT: Neutral - only natural fade applies
 * - ANHYZER: Counteracts natural fade
 */
public enum ReleaseAngle {
    HYZER,
    FLAT,
    ANHYZER;

    /**
     * Returns the next angle in the cycle: Hyzer -> Flat -> Anhyzer -> Hyzer
     */
    public ReleaseAngle next() {
        return switch (this) {
            case HYZER -> FLAT;
            case FLAT -> ANHYZER;
            case ANHYZER -> HYZER;
        };
    }

    /**
     * Returns the bias this angle applies to curve calculation.
     * -1 = left bias, 0 = neutral, +1 = right bias
     */
    public int angleBias() {
        return switch (this) {
            case HYZER -> -1;
            case FLAT -> 0;
            case ANHYZER -> 1;
        };
    }
}
