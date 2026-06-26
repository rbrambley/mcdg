package com.mcdg.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A portable disc bag that stores multiple discs for quick access during rounds.
 * Right-click to open the bag inventory and manage disc collection.
 */
public class DiscBagItem extends Item {
    private static final int BAG_SLOTS = 12;
    private static final String KEY_UUID = "BagUuid";

    public DiscBagItem(Settings settings) {
        super(settings.maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            ItemStack bagStack = user.getStackInHand(hand);
            ensureBagUuid(bagStack);
            user.openHandledScreen(new DiscBagScreenHandlerFactory(bagStack, world.getRegistryManager()));
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    /**
     * Returns the bag's tracked UUID, or null if none is present.
     */
    public static UUID getBagUuid(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(KEY_UUID)) {
            return null;
        }
        try {
            return UUID.fromString(nbt.getString(KEY_UUID));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Assigns a UUID to the bag if it does not already have one.
     * Returns the existing or newly assigned UUID.
     */
    public static UUID ensureBagUuid(ItemStack stack) {
        UUID existing = getBagUuid(stack);
        if (existing != null) {
            return existing;
        }
        UUID uuid = UUID.randomUUID();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(KEY_UUID, uuid.toString());
        });
        return uuid;
    }
}