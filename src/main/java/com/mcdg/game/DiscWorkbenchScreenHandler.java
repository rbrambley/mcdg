package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class DiscWorkbenchScreenHandler extends ScreenHandler {
    private final Inventory inventory;

    // Server-side constructor (called by BlockEntity)
    public DiscWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(McdgScreenHandlers.DISC_WORKBENCH, syncId);
        this.inventory = inventory;
        initSlots(playerInventory);
    }

    // Client-side constructor (called by ScreenHandlerType factory)
    public DiscWorkbenchScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(McdgScreenHandlers.DISC_WORKBENCH, syncId);
        this.inventory = new SimpleInventory(3);
        initSlots(playerInventory);
    }

    private void initSlots(PlayerInventory playerInventory) {
        // Disc input slot (left)
        this.addSlot(new Slot(inventory, 0, 44, 32) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return McdgItems.isDisc(stack);
            }
        });

        // Disc enchanted book slot (middle)
        this.addSlot(new Slot(inventory, 1, 80, 32) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(McdgItems.DISC_ENCHANTED_BOOK);
            }
        });

        // Result slot (right) - output only
        this.addSlot(new Slot(inventory, 2, 116, 32) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
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

        if (slotIndex < 3) {
            // Move from workbench to player inventory
            if (!this.insertItem(originalStack, 3, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Move from player to workbench
            if (McdgItems.isDisc(originalStack)) {
                if (!this.insertItem(originalStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (originalStack.isOf(McdgItems.DISC_ENCHANTED_BOOK)) {
                if (!this.insertItem(originalStack, 1, 2, false)) {
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

    /**
     * Returns true when the workbench has a valid disc + book combination and the
     * output slot is empty, meaning the Apply button can be pressed.
     */
    public boolean canApply() {
        ItemStack disc = inventory.getStack(0);
        ItemStack book = inventory.getStack(1);
        if (!McdgItems.isDisc(disc) || !book.isOf(McdgItems.DISC_ENCHANTED_BOOK)) {
            return false;
        }
        if (!inventory.getStack(2).isEmpty()) {
            return false;
        }
        DiscEnchantment enchant = DiscEnchantedBook.getEnchantment(book);
        int level = DiscEnchantedBook.getLevel(book);
        if (enchant == null || level <= 0) {
            return false;
        }
        int currentLevel = DiscEnchantmentHelper.getLevel(disc, enchant);
        return currentLevel < level;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == 0 && canApply()) {
            ItemStack disc = inventory.getStack(0);
            ItemStack book = inventory.getStack(1);
            DiscEnchantment enchant = DiscEnchantedBook.getEnchantment(book);
            int level = DiscEnchantedBook.getLevel(book);

            ItemStack result = disc.copy();
            result.setCount(1);
            DiscEnchantmentHelper.setLevel(result, enchant, level);
            inventory.setStack(2, result);

            book.decrement(1);
            if (book.isEmpty()) {
                inventory.setStack(1, ItemStack.EMPTY);
            }
            disc.decrement(1);
            if (disc.isEmpty()) {
                inventory.setStack(0, ItemStack.EMPTY);
            }
            return true;
        }
        return super.onButtonClick(player, id);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    public Inventory getInventory() {
        return inventory;
    }
}
