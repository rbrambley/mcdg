package com.mcdg.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for player skill data defaults and unlock evaluation.
 */
public class PlayerSkillDataTest {

    @Test
    @DisplayName("PlayerSkillData starts with zero progress")
    public void testDefaultValues() {
        PlayerSkillData data = new PlayerSkillData();
        assertEquals(0, data.totalThrows);
        assertEquals(0, data.roundsCompleted);
        assertEquals(0, data.holesCompleted);
        assertEquals(0, data.aces);
        assertEquals(0, data.nearPins);
        assertEquals(0, data.totalXp);
        assertTrue(data.unlockedSkills.isEmpty());
        assertTrue(data.tierDiscsCrafted.isEmpty());
    }

    @Test
    @DisplayName("Power Control unlocks at 100 skill XP")
    public void testPowerControlUnlock() {
        PlayerSkillData data = new PlayerSkillData();
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.POWER_CONTROL, data));

        data.totalXp = 99;
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.POWER_CONTROL, data));

        data.totalXp = 100;
        assertTrue(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.POWER_CONTROL, data));
    }

    @Test
    @DisplayName("Release Control unlocks at 10 rounds")
    public void testReleaseControlUnlock() {
        PlayerSkillData data = new PlayerSkillData();
        data.roundsCompleted = 9;
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.RELEASE_CONTROL, data));

        data.roundsCompleted = 10;
        assertTrue(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.RELEASE_CONTROL, data));
    }

    @Test
    @DisplayName("Wind Reading unlocks at 500 throws")
    public void testWindReadingUnlock() {
        PlayerSkillData data = new PlayerSkillData();
        data.totalThrows = 499;
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.WIND_READING, data));

        data.totalThrows = 500;
        assertTrue(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.WIND_READING, data));
    }

    @Test
    @DisplayName("Focus unlocks at 50 near pins")
    public void testFocusUnlock() {
        PlayerSkillData data = new PlayerSkillData();
        data.nearPins = 49;
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.FOCUS, data));

        data.nearPins = 50;
        assertTrue(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.FOCUS, data));
    }

    @Test
    @DisplayName("Disc Mastery unlocks when all tiers have been thrown")
    public void testDiscMasteryUnlock() {
        PlayerSkillData data = new PlayerSkillData();
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.DISC_MASTERY, data));

        for (DiscTier tier : DiscTier.values()) {
            data.tierDiscsCrafted.put(tier.name().toLowerCase(), 1);
        }
        assertTrue(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.DISC_MASTERY, data));
    }

    @Test
    @DisplayName("Disc Mastery stays locked when any tier is missing")
    public void testDiscMasteryLockedWithMissingTier() {
        PlayerSkillData data = new PlayerSkillData();
        for (DiscTier tier : DiscTier.values()) {
            if (tier != DiscTier.NETHERITE) {
                data.tierDiscsCrafted.put(tier.name().toLowerCase(), 1);
            }
        }
        assertFalse(SkillUnlockEvaluator.isSkillUnlocked(SkillUnlock.DISC_MASTERY, data));
    }

    @Test
    @DisplayName("evaluateUnlocksForData unlocks skills and updates data map")
    public void testEvaluateUnlocksUpdatesData() {
        PlayerSkillData data = new PlayerSkillData();
        data.totalXp = 100;
        data.roundsCompleted = 10;
        data.totalThrows = 500;
        data.nearPins = 50;

        boolean unlocked = SkillUnlockEvaluator.evaluateUnlocksForData(data);
        assertTrue(unlocked);
        assertTrue(data.unlockedSkills.get(SkillUnlock.POWER_CONTROL.key()));
        assertTrue(data.unlockedSkills.get(SkillUnlock.RELEASE_CONTROL.key()));
        assertTrue(data.unlockedSkills.get(SkillUnlock.WIND_READING.key()));
        assertTrue(data.unlockedSkills.get(SkillUnlock.FOCUS.key()));

        // Calling again should not unlock anything new.
        boolean second = SkillUnlockEvaluator.evaluateUnlocksForData(data);
        assertFalse(second);
    }
}
