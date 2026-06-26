package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class McdgScreenHandlers {
    public static ScreenHandlerType<DiscWorkbenchScreenHandler> DISC_WORKBENCH;
    public static ScreenHandlerType<DiscBagScreenHandler> DISC_BAG;

    private McdgScreenHandlers() {}

    public static void register() {
        DISC_WORKBENCH = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(McdgMod.MOD_ID, "disc_workbench"),
                new ScreenHandlerType<>((syncId, playerInventory) -> new DiscWorkbenchScreenHandler(syncId, playerInventory), FeatureSet.empty())
        );

        DISC_BAG = Registry.register(
                Registries.SCREEN_HANDLER,
                new Identifier(McdgMod.MOD_ID, "disc_bag"),
                new ScreenHandlerType<>((syncId, playerInventory) -> new DiscBagScreenHandler(syncId, playerInventory, new SimpleInventory(12), null), FeatureSet.empty())
        );
    }
}
