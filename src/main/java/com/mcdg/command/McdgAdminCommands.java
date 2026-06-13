package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.CourseFireProtection;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.McdgMod;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortBuilder;
import com.mcdg.world.ResortWaypointManager;
import com.mcdg.world.ResortCoursePlacement;
import com.mcdg.world.WorldSpawnHandler;
import com.mcdg.world.ResortData;
import com.mcdg.net.WaypointRemovedSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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
            ThrowAutoTestService throwAutoTestService,
            RoundSessionStorage roundSessionStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            BuildCourseSessionManager buildCourseSessionManager,
            AutoCourseService autoCourseService
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mcdg")
                        .executes(context -> MenuCommands.sendMenuScreen(context.getSource(), courseManager, playerRoundSessionStorage, rulesetManager, practiceCourseStorage))
                        .then(literal("menu")
                                .executes(context -> MenuCommands.sendMenuScreen(context.getSource(), courseManager, playerRoundSessionStorage, rulesetManager, practiceCourseStorage))
                                .then(literal("player")
                                        .executes(context -> MenuCommands.executeMenuPlayer(context.getSource(), rulesetManager)))
                                .then(literal("admin").requires(CommandPermissions::canUseAdminCommands)
                                        .executes(context -> MenuCommands.executeMenuAdmin(context.getSource(), rulesetManager)))
                                .then(literal("round")
                                        .executes(context -> MenuCommands.executeMenuRound(context.getSource(), rulesetManager)))
                                .then(literal("courses")
                                        .executes(context -> MenuCommands.executeMenuCourses(context.getSource(), rulesetManager)))
                                .then(literal("waypoints")
                                        .executes(context -> MenuCommands.executeMenuWaypoints(context.getSource(), rulesetManager)))
                                .then(literal("rules")
                                        .executes(context -> MenuCommands.executeMenuRules(context.getSource(), rulesetManager)))
                                .then(literal("session")
                                        .executes(context -> MenuCommands.executeMenuSession(context.getSource(), rulesetManager)))
                                .then(literal("confirm-request").requires(CommandPermissions::canUseAdminCommands)
                                        .then(argument("action", StringArgumentType.word())
                                                .executes(context -> MenuCommands.executeMenuConfirmRequest(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "action")
                                                ))))
                                .then(literal("confirm-run").requires(CommandPermissions::canUseAdminCommands)
                                        .then(argument("token", LongArgumentType.longArg())
                                                .executes(context -> MenuCommands.executeMenuConfirmRun(
                                                        context.getSource(),
                                                        LongArgumentType.getLong(context, "token")
                                                ))))
                                .then(literal("confirm-cancel").requires(CommandPermissions::canUseAdminCommands)
                                        .executes(context -> MenuCommands.executeMenuConfirmCancel(context.getSource()))))
                        .then(buildCourseSessionManager.registerNode().requires(CommandPermissions::canUseAdminCommands))
                        .then(literal("help").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> executeHelp(context.getSource())))
                        .then(literal("gotolie")
                                .executes(context -> RoundAdminCommands.executeGotoLie(context.getSource(), roundStateManager)))
                        .then(literal("createcourse").requires(CommandPermissions::canUseAdminCommands)
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(context -> CourseAdminCommands.executeCreateCourse(
                                                context.getSource(),
                                                generator,
                                                courseManager,
                                                LongArgumentType.getLong(context, "seed")
                                        ))))
                        .then(literal("autocourse").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> autoCourseService.executeAutoCourseNoName(context.getSource()))
                                .then(argument("name", StringArgumentType.greedyString())
                                        .executes(context -> autoCourseService.executeAutoCourseNamed(context.getSource(), StringArgumentType.getString(context, "name"))))
                                .then(literal("cancel")
                                        .executes(context -> autoCourseService.executeCancel(context.getSource())))) 
                        .then(literal("startround").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> RoundLifecycleCommands.executeStartRound(
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
                                        .executes(context -> RoundLifecycleCommands.executeStartRound(
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
                                        .executes(context -> RoundLifecycleCommands.executeStartRound(
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
                                                .executes(context -> RoundLifecycleCommands.executeStartRound(
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
                        .then(literal("resumecourse").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
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
                        .then(literal("listcourses").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> CourseAdminCommands.executeListCourses(
                                        context.getSource(),
                                        practiceCourseStorage
                                )))
                        .then(literal("usecourse").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> CourseAdminCommands.executeUseCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("playcourse").requires(CommandPermissions::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> CourseAdminCommands.executePlayCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                roundPresentationService,
                                                skipRoundPresentation,
                                                rulesetManager,
                                                false,
                                                null,
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index"),
                                                null
                                        ))
                                        .then(literal("strict")
                                                .executes(context -> CourseAdminCommands.executePlayCourseStrictPrompt(
                                                        context.getSource(),
                                                        practiceCourseStorage,
                                                        IntegerArgumentType.getInteger(context, "index")
                                                ))
                                                .then(literal("fast")
                                                        .executes(context -> CourseAdminCommands.executePlayCourse(
                                                                context.getSource(),
                                                                courseManager,
                                                                roundStateManager,
                                                                roundPresentationService,
                                                                skipRoundPresentation,
                                                                rulesetManager,
                                                                true,
                                                                TournamentRulesetManager.StrictSurfacePreset.FAST,
                                                                practiceCourseStorage,
                                                                IntegerArgumentType.getInteger(context, "index"),
                                                                null
                                                        )))
                                                .then(literal("balanced")
                                                        .executes(context -> CourseAdminCommands.executePlayCourse(
                                                                context.getSource(),
                                                                courseManager,
                                                                roundStateManager,
                                                                roundPresentationService,
                                                                skipRoundPresentation,
                                                                rulesetManager,
                                                                true,
                                                                TournamentRulesetManager.StrictSurfacePreset.BALANCED,
                                                                practiceCourseStorage,
                                                                IntegerArgumentType.getInteger(context, "index"),
                                                                null
                                                        )))
                                                .then(literal("tournament")
                                                        .executes(context -> CourseAdminCommands.executePlayCourse(
                                                                context.getSource(),
                                                                courseManager,
                                                                roundStateManager,
                                                                roundPresentationService,
                                                                skipRoundPresentation,
                                                                rulesetManager,
                                                                true,
                                                                TournamentRulesetManager.StrictSurfacePreset.TOURNAMENT,
                                                                practiceCourseStorage,
                                                                IntegerArgumentType.getInteger(context, "index"),
                                                                null
                                                        ))))
                                        .then(argument("players", EntityArgumentType.players())
                                                .executes(context -> CourseAdminCommands.executePlayCourse(
                                                        context.getSource(),
                                                        courseManager,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        skipRoundPresentation,
                                                        rulesetManager,
                                                        false,
                                                        null,
                                                        practiceCourseStorage,
                                                        IntegerArgumentType.getInteger(context, "index"),
                                                        EntityArgumentType.getPlayers(context, "players")
                                                )))))
                        .then(literal("prunecourses").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> CourseAdminCommands.executePruneCourses(
                                        context.getSource(),
                                        practiceCourseStorage,
                                        6
                                ))
                                .then(argument("keep", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> CourseAdminCommands.executePruneCourses(
                                                context.getSource(),
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "keep")
                                        ))))
                        .then(literal("removecourse").requires(CommandPermissions::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> CourseAdminCommands.executeRemoveCourse(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                playerRoundSessionStorage,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("cleanupcourse").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> RoundLifecycleCommands.executeCleanupCourse(
                                        context.getSource(),
                                        courseManager,
                                        placementService,
                                        roundStateManager,
                                        practiceCourseStorage
                                )))
                        .then(literal("gotocoursebyindex").requires(CommandPermissions::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> RoundLifecycleCommands.executeGotoCourseByIndex(
                                                context.getSource(),
                                                practiceCourseStorage,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("cleanupcoursebyindex").requires(CommandPermissions::canUseAdminCommands)
                                .then(argument("index", IntegerArgumentType.integer(1))
                                        .executes(context -> RoundLifecycleCommands.executeCleanupCourseByIndex(
                                                context.getSource(),
                                                practiceCourseStorage,
                                                placementService,
                                                roundStateManager,
                                                courseManager,
                                                IntegerArgumentType.getInteger(context, "index")
                                        ))))
                        .then(literal("waypoint").requires(CommandPermissions::canUseAdminCommands)
                                .then(literal("list")
                                        .executes(context -> WaypointCommands.executeWaypointList(context.getSource(), courseManager)))
                                .then(literal("clear")
                                        .executes(context -> WaypointCommands.executeWaypointClear(context.getSource())))
                                .then(literal("tp")
                                        .executes(context -> WaypointCommands.executeWaypointTeleportPrompt(context.getSource(), courseManager))
                                        .then(argument("target", StringArgumentType.greedyString())
                                                .executes(context -> WaypointCommands.executeWaypointTeleport(
                                                        context.getSource(),
                                                        courseManager,
                                                        StringArgumentType.getString(context, "target")
                                                )))))
                        .then(literal("endround").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> RoundAdminCommands.executeEndRound(context.getSource(), courseManager, roundStateManager)))
                        .then(literal("joinround").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> RoundAdminCommands.executeJoinRound(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> RoundAdminCommands.executeJoinRound(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("roundstatus").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> RoundAdminCommands.executeRoundStatus(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager
                                )))
                        .then(literal("savesession").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> SessionCommands.executeSaveSession(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        playerRoundSessionStorage,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> SessionCommands.executeSaveSession(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                playerRoundSessionStorage,
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
                        .then(literal("resumesession").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> SessionCommands.executeResumeSession(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        practiceCourseStorage,
                                        playerRoundSessionStorage,
                                        SessionCommands.ResumeSourceSelection.PREFER_MANUAL,
                                        null
                                ))
                                .then(argument("players", EntityArgumentType.players())
                                        .executes(context -> SessionCommands.executeResumeSession(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                playerRoundSessionStorage,
                                                SessionCommands.ResumeSourceSelection.PREFER_MANUAL,
                                                EntityArgumentType.getPlayers(context, "players")
                                        )))
                                .then(literal("manual")
                                        .executes(context -> SessionCommands.executeResumeSession(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                playerRoundSessionStorage,
                                                SessionCommands.ResumeSourceSelection.MANUAL_ONLY,
                                                null
                                        ))
                                        .then(argument("players", EntityArgumentType.players())
                                                .executes(context -> SessionCommands.executeResumeSession(
                                                        context.getSource(),
                                                        courseManager,
                                                        roundStateManager,
                                                        practiceCourseStorage,
                                                        playerRoundSessionStorage,
                                                        SessionCommands.ResumeSourceSelection.MANUAL_ONLY,
                                                        EntityArgumentType.getPlayers(context, "players")
                                                ))))
                                .then(literal("auto")
                                        .executes(context -> SessionCommands.executeResumeSession(
                                                context.getSource(),
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage,
                                                playerRoundSessionStorage,
                                                SessionCommands.ResumeSourceSelection.AUTO_ONLY,
                                                null
                                        ))
                                        .then(argument("players", EntityArgumentType.players())
                                                .executes(context -> SessionCommands.executeResumeSession(
                                                        context.getSource(),
                                                        courseManager,
                                                        roundStateManager,
                                                        practiceCourseStorage,
                                                        playerRoundSessionStorage,
                                                        SessionCommands.ResumeSourceSelection.AUTO_ONLY,
                                                        EntityArgumentType.getPlayers(context, "players")
                                                )))))
                        .then(literal("roundsession").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> SessionCommands.executeRoundSessionStatus(
                                        context.getSource(),
                                        roundSessionStorage
                                ))
                                .then(literal("status")
                                        .executes(context -> SessionCommands.executeRoundSessionStatus(
                                                context.getSource(),
                                                roundSessionStorage
                                        )))
                                .then(literal("clear")
                                        .executes(context -> SessionCommands.executeRoundSessionClear(
                                                context.getSource(),
                                                roundSessionStorage,
                                                courseManager,
                                                roundStateManager,
                                                practiceCourseStorage
                                        ))))
                        .then(literal("ruleset").requires(CommandPermissions::canUseAdminCommands)
                                .executes(context -> RulesetCommands.executeShowRuleset(context.getSource(), rulesetManager))
                                .then(literal("casual")
                                        .executes(context -> RulesetCommands.executeSetRuleset(context.getSource(), rulesetManager, TournamentRulesetManager.Ruleset.CASUAL)))
                                .then(literal("strict")
                                        .executes(context -> RulesetCommands.executeSetRuleset(context.getSource(), rulesetManager, TournamentRulesetManager.Ruleset.STRICT)))
                                .then(literal("surface")
                                        .executes(context -> RulesetCommands.executeShowStrictSurfacePreset(context.getSource(), rulesetManager))
                                        .then(argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                        builder.suggest("fast");
                                                        builder.suggest("balanced");
                                                        builder.suggest("tournament");
                                                        return builder.buildFuture();
                                                })
                                                .executes(context -> RulesetCommands.executeSetStrictSurfacePreset(context.getSource(), rulesetManager, StringArgumentType.getString(context, "preset"))))))
                        .then(literal("debugperms").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> DebugCommands.executeDebugPermissions(context.getSource())))
                        .then(literal("validateplacement").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> DebugCommands.executeValidatePlacement(
                                        context.getSource(),
                                        courseManager,
                                        placementValidator
                                )))
                        .then(literal("autotestplacement").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .executes(context -> DebugCommands.executeAutoTestPlacement(
                                                        context.getSource(),
                                                        autoTestService,
                                                        IntegerArgumentType.getInteger(context, "runs"),
                                                        IntegerArgumentType.getInteger(context, "holes")
                                                )))))
                        .then(literal("autotestplacementseed").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .then(argument("seed", LongArgumentType.longArg())
                                                        .executes(context -> DebugCommands.executeAutoTestPlacementSeeded(
                                                                context.getSource(),
                                                                autoTestService,
                                                                IntegerArgumentType.getInteger(context, "runs"),
                                                                IntegerArgumentType.getInteger(context, "holes"),
                                                                LongArgumentType.getLong(context, "seed")
                                                        ))))))
                        .then(literal("autotestshadow").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> DebugCommands.executeAutoTestShadowStatus(context.getSource(), autoTestService))
                                .then(literal("status")
                                        .executes(context -> DebugCommands.executeAutoTestShadowStatus(context.getSource(), autoTestService)))
                                .then(literal("on")
                                        .executes(context -> DebugCommands.executeAutoTestShadowSet(context.getSource(), autoTestService, true)))
                                .then(literal("off")
                                        .executes(context -> DebugCommands.executeAutoTestShadowSet(context.getSource(), autoTestService, false))))
                        .then(literal("cancelautotest").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> DebugCommands.executeCancelAutoTest(context.getSource(), autoTestService)))
                        .then(literal("autotestthrows").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .then(argument("count", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> DebugCommands.executeAutoTestThrows(
                                                context.getSource(),
                                                throwAutoTestService,
                                                IntegerArgumentType.getInteger(context, "count")
                                        ))))
                        .then(literal("quickthrowtest").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .then(argument("seed", LongArgumentType.longArg())
                                        .then(argument("count", IntegerArgumentType.integer(1, 200))
                                                .executes(context -> DebugCommands.executeQuickThrowTest(
                                                        context.getSource(),
                                                        generator,
                                                        courseManager,
                                                        placementService,
                                                        placementValidator,
                                                        roundStateManager,
                                                        roundPresentationService,
                                                        practiceCourseStorage,
                                                        throwAutoTestService,
                                                        LongArgumentType.getLong(context, "seed"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                )))))
                        .then(literal("cancelthrowtest").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> DebugCommands.executeCancelThrowTest(context.getSource(), throwAutoTestService)))
                        .then(literal("buildresort").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> ResortAdminCommands.executeBuildResort(
                                        context.getSource(),
                                        generator,
                                        autoCourseService,
                                        practiceCourseStorage,
                                        null,
                                        null
                                ))
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("z", IntegerArgumentType.integer())
                                                .executes(context -> ResortAdminCommands.executeBuildResort(
                                                        context.getSource(),
                                                        generator,
                                                        autoCourseService,
                                                        practiceCourseStorage,
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z")
                                                ))))
                                .then(literal("overwrite").requires(CommandPermissions::canUseAdminCommands)
                                        .requires(CommandPermissions::canUseAdvancedCommands)
                                        .executes(context -> ResortAdminCommands.executeBuildResortOverwrite(
                                                context.getSource(),
                                                generator,
                                                autoCourseService,
                                                practiceCourseStorage
                                        )))
                                .then(literal("cancel").requires(CommandPermissions::canUseAdminCommands)
                                        .requires(CommandPermissions::canUseAdvancedCommands)
                                        .executes(context -> ResortAdminCommands.executeBuildResortCancel(
                                                context.getSource()
                                        ))))
                        .then(literal("resetresort").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> ResortAdminCommands.executeResetResort(
                                        context.getSource()
                                )))
                        .then(literal("removesurroundcourses").requires(CommandPermissions::canUseAdminCommands)
                                .requires(CommandPermissions::canUseAdvancedCommands)
                                .executes(context -> ResortAdminCommands.executeRemoveSurroundCourses(
                                        context.getSource(),
                                        placementService,
                                        practiceCourseStorage
                                )))
                        .then(literal("leaderboard")
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    String courseName = courseManager.getActiveCourse()
                                            .map(Course::name)
                                            .orElse("");
                                    if (courseName.isEmpty()) {
                                        player.sendMessage(Text.literal("No active course."), false);
                                        return 0;
                                    }
                                    McdgMod.handleLeaderboardRequest(player, courseName);
                                    return 1;
                                }))));
    }
    private static int executeHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("MCDG quick help:"), false);
        source.sendFeedback(() -> Text.literal("- New course: /mcdg createcourse <seed> -> /mcdg startround (or /mcdg startround strict)."), false);
        source.sendFeedback(() -> Text.literal("- Generation model is unified across modes: land-first routing with water-carry cap <= 91 blocks (~300 ft)."), false);
        source.sendFeedback(() -> Text.literal("- Saved course: /mcdg listcourses -> /mcdg playcourse <index>."), false);
        source.sendFeedback(() -> Text.literal("- In-round basics: /mcdg joinround, /mcdg endround, /mcdg cleanupcourse."), false);
        source.sendFeedback(() -> Text.literal("- Waypoints: /mcdg waypoint list, /mcdg waypoint tp <central|hole N>."), false);
        source.sendFeedback(() -> Text.literal("- Player sessions: /mcdg savesession [players], /mcdg resumesession [manual|auto] [players]."), false);
        source.sendFeedback(() -> Text.literal("- Persisted round session: /mcdg roundsession status | /mcdg roundsession clear."), false);
        if (CommandPermissions.SHOW_ADVANCED_COMMANDS) {
            source.sendFeedback(() -> Text.literal("- Advanced commands are visible (MCDG_SHOW_ADVANCED_COMMANDS=true)."), false);
        } else {
            source.sendFeedback(() -> Text.literal("- Advanced commands are hidden by default; set MCDG_SHOW_ADVANCED_COMMANDS=true to expose them."), false);
        }
        return 1;
    }
}
