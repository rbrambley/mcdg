package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Factory for creating disc bag screen handlers from the bag item.
 */
public class DiscBagScreenHandlerFactory implements NamedScreenHandlerFactory {
    private final ItemStack bagStack;
    private final Inventory inventory;
    private final RegistryWrapper.WrapperLookup registryLookup;
    private final UUID bagUuid;
    private PlayerEntity player;

    public DiscBagScreenHandlerFactory(ItemStack bagStack, RegistryWrapper.WrapperLookup registryLookup) {
        this.bagStack = bagStack;
        this.registryLookup = registryLookup;
        this.bagUuid = DiscBagItem.ensureBagUuid(bagStack);
        this.inventory = new SimpleInventory(DiscBagItem.BAG_SLOTS) {
            @Override
            public boolean isValid(int slot, ItemStack stack) {
                return DiscBagItem.canStore(stack);
            }

            @Override
            public void markDirty() {
                super.markDirty();
                // Save inventory state to the current bag stack (by UUID) so moving the bag
                // while the GUI is open does not write to a stale stack.
                ItemStack currentBag = findBagStack();
                if (currentBag != null) {
                    DiscBagInventory.saveToBag(currentBag, this, registryLookup);
                }
            }
        };

        // Load inventory from bag NBT if it exists
        DiscBagInventory.loadFromBag(bagStack, this.inventory, registryLookup);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.mcdg.disc_bag");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        this.player = player;
        return new DiscBagScreenHandler(syncId, playerInventory, inventory, bagUuid);
    }

    /**
     * Finds the bag stack in the player's inventory that matches our tracked UUID.
     */
    private ItemStack findBagStack() {
        if (bagStack != null && bagUuid.equals(DiscBagItem.getBagUuid(bagStack))) {
            return bagStack;
        }
        if (player == null) {
            return null;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(McdgItems.DISC_BAG) && bagUuid.equals(DiscBagItem.getBagUuid(stack))) {
                return stack;
            }
        }
        return null;
    }

}