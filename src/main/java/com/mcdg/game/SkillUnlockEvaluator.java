package com.mcdg.game;

/**
 * Pure logic for evaluating skill unlock requirements against player skill data.
 * Kept separate from persistence and player notifications so it can be unit tested.
 */
final class SkillUnlockEvaluator {
    private SkillUnlockEvaluator() {}

    /**
     * Evaluates whether a skill's requirements are met by the given data.
     */
    static boolean isSkillUnlocked(SkillUnlock skill, PlayerSkillData data) {
        return switch (skill) {
            case POWER_CONTROL -> data.totalXp >= skill.requiredCount();
            case RELEASE_CONTROL -> data.roundsCompleted >= skill.requiredCount();
            case WIND_READING -> data.totalThrows >= skill.requiredCount();
            case FOCUS -> data.nearPins >= skill.requiredCount();
            case DISC_MASTERY -> {
                boolean allTiers = true;
                for (DiscTier tier : DiscTier.values()) {
                    if (data.tierDiscsCrafted.getOrDefault(tier.name().toLowerCase(), 0) < 1) {
                        allTiers = false;
                        break;
                    }
                }
                yield allTiers;
            }
        };
    }

    /**
     * Updates the data's unlocked skill map based on current progress.
     * Returns true if any new skill was unlocked.
     */
    static boolean evaluateUnlocksForData(PlayerSkillData data) {
        boolean unlockedAny = false;
        for (SkillUnlock skill : SkillUnlock.values()) {
            if (Boolean.TRUE.equals(data.unlockedSkills.get(skill.key()))) {
                continue;
            }
            if (isSkillUnlocked(skill, data)) {
                data.unlockedSkills.put(skill.key(), true);
                unlockedAny = true;
            }
        }
        return unlockedAny;
    }
}
