package com.mcdg;

import com.mcdg.command.McdgAdminCommands;
import com.mcdg.config.McdgConfig;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.McdgItems;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundRespawnHandler;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.SeededCourseGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McdgMod implements ModInitializer {
    public static final String MOD_ID = "mcdg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String AUTOTEST_ENV = "MCDG_AUTOTEST";

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

    @Override
    public void onInitialize() {
        McdgConfig config = McdgConfig.loadDefault();
        McdgItems.register(ACTIVE_COURSE_MANAGER, ROUND_STATE_MANAGER, TOURNAMENT_RULESET_MANAGER);
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
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::loadPersistedPracticeCourse);
        ServerLifecycleEvents.SERVER_STARTED.register(McdgMod::maybeStartHeadlessAutoTest);
        HoleProgressTracker.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.enableHudScoringDebug()
        );
        RoundRespawnHandler.register(
            ACTIVE_COURSE_MANAGER,
            ROUND_STATE_MANAGER,
            TOURNAMENT_RULESET_MANAGER,
            config.respawnPenaltyStrokes()
        );

        LOGGER.info("Initialized {} (defaultHoles={}, protection={}, debug={}, hudScoringDebug={}, skipRoundPresentation={}, rulesetDefault={}, strictRespawnPenaltyStrokes={})",
                MOD_ID,
                config.defaultHoleCount(),
            config.enforceCourseProtection(),
            config.enableDebugLogging(),
            config.enableHudScoringDebug(),
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
        LOGGER.info("Headless autotest requested via {}: runs={}, holes={}", AUTOTEST_ENV, safeRuns, safeHoles);

        server.execute(() -> {
            int started = PLACEMENT_AUTO_TEST_SERVICE.start(server.getCommandSource(), safeRuns, safeHoles);
            if (started == 0) {
                LOGGER.error("Headless autotest did not start.");
            }
        });
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
}
