package com.mcdg.client;

import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;

/**
 * Client-side manager for throw preferences (stance and release angle).
 *
 * <p>Following the Phase 2 simplified architecture, this remains purely client-side
 * until throw time. No server sync packets - the stance is sent with the throw
 * via {@link com.mcdg.game.ChargedDiscItem#performThrow}.
 *
 * <p>Defaults: Overhand stance, Flat angle.
 */
public final class ThrowPreferenceManager {

    private static ThrowStance selectedStance = ThrowStance.OVERHAND;
    private static ReleaseAngle selectedAngle = ReleaseAngle.FLAT;

    private ThrowPreferenceManager() {
        // Utility class
    }

    /**
     * Get the currently selected throw stance.
     * Defaults to OVERHAND for new players.
     */
    public static ThrowStance getSelectedStance() {
        return selectedStance;
    }

    /**
     * Get the currently selected release angle.
     * Defaults to FLAT for new players.
     */
    public static ReleaseAngle getSelectedAngle() {
        return selectedAngle;
    }

    /**
     * Cycle to the next throw stance: Overhand -> Backhand -> Forehand -> Overhand.
     * Called when the player presses the stance cycle keybind (R).
     */
    public static void cycleStance() {
        selectedStance = selectedStance.next();
    }

    /**
     * Cycle to the next release angle: Hyzer -> Flat -> Anhyzer -> Hyzer.
     * Called when the player scrolls while charging.
     */
    public static void cycleAngle() {
        selectedAngle = selectedAngle.next();
    }

    /**
     * Reset preferences to defaults. Called on round end or when appropriate.
     */
    public static void reset() {
        selectedStance = ThrowStance.OVERHAND;
        selectedAngle = ReleaseAngle.FLAT;
    }

    /**
     * Set the stance directly (for UI or debug use).
     */
    public static void setStance(ThrowStance stance) {
        selectedStance = stance;
    }

    /**
     * Set the release angle directly (for UI or debug use).
     */
    public static void setAngle(ReleaseAngle angle) {
        selectedAngle = angle;
    }
}
