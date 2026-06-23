package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

        // Disc slot (left)
        this.addSlot(new Slot(inventory, 0, 44, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(McdgItems.TRAINING_DISC) || stack.isOf(McdgItems.ELYTRA_DISC);
            }
        });

        // Book slot (right)
        this.addSlot(new Slot(inventory, 1, 116, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(McdgItems.DISC_ENCHANTED_BOOK);
            }
        });

        // Upgrade material slot (bottom center)
        this.addSlot(new Slot(inventory, 2, 80, 70) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.NETHERITE_INGOT);
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
            if (originalStack.isOf(McdgItems.TRAINING_DISC) || originalStack.isOf(McdgItems.ELYTRA_DISC)) {
                if (!this.insertItem(originalStack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (originalStack.isOf(McdgItems.DISC_ENCHANTED_BOOK)) {
                if (!this.insertItem(originalStack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (originalStack.isOf(Items.NETHERITE_INGOT)) {
                if (!this.insertItem(originalStack, 2, 3, false)) {
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
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id == 0) {
            // Apply enchantment from book to disc
            ItemStack disc = inventory.getStack(0);
            ItemStack book = inventory.getStack(1);
            if (!disc.isOf(McdgItems.TRAINING_DISC) || !book.isOf(McdgItems.DISC_ENCHANTED_BOOK)) {
                return true;
            }
            DiscEnchantment enchant = DiscEnchantedBook.getEnchantment(book);
            int level = DiscEnchantedBook.getLevel(book);
            if (enchant == null || level <= 0) {
                return true;
            }
            int currentLevel = DiscEnchantmentHelper.getLevel(disc, enchant);
            if (currentLevel >= level) {
                return true;
            }
            DiscEnchantmentHelper.setLevel(disc, enchant, level);
            book.decrement(1);
            if (book.isEmpty()) {
                inventory.setStack(1, ItemStack.EMPTY);
            }
            return true;
        }
        if (id == 1) {
            // Apply netherite upgrade to Elytra Disc (convert to netherite item)
            ItemStack disc = inventory.getStack(0);
            ItemStack upgrade = inventory.getStack(2);
            if (!disc.isOf(McdgItems.ELYTRA_DISC) || !upgrade.isOf(Items.NETHERITE_INGOT)) {
                return true;
            }
            // Convert to netherite version
            ItemStack netheriteDisc = new ItemStack(McdgItems.ELYTRA_DISC_NETHERITE, disc.getCount());

            // Preserve disc enchantments
            java.util.Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(disc);
            for (java.util.Map.Entry<DiscEnchantment, Integer> entry : enchantments.entrySet()) {
                DiscEnchantmentHelper.setLevel(netheriteDisc, entry.getKey(), entry.getValue());
            }

            inventory.setStack(0, netheriteDisc);
            upgrade.decrement(1);
            if (upgrade.isEmpty()) {
                inventory.setStack(2, ItemStack.EMPTY);
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
