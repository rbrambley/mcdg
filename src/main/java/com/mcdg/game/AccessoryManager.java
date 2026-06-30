package com.mcdg.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks active accessory effects from player inventory and disc bags.
 * Accessories can be in any inventory slot or inside a disc bag to provide passive effects.
 */
public final class AccessoryManager {
    private static final String KEY_INVENTORY = "Inventory";
    private static final String KEY_ITEMS = "Items";

    private AccessoryManager() {}

    /**
     * Returns a map of active accessory effects and their total levels for the player.
     * Effects are granted by accessories in the player's inventory as well as accessories
     * stored inside any disc bags in the player's inventory.
     */
    @SuppressWarnings("PMD.LawOfDemeter")
    public static Map<AccessoryEffect, Integer> getActiveEffects(PlayerEntity player) {
        Map<AccessoryEffect, Integer> effects = new EnumMap<>(AccessoryEffect.class);
        PlayerInventory inventory = player.getInventory();
        RegistryWrapper.WrapperLookup registryLookup = player.getWorld().getRegistryManager();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            collectAccessoryEffects(stack, effects);
            if (DiscBagItem.isDiscBag(stack)) {
                collectBagAccessoryEffects(stack, registryLookup, effects);
            }
        }

        return effects;
    }

    private static void collectAccessoryEffects(ItemStack stack, Map<AccessoryEffect, Integer> effects) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() instanceof AccessoryItem accessory) {
            AccessoryEffect effect = accessory.effect();
            effects.merge(effect, 1, Integer::sum);
        }
    }

    private static void collectBagAccessoryEffects(
            ItemStack bagStack,
            RegistryWrapper.WrapperLookup registryLookup,
            Map<AccessoryEffect, Integer> effects
    ) {
        NbtComponent customData = bagStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(KEY_INVENTORY, NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound inventoryNbt = nbt.getCompound(KEY_INVENTORY);
        if (!inventoryNbt.contains(KEY_ITEMS, NbtElement.LIST_TYPE)) {
            return;
        }
        var items = inventoryNbt.getList(KEY_ITEMS, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < items.size(); i++) {
            ItemStack.fromNbt(registryLookup, items.getCompound(i))
                    .ifPresent(stack -> collectAccessoryEffects(stack, effects));
        }
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
