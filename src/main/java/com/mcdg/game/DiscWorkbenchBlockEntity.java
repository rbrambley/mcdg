package com.mcdg.game;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public class DiscWorkbenchBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private final SimpleInventory inventory = new SimpleInventory(2) {
        @Override
        public boolean isValid(int slot, ItemStack stack) {
            if (slot == 0) {
                return McdgItems.isDisc(stack);
            }
            if (slot == 1) {
                return stack.isOf(McdgItems.DISC_ENCHANTED_BOOK);
            }
            return false;
        }
    };

    public DiscWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(McdgBlockEntities.DISC_WORKBENCH, pos, state);
    }

    public SimpleInventory getInventory() {
        return inventory;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Disc Workbench");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new DiscWorkbenchScreenHandler(syncId, playerInventory, inventory);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(3, ItemStack.EMPTY);
        Inventories.readNbt(nbt, stacks, registryLookup);
        for (int i = 0; i < stacks.size(); i++) {
            inventory.setStack(i, stacks.get(i));
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        DefaultedList<ItemStack> stacks = DefaultedList.ofSize(3, ItemStack.EMPTY);
        for (int i = 0; i < stacks.size(); i++) {
            stacks.set(i, inventory.getStack(i));
        }
        Inventories.writeNbt(nbt, stacks, registryLookup);
    }
}
