package com.mcdg.game;

import java.util.List;
import net.minecraft.client.item.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Base item for disc golf accessories that grant passive effects when carried.
 */
public class AccessoryItem extends Item {
    private final AccessoryEffect effect;

    public AccessoryItem(Settings settings, AccessoryEffect effect) {
        super(settings.maxCount(1));
        this.effect = effect;
    }

    public AccessoryEffect effect() {
        return effect;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("tooltip.mcdg.accessory.effect", effect.displayName())
                .formatted(effect.color()));
        tooltip.add(Text.translatable(effect.tooltipKey()).formatted(Formatting.DARK_GRAY));
    }
}