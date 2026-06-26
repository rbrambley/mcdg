package com.mcdg.game;

import java.util.List;
import java.util.Map;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.client.item.TooltipType;
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
    private static final int FLIGHT_SPEED_MIN = 1;
    private static final int FLIGHT_SPEED_MAX = 14;
    private static final int FLIGHT_GLIDE_MIN = 1;
    private static final int FLIGHT_GLIDE_MAX = 7;
    private static final int FLIGHT_TURN_MIN = -5;
    private static final int FLIGHT_TURN_MAX = 1;
    private static final int FLIGHT_FADE_MIN = 0;
    private static final int FLIGHT_FADE_MAX = 5;

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
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Formatting color = Formatting.byName(tier.colorName());
        if (color == null) {
            color = Formatting.WHITE;
        }

        tooltip.add(Text.literal(tier.displayName() + " Disc").formatted(color));

        int[] flight = modifiedFlightNumbers(stack);
        tooltip.add(Text.translatable("tooltip.mcdg.flight",
                formatFlightNumber(flight[0]),
                formatFlightNumber(flight[1]),
                formatFlightNumber(flight[2]),
                formatFlightNumber(flight[3]))
                .formatted(Formatting.GRAY));

        tooltip.add(Text.translatable("tooltip.mcdg.durability", stack.getMaxDamage())
                .formatted(Formatting.GRAY));

        int windPercent = (int) Math.round(tier.stats().windResistance() * 100.0);
        tooltip.add(Text.translatable("tooltip.mcdg.wind_resistance", windPercent)
                .formatted(Formatting.GRAY));

        addEnchantmentTooltip(stack, tooltip);

        if (tier == DiscTier.TRAINING) {
            tooltip.add(Text.translatable("tooltip.mcdg.training.usage")
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    private int[] modifiedFlightNumbers(ItemStack stack) {
        Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(stack);
        int speed = tier.flightSpeed() + enchantments.getOrDefault(DiscEnchantment.DISTANCE, 0);
        int glide = tier.flightGlide() + enchantments.getOrDefault(DiscEnchantment.GLIDE, 0);
        int fade = tier.flightFade() - enchantments.getOrDefault(DiscEnchantment.FADE_CONTROL, 0);
        int turn = tier.flightTurn() + enchantments.getOrDefault(DiscEnchantment.FADE_CONTROL, 0);
        return new int[] {
                Math.max(FLIGHT_SPEED_MIN, Math.min(FLIGHT_SPEED_MAX, speed)),
                Math.max(FLIGHT_GLIDE_MIN, Math.min(FLIGHT_GLIDE_MAX, glide)),
                Math.max(FLIGHT_TURN_MIN, Math.min(FLIGHT_TURN_MAX, turn)),
                Math.max(FLIGHT_FADE_MIN, Math.min(FLIGHT_FADE_MAX, fade))
        };
    }

    private static String formatFlightNumber(int value) {
        return String.valueOf(value);
    }
}
