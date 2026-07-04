package com.mcdg.game;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;

import java.util.List;

/**
 * Combat-focused enchantments for boss hole rewards.
 */
public enum CombatEnchantment {
    SHARPNESS(Enchantments.SHARPNESS, 1, 5, "Sharpness"),
    SMITE(Enchantments.SMITE, 1, 5, "Smite"),
    PROTECTION(Enchantments.PROTECTION, 1, 4, "Protection"),
    UNBREAKING(Enchantments.UNBREAKING, 1, 3, "Unbreaking"),
    MENDING(Enchantments.MENDING, 1, 1, "Mending"),
    FEATHER_FALLING(Enchantments.FEATHER_FALLING, 1, 4, "Feather Falling"),
    POWER(Enchantments.POWER, 1, 5, "Power"),
    PUNCH(Enchantments.PUNCH, 1, 2, "Punch"),
    FLAME(Enchantments.FLAME, 1, 1, "Flame");

    private final Enchantment enchantment;
    private final int minLevel;
    private final int maxLevel;
    private final String displayName;

    CombatEnchantment(Enchantment enchantment, int minLevel, int maxLevel, String displayName) {
        this.enchantment = enchantment;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.displayName = displayName;
    }

    public Enchantment enchantment() {
        return enchantment;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Gets enchantments appropriate for the given item type.
     */
    public static List<CombatEnchantment> forItemType(ItemType itemType) {
        return switch (itemType) {
            case SWORD -> List.of(SHARPNESS, SMITE, UNBREAKING, MENDING);
            case ARMOR -> List.of(PROTECTION, UNBREAKING, MENDING, FEATHER_FALLING);
            case BOW -> List.of(POWER, PUNCH, FLAME, UNBREAKING);
        };
    }

    /**
     * Item types for combat equipment.
     */
    public enum ItemType {
        SWORD,
        ARMOR,
        BOW
    }
}
