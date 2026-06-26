package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class McdgBlocks {
    public static Block DISC_WORKBENCH = Blocks.AIR;

    private McdgBlocks() {}

    public static void register() {
        DISC_WORKBENCH = Registry.register(
                Registries.BLOCK,
                new Identifier(McdgMod.MOD_ID, "disc_workbench"),
                new DiscWorkbenchBlock(AbstractBlock.Settings.copy(Blocks.SMITHING_TABLE))
        );
        Registry.register(
                Registries.ITEM,
                new Identifier(McdgMod.MOD_ID, "disc_workbench"),
                new DiscWorkbenchItem(DISC_WORKBENCH, new Item.Settings())
        );
    }
}
