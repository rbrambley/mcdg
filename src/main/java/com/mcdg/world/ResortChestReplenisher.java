package com.mcdg.world;

import com.mcdg.McdgMod;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gives starter tools and apples to players when they right-click the resort starter chest.
 * Each player has a 5-minute cooldown per interaction.
 */
public final class ResortChestReplenisher {
    private static final long COOLDOWN_TICKS = 6000; // 5 minutes (20 ticks/s * 300s)
    private static volatile BlockPos chestPos = null;
    private static final Map<UUID, Long> lastReplenishTimeByPlayer = new ConcurrentHashMap<>();
    private static final AtomicBoolean handlerRegistered = new AtomicBoolean(false);

    private ResortChestReplenisher() {}

    public static void setChestPosition(BlockPos pos) {
        chestPos = pos == null ? null : pos.toImmutable();
    }

    public static void clear() {
        chestPos = null;
        lastReplenishTimeByPlayer.clear();
    }

    /**
     * Registers the chest interaction handler exactly once for the lifetime of the JVM.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    public static void registerInteractionHandler() {
        if (!handlerRegistered.compareAndSet(false, true)) {
            return;
        }

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            // Only handle main hand to avoid double-firing, and only on the server side.
            if (hand != Hand.MAIN_HAND || world.isClient()) {
                return ActionResult.PASS;
            }

            BlockPos target = chestPos;
            if (target == null || !hitResult.getBlockPos().equals(target)) {
                return ActionResult.PASS;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            if (serverPlayer.interactionManager.getGameMode() != GameMode.SURVIVAL) {
                return ActionResult.PASS;
            }

            long now = world.getTime();
            UUID playerId = serverPlayer.getUuid();
            Long lastReplenish = lastReplenishTimeByPlayer.get(playerId);

            if (lastReplenish != null && (now - lastReplenish) < COOLDOWN_TICKS) {
                long remainingTicks = COOLDOWN_TICKS - (now - lastReplenish);
                long remainingSeconds = remainingTicks / 20;
                serverPlayer.sendMessage(
                        net.minecraft.text.Text.literal("Starter chest cooldown: " + remainingSeconds + "s remaining")
                                .formatted(net.minecraft.util.Formatting.YELLOW),
                        true
                );
                return ActionResult.SUCCESS;
            }

            // Give items directly to player inventory
            giveStarterItems(serverPlayer);

            // Update cooldown
            lastReplenishTimeByPlayer.put(playerId, now);

            McdgMod.LOGGER.info(
                    "Starter chest used by player={} at pos=({}, {}, {})",
                    serverPlayer.getGameProfile().getName(),
                    target.getX(), target.getY(), target.getZ()
            );

            // Consume the interaction so the (empty) chest does not open.
            return ActionResult.SUCCESS;
        });
    }

    private static void giveStarterItems(ServerPlayerEntity player) {
        giveOrDrop(player, new ItemStack(Items.WOODEN_AXE));
        giveOrDrop(player, new ItemStack(Items.WOODEN_PICKAXE));
        giveOrDrop(player, new ItemStack(Items.WOODEN_SHOVEL));
        giveOrDrop(player, new ItemStack(Items.APPLE, 6));
        player.getInventory().markDirty();

        player.sendMessage(
                net.minecraft.text.Text.literal("Received starter tools and apples!")
                        .formatted(net.minecraft.util.Formatting.GREEN),
                true
        );
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) {
            player.dropItem(stack, false);
        }
    }
}
