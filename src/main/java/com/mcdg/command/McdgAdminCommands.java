package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.McdgItems;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
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
            ThrowAutoTestService throwAutoTestService
    ) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("mcdg")
                        .requires(McdgAdminCommands::canUseAdminCommands)
                        .then(literal("createcourse")
                                .then(argument("seed", LongArgumentType.longArg())
                                        .executes(context -> executeCreateCourse(
                                                context.getSource(),
                                                generator,
                                                courseManager,
                                                LongArgumentType.getLong(context, "seed")
                                        ))))
                        .then(literal("startround")
                                .executes(context -> executeStartRound(
                                        context.getSource(),
                                        courseManager,
                                        placementService,
                                        placementValidator,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        practiceCourseStorage,
                                        false
                                )))
                        .then(literal("practicecourse")
                                .executes(context -> executeStartRound(
                                        context.getSource(),
                                        courseManager,
                                        placementService,
                                        placementValidator,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation,
                                        practiceCourseStorage,
                                        true
                                )))
                        .then(literal("resumecourse")
                                .executes(context -> executeResumeCourse(
                                        context.getSource(),
                                        courseManager,
                                        roundStateManager,
                                        roundPresentationService,
                                        skipRoundPresentation
                                )))
                        .then(literal("resetcourse")
                                .executes(context -> executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("cleanupcourse")
                                .executes(context -> executeCleanupCourse(context.getSource(), courseManager, placementService, roundStateManager, practiceCourseStorage)))
                        .then(literal("gotocourse")
                                .executes(context -> executeGotoCourse(context.getSource(), courseManager)))
                        .then(literal("endround")
                                .executes(context -> executeEndRound(context.getSource(), courseManager, roundStateManager)))
                        .then(literal("ruleset")
                                .executes(context -> executeShowRuleset(context, rulesetManager))
                                .then(literal("casual")
                                        .executes(context -> executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.CASUAL)))
                                .then(literal("strict")
                                        .executes(context -> executeSetRuleset(context, rulesetManager, TournamentRulesetManager.Ruleset.STRICT)))
                                .then(literal("surface")
                                        .executes(context -> executeShowStrictSurfacePreset(context, rulesetManager))
                                        .then(argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                        builder.suggest("fast");
                                                        builder.suggest("balanced");
                                                        builder.suggest("tournament");
                                                        return builder.buildFuture();
                                                })
                                                .executes(context -> executeSetStrictSurfacePreset(context, rulesetManager, StringArgumentType.getString(context, "preset")))))
                                .then(literal("minimap")
                                        .executes(context -> executeShowMiniMapQualityPreset(context, rulesetManager))
                                        .then(argument("preset", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                        builder.suggest("performance");
                                                        builder.suggest("balanced");
                                                        builder.suggest("ultra");
                                                        return builder.buildFuture();
                                                })
                                                .executes(context -> executeSetMiniMapQualityPreset(context, rulesetManager, StringArgumentType.getString(context, "preset"))))))
                        .then(literal("debugperms")
                                .executes(context -> executeDebugPermissions(context.getSource())))
                        .then(literal("validateplacement")
                                .executes(context -> executeValidatePlacement(
                                        context.getSource(),
                                        courseManager,
                                        placementValidator
                                )))
                        .then(literal("autotestplacement")
                                .then(argument("runs", IntegerArgumentType.integer(1, 200))
                                        .then(argument("holes", IntegerArgumentType.integer(1, 18))
                                                .executes(context -> executeAutoTestPlacement(
                                                        context.getSource(),
                                                        autoTestService,
                                                        IntegerArgumentType.getInteger(context, "runs"),
                                                        IntegerArgumentType.getInteger(context, "holes")
                                                )))))
                        .then(literal("cancelautotest")
                                .executes(context -> executeCancelAutoTest(context.getSource(), autoTestService)))
                        .then(literal("autotestthrows")
                                .then(argument("count", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> executeAutoTestThrows(
                                                context.getSource(),
                                                throwAutoTestService,
                                                IntegerArgumentType.getInteger(context, "count")
                                        ))))
                        .then(literal("quickthrowtest")
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
                                                        LongArgumentType.getLong(context, "seed"),
                                                        IntegerArgumentType.getInteger(context, "count")
                                                )))))
                        .then(literal("cancelthrowtest")
                                .executes(context -> executeCancelThrowTest(context.getSource(), throwAutoTestService)))));
    }

        private static boolean canUseAdminCommands(ServerCommandSource source) {
                if (source.hasPermissionLevel(2)) {
                        return true;
                }

                // Keep local/integrated dev sessions usable even when OP metadata is not applied.
                return !source.getServer().isDedicated();
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
                                + ", sourceType=" + finalSourceType
                                + ", source=" + finalSourceIdentity
                ), false);
                return 1;
        }

    private static int executeCreateCourse(
            ServerCommandSource source,
            CourseGenerator generator,
            ActiveCourseManager courseManager,
            long seed
        ) {
        int holeCount = 9;

        try {
            Course course = generator.generate(seed, holeCount);
            courseManager.setActiveCourse(course);

            source.sendFeedback(() -> Text.literal(
                    "Created active course '" + course.name() + "' with " + course.holes().size() + " holes (seed=" + seed + "). Use /mcdg startround or /mcdg practicecourse to place it near you on the surface."
            ), false);
            return 1;
        } catch (RuntimeException ex) {
            source.sendError(Text.literal("Course generation failed: " + ex.getMessage()));
            return 0;
        }
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
                                        boolean persistentCourse
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                if (course == null) {
                        source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> first."));
                        return 0;
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
                        }
                        courseManager.clearPlacedCourseState();
                        practiceCourseStorage.clear(source.getServer());
                        roundStateManager.clearAll();
                }

                ServerWorld world = source.getWorld();
                int totalHoles = course.holes().size();

                // Show a progress bar and status message before the terrain generation starts.
                ServerBossBar progressBar = new ServerBossBar(
                        Text.literal("Building course... 0/" + totalHoles + " holes"),
                        BossBar.Color.GREEN,
                        BossBar.Style.PROGRESS
                );
                progressBar.setPercent(0.0f);
                for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                        if (barPlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                progressBar.addPlayer(barPlayer);
                                barPlayer.sendMessage(Text.literal("Building disc golf course, please wait..."), true);
                        }
                }

                try {
                        BlockPos baseOrigin = BlockPos.ofFloored(source.getPosition());
                        PlacedCourseState placed = null;
                        final int maxPlacementAttempts = 5;

                        for (int attempt = 1; attempt <= maxPlacementAttempts; attempt++) {
                                BlockPos attemptOrigin = offsetOriginForAttempt(baseOrigin, attempt);
                                final int displayAttempt = attempt;
                                placed = placementService.placeCourse(world, attemptOrigin, course, holesDone -> {
                                        float pct = holesDone / (float) totalHoles;
                                        progressBar.setPercent(Math.min(1.0f, pct));
                                        progressBar.setName(Text.literal(
                                                "Building course... " + holesDone + "/" + totalHoles + " holes"
                                                        + " (attempt " + displayAttempt + "/" + maxPlacementAttempts + ")"
                                        ));
                                });

                                CoursePlacementValidator.ValidationReport attemptReport = placementValidator.validatePlacedCourse(
                                        world,
                                        course,
                                        placed,
                                        "start-round-attempt-" + attempt
                                );

                                if (!hasDeeplyEnclosedBasketIssue(attemptReport)) {
                                        break;
                                }

                                placementService.resetPlacedCourse(world, placed);
                                placed = null;

                                if (attempt < maxPlacementAttempts) {
                                        final int nextAttempt = attempt + 1;
                                        source.sendFeedback(() -> Text.literal(
                                                "Detected deeply enclosed basket placement. Retrying at a nearby surface anchor (attempt "
                                                        + nextAttempt + "/" + maxPlacementAttempts + ")..."
                                        ), false);
                                }
                        }

                        if (placed == null) {
                                for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                        progressBar.removePlayer(barPlayer);
                                }
                                source.sendError(Text.literal(
                                        "Failed to place a surface-playable course after multiple attempts (deeply enclosed basket detected)."
                                ));
                                return 0;
                        }

                        // Course is placed — remove the progress bar.
                        for (var barPlayer : source.getServer().getPlayerManager().getPlayerList()) {
                                progressBar.removePlayer(barPlayer);
                        }

                        roundStateManager.clearAll();
                        removeRoundThrowItemsFromWorldPlayers(source, world);
                        int initializedPlayers = 0;
                        List<java.util.UUID> participantIds = new java.util.ArrayList<>();
                        for (var player : source.getServer().getPlayerManager().getPlayerList()) {
                                if (player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                        BlockPos firstTee = placed.holeTees().getOrDefault(1, player.getBlockPos());
                                        BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                                        roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                                        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                                        ensureSingleRoundThrowItem(player);
                                        ScorecardManager.initializeScorecard(player, course);
                                        participantIds.add(player.getUuid());
                                        player.sendMessage(Text.literal("Round staging. Moved to Hole 1 tee."), true);
                                        initializedPlayers++;
                                }
                        }

                        if (initializedPlayers == 0) {
                                try {
                                        var sourcePlayer = source.getPlayerOrThrow();
                                        BlockPos firstTee = placed.holeTees().getOrDefault(1, sourcePlayer.getBlockPos());
                                        BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                                        roundStateManager.startRoundForPlayer(sourcePlayer.getUuid(), safeTee);
                                        sourcePlayer.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                                        ensureSingleRoundThrowItem(sourcePlayer);
                                        ScorecardManager.initializeScorecard(sourcePlayer, course);
                                        participantIds.add(sourcePlayer.getUuid());
                                        sourcePlayer.sendMessage(Text.literal("Round staging. Moved to Hole 1 tee."), true);
                                        initializedPlayers = 1;
                                } catch (Exception ignored) {
                                }
                        }

                        final int trackedPlayers = initializedPlayers;
                        announceSignatureHole(source, course, participantIds);

                        courseManager.setPlacedCourseState(placed);
                        courseManager.setPersistentPlacedCourse(persistentCourse);
                        courseManager.setLegacyPracticeSnapshot(false);
                        if (persistentCourse) {
                                practiceCourseStorage.save(source.getServer(), course, placed);
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
                        boolean skipRoundPresentation
        ) {
                Course course = courseManager.getActiveCourse().orElse(null);
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                if (course == null || placed == null) {
                        source.sendError(Text.literal("No stale placed course found. Use /mcdg startround or /mcdg practicecourse first."));
                        return 0;
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

                int totalHoles = course.holes().size();
                roundStateManager.clearAll();
                removeRoundThrowItemsFromWorldPlayers(source, world);

                int initializedPlayers = 0;
                List<java.util.UUID> participantIds = new java.util.ArrayList<>();
                for (var player : source.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                                BlockPos firstTee = placed.holeTees().getOrDefault(1, player.getBlockPos());
                                BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                                roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
                                player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                                ensureSingleRoundThrowItem(player);
                                ScorecardManager.initializeScorecard(player, course);
                                participantIds.add(player.getUuid());
                                player.sendMessage(Text.literal("Round resumed on existing course. Moved to Hole 1 tee."), true);
                                initializedPlayers++;
                        }
                }

                if (initializedPlayers == 0) {
                        try {
                                var sourcePlayer = source.getPlayerOrThrow();
                                BlockPos firstTee = placed.holeTees().getOrDefault(1, sourcePlayer.getBlockPos());
                                BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
                                roundStateManager.startRoundForPlayer(sourcePlayer.getUuid(), safeTee);
                                sourcePlayer.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                                ensureSingleRoundThrowItem(sourcePlayer);
                                ScorecardManager.initializeScorecard(sourcePlayer, course);
                                participantIds.add(sourcePlayer.getUuid());
                                sourcePlayer.sendMessage(Text.literal("Round resumed on existing course. Moved to Hole 1 tee."), true);
                                initializedPlayers = 1;
                        } catch (Exception ignored) {
                        }
                }

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

        private static int totalCoursePar(Course course) {
                int par = 0;
                for (var hole : course.holes()) {
                        par += hole.par();
                }
                return par;
        }

        private static void announceSignatureHole(ServerCommandSource source, Course course, List<java.util.UUID> participantIds) {
                var signatureHole = course.holes().stream().filter(hole -> hole.isSignature()).findFirst();
                if (signatureHole.isEmpty()) {
                        if (source.getEntity() instanceof ServerPlayerEntity player) {
                                player.sendMessage(Text.literal("Signature Hole: none detected on this layout."), true);
                        } else {
                                source.sendFeedback(() -> Text.literal("Signature Hole: none detected on this layout."), false);
                        }
                        return;
                }

                var hole = signatureHole.get();
                String message = "Signature Hole: H" + hole.index() + " | " + hole.signatureType().displayName();
                if (source.getEntity() instanceof ServerPlayerEntity player) {
                        player.sendMessage(Text.literal(message), true);
                } else {
                        source.sendFeedback(() -> Text.literal(message), false);
                }

                for (java.util.UUID participantId : participantIds) {
                        var player = source.getServer().getPlayerManager().getPlayer(participantId);
                        if (player != null) {
                                player.sendMessage(Text.literal(message), true);
                        }
                }
        }

        private static boolean hasDeeplyEnclosedBasketIssue(CoursePlacementValidator.ValidationReport report) {
                for (CoursePlacementValidator.ValidationIssue issue : report.issues()) {
                        if ("basket_deeply_enclosed".equals(issue.code())) {
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

                placementService.resetPlacedCourse(world, placed);
                removeJunkDropsNearCourse(world, placed);
                removeRoundThrowItemsFromCourseWorldPlayers(source, courseManager);
                courseManager.clearPlacedCourseState();
                courseManager.setRoundActive(false);
                roundStateManager.clearAll();
                practiceCourseStorage.clear(source.getServer());

                source.sendFeedback(() -> Text.literal("Course cleanup complete. Original blocks restored."), true);
                return 1;
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

                if (roundStateManager.getState(sourcePlayer.getUuid()).isEmpty()) {
                        roundStateManager.startRoundForPlayer(sourcePlayer.getUuid(), safeTee);
                }

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
                        player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
                        source.sendFeedback(() -> Text.literal("Teleported to Hole 1 tee."), false);
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
                roundStateManager.clearAll();
                source.sendFeedback(() -> Text.literal("Round ended. Use /mcdg resetcourse to restore terrain edits."), true);
                return 1;
        }

        private static int executeShowRuleset(
                        CommandContext<ServerCommandSource> context,
                        TournamentRulesetManager rulesetManager
        ) {
                TournamentRulesetManager.Ruleset active = rulesetManager.getActiveRuleset();
                TournamentRulesetManager.StrictSurfacePreset preset = rulesetManager.getStrictSurfacePreset();
                TournamentRulesetManager.MiniMapQualityPreset miniMapPreset = rulesetManager.getMiniMapQualityPreset();
                context.getSource().sendFeedback(
                        () -> Text.literal("Current ruleset: " + active.name().toLowerCase()
                                + " | strict surface preset: " + preset.name().toLowerCase()
                                + " | minimap quality preset: " + miniMapPreset.name().toLowerCase()),
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

        private static int executeShowMiniMapQualityPreset(
                        CommandContext<ServerCommandSource> context,
                        TournamentRulesetManager rulesetManager
        ) {
                TournamentRulesetManager.MiniMapQualityPreset preset = rulesetManager.getMiniMapQualityPreset();
                context.getSource().sendFeedback(
                        () -> Text.literal("Mini-map quality preset: " + preset.name().toLowerCase()
                                + " | terrain refresh interval: " + rulesetManager.miniMapTerrainRefreshIntervalTicks() + " ticks"
                                + " | move threshold: " + rulesetManager.miniMapTerrainRefreshMoveThresholdBlocks() + " blocks"),
                        false
                );
                return 1;
        }

        private static int executeSetMiniMapQualityPreset(
                        CommandContext<ServerCommandSource> context,
                        TournamentRulesetManager rulesetManager,
                        String presetName
        ) {
                TournamentRulesetManager.MiniMapQualityPreset preset;
                try {
                        preset = TournamentRulesetManager.MiniMapQualityPreset.valueOf(presetName.toUpperCase());
                } catch (IllegalArgumentException ex) {
                        context.getSource().sendError(Text.literal("Unknown mini-map quality preset: " + presetName + ". Use performance, balanced, or ultra."));
                        return 0;
                }

                rulesetManager.setMiniMapQualityPreset(preset);
                context.getSource().sendFeedback(() -> Text.literal("Mini-map quality preset set to " + preset.name().toLowerCase() + "."), true);
                return 1;
        }

        private static void removeRoundThrowItemsFromCourseWorldPlayers(ServerCommandSource source, ActiveCourseManager courseManager) {
                PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
                for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                        if (placed != null && player.getWorld().getRegistryKey() != placed.worldKey()) {
                                continue;
                        }
                        removeRoundThrowItems(player);
                }
        }

        private static void removeRoundThrowItemsFromWorldPlayers(ServerCommandSource source, ServerWorld world) {
                for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                        if (player.getWorld().getRegistryKey() != world.getRegistryKey()) {
                                continue;
                        }
                        removeRoundThrowItems(player);
                }
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

        private static int executeCancelAutoTest(ServerCommandSource source, PlacementAutoTestService autoTestService) {
                return autoTestService.cancel(source);
        }

        private static int executeAutoTestThrows(
                        ServerCommandSource source,
                        ThrowAutoTestService throwAutoTestService,
                        int count
        ) {
                return throwAutoTestService.start(source, count);
        }

        private static int executeCancelThrowTest(ServerCommandSource source, ThrowAutoTestService throwAutoTestService) {
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
                        long seed,
                        int throwCount
        ) {
                int created = executeCreateCourse(source, generator, courseManager, seed);
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
                        false
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
}
