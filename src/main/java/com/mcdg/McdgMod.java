package com.mcdg;

import com.mcdg.command.McdgAdminCommands;
import com.mcdg.command.SkillCommands;
import com.mcdg.config.McdgConfig;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleMapSyncService;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.LeaderboardManager;
import com.mcdg.game.McdgBlocks;
import com.mcdg.game.WindManager;
import com.mcdg.game.McdgEntityTypes;
import com.mcdg.game.McdgBlockEntities;
import com.mcdg.game.McdgItems;
import com.mcdg.game.McdgScreenHandlers;
import com.mcdg.game.PlayerRoundState;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.DiscFlightSimulator;
import com.mcdg.game.ElytraDiscMigration;
import com.mcdg.game.EntityCapper;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.ChallengeCourseDiscoveryHandler;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseCatalog;
import com.mcdg.game.ChallengeCourseBuildTracker;
import com.mcdg.game.BossMobSpawner;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundRespawnHandler;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowSetupSyncHelper;
import com.mcdg.game.PlayerSkillManager;
import com.mcdg.game.RoundInviteManager;
import com.mcdg.net.AceCinematicSync;

import com.mcdg.net.HoleMapSync;
import com.mcdg.net.LeaderboardRequest;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.RoundRunningScoresSync;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.net.NextThrowModifierSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.net.SkillsScreenRequest;
import com.mcdg.net.SkillsScreenSync;
import com.mcdg.net.SkillsStatusSync;
import com.mcdg.net.ThrowPowerLockSync;
import com.mcdg.net.ThrowSetupSync;
import com.mcdg.net.ThrowStanceSync;
import com.mcdg.net.ThrowTrailStartSync;
import com.mcdg.net.ThrowTrailCompleteSync;
import com.mcdg.net.RoundInviteRequest;
import com.mcdg.net.RoundInviteNotification;
import com.mcdg.net.RoundInviteResponse;
import com.mcdg.net.WindSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortWaypointManager;
import com.mcdg.world.ResortChestReplenisher;
import com.mcdg.world.SurfaceResolver;
import com.mcdg.world.ResortCourseBuilder;
import com.mcdg.world.WorldSpawnHandler;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.SeededCourseGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McdgMod implements ModInitializer {
    public static final String MOD_ID = "mcdg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String AUTOTEST_ENV = "MCDG_AUTOTEST";
    private static final String AUTOTEST_BASE_SEED_ENV = "MCDG_AUTOTEST_BASE_SEED";
    private static final String AUTO_STRICT_SETUP_ENV = "MCDG_AUTO_STRICT_SETUP";
    private static final int AUTO_STRICT_SETUP_MAX_WAIT_TICKS = 20 * 120;
    private static final int ROUND_SESSION_AUTOSAVE_INTERVAL_TICKS = 600;
    private static final long TICK_HANDLER_WARNING_THRESHOLD_MS = 10L;
    private static final ExecutorService AUTOSAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MCDG-Autosave");
        t.setDaemon(true);
        return t;
    });

    public static ExecutorService autosaveExecutor() {
        return AUTOSAVE_EXECUTOR;
    }
    
    static {
        // Add shutdown hook to ensure executor is cleaned up even if normal shutdown fails
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                AUTOSAVE_EXECUTOR.shutdown();
                if (!AUTOSAVE_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warn("Autosave executor did not terminate gracefully in shutdown hook, forcing shutdown");
                    AUTOSAVE_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for autosave executor shutdown in shutdown hook", e);
                AUTOSAVE_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.error("Unexpected exception during autosave executor shutdown in shutdown hook", e);
                AUTOSAVE_EXECUTOR.shutdownNow();
            }
        }, "MCDG-Autosave-ShutdownHook"));
    }

    private static final CourseGenerator COURSE_GENERATOR = new SeededCourseGenerator();
    private static final ActiveCourseManager ACTIVE_COURSE_MANAGER = new ActiveCourseManager();
    private static final CoursePlacementService COURSE_PLACEMENT_SERVICE = new CoursePlacementService();
    private static final CoursePlacementValidator COURSE_PLACEMENT_VALIDATOR = new CoursePlacementValidator();
    private static final RoundStateManager ROUND_STATE_MANAGER = new RoundStateManager();
    private static final TournamentRulesetManager TOURNAMENT_RULESET_MANAGER = new TournamentRulesetManager();
    private static final LeaderboardManager LEADERBOARD_MANAGER = new LeaderboardManager();

    public static TournamentRulesetManager getRulesetManager() {
        return TOURNAMENT_RULESET_MANAGER;
    }

    public static RoundStateManager getRoundStateManager() {
        return ROUND_STATE_MANAGER;
    }
    private static final RoundPresentationService ROUND_PRESENTATION_SERVICE = new RoundPresentationService();
    private static final PracticeCourseStorage PRACTICE_COURSE_STORAGE = new PracticeCourseStorage();
    private static final RoundSessionStorage ROUND_SESSION_STORAGE = new RoundSessionStorage();
    private static final PlayerRoundSessionStorage PLAYER_ROUND_SESSION_STORAGE = new PlayerRoundSessionStorage();
    private static final BuildCourseSessionManager BUILD_COURSE_SESSION_MANAGER = new BuildCourseSessionManager(
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            PRACTICE_COURSE_STORAGE
    );
    private static final com.mcdg.game.AutoCourseService AUTO_COURSE_SERVICE = new com.mcdg.game.AutoCourseService(
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            COURSE_GENERATOR,
            PRACTICE_COURSE_STORAGE,
            ACTIVE_COURSE_MANAGER
    );
    private static final PlacementAutoTestService PLACEMENT_AUTO_TEST_SERVICE = new PlacementAutoTestService(
            COURSE_GENERATOR,
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            AUTO_COURSE_SERVICE
    );
    private static final RoundInviteManager ROUND_INVITE_MANAGER = new RoundInviteManager(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            PRACTICE_COURSE_STORAGE,
            ROUND_PRESENTATION_SERVICE
    );
        private static Long pendingAutoStrictSetupSeed;
        private static int pendingAutoStrictSetupWaitTicks;
        private static int roundSessionAutosaveTicks;
        private static volatile String lastRoundSessionSignature = "";
        private static volatile String lastPracticeCourseSignature = "";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(LeaderboardRequest.ID, LeaderboardRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(SkillsScreenRequest.ID, SkillsScreenRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(ThrowPowerLockSync.ID, ThrowPowerLockSync.CODEC);
        PayloadTypeRegistry.playC2S().register(ThrowStanceSync.ID, ThrowStanceSync.CODEC);
        PayloadTypeRegistry.playS2C().register(AceCinematicSync.ID, AceCinematicSync.CODEC);
        PayloadTypeRegistry.playS2C().register(HoleMapSync.ID, HoleMapSync.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundRunningScoresSync.ID, RoundRunningScoresSync.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundCompleteCinematicSync.ID, RoundCompleteCinematicSync.CODEC);
        PayloadTypeRegistry.playS2C().register(MenuScreenSync.ID, MenuScreenSync.CODEC);
        PayloadTypeRegistry.playS2C().register(LeaderboardResponse.ID, LeaderboardResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(SkillsScreenSync.ID, SkillsScreenSync.CODEC);
        PayloadTypeRegistry.playS2C().register(SkillsStatusSync.ID, SkillsStatusSync.CODEC);

        PayloadTypeRegistry.playS2C().register(ThrowPowerLockSync.ID, ThrowPowerLockSync.CODEC);
        PayloadTypeRegistry.playS2C().register(ThrowSetupSync.ID, ThrowSetupSync.CODEC);
        PayloadTypeRegistry.playS2C().register(ThrowTrailStartSync.ID, ThrowTrailStartSync.CODEC);
        PayloadTypeRegistry.playS2C().register(ThrowTrailCompleteSync.ID, ThrowTrailCompleteSync.CODEC);

        PayloadTypeRegistry.playC2S().register(RoundInviteRequest.ID, RoundInviteRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundInviteNotification.ID, RoundInviteNotification.CODEC);
        PayloadTypeRegistry.playC2S().register(RoundInviteResponse.ID, RoundInviteResponse.CODEC);

        PayloadTypeRegistry.playS2C().register(WindSync.ID, WindSync.CODEC);
        PayloadTypeRegistry.playS2C().register(NextThrowModifierSync.ID, NextThrowModifierSync.CODEC);

        ResourceManagerHelper.registerBuiltinResourcePack(
                new Identifier(MOD_ID, "mcdg-test-resources"),
                FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(),
                Text.literal("MCDG Test Resources"),
                ResourcePackActivationType.DEFAULT_ENABLED
        );
        ServerPlayNetworking.registerGlobalReceiver(LeaderboardRequest.ID, (payload, context) ->
            context.server().execute(() -> handleLeaderboardRequest(context.player(), payload.courseName()))
        );
        ServerPlayNetworking.registerGlobalReceiver(SkillsScreenRequest.ID, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                if (player != null) {
                    ServerPlayNetworking.send(player, PlayerSkillManager.createSkillsScreenPayload(player));
                }
            })
        );
        ServerPlayNetworking.registerGlobalReceiver(ThrowPowerLockSync.ID, (payload, context) ->
                context.server().execute(() -> {
                    // Store server-side power lock state for use during throw
                    ChargedDiscItem.setServerPowerLocked(context.player().getUuid(), payload.locked(), payload.lockedChargePercent());
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(ThrowStanceSync.ID, (payload, context) ->
                context.server().execute(() -> {
                    // Store server-side stance for use during throw
                    ChargedDiscItem.setServerStance(context.player().getUuid(), payload.stance(), payload.angle());
                    McdgMod.LOGGER.info("Server received stance sync: player={} stance={} angle={}", context.player().getUuid(), payload.stance(), payload.angle());
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(RoundInviteRequest.ID, (payload, context) ->
                context.server().execute(() -> ROUND_INVITE_MANAGER.handleInviteRequest(
                        context.server(),
                        context.player(),
                        payload.targetPlayerIds(),
                        payload.catalogIndex(),
                        payload.courseId(),
                        payload.isChallengeCourse()
                ))
        );
        ServerPlayNetworking.registerGlobalReceiver(RoundInviteResponse.ID, (payload, context) ->
                context.server().execute(() -> ROUND_INVITE_MANAGER.handleInviteResponse(
                        context.server(),
                        context.player(),
                        payload.initiatorId(),
                        payload.accepted()
                ))
        );
        McdgConfig config = McdgConfig.loadDefault();
        
        // Initialize wind manager with configuration
        WindManager.initialize(config.enableWindSystem(), config.defaultWindSpeed(), config.windUpdateIntervalTicks());
        RoundWindService.initialize(config.roundWindMode());

        McdgItems.register(ACTIVE_COURSE_MANAGER, ROUND_STATE_MANAGER, TOURNAMENT_RULESET_MANAGER, config.enableStrictFlowDebug());
        McdgBlocks.register();
        McdgBlockEntities.register();
        McdgScreenHandlers.register();
        McdgEntityTypes.register();
        McdgAdminCommands.register(
            COURSE_GENERATOR,
            ACTIVE_COURSE_MANAGER,
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            PLACEMENT_AUTO_TEST_SERVICE,
            ROUND_STATE_MANAGER,
            ROUND_PRESENTATION_SERVICE,
            config.skipRoundPresentation(),
            TOURNAMENT_RULESET_MANAGER,
            PRACTICE_COURSE_STORAGE,
            ROUND_SESSION_STORAGE,
            PLAYER_ROUND_SESSION_STORAGE,
            BUILD_COURSE_SESSION_MANAGER,
            AUTO_COURSE_SERVICE
        );
        SkillCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            PLACEMENT_AUTO_TEST_SERVICE.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("PLACEMENT_AUTO_TEST_SERVICE tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            ROUND_PRESENTATION_SERVICE.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("ROUND_PRESENTATION_SERVICE tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            BUILD_COURSE_SESSION_MANAGER.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("BUILD_COURSE_SESSION_MANAGER tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            AUTO_COURSE_SERVICE.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("AUTO_COURSE_SERVICE tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            ROUND_INVITE_MANAGER.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("ROUND_INVITE_MANAGER tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            handlePendingAutoStrictSetup(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("handlePendingAutoStrictSetup tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            autosaveRoundSession(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("autosaveRoundSession tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            PlayerSkillManager.tickAutosave(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("PlayerSkillManager.tickAutosave took {}ms", elapsedMs);
            }
        });
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            ResortCourseBuilder.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("ResortCourseBuilder tick took {}ms", elapsedMs);
            }
        });
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            ChallengeCourseBuildTracker.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("ChallengeCourseBuildTracker tick took {}ms", elapsedMs);
            }
        });
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            BossMobSpawner.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("BossMobSpawner tick took {}ms", elapsedMs);
            }
        });
		ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            DiscFlightSimulator.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("DiscFlightSimulator tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            EntityCapper.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("EntityCapper tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            WindManager.tick(server);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("WindManager tick took {}ms", elapsedMs);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long start = System.nanoTime();
            // Sync effective throw multipliers every 20 ticks for active players
            if (server.getTicks() % 20 == 0) {
                Set<UUID> activeParticipants = ACTIVE_COURSE_MANAGER.getActiveParticipantIds();
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (activeParticipants.contains(player.getUuid())) {
                        ThrowSetupSyncHelper.syncSetupMultipliers(player);
                    }
                }
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                LOGGER.warn("ThrowSetupSyncHelper tick took {}ms", elapsedMs);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(ElytraDiscMigration::run);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ResortWaypointManager.clearResortWaypoint());
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedPracticeCourse);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedRoundSession);
        ServerLifecycleEvents.SERVER_STARTED.register(BUILD_COURSE_SESSION_MANAGER::load);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartHeadlessAutoTest);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartAutoStrictSetup);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LEADERBOARD_MANAGER.load(server));
        ServerLifecycleEvents.SERVER_STARTED.register(PlayerSkillManager::load);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WorldSpawnHandler.onServerStarted(server, AUTO_COURSE_SERVICE, PRACTICE_COURSE_STORAGE));
        // Warm storage caches after full startup (including WorldSpawnHandler) so the
        // first G key press after any server restart is instant for all players.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> PRACTICE_COURSE_STORAGE.listReusable(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(McdgMod::flushRoundSessionOnShutdown);
        ServerLifecycleEvents.SERVER_STOPPING.register(BUILD_COURSE_SESSION_MANAGER::save);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LEADERBOARD_MANAGER.save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerSkillManager::save);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResortWaypointManager.clearResortWaypoint());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResortCourseBuilder.reset());
        ResortChestReplenisher.registerInteractionHandler();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResortChestReplenisher.clear());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ChallengeCourseManager.getCatalog().ifPresent(catalog -> catalog.save(server));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BossMobSpawner.stopAll());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                restoreRoundParticipantOnJoin(handler.player, server);
                ResortCourseBuilder.onPlayerJoin(handler.player);
                // Warm storage caches on join so the first G key press is instant.
                PRACTICE_COURSE_STORAGE.listReusable(server);
                PLAYER_ROUND_SESSION_STORAGE.loadPlayer(server, handler.player.getUuid(), null);
                PlayerSkillManager.onPlayerJoin(handler.player);
            })
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            server.execute(() -> {
                UUID playerUuid = handler.player.getUuid();
                HoleMapSyncService.onPlayerDisconnect(playerUuid);
                handlePlayerDisconnectDuringWarmup(playerUuid, server);
                PlayerSkillManager.onPlayerDisconnect(handler.player);
                ChargedDiscItem.clearServerState(playerUuid);
                ThrowSetupSyncHelper.clearPlayerState(playerUuid);
            })
        );
        HoleProgressTracker.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            LEADERBOARD_MANAGER,
            config.enableHudScoringDebug(),
            config.enableStrictFlowDebug(),
            config.enableSurvivalRewards()
        );
        RoundRespawnHandler.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.respawnPenaltyStrokes()
        );
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                world.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
            }
            
            // Load or create challenge course catalog
            ChallengeCourseCatalog catalog = ChallengeCourseCatalog.load(server)
                .orElseGet(ChallengeCourseCatalog::new);
            ChallengeCourseManager.initialize(catalog);
            LOGGER.info("Challenge course catalog initialized with {} courses", 
                catalog.getAllCourses().size());
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            if (world.getBlockEntity(hitResult.getBlockPos()) instanceof SignBlockEntity sign) {
                SignText front = sign.getFrontText();
                Text line0 = front.getMessage(0, false);
                Text line1 = front.getMessage(1, false);
                if (line0 != null && line0.getString().equals("[Leaderboard]") && line1 != null) {
                    String courseName = line1.getString();
                    handleLeaderboardRequest(serverPlayer, courseName);
                    return ActionResult.SUCCESS;
                }
            }
            // Handle challenge course discovery
            ChallengeCourseDiscoveryHandler.onBlockInteract(player, hitResult.getBlockPos(), world.getBlockState(hitResult.getBlockPos()));
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            ChallengeCourseDiscoveryHandler.onBlockBreak(player, pos, state);
        });

        LOGGER.info("Initialized {} (defaultHoles={}, protection={}, hudScoringDebug={}, strictFlowDebug={}, skipRoundPresentation={}, rulesetDefault={}, strictRespawnPenaltyStrokes={}, survivalRewards={}, productionMode={})",
                MOD_ID,
                config.defaultHoleCount(),
            config.enforceCourseProtection(),
            config.enableHudScoringDebug(),
            config.enableStrictFlowDebug(),
            config.skipRoundPresentation(),
            TOURNAMENT_RULESET_MANAGER.getActiveRuleset().name().toLowerCase(),
            config.respawnPenaltyStrokes(),
            config.enableSurvivalRewards(),
            config.productionMode());
    }

    private static void maybeStartHeadlessAutoTest(net.minecraft.server.MinecraftServer server) {
        String value = System.getenv(AUTOTEST_ENV);
        if (value == null || value.isBlank()) {
            return;
        }

        int runs = 8;
        int holes = 9;
        String normalized = value.trim().replace(':', ',').replace(' ', ',');
        String[] parts = normalized.split(",");
        try {
            if (parts.length >= 1 && !parts[0].isBlank()) {
                runs = Integer.parseInt(parts[0].trim());
            }
            if (parts.length >= 2 && !parts[1].isBlank()) {
                holes = Integer.parseInt(parts[1].trim());
            }
        } catch (NumberFormatException ex) {
            LOGGER.error("Invalid {} value '{}'. Expected 'runs,holes' (for example: 25,9).", AUTOTEST_ENV, value);
            return;
        }

        final int safeRuns = Math.max(1, Math.min(200, runs));
        final int safeHoles = Math.max(1, Math.min(18, holes));
        Long baseSeedOverride = null;
        String baseSeedValue = System.getenv(AUTOTEST_BASE_SEED_ENV);
        if (baseSeedValue != null && !baseSeedValue.isBlank()) {
            try {
                baseSeedOverride = Long.parseLong(baseSeedValue.trim());
            } catch (NumberFormatException ex) {
                LOGGER.error("Invalid {} value '{}'. Expected a numeric seed.", AUTOTEST_BASE_SEED_ENV, baseSeedValue);
                return;
            }
        }

        if (baseSeedOverride != null) {
            LOGGER.info(
                    "Headless autotest requested via {}: runs={}, holes={}, baseSeed={} (from {})",
                    AUTOTEST_ENV,
                    safeRuns,
                    safeHoles,
                    baseSeedOverride,
                    AUTOTEST_BASE_SEED_ENV
            );
        } else {
            LOGGER.info("Headless autotest requested via {}: runs={}, holes={}", AUTOTEST_ENV, safeRuns, safeHoles);
        }

        final Long finalBaseSeedOverride = baseSeedOverride;
        server.execute(() -> {
            int started = PLACEMENT_AUTO_TEST_SERVICE.start(server.getCommandSource(), safeRuns, safeHoles, finalBaseSeedOverride);
            if (started == 0) {
                LOGGER.error("Headless autotest did not start.");
            }
        });
    }

    private static void maybeStartAutoStrictSetup(net.minecraft.server.MinecraftServer server) {
        String value = System.getenv(AUTO_STRICT_SETUP_ENV);
        if (value == null || value.isBlank()) {
            return;
        }

        long seed;
        try {
            seed = Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            LOGGER.error("Invalid {} value '{}'. Expected a numeric seed.", AUTO_STRICT_SETUP_ENV, value);
            return;
        }

        LOGGER.info("Auto strict setup requested via {}: seed={}", AUTO_STRICT_SETUP_ENV, seed);
        pendingAutoStrictSetupSeed = seed;
        pendingAutoStrictSetupWaitTicks = 0;
    }

    private static void handlePendingAutoStrictSetup(net.minecraft.server.MinecraftServer server) {
        Long pendingSeed = pendingAutoStrictSetupSeed;
        if (pendingSeed == null) {
            return;
        }

        if (server.getPlayerManager().getPlayerList().isEmpty()) {
            pendingAutoStrictSetupWaitTicks++;
            if (pendingAutoStrictSetupWaitTicks % 100 == 0) {
                LOGGER.info("Auto strict setup waiting for player join... {} ticks", pendingAutoStrictSetupWaitTicks);
            }
            if (pendingAutoStrictSetupWaitTicks >= AUTO_STRICT_SETUP_MAX_WAIT_TICKS) {
                LOGGER.error("Auto strict setup timed out waiting for a player to join.");
                pendingAutoStrictSetupSeed = null;
                pendingAutoStrictSetupWaitTicks = 0;
            }
            return;
        }

        pendingAutoStrictSetupSeed = null;
        pendingAutoStrictSetupWaitTicks = 0;

        var commandManager = server.getCommandManager();
        var source = server.getCommandSource();

        commandManager.executeWithPrefix(source, "mcdg createcourse " + pendingSeed);
        if (ACTIVE_COURSE_MANAGER.getActiveCourse().isEmpty()) {
            LOGGER.error("Auto strict setup failed while creating course.");
            return;
        }

        commandManager.executeWithPrefix(source, "mcdg ruleset strict");
        if (!TOURNAMENT_RULESET_MANAGER.isStrict()) {
            LOGGER.error("Auto strict setup failed while enabling strict rules.");
            return;
        }

        commandManager.executeWithPrefix(source, "mcdg startround");
        if (!ACTIVE_COURSE_MANAGER.isRoundActive()) {
            LOGGER.error("Auto strict setup failed while starting round.");
            return;
        }

        LOGGER.info("Auto strict setup complete: course created, strict mode enabled, round started.");
    }

    private static void loadPersistedPracticeCourse(net.minecraft.server.MinecraftServer server) {
        PRACTICE_COURSE_STORAGE.load(server).ifPresent(snapshot -> {
            ACTIVE_COURSE_MANAGER.setActiveCourse(snapshot.course());
            // Restore catalog index so the auto-save path in sendMenuScreen() is not
            // triggered on the first G key press after a server restart.
            int catalogIndex = PRACTICE_COURSE_STORAGE.findCatalogIndex(
                    server, snapshot.course(), snapshot.placedCourseState());
            ACTIVE_COURSE_MANAGER.setActiveCourseCatalogIndex(catalogIndex > 0 ? catalogIndex : null);
            ACTIVE_COURSE_MANAGER.setPlacedCourseState(snapshot.placedCourseState());
            ACTIVE_COURSE_MANAGER.setPersistentPlacedCourse(true);
            ACTIVE_COURSE_MANAGER.setLegacyPracticeSnapshot(snapshot.legacyFormat());
            ACTIVE_COURSE_MANAGER.setRoundActive(false);

            ServerWorld courseWorld = server.getWorld(snapshot.placedCourseState().worldKey());
            if (courseWorld != null) {
                // mobGriefing is already set to false on server start.
            }

            // Initialize the practice course signature to avoid unnecessary saves on first autosave
            if (snapshot.placedCourseState() != null) {
                lastPracticeCourseSignature = buildPracticeCourseSignature(snapshot.course(), snapshot.placedCourseState());
            }

            LOGGER.info(
                    "Loaded persisted practice course '{}' with {} holes.",
                    snapshot.course().name(),
                    snapshot.course().holes().size()
            );

            if (snapshot.legacyFormat()) {
                LOGGER.warn(
                        "Loaded legacy practice snapshot format. Rebuild and save a fresh practice course to refresh persisted tee/basket coordinates."
                );
            }
        });
    }

    private static void loadPersistedRoundSession(MinecraftServer server) {
        ROUND_SESSION_STORAGE.load(server, LOGGER).ifPresent(snapshot -> {
            var course = ACTIVE_COURSE_MANAGER.getActiveCourse().orElse(null);
            var placed = ACTIVE_COURSE_MANAGER.getPlacedCourseState().orElse(null);

            if (course == null || placed == null) {
                LOGGER.warn("Discarding persisted round session: no persisted course is available.");
                ROUND_SESSION_STORAGE.clear(server, LOGGER);
                return;
            }

            String placedWorldKey = placed.worldKey().getValue().toString();
            if (!Objects.equals(placedWorldKey, snapshot.worldKey())) {
                LOGGER.warn("Discarding persisted round session: world mismatch (snapshot={}, current={}).", snapshot.worldKey(), placedWorldKey);
                ROUND_SESSION_STORAGE.clear(server, LOGGER);
                return;
            }

            if (snapshot.courseSeed() != course.seed()) {
                LOGGER.warn("Discarding persisted round session: seed mismatch (snapshot={}, current={}).", snapshot.courseSeed(), course.seed());
                ROUND_SESSION_STORAGE.clear(server, LOGGER);
                return;
            }

            if (snapshot.holeCount() != course.holes().size()) {
                LOGGER.warn("Discarding persisted round session: hole count mismatch (snapshot={}, current={}).", snapshot.holeCount(), course.holes().size());
                ROUND_SESSION_STORAGE.clear(server, LOGGER);
                return;
            }

            ROUND_STATE_MANAGER.restoreSnapshot(snapshot.playerStates(), snapshot.completedTotals());
            ACTIVE_COURSE_MANAGER.setActiveParticipantIds(snapshot.participantIds());
            ACTIVE_COURSE_MANAGER.setRoundActive(snapshot.roundActive());

            LOGGER.info(
                    "Restored persisted round session with {} participants and {} live player states.",
                    snapshot.participantIds().size(),
                    snapshot.playerStates().size()
            );
        });

        // Initialize signatures after loading to avoid unnecessary saves on first autosave
        lastRoundSessionSignature = buildRoundSessionSignature();
        roundSessionAutosaveTicks = 0;
        
        // Also initialize practice course signature if a course is loaded
        var loadedCourse = ACTIVE_COURSE_MANAGER.getActiveCourse().orElse(null);
        var loadedPlaced = ACTIVE_COURSE_MANAGER.getPlacedCourseState().orElse(null);
        if (loadedCourse != null && loadedPlaced != null) {
            lastPracticeCourseSignature = buildPracticeCourseSignature(loadedCourse, loadedPlaced);
        } else {
            // Initialize to empty string if no course loaded
            lastPracticeCourseSignature = "";
        }
    }

    private static void autosaveRoundSession(MinecraftServer server) {
        if (!ACTIVE_COURSE_MANAGER.isRoundActive()) {
            return;
        }
        roundSessionAutosaveTicks++;
        if (roundSessionAutosaveTicks < ROUND_SESSION_AUTOSAVE_INTERVAL_TICKS) {
            return;
        }
        roundSessionAutosaveTicks = 0;
        
        // Submit async save task to avoid blocking server tick
        if (AUTOSAVE_EXECUTOR.isShutdown()) {
            return;
        }
        AUTOSAVE_EXECUTOR.submit(() -> {
            long start = System.nanoTime();
            try {
                persistRoundSession(server, false);
                long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
                if (elapsedMs > TICK_HANDLER_WARNING_THRESHOLD_MS) {
                    LOGGER.warn("Async autosave took {}ms", elapsedMs);
                }
            } catch (RuntimeException ex) {
                LOGGER.error("Async autosave failed due to runtime exception", ex);
            } catch (Exception ex) {
                LOGGER.error("Async autosave failed due to unexpected exception", ex);
            }
        });
    }

    private static void flushRoundSessionOnShutdown(MinecraftServer server) {
        // Run synchronously during shutdown to ensure data is saved before exit
        persistRoundSession(server, true);
        
        // Shutdown the executor gracefully (may already be shut down by shutdown hook)
        if (!AUTOSAVE_EXECUTOR.isShutdown()) {
            try {
                AUTOSAVE_EXECUTOR.shutdown();
                if (!AUTOSAVE_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("Autosave executor did not terminate gracefully, forcing shutdown");
                    AUTOSAVE_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException ex) {
                LOGGER.warn("Interrupted while waiting for autosave executor shutdown", ex);
                AUTOSAVE_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                LOGGER.error("Unexpected exception during autosave executor shutdown", ex);
                AUTOSAVE_EXECUTOR.shutdownNow();
            }
        }
    }

    private static void persistRoundSession(MinecraftServer server, boolean force) {
        String signature = buildRoundSessionSignature();
        if (!force && Objects.equals(signature, lastRoundSessionSignature)) {
            return;
        }

        if (ACTIVE_COURSE_MANAGER.isRoundActive()) {
            var course = ACTIVE_COURSE_MANAGER.getActiveCourse().orElse(null);
            var placed = ACTIVE_COURSE_MANAGER.getPlacedCourseState().orElse(null);
            if (course != null && placed != null) {
                // Keep the latest playable layout available so persisted round sessions can restore after restart.
                String practiceSignature = buildPracticeCourseSignature(course, placed);
                if (force || !Objects.equals(practiceSignature, lastPracticeCourseSignature)) {
                    PRACTICE_COURSE_STORAGE.save(server, course, placed);
                    lastPracticeCourseSignature = practiceSignature;
                }
            }
        }

        ROUND_SESSION_STORAGE.save(server, ACTIVE_COURSE_MANAGER, ROUND_STATE_MANAGER, LOGGER);
        lastRoundSessionSignature = signature;
    }

    private static String buildRoundSessionSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append("active=").append(ACTIVE_COURSE_MANAGER.isRoundActive());

        ACTIVE_COURSE_MANAGER.getPlacedCourseState().ifPresent(placed ->
                builder.append("|world=").append(placed.worldKey().getValue())
        );
        ACTIVE_COURSE_MANAGER.getActiveCourse().ifPresent(course -> {
            builder.append("|seed=").append(course.seed());
            builder.append("|holes=").append(course.holes().size());
        });

        List<String> participantIds = new ArrayList<>();
        for (UUID participantId : ACTIVE_COURSE_MANAGER.getActiveParticipantIds()) {
            if (participantId != null) {
                participantIds.add(participantId.toString());
            }
        }
        Collections.sort(participantIds);
        builder.append("|participants=").append(String.join(",", participantIds));

        Map<UUID, PlayerRoundState> states = ROUND_STATE_MANAGER.snapshotStates();
        List<String> stateTokens = new ArrayList<>();
        for (Map.Entry<UUID, PlayerRoundState> entry : states.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerRoundState state = entry.getValue();
            if (playerId == null || state == null) {
                continue;
            }
            BlockPos lie = state.lie();
            stateTokens.add(
                    playerId
                            + ":h" + state.currentHole()
                            + ":hs" + state.holeStrokes()
                            + ":ts" + state.totalStrokes()
                            + ":lp" + state.lastThrowPenalty()
                            + ":x" + lie.getX()
                            + ":y" + lie.getY()
                            + ":z" + lie.getZ()
            );
        }
        Collections.sort(stateTokens);
        builder.append("|states=").append(String.join(",", stateTokens));

        Map<UUID, Integer> completedTotals = ROUND_STATE_MANAGER.snapshotCompletedRounds();
        List<String> completedTokens = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : completedTotals.entrySet()) {
            UUID playerId = entry.getKey();
            Integer total = entry.getValue();
            if (playerId != null && total != null) {
                completedTokens.add(playerId + ":" + total);
            }
        }
        Collections.sort(completedTokens);
        builder.append("|completed=").append(String.join(",", completedTokens));

        return builder.toString();
    }

    private static String buildPracticeCourseSignature(Course course, PlacedCourseState placed) {
        StringBuilder builder = new StringBuilder();
        builder.append("seed=").append(course.seed());
        builder.append("|holes=").append(course.holes().size());
        builder.append("|world=").append(placed.worldKey().getValue());
        builder.append("|tees=").append(placed.holeTees().size());
        builder.append("|baskets=").append(placed.holeBaskets().size());
        
        // Include hash of tee and basket positions to detect layout changes
        // Using hash codes is more efficient than full position strings
        int teeHash = placed.holeTees().hashCode();
        int basketHash = placed.holeBaskets().hashCode();
        builder.append("|teeHash=").append(teeHash);
        builder.append("|basketHash=").append(basketHash);
        
        return builder.toString();
    }

    private static void restoreRoundParticipantOnJoin(ServerPlayerEntity player, net.minecraft.server.MinecraftServer server) {
        if (!ACTIVE_COURSE_MANAGER.isRoundActive()) {
            return;
        }

        if (!ACTIVE_COURSE_MANAGER.getActiveParticipantIds().contains(player.getUuid())) {
            return;
        }

        var placed = ACTIVE_COURSE_MANAGER.getPlacedCourseState().orElse(null);
        if (placed == null) {
            return;
        }

        ServerWorld world = server.getWorld(placed.worldKey());
        if (world == null) {
            return;
        }

        BlockPos targetLie = null;
        var existingState = ROUND_STATE_MANAGER.getState(player.getUuid()).orElse(null);
        if (existingState != null) {
            targetLie = existingState.lie();
        } else {
            BlockPos firstTee = placed.holeTees().get(1);
            if (firstTee != null) {
                targetLie = resolveSafeFeetNearWithin(world, firstTee, 2);
                ROUND_STATE_MANAGER.startRoundForPlayer(player, targetLie);
            }
        }
    }

    private static void handlePlayerDisconnectDuringWarmup(UUID playerId, net.minecraft.server.MinecraftServer server) {
        if (!ACTIVE_COURSE_MANAGER.isWarmupActive()) {
            return;
        }

        if (!ACTIVE_COURSE_MANAGER.getActiveParticipantIds().contains(playerId)) {
            return;
        }

        ACTIVE_COURSE_MANAGER.removeActiveParticipantId(playerId);
        ROUND_STATE_MANAGER.clearPlayer(playerId);

        // Notify remaining participants
        for (UUID participantId : ACTIVE_COURSE_MANAGER.getActiveParticipantIds()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(participantId);
            if (player != null) {
                player.sendMessage(Text.literal("A player left during warmup."), true);
            }
        }

        // If no participants remain, cancel warmup
        if (ACTIVE_COURSE_MANAGER.getActiveParticipantIds().isEmpty()) {
            ACTIVE_COURSE_MANAGER.setWarmupActive(false);
            ACTIVE_COURSE_MANAGER.clear();
            ROUND_STATE_MANAGER.clearAll();
            LOGGER.info("Warmup cancelled: all players left during warmup.");
        }
    }

    public static void handleLeaderboardRequest(ServerPlayerEntity player, String courseName) {
        if (player == null || courseName == null || courseName.isBlank()) {
            return;
        }

        int totalPar = 0;
        List<LeaderboardResponse.Entry> responseEntries = new ArrayList<>();

        // Check if this is a challenge course first
        var challengeCatalog = ChallengeCourseManager.getCatalog();
        if (challengeCatalog.isPresent()) {
            var challengeEntry = challengeCatalog.get().getCourseByName(courseName);
            if (challengeEntry.isPresent()) {
                // Use challenge course completion data from ChallengeCourseCatalog
                List<ChallengeCourseCatalog.CompletionEntry> completions =
                    challengeCatalog.get().getCompletionEntries(challengeEntry.get().courseId());

                for (ChallengeCourseCatalog.CompletionEntry entry : completions) {
                    responseEntries.add(new LeaderboardResponse.Entry(entry.playerName(), entry.score()));
                }

                // Calculate total par from the generated course
                if (challengeEntry.get().generatedCourse() != null) {
                    for (Hole hole : challengeEntry.get().generatedCourse().holes()) {
                        totalPar += hole.par();
                    }
                }
                ServerPlayNetworking.send(player, LeaderboardResponse.Payload.active(courseName, totalPar, responseEntries));
                return;
            }
        }

        // Regular course: calculate par from active course if available
        Course activeCourse = ACTIVE_COURSE_MANAGER.getActiveCourse().orElse(null);
        if (activeCourse != null && activeCourse.name().equalsIgnoreCase(courseName)) {
            for (Hole hole : activeCourse.holes()) {
                totalPar += hole.par();
            }
        }

        // Use regular leaderboard data
        responseEntries.addAll(getRegularLeaderboardEntries(courseName));

        ServerPlayNetworking.send(player, LeaderboardResponse.Payload.active(courseName, totalPar, responseEntries));
    }

    private static List<LeaderboardResponse.Entry> getRegularLeaderboardEntries(String courseName) {
        List<LeaderboardResponse.Entry> entries = new ArrayList<>();
        List<LeaderboardManager.LeaderboardEntry> topEntries = LEADERBOARD_MANAGER.getTopScores(courseName, 5);
        for (LeaderboardManager.LeaderboardEntry entry : topEntries) {
            entries.add(new LeaderboardResponse.Entry(entry.playerName(), entry.score()));
        }
        return entries;
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
                    BlockPos candidate = preferredFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 4; dy++) {
                        BlockPos up = candidate.up(dy);
                        if (isStandableFeet(world, up)) {
                            return up;
                        }
                        BlockPos down = candidate.down(dy);
                        if (isStandableFeet(world, down)) {
                            return down;
                        }
                    }
                }
            }
        }

        return preferredFeet;
    }

    private static BlockPos resolveSafeFeetNearWithin(ServerWorld world, BlockPos preferredFeet, int maxRadius) {
        int safeRadius = Math.max(0, maxRadius);
        if (isStandableFeet(world, preferredFeet)) {
            return preferredFeet;
        }

        for (int radius = 1; radius <= safeRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > (safeRadius * safeRadius)) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos candidate = preferredFeet.add(dx, dy, dz);
                        if (isStandableFeet(world, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return preferredFeet;
    }

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        var feetState = world.getBlockState(feet);
        var headState = world.getBlockState(feet.up());
        if (!feetState.getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        if (!headState.getCollisionShape(world, feet.up()).isEmpty()) {
            return false;
        }

        BlockPos below = feet.down();
        var belowState = world.getBlockState(below);
        if (belowState.isAir()) {
            return false;
        }

        var belowShape = belowState.getCollisionShape(world, below);
        return !belowShape.isEmpty();
    }

}
