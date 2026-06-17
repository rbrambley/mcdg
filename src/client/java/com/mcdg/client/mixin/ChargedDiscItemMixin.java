package com.mcdg.client.mixin;

import com.mcdg.client.ThrowPreferenceManager;
import com.mcdg.game.ThrowStance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.UseAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 6: Client-side mixin to provide different UseAction values per throw stance.
 * This gives visual feedback to players by changing the arm pose during charging.
 */
@Mixin(value = com.mcdg.game.ChargedDiscItem.class)
public class ChargedDiscItemMixin {

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void mcdg_getStanceSpecificUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
        // Get the current throw stance from client-side preference manager
        ThrowStance stance = ThrowPreferenceManager.getSelectedStance();
        
        // Return different UseAction based on stance for visual feedback
        cir.setReturnValue(switch (stance) {
            case OVERHAND -> UseAction.SPEAR;
            case BACKHAND -> UseAction.CROSSBOW;
            case FOREHAND -> UseAction.BOW;
        });
    }
}
