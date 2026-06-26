package com.mcdg.game;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

/**
 * Helper class for managing disc bag inventory persistence in NBT.
 */
public final class DiscBagInventory {
    private static final String KEY_INVENTORY = "Inventory";

    private DiscBagInventory() {}

    /**
     * Saves the inventory contents to the bag item's NBT data.
     */
    public static void saveToBag(ItemStack bagStack, Inventory inventory, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound inventoryNbt = new NbtCompound();
        Inventories.writeNbt(inventoryNbt, toItemList(inventory), registryLookup);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, bagStack, nbt -> {
            nbt.put(KEY_INVENTORY, inventoryNbt);
        });
    }

    /**
     * Loads inventory contents from the bag item's NBT data.
     */
    public static void loadFromBag(ItemStack bagStack, Inventory inventory, RegistryWrapper.WrapperLookup registryLookup) {
        NbtComponent customData = bagStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(KEY_INVENTORY)) {
            return;
        }
        NbtCompound inventoryNbt = nbt.getCompound(KEY_INVENTORY);
        fromItemList(inventoryNbt, inventory, registryLookup);
    }

    private static net.minecraft.util.collection.DefaultedList<ItemStack> toItemList(Inventory inventory) {
        net.minecraft.util.collection.DefaultedList<ItemStack> list =
            net.minecraft.util.collection.DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.size(); i++) {
            list.set(i, inventory.getStack(i).copy());
        }
        return list;
    }

    private static void fromItemList(NbtCompound nbt, Inventory inventory, RegistryWrapper.WrapperLookup registryLookup) {
        net.minecraft.util.collection.DefaultedList<ItemStack> list =
            net.minecraft.util.collection.DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        Inventories.readNbt(nbt, list, registryLookup);
        for (int i = 0; i < Math.min(list.size(), inventory.size()); i++) {
            inventory.setStack(i, list.get(i));
        }
    }
}