package com.mcdg.game;

import java.util.List;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A tiered disc golf disc with crafting-progression stats.
 * Extends the standard charged disc and applies tier-specific glide, stability,
 * throw speed, wind resistance, and durability.
 */
public class TieredDiscItem extends ChargedDiscItem {
    private final DiscTier tier;

    public TieredDiscItem(
            Settings settings,
            DiscTier tier,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean strictFlowDebug
    ) {
        super(settings, courseManager, roundStateManager, rulesetManager, strictFlowDebug);
        this.tier = tier;
    }

    public DiscTier tier() {
        return tier;
    }

    @Override
    protected DiscStats getDiscStats(ItemStack stack) {
        return tier.stats();
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, net.minecraft.client.item.TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Formatting color = Formatting.byName(tier.colorName());
        tooltip.add(Text.literal(tier.displayName() + " Disc").formatted(color));
        if (tier.durability() > 0) {
            int remaining = tier.durability() - stack.getDamage();
            tooltip.add(Text.literal("Durability: " + remaining + " / " + tier.durability()).formatted(Formatting.GRAY));
        }
    }
}
