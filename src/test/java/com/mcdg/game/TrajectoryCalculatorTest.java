package com.mcdg.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Mathematical tests for TrajectoryCalculator physics.
 * Validates that calculated trajectories match expected behavior for different stances and angles.
 * 
 * Note: These tests require a mock ServerWorld for terrain collision detection.
 * For now, they test the mathematical physics without terrain collision.
 */
public class TrajectoryCalculatorTest {

    /**
     * Test that OVERHAND throws produce ballistic arc without glide.
     * Expected: Standard ballistic trajectory, no lateral drift.
     */
    @Test
    @DisplayName("OVERHAND throws should have minimal lateral drift")
    public void testOverhandMinimalDrift() {
        // Test mathematical behavior without terrain
        // OVERHAND has no glide, so should have minimal lateral curve
        ThrowStance stance = ThrowStance.OVERHAND;
        ReleaseAngle angle = ReleaseAngle.FLAT;
        
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias);
        
        // OVERHAND + FLAT should have zero bias (naturalFade = 0)
        assertEquals(0, totalBias, "OVERHAND + FLAT should have zero lateral bias");
    }

    /**
     * Test that BACKHAND + FLAT produces left fade.
     * Expected: Natural fade direction should be negative (left).
     */
    @Test
    @DisplayName("BACKHAND + FLAT should produce left fade")
    public void testBackhandFlatLeftFade() {
        ThrowStance stance = ThrowStance.BACKHAND;
        ReleaseAngle angle = ReleaseAngle.FLAT;
        
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias);
        
        // BACKHAND natural fade is left (-1), FLAT has no bias (0)
        // Formula: naturalFade * (1 - angleBias) = -1 * (1 - 0) = -1
        assertEquals(-1, naturalFade, "BACKHAND should fade left");
        assertEquals(0, angleBias, "FLAT should have no angle bias");
        assertEquals(-1, totalBias, "BACKHAND + FLAT should have left bias");
    }

    /**
     * Test that FOREHAND + FLAT produces right fade.
     * Expected: Natural fade direction should be positive (right).
     */
    @Test
    @DisplayName("FOREHAND + FLAT should produce right fade")
    public void testForehandFlatRightFade() {
        ThrowStance stance = ThrowStance.FOREHAND;
        ReleaseAngle angle = ReleaseAngle.FLAT;
        
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias);
        
        // FOREHAND natural fade is right (+1), FLAT has no bias (0)
        // Formula: naturalFade * (1 - angleBias) = 1 * (1 - 0) = 1
        assertEquals(1, naturalFade, "FOREHAND should fade right");
        assertEquals(0, angleBias, "FLAT should have no angle bias");
        assertEquals(1, totalBias, "FOREHAND + FLAT should have right bias");
    }

    /**
     * Test that BACKHAND + ANHYZER neutralizes natural left fade.
     * Expected: Combined bias should be neutral (0).
     */
    @Test
    @DisplayName("BACKHAND + ANHYZER should neutralize left fade")
    public void testBackhandAnhyzerNeutralizesFade() {
        ThrowStance stance = ThrowStance.BACKHAND;
        ReleaseAngle angle = ReleaseAngle.ANHYZER;
        
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias);
        
        // BACKHAND natural fade is left (-1), ANHYZER bias is right (+1)
        // Formula: naturalFade * (1 - angleBias) = -1 * (1 - 1) = 0
        assertEquals(-1, naturalFade, "BACKHAND should fade left");
        assertEquals(1, angleBias, "ANHYZER should have right bias");
        assertEquals(0, totalBias, "BACKHAND + ANHYZER should have neutral bias");
    }

    /**
     * Test that FOREHAND + HYZER exaggerates natural right fade.
     * Expected: Combined bias should be 2x natural (stronger right fade).
     */
    @Test
    @DisplayName("FOREHAND + HYZER should exaggerate right fade")
    public void testForehandHyzerExaggeratesFade() {
        ThrowStance stance = ThrowStance.FOREHAND;
        ReleaseAngle angle = ReleaseAngle.HYZER;
        
        int naturalFade = stance.naturalFadeDirection();
        int angleBias = angle.angleBias();
        int totalBias = naturalFade * (1 - angleBias);
        
        // FOREHAND natural fade is right (+1), HYZER bias is left (-1)
        // Formula: naturalFade * (1 - angleBias) = 1 * (1 - (-1)) = 2
        assertEquals(1, naturalFade, "FOREHAND should fade right");
        assertEquals(-1, angleBias, "HYZER should have left bias");
        assertEquals(2, totalBias, "FOREHAND + HYZER should have 2x right bias");
    }

    /**
     * Test that HYZER exaggerates natural fade direction.
     */
    @Test
    @DisplayName("HYZER should exaggerate natural fade direction")
    public void testHyzerExaggeratesFade() {
        // BACKHAND + HYZER: left fade (-1) * (1 - (-1)) = stronger left (-2)
        ThrowStance backhand = ThrowStance.BACKHAND;
        ReleaseAngle hyzer = ReleaseAngle.HYZER;
        int backhandHyzerBias = backhand.naturalFadeDirection() * (1 - hyzer.angleBias());
        assertEquals(-2, backhandHyzerBias, "BACKHAND + HYZER should have 2x left bias");
        
        // FOREHAND + HYZER: right fade (+1) * (1 - (-1)) = stronger right (+2)
        ThrowStance forehand = ThrowStance.FOREHAND;
        int forehandHyzerBias = forehand.naturalFadeDirection() * (1 - hyzer.angleBias());
        assertEquals(2, forehandHyzerBias, "FOREHAND + HYZER should have 2x right bias");
    }

    /**
     * Test that ANHYZER neutralizes natural fade direction.
     */
    @Test
    @DisplayName("ANHYZER should neutralize natural fade direction")
    public void testAnhyzerNeutralizesFade() {
        // BACKHAND + ANHYZER: left fade (-1) * (1 - 1) = neutral (0)
        ThrowStance backhand = ThrowStance.BACKHAND;
        ReleaseAngle anhyzer = ReleaseAngle.ANHYZER;
        int backhandAnhyzerBias = backhand.naturalFadeDirection() * (1 - anhyzer.angleBias());
        assertEquals(0, backhandAnhyzerBias, "BACKHAND + ANHYZER should have neutral bias");
        
        // FOREHAND + ANHYZER: right fade (+1) * (1 - 1) = neutral (0)
        ThrowStance forehand = ThrowStance.FOREHAND;
        int forehandAnhyzerBias = forehand.naturalFadeDirection() * (1 - anhyzer.angleBias());
        assertEquals(0, forehandAnhyzerBias, "FOREHAND + ANHYZER should have neutral bias");
    }

    /**
     * Test that glide stances are correctly identified.
     */
    @Test
    @DisplayName("Glide stances should be correctly identified")
    public void testGlideStanceIdentification() {
        assertTrue(ThrowStance.BACKHAND.hasGlide(), "BACKHAND should have glide");
        assertTrue(ThrowStance.FOREHAND.hasGlide(), "FOREHAND should have glide");
        assertFalse(ThrowStance.OVERHAND.hasGlide(), "OVERHAND should not have glide");
    }

    /**
     * Test that stance cycling works correctly.
     */
    @Test
    @DisplayName("Stance cycling should work correctly")
    public void testStanceCycling() {
        assertEquals(ThrowStance.BACKHAND, ThrowStance.OVERHAND.next(), "OVERHAND -> BACKHAND");
        assertEquals(ThrowStance.FOREHAND, ThrowStance.BACKHAND.next(), "BACKHAND -> FOREHAND");
        assertEquals(ThrowStance.OVERHAND, ThrowStance.FOREHAND.next(), "FOREHAND -> OVERHAND");
    }

    /**
     * Test that angle cycling works correctly.
     */
    @Test
    @DisplayName("Angle cycling should work correctly")
    public void testAngleCycling() {
        assertEquals(ReleaseAngle.FLAT, ReleaseAngle.HYZER.next(), "HYZER -> FLAT");
        assertEquals(ReleaseAngle.ANHYZER, ReleaseAngle.FLAT.next(), "FLAT -> ANHYZER");
        assertEquals(ReleaseAngle.HYZER, ReleaseAngle.ANHYZER.next(), "ANHYZER -> HYZER");
    }
}