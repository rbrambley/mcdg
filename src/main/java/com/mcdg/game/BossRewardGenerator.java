package com.mcdg.game;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Generates enchanted weapons and armor rewards for boss hole completion.
 */
public final class BossRewardGenerator {
    private static final Random RANDOM = new Random();

    private BossRewardGenerator() {
    }

    /**
     * Performance tiers based on score relative to par.
     */
    public enum PerformanceTier {
        TIER_1, // Par or worse
        TIER_2, // 1-2 under par
        TIER_3, // 3+ under par
        TIER_4  // Ace
    }

    /**
     * Calculates performance tier based on score and par.
     */
    public static PerformanceTier calculateTier(int score, int par) {
        int underPar = par - score;
        if (score == 1) {
            return PerformanceTier.TIER_4; // Ace
        } else if (underPar >= 3) {
            return PerformanceTier.TIER_3;
        } else if (underPar >= 1) {
            return PerformanceTier.TIER_2;
        } else {
            return PerformanceTier.TIER_1;
        }
    }

    /**
     * Generates reward items based on performance tier.
     */
    public static List<ItemStack> generateRewards(PerformanceTier tier) {
        List<ItemStack> rewards = new ArrayList<>();

        switch (tier) {
            case TIER_1 -> {
                rewards.add(generateTier1Weapon());
                rewards.add(generateTier1Armor());
            }
            case TIER_2 -> {
                rewards.add(generateTier2Weapon());
                rewards.add(generateTier2Armor());
                rewards.add(generateTier2Bow());
            }
            case TIER_3 -> {
                rewards.add(generateTier3Weapon());
                rewards.add(generateTier3Armor());
                rewards.add(generateTier3Bow());
                rewards.add(new ItemStack(Items.DIAMOND, 1));
            }
            case TIER_4 -> {
                rewards.add(generateTier4Weapon());
                rewards.add(generateTier4Armor());
                rewards.add(generateTier4Bow());
                rewards.add(new ItemStack(Items.DIAMOND, 2 + RANDOM.nextInt(2)));
            }
        }

        return rewards;
    }

    // Tier 1 rewards (Par or worse)
    private static ItemStack generateTier1Weapon() {
        return enchanted(Items.IRON_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 1);
        });
    }

    private static ItemStack generateTier1Armor() {
        return enchanted(Items.IRON_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 1);
        });
    }

    // Tier 2 rewards (1-2 under par)
    private static ItemStack generateTier2Weapon() {
        return enchanted(Items.IRON_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 2);
            if (RANDOM.nextFloat() < 0.5f) {
                b.add(Enchantments.SMITE, 1);
            }
        });
    }

    private static ItemStack generateTier2Armor() {
        return enchanted(Items.IRON_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 2);
        });
    }

    private static ItemStack generateTier2Bow() {
        return enchanted(Items.BOW, b -> {
            b.add(Enchantments.POWER, 1);
        });
    }

    // Tier 3 rewards (3+ under par)
    private static ItemStack generateTier3Weapon() {
        return enchanted(Items.DIAMOND_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 3);
            b.add(Enchantments.SMITE, 2);
            if (RANDOM.nextFloat() < 0.3f) {
                b.add(Enchantments.UNBREAKING, 1);
            }
        });
    }

    private static ItemStack generateTier3Armor() {
        return enchanted(Items.DIAMOND_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 3);
            if (RANDOM.nextFloat() < 0.3f) {
                b.add(Enchantments.UNBREAKING, 1);
            }
        });
    }

    private static ItemStack generateTier3Bow() {
        return enchanted(Items.BOW, b -> {
            b.add(Enchantments.POWER, 2);
            if (RANDOM.nextFloat() < 0.5f) {
                b.add(Enchantments.PUNCH, 1);
            }
        });
    }

    // Tier 4 rewards (Ace)
    private static ItemStack generateTier4Weapon() {
        return enchanted(Items.NETHERITE_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 4);
            b.add(Enchantments.SMITE, 3);
            b.add(Enchantments.MENDING, 1);
            if (RANDOM.nextFloat() < 0.5f) {
                b.add(Enchantments.UNBREAKING, 2);
            }
        });
    }

    private static ItemStack generateTier4Armor() {
        return enchanted(Items.NETHERITE_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 4);
            b.add(Enchantments.UNBREAKING, 3);
            b.add(Enchantments.MENDING, 1);
        });
    }

    private static ItemStack generateTier4Bow() {
        return enchanted(Items.BOW, b -> {
            b.add(Enchantments.POWER, 4);
            b.add(Enchantments.FLAME, 1);
            b.add(Enchantments.INFINITY, 1);
            if (RANDOM.nextFloat() < 0.5f) {
                b.add(Enchantments.UNBREAKING, 2);
            }
        });
    }

    /**
     * Creates an enchanted item stack.
     */
    private static ItemStack enchanted(net.minecraft.item.Item item, Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder> builder) {
        ItemStack stack = new ItemStack(item);
        EnchantmentHelper.apply(stack, builder);
        return stack;
    }
}