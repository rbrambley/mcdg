package com.mcdg.command;

import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RulesetCommands {
    private RulesetCommands() {
    }

    public static int executeShowRuleset(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        TournamentRulesetManager.Ruleset active = rulesetManager.getActiveRuleset();
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();

        source.sendFeedback(
                () -> Text.literal("Current ruleset: " + active.name().toLowerCase()
                        + " | strict surface preset: " + preset.name().toLowerCase()),
                false
        );
        return completePlayerFacingLegacyCommand(source, "rules");
    }

    public static int executeSetRuleset(ServerCommandSource source, TournamentRulesetManager rulesetManager, TournamentRulesetManager.Ruleset ruleset) {
        rulesetManager.setActiveRuleset(ruleset);
        source.sendFeedback(() -> Text.literal("Ruleset set to " + ruleset.name().toLowerCase() + "."), true);
        return 1;
    }

    public static int executeShowStrictSurfacePreset(ServerCommandSource source, TournamentRulesetManager rulesetManager) {
        TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
        source.sendFeedback(
                () -> Text.literal("Strict surface preset: " + preset.name().toLowerCase()
                        + " (" + describeStrictSurfacePreset(preset) + ")"),
                false
        );
        source.sendFeedback(() -> Text.literal("Options: fast (forgiving), balanced (default), tournament (hardest)."), false);
        return completePlayerFacingLegacyCommand(source, "rules");
    }

    public static int executeSetStrictSurfacePreset(ServerCommandSource source, TournamentRulesetManager rulesetManager, String presetName) {
        TournamentRulesetManager.StrictSurfacePreset preset;
        try {
            preset = TournamentRulesetManager.StrictSurfacePreset.valueOf(presetName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendError(Text.literal("Unknown strict surface preset: " + presetName + ". Use fast, balanced, or tournament."));
            return 0;
        }

        rulesetManager.setStrictSurfacePreset(preset);
        source.sendFeedback(() -> Text.literal("Strict surface preset set to " + preset.name().toLowerCase() + "."), true);
        return 1;
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

    private static int completePlayerFacingLegacyCommand(ServerCommandSource source, String submenu) {
        source.sendFeedback(() -> Text.literal("Tip: use /mcdg menu for clickable controls. Opening " + submenu + " menu...")
                .formatted(Formatting.DARK_GRAY), false);
        source.getServer().getCommandManager().executeWithPrefix(source, "/mcdg menu " + submenu);
        return 1;
    }
}
