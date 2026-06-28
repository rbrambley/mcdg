package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.game.HazardManager;
import com.mcdg.game.HazardType;
import com.mcdg.game.HazardBehavior;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseDiscoveryHandler;
import com.mcdg.game.LostCourse;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.PlacementAutoTestService;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Debug and autotest command handlers.
 */
public final class DebugCommands {
    private DebugCommands() {
    }

        public static int executeDebugPermissions(ServerCommandSource source) {
                boolean hasPermissionLevelTwo = source.hasPermissionLevel(2);
                boolean dedicated = source.getServer().isDedicated();
                boolean allowedByGate = CommandPermissions.canUseAdminCommands(source);

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
                                + ", showAdvancedCommands=" + CommandPermissions.SHOW_ADVANCED_COMMANDS
                                + ", sourceType=" + finalSourceType
                                + ", source=" + finalSourceIdentity
                ), false);
                return 1;
        }

        public static int executeValidatePlacement(
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

        public static int executeAutoTestPlacement(
                        ServerCommandSource source,
                        PlacementAutoTestService autoTestService,
                        int runs,
                        int holes
        ) {
                return autoTestService.start(source, runs, holes);
        }

        public static int executeAutoTestPlacementSeeded(
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

        public static int executeAutoTestShadowStatus(
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

        /**
         * Debug command to show hazard type at player position.
         */
        public static int executeDebugHazardInfo(ServerCommandSource source) {
                if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                        source.sendError(Text.literal("This command can only be run by a player."));
                        return 0;
                }

                ServerWorld world = player.getServerWorld();
                BlockPos feet = player.getBlockPos();

                HazardType hazard = HazardManager.getHazardType(world, feet);
                HazardBehavior behavior = HazardManager.getHazardBehavior(hazard);

                source.sendFeedback(() -> {
                        MutableText header = Text.literal("Hazard at " + feet.getX() + "," + feet.getY() + "," + feet.getZ() + ": ")
                                .formatted(Formatting.GRAY);
                        header.append(Text.literal(hazard.displayName()).formatted(Formatting.YELLOW));
                        header.append(Text.literal(" (" + hazard.description() + ")").formatted(Formatting.WHITE));
                        return header;
                }, false);

                MutableText behaviorLine = Text.literal("Behavior: ").formatted(Formatting.GRAY);
                if (behavior.addsPenaltyStroke()) {
                        behaviorLine.append(Text.literal("+1 stroke ").formatted(Formatting.RED));
                }
                if (behavior.nextThrowPowerMultiplier() < 1.0f) {
                        int percent = Math.round(behavior.nextThrowPowerMultiplier() * 100.0f);
                        behaviorLine.append(Text.literal("next throw " + percent + "% ").formatted(Formatting.AQUA));
                }
                if (behavior.destroysDisc()) {
                        behaviorLine.append(Text.literal("destroys disc ").formatted(Formatting.DARK_RED));
                }
                if (behavior.damageAmount() > 0) {
                        behaviorLine.append(Text.literal(behavior.damageAmount() + " dmg ").formatted(Formatting.DARK_RED));
                }
                behaviorLine.append(Text.literal("bounce=" + String.format("%.1f", behavior.bounceModifier())).formatted(Formatting.WHITE));

                final Text finalBehaviorLine = behaviorLine;
                source.sendFeedback(() -> finalBehaviorLine, false);

                return 1;
        }

        /**
         * Debug command to list all hazard types with their behavior values.
         */
        public static int executeDebugHazardList(ServerCommandSource source) {
                source.sendFeedback(() -> Text.literal("Available Hazard Types:").formatted(Formatting.GOLD, Formatting.BOLD), false);
                for (HazardType type : HazardType.values()) {
                        HazardBehavior behavior = HazardManager.getHazardBehavior(type);
                        source.sendFeedback(() -> formatHazardListLine(type, behavior), false);
                }
                return 1;
        }

        private static Text formatHazardListLine(HazardType type, HazardBehavior behavior) {
                MutableText line = Text.literal(" - ").formatted(Formatting.GRAY);
                line.append(Text.literal(type.displayName()).formatted(Formatting.YELLOW));
                line.append(Text.literal(": " + type.description()).formatted(Formatting.WHITE));

                if (behavior.addsPenaltyStroke()) {
                        line.append(Text.literal(" [+1 stroke]").formatted(Formatting.RED));
                }
                if (behavior.nextThrowPowerMultiplier() < 1.0f) {
                        int percent = Math.round(behavior.nextThrowPowerMultiplier() * 100.0f);
                        line.append(Text.literal(" [next throw " + percent + "%]").formatted(Formatting.AQUA));
                }
                if (behavior.destroysDisc()) {
                        line.append(Text.literal(" [destroys disc]").formatted(Formatting.DARK_RED));
                }
                if (behavior.damageAmount() > 0) {
                        line.append(Text.literal(" [" + behavior.damageAmount() + " dmg]").formatted(Formatting.DARK_RED));
                }

                return line;
        }

        public static int executeAutoTestShadowSet(
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

        public static int executeCancelAutoTest(ServerCommandSource source, PlacementAutoTestService autoTestService) {
                return autoTestService.cancel(source);
        }

        public static int executeAutoTestThrows(
                        ServerCommandSource source,
                        ThrowAutoTestService throwAutoTestService,
                        int count
        ) {
                return throwAutoTestService.start(source, count);
        }

        public static int executeCancelThrowTest(ServerCommandSource source, ThrowAutoTestService throwAutoTestService) {
                return throwAutoTestService.cancel(source);
        }

        public static int executeQuickThrowTest(
                        ServerCommandSource source,
                        CourseGenerator generator,
                        ActiveCourseManager courseManager,
                        CoursePlacementService placementService,
                        CoursePlacementValidator placementValidator,
                        RoundStateManager roundStateManager,
                        RoundPresentationService roundPresentationService,
                        PracticeCourseStorage practiceCourseStorage,
                        ThrowAutoTestService throwAutoTestService,
                        long seed,
                        int throwCount
        ) {
                int created = CourseAdminCommands.executeCreateCourse(source, generator, courseManager, seed);
                if (created == 0) {
                        return 0;
                }

                int started = RoundLifecycleCommands.executeStartRound(
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

                int testStarted = executeAutoTestThrows(source, throwAutoTestService, throwCount);
                if (testStarted == 0) {
                        return 0;
                }

                source.sendFeedback(() -> Text.literal(
                        "Quick throw test running: seed=" + seed + ", throws=" + throwCount + "."
                ), true);
                return 1;
        }

        /**
         * Lists all lost courses in the world.
         */
        public static int executeListLostCourses(ServerCommandSource source) {
                List<LostCourse> courses = ChallengeCourseManager.getAllLostCourses();
                
                if (courses.isEmpty()) {
                        source.sendFeedback(() -> Text.literal("No lost courses registered."), false);
                        return 1;
                }

                source.sendFeedback(() -> Text.literal("Lost Courses (" + courses.size() + "):"), false);
                for (LostCourse course : courses) {
                        String status = course.isDiscovered() ? "[DISCOVERED]" : "[HIDDEN]";
                        source.sendFeedback(() -> Text.literal(
                                " - " + status + " " + course.name() + " (" + course.type().getDisplayName() + ")"
                                        + " at (" + course.entrancePosition().getX() + ", " + course.entrancePosition().getY() + ", " + course.entrancePosition().getZ() + ")"
                        ), false);
                }

                return 1;
        }

        /**
         * Discovers a lost course by ID.
         */
        public static int executeDiscoverCourse(ServerCommandSource source, String courseIdString) {
                if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                        source.sendError(Text.literal("This command can only be run by a player."));
                        return 0;
                }

                try {
                        UUID courseId = UUID.fromString(courseIdString);
                        ChallengeCourseDiscoveryHandler.discoverCourseById(player, courseId);
                        source.sendFeedback(() -> Text.literal("Attempting to discover course: " + courseIdString), true);
                        return 1;
                } catch (IllegalArgumentException e) {
                        source.sendError(Text.literal("Invalid UUID format: " + courseIdString));
                        return 0;
                }
        }

        /**
         * Clears all lost courses (for testing).
         */
        public static int executeClearLostCourses(ServerCommandSource source) {
                int count = ChallengeCourseManager.getAllLostCourses().size();
                ChallengeCourseManager.clearAllLostCourses();
                source.sendFeedback(() -> Text.literal("Cleared " + count + " lost courses."), true);
                return 1;
        }

        /**
         * Places a test lost course at player position.
         */
        public static int executePlaceTestLostCourse(ServerCommandSource source) {
                if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                        source.sendError(Text.literal("This command can only be run by a player."));
                        return 0;
                }

                BlockPos playerPos = player.getBlockPos();
                LostCourse testCourse = createTestLostCourse(playerPos);
                
                ChallengeCourseManager.registerLostCourse(testCourse);
                ChallengeCourseManager.placeLostCourseEntrance(player.getServerWorld(), playerPos, testCourse);
                
                source.sendFeedback(() -> Text.literal("Placed test lost course: " + testCourse.name() + " at your position"), true);
                return 1;
        }

        /**
         * Creates a test lost course for debugging.
         */
        private static LostCourse createTestLostCourse(BlockPos pos) {
                UUID courseId = UUID.randomUUID();
                return new LostCourse(
                        courseId,
                        "Test Lost Course",
                        pos,
                        pos.add(50, 0, 50),
                        List.of(),
                        com.mcdg.game.ChallengeCourseType.LOST_COURSE,
                        false
                );
        }

}
