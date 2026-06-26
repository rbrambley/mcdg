package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class McdgItems {
    public static Item TRAINING_DISC = Items.AIR;
    public static Item WOODEN_DISC = Items.AIR;
    public static Item STONE_DISC = Items.AIR;
    public static Item IRON_DISC = Items.AIR;
    public static Item GOLD_DISC = Items.AIR;
    public static Item DIAMOND_DISC = Items.AIR;
    public static Item NETHERITE_DISC = Items.AIR;
    public static Item SCORECARD = Items.AIR;
    public static Item DISC_ENCHANTED_BOOK = Items.AIR;
    public static Item DISC_BAG = Items.AIR;
    public static Item DISC_GLOVE = Items.AIR;
    public static Item DISC_TOWEL = Items.AIR;
    public static Item RANGE_FINDER = Items.AIR;
    private static boolean strictFlowDebug;

    private McdgItems() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean strictFlowDebugEnabled
    ) {
        strictFlowDebug = strictFlowDebugEnabled;
        TRAINING_DISC = registerTieredDisc("training_disc", DiscTier.TRAINING, courseManager, roundStateManager, rulesetManager);

        WOODEN_DISC = registerTieredDisc("wooden_disc", DiscTier.WOODEN, courseManager, roundStateManager, rulesetManager);
        STONE_DISC = registerTieredDisc("stone_disc", DiscTier.STONE, courseManager, roundStateManager, rulesetManager);
        IRON_DISC = registerTieredDisc("iron_disc", DiscTier.IRON, courseManager, roundStateManager, rulesetManager);
        GOLD_DISC = registerTieredDisc("gold_disc", DiscTier.GOLD, courseManager, roundStateManager, rulesetManager);
        DIAMOND_DISC = registerTieredDisc("diamond_disc", DiscTier.DIAMOND, courseManager, roundStateManager, rulesetManager);
        NETHERITE_DISC = registerTieredDisc("netherite_disc", DiscTier.NETHERITE, courseManager, roundStateManager, rulesetManager);

        SCORECARD = Registry.register(
            Registries.ITEM,
            new Identifier(McdgMod.MOD_ID, "scorecard"),
            new ScorecardItem(new Item.Settings().maxCount(1))
        );

        DISC_ENCHANTED_BOOK = Registry.register(
            Registries.ITEM,
            new Identifier(McdgMod.MOD_ID, "disc_enchanted_book"),
            new DiscEnchantedBook(new Item.Settings().maxCount(1))
        );

        DISC_BAG = Registry.register(
            Registries.ITEM,
            new Identifier(McdgMod.MOD_ID, "disc_bag"),
            new DiscBagItem(new Item.Settings().maxCount(1))
        );

        DISC_GLOVE = registerAccessory("disc_glove", AccessoryEffect.GRIP_STABILITY);
        DISC_TOWEL = registerAccessory("disc_towel", AccessoryEffect.DURABILITY_PRESERVE);
        RANGE_FINDER = registerAccessory("range_finder", AccessoryEffect.RANGE_FINDER);
    }

    private static Item registerAccessory(String id, AccessoryEffect effect) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(McdgMod.MOD_ID, id),
                new AccessoryItem(new Item.Settings().maxCount(1), effect)
        );
    }

    private static Item registerTieredDisc(
            String id,
            DiscTier tier,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager
    ) {
        Item.Settings settings = new Item.Settings().maxCount(1).maxDamage(tier.durability());
        return Registry.register(
                Registries.ITEM,
                new Identifier(McdgMod.MOD_ID, id),
                new TieredDiscItem(settings, tier, courseManager, roundStateManager, rulesetManager, strictFlowDebug)
        );
    }

    /**
     * Returns true if the stack is any MCDG disc (training or tiered).
     */
    public static boolean isDisc(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.isOf(TRAINING_DISC) ||
               stack.isOf(WOODEN_DISC) ||
               stack.isOf(STONE_DISC) ||
               stack.isOf(IRON_DISC) ||
               stack.isOf(GOLD_DISC) ||
               stack.isOf(DIAMOND_DISC) ||
               stack.isOf(NETHERITE_DISC);
    }
}
