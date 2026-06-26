package com.mcdg.game;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Block item for the Disc Workbench. Adds a tooltip describing its use.
 */
public final class DiscWorkbenchItem extends BlockItem {
    public DiscWorkbenchItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, net.minecraft.client.item.TooltipType type) {
        tooltip.add(Text.translatable("tooltip.mcdg.disc_workbench").formatted(Formatting.GRAY));
    }
}
