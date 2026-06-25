package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.item.Item;
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
        TRAINING_DISC = Registry.register(
                Registries.ITEM,
                new Identifier(McdgMod.MOD_ID, "training_disc"),
                new ChargedDiscItem(new Item.Settings().maxCount(1), courseManager, roundStateManager, rulesetManager, strictFlowDebug)
        );

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
}
