package com.mcdg.game;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/**
 * Reads and writes disc-specific enchantment data on {@link ChargedDiscItem} stacks
 * using the item's custom data component.
 */
public final class DiscEnchantmentHelper {
    private static final String NBT_ROOT = "McdgDiscEnchantments";

    private DiscEnchantmentHelper() {}

    private static NbtCompound getRoot(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(NBT_ROOT, 10)) { // 10 = NbtCompound type
            return null;
        }
        return nbt.getCompound(NBT_ROOT);
    }

    private static void setRoot(ItemStack stack, NbtCompound root) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.put(NBT_ROOT, root.copy()));
    }

    /**
     * Gets the level of a specific enchantment on the disc.
     *
     * @param stack the disc item stack
     * @param enchant the enchantment to query
     * @return enchantment level (0 if absent)
     */
    public static int getLevel(ItemStack stack, DiscEnchantment enchant) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        NbtCompound root = getRoot(stack);
        if (root == null) {
            return 0;
        }
        return root.getInt(enchant.key());
    }

    /**
     * Returns all enchantment levels on the disc as a map.
     */
    public static Map<DiscEnchantment, Integer> getAll(ItemStack stack) {
        Map<DiscEnchantment, Integer> result = new EnumMap<>(DiscEnchantment.class);
        if (stack == null || stack.isEmpty()) {
            return result;
        }
        NbtCompound root = getRoot(stack);
        if (root == null) {
            return result;
        }
        for (DiscEnchantment enchant : DiscEnchantment.values()) {
            int level = root.getInt(enchant.key());
            if (level > 0) {
                result.put(enchant, Math.min(level, enchant.maxLevel()));
            }
        }
        return result;
    }

    /**
     * Sets the level of a specific enchantment on the disc.
     *
     * @param stack the disc item stack
     * @param enchant the enchantment to set
     * @param level level to set (clamped to 0..maxLevel)
     */
    public static void setLevel(ItemStack stack, DiscEnchantment enchant, int level) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int clamped = Math.max(0, Math.min(level, enchant.maxLevel()));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            NbtCompound root = nbt.contains(NBT_ROOT, 10) ? nbt.getCompound(NBT_ROOT) : new NbtCompound();
            root.putInt(enchant.key(), clamped);
            nbt.put(NBT_ROOT, root);
        });
    }

    /**
     * Removes all disc enchantments from the stack.
     */
    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        NbtCompound nbt = customData.copyNbt();
        if (nbt.contains(NBT_ROOT, 10)) {
            nbt.remove(NBT_ROOT);
            if (nbt.isEmpty()) {
                stack.remove(DataComponentTypes.CUSTOM_DATA);
            } else {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            }
        }
    }

    /**
     * Returns true if the stack has any disc enchantments.
     */
    public static boolean hasAny(ItemStack stack) {
        return !getAll(stack).isEmpty();
    }
}
