package com.mcdg.config;

import com.mcdg.game.RoundWindMode;

public record McdgConfig(
        boolean enableHudScoringDebug,
    boolean enableStrictFlowDebug,
        boolean skipRoundPresentation,
        int respawnPenaltyStrokes,
        int defaultHoleCount,
        boolean enforceCourseProtection,
        boolean enableSurvivalRewards,
        boolean productionMode,
        boolean enableWindSystem,
        double defaultWindSpeed,
        int windUpdateIntervalTicks,
        RoundWindMode roundWindMode
) {
    public static McdgConfig loadDefault() {
        boolean productionMode = readBoolEnvWithDefault("MCDG_PRODUCTION_MODE", true);
        boolean hudScoringDebug = !productionMode && readBoolEnv("MCDG_DEBUG_HUD_SCORING");
    boolean strictFlowDebug = !productionMode && readBoolEnv("MCDG_DEBUG_STRICT_FLOW");
        boolean skipPresentation = readBoolEnv("MCDG_SKIP_ROUND_PRESENTATION");
        int respawnPenaltyStrokes = readIntEnv("MCDG_RESPAWN_PENALTY_STROKES", 1, 0, 5);
        // Survival rewards enabled by default; disable via MCDG_SURVIVAL_REWARDS=false.
        boolean survivalRewards = readBoolEnvWithDefault("MCDG_SURVIVAL_REWARDS", true);
        // Wind system configuration
        boolean enableWind = readBoolEnvWithDefault("MCDG_ENABLE_WIND", true);
        double defaultWindSpeed = readDoubleEnv("MCDG_DEFAULT_WIND_SPEED", 0.2, 0.0, 1.0);
        int windUpdateInterval = readIntEnv("MCDG_WIND_UPDATE_INTERVAL", 200, 20, 600);
        RoundWindMode roundWindMode = readRoundWindModeEnv("MCDG_ROUND_WIND_MODE", RoundWindMode.FIXED_RANDOM);
    return new McdgConfig(hudScoringDebug, strictFlowDebug, skipPresentation, respawnPenaltyStrokes, 9, true, survivalRewards, productionMode, enableWind, defaultWindSpeed, windUpdateInterval, roundWindMode);
    }

    private static boolean readBoolEnv(String name) {
        String raw = System.getenv(name);
        if (raw == null) {
            return false;
        }

        String value = raw.trim().toLowerCase();
        return value.equals("1") || value.equals("true") || value.equals("yes") || value.equals("on");
    }

    private static boolean readBoolEnvWithDefault(String name, boolean fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String value = raw.trim().toLowerCase();
        if (value.equals("0") || value.equals("false") || value.equals("no") || value.equals("off")) {
            return false;
        }
        if (value.equals("1") || value.equals("true") || value.equals("yes") || value.equals("on")) {
            return true;
        }
        return fallback;
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

    private static double readDoubleEnv(String name, double fallback, double min, double max) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            double parsed = Double.parseDouble(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static RoundWindMode readRoundWindModeEnv(String name, RoundWindMode fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return RoundWindMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
