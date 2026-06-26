package com.mcdg.game;

/**
 * Pure configuration holder for round wind automation.
 * Kept separate from {@link RoundWindService} so the lifecycle logic can be unit tested
 * without loading Minecraft server classes.
 */
public final class RoundWindPolicy {

    private static RoundWindMode roundWindMode = RoundWindMode.CALM;

    private RoundWindPolicy() {
        // Utility class
    }

    /**
     * Initialize the policy with the configured round wind mode.
     */
    public static void initialize(RoundWindMode mode) {
        roundWindMode = mode != null ? mode : RoundWindMode.CALM;
    }

    /**
     * Get the currently configured round wind mode.
     */
    public static RoundWindMode getRoundWindMode() {
        return roundWindMode;
    }
}
