package com.mcdg.command;

import com.mcdg.game.RoundWindService;
import com.mcdg.game.WindManager;
import com.mcdg.game.WindMode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class WindCommands {
    private WindCommands() {
    }

    public static int executeWindSet(ServerCommandSource source, double speed, int direction) {
        ServerWorld world = source.getWorld();
        WindManager.setManualWind(world, speed, direction);
        source.sendFeedback(() -> Text.literal("Wind set to " + speed + " speed, " + direction + " degrees").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindClear(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        WindManager.setWindMode(world, WindMode.CALM);
        source.sendFeedback(() -> Text.literal("Wind cleared (calm conditions)").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindCalm(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        WindManager.setWindMode(world, WindMode.CALM);
        source.sendFeedback(() -> Text.literal("Wind set to calm (0 speed)").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindMode(ServerCommandSource source, WindMode mode) {
        ServerWorld world = source.getWorld();
        WindManager.setWindMode(world, mode);
        source.sendFeedback(() -> Text.literal("Wind mode set to " + mode).formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindShow(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        com.mcdg.game.WindState wind = WindManager.getWindState(world);
        String compassDirection = getCompassDirection(wind.directionDegrees());
        
        net.minecraft.text.MutableText feedback = Text.empty()
            .append(Text.literal("Current Wind: ").formatted(Formatting.AQUA))
            .append(Text.literal(wind.speed() + " speed, " + wind.directionDegrees() + "° (" + compassDirection + ")").formatted(Formatting.WHITE))
            .append(Text.literal(", mode: ").formatted(Formatting.GRAY))
            .append(Text.literal(wind.mode().toString()).formatted(Formatting.YELLOW));
        
        if (wind.isGusting()) {
            feedback.append(Text.literal(" [GUSTING]").formatted(Formatting.RED));
        }
        
        source.sendFeedback(() -> feedback, false);
        
        // Add usage hint
        source.sendFeedback(() -> Text.literal("Use /mcdg wind set <speed> <direction> to set manual wind").formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> Text.literal("Use /mcdg wind calm|random|gust for quick wind changes").formatted(Formatting.DARK_GRAY), false);
        source.sendFeedback(() -> Text.literal("Use /mcdg wind mode <calm|natural|fixed> to change wind mode").formatted(Formatting.DARK_GRAY), false);
        
        return 1;
    }

    public static int executeWindRandom(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        WindManager.setWindMode(world, WindMode.NATURAL);
        source.sendFeedback(() -> Text.literal("Wind set to random natural mode").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindGust(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        WindManager.triggerGust(world);
        source.sendFeedback(() -> Text.literal("Wind gust triggered").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeWindAuto(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        RoundWindService.reEnableAutomation(world, 0L);
        source.sendFeedback(() -> Text.literal(
                "Round wind automation re-enabled | mode=" + RoundWindService.getRoundWindMode()
        ).formatted(Formatting.GREEN), true);
        return 1;
    }

    private static String getCompassDirection(float degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(degrees / 45.0f) % 8;
        return directions[index];
    }
}
