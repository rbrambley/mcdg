package com.mcdg.game;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * UI helper methods for buildcourse session management.
 */
public final class BuildCourseUIHelper {

    private BuildCourseUIHelper() {}

    public static Text button(String label, String command, Formatting color, boolean runNow) {
        ClickEvent.Action action = runNow ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        return Text.literal("[" + label + "]").styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal((runNow ? "Run: " : "Fill chat: ") + command)))
        );
    }
}