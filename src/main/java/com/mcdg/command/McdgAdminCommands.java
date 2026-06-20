package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.McdgItems;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortWaypointManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.mcdg.game.OutOfBoundsClassifier;
import com.mojang.brigadier.arguments.BoolArgumentType;

public final class McdgAdminCommands {
        private static final String ADVANCED_COMMANDS_ENV = "MCDG_SHOW_ADVANCED_COMMANDS";
        private static final String ADVANCED_COMMANDS_PROPERTY = "mcdg.showAdvancedCommands";
        private static final boolean SHOW_ADVANCED_COMMANDS = readAdvancedCommandVisibility();

    private McdgAdminCommands() {
    }

        private static boolean readAdvancedCommandVisibility() {
                String value = System.getProperty(ADVANCED_COMMANDS_PROPERTY);
                if (value == null || value.isBlank()) {
                        value = System.getenv(ADVANCED_COMMANDS_ENV);
                }
                return value != null && value.equalsIgnoreCase("true");
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
            ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mcdg")
                        .executes(context -> MenuCommands.sendMenuScreen(context.getSource(), courseManager, playerRoundSessionStorage, rulesetManager, practiceCourseStorage))
                        .then(literal("help").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeHelp(context.getSource())))
                        .then(literal("gotolie").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeGotoLie(context.getSource(), roundStateManager)))
                        .then(literal("resort")
                                .executes(context -> executeResortTeleport(context.getSource())))
                        .then(literal("createcourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(context -> CourseAdminCommands.executeCreateCourse(
                                                context.getSource(),
                                                generator,
                                                courseManager,
                                                LongArgumentType.getLong(context, "seed")
                                        ))))
                        .then(literal("startround").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeStartRound(
                                        context.getSource(),
                                        courseManager,
                                        placementService,
                                        placementValidator,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        practiceCourseStorage,
                                        false,
                                        true,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> executeStartRound(
                                                context.getSource(),
                                                courseManager,
                                                placementService,
                                                placementValidator,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                practiceCourseStorage,
                                                false,
                                                true,
                                                EntityArgumentType.getPlayers(context, "players")
                                        )))
                                .then(literal("strict")
                                        .executes(context -> executeStartRound(
                                                context.getSource(),
                                                courseManager,
                                                placementService,
                                                placementValidator,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                practiceCourseStorage,
                                                false,
                                                true,
                                                null
                                        ))
                                        .then(argument("players", EntityArgumentType.players())
                                                .executes(context -> executeStartRound(
                                                        context.getSource(),
                                                        courseManager,
                                                        placementService,
                                                        placementValidator,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        skipRoundPresentation,
                                                        practiceCourseStorage,
                                                        false,
                                                        true,
                                                        EntityArgumentType.getPlayers(context, "players")
                                                )))))
                        .then(literal("practicecourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executePracticeCourseDeprecated(
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
                                        .executes(context -> executePracticeCourseDeprecated(
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
                                        )))
                                .then(literal("strict")
                                        .executes(context -> executePracticeCourseDeprecated(
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
                                                .executes(context -> executePracticeCourseDeprecated(
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
                                                )))))
                        .then(literal("resumecourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeResumeCourse(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> executeResumeCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("listcourses").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeListCourses(
                                        context.getSource(),
                                        practiceCourseStorage
                                )))
                        .then(literal("usecourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> executeUseCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("playcourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> executePlayCourse(
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
                                                .executes(context -> executePlayCourse(
                                                        context.getSource(),
                                                        courseManager,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        skipRoundPresentation,
                                                        practiceCourseStorage,
                                                        IntegerArgumentType.getInteger(context, "index"),
                                                        EntityArgumentType.getPlayers(context, "players")
                                                )))))
                        .then(literal("prunecourses").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executePruneCourses(
                                        context.getSource(),
                                        practiceCourseStorage,
                                        6
                                ))
                                .then(argument("keep", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> executePruneCourses(
                                                context.getSource(),
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "keep")
                                        ))))
                        .then(literal("resetcourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("cleanupcourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("gotocourse").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeGotoCourse(context.getSource(), courseManager)))
                        .then(literal("endround").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeEndRound(context.getSource(), courseManager, roundStateManager)))
                        .then(literal("joinround").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeJoinRound(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> executeJoinRound(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("roundstatus").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeRoundStatus(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager
                                )))
                        .then(literal("ruleset").requires(McdgAdminCommands::canUseAdminCommands)
                                .executes(context -> executeShowRuleset(context, rulesetManager))
                                .then(literal("casual")
                                        .executes(context -> executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.CASUAL)))
                                .then(literal("strict")
                                        .executes(context -> executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.STRICT)))
                                .then(literal("surface")
                                        .requires(McdgAdminCommands::canUseAdvancedCommands)
                                        .executes(context -> executeShowStrictSurfacePreset(context, rulesetManager))
                                        .then(argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                        builder.suggest("fast");
                                                        builder.suggest("balanced");
                                                        builder.suggest("tournament");
                                                        return builder.buildFuture();
                                                })
                                                .executes(context -> executeSetStrictSurfacePreset(context, rulesetManager, StringArgumentType.getString(context, "preset"))))))
                        .then(literal("debugperms").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeDebugPermissions(context.getSource())))
                        .then(literal("debug").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(literal("obclassifier")
                                        .executes(context -> executeDebugObClassifier(context.getSource()))
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> executeDebugObClassifierSet(context.getSource(), BoolArgumentType.getBool(context, "enabled"))))))
                        .then(literal("validateplacement").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeValidatePlacement(
                                        context.getSource(),
                                        courseManager,
                                        placementValidator
                                )))
                        // Commented out due to missing CoursePlacementService.LodgingBuildResult and tryBuildPermanentLodgingSite
                        //                         .then(literal("buildcamp").requires(McdgAdminCommands::canUseAdminCommands)
                        //                                 .requires(McdgAdminCommands::canUseAdvancedCommands)
                        //                                 .executes(context -> executeBuildCamp(
                        //                                         context.getSource(),
                        //                                         placementService
                        //                                 )))
                        .then(literal("autotestplacement").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .executes(context -> executeAutoTestPlacement(
                                                        context.getSource(),
                                                        autoTestService,
                                                        IntegerArgumentType.getInteger(context, "runs"),
                                                        IntegerArgumentType.getInteger(context, "holes")
                                                )))))
                        .then(literal("autotestplacementseed").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .then(argument("seed", LongArgumentType.longArg())
                                                        .executes(context -> executeAutoTestPlacementSeeded(
                                                                context.getSource(),
                                                                autoTestService,
                                                                IntegerArgumentType.getInteger(context, "runs"),
                                                                IntegerArgumentType.getInteger(context, "holes"),
                                                                LongArgumentType.getLong(context, "seed")
                                                        ))))))
                        .then(literal("autotestshadow").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeAutoTestShadowStatus(context.getSource(), autoTestService))
                                .then(literal("status")
                                        .executes(context -> executeAutoTestShadowStatus(context.getSource(), autoTestService)))
                                .then(literal("on")
                                        .executes(context -> executeAutoTestShadowSet(context.getSource(), autoTestService, true)))
                                .then(literal("off")
                                        .executes(context -> executeAutoTestShadowSet(context.getSource(), autoTestService, false))))
                        .then(literal("cancelautotest").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeCancelAutoTest(context.getSource(), autoTestService)))
                        .then(literal("autotestthrows").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(argument("count", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> executeAutoTestThrows(
                                                context.getSource(),
                                                throwAutoTestService,
                                                roundSessionStorage,
                                                playerRoundSessionStorage,
                                                buildCourseSessionManager,
                                                autoCourseService,
                                                IntegerArgumentType.getInteger(context, "count")
                                        ))))
                        .then(literal("quickthrowtest").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .then(argument("seed", LongArgumentType.longArg())
                                        .then(argument("count", IntegerArgumentType.integer(1, 200))
                                                .executes(context -> executeQuickThrowTest(
                                                        context.getSource(),
                                                        generator,
                                                        courseManager,
                                                        placementService,
                                                        placementValidator,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        practiceCourseStorage,
                                                        throwAutoTestService,
                                                        roundSessionStorage,
                                                        playerRoundSessionStorage,
                                                        buildCourseSessionManager,
                                                        autoCourseService,
                                                        LongArgumentType.getLong(context, "seed"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                )))))
                        .then(literal("cancelthrowtest").requires(McdgAdminCommands::canUseAdminCommands)
                                .requires(McdgAdminCommands::canUseAdvancedCommands)
                                .executes(context -> executeCancelThrowTest(context.getSource(), throwAutoTestService, roundSessionStorage, playerRoundSessionStorage, buildCourseSessionManager, autoCourseService)))
                        .then(buildCourseSessionManager.registerNode().requires(McdgAdminCommands::canUseAdminCommands))
                        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mcdg")
                    .then(literal("savesession").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> SessionCommands.executeSaveSession(
                                    context.getSource(), courseManager, roundStateManager, playerRoundSessionStorage, null))
                            .then(argument("players", EntityArgumentType.players())
                                    .executes(context -> SessionCommands.executeSaveSession(
                                            context.getSource(), courseManager, roundStateManager, playerRoundSessionStorage,
                                            EntityArgumentType.getPlayers(context, "players"))))));

            dispatcher.register(literal("mcdg")
                    .then(literal("resumesession").requires(McdgAdminCommands::canUseAdminCommands)
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
                    .then(literal("roundsession").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> SessionCommands.executeRoundSessionStatus(
                                    context.getSource(), roundSessionStorage))
                            .then(literal("status")
                                    .executes(context -> SessionCommands.executeRoundSessionStatus(
                                            context.getSource(), roundSessionStorage)))
                            .then(literal("clear")
                                    .executes(context -> SessionCommands.executeRoundSessionClear(
                                            context.getSource(), roundSessionStorage, courseManager, roundStateManager, practiceCourseStorage)))));

            dispatcher.register(literal("mcdg")
                    .then(literal("gotocoursebyindex").requires(McdgAdminCommands::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> executeGotoCourseByIndex(
                                            context.getSource(), practiceCourseStorage,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            dispatcher.register(literal("mcdg")
                    .then(literal("cleanupcoursebyindex").requires(McdgAdminCommands::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> executeCleanupCourseByIndex(
                                            context.getSource(), practiceCourseStorage, placementService,
                                            roundStateManager, courseManager,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            // Waypoint commands removed (player waypoints replaced by Xaero's Minimap)

            dispatcher.register(literal("mcdg")
                    .then(literal("buildresort").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeBuildResort(
                                    context.getSource(), generator, autoCourseService, practiceCourseStorage, null, null))
                            .then(argument("x", IntegerArgumentType.integer())
                                    .then(argument("z", IntegerArgumentType.integer())
                                            .executes(context -> ResortAdminCommands.executeBuildResort(
                                                    context.getSource(), generator, autoCourseService, practiceCourseStorage,
                                                    IntegerArgumentType.getInteger(context, "x"),
                                                    IntegerArgumentType.getInteger(context, "z")))))
                            .then(literal("overwrite").requires(McdgAdminCommands::canUseAdminCommands)
                                    .executes(context -> ResortAdminCommands.executeBuildResortOverwrite(
                                            context.getSource(), generator, autoCourseService, practiceCourseStorage)))
                            .then(literal("cancel").requires(McdgAdminCommands::canUseAdminCommands)
                                    .executes(context -> ResortAdminCommands.executeBuildResortCancel(
                                            context.getSource())))));

            dispatcher.register(literal("mcdg")
                    .then(literal("resetresort").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeResetResort(
                                    context.getSource()))));

            dispatcher.register(literal("mcdg")
                    .then(literal("removesurroundcourses").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> ResortAdminCommands.executeRemoveSurroundCourses(
                                    context.getSource(), placementService, practiceCourseStorage))));

            // autocourse
            dispatcher.register(literal("mcdg")
                    .then(literal("autocourse").requires(McdgAdminCommands::canUseAdminCommands)
                            .executes(context -> autoCourseService.executeAutoCourseNoName(context.getSource()))
                            .then(argument("name", StringArgumentType.greedyString())
                                    .executes(context -> autoCourseService.executeAutoCourseNamed(
                                            context.getSource(), StringArgumentType.getString(context, "name"))))
                            .then(literal("cancel")
                                    .executes(context -> autoCourseService.executeCancel(context.getSource())))));

            // removecourse
            dispatcher.register(literal("mcdg")
                    .then(literal("removecourse").requires(McdgAdminCommands::canUseAdminCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseAdminCommands.executeRemoveCourse(
                                            context.getSource(), courseManager, roundStateManager,
                                            practiceCourseStorage, playerRoundSessionStorage,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            // usecourse
            dispatcher.register(literal("mcdg")
                    .then(literal("usecourse").requires(McdgAdminCommands::canUseAdminCommands)
                            .requires(McdgAdminCommands::canUseAdvancedCommands)
                            .then(argument("index", IntegerArgumentType.integer(1))
                                    .executes(context -> CourseAdminCommands.executeUseCourse(
                                            context.getSource(), courseManager, roundStateManager,
                                            practiceCourseStorage,
                                            IntegerArgumentType.getInteger(context, "index"))))));

            // resumecourse
            dispatcher.register(literal("mcdg")
                    .then(literal("resumecourse").requires(McdgAdminCommands::canUseAdminCommands)
                            .requires(McdgAdminCommands::canUseAdvancedCommands)
                            .executes(context -> RoundLifecycleCommands.executeResumeCourse(
                                    context.getSource(), courseManager, roundStateManager,
                                    roundPresentationService, skipRoundPresentation, null))
                            .then(argument("players", EntityArgumentType.players())
                                    .executes(context -> RoundLifecycleCommands.executeResumeCourse(
                                            context.getSource(), courseManager, roundStateManager,
                                            roundPresentationService, skipRoundPresentation,
                                            EntityArgumentType.getPlayers(context, "players"))))));

            // roundstatus
            dispatcher.register(literal("mcdg")
                    .then(literal("roundstatus").requires(McdgAdminCommands::canUseAdminCommands)
                            .requires(McdgAdminCommands::canUseAdvancedCommands)
                            .executes(context -> RoundAdminCommands.executeRoundStatus(
                                    context.getSource(), courseManager, roundStateManager))));
        });
    }

        private static boolean canUseAdminCommands(ServerCommandSource source) {
                if (source.hasPermissionLevel(2)) {
                        return true;
                }

                // Keep local/integrated dev sessions usable even when OP metadata is not applied.
                return !source.getServer().isDedicated();
        }

        private static boolean canUseAdvancedCommands(ServerCommandSource source) {
                return canUseAdminCommands(source) && SHOW_ADVANCED_COMMANDS;
        }

        private static int executeDebugPermissions(ServerCommandSource source) {
                boolean hasPermissionLevelTwo = source.hasPermissionLevel(2);
                boolean dedicated = source.getServer().isDedicated();
                boolean allowedByGate = canUseAdminCommands(source);

                String sourceType = "non-entity";
                String sourceIdentity = source.getName();
                if (source.getEntity() instanceof ServerPlayerEntity player) {
                        sourceType = "player";
                        sourceIdentity = player.getGameProfile().getName() + " (" + player.getUuid() + ")";
                } else if (source.getEntity() != null) {
                        sourceType = "entity";
                        sourceIdentity = source.getEntity().getName().getString();
                }

                final String finalSourceType = sourceType;
                final String finalSourceIdentity = sourceIdentity;

                source.sendFeedback(() -> Text.literal(
                        "mcdg debug perms -> hasPermissionLevel(2)=" + hasPermissionLevelTwo
                                + ", dedicated=" + dedicated
                                + ", canUseAdminCommands=" + allowedByGate
                                + ", showAdvancedCommands=" + SHOW_ADVANCED_COMMANDS
                                + ", sourceType=" + finalSourceType
                                + ", source=" + finalSourceIdentity
                ), false);
                return 1;
        }

        private static int executeHelp(ServerCommandSource source) {
                source.sendFeedback(() -> Text.literal("MCDG quick help:"), false);
                source.sendFeedback(() -> Text.literal("- New course: /mcdg createcourse <seed> -> /mcdg startround (or /mcdg startround strict)."), false);
                source.sendFeedback(() -> Text.literal("- Generation model is unified across modes: land-first routing with water-carry cap <= 91 blocks (~300 ft)."), false);
                source.sendFeedback(() -> Text.literal("- Saved course: /mcdg listcourses -> /mcdg playcourse <index>."), false);
                source.sendFeedback(() -> Text.literal("- In-round basics: /mcdg joinround, /mcdg endround, /mcdg cleanupcourse."), false);
                if (SHOW_ADVANCED_COMMANDS) {
                        source.sendFeedback(() -> Text.literal("- Advanced commands are visible (MCDG_SHOW_ADVANCED_COMMANDS=true)."), false);
                } else {
                        source.sendFeedback(() -> Text.literal("- Advanced commands are hidden by default; set MCDG_SHOW_ADVANCED_COMMANDS=true to expose them."), false);
                }
                return 1;
        }

    private static int executePracticeCourseDeprecated(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        CoursePlacementService placementService,
                        CoursePlacementValidator placementValidator,
                        RoundStateManager roundStateManager,
                        RoundPresentationService roundPresentationService,
                        boolean skipRoundPresentation,
                        PracticeCourseStorage practiceCourseStorage,
                        boolean allowReusableFallback,
                        Collection<ServerPlayerEntity> selectedPlayers
        ) {
                source.sendFeedback(() -> Text.literal(
                        "practicecourse is deprecated. Use /mcdg startround for new generation or /mcdg playcourse <index> for saved courses."
                ), false);

                return executeStartRound(
                        source,
                        courseManager,
                        placementService,
                        placementValidator,
                        roundStateManager,
                        roundPresentationService,
                        skipRoundPresentation,
                        practiceCourseStorage,
                        true,
                        allowReusableFallback,
                        selectedPlayers
                );
        }

        private static int executeStartRound(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                    CoursePlacementService placementService,
                                        CoursePlacementValidator placementValidator,
                                        RoundStateManager roundStateManager,
                                        RoundPresentationService roundPresentationService,
                                        boolean skipRoundPresentation,
                                        PracticeCourseStorage practiceCourseStorage,
                                        boolean persistentCourse,
                                        boolean allowReusableFallback,
                                        Collection<ServerPlayerEntity> selectedPlayers
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                if (course == null) {
                        source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> first."));
                        return 0;
                }
                Course normalizedCourse = ensureSingleSignatureHole(course);
                if (normalizedCourse != course) {
                        course = normalizedCourse;
                        courseManager.setActiveCourse(course);
                }

                if (courseManager.isRoundActive()) {
                        source.sendError(Text.literal("Round is already active."));
                        return 0;
                }

                // Clean up any previously placed course edits so repeated test runs start from a fresh world state.
                PlacedCourseState existingPlaced = courseManager.getPlacedCourseState().orElse(null);
                if (existingPlaced != null) {
                        ServerWorld existingWorld = source.getServer().getWorld(existingPlaced.worldKey());
                        if (existingWorld != null) {
                                placementService.resetPlacedCourse(existingWorld, existingPlaced);
                                RoundChunkLoader.unloadAll(existingWorld);
                        }
                        courseManager.clearPlacedCourseState();
                        practiceCourseStorage.clear(source.getServer());
                        clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                }

                ServerWorld world = source.getWorld();
                int totalHoles = course.holes().size();
                long requestedSeed = course.seed();
                boolean startedFromFallback = false;

                // Show a center-screen progress title before terrain generation starts.
                for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                        if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                sendCourseBuildProgressOverlay(barPlayer, 0, totalHoles, 1, 1);
                        }
                }
                source.sendFeedback(() -> Text.literal("Starting course placement near your current surface position..."), false);

                try {
                        BlockPos baseOrigin = BlockPos.ofFloored(source.getPosition());
                        PlacedCourseState placed = null;
                        final int maxPlacementAttempts = 9;

                        for (int attempt = 1; attempt <= maxPlacementAttempts; attempt++) {
                                BlockPos attemptOrigin = offsetOriginForAttempt(baseOrigin, attempt);
                                final int displayAttempt = attempt;
                                try {
                                        placed = placementService.placeCourse(world, attemptOrigin, course, holesDone -> {
                                                for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                                        if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                                                sendCourseBuildProgressOverlay(barPlayer, holesDone, totalHoles, displayAttempt, maxPlacementAttempts);
                                                        }
                                                }
                                        });
                                } catch (RuntimeException placementEx) {
                                        if (attempt < maxPlacementAttempts) {
                                                final int nextAttempt = attempt + 1;
                                                source.sendFeedback(() -> Text.literal(
                                                        "Placement policy rejected this anchor (" + placementEx.getMessage()
                                                                + "). Retrying nearby (attempt " + nextAttempt + "/" + maxPlacementAttempts + ")..."
                                                ), false);
                                                continue;
                                        }
                                        throw placementEx;
                                }

                                CoursePlacementValidator.ValidationReport attemptReport = placementValidator.validatePlacedCourse(
                                        world,
                                        course,
                                        placed,
                                        "start-round-attempt-" + attempt
                                );

                                if (!hasRetryablePlacementIssue(attemptReport)) {
                                        break;
                                }

                                placementService.resetPlacedCourse(world, placed);
                                placed = null;

                                if (attempt < maxPlacementAttempts) {
                                        final int nextAttempt = attempt + 1;
                                        source.sendFeedback(() -> Text.literal(
                                                "Detected retryable placement issue (enclosure/route gap). Retrying at a nearby surface anchor (attempt "
                                                        + nextAttempt + "/" + maxPlacementAttempts + ")..."
                                        ), false);
                                }
                        }

                        if (placed == null && allowReusableFallback) {
                                Optional<PracticeCourseStorage.LoadedPracticeCourse> reusableFallback =
                                        practiceCourseStorage.loadMostRecentReusable(source.getServer(), world.getRegistryKey());
                                if (reusableFallback.isPresent()) {
                                        PracticeCourseStorage.LoadedPracticeCourse fallback = reusableFallback.get();
                                        course = ensureSingleSignatureHole(fallback.course());
                                        placed = fallback.placedCourseState();
                                        startedFromFallback = true;
                                        source.sendFeedback(() -> Text.literal(
                                                "Placement retries exhausted. Reusing the most recent recoverable course snapshot in this world."
                                        ), false);
                                }
                        }

                        if (placed == null) {
                                for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                        if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                                clearCourseBuildProgressOverlay(barPlayer);
                                        }
                                }
                                source.sendError(Text.literal(
                                        "Failed to place a surface-playable course after multiple attempts (enclosure/route issue persisted)."
                                ));
                                return 0;
                        }

                        // Course is placed — clear the center-screen progress title.
                        for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                        clearCourseBuildProgressOverlay(barPlayer);
                                }
                        }

                        List<ServerPlayerEntity> participants = resolveRoundParticipants(
                                source,
                                world,
                                selectedPlayers,
                                persistentCourse ? "practicecourse" : "startround"
                        );
                        if (participants.isEmpty()) {
                                source.sendError(Text.literal("No eligible participants selected for this world."));
                                return 0;
                        }

                        clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                        removeRoundThrowItemsFromPlayers(participants);

                        List<UUID> participantIds = new ArrayList<>();
                        BlockPos firstTee = placed.holeTees().get(1);
                        if (firstTee == null) {
                                source.sendError(Text.literal("Placed course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
                                return 0;
                        }

                        RoundChunkLoader.loadCourseChunks(world, placed);

                        for (ServerPlayerEntity player : participants) {
                                BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                                roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                                RoundChunkLoader.preloadChunksAround(world, safeTee, 1);
                                player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                                ensureSingleRoundThrowItem(player);
                                ScorecardManager.initializeScorecard(player, course, placed);
                                participantIds.add(player.getUuid());
                                player.sendMessage(Text.literal("Round staging. Moved to Hole 1 tee."), true);
                        }

                        int initializedPlayers = participantIds.size();
                        courseManager.setActiveParticipantIds(participantIds);

                        final int trackedPlayers = initializedPlayers;
                        announceSignatureHole(source, course, participantIds);

                        courseManager.setActiveCourse(course);
                        courseManager.setPlacedCourseState(placed);
                        courseManager.setPersistentPlacedCourse(persistentCourse);
                        courseManager.setLegacyPracticeSnapshot(false);
                        if (persistentCourse) {
                                practiceCourseStorage.save(source.getServer(), course, placed);
                        }
                        if (!startedFromFallback) {
                                // Compact is the default placement target; persist successful placements for reuse/recovery.
                                practiceCourseStorage.saveReusable(
                                        source.getServer(),
                                        course,
                                        placed,
                                        persistentCourse ? "practicecourse" : "startround",
                                        true
                                );
                        }

                        if (startedFromFallback) {
                                long fallbackSeed = course.seed();
                                source.sendFeedback(() -> Text.literal(
                                        "Started fallback course seed=" + fallbackSeed + " (requested seed=" + requestedSeed + ")."
                                ), false);
                        }

                        if (skipRoundPresentation) {
                                courseManager.setRoundActive(true);
                                // LAN safety: force source-player teleport using the same path as /mcdg gotocourse.
                                teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                                source.sendFeedback(() -> Text.literal(
                                                (persistentCourse ? "Practice course started" : "Round started")
                                                        + ". Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
                                ), true);
                                return 1;
                        }

                        int totalPar = totalCoursePar(course);
                        roundPresentationService.startCountdown(
                                source.getServer(),
                                participantIds,
                                course.name(),
                                course.holes().size(),
                                totalPar,
                                () -> {
                                        courseManager.setRoundActive(true);
                                        teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                                        source.sendFeedback(() -> Text.literal(
                                                (persistentCourse ? "Practice course live" : "Round live")
                                                        + ". Players=" + trackedPlayers + "."
                                        ), true);
                                }
                        );

                        source.sendFeedback(() -> Text.literal(
                                        (persistentCourse ? "Practice course presentation started" : "Round presentation started")
                                                + ". Players=" + trackedPlayers + "."
                        ), true);
                        return 1;
                } catch (RuntimeException ex) {
                        source.sendError(Text.literal("Failed to start round: " + ex.getMessage()));
                        return 0;
                }
        }

        private static int executeResumeCourse(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager,
                        RoundPresentationService roundPresentationService,
                        boolean skipRoundPresentation,
                        Collection<ServerPlayerEntity> selectedPlayers
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (course == null || placed == null) {
                        source.sendError(Text.literal("No stale placed course found. Use /mcdg startround or /mcdg practicecourse first."));
                        return 0;
                }
                Course normalizedCourse = ensureSingleSignatureHole(course);
                if (normalizedCourse != course) {
                        course = normalizedCourse;
                        courseManager.setActiveCourse(course);
                }

                if (courseManager.isRoundActive()) {
                        source.sendError(Text.literal("Round is already active."));
                        return 0;
                }

                ServerWorld world = source.getServer().getWorld(placed.worldKey());
                if (world == null) {
                        source.sendError(Text.literal("Placed course world is unavailable."));
                        return 0;
                }

                if (courseManager.isLegacyPracticeSnapshot()) {
                        source.sendFeedback(() -> Text.literal(
                                "Warning: this practice course came from a legacy snapshot format. If anything looks off, run /mcdg cleanupcourse then rebuild with /mcdg practicecourse."
                        ), false);
                }

                SavedCourseIntegrity integrity = validateSavedCourseIntegrity(world, course, placed);
                if (!integrity.valid()) {
                        source.sendError(Text.literal(
                                "Saved course data is stale/unplayable: " + integrity.reason()
                                        + ". Rebuild with /mcdg startround or choose another /mcdg playcourse entry."
                        ));
                        return 0;
                }

                int totalHoles = course.holes().size();
                List<ServerPlayerEntity> participants = resolveRoundParticipants(
                        source,
                        world,
                        selectedPlayers,
                        "resumecourse"
                );
                if (participants.isEmpty()) {
                        source.sendError(Text.literal("No eligible participants selected for this world."));
                        return 0;
                }

                clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                removeRoundThrowItemsFromPlayers(participants);

                List<UUID> participantIds = new ArrayList<>();
                BlockPos firstTee = placed.holeTees().get(1);
                if (firstTee == null) {
                        source.sendError(Text.literal("Saved course data is missing hole 1 tee position. Rebuild with /mcdg startround."));
                        return 0;
                }

                RoundChunkLoader.loadCourseChunks(world, placed);

                for (ServerPlayerEntity player : participants) {
                        BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                        roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                        RoundChunkLoader.preloadChunksAround(world, safeTee, 1);
                        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                        ensureSingleRoundThrowItem(player);
                        ScorecardManager.initializeScorecard(player, course, placed);
                        participantIds.add(player.getUuid());
                        player.sendMessage(Text.literal("Round resumed on existing course. Moved to Hole 1 tee."), true);
                }

                int initializedPlayers = participantIds.size();
                courseManager.setActiveParticipantIds(participantIds);

                final int trackedPlayers = initializedPlayers;
                announceSignatureHole(source, course, participantIds);
                if (skipRoundPresentation) {
                        courseManager.setRoundActive(true);
                        teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                        source.sendFeedback(() -> Text.literal(
                                "Round resumed. Players=" + trackedPlayers + ". Use /mcdg gotocourse if needed."
                        ), true);
                        return 1;
                }

                int totalPar = totalCoursePar(course);
                roundPresentationService.startCountdown(
                        source.getServer(),
                        participantIds,
                        course.name(),
                        totalHoles,
                        totalPar,
                        () -> {
                                courseManager.setRoundActive(true);
                                teleportSourcePlayerToHoleOne(source, courseManager, roundStateManager);
                                source.sendFeedback(() -> Text.literal(
                                        "Round live on existing course. Players=" + trackedPlayers + "."
                                ), true);
                        }
                );

                source.sendFeedback(() -> Text.literal(
                        "Round resume presentation started. Players=" + trackedPlayers + "."
                ), true);
                return 1;
        }

        private static int executeListCourses(
                        ServerCommandSource source,
                        PracticeCourseStorage practiceCourseStorage
        ) {
                Set<Integer> staleIndices = new HashSet<>();
                List<PracticeCourseStorage.ReusableCourseEntry> initialEntries = practiceCourseStorage.listReusable(source.getServer());
                for (PracticeCourseStorage.ReusableCourseEntry entry : initialEntries) {
                        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded =
                                practiceCourseStorage.loadReusableByIndex(source.getServer(), entry.index());
                        if (loaded.isEmpty()) {
                                staleIndices.add(entry.index());
                                continue;
                        }

                        PracticeCourseStorage.LoadedPracticeCourse reusable = loaded.get();
                        ServerWorld world = source.getServer().getWorld(reusable.placedCourseState().worldKey());
                        if (world == null) {
                                staleIndices.add(entry.index());
                                continue;
                        }

                        SavedCourseIntegrity integrity = validateSavedCourseIntegrity(world, reusable.course(), reusable.placedCourseState());
                        if (!integrity.valid()) {
                                staleIndices.add(entry.index());
                        }
                }

                if (!staleIndices.isEmpty()) {
                        int removed = practiceCourseStorage.pruneReusableByIndices(source.getServer(), staleIndices);
                        if (removed > 0) {
                                final int pruned = removed;
                                source.sendFeedback(() -> Text.literal(
                                        "Pruned " + pruned + " stale/unplayable reusable entries from catalog."
                                ), false);
                        }
                }

                List<PracticeCourseStorage.ReusableCourseEntry> entries = practiceCourseStorage.listReusable(source.getServer());
                if (entries.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("No reusable courses are saved yet."), false);
                        return 1;
                }

                source.sendFeedback(() -> Text.literal("Reusable courses (newest first): " + entries.size()), false);
                source.sendFeedback(() -> Text.literal("Tip: use /mcdg playcourse <index> to select and start a saved course in one step."), false);
                for (PracticeCourseStorage.ReusableCourseEntry entry : entries) {
                        source.sendFeedback(() -> Text.literal(
                                "#" + entry.index()
                                        + " " + entry.name()
                                        + " seed=" + entry.seed()
                                        + " holes=" + entry.holeCount()
                                        + " world=" + entry.worldKey()
                                        + " source=" + entry.sourceTag()
                                        + " compact=" + (entry.compactPreferred() ? "yes" : "no")
                        ), false);
                }
                return 1;
        }

        private static int executeUseCourse(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager,
                        PracticeCourseStorage practiceCourseStorage,
                        int oneBasedIndex
        ) {
                if (courseManager.isRoundActive()) {
                        source.sendError(Text.literal("Round is active. End the round before switching reusable courses."));
                        return 0;
                }

                Optional<PracticeCourseStorage.LoadedPracticeCourse> selected =
                        practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
                if (selected.isEmpty()) {
                        source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " was not found."));
                        return 0;
                }

                PracticeCourseStorage.LoadedPracticeCourse loaded = selected.get();
                if (source.getServer().getWorld(loaded.placedCourseState().worldKey()) == null) {
                        source.sendError(Text.literal("Reusable course #" + oneBasedIndex + " points to an unavailable world."));
                        return 0;
                }

                clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                courseManager.setActiveCourse(ensureSingleSignatureHole(loaded.course()));
                courseManager.setPlacedCourseState(loaded.placedCourseState());
                courseManager.setPersistentPlacedCourse(true);
                courseManager.setLegacyPracticeSnapshot(loaded.legacyFormat());
                courseManager.setRoundActive(false);

                Course active = courseManager.getActiveCourse().orElse(null);
                int holes = active == null ? 0 : active.holes().size();
                source.sendFeedback(() -> Text.literal(
                        "Reusable course #" + oneBasedIndex + " activated: "
                                + (active == null ? "unknown" : active.name())
                                + " (holes=" + holes + "). Use /mcdg resumecourse to play this saved placement, or /mcdg startround to generate a new placement."
                ), true);
                return 1;
        }

        private static int executePlayCourse(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager,
                        RoundPresentationService roundPresentationService,
                        boolean skipRoundPresentation,
                        PracticeCourseStorage practiceCourseStorage,
                        int oneBasedIndex,
                        Collection<ServerPlayerEntity> selectedPlayers
        ) {
                int activated = executeUseCourse(
                        source,
                        courseManager,
                        roundStateManager,
                        practiceCourseStorage,
                        oneBasedIndex
                );
                if (activated == 0) {
                        return 0;
                }

                return executeResumeCourse(
                        source,
                        courseManager,
                        roundStateManager,
                        roundPresentationService,
                        skipRoundPresentation,
                        selectedPlayers
                );
        }

        private static SavedCourseIntegrity validateSavedCourseIntegrity(ServerWorld world, Course course, PlacedCourseState placed) {
                if (placed.holeTees().isEmpty() || placed.holeBaskets().isEmpty()) {
                        return new SavedCourseIntegrity(false, "missing tee/basket placement maps");
                }

                BlockPos holeOneTee = placed.holeTees().get(1);
                if (holeOneTee == null) {
                        return new SavedCourseIntegrity(false, "hole 1 tee is missing");
                }

                for (Hole hole : course.holes()) {
                        int holeIndex = hole.index();
                        BlockPos teePos = placed.holeTees().get(holeIndex);
                        if (teePos == null) {
                                return new SavedCourseIntegrity(false, "hole " + holeIndex + " tee is missing");
                        }
                        if (!isExpectedTeePad(world, teePos)) {
                                return new SavedCourseIntegrity(false, "hole " + holeIndex + " tee pad is missing");
                        }

                        BlockPos basketPos = placed.holeBaskets().get(holeIndex);
                        if (basketPos == null) {
                                return new SavedCourseIntegrity(false, "hole " + holeIndex + " basket is missing");
                        }
                        if (!isExpectedBasketMarker(world, hole, basketPos)) {
                                return new SavedCourseIntegrity(false, "hole " + holeIndex + " basket marker is missing");
                        }
                }

                return new SavedCourseIntegrity(true, "ok");
        }

        private static boolean isExpectedTeePad(ServerWorld world, BlockPos teeCenter) {
                for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                                BlockPos sample = teeCenter.add(dx, 0, dz);
                                if (dx == 0 && dz == 0) {
                                        if (!world.getBlockState(sample).isOf(Blocks.LIME_CONCRETE)) {
                                                return false;
                                        }
                                } else if (!world.getBlockState(sample).isOf(Blocks.SMOOTH_STONE)) {
                                        return false;
                                }
                        }
                }
                return true;
        }

        private static boolean isExpectedBasketMarker(ServerWorld world, Hole hole, BlockPos basketStoredPos) {
                if (!world.getBlockState(basketStoredPos).isOf(Blocks.HOPPER)) {
                        return false;
                }

                int basketHeight = Math.max(1, hole.basket().basketHeight());
                for (int i = 1; i <= basketHeight + 1; i++) {
                        if (!world.getBlockState(basketStoredPos.up(i)).isOf(Blocks.IRON_BARS)) {
                                return false;
                        }
                }

                return world.getBlockState(basketStoredPos.up(basketHeight + 2)).isOf(Blocks.LANTERN);
        }

        private record SavedCourseIntegrity(boolean valid, String reason) {
        }

        private static int executePruneCourses(
                        ServerCommandSource source,
                        PracticeCourseStorage practiceCourseStorage,
                        int keepCount
        ) {
                int removed = practiceCourseStorage.pruneReusable(source.getServer(), keepCount);
                if (removed <= 0) {
                        source.sendFeedback(() -> Text.literal("No reusable courses were pruned. Keep count=" + keepCount + "."), false);
                        return 1;
                }

                final int finalRemoved = removed;
                source.sendFeedback(() -> Text.literal(
                        "Pruned " + finalRemoved + " reusable courses. Keep count=" + keepCount + "."
                ), true);
                return 1;
        }

        private static int totalCoursePar(Course course) {
                int par = 0;
                for (var hole : course.holes()) {
                        par += hole.par();
                }
                return par;
        }

        // Commented out due to missing CoursePlacementService.LodgingBuildResult and tryBuildPermanentLodgingSite
        //         private static int executeBuildCamp(
        //                         ServerCommandSource source,
        //                         CoursePlacementService placementService
        //         ) {
        //                 ServerWorld world = source.getWorld();
        //                 BlockPos requestedOrigin = BlockPos.ofFloored(source.getPosition());
        //                 CoursePlacementService.LodgingBuildResult result = placementService.tryBuildPermanentLodgingSite(world, requestedOrigin);
        //                 if (!result.success()) {
        //                         source.sendError(Text.literal(result.message()));
        //                         return 0;
        //                 }
        // 
        //                 BlockPos center = result.center();
        //                 source.sendFeedback(() -> Text.literal(
        //                                 "Permanent lodging site built at X=" + center.getX() + " Y=" + center.getY() + " Z=" + center.getZ()
        //                                         + ". This camp is separate from course central and created only on command."
        //                 ), true);
        //                 return 1;
        //         }
        // 
        private static void sendCourseBuildProgressOverlay(
                        ServerPlayerEntity player,
                        int holesDone,
                        int totalHoles,
                        int attempt,
                        int maxAttempts
        ) {
                int clampedDone = Math.max(0, Math.min(totalHoles, holesDone));
                int percent = totalHoles <= 0 ? 0 : Math.round((clampedDone * 100.0f) / totalHoles);
                String title = "Building Course " + percent + "%";
                String subtitle = clampedDone + "/" + totalHoles + " holes  |  attempt " + attempt + "/" + maxAttempts;

                int stayTicks = clampedDone == 0 ? 240 : 18;
                int fadeOutTicks = clampedDone == 0 ? 0 : 5;
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(2, stayTicks, fadeOutTicks));
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(Formatting.GOLD, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle).formatted(Formatting.WHITE)));
        }

        private static void clearCourseBuildProgressOverlay(ServerPlayerEntity player) {
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
        }

        private static void announceSignatureHole(ServerCommandSource source, Course course, List<java.util.UUID> participantIds) {
                var signatureHole = course.holes().stream().filter(hole -> hole.isSignature()).findFirst();
                if (signatureHole.isEmpty()) {
                        if (source.getEntity() instanceof ServerPlayerEntity player) {
                                player.sendMessage(Text.literal("Signature Hole: none detected on this layout."), false);
                        } else {
                                source.sendFeedback(() -> Text.literal("Signature Hole: none detected on this layout."), false);
                        }
                        return;
                }

                var hole = signatureHole.get();
                String message = "Signature Hole: H" + hole.index() + " | " + hole.signatureType().displayName();
                if (source.getEntity() instanceof ServerPlayerEntity player) {
                        showSignatureHoleOverlay(player, hole);
                } else {
                        source.sendFeedback(() -> Text.literal(message), false);
                }

                for (java.util.UUID participantId : participantIds) {
                        var player = source.getServer().getPlayerManager().getPlayer(participantId);
                        if (player != null) {
                                showSignatureHoleOverlay(player, hole);
                        }
                }
        }

        private static void showSignatureHoleOverlay(ServerPlayerEntity player, Hole hole) {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(6, 60, 12));
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Signature Hole: H" + hole.index()).formatted(Formatting.GOLD, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(hole.signatureType().displayName()).formatted(Formatting.WHITE)));
        }

        private static Course ensureSingleSignatureHole(Course generated) {
                if (generated == null || generated.holes().isEmpty()) {
                        return generated;
                }

                List<Hole> normalized = new ArrayList<>(generated.holes().size());
                int signatureCount = 0;
                for (Hole hole : generated.holes()) {
                        if (hole.isSignature()) {
                                signatureCount++;
                        }
                        normalized.add(hole);
                }

                if (signatureCount == 1) {
                        return generated;
                }

                for (int i = 0; i < normalized.size(); i++) {
                        Hole hole = normalized.get(i);
                        if (hole.isSignature()) {
                                normalized.set(i, new Hole(
                                        hole.index(),
                                        hole.par(),
                                        hole.distanceFeet(),
                                        hole.tee(),
                                        hole.basket(),
                                        hole.fairwaySegments(),
                                        SignatureHoleType.NONE
                                ));
                        }
                }

                int sigIndex = Math.floorMod((int) generated.seed(), normalized.size());
                Hole selected = normalized.get(sigIndex);
                normalized.set(sigIndex, new Hole(
                        selected.index(),
                        selected.par(),
                        selected.distanceFeet(),
                        selected.tee(),
                        selected.basket(),
                        selected.fairwaySegments(),
                        SignatureHoleType.ISLAND_GREEN
                ));

                return new Course(generated.seed(), generated.name(), normalized);
        }

        private static boolean hasRetryablePlacementIssue(CoursePlacementValidator.ValidationReport report) {
                for (CoursePlacementValidator.ValidationIssue issue : report.issues()) {
                        if ("basket_deeply_enclosed".equals(issue.code())
                                || "tee_deeply_enclosed".equals(issue.code())
                                || "par5_alternate_route_missing".equals(issue.code())
                                || "alternate_route_missing".equals(issue.code())
                                || "landing_gap_too_long".equals(issue.code())) {
                                return true;
                        }
                }
                return false;
        }

        private static BlockPos offsetOriginForAttempt(BlockPos baseOrigin, int attempt) {
                if (attempt <= 1) {
                        return baseOrigin;
                }

                return switch (attempt) {
                        case 2 -> baseOrigin.add(48, 0, 0);
                        case 3 -> baseOrigin.add(-48, 0, 0);
                        case 4 -> baseOrigin.add(0, 0, 48);
                        case 5 -> baseOrigin.add(0, 0, -48);
                        case 6 -> baseOrigin.add(72, 0, 72);
                        case 7 -> baseOrigin.add(-72, 0, 72);
                        case 8 -> baseOrigin.add(72, 0, -72);
                        case 9 -> baseOrigin.add(-72, 0, -72);
                        default -> baseOrigin;
                };
        }

                private static int executeCleanupCourse(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                    CoursePlacementService placementService,
                                        RoundStateManager roundStateManager,
                                        PracticeCourseStorage practiceCourseStorage
        ) {
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (placed == null) {
                        source.sendError(Text.literal("No placed course state to reset."));
                        return 0;
                }

                ServerWorld world = source.getServer().getWorld(placed.worldKey());
                if (world == null) {
                        source.sendError(Text.literal("Original world for placed course is not available."));
                        return 0;
                }

                evacuatePlayersBeforeCleanup(source, world, placed);
                RoundChunkLoader.unloadAll(world);
                placementService.resetPlacedCourse(world, placed);
                removeJunkDropsNearCourse(world, placed);
                removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);
                courseManager.clearPlacedCourseState();
                courseManager.setRoundActive(false);
                clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                practiceCourseStorage.clear(source.getServer());

                source.sendFeedback(() -> Text.literal("Course cleanup complete. Original blocks restored."), true);
                return 1;
        }

        private static void evacuatePlayersBeforeCleanup(ServerCommandSource source, ServerWorld world, PlacedCourseState placed) {
                ServerPlayerEntity sourcePlayer = source.getPlayer();
                BlockPos sourceAnchorSafeFeet = sourcePlayer != null && sourcePlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())
                        ? resolveSafeFeetNear(world, sourcePlayer.getBlockPos())
                        : resolveSafeFeetNear(world, world.getSpawnPos());
                if (isWithinPlacedCourseBuffer(placed, sourceAnchorSafeFeet, 28)) {
                        sourceAnchorSafeFeet = findNearestSafeOutsideCourse(world, placed, sourceAnchorSafeFeet, 28);
                }

                for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                        if (!player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                continue;
                        }

                        BlockPos targetFeet = resolveSafeFeetNear(world, player.getBlockPos());
                        String relocationReason = "nearby";

                        if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                                targetFeet = findNearestSafeOutsideCourse(world, placed, targetFeet, 28);
                                relocationReason = "nearby-safe";
                        }
                        if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                                targetFeet = sourceAnchorSafeFeet;
                                relocationReason = "admin";
                        }

                        player.teleport(targetFeet.getX() + 0.5, targetFeet.getY() + 1.0, targetFeet.getZ() + 0.5);
                        if ("nearby".equals(relocationReason) || "nearby-safe".equals(relocationReason)) {
                                player.sendMessage(Text.literal("Course cleanup in progress. Relocated to a nearby safe location."), true);
                        } else {
                                player.sendMessage(Text.literal("Course cleanup in progress. Relocated to an admin safe zone."), true);
                        }
                }
        }

        private static BlockPos findNearestSafeOutsideCourse(ServerWorld world, PlacedCourseState placed, BlockPos originFeet, int bufferBlocks) {
                BlockPos safeOrigin = resolveSafeFeetNear(world, originFeet);
                if (!isWithinPlacedCourseBuffer(placed, safeOrigin, bufferBlocks)) {
                        return safeOrigin;
                }

                for (int radius = 12; radius <= 144; radius += 12) {
                        for (int dx = -radius; dx <= radius; dx += 4) {
                                for (int dz = -radius; dz <= radius; dz += 4) {
                                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                                                continue;
                                        }
                                        BlockPos candidate = resolveSafeFeetNear(world, safeOrigin.add(dx, 0, dz));
                                        if (!isWithinPlacedCourseBuffer(placed, candidate, bufferBlocks)) {
                                                return candidate;
                                        }
                                }
                        }
                }

                return safeOrigin;
        }

        private static boolean isWithinPlacedCourseBuffer(PlacedCourseState placed, BlockPos pos, int bufferBlocks) {
                if (placed == null || pos == null || placed.holeTees().isEmpty()) {
                        return false;
                }

                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxZ = Integer.MIN_VALUE;

                for (BlockPos tee : placed.holeTees().values()) {
                        minX = Math.min(minX, tee.getX());
                        maxX = Math.max(maxX, tee.getX());
                        minZ = Math.min(minZ, tee.getZ());
                        maxZ = Math.max(maxZ, tee.getZ());
                }
                for (BlockPos basket : placed.holeBaskets().values()) {
                        minX = Math.min(minX, basket.getX());
                        maxX = Math.max(maxX, basket.getX());
                        minZ = Math.min(minZ, basket.getZ());
                        maxZ = Math.max(maxZ, basket.getZ());
                }
                for (BlockPos alternate : placed.holeAlternateAnchors().values()) {
                        minX = Math.min(minX, alternate.getX());
                        maxX = Math.max(maxX, alternate.getX());
                        minZ = Math.min(minZ, alternate.getZ());
                        maxZ = Math.max(maxZ, alternate.getZ());
                }

                int expandedMinX = minX - bufferBlocks;
                int expandedMaxX = maxX + bufferBlocks;
                int expandedMinZ = minZ - bufferBlocks;
                int expandedMaxZ = maxZ + bufferBlocks;
                return pos.getX() >= expandedMinX
                        && pos.getX() <= expandedMaxX
                        && pos.getZ() >= expandedMinZ
                        && pos.getZ() <= expandedMaxZ;
        }

        private static void teleportSourcePlayerToHoleOne(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager
        ) {
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (placed == null) {
                        return;
                }

                BlockPos firstTee = placed.holeTees().get(1);
                if (firstTee == null) {
                        return;
                }

                ServerWorld world = source.getServer().getWorld(placed.worldKey());
                if (world == null) {
                        return;
                }

                BlockPos safeTee = resolveSafeFeetNear(world, firstTee);

                ServerPlayerEntity sourcePlayer = source.getPlayer();
                if (sourcePlayer == null) {
                        return;
                }

                if (!courseManager.getActiveParticipantIds().contains(sourcePlayer.getUuid())) {
                        return;
                }

                if (roundStateManager.getState(sourcePlayer.getUuid()).isEmpty()) {
                        roundStateManager.startRoundForPlayer(sourcePlayer.getUuid(), safeTee);
                }

                RoundChunkLoader.preloadChunksAround(world, safeTee, 1);
                sourcePlayer.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
        }

        private static int executeGotoCourse(ServerCommandSource source, ActiveCourseManager courseManager) {
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (placed == null) {
                        source.sendError(Text.literal("No placed course found. Run /mcdg startround first."));
                        return 0;
                }

                BlockPos firstTee = placed.holeTees().get(1);
                if (firstTee == null) {
                        source.sendError(Text.literal("Hole 1 tee location is unavailable."));
                        return 0;
                }

                try {
                        var player = source.getPlayerOrThrow();
                        ServerWorld world = source.getServer().getWorld(placed.worldKey());
                        BlockPos safeTee = world == null ? firstTee : resolveSafeFeetNear(world, firstTee);
                        if (world != null) {
                                RoundChunkLoader.preloadChunksAround(world, safeTee, 1);
                        }
                        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                        source.sendFeedback(() -> Text.literal("Teleported to Hole 1 tee."), false);
                        return 1;
                } catch (Exception ex) {
                        source.sendError(Text.literal("This command must be run by a player."));
                        return 0;
                }
        }

        private static int executeGotoLie(ServerCommandSource source, RoundStateManager roundStateManager) {
                try {
                        ServerPlayerEntity player = source.getPlayerOrThrow();
                        Optional<BlockPos> relocated = HoleProgressTracker.relocatePlayerToSafeLie(player, roundStateManager);
                        if (relocated.isEmpty()) {
                                player.sendMessage(Text.literal("No active lie found to teleport to."), true);
                                return 0;
                        }

                        BlockPos lie = relocated.get();
                        player.sendMessage(
                                Text.literal("Teleported to lie: " + lie.getX() + ", " + lie.getY() + ", " + lie.getZ())
                                        .formatted(Formatting.GREEN),
                                true
                        );
                        return 1;
                } catch (Exception ex) {
                        source.sendError(Text.literal("This command must be run by a player."));
                        return 0;
                }
        }

        private static int executeResortTeleport(ServerCommandSource source) {
                try {
                        ServerPlayerEntity player = source.getPlayerOrThrow();
                        var resort = ResortWaypointManager.getResortWaypoint().orElse(null);
                        if (resort == null) {
                                source.sendError(Text.literal("No resort has been built yet."));
                                return 0;
                        }
                        String playerDimension = player.getWorld().getRegistryKey().getValue().toString();
                        if (!playerDimension.equals(resort.dimensionId())) {
                                source.sendError(Text.literal("Resort is in a different dimension. Use a portal to return."));
                                return 0;
                        }
                        ServerWorld world = player.getServerWorld();
                        BlockPos target = new BlockPos(resort.x(), resort.y(), resort.z()).south(4);
                        BlockPos safe = resolveSafeFeetNear(world, target);
                        player.teleport(world, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
                        player.sendMessage(Text.literal("Teleported to MCDG Resort!").formatted(Formatting.GREEN), false);
                        return 1;
                } catch (Exception ex) {
                        source.sendError(Text.literal("This command must be run by a player."));
                        return 0;
                }
        }

        private static int executeEndRound(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager
        ) {
                if (!courseManager.isRoundActive()) {
                        source.sendError(Text.literal("No active round to end."));
                        return 0;
                }

                removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);
                courseManager.setRoundActive(false);
                clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
                source.sendFeedback(() -> Text.literal("Round ended. Use /mcdg resetcourse to restore terrain edits."), true);
                return 1;
        }

        private static int executeJoinRound(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager,
                        Collection<ServerPlayerEntity> selectedPlayers
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (course == null || placed == null) {
                        source.sendError(Text.literal("No active placed course. Run /mcdg startround first."));
                        return 0;
                }
                if (!courseManager.isRoundActive()) {
                        source.sendError(Text.literal("Round is not live. Wait for presentation to finish before joining."));
                        return 0;
                }

                ServerWorld world = source.getServer().getWorld(placed.worldKey());
                if (world == null) {
                        source.sendError(Text.literal("Placed course world is unavailable."));
                        return 0;
                }

                List<ServerPlayerEntity> participants = resolveRoundParticipants(source, world, selectedPlayers, "joinround");
                if (participants.isEmpty()) {
                        source.sendError(Text.literal("No eligible participants selected for this world."));
                        return 0;
                }

                BlockPos firstTee = placed.holeTees().get(1);
                if (firstTee == null) {
                        source.sendError(Text.literal("Hole 1 tee location is unavailable."));
                        return 0;
                }

                int joinedCount = 0;
                int alreadyJoinedCount = 0;
                List<UUID> joinedIds = new ArrayList<>();
                for (ServerPlayerEntity player : participants) {
                        UUID playerId = player.getUuid();
                        boolean alreadyTracked = courseManager.getActiveParticipantIds().contains(playerId);
                        boolean hasRoundState = roundStateManager.getState(playerId).isPresent();
                        if (alreadyTracked && hasRoundState) {
                                RoundInventoryCleaner.restoreRoundInventory(player);
                                alreadyJoinedCount++;
                                continue;
                        }

                        BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                        roundStateManager.startRoundForPlayer(playerId, safeTee);
                        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                        RoundInventoryCleaner.restoreRoundInventory(player);
                        ScorecardManager.initializeScorecard(player, course, placed);
                        player.sendMessage(Text.literal("Joined current round. Teleported to Hole 1 tee."), true);
                        joinedIds.add(playerId);
                        joinedCount++;
                }

                if (!joinedIds.isEmpty()) {
                        courseManager.addActiveParticipantIds(joinedIds);
                }

                final int finalJoinedCount = joinedCount;
                final int finalAlreadyJoinedCount = alreadyJoinedCount;
                source.sendFeedback(() -> Text.literal(
                        "Join round complete. Added=" + finalJoinedCount + ", already active=" + finalAlreadyJoinedCount + "."
                ), true);
                return finalJoinedCount > 0 ? 1 : 0;
        }

        private static int executeRoundStatus(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        RoundStateManager roundStateManager
        ) {
                Set<UUID> participantIds = courseManager.getActiveParticipantIds();
                if (participantIds.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("Round status: no tracked participants."), false);
                        return 1;
                }

                int onlineCount = 0;
                int withStateCount = 0;
                for (UUID participantId : participantIds) {
                        if (source.getServer().getPlayerManager().getPlayer(participantId) != null) {
                                onlineCount++;
                        }
                        if (roundStateManager.getState(participantId).isPresent()) {
                                withStateCount++;
                        }
                }

                final int totalParticipants = participantIds.size();
                final int totalOnline = onlineCount;
                final int totalWithState = withStateCount;
                final boolean roundActive = courseManager.isRoundActive();
                final String worldLabel = courseManager.getPlacedCourseState()
                        .map(placed -> placed.worldKey().getValue().toString())
                        .orElse("none");

                source.sendFeedback(() -> Text.literal(
                        "Round status: active=" + roundActive
                                + ", participants=" + totalParticipants
                                + ", online=" + totalOnline
                                + ", withState=" + totalWithState
                                + ", world=" + worldLabel
                ), false);

                int listed = 0;
                for (UUID participantId : participantIds) {
                        if (listed >= 10) {
                                break;
                        }

                        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(participantId);
                        String playerLabel = onlinePlayer == null
                                ? participantId.toString().substring(0, 8)
                                : onlinePlayer.getName().getString();
                        var state = roundStateManager.getState(participantId).orElse(null);
                        String stateLabel = state == null
                                ? "no-state"
                                : ("H" + state.currentHole() + " strokes=" + state.totalStrokes());
                        String presence = onlinePlayer == null ? "offline" : "online";

                        source.sendFeedback(() -> Text.literal(
                                " - " + playerLabel + " | " + presence + " | " + stateLabel
                        ), false);
                        listed++;
                }

                if (participantIds.size() > listed) {
                        final int remaining = participantIds.size() - listed;
                        source.sendFeedback(() -> Text.literal(" - ... and " + remaining + " more participant(s)."), false);
                }
                return 1;
        }

        private static int executeShowRuleset(
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

        private static int executeSetRuleset(
                        CommandContext<ServerCommandSource> context,
                        TournamentRulesetManager rulesetManager,
                        TournamentRulesetManager.Ruleset ruleset
        ) {
                rulesetManager.setActiveRuleset(ruleset);
                context.getSource().sendFeedback(() -> Text.literal("Ruleset set to " + ruleset.name().toLowerCase() + "."), true);
                return 1;
        }

        private static int executeShowStrictSurfacePreset(
                        CommandContext<ServerCommandSource> context,
                        TournamentRulesetManager rulesetManager
        ) {
                TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
                context.getSource().sendFeedback(
                        () -> Text.literal("Strict surface preset: " + preset.name().toLowerCase()),
                        false
                );
                return 1;
        }

        private static int executeSetStrictSurfacePreset(
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

        private static void removeRoundThrowItemsFromCourseWorldPlayers(ServerCommandSource source, ActiveCourseManager courseManager) {
                Set<UUID> participantIds = courseManager.getActiveParticipantIds();
                if (!participantIds.isEmpty()) {
                        for (UUID playerId : participantIds) {
                                ServerPlayerEntity participant = source.getServer().getPlayerManager().getPlayer(playerId);
                                if (participant != null) {
                                        removeRoundThrowItems(participant);
                                }
                        }
                        return;
                }

                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (placed == null) {
                        return;
                }
                for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld().getRegistryKey() == placed.worldKey()) {
                                removeRoundThrowItems(player);
                        }
                }
        }

        private static void removeRoundThrowItemsFromPlayers(Collection<ServerPlayerEntity> players) {
                for (ServerPlayerEntity player : players) {
                        removeRoundThrowItems(player);
                }
        }

        private static void clearRoundStateForTrackedParticipants(ActiveCourseManager courseManager, RoundStateManager roundStateManager) {
                roundStateManager.clearPlayers(courseManager.getActiveParticipantIds());
                courseManager.clearActiveParticipantIds();
        }

        private static List<ServerPlayerEntity> resolveRoundParticipants(
                        ServerCommandSource source,
                        ServerWorld world,
                        Collection<ServerPlayerEntity> selectedPlayers,
                        String commandName
        ) {
                LinkedHashSet<ServerPlayerEntity> participants = new LinkedHashSet<>();
                if (selectedPlayers != null && !selectedPlayers.isEmpty()) {
                        participants.addAll(selectedPlayers);
                } else {
                        ServerPlayerEntity sourcePlayer = source.getPlayer();
                        if (sourcePlayer == null) {
                                source.sendError(Text.literal(
                                        "Console usage requires explicit players: /mcdg " + commandName + " <players>."
                                ));
                                return List.of();
                        }
                        participants.add(sourcePlayer);
                }

                List<ServerPlayerEntity> sameWorldParticipants = new ArrayList<>();
                int skippedDifferentWorld = 0;
                for (ServerPlayerEntity participant : participants) {
                        if (participant.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                sameWorldParticipants.add(participant);
                        } else {
                                skippedDifferentWorld++;
                        }
                }

                final int skippedCount = skippedDifferentWorld;
                if (skippedCount > 0) {
                        source.sendFeedback(() -> Text.literal(
                                "Skipped " + skippedCount + " player(s) not in the current course world."
                        ), false);
                }

                return sameWorldParticipants;
        }

        private static void ensureSingleRoundThrowItem(ServerPlayerEntity player) {
                removeRoundThrowItems(player);
                player.giveItemStack(new ItemStack(McdgItems.TRAINING_DISC, 1));
        }

        private static void removeRoundThrowItems(ServerPlayerEntity player) {
                RoundInventoryCleaner.purgeRoundItemsAndJunk(player);
        }

        private static void removeJunkDropsNearCourse(ServerWorld world, PlacedCourseState placed) {
                if (placed.originalBlocks().isEmpty()) {
                        return;
                }

                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxY = Integer.MIN_VALUE;
                int maxZ = Integer.MIN_VALUE;

                for (BlockPos pos : placed.originalBlocks().keySet()) {
                        minX = Math.min(minX, pos.getX());
                        minY = Math.min(minY, pos.getY());
                        minZ = Math.min(minZ, pos.getZ());
                        maxX = Math.max(maxX, pos.getX());
                        maxY = Math.max(maxY, pos.getY());
                        maxZ = Math.max(maxZ, pos.getZ());
                }

                Box search = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).expand(8.0);
                for (ItemEntity entity : world.getEntitiesByClass(ItemEntity.class, search, e -> true)) {
                        if (RoundInventoryCleaner.isJunkItem(entity.getStack())) {
                                entity.discard();
                        }
                }
        }

        private static int executeValidatePlacement(
                        ServerCommandSource source,
                        ActiveCourseManager courseManager,
                        CoursePlacementValidator placementValidator
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                if (course == null) {
                        source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> and /mcdg startround first."));
                        return 0;
                }

                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (placed == null) {
                        source.sendError(Text.literal("No placed course found. Run /mcdg startround first."));
                        return 0;
                }

                ServerWorld world = source.getServer().getWorld(placed.worldKey());
                if (world == null) {
                        source.sendError(Text.literal("Placed course world is unavailable for validation."));
                        return 0;
                }

                CoursePlacementValidator.ValidationReport report = placementValidator.validatePlacedCourse(world, course, placed, "active-course");
                int invalidHoles = report.metrics().getOrDefault("invalid_holes", 0);
                int warningLandingGaps = report.metrics().getOrDefault("warning_landing_gaps", 0);
                int maxLandingGap = report.metrics().getOrDefault("max_landing_gap", 0);
                int landingGapWarningThreshold = report.metrics().getOrDefault("landing_gap_warning_threshold", 95);
                int landingGapFailThreshold = report.metrics().getOrDefault("landing_gap_fail_threshold", 110);
                source.sendFeedback(() -> Text.literal(
                                "Validation " + (report.passed() ? "PASSED" : "FAILED")
                                        + " | holes=" + report.metrics().getOrDefault("total_holes", 0)
                                        + ", invalid=" + invalidHoles
                                        + ", issues=" + report.issueCount()
                                        + ", warningLandingGaps=" + warningLandingGaps
                                        + ", maxLandingGap=" + maxLandingGap
                                        + " (warn>" + landingGapWarningThreshold + ", fail>" + landingGapFailThreshold + ")"
                                        + ", biome=" + report.biome()
                ), true);

                int maxIssueLines = 8;
                List<CoursePlacementValidator.ValidationIssue> issues = report.issues();
                for (int i = 0; i < issues.size() && i < maxIssueLines; i++) {
                        CoursePlacementValidator.ValidationIssue issue = issues.get(i);
                        String posText = issue.position() == null
                                ? ""
                                : (" @ " + issue.position().getX() + " " + issue.position().getY() + " " + issue.position().getZ());
                        source.sendFeedback(() -> Text.literal(
                                " - H" + issue.holeIndex() + " [" + issue.code() + "] " + issue.message() + posText
                        ), false);
                }

                if (issues.size() > maxIssueLines) {
                        int remaining = issues.size() - maxIssueLines;
                        source.sendFeedback(() -> Text.literal(" - ... and " + remaining + " more issues."), false);
                }

                return report.passed() ? 1 : 0;
        }

        private static int executeAutoTestPlacement(
                        ServerCommandSource source,
                        PlacementAutoTestService autoTestService,
                        int runs,
                        int holes
        ) {
                return autoTestService.start(source, runs, holes);
        }

        private static int executeAutoTestPlacementSeeded(
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

        private static int executeAutoTestShadowStatus(
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

        private static int executeAutoTestShadowSet(
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

        private static int executeCancelAutoTest(ServerCommandSource source, PlacementAutoTestService autoTestService) {
                return autoTestService.cancel(source);
        }

        private static int executeAutoTestThrows(
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

        private static int executeCancelThrowTest(ServerCommandSource source, ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService) {
                return throwAutoTestService.cancel(source);
        }

        private static int executeQuickThrowTest(
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

                int started = executeStartRound(
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

        private static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
                if (isStandableFeet(world, preferredFeet)) {
                        return preferredFeet;
                }

                for (int dy = 1; dy <= 6; dy++) {
                        BlockPos up = preferredFeet.up(dy);
                        if (isStandableFeet(world, up)) {
                                return up;
                        }
                        BlockPos down = preferredFeet.down(dy);
                        if (isStandableFeet(world, down)) {
                                return down;
                        }
                }

                for (int radius = 1; radius <= 6; radius++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                                for (int dz = -radius; dz <= radius; dz++) {
                                        if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                                                continue;
                                        }
                                        BlockPos candidate = preferredFeet.add(dx, 0, dz);
                                        if (isStandableFeet(world, candidate)) {
                                                return candidate;
                                        }
                                        for (int dy = 1; dy <= 3; dy++) {
                                                BlockPos candidateUp = candidate.up(dy);
                                                if (isStandableFeet(world, candidateUp)) {
                                                        return candidateUp;
                                                }
                                                BlockPos candidateDown = candidate.down(dy);
                                                if (isStandableFeet(world, candidateDown)) {
                                                        return candidateDown;
                                                }
                                        }
                                }
                        }
                }

                return preferredFeet;
        }

        private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
                if (!world.getFluidState(feet).isEmpty()) {
                        return false;
                }
                if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
                        return false;
                }

                BlockPos head = feet.up();
                if (!world.getFluidState(head).isEmpty()) {
                        return false;
                }
                if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
                        return false;
                }

                BlockPos ground = feet.down();
                if (!world.getFluidState(ground).isEmpty()) {
                        return false;
                }

                return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
        }

    private static int executeGotoCourseByIndex(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            int oneBasedIndex
    ) {
        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
        if (loaded.isEmpty()) {
            source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
            return 0;
        }

        PlacedCourseState placed = loaded.get().placedCourseState();
        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Hole 1 tee location is unavailable for course #" + oneBasedIndex + "."));
            return 0;
        }

        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            BlockPos safeTee = world == null ? firstTee : resolveSafeFeetNear(world, firstTee);
            if (world != null) {
                RoundChunkLoader.preloadChunksAround(world, safeTee, 1);
            }
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            source.sendFeedback(() -> Text.literal("Teleported to Hole 1 tee."), false);
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int executeCleanupCourseByIndex(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            int oneBasedIndex
    ) {
        // Full load required: cleanup needs originalBlocks to restore the world.
        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndexFull(source.getServer(), oneBasedIndex);
        if (loaded.isEmpty()) {
            source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
            return 0;
        }

        PlacedCourseState placed = loaded.get().placedCourseState();
        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("World for course #" + oneBasedIndex + " is not available."));
            return 0;
        }

        evacuatePlayersBeforeCleanup(source, world, placed);
        RoundChunkLoader.unloadAll(world);
        placementService.resetPlacedCourse(world, placed);
        removeJunkDropsNearCourse(world, placed);
        removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);
        courseManager.setRoundActive(false);
        clearRoundStateForTrackedParticipants(courseManager, roundStateManager);

        source.sendFeedback(() -> Text.literal("Cleaned up course #" + oneBasedIndex + "."), true);
        return 1;
    }

    private static int executeDebugObClassifier(ServerCommandSource source) {
        boolean current = OutOfBoundsClassifier.isDebugLoggingEnabled();
        source.sendFeedback(() -> Text.literal("OB Classifier debug logging: " + (current ? "enabled" : "disabled")), false);
        return current ? 1 : 0;
    }

    private static int executeDebugObClassifierSet(ServerCommandSource source, boolean enabled) {
        OutOfBoundsClassifier.setDebugLogging(enabled);
        source.sendFeedback(() -> Text.literal("OB Classifier debug logging " + (enabled ? "enabled" : "disabled")), true);
        return enabled ? 1 : 0;
    }
}
