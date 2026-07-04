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
 * Provides 18 slots for disc and accessory storage plus player inventory access.
 */
public class DiscBagScreenHandler extends ScreenHandler {
    private static final int BAG_COLUMNS = 6;
    private static final int BAG_ROWS = 3;
    private static final int BAG_SLOT_COUNT = BAG_COLUMNS * BAG_ROWS;
    private static final int PLAYER_INVENTORY_START = 9;
    private static final int PLAYER_INVENTORY_Y = 83;
    private static final int HOTBAR_Y = 137;

    private final Inventory bagInventory;
    private final UUID bagUuid;

    public DiscBagScreenHandler(int syncId, PlayerInventory playerInventory, Inventory bagInventory, UUID bagUuid) {
        super(McdgScreenHandlers.DISC_BAG, syncId);
        this.bagInventory = bagInventory;
        this.bagUuid = bagUuid;
        initSlots(playerInventory);
    }

    private void initSlots(PlayerInventory playerInventory) {
        // Disc bag inventory (3 rows of 6 slots, left-aligned to match vanilla container grid)
        for (int row = 0; row < BAG_ROWS; row++) {
            for (int col = 0; col < BAG_COLUMNS; col++) {
                this.addSlot(new DiscSlot(bagInventory, col + row * BAG_COLUMNS, 8 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + PLAYER_INVENTORY_START, 8 + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
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

        if (slotIndex < BAG_SLOT_COUNT) {
            // Move from bag to player inventory
            if (!this.insertItem(originalStack, BAG_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player to bag (only if it's a disc or accessory)
            if (DiscBagItem.canStore(originalStack)) {
                if (!this.insertItem(originalStack, 0, BAG_SLOT_COUNT, false)) {
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
            if (DiscBagItem.isDiscBag(stack) && bagUuid.equals(DiscBagItem.getBagUuid(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Custom slot that accepts disc items and MCDG accessories.
     */
    private static class DiscSlot extends Slot {
        public DiscSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return DiscBagItem.canStore(stack);
        }
    }
}
