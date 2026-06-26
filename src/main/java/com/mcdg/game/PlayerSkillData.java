package com.mcdg.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent player skill data.
 */
public final class PlayerSkillData {
    public int version = 1;
    public int totalThrows = 0;
    public int roundsCompleted = 0;
    public int holesCompleted = 0;
    public int aces = 0;
    public int nearPins = 0;
    public int totalXp = 0;
    public Map<String, Boolean> unlockedSkills = new HashMap<>();
    public Map<String, Integer> tierDiscsCrafted = new HashMap<>();

    public PlayerSkillData() {}
}