package com.mcdg.world;

import com.mcdg.McdgMod;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives starter tools and apples to players when they interact with the resort starter chest.
 * Each player has a 5-minute cooldown per interaction.
 */
public final class ResortChestReplenisher {
    private static final long COOLDOWN_TICKS = 6000; // 5 minutes (60 seconds * 5)
    private static BlockPos chestPos = null;
    private static final Map<UUID, Long> lastReplenishTimeByPlayer = new ConcurrentHashMap<>();

    private ResortChestReplenisher() {}

    public static void setChestPosition(BlockPos pos) {
        chestPos = pos;
    }

    public static void clear() {
        chestPos = null;
        lastReplenishTimeByPlayer.clear();
    }

    public static void registerInteractionHandler() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (chestPos == null) {
                return true;
            }

            if (!pos.equals(chestPos)) {
                return true;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return true;
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
                return true;
            }

            // Give items directly to player inventory
            giveStarterItems(serverPlayer);

            // Update cooldown
            lastReplenishTimeByPlayer.put(playerId, now);

            McdgMod.LOGGER.info(
                    "Starter chest used by player={} at pos=({}, {}, {})",
                    serverPlayer.getGameProfile().getName(),
                    pos.getX(), pos.getY(), pos.getZ()
            );

            return true;
        });
    }

    private static void giveStarterItems(ServerPlayerEntity player) {
        player.getInventory().insertStack(new ItemStack(Items.WOODEN_AXE));
        player.getInventory().insertStack(new ItemStack(Items.WOODEN_PICKAXE));
        player.getInventory().insertStack(new ItemStack(Items.WOODEN_SHOVEL));
        player.getInventory().insertStack(new ItemStack(Items.APPLE, 6));
        player.getInventory().markDirty();

        player.sendMessage(
                net.minecraft.text.Text.literal("Received starter tools and apples!")
                        .formatted(net.minecraft.util.Formatting.GREEN),
                true
        );
    }
}
