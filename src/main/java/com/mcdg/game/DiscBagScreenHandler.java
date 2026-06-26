package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.UUID;

/**
 * Screen handler for the disc bag inventory GUI.
 * Provides 12 slots for disc storage plus player inventory access.
 */
public class DiscBagScreenHandler extends ScreenHandler {
    private final Inventory bagInventory;
    private final UUID bagUuid;

    public DiscBagScreenHandler(int syncId, PlayerInventory playerInventory, Inventory bagInventory, UUID bagUuid) {
        super(McdgScreenHandlers.DISC_BAG, syncId);
        this.bagInventory = bagInventory;
        this.bagUuid = bagUuid;
        initSlots(playerInventory);
    }

    private void initSlots(PlayerInventory playerInventory) {
        // Disc bag inventory (2 rows of 6 slots)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                this.addSlot(new DiscSlot(bagInventory, col + row * 6, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 58 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 116));
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack originalStack = slot.getStack();
        ItemStack stack = originalStack.copy();

        if (slotIndex < 12) {
            // Move from bag to player inventory
            if (!this.insertItem(originalStack, 12, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player to bag (only if it's a disc)
            if (McdgItems.isDisc(originalStack)) {
                if (!this.insertItem(originalStack, 0, 12, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        return stack;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Client-side handler has no UUID; defer to server-side validation.
        if (bagUuid == null) {
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(McdgItems.DISC_BAG) && bagUuid.equals(DiscBagItem.getBagUuid(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Custom slot that only accepts disc items.
     */
    private static class DiscSlot extends Slot {
        public DiscSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return McdgItems.isDisc(stack);
        }
    }
}