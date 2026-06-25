package com.mcdg.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase 3.1 tiered disc stats.
 */
public class DiscTierTest {

    @Test
    @DisplayName("DiscStats default values should be baseline multipliers")
    public void testDefaultStats() {
        DiscStats stats = DiscStats.DEFAULT;
        assertEquals(1.0, stats.glideMultiplier(), 0.0001);
        assertEquals(1.0, stats.stabilityMultiplier(), 0.0001);
        assertEquals(1.0, stats.throwSpeedMultiplier(), 0.0001);
        assertEquals(0.0, stats.windResistance(), 0.0001);
    }

    @Test
    @DisplayName("DiscTier stats should match progression design")
    public void testTierStats() {
        assertEquals(0.8, DiscTier.WOODEN.stats().glideMultiplier(), 0.0001);
        assertEquals(0.8, DiscTier.WOODEN.stats().stabilityMultiplier(), 0.0001);
        assertEquals(50, DiscTier.WOODEN.durability());

        assertEquals(1.0, DiscTier.IRON.stats().glideMultiplier(), 0.0001);
        assertEquals(1.0, DiscTier.IRON.stats().stabilityMultiplier(), 0.0001);
        assertEquals(200, DiscTier.IRON.durability());

        assertEquals(1.1, DiscTier.GOLD.stats().throwSpeedMultiplier(), 0.0001);
        assertEquals(150, DiscTier.GOLD.durability());

        assertEquals(0.5, DiscTier.DIAMOND.stats().windResistance(), 0.0001);
        assertEquals(0.75, DiscTier.NETHERITE.stats().windResistance(), 0.0001);
    }

    @Test
    @DisplayName("DiscStats wind resistance should be stored correctly")
    public void testWindResistance() {
        assertEquals(0.0, new DiscStats(1.0, 1.0, 1.0, 0.0).windResistance(), 0.0001);
        assertEquals(0.5, new DiscStats(1.0, 1.0, 1.0, 0.5).windResistance(), 0.0001);
        assertEquals(0.75, new DiscStats(1.0, 1.0, 1.0, 0.75).windResistance(), 0.0001);
        assertEquals(1.0, new DiscStats(1.0, 1.0, 1.0, 1.0).windResistance(), 0.0001);
    }

    @Test
    @DisplayName("DiscStats should reject invalid values")
    public void testInvalidStats() {
        assertThrows(IllegalArgumentException.class, () -> new DiscStats(-1.0, 1.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DiscStats(1.0, -1.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DiscStats(1.0, 1.0, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new DiscStats(1.0, 1.0, 1.0, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new DiscStats(1.0, 1.0, 1.0, 1.1));
    }
}
