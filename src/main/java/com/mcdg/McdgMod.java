package com.mcdg;

import com.mcdg.command.McdgAdminCommands;
import com.mcdg.config.McdgConfig;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.McdgItems;
import com.mcdg.game.PlayerRoundState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundRespawnHandler;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.net.AceCinematicSync;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.net.RoundCompleteCinematicSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.SeededCourseGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McdgMod implements ModInitializer {
    public static final String MOD_ID = "mcdg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String AUTOTEST_ENV = "MCDG_AUTOTEST";
    private static final String AUTOTEST_BASE_SEED_ENV = "MCDG_AUTOTEST_BASE_SEED";
    private static final String AUTO_STRICT_SETUP_ENV = "MCDG_AUTO_STRICT_SETUP";
    private static final int AUTO_STRICT_SETUP_MAX_WAIT_TICKS = 20 * 120;

    private static final CourseGenerator COURSE_GENERATOR = new SeededCourseGenerator();
    private static final ActiveCourseManager ACTIVE_COURSE_MANAGER = new ActiveCourseManager();
    private static final CoursePlacementService COURSE_PLACEMENT_SERVICE = new CoursePlacementService();
    private static final CoursePlacementValidator COURSE_PLACEMENT_VALIDATOR = new CoursePlacementValidator();
    private static final RoundStateManager ROUND_STATE_MANAGER = new RoundStateManager();
    private static final TournamentRulesetManager TOURNAMENT_RULESET_MANAGER = new TournamentRulesetManager();
    private static final RoundPresentationService ROUND_PRESENTATION_SERVICE = new RoundPresentationService();
    private static final PracticeCourseStorage PRACTICE_COURSE_STORAGE = new PracticeCourseStorage();
        private static final ThrowAutoTestService THROW_AUTO_TEST_SERVICE = new ThrowAutoTestService(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER
        );
    private static final PlacementAutoTestService PLACEMENT_AUTO_TEST_SERVICE = new PlacementAutoTestService(
            COURSE_GENERATOR,
            COURSE_PLACEMENT_SERVICE,
            COURSE_PLACEMENT_VALIDATOR,
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER
    );
        private static Long pendingAutoStrictSetupSeed;
        private static int pendingAutoStrictSetupWaitTicks;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(AceCinematicSync.ID, AceCinematicSync.CODEC);
        PayloadTypeRegistry.playS2C().register(HoleMiniMapSync.ID, HoleMiniMapSync.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundCompleteCinematicSync.ID, RoundCompleteCinematicSync.CODEC);
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
            THROW_AUTO_TEST_SERVICE
        );
        ServerTickEvents.END_SERVER_TICK.register(PLACEMENT_AUTO_TEST_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(THROW_AUTO_TEST_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(ROUND_PRESENTATION_SERVICE::tick);
        ServerTickEvents.END_SERVER_TICK.register(McdgMod::handlePendingAutoStrictSetup);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedPracticeCourse);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartHeadlessAutoTest);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartAutoStrictSetup);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> restoreRoundParticipantOnJoin(handler.player, server))
        );
        HoleProgressTracker.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.enableHudScoringDebug(),
            config.enableStrictFlowDebug()
        );
        RoundRespawnHandler.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.respawnPenaltyStrokes()
        );

        LOGGER.info("Initialized {} (defaultHoles={}, protection={}, debug={}, hudScoringDebug={}, strictFlowDebug={}, skipRoundPresentation={}, rulesetDefault={}, strictRespawnPenaltyStrokes={})",
                MOD_ID,
                config.defaultHoleCount(),
            config.enforceCourseProtection(),
            config.enableDebugLogging(),
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
            ACTIVE_COURSE_MANAGER.setPlacedCourseState(snapshot.placedCourseState());
            ACTIVE_COURSE_MANAGER.setPersistentPlacedCourse(true);
            ACTIVE_COURSE_MANAGER.setLegacyPracticeSnapshot(snapshot.legacyFormat());
            ACTIVE_COURSE_MANAGER.setRoundActive(false);

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
                targetLie = resolveSafeFeetNear(world, firstTee);
                ROUND_STATE_MANAGER.startRoundForPlayer(player.getUuid(), targetLie);
            }
        }

        RoundInventoryCleaner.restoreRoundInventory(player);
        ScorecardManager.ensureScorecardInInventory(player);

        if (targetLie != null && player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
            BlockPos safeLie = resolveSafeFeetNear(world, targetLie);
            player.teleport(safeLie.getX() + 0.5, safeLie.getY() + 1.0, safeLie.getZ() + 0.5);
        }

        PlayerRoundState currentState = ROUND_STATE_MANAGER.getState(player.getUuid()).orElse(null);
        int hole = currentState == null ? 1 : currentState.currentHole();
        player.sendMessage(Text.literal("Rejoined active round at hole " + hole + "."), true);
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
