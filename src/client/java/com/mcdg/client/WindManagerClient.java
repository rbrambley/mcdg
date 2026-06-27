package com.mcdg.client;

import com.mcdg.game.WindState;
import com.mcdg.net.WindSync;

/**
 * Client-side wind state manager.
 * Receives wind updates from server and provides wind information for HUD display.
 */
public final class WindManagerClient {
    private static WindState currentWind;

    private WindManagerClient() {
        // Utility class
    }

    /**
     * Update wind state from server sync packet.
     */
    public static void updateWindState(WindSync.Payload sync) {
        currentWind = new WindState(
            sync.velocity(),
            sync.speed(),
            sync.directionDegrees(),
            sync.mode(),
            sync.isGusting(),
            System.currentTimeMillis(),
            null
        );
    }

    /**
     * Get current wind state.
     * Returns calm wind if no wind has been synced yet.
     */
    public static WindState getCurrentWind() {
        if (currentWind == null) {
            return WindState.calm();
        }
        return currentWind;
    }

    /**
     * Get wind direction text (e.g., "N", "NE", "E").
     * Always returns direction, never "CALM".
     */
    public static String getWindDirectionText() {
        WindState wind = getCurrentWind();
        return getCompassDirection(wind.directionDegrees());
    }

    /**
     * Convert degrees to compass direction abbreviation.
     */
    private static String getCompassDirection(float degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(degrees / 45.0f) % 8;
        return directions[index];
    }

    /**
     * Get wind arrow character for HUD display.
     */
    public static String getWindArrow(float degrees) {
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        int index = Math.round(degrees / 45.0f) % 8;
        return arrows[index];
    }

    /**
     * Check if wind is strong enough to display.
     * Always returns true to show wind arrow even at minimum speeds.
     */
    public static boolean isWindSignificant() {
        return true;
    }

    /**
     * Get wind color based on speed.
     * Green for light, yellow for moderate, red for strong.
     */
    public static int getWindColor(double speed) {
        if (speed > 0.5) {
            return 0xFFFF5555; // Red
        } else if (speed > 0.3) {
            return 0xFFFFFF55; // Yellow
        }
        return 0xFF55FF55; // Green
    }
}
