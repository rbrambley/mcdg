package com.mcdg;

import com.mcdg.command.McdgAdminCommands;
import com.mcdg.config.McdgConfig;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.LeaderboardManager;
import com.mcdg.game.McdgItems;
import com.mcdg.game.PlayerRoundState;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.game.ChargedDiscItem;
import com.mcdg.game.DiscFlightSimulator;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.CourseFireProtection;
import com.mcdg.game.RoundRespawnHandler;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.net.AceCinematicSync;

import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.LeaderboardRequest;
import com.mcdg.net.LeaderboardResponse;
import com.mcdg.net.WaypointSync;
import com.mcdg.net.RoundRunningScoresSync;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.net.WaypointTeleportSync;
import com.mcdg.net.WaypointRemovedSync;
import com.mcdg.net.ThrowPowerLockSync;
import com.mcdg.net.ThrowStanceSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortWaypointManager;
import com.mcdg.world.SurfaceResolver;
import com.mcdg.world.ResortCourseBuilder;
import com.mcdg.world.WorldSpawnHandler;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.SeededCourseGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McdgMod implements ModInitializer {
    public static final String MOD_ID = "mcdg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String AUTOTEST_ENV = "MCDG_AUTOTEST";
    private static final String AUTOTEST_BASE_SEED_ENV = "MCDG_AUTOTEST_BASE_SEED";
    private static final String AUTO_STRICT_SETUP_ENV = "MCDG_AUTO_STRICT_SETUP";
    private static final int AUTO_STRICT_SETUP_MAX_WAIT_TICKS = 20 * 120;
    private static final int ROUND_SESSION_AUTOSAVE_INTERVAL_TICKS = 20;

    private static final CourseGenerator COURSE_GENERATOR = new SeededCourseGenerator();
    private static final ActiveCourseManager ACTIVE_COURSE_MANAGER = new ActiveCourseManager();
    private static final CoursePlacementService COURSE_PLACEMENT_SERVICE = new CoursePlacementService();
    private static final CoursePlacementValidator COURSE_PLACEMENT_VALIDATOR = new CoursePlacementValidator();
    private static final RoundStateManager ROUND_STATE_MANAGER = new RoundStateManager();
    private static final TournamentRulesetManager TOURNAMENT_RULESET_MANAGER = new TournamentRulesetManager();
    private static final LeaderboardManager LEADERBOARD_MANAGER = new LeaderboardManager();
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
            PRACTICE_COURSE_STORAGE
    );
        private static final ThrowAutoTestService THROW_AUTO_TEST_SERVICE = new ThrowAutoTestService(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER
        );
    private static final PlacementAutoTestService PLACEMENT_AUTO_TEST_SERVICE = new PlacementAutoTestService(
            COURSE_GENERATOR,
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            AUTO_COURSE_SERVICE
    );
        private static Long pendingAutoStrictSetupSeed;
        private static int pendingAutoStrictSetupWaitTicks;
        private static int roundSessionAutosaveTicks;
        private static String lastRoundSessionSignature = "";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(WaypointSync.ID, WaypointSync.CODEC);
        PayloadTypeRegistry.playC2S().register(LeaderboardRequest.ID, LeaderboardRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(WaypointTeleportSync.ID, WaypointTeleportSync.CODEC);
        PayloadTypeRegistry.playC2S().register(ThrowPowerLockSync.ID, ThrowPowerLockSync.CODEC);
        PayloadTypeRegistry.playC2S().register(ThrowStanceSync.ID, ThrowStanceSync.CODEC);
        PayloadTypeRegistry.playS2C().register(AceCinematicSync.ID, AceCinematicSync.CODEC);
        PayloadTypeRegistry.playS2C().register(HoleMiniMapSync.ID, HoleMiniMapSync.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundRunningScoresSync.ID, RoundRunningScoresSync.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundCompleteCinematicSync.ID, RoundCompleteCinematicSync.CODEC);
        PayloadTypeRegistry.playS2C().register(MenuScreenSync.ID, MenuScreenSync.CODEC);
        PayloadTypeRegistry.playS2C().register(LeaderboardResponse.ID, LeaderboardResponse.CODEC);

        PayloadTypeRegistry.playS2C().register(WaypointSync.ID, WaypointSync.CODEC);
        PayloadTypeRegistry.playS2C().register(WaypointRemovedSync.ID, WaypointRemovedSync.CODEC);
        PayloadTypeRegistry.playS2C().register(ThrowPowerLockSync.ID, ThrowPowerLockSync.CODEC);

        ResourceManagerHelper.registerBuiltinResourcePack(
                new Identifier(MOD_ID, "mcdg-test-resources"),
                FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(),
                Text.literal("MCDG Test Resources"),
                ResourcePackActivationType.DEFAULT_ENABLED
        );
        ServerPlayNetworking.registerGlobalReceiver(WaypointSync.ID, (payload, context) ->
            context.server().execute(() -> WaypointSync.update(context.player(), payload.waypoints()))
        );
        ServerPlayNetworking.registerGlobalReceiver(LeaderboardRequest.ID, (payload, context) ->
            context.server().execute(() -> handleLeaderboardRequest(context.player(), payload.courseName()))
        );
        ServerPlayNetworking.registerGlobalReceiver(WaypointTeleportSync.ID, (payload, context) ->
                context.server().execute(() -> handleWaypointTeleport(context.player(), payload))
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
        McdgConfig config = McdgConfig.loadDefault();
        McdgItems.register(ACTIVE_COURSE_MANAGER, ROUND_STATE_MANAGER, TOURNAMENT_RULESET_MANAGER, config.enableStrictFlowDebug());
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
            THROW_AUTO_TEST_SERVICE,
            ROUND_SESSION_STORAGE,
            PLAYER_ROUND_SESSION_STORAGE,
            BUILD_COURSE_SESSION_MANAGER,
            AUTO_COURSE_SERVICE
        );
        ServerTickEvents.END_SERVER_TICK.register(PLACEMENT_AUTO_TEST_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(THROW_AUTO_TEST_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(ROUND_PRESENTATION_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(BUILD_COURSE_SESSION_MANAGER::tick);
        ServerTickEvents.END_SERVER_TICK.register(AUTO_COURSE_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(McdgMod::handlePendingAutoStrictSetup);
        ServerTickEvents.END_SERVER_TICK.register(McdgMod::autosaveRoundSession);
	ServerTickEvents.END_SERVER_TICK.register(ResortCourseBuilder::tick);
	ServerTickEvents.END_SERVER_TICK.register(DiscFlightSimulator::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WaypointSync.clearAll());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ResortWaypointManager.clearResortWaypoint());
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedPracticeCourse);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedRoundSession);
        ServerLifecycleEvents.SERVER_STARTED.register(BUILD_COURSE_SESSION_MANAGER::load);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartHeadlessAutoTest);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartAutoStrictSetup);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> LEADERBOARD_MANAGER.load(server));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> WorldSpawnHandler.onServerStarted(server, AUTO_COURSE_SERVICE, PRACTICE_COURSE_STORAGE));
        // Warm storage caches after full startup (including WorldSpawnHandler) so the
        // first G key press after any server restart is instant for all players.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> PRACTICE_COURSE_STORAGE.listReusable(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(McdgMod::flushRoundSessionOnShutdown);
        ServerLifecycleEvents.SERVER_STOPPING.register(BUILD_COURSE_SESSION_MANAGER::save);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> LEADERBOARD_MANAGER.save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WaypointSync.clearAll());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResortWaypointManager.clearResortWaypoint());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ResortCourseBuilder.reset());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                restoreRoundParticipantOnJoin(handler.player, server);
                ResortWaypointManager.broadcastToPlayer(handler.player);
                ResortCourseBuilder.onPlayerJoin(handler.player);
                // Warm storage caches on join so the first G key press is instant.
                PRACTICE_COURSE_STORAGE.listReusable(server);
                PLAYER_ROUND_SESSION_STORAGE.loadPlayer(server, handler.player.getUuid(), null);
            })
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            server.execute(() -> WaypointSync.clear(handler.player))
        );
        HoleProgressTracker.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            LEADERBOARD_MANAGER,
            config.enableHudScoringDebug(),
            config.enableStrictFlowDebug()
        );
        RoundRespawnHandler.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.respawnPenaltyStrokes()
        );
        CourseFireProtection.registerDamageHandler(ACTIVE_COURSE_MANAGER);
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
            return ActionResult.PASS;
        });

        LOGGER.info("Initialized {} (defaultHoles={}, protection={}, hudScoringDebug={}, strictFlowDebug={}, skipRoundPresentation={}, rulesetDefault={}, strictRespawnPenaltyStrokes={})",
                MOD_ID,
                config.defaultHoleCount(),
            config.enforceCourseProtection(),
            config.enableHudScoringDebug(),
            config.enableStrictFlowDebug(),
            config.skipRoundPresentation(),
            TOURNAMENT_RULESET_MANAGER.getActiveRuleset().name().toLowerCase(),
            config.respawnPenaltyStrokes());
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
                CourseFireProtection.apply(courseWorld);
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

        lastRoundSessionSignature = buildRoundSessionSignature();
        roundSessionAutosaveTicks = 0;
    }

    private static void autosaveRoundSession(MinecraftServer server) {
        roundSessionAutosaveTicks++;
        if (roundSessionAutosaveTicks < ROUND_SESSION_AUTOSAVE_INTERVAL_TICKS) {
            return;
        }
        roundSessionAutosaveTicks = 0;
        persistRoundSession(server, false);
    }

    private static void flushRoundSessionOnShutdown(MinecraftServer server) {
        persistRoundSession(server, true);
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
                PRACTICE_COURSE_STORAGE.save(server, course, placed);
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
                ROUND_STATE_MANAGER.startRoundForPlayer(player.getUuid(), targetLie);
            }
        }

        RoundInventoryCleaner.restoreRoundInventory(player);
        ScorecardManager.ensureScorecardInInventory(player);

        PlayerRoundState currentState = ROUND_STATE_MANAGER.getState(player.getUuid()).orElse(null);

        if (targetLie != null && player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
            BlockPos safeLie = resolveSafeFeetNearWithin(world, targetLie, 2);
            ROUND_STATE_MANAGER.setState(player.getUuid(), currentState == null
                    ? new PlayerRoundState(1, safeLie, 0, 0, false)
                    : currentState.withLie(safeLie));
            player.teleport(safeLie.getX() + 0.5, safeLie.getY() + 1.0, safeLie.getZ() + 0.5);
            currentState = ROUND_STATE_MANAGER.getState(player.getUuid()).orElse(null);
        }
        int hole = currentState == null ? 1 : currentState.currentHole();
        player.sendMessage(Text.literal("Rejoined active round at hole " + hole + "."), true);
        HoleProgressTracker.sendRunningScoreboardToPlayer(player, ACTIVE_COURSE_MANAGER, ROUND_STATE_MANAGER);
    }

    public static void handleLeaderboardRequest(ServerPlayerEntity player, String courseName) {
        if (player == null || courseName == null || courseName.isBlank()) {
            return;
        }

        int totalPar = 0;
        Course activeCourse = ACTIVE_COURSE_MANAGER.getActiveCourse().orElse(null);
        if (activeCourse != null && activeCourse.name().equalsIgnoreCase(courseName)) {
            totalPar = 0;
            for (Hole hole : activeCourse.holes()) {
                totalPar += hole.par();
            }
        }

        List<LeaderboardManager.LeaderboardEntry> topEntries = LEADERBOARD_MANAGER.getTopScores(courseName, 5);
        List<LeaderboardResponse.Entry> responseEntries = new ArrayList<>();
        for (LeaderboardManager.LeaderboardEntry entry : topEntries) {
            responseEntries.add(new LeaderboardResponse.Entry(entry.playerName(), entry.score()));
        }

        ServerPlayNetworking.send(player, LeaderboardResponse.Payload.active(courseName, totalPar, responseEntries));
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
    private static void handleWaypointTeleport(ServerPlayerEntity player, WaypointTeleportSync payload) {
        String name = payload.name();
        if (name == null || name.isBlank()) return;

        // Resort waypoint
        var resort = ResortWaypointManager.getResortWaypoint().orElse(null);
        if (resort != null && resort.name().equals(name)) {
            BlockPos target = new BlockPos(resort.x(), resort.y(), resort.z()).south(4);
            BlockPos safe = resolveSafeFeetNearWithin(player.getServerWorld(), target, 2);
            player.teleport(player.getServerWorld(), safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("Teleported to MCDG Resort!"), false);
            return;
        }

        // Player-created waypoint
        for (WaypointSync.WaypointEntry entry : WaypointSync.getWaypoints(player)) {
            if (entry != null && entry.name().equals(name)) {
                int targetY = entry.y();
                if (targetY == WaypointSync.UNKNOWN_Y) {
                    targetY = SurfaceResolver.resolveSurfacePos(player.getServerWorld(), entry.x(), entry.z()).getY();
                }
                BlockPos target = new BlockPos(entry.x(), targetY, entry.z());
                BlockPos safe = resolveSafeFeetNearWithin(player.getServerWorld(), target, 2);
                player.teleport(player.getServerWorld(), safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
                player.sendMessage(Text.literal("Teleported to " + name + "!"), false);
                return;
            }
        }
    }

}
