package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class AutoTestThrowCommand {

    private AutoTestThrowCommand() {
    }

    static int executeAutoTestThrows(
            ServerCommandSource source,
            ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService,
            int count
    ) {
        return throwAutoTestService.start(source, count);
    }

    static int executeCancelThrowTest(ServerCommandSource source, ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService) {
        return throwAutoTestService.cancel(source);
    }

    static int executeQuickThrowTest(
            ServerCommandSource source,
            CourseGenerator generator,
            ActiveCourseManager courseManager,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            PracticeCourseStorage practiceCourseStorage,
            ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService,
            long seed,
            int throwCount
    ) {
        int created = CourseAdminCommands.executeCreateCourse(source, generator, courseManager, seed);
        if (created == 0) {
            return 0;
        }

        int started = RoundStartCommand.executeStartRound(
                source,
                courseManager,
                placementService,
                placementValidator,
                roundStateManager,
                roundPresentationService,
                true,
                practiceCourseStorage,
                false,
                true,
                null
        );
        if (started == 0) {
            return 0;
        }

        int testStarted = executeAutoTestThrows(source, throwAutoTestService, roundSessionStorage, playerRoundSessionStorage, buildCourseSessionManager, autoCourseService, throwCount);
        if (testStarted == 0) {
            return 0;
        }

        source.sendFeedback(() -> Text.literal(
                "Quick throw test running: seed=" + seed + ", throws=" + throwCount + "."
        ), true);
        return 1;
    }
}
