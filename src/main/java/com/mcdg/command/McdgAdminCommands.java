package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.arguments.BoolArgumentType;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import java.util.UUID;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import com.mcdg.game.WindMode;
import com.mojang.brigadier.arguments.DoubleArgumentType;

public final class McdgAdminCommands {

    private McdgAdminCommands() {
    }

    public static void register(
            CourseGenerator generator,
            ActiveCourseManager courseManager,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            PlacementAutoTestService autoTestService,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation,
            TournamentRulesetManager rulesetManager,
            PracticeCourseStorage practiceCourseStorage,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mcdg")
                        .executes(context -> MenuCommands.sendMenuScreen(context.getSource(), courseManager, playerRoundSessionStorage, rulesetManager, practiceCourseStorage))
                        .then(literal("help").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> HelpCommand.executeHelp(context.getSource())))
                        .then(literal("gotolie").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> TeleportCommands.executeGotoLie(context.getSource(), roundStateManager)))
                        .then(literal("menu")
                                .then(literal("rules")
                                        .executes(context -> MenuCommands.executeMenuRules(context.getSource(), rulesetManager)))
                                .then(literal("challenge")
                                        .executes(context -> MenuCommands.executeMenuChallenge(context.getSource()))))
                        .then(literal("startchallenge")
                                .then(argument("courseId", StringArgumentType.string())
                                        .executes(context -> ChallengeCourseCommands.executeStartChallenge(
                                                context.getSource(),
                                                courseManager,
                                                placementService,
                                                placementValidator,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                StringArgumentType.getString(context, "courseId")
                                        ))))
                        .then(literal("gotochallenge")
                                .then(argument("courseId", StringArgumentType.string())
                                        .executes(context -> ChallengeCourseCommands.executeGotoChallenge(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "courseId")
                                        ))))
                        .then(literal("cleanupchallenge").requires(CommandPermission::canUseAdminCommands)
                                .then(argument("courseId", StringArgumentType.string())
                                        .executes(context -> CourseCleanupCommand.executeCleanupChallenge(
                                                context.getSource(),
                                                placementService,
                                                roundStateManager,
                                                courseManager,
                                                StringArgumentType.getString(context, "courseId")
                                        ))))
                        .then(literal("removechallenge").requires(CommandPermission::canUseAdminCommands)
                                .then(argument("courseId", StringArgumentType.string())
                                        .executes(context -> CourseCleanupCommand.executeRemoveChallenge(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "courseId")
                                        ))))
                        .then(literal("removechallengeboth").requires(CommandPermission::canUseAdminCommands)
                                .then(argument("courseId", StringArgumentType.string())
                                        .executes(context -> CourseCleanupCommand.executeRemoveChallengeBoth(
                                                context.getSource(),
                                                placementService,
                                                roundStateManager,
                                                courseManager,
                                                StringArgumentType.getString(context, "courseId")
                                        ))))
                        .then(literal("resort")
                                .executes(context -> TeleportCommands.executeResortTeleport(context.getSource())))
                        .then(literal("createcourse").requires(CommandPermission::canUseAdminCommands)
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(context -> CourseAdminCommands.executeCreateCourse(
                                                context.getSource(),
                                                generator,
                                                courseManager,
                                                LongArgumentType.getLong(context, "seed")
                                        ))))
                        .then(literal("startround").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> RoundStartCommand.executeStartRound(
                                        context.getSource(),
                                        courseManager,
                                        placementService,
                                        placementValidator,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        practiceCourseStorage,
                                        true,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> RoundStartCommand.executeStartRound(
                                                context.getSource(),
                                                courseManager,
                                                placementService,
                                                placementValidator,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                practiceCourseStorage,
                                                true,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("resumecourse").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> RoundLifecycleCommands.executeResumeCourse(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> RoundLifecycleCommands.executeResumeCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("listcourses").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> CourseManagementCommand.executeListCourses(
                                        context.getSource(),
                                        practiceCourseStorage
                                )))
                        .then(literal("usecourse").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> CourseAdminCommands.executeUseCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("playcourse").requires(CommandPermission::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> CourseManagementCommand.executePlayCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index"),
                                                null
                                        ))
                                        .then(argument("players", EntityArgumentType.players())
                                                .executes(context -> CourseManagementCommand.executePlayCourse(
                                                        context.getSource(),
                                                        courseManager,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        skipRoundPresentation,
                                                        practiceCourseStorage,
                                                        IntegerArgumentType.getInteger(context, "index"),
                                                        EntityArgumentType.getPlayers(context, "players")
                                                )))))
                        .then(literal("prunecourses").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> CourseManagementCommand.executePruneCourses(
                                        context.getSource(),
                                        practiceCourseStorage,
                                        6
                                ))
                                .then(argument("keep", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> CourseManagementCommand.executePruneCourses(
                                                context.getSource(),
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "keep")
                                        ))))
                        .then(literal("resetcourse").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> CourseCleanupCommand.executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("cleanupcourse").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> CourseCleanupCommand.executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("gotocourse").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> TeleportCommands.executeGotoCourse(context.getSource(), courseManager)))
                        .then(literal("endround").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> RoundEndCommand.executeEndRound(context.getSource(), courseManager, roundStateManager)))
                        .then(literal("joinround").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> RoundJoinCommand.executeJoinRound(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> RoundJoinCommand.executeJoinRound(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("roundstatus").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> RoundStatusCommand.executeRoundStatus(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager
                                )))
                        .then(literal("ruleset").requires(CommandPermission::canUseAdminCommands)
                                .executes(context -> RulesetCommands.executeShowRuleset(context, rulesetManager))
                                .then(literal("casual")
                                        .executes(context -> RulesetCommands.executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.CASUAL)))
                                .then(literal("strict")
                                        .executes(context -> RulesetCommands.executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.STRICT)))
                                .then(literal("surface")
                                        .requires(CommandPermission::canUseAdvancedCommands)
                                        .executes(context -> RulesetCommands.executeShowStrictSurfacePreset(context, rulesetManager))
                                        .then(argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                        builder.suggest("fast");
                                                        builder.suggest("balanced");
                                                        builder.suggest("tournament");
                                                        return builder.buildFuture();
                                                })
                                                .executes(context -> RulesetCommands.executeSetStrictSurfacePreset(context, rulesetManager, StringArgumentType.getString(context, "preset"))))))
                        .then(literal("debugperms").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> CommandPermission.sendDebugPermissions(context.getSource())))
                        .then(literal("debug").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .then(literal("obclassifier")
                                        .executes(context -> DebugCommands.executeDebugObClassifier(context.getSource()))
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> DebugCommands.executeDebugObClassifierSet(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                                .then(literal("hazard")
                                        .executes(context -> DebugCommands.executeDebugHazardInfo(context.getSource())))
                                .then(literal("hazardlist")
                                        .executes(context -> DebugCommands.executeDebugHazardList(context.getSource())))
                                .then(literal("coursehazards")
                                        .executes(context -> DebugCommands.executeDebugCourseHazards(
                                                context.getSource(),
                                                courseManager,
                                                rulesetManager
                                        )))
                                .then(literal("holehazards")
                                        .then(argument("hole", IntegerArgumentType.integer(1))
                                                .executes(context -> DebugCommands.executeDebugHoleHazards(
                                                        context.getSource(),
                                                        courseManager,
                                                        rulesetManager,
                                                        IntegerArgumentType.getInteger(context, "hole")
                                                ))))
                                .then(literal("lostcourses")
                                        .executes(context -> DebugCommands.executeListLostCourses(context.getSource())))
                                .then(literal("discovercourse")
                                        .then(argument("courseId", StringArgumentType.string())
                                                .executes(context -> DebugCommands.executeDiscoverCourse(context.getSource(), StringArgumentType.getString(context, "courseId")))))
                                .then(literal("clearlostcourses")
                                        .executes(context -> DebugCommands.executeClearLostCourses(context.getSource())))
                                .then(literal("placetestlostcourse")
                                        .executes(context -> DebugCommands.executePlaceTestLostCourse(context.getSource())))
                                .then(literal("repairchallengenames")
                                        .executes(context -> DebugCommands.executeRepairChallengeNames(context.getSource()))))
                        .then(literal("validateplacement").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> PlacementValidationCommand.executeValidatePlacement(
                                        context.getSource(),
                                        courseManager,
                                        placementValidator
                                )))
                        .then(literal("autotestplacement").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .executes(context -> AutoTestPlacementCommand.executeAutoTestPlacement(
                                                        context.getSource(),
                                                        autoTestService,
                                                        IntegerArgumentType.getInteger(context, "runs"),
                                                        IntegerArgumentType.getInteger(context, "holes")
                                                )))))
                        .then(literal("autotestplacementseed").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .then(argument("seed", LongArgumentType.longArg())
                                                        .executes(context -> AutoTestPlacementCommand.executeAutoTestPlacementSeeded(
                                                                context.getSource(),
                                                                autoTestService,
                                                                IntegerArgumentType.getInteger(context, "runs"),
                                                                IntegerArgumentType.getInteger(context, "holes"),
                                                                LongArgumentType.getLong(context, "seed")
                                                        ))))))
                        .then(literal("autotestshadow").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> AutoTestPlacementCommand.executeAutoTestShadowStatus(context.getSource(), autoTestService))
                                .then(literal("status")
                                        .executes(context -> AutoTestPlacementCommand.executeAutoTestShadowStatus(context.getSource(), autoTestService)))
                                .then(literal("on")
                                        .executes(context -> AutoTestPlacementCommand.executeAutoTestShadowSet(context.getSource(), autoTestService, true)))
                                .then(literal("off")
                                        .executes(context -> AutoTestPlacementCommand.executeAutoTestShadowSet(context.getSource(), autoTestService, false))))
                        .then(literal("cancelautotest").requires(CommandPermission::canUseAdminCommands)
                                .requires(CommandPermission::canUseAdvancedCommands)
                                .executes(context -> AutoTestPlacementCommand.executeCancelAutoTest(context.getSource(), autoTestService)))
                        .then(buildCourseSessionManager.registerNode().requires(CommandPermission::canUseAdminCommands))
                        .then(literal("bot").requires(CommandPermission::canUseAdminCommands)
                                .then(literal("add")
                                        .then(argument("name", StringArgumentType.string())
                                                .then(argument("skill", StringArgumentType.string())
                                                        .executes(context -> BotCommands.executeBotAdd(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "name"),
                                                                StringArgumentType.getString(context, "skill")
                                                        )))))
                                .then(literal("remove")
                                        .then(argument("uuid", StringArgumentType.string())
                                                .executes(context -> BotCommands.executeBotRemove(
                                                        context.getSource(),
                                                        roundStateManager,
                                                        StringArgumentType.getString(context, "uuid")
                                                ))))
                                .then(literal("list")
                                        .executes(context -> BotCommands.executeBotList(context.getSource())))
                                .then(literal("clear")
                                        .executes(context -> BotCommands.executeBotClear(
                                                context.getSource(),
                                                roundStateManager
                                        )))
                                .then(literal("joinround")
                                        .executes(context -> BotCommands.executeBotJoinRound(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager
                                        )))
                                .then(literal("leaveround")
                                        .executes(context -> BotCommands.executeBotLeaveRound(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager
                                        ))))
                        .then(literal("wind").requires(CommandPermission::canUseAdminCommands)
                                .then(literal("set")
                                        .then(argument("speed", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .then(argument("direction", IntegerArgumentType.integer(0, 359))
                                                        .executes(context -> WindCommands.executeWindSet(
                                                                context.getSource(),
                                                                DoubleArgumentType.getDouble(context, "speed"),
                                                                IntegerArgumentType.getInteger(context, "direction")
                                                        )))))
                                .then(literal("clear")
                                        .executes(context -> WindCommands.executeWindClear(context.getSource())))
                                .then(literal("calm")
                                        .executes(context -> WindCommands.executeWindCalm(context.getSource())))
                                .then(literal("mode")
                                        .then(literal("calm")
                                                .executes(context -> WindCommands.executeWindMode(context.getSource(), WindMode.CALM)))
                                        .then(literal("natural")
                                                .executes(context -> WindCommands.executeWindMode(context.getSource(), WindMode.NATURAL)))
                                        .then(literal("fixed")
                                                .executes(context -> WindCommands.executeWindMode(context.getSource(), WindMode.FIXED))))
                                .then(literal("show")
                                        .executes(context -> WindCommands.executeWindShow(context.getSource())))
                                .then(literal("random")
                                        .executes(context -> WindCommands.executeWindRandom(context.getSource())))
                                .then(literal("gust")
                                        .executes(context -> WindCommands.executeWindGust(context.getSource())))
                                .then(literal("auto")
                                        .executes(context -> WindCommands.executeWindAuto(context.getSource()))))
                        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mcdg")
                    .then(literal("savesession").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> SessionCommands.executeSaveSession(
                                    context.getSource(), courseManager, roundStateManager, playerRoundSessionStorage, null))
                            .then(argument("players", EntityArgumentType.players())
                                    .executes(context -> SessionCommands.executeSaveSession(
                                            context.getSource(), courseManager, roundStateManager, playerRoundSessionStorage,
                                            EntityArgumentType.getPlayers(context, "players"))))));

            dispatcher.register(literal("mcdg")
                    .then(literal("resumesession").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> SessionCommands.executeResumeSession(
                                    context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                    playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.PREFER_MANUAL, null))
                            .then(argument("players", EntityArgumentType.players())
                                    .executes(context -> SessionCommands.executeResumeSession(
                                            context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                            playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.PREFER_MANUAL,
                                            EntityArgumentType.getPlayers(context, "players"))))
                            .then(literal("manual")
                                    .executes(context -> SessionCommands.executeResumeSession(
                                            context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                            playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.MANUAL_ONLY, null))
                                    .then(argument("players", EntityArgumentType.players())
                                            .executes(context -> SessionCommands.executeResumeSession(
                                                    context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                                    playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.MANUAL_ONLY,
                                                    EntityArgumentType.getPlayers(context, "players")))))
                            .then(literal("auto")
                                    .executes(context -> SessionCommands.executeResumeSession(
                                            context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                            playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.AUTO_ONLY, null))
                                    .then(argument("players", EntityArgumentType.players())
                                            .executes(context -> SessionCommands.executeResumeSession(
                                                    context.getSource(), courseManager, roundStateManager, practiceCourseStorage,
                                                    playerRoundSessionStorage, SessionCommands.ResumeSourceSelection.AUTO_ONLY,
                                                    EntityArgumentType.getPlayers(context, "players")))))));

            dispatcher.register(literal("mcdg")
                    .then(literal("roundsession").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> SessionCommands.executeRoundSessionStatus(
                                    context.getSource(), roundSessionStorage))
                            .then(literal("status")
                                    .executes(context -> SessionCommands.executeRoundSessionStatus(
                                            context.getSource(), roundSessionStorage)))
                            .then(literal("clear")
                                    .executes(context -> SessionCommands.executeRoundSessionClear(
                                            context.getSource(), roundSessionStorage, courseManager, roundStateManager, practiceCourseStorage)))));

            dispatcher.register(literal("mcdg")
                    .then(literal("gotocoursebyindex").requires(CommandPermission::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseCleanupCommand.executeGotoCourseByIndex(
                                            context.getSource(), practiceCourseStorage,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            dispatcher.register(literal("mcdg")
                    .then(literal("cleanupcoursebyindex").requires(CommandPermission::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseCleanupCommand.executeCleanupCourseByIndex(
                                            context.getSource(), practiceCourseStorage, placementService,
                                            roundStateManager, courseManager,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            // Waypoint commands removed (player waypoints replaced by Xaero's Minimap)

            dispatcher.register(literal("mcdg")
                    .then(literal("buildresort").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeBuildResort(
                                    context.getSource(), generator, autoCourseService, practiceCourseStorage, null, null))
                            .then(argument("x", IntegerArgumentType.integer())
                                    .then(argument("z", IntegerArgumentType.integer())
                                            .executes(context -> ResortAdminCommands.executeBuildResort(
                                                    context.getSource(), generator, autoCourseService, practiceCourseStorage,
                                                    IntegerArgumentType.getInteger(context, "x"),
                                                    IntegerArgumentType.getInteger(context, "z")))))
                            .then(literal("overwrite").requires(CommandPermission::canUseAdminCommands)
                                    .executes(context -> ResortAdminCommands.executeBuildResortOverwrite(
                                            context.getSource(), generator, autoCourseService, practiceCourseStorage)))
                            .then(literal("cancel").requires(CommandPermission::canUseAdminCommands)
                                    .executes(context -> ResortAdminCommands.executeBuildResortCancel(
                                            context.getSource())))));

            dispatcher.register(literal("mcdg")
                    .then(literal("resetresort").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeResetResort(
                                    context.getSource()))));

            dispatcher.register(literal("mcdg")
                    .then(literal("removesurroundcourses").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeRemoveSurroundCourses(
                                    context.getSource(), placementService, practiceCourseStorage))));

            // autocourse
            dispatcher.register(literal("mcdg")
                    .then(literal("autocourse").requires(CommandPermission::canUseAdminCommands)
                            .executes(context -> autoCourseService.executeAutoCourseNoName(context.getSource()))
                            .then(argument("name", StringArgumentType.greedyString())
                                    .executes(context -> autoCourseService.executeAutoCourseNamed(
                                            context.getSource(), StringArgumentType.getString(context, "name"))))
                            .then(literal("cancel")
                                    .executes(context -> autoCourseService.executeCancel(context.getSource())))));

            // removecourse
            dispatcher.register(literal("mcdg")
                    .then(literal("removecourse").requires(CommandPermission::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseAdminCommands.executeRemoveCourse(
                                            context.getSource(), courseManager, roundStateManager,
                                            practiceCourseStorage, playerRoundSessionStorage,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            // removecourseboth
            dispatcher.register(literal("mcdg")
                    .then(literal("removecourseboth").requires(CommandPermission::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseCleanupCommand.executeRemoveCourseBoth(
                                            context.getSource(), courseManager, roundStateManager,
                                            practiceCourseStorage, playerRoundSessionStorage, placementService,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            dispatcher.register(literal("mcdg")
                    .then(DiscEnchantmentCommands.build()));
        });
    }

}

