package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class McdgBlockEntities {
    public static BlockEntityType<DiscWorkbenchBlockEntity> DISC_WORKBENCH;

    private McdgBlockEntities() {}

    public static void register() {
        DISC_WORKBENCH = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(McdgMod.MOD_ID, "disc_workbench"),
                BlockEntityType.Builder.create(DiscWorkbenchBlockEntity::new, McdgBlocks.DISC_WORKBENCH).build()
        );
    }
}
