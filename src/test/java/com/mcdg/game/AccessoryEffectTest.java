package com.mcdg.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for accessory effect definitions.
 */
public class AccessoryEffectTest {

    @Test
    @DisplayName("Grip Stability provides a stability bonus")
    public void testGripStabilityValues() {
        assertEquals("Grip Stability", AccessoryEffect.GRIP_STABILITY.displayName());
        assertEquals(0.08f, AccessoryEffect.GRIP_STABILITY.perLevelMultiplier(), 0.0001f);
        assertEquals(1, AccessoryEffect.GRIP_STABILITY.maxLevel());
    }

    @Test
    @DisplayName("Durability Preserve provides a preservation chance")
    public void testDurabilityPreserveValues() {
        assertEquals("Durability Preserve", AccessoryEffect.DURABILITY_PRESERVE.displayName());
        assertEquals(0.15f, AccessoryEffect.DURABILITY_PRESERVE.perLevelMultiplier(), 0.0001f);
        assertEquals(1, AccessoryEffect.DURABILITY_PRESERVE.maxLevel());
    }

    @Test
    @DisplayName("Range Finder provides a throw speed/distance bonus")
    public void testRangeFinderValues() {
        assertEquals("Range Finder", AccessoryEffect.RANGE_FINDER.displayName());
        assertEquals(0.05f, AccessoryEffect.RANGE_FINDER.perLevelMultiplier(), 0.0001f);
        assertEquals(1, AccessoryEffect.RANGE_FINDER.maxLevel());
    }
}
