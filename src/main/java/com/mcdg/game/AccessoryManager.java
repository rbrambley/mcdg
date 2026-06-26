package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks active accessory effects from player inventory.
 * Accessories can be in any inventory slot to provide passive effects.
 */
public final class AccessoryManager {
    private AccessoryManager() {}

    /**
     * Returns a map of active accessory effects and their total levels for the player.
     */
    public static Map<AccessoryEffect, Integer> getActiveEffects(PlayerEntity player) {
        Map<AccessoryEffect, Integer> effects = new EnumMap<>(AccessoryEffect.class);
        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof AccessoryItem accessory) {
                AccessoryEffect effect = accessory.effect();
                effects.merge(effect, 1, Integer::sum);
            }
        }

        return effects;
    }

    /**
     * Returns the total level of a specific effect active on the player.
     */
    public static int getEffectLevel(PlayerEntity player, AccessoryEffect effect) {
        return getActiveEffects(player).getOrDefault(effect, 0);
    }

    /**
     * Returns true if the player has any active accessory that provides the effect.
     */
    public static boolean hasEffect(PlayerEntity player, AccessoryEffect effect) {
        return getEffectLevel(player, effect) > 0;
    }

    /**
     * Computes the total durability preservation multiplier (0.0 to 1.0).
     * Each level of durability preservation reduces the chance of durability loss.
     */
    public static float getDurabilityPreserveMultiplier(PlayerEntity player) {
        int level = getEffectLevel(player, AccessoryEffect.DURABILITY_PRESERVE);
        return level * AccessoryEffect.DURABILITY_PRESERVE.perLevelMultiplier();
    }

    /**
     * Computes the stability multiplier adjustment from accessories.
     * Positive values reduce curve/fade.
     */
    public static float getStabilityBonus(PlayerEntity player) {
        int level = getEffectLevel(player, AccessoryEffect.GRIP_STABILITY);
        return level * AccessoryEffect.GRIP_STABILITY.perLevelMultiplier();
    }

    /**
     * Computes the throw speed/distance multiplier bonus from range finder accessories.
     */
    public static float getRangeBonus(PlayerEntity player) {
        int level = getEffectLevel(player, AccessoryEffect.RANGE_FINDER);
        return level * AccessoryEffect.RANGE_FINDER.perLevelMultiplier();
    }

    /**
     * Applies accessory effects to the base disc stats to create an effective
     * stat set for the current throw.
     */
    public static DiscStats applyAccessoryEffects(DiscStats baseStats, PlayerEntity player) {
        float stabilityBonus = getStabilityBonus(player);
        double effectiveStability = Math.min(2.0, baseStats.stabilityMultiplier() + stabilityBonus);
        double effectiveThrowSpeed = Math.min(2.0, baseStats.throwSpeedMultiplier() + getRangeBonus(player));
        return new DiscStats(
                baseStats.glideMultiplier(),
                effectiveStability,
                effectiveThrowSpeed,
                baseStats.windResistance()
        );
    }
}