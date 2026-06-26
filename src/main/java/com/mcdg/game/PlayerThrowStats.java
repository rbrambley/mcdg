package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;

/**
 * Combines tiered disc stats with player skill and accessory effects.
 */
public final class PlayerThrowStats {
    private PlayerThrowStats() {}

    /**
     * Returns the effective disc stats for a throw, applying skill unlocks and accessory effects.
     */
    public static DiscStats applyPlayerEffects(DiscStats baseStats, PlayerEntity player) {
        if (player == null) {
            return baseStats;
        }

        double stability = baseStats.stabilityMultiplier();
        double throwSpeed = baseStats.throwSpeedMultiplier();
        double glide = baseStats.glideMultiplier();
        double windResistance = baseStats.windResistance();

        // Accessory effects
        stability += AccessoryManager.getStabilityBonus(player);

        // Skill effects (only apply to ServerPlayerEntity on the server)
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            if (PlayerSkillManager.hasSkill(serverPlayer, SkillUnlock.WIND_READING)) {
                windResistance = Math.min(1.0, windResistance + 0.15);
            }
            if (PlayerSkillManager.hasSkill(serverPlayer, SkillUnlock.DISC_MASTERY)) {
                glide = Math.min(2.0, glide + 0.05);
                stability = Math.min(2.0, stability + 0.05);
                throwSpeed = Math.min(2.0, throwSpeed + 0.05);
            }
        }

        return new DiscStats(
                Math.min(2.0, glide),
                Math.min(2.0, stability),
                Math.min(2.0, throwSpeed),
                Math.min(1.0, windResistance)
        );
    }

    /**
     * Returns a power multiplier bonus for players with Power Control skill.
     */
    public static float getPowerMultiplierBonus(PlayerEntity player) {
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && PlayerSkillManager.hasSkill(serverPlayer, SkillUnlock.POWER_CONTROL)) {
            return 0.05f;
        }
        return 0.0f;
    }

    /**
     * Returns an exhaustion multiplier for players with Focus skill.
     * 1.0 means no change; lower values reduce stamina cost.
     */
    public static float getExhaustionMultiplier(PlayerEntity player) {
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && PlayerSkillManager.hasSkill(serverPlayer, SkillUnlock.FOCUS)) {
            return 0.85f;
        }
        return 1.0f;
    }

    /**
     * Returns an angle penalty reduction for players with Release Control skill.
     */
    public static float getAnglePenaltyReduction(PlayerEntity player) {
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                && PlayerSkillManager.hasSkill(serverPlayer, SkillUnlock.RELEASE_CONTROL)) {
            return 0.25f;
        }
        return 0.0f;
    }
}