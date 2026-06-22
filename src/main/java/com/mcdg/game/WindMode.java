package com.mcdg.game;

/**
 * Wind system modes for disc golf.
 * Controls how wind is generated and applied to disc flight.
 */
public enum WindMode {
    /**
     * No wind (speed = 0.0).
     * Default mode for fair conditions or when wind is disabled.
     */
    CALM,
    
    /**
     * Dynamic weather-based wind.
     * Wind varies based on biome, weather conditions, and time of day.
     */
    NATURAL,
    
    /**
     * Manually set wind (admin controlled).
     * Fixed wind speed and direction set by admin commands.
     */
    FIXED,
    
    /**
     * Tournament-specific wind behavior.
     * Consistent wind conditions for competitive play with optional seeding.
     */
    TOURNAMENT
}
