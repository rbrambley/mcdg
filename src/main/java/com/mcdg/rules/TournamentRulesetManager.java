package com.mcdg.rules;

public final class TournamentRulesetManager {
    public enum Ruleset {
        CASUAL,
        STRICT
    }

    private volatile Ruleset activeRuleset = Ruleset.CASUAL;

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

    public int allowedLieToleranceBlocks() {
        return isStrict() ? 2 : 5;
    }

    public int strictHazardPenaltyStrokes() {
        return 1;
    }

    public int strictObPenaltyStrokes() {
        return 1;
    }
}
