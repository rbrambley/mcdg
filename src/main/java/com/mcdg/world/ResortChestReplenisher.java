package com.mcdg.world;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Periodically replenishes the starter chest in the resort housing building.
 * Ensures wooden tools and apples are always available for players.
 */
public final class ResortChestReplenisher {
    private static final int REPLENISH_INTERVAL_TICKS = 100; // Check every 5 seconds
    private static BlockPos chestPos = null;
    private static int tickCounter = 0;

    private ResortChestReplenisher() {}

    public static void setChestPosition(BlockPos pos) {
        chestPos = pos;
    }

    public static void clear() {
        chestPos = null;
        tickCounter = 0;
    }

    public static void tick(MinecraftServer server) {
        if (chestPos == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < REPLENISH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }

        if (!(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            return;
        }

        replenishChest(chest);
    }

    private static void replenishChest(ChestBlockEntity chest) {
        // Slot 0: Wooden axe
        if (!hasItem(chest, 0, Items.WOODEN_AXE)) {
            chest.setStack(0, new ItemStack(Items.WOODEN_AXE));
        }

        // Slot 1: Wooden pickaxe
        if (!hasItem(chest, 1, Items.WOODEN_PICKAXE)) {
            chest.setStack(1, new ItemStack(Items.WOODEN_PICKAXE));
        }

        // Slot 2: Wooden shovel
        if (!hasItem(chest, 2, Items.WOODEN_SHOVEL)) {
            chest.setStack(2, new ItemStack(Items.WOODEN_SHOVEL));
        }

        // Slot 3: 6 apples (replenish if count < 6)
        ItemStack appleStack = chest.getStack(3);
        if (appleStack.isEmpty() || appleStack.getItem() != Items.APPLE || appleStack.getCount() < 6) {
            chest.setStack(3, new ItemStack(Items.APPLE, 6));
        }
    }

    private static boolean hasItem(ChestBlockEntity chest, int slot, net.minecraft.item.Item item) {
        ItemStack stack = chest.getStack(slot);
        return !stack.isEmpty() && stack.getItem() == item;
    }
}
