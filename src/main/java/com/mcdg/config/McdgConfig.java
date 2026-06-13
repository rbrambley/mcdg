package com.mcdg.config;

public record McdgConfig(
        boolean enableHudScoringDebug,
    boolean enableStrictFlowDebug,
        boolean skipRoundPresentation,
        int respawnPenaltyStrokes,
        int defaultHoleCount,
        boolean enforceCourseProtection
) {
    public static McdgConfig loadDefault() {
        boolean hudScoringDebug = readBoolEnv("MCDG_DEBUG_HUD_SCORING");
    boolean strictFlowDebug = readBoolEnv("MCDG_DEBUG_STRICT_FLOW");
        boolean skipPresentation = readBoolEnv("MCDG_SKIP_ROUND_PRESENTATION");
        int respawnPenaltyStrokes = readIntEnv("MCDG_RESPAWN_PENALTY_STROKES", 1, 0, 5);
    return new McdgConfig(hudScoringDebug, strictFlowDebug, skipPresentation, respawnPenaltyStrokes, 9, true);
    }

    private static boolean readBoolEnv(String name) {
        String raw = System.getenv(name);
        if (raw == null) {
            return false;
        }

        String value = raw.trim().toLowerCase();
        return value.equals("1") || value.equals("true") || value.equals("yes") || value.equals("on");
    }

    private static int readIntEnv(String name, int fallback, int min, int max) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
