package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.command.RoundLifecycleCommands;
import com.mcdg.data.Course;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CourseStructureBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks incremental, non-blocking builds of challenge courses.
 * Builds one hole per server tick so the server thread never freezes.
 */
public final class ChallengeCourseBuildTracker {
    private static final Map<UUID, PendingBuild> PENDING_BUILDS = new ConcurrentHashMap<>();

    private ChallengeCourseBuildTracker() {
    }

    private record PendingBuild(
            UUID courseId,
            TickIncrementalCoursePlacer placer,
            ServerWorld world,
            Course course,
            ServerPlayerEntity player,
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation
    ) {
    }

    /**
     * Replaces the normal course banner and sign at each tee with challenge-themed
     * versions and auto-populated hole text.
     */
    private static void placeChallengeCourseTheme(ServerWorld world, Course course, PlacedCourseState placed, ChallengeCourseType type) {
        CourseStructureBuilder.applyChallengeTheme(world, course, placed, type);
    }

    public static void startBuild(
            UUID courseId,
            ServerWorld world,
            BlockPos anchor,
            Course course,
            ServerPlayerEntity player,
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            RoundPresentationService roundPresentationService,
            boolean skipRoundPresentation
    ) {
        if (PENDING_BUILDS.containsKey(courseId)) {
            player.sendMessage(Text.literal("This challenge course is already being built.")
                    .formatted(Formatting.YELLOW));
            return;
        }

        CoursePlacementService placerService = new CoursePlacementService();
        TickIncrementalCoursePlacer placer = new TickIncrementalCoursePlacer(
                placerService, world, anchor, course, true,
                msg -> player.sendMessage(Text.literal(msg).formatted(Formatting.AQUA)),
                false,
                false
        );

        PENDING_BUILDS.put(courseId, new PendingBuild(
                courseId, placer, world, course, player, source,
                courseManager, roundStateManager, roundPresentationService, skipRoundPresentation
        ));

        player.sendMessage(Text.literal("Building challenge course: " + course.name())
                .formatted(Formatting.LIGHT_PURPLE));
        McdgMod.LOGGER.info("Queued incremental build for challenge course {} for player {}",
                courseId, player.getName().getString());
    }

    /**
     * Cancels an in-progress challenge course build and rolls back any partial placement.
     */
    public static boolean cancelBuild(UUID courseId) {
        PendingBuild build = PENDING_BUILDS.remove(courseId);
        if (build == null) {
            return false;
        }
        build.placer().cancel();
        McdgMod.LOGGER.info("Cancelled challenge course build for {}", courseId);
        return true;
    }

    public static void tick(MinecraftServer server) {
        PENDING_BUILDS.values().removeIf(build -> {
            ServerPlayerEntity player = build.player();
            UUID playerId = player.getUuid();
            if (player.isRemoved() || server.getPlayerManager().getPlayer(playerId) != player) {
                McdgMod.LOGGER.info("Player went offline during challenge course build for {}; cancelling", build.courseId());
                build.placer().cancel();
                return true;
            }

            build.placer().tick();

            if (build.placer().isFailed()) {
                player.sendMessage(Text.literal("Failed to build challenge course: " + build.placer().getFailureMessage())
                        .formatted(Formatting.RED));
                McdgMod.LOGGER.error("Challenge course build failed for {}", build.courseId());
                return true;
            }

            if (build.placer().isDone()) {
                AutoCourseService.AutoCourseScenarioResult result = build.placer().getResult();
                PlacedCourseState placedState = result.placedState();
                Course builtCourse = result.course();

                build.courseManager().setActiveCourse(builtCourse);
                build.courseManager().setPlacedCourseState(placedState);
                build.courseManager().setActiveChallengeCourseId(build.courseId());

                LostCourseStorage.savePlacedState(server, build.courseId(), placedState);

                ChallengeCourseManager.getCatalog().ifPresent(catalog -> {
                    catalog.markCourseAsPlaced(build.courseId());
                    catalog.save(server);
                });

                // Apply challenge course visual theme
                ChallengeCourseManager.getLostCourse(build.courseId()).ifPresent(lostCourse -> {
                    placeChallengeCourseTheme(build.world(), builtCourse, placedState, lostCourse.type());
                });

                player.sendMessage(Text.literal("Challenge course built. Starting round...")
                        .formatted(Formatting.GREEN));

                int startResult = RoundLifecycleCommands.executeResumeCourse(
                        build.source(),
                        build.courseManager(),
                        build.roundStateManager(),
                        build.roundPresentationService(),
                        build.skipRoundPresentation(),
                        List.of(player)
                );

                // Start boss hole mob spawning if this is a boss hole
                if (startResult == 1) {
                    ChallengeCourseManager.getLostCourse(build.courseId()).ifPresent(lostCourse ->
                            BossMobSpawner.startSpawningIfBossHole(build.courseId(), player, placedState, lostCourse.type())
                    );
                }

                McdgMod.LOGGER.info("Challenge course build completed for {}; round start result={}",
                        build.courseId(), startResult);
                return true;
            }

            return false;
        });
    }
}
