package com.mcdg.game;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

/**
 * Awards survival-mode players with tool sets, armor, weapons, and enchanted books
 * based on round performance (score vs par, ace count, and strict ruleset).
 */
public final class RoundRewardService {
    private static final Random RANDOM = new Random();

    private RoundRewardService() {
    }

    public static void grantRoundRewards(
            ServerPlayerEntity player,
            int totalStrokes,
            int totalPar,
            int aceCount,
            boolean strictRuleset
    ) {
        if (player.interactionManager.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        int delta = totalStrokes - totalPar;
        List<ItemStack> rewards = new ArrayList<>();

        // Base tier by score vs par
        if (delta < 0) {
            rewards.addAll(underParRewards());
        } else if (delta == 0) {
            rewards.addAll(parRewards());
        } else {
            rewards.addAll(overParRewards());
        }

        // Strict ruleset bonus
        if (strictRuleset) {
            rewards.add(randomEnchantedBook(true));
        }

        // Ace bonuses (one per ace, always an enchanted book)
        for (int i = 0; i < aceCount; i++) {
            rewards.add(randomEnchantedBook(false));
        }

        if (rewards.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : rewards) {
            if (!stack.isEmpty()) {
                inventory.insertStack(stack);
            }
        }
        inventory.markDirty();

        player.sendMessage(
                Text.literal("Round rewards received! (" + rewards.size() + " items)")
                        .formatted(Formatting.GREEN),
                true
        );

        McdgMod.LOGGER.info(
                "Round rewards granted | player={} strokes={} par={} delta={} aces={} strict={} items={}",
                player.getGameProfile().getName(), totalStrokes, totalPar, delta, aceCount, strictRuleset, rewards.size()
        );
    }

    public static void reset() {
        // No pending state to clear; rewards are granted immediately.
    }

    private static List<ItemStack> underParRewards() {
        List<ItemStack> list = new ArrayList<>();
        list.add(enchanted(Items.DIAMOND_PICKAXE, b -> {
            b.add(Enchantments.EFFICIENCY, 3);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.DIAMOND_AXE, b -> {
            b.add(Enchantments.EFFICIENCY, 3);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.DIAMOND_SHOVEL, b -> {
            b.add(Enchantments.EFFICIENCY, 3);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.IRON_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 3);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.IRON_HELMET, b -> {
            b.add(Enchantments.PROTECTION, 2);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.IRON_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 2);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.IRON_LEGGINGS, b -> {
            b.add(Enchantments.PROTECTION, 2);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        list.add(enchanted(Items.IRON_BOOTS, b -> {
            b.add(Enchantments.PROTECTION, 2);
            b.add(Enchantments.FEATHER_FALLING, 2);
            b.add(Enchantments.UNBREAKING, 2);
        }));
        return list;
    }

    private static List<ItemStack> parRewards() {
        List<ItemStack> list = new ArrayList<>();
        list.add(enchanted(Items.IRON_PICKAXE, b -> {
            b.add(Enchantments.EFFICIENCY, 2);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.IRON_AXE, b -> {
            b.add(Enchantments.EFFICIENCY, 2);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.IRON_SHOVEL, b -> {
            b.add(Enchantments.EFFICIENCY, 2);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.IRON_SWORD, b -> {
            b.add(Enchantments.SHARPNESS, 2);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.LEATHER_HELMET, b -> {
            b.add(Enchantments.PROTECTION, 1);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.LEATHER_CHESTPLATE, b -> {
            b.add(Enchantments.PROTECTION, 1);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.LEATHER_LEGGINGS, b -> {
            b.add(Enchantments.PROTECTION, 1);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        list.add(enchanted(Items.LEATHER_BOOTS, b -> {
            b.add(Enchantments.PROTECTION, 1);
            b.add(Enchantments.UNBREAKING, 1);
        }));
        return list;
    }

    private static List<ItemStack> overParRewards() {
        List<ItemStack> list = new ArrayList<>();
        list.add(new ItemStack(Items.STONE_PICKAXE));
        list.add(new ItemStack(Items.STONE_AXE));
        list.add(new ItemStack(Items.STONE_SHOVEL));
        list.add(new ItemStack(Items.STONE_SWORD));
        list.add(new ItemStack(Items.BREAD, 16));
        list.add(new ItemStack(Items.ARROW, 32));
        return list;
    }

    private static ItemStack randomEnchantedBook(boolean highTier) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        List<Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder>> pool = highTier ? highTierBooks() : standardBooks();
        Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder> choice = pool.get(RANDOM.nextInt(pool.size()));
        EnchantmentHelper.apply(book, choice);
        return book;
    }

    private static List<Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder>> standardBooks() {
        List<Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder>> list = new ArrayList<>();
        list.add(b -> b.add(Enchantments.EFFICIENCY, 2));
        list.add(b -> b.add(Enchantments.UNBREAKING, 2));
        list.add(b -> b.add(Enchantments.SHARPNESS, 2));
        list.add(b -> b.add(Enchantments.PROTECTION, 2));
        list.add(b -> b.add(Enchantments.FEATHER_FALLING, 2));
        list.add(b -> b.add(Enchantments.FORTUNE, 1));
        list.add(b -> b.add(Enchantments.LOOTING, 1));
        list.add(b -> b.add(Enchantments.MENDING, 1));
        return list;
    }

    private static List<Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder>> highTierBooks() {
        List<Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder>> list = new ArrayList<>();
        list.add(b -> b.add(Enchantments.EFFICIENCY, 4));
        list.add(b -> b.add(Enchantments.UNBREAKING, 3));
        list.add(b -> b.add(Enchantments.SHARPNESS, 4));
        list.add(b -> b.add(Enchantments.PROTECTION, 3));
        list.add(b -> b.add(Enchantments.FORTUNE, 2));
        list.add(b -> b.add(Enchantments.LOOTING, 2));
        list.add(b -> b.add(Enchantments.MENDING, 1));
        list.add(b -> b.add(Enchantments.SILK_TOUCH, 1));
        return list;
    }

    private static ItemStack enchanted(
            net.minecraft.item.Item item,
            Consumer<net.minecraft.component.type.ItemEnchantmentsComponent.Builder> builder
    ) {
        ItemStack stack = new ItemStack(item);
        EnchantmentHelper.apply(stack, builder);
        return stack;
    }
}
