package com.mcdg.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.mcdg.rules.TournamentRulesetManager.Ruleset;
import com.mcdg.rules.TournamentRulesetManager.StrictSurfacePreset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TournamentRulesetManagerTest {
    private TournamentRulesetManager manager;

    @BeforeEach
    void setUp() {
        manager = new TournamentRulesetManager();
    }

    @Test
    void defaultRulesetIsStrict() {
        assertEquals(Ruleset.STRICT, manager.getActiveRuleset());
        assertTrue(manager.isStrict());
    }

    @Test
    void switchToCasual() {
        manager.setActiveRuleset(Ruleset.CASUAL);
        assertEquals(Ruleset.CASUAL, manager.getActiveRuleset());
        assertFalse(manager.isStrict());
    }

    @Test
    void switchBackToStrict() {
        manager.setActiveRuleset(Ruleset.CASUAL);
        manager.setActiveRuleset(Ruleset.STRICT);
        assertTrue(manager.isStrict());
    }

    @Test
    void setNullRulesetIsIgnored() {
        manager.setActiveRuleset(null);
        assertEquals(Ruleset.STRICT, manager.getActiveRuleset());
    }

    @Test
    void defaultSurfacePresetIsBalanced() {
        assertEquals(StrictSurfacePreset.BALANCED, manager.getStrictSurfacePreset());
    }

    @Test
    void setNullSurfacePresetIsIgnored() {
        manager.setStrictSurfacePreset(null);
        assertEquals(StrictSurfacePreset.BALANCED, manager.getStrictSurfacePreset());
    }

    @Test
    void lieToleranceStrictVsCasual() {
        assertEquals(2, manager.allowedLieToleranceBlocks());
        manager.setActiveRuleset(Ruleset.CASUAL);
        assertEquals(5, manager.allowedLieToleranceBlocks());
    }

    @Test
    void hazardPenaltyIsOne() {
        assertEquals(1, manager.strictHazardPenaltyStrokes());
    }

    @Test
    void obPenaltyIsOne() {
        assertEquals(1, manager.strictObPenaltyStrokes());
    }

    @Test
    void corridorBasePaddingByPreset() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertEquals(7, manager.strictCorridorBasePaddingBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertEquals(6, manager.strictCorridorBasePaddingBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertEquals(5, manager.strictCorridorBasePaddingBlocks());
    }

    @Test
    void corridorMinHalfWidthByPreset() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertEquals(9, manager.strictCorridorMinimumHalfWidthBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertEquals(8, manager.strictCorridorMinimumHalfWidthBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertEquals(7, manager.strictCorridorMinimumHalfWidthBlocks());
    }

    @Test
    void altRouteHalfWidthByPreset() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertEquals(76, manager.strictAltRouteHalfWidthBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertEquals(72, manager.strictAltRouteHalfWidthBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertEquals(66, manager.strictAltRouteHalfWidthBlocks());
    }

    @Test
    void altRouteCarryTriggerByPreset() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertEquals(80, manager.strictAltRouteCarryTriggerBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertEquals(72, manager.strictAltRouteCarryTriggerBlocks());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertEquals(64, manager.strictAltRouteCarryTriggerBlocks());
    }

    @Test
    void slopeHazardDisabledForFast() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertFalse(manager.strictEnableSlopeHazard());
    }

    @Test
    void slopeHazardEnabledForBalancedAndTournament() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertTrue(manager.strictEnableSlopeHazard());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertTrue(manager.strictEnableSlopeHazard());
    }

    @Test
    void slopeHazardDeltaYByPreset() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertEquals(3, manager.strictSlopeHazardDeltaY());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertEquals(4, manager.strictSlopeHazardDeltaY());

        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertEquals(4, manager.strictSlopeHazardDeltaY());
    }

    @Test
    void roughHazardOnlyForTournament() {
        manager.setStrictSurfacePreset(StrictSurfacePreset.FAST);
        assertFalse(manager.strictEnableRoughHazard());

        manager.setStrictSurfacePreset(StrictSurfacePreset.BALANCED);
        assertFalse(manager.strictEnableRoughHazard());

        manager.setStrictSurfacePreset(StrictSurfacePreset.TOURNAMENT);
        assertTrue(manager.strictEnableRoughHazard());
    }

    @Test
    void roughHazardThresholdIsEleven() {
        assertEquals(11, manager.strictRoughHazardLeafLogThreshold());
    }
}
