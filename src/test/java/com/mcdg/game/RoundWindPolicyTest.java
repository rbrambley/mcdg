package com.mcdg.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for round wind automation configuration.
 * Server/world coupling is covered by integration tests rather than mocked unit tests.
 */
public class RoundWindPolicyTest {

    @Test
    @DisplayName("RoundWindMode defaults to CALM and supports NATURAL and FIXED_RANDOM")
    public void testRoundWindModeValues() {
        assertSame(RoundWindMode.CALM, RoundWindMode.valueOf("CALM"));
        assertSame(RoundWindMode.NATURAL, RoundWindMode.valueOf("NATURAL"));
        assertSame(RoundWindMode.FIXED_RANDOM, RoundWindMode.valueOf("FIXED_RANDOM"));
    }

    @Test
    @DisplayName("RoundWindPolicy initialize stores the configured mode")
    public void testInitializeStoresMode() {
        RoundWindPolicy.initialize(RoundWindMode.NATURAL);
        assertSame(RoundWindMode.NATURAL, RoundWindPolicy.getRoundWindMode());

        RoundWindPolicy.initialize(RoundWindMode.FIXED_RANDOM);
        assertSame(RoundWindMode.FIXED_RANDOM, RoundWindPolicy.getRoundWindMode());

        // Reset to default for other tests
        RoundWindPolicy.initialize(RoundWindMode.CALM);
    }

    @Test
    @DisplayName("RoundWindPolicy initialize falls back to CALM for null")
    public void testInitializeNullFallsBackToCalm() {
        RoundWindPolicy.initialize(null);
        assertSame(RoundWindMode.CALM, RoundWindPolicy.getRoundWindMode());
    }
}
