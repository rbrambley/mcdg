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
    public static Item SCORECARD = Items.AIR;
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

        SCORECARD = Registry.register(
            Registries.ITEM,
            new Identifier(McdgMod.MOD_ID, "scorecard"),
            new ScorecardItem(new Item.Settings().maxCount(1))
        );
    }
}
