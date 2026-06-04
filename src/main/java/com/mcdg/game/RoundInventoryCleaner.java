package com.mcdg.game;

import net.minecraft.item.BannerItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SignItem;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RoundInventoryCleaner {
    private RoundInventoryCleaner() {
    }

    public static boolean purgeJunkItems(ServerPlayerEntity player) {
        return purge(player, false);
    }

    public static boolean purgeRoundItemsAndJunk(ServerPlayerEntity player) {
        return purge(player, true);
    }

    public static void restoreRoundInventory(ServerPlayerEntity player) {
        // Keep cleanup deterministic first so restoration always starts from a known state.
        purge(player, true);
        player.giveItemStack(new ItemStack(McdgItems.TRAINING_DISC, 1));
        ScorecardManager.ensureScorecardInInventory(player);
        player.getInventory().markDirty();
    }

    public static boolean isJunkItem(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BannerItem
            || item instanceof SignItem
            || item instanceof HangingSignItem
                || stack.isOf(Items.LANTERN)
                || stack.isOf(Items.SOUL_LANTERN)
            || stack.isOf(Items.SEAGRASS)
            || stack.isOf(Items.KELP)
                || stack.isOf(Items.WHEAT_SEEDS)
                || stack.isOf(Items.BEETROOT_SEEDS)
                || stack.isOf(Items.MELON_SEEDS)
                || stack.isOf(Items.PUMPKIN_SEEDS)
                || stack.isOf(Items.TORCHFLOWER_SEEDS)
                || stack.isOf(Items.PITCHER_POD);
    }

    private static boolean purge(ServerPlayerEntity player, boolean includeRoundThrowItems) {
        boolean removed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            boolean removeRoundItems = includeRoundThrowItems
                    && (stack.isOf(McdgItems.TRAINING_DISC)
                        || stack.isOf(Items.ENDER_PEARL)
                        || HoleTeeMapManager.isManagedHoleMap(stack));

            if (removeRoundItems || isJunkItem(stack)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
                removed = true;
            }
        }

        if (removed) {
            player.getInventory().markDirty();
        }
        return removed;
    }
}
