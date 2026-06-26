package com.mcdg.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for skill unlock definitions.
 */
public class SkillUnlockTest {

    @Test
    @DisplayName("Power Control requires 100 skill XP")
    public void testPowerControlRequirement() {
        assertEquals("Earn 100 MCDG skill XP", SkillUnlock.POWER_CONTROL.description());
        assertEquals(100, SkillUnlock.POWER_CONTROL.requiredCount());
    }

    @Test
    @DisplayName("Release Control requires 10 rounds")
    public void testReleaseControlRequirement() {
        assertEquals("Complete 10 rounds", SkillUnlock.RELEASE_CONTROL.description());
        assertEquals(10, SkillUnlock.RELEASE_CONTROL.requiredCount());
    }

    @Test
    @DisplayName("Wind Reading requires 500 throws")
    public void testWindReadingRequirement() {
        assertEquals("Throw 500 discs", SkillUnlock.WIND_READING.description());
        assertEquals(500, SkillUnlock.WIND_READING.requiredCount());
    }

    @Test
    @DisplayName("Focus requires 50 near pins")
    public void testFocusRequirement() {
        assertEquals("Land 50 throws within 10ft of the basket", SkillUnlock.FOCUS.description());
        assertEquals(50, SkillUnlock.FOCUS.requiredCount());
    }

    @Test
    @DisplayName("Disc Mastery requires throwing all tiers")
    public void testDiscMasteryRequirement() {
        assertEquals("Throw one of each tiered disc", SkillUnlock.DISC_MASTERY.description());
        assertEquals(1, SkillUnlock.DISC_MASTERY.requiredCount());
    }

    @Test
    @DisplayName("Skill keys are lowercase enum names")
    public void testSkillKeys() {
        assertEquals("power_control", SkillUnlock.POWER_CONTROL.key());
        assertEquals("release_control", SkillUnlock.RELEASE_CONTROL.key());
        assertEquals("wind_reading", SkillUnlock.WIND_READING.key());
        assertEquals("focus", SkillUnlock.FOCUS.key());
        assertEquals("disc_mastery", SkillUnlock.DISC_MASTERY.key());
    }
}
