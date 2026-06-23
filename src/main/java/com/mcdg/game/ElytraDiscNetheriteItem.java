package com.mcdg.game;

/**
 * Netherite-upgraded throwable disc that launches the player along a calculated flight trajectory.
 * Identical to ElytraDiscItem but with player control enabled during flight.
 */
public final class ElytraDiscNetheriteItem extends ElytraDiscItem {

    public ElytraDiscNetheriteItem() {
        super(new net.minecraft.item.Item.Settings(), true);
    }
}
