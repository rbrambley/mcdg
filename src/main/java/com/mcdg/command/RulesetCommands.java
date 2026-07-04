package com.mcdg.command;

import com.mcdg.rules.TournamentRulesetManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class RulesetCommands {
    private RulesetCommands() {
    }

    private static String describeStrictSurfacePreset(TournamentRulesetManager.StrictSurfacePreset preset) {
        if (preset == null) {
            return "unknown";
        }

        return switch (preset) {
            case FAST -> "forgiving strict profile";
            case BALANCED -> "default strict profile";
            case TOURNAMENT -> "hardest strict profile";
        };
    }

    public static int executeShowRuleset(
            CommandContext<ServerCommandSource> context,
            TournamentRulesetManager rulesetManager
    ) {
        TournamentRulesetManager.Ruleset active = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        context.getSource().sendFeedback(
                () -> Text.literal("Current ruleset: " + active.name().toLowerCase()
                        + " | strict surface preset: " + preset.name().toLowerCase()),
                false
        );
        return 1;
    }

    public static int executeSetRuleset(
            CommandContext<ServerCommandSource> context,
            TournamentRulesetManager rulesetManager,
            TournamentRulesetManager.Ruleset ruleset
    ) {
        rulesetManager.setActiveRuleset(ruleset);
        context.getSource().sendFeedback(() -> Text.literal("Ruleset set to " + ruleset.name().toLowerCase() + "."), true);
        return 1;
    }

    public static int executeShowStrictSurfacePreset(
            CommandContext<ServerCommandSource> context,
            TournamentRulesetManager rulesetManager
    ) {
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        context.getSource().sendFeedback(
                () -> Text.literal("Strict surface preset: " + preset.name().toLowerCase()
                        + " (" + describeStrictSurfacePreset(preset) + ")"),
                false
        );
        context.getSource().sendFeedback(() -> Text.literal("Options: fast (forgiving), balanced (default), tournament (hardest)."), false);
        return 1;
    }

    public static int executeSetStrictSurfacePreset(
            CommandContext<ServerCommandSource> context,
            TournamentRulesetManager rulesetManager,
            String presetName
    ) {
        TournamentRulesetManager.StrictSurfacePreset preset;
        try {
            preset = TournamentRulesetManager.StrictSurfacePreset.valueOf(presetName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            context.getSource().sendError(Text.literal("Unknown strict surface preset: " + presetName + ". Use fast, balanced, or tournament."));
            return 0;
        }

        rulesetManager.setStrictSurfacePreset(preset);
        context.getSource().sendFeedback(() -> Text.literal("Strict surface preset set to " + preset.name().toLowerCase() + "."), true);
        return 1;
    }

}
