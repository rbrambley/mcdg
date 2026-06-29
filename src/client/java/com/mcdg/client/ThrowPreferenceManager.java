package com.mcdg.client;

import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side manager for throw preferences (stance and release angle).
 *
 * <p>Following the Phase 2 simplified architecture, this remains purely client-side
 * until throw time. No server sync packets - the stance is sent with the throw
 * via {@link com.mcdg.game.ChargedDiscItem#performThrow}.
 *
 * <p>Defaults: Overhand stance, Flat angle. Preferences are stored per player UUID
 * so multiple players on the same client do not share state.
 */
public final class ThrowPreferenceManager {

    private static final Map<UUID, ThrowStance> SELECTED_STANCE = new HashMap<>();
    private static final Map<UUID, ReleaseAngle> SELECTED_ANGLE = new HashMap<>();

    private ThrowPreferenceManager() {
        // Utility class
    }

    /**
     * Get the currently selected throw stance for the given player.
     * Defaults to OVERHAND for new players.
     */
    public static ThrowStance getSelectedStance(UUID playerUuid) {
        return SELECTED_STANCE.getOrDefault(playerUuid, ThrowStance.OVERHAND);
    }

    /**
     * Get the currently selected release angle for the given player.
     * Defaults to FLAT for new players.
     */
    public static ReleaseAngle getSelectedAngle(UUID playerUuid) {
        return SELECTED_ANGLE.getOrDefault(playerUuid, ReleaseAngle.FLAT);
    }

    /**
     * Cycle to the next throw stance for the given player: Overhand -> Backhand -> Forehand -> Overhand.
     * Called when the player presses the stance cycle keybind (R).
     */
    public static void cycleStance(UUID playerUuid) {
        SELECTED_STANCE.put(playerUuid, getSelectedStance(playerUuid).next());
    }

    /**
     * Cycle to the next release angle for the given player: Hyzer -> Flat -> Anhyzer -> Hyzer.
     * Called when the player scrolls while charging.
     */
    public static void cycleAngle(UUID playerUuid) {
        SELECTED_ANGLE.put(playerUuid, getSelectedAngle(playerUuid).next());
    }

    /**
     * Reset preferences to defaults for the given player. Called on round end or when appropriate.
     */
    public static void reset(UUID playerUuid) {
        SELECTED_STANCE.put(playerUuid, ThrowStance.OVERHAND);
        SELECTED_ANGLE.put(playerUuid, ReleaseAngle.FLAT);
    }

    /**
     * Set the stance directly for the given player (for UI or debug use).
     */
    public static void setStance(UUID playerUuid, ThrowStance stance) {
        SELECTED_STANCE.put(playerUuid, stance);
    }

    /**
     * Set the release angle directly for the given player (for UI or debug use).
     */
    public static void setAngle(UUID playerUuid, ReleaseAngle angle) {
        SELECTED_ANGLE.put(playerUuid, angle);
    }
}
