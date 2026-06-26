package com.mcdg.game;

/**
 * Policy that controls how wind is automatically applied during a round.
 * The selected mode is chosen once at round start and held constant for the entire round.
 */
public enum RoundWindMode {
    /**
     * No wind during rounds. Safe default that preserves legacy behavior.
     */
    CALM,

    /**
     * Generate a single natural wind state at round start using biome/weather/time modifiers.
     * The wind remains constant for the whole round; it does not change per tick.
     */
    NATURAL,

    /**
     * Generate a single fixed wind state at round start with a random speed and direction.
     * Speed is constrained to a moderate range so the round stays playable.
     */
    FIXED_RANDOM
}
