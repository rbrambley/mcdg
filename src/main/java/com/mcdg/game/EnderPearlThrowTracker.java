package com.mcdg.game;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

public final class EnderPearlThrowTracker {
    private EnderPearlThrowTracker() {
    }

    public static void register(ActiveCourseManager courseManager, RoundStateManager roundStateManager) {
        UseItemCallback.EVENT.register((player, world, hand) -> onUseItem(player, hand, courseManager, roundStateManager));
    }

        private static TypedActionResult<ItemStack> onUseItem(
            net.minecraft.entity.player.PlayerEntity player,
            Hand hand,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        if (!courseManager.isRoundActive()) {
            return TypedActionResult.pass(player.getStackInHand(hand));
        }

        if (serverPlayer.getStackInHand(hand).isOf(Items.ENDER_PEARL)) {
            roundStateManager.recordThrow(serverPlayer.getUuid(), serverPlayer.getBlockPos());
            // Keep one-disc loop active by replenishing immediately after each throw.
            serverPlayer.giveItemStack(new ItemStack(Items.ENDER_PEARL, 1));
        }

        return TypedActionResult.pass(player.getStackInHand(hand));
    }
}
