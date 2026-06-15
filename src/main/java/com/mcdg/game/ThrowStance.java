package com.mcdg.game;

/**
 * Represents the player's throw stance/mode.
 * Each stance has different flight characteristics:
 * - OVERHAND: Ballistic arc, no glide, no lateral curve (vanilla behavior)
 * - BACKHAND (RHBH): Flat glide phase with natural left fade
 * - FOREHAND (RHFH): Flat glide phase with natural right fade
 */
public enum ThrowStance {
    OVERHAND,
    BACKHAND,
    FOREHAND;

    /**
     * Returns the next stance in the cycle: Overhand -> Backhand -> Forehand -> Overhand
     */
    public ThrowStance next() {
        return switch (this) {
            case OVERHAND -> BACKHAND;
            case BACKHAND -> FOREHAND;
            case FOREHAND -> OVERHAND;
        };
    }

    /**
     * Returns the natural fade direction for this stance.
     * -1 = left, +1 = right, 0 = none
     */
    public int naturalFadeDirection() {
        return switch (this) {
            case OVERHAND -> 0;
            case BACKHAND -> -1;  // RHBH fades left
            case FOREHAND -> 1;   // RHFH fades right
        };
    }

    /**
     * Returns true if this stance uses glide physics.
     */
    public boolean hasGlide() {
        return this != OVERHAND;
    }
}
