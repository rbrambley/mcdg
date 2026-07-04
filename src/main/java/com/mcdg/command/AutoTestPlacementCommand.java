package com.mcdg.command;

import com.mcdg.world.PlacementAutoTestService;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class AutoTestPlacementCommand {

    private AutoTestPlacementCommand() {
    }

    static int executeAutoTestPlacement(
            ServerCommandSource source,
            PlacementAutoTestService autoTestService,
            int runs,
            int holes
    ) {
        return autoTestService.start(source, runs, holes);
    }

    static int executeAutoTestPlacementSeeded(
            ServerCommandSource source,
            PlacementAutoTestService autoTestService,
            int runs,
            int holes,
            long seed
    ) {
        source.sendFeedback(() -> Text.literal(
                "Starting seeded autotest with baseSeed=" + seed + "."
        ), false);
        return autoTestService.start(source, runs, holes, seed);
    }

    static int executeAutoTestShadowStatus(
            ServerCommandSource source,
            PlacementAutoTestService autoTestService
    ) {
        boolean enabled = autoTestService.isShadowSurfaceRuleEnabledNow();
        boolean override = autoTestService.isShadowSurfaceRuleOverrideSet();
        String mode = override ? "manual override" : "environment/default";
        source.sendFeedback(() -> Text.literal(
                "Autotest shadow mode is " + (enabled ? "ON" : "OFF") + " (" + mode + ")."
        ), false);
        return 1;
    }

    static int executeAutoTestShadowSet(
            ServerCommandSource source,
            PlacementAutoTestService autoTestService,
            boolean enabled
    ) {
        autoTestService.setShadowSurfaceRuleOverride(enabled);
        source.sendFeedback(() -> Text.literal(
                "Autotest shadow mode override set to " + (enabled ? "ON" : "OFF") + "."
        ), true);
        return 1;
    }

    static int executeCancelAutoTest(ServerCommandSource source, PlacementAutoTestService autoTestService) {
        return autoTestService.cancel(source);
    }
}
