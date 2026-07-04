package com.mcdg.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class HelpCommand {

    private HelpCommand() {
        // utility class
    }

    static int executeHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("MCDG quick help:"), false);
        source.sendFeedback(() -> Text.literal("- New course: /mcdg createcourse <seed> -> /mcdg startround."), false);
        source.sendFeedback(() -> Text.literal("- Generation model is unified across modes: land-first routing with water-carry cap <= 91 blocks (~300 ft)."), false);
        source.sendFeedback(() -> Text.literal("- Saved course: /mcdg listcourses -> /mcdg playcourse <index>."), false);
        source.sendFeedback(() -> Text.literal("- In-round basics: /mcdg joinround, /mcdg endround, /mcdg cleanupcourse."), false);
        if (CommandPermission.canUseAdvancedCommands(source)) {
            source.sendFeedback(() -> Text.literal("- Advanced commands are visible (MCDG_SHOW_ADVANCED_COMMANDS=true)."), false);
        } else {
            source.sendFeedback(() -> Text.literal("- Advanced commands are hidden by default; set MCDG_SHOW_ADVANCED_COMMANDS=true to expose them."), false);
        }
        return 1;
    }
}
