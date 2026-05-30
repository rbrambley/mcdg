package com.mcdg.rules;

public final class TournamentRulesetManager {
    public enum Ruleset {
        CASUAL,
        STRICT
    }

    public enum StrictSurfacePreset {
        FAST,
        BALANCED,
        TOURNAMENT
    }

    public enum MiniMapQualityPreset {
        PERFORMANCE,
        BALANCED,
        ULTRA
    }

    private volatile Ruleset activeRuleset = Ruleset.STRICT;
    private volatile StrictSurfacePreset strictSurfacePreset = StrictSurfacePreset.BALANCED;
    private volatile MiniMapQualityPreset miniMapQualityPreset = MiniMapQualityPreset.BALANCED;

    public Ruleset getActiveRuleset() {
        return activeRuleset;
    }

    public void setActiveRuleset(Ruleset ruleset) {
        if (ruleset == null) {
            return;
        }
        activeRuleset = ruleset;
    }

    public boolean isStrict() {
        return activeRuleset == Ruleset.STRICT;
    }

    public StrictSurfacePreset getStrictSurfacePreset() {
        return strictSurfacePreset;
    }

    public void setStrictSurfacePreset(StrictSurfacePreset preset) {
        if (preset == null) {
            return;
        }
        strictSurfacePreset = preset;
    }

    public MiniMapQualityPreset getMiniMapQualityPreset() {
        return miniMapQualityPreset;
    }

    public void setMiniMapQualityPreset(MiniMapQualityPreset preset) {
        if (preset == null) {
            return;
        }
        miniMapQualityPreset = preset;
    }

    public int allowedLieToleranceBlocks() {
        return isStrict() ? 2 : 5;
    }

    public int strictHazardPenaltyStrokes() {
        return 1;
    }

    public int strictObPenaltyStrokes() {
        return 1;
    }

    public int strictCorridorBasePaddingBlocks() {
        return switch (strictSurfacePreset) {
            case FAST -> 7;
            case BALANCED -> 6;
            case TOURNAMENT -> 5;
        };
    }

    public int strictCorridorMinimumHalfWidthBlocks() {
        return switch (strictSurfacePreset) {
            case FAST -> 9;
            case BALANCED -> 8;
            case TOURNAMENT -> 7;
        };
    }

    public int strictAltRouteHalfWidthBlocks() {
        return switch (strictSurfacePreset) {
            case FAST -> 76;
            case BALANCED -> 72;
            case TOURNAMENT -> 66;
        };
    }

    public int strictAltRouteCarryTriggerBlocks() {
        return switch (strictSurfacePreset) {
            case FAST -> 80;
            case BALANCED -> 72;
            case TOURNAMENT -> 64;
        };
    }

    public boolean strictEnableSlopeHazard() {
        return strictSurfacePreset != StrictSurfacePreset.FAST;
    }

    public int strictSlopeHazardDeltaY() {
        return strictSurfacePreset == StrictSurfacePreset.TOURNAMENT ? 3 : 4;
    }

    public boolean strictEnableRoughHazard() {
        return strictSurfacePreset == StrictSurfacePreset.TOURNAMENT;
    }

    public int strictRoughHazardLeafLogThreshold() {
        return 11;
    }

    public int miniMapTerrainRefreshIntervalTicks() {
        return switch (miniMapQualityPreset) {
            case PERFORMANCE -> 30;
            case BALANCED -> 10;
            case ULTRA -> 2;
        };
    }

    public int miniMapTerrainRefreshMoveThresholdBlocks() {
        return switch (miniMapQualityPreset) {
            case PERFORMANCE -> 12;
            case BALANCED -> 8;
            case ULTRA -> 4;
        };
    }
}
