package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.PracticeCourseStorage;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Background builder for resort surround courses.
 * Processes one course per server tick to avoid blocking world startup.
 * Displays a ServerBossBar to players showing build progress.
 */
public final class ResortCourseBuilder {
    private static final int SURROUND_COURSE_COUNT = 3;
    private static final int CANDIDATE_COUNT = 6; // Increased from 3 for more fallback options

    private static ServerBossBar progressBar = null;
    private static List<ResortCoursePlacement.Candidate> pendingCandidates = null;
    private static ServerWorld targetWorld = null;
    private static BlockPos resortCenter = null;
    private static AutoCourseService autoCourseService = null;
    private static PracticeCourseStorage practiceCourseStorage = null;
    private static MinecraftServer server = null;
    private static int builtCourses = 0;
    private static int attemptedCandidates = 0;
    private static Random random = null;
    private static boolean isBuilding = false;

    private ResortCourseBuilder() {}

    /**
     * Queues surround courses for background building after the resort is placed.
     * Call this from WorldSpawnHandler.onServerStarted after building the resort.
     */
    public static void queueSurroundCourses(
            ServerWorld world,
            BlockPos center,
            AutoCourseService courseService,
            PracticeCourseStorage storage,
            MinecraftServer minecraftServer
    ) {
        targetWorld = world;
        resortCenter = center;
        autoCourseService = courseService;
        practiceCourseStorage = storage;
        server = minecraftServer;
        builtCourses = 0;
        attemptedCandidates = 0;
        random = new Random(world.getSeed());
        isBuilding = true;

        pendingCandidates = new ArrayList<>(ResortCoursePlacement.selectCourseAnchors(world, center, random));
        if (pendingCandidates.isEmpty()) {
            McdgMod.LOGGER.warn("No suitable surround course locations found for auto-build.");
            isBuilding = false;
            return;
        }

        // Create boss bar for progress tracking
        progressBar = new ServerBossBar(
                Text.literal("Building resort courses... 0/" + SURROUND_COURSE_COUNT),
                BossBar.Color.GREEN,
                BossBar.Style.PROGRESS
        );
        progressBar.setPercent(0.0f);

        // Add any currently connected players
        for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                progressBar.addPlayer(player);
            }
        }

        McdgMod.LOGGER.info("Queued {} candidate locations for {} resort surround courses", 
                pendingCandidates.size(), SURROUND_COURSE_COUNT);
    }

    /**
     * Called every server tick to process the next course in the queue.
     * Register this in McdgMod via ServerTickEvents.END_SERVER_TICK.
     */
    public static void tick(MinecraftServer minecraftServer) {
        if (!isBuilding || pendingCandidates == null || pendingCandidates.isEmpty()) {
            return;
        }

        if (builtCourses >= SURROUND_COURSE_COUNT) {
            finishBuilding();
            return;
        }

        if (attemptedCandidates >= pendingCandidates.size()) {
            McdgMod.LOGGER.warn("Exhausted all {} candidate locations, built {} of {} courses",
                    pendingCandidates.size(), builtCourses, SURROUND_COURSE_COUNT);
            finishBuilding();
            return;
        }

        // Try the next candidate
        ResortCoursePlacement.Candidate candidate = pendingCandidates.get(attemptedCandidates);
        attemptedCandidates++;

        BlockPos hubOrigin = candidate.pos();
        double angle = candidate.angle();
        long seed = random.nextLong();
        float facingYaw = (float) Math.toDegrees(angle);

        try {
            // Use compact cone with baseLineDistance=25, same as player auto-build
            var course = autoCourseService.generateOutwardConeCourse(seed, hubOrigin, facingYaw, 25, 80);
            AutoCourseService.AutoCourseScenarioResult result = autoCourseService.placeCourseIncrementally(
                    targetWorld, hubOrigin, course, true
            );
            int catalogIndex = practiceCourseStorage.saveReusable(
                    server, result.course(), result.placedState(), "resort-surround", false
            );
            builtCourses++;
            McdgMod.LOGGER.info("Resort surround course {} placed at ({}, {}), saved as catalog #{}",
                    builtCourses, hubOrigin.getX(), hubOrigin.getZ(), catalogIndex);

            // Update boss bar
            updateProgressBar();

        } catch (Exception ex) {
            McdgMod.LOGGER.warn("Surround course candidate at ({}, {}) failed: {}",
                    hubOrigin.getX(), hubOrigin.getZ(), ex.getMessage());
            // Continue to next candidate on next tick
        }
    }

    /**
     * Adds a newly joined player to the progress bar if a build is in progress.
     * Call this from ServerPlayConnectionEvents.JOIN.
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (isBuilding && progressBar != null && targetWorld != null
                && player.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey())) {
            progressBar.addPlayer(player);
        }
    }

    /**
     * Returns true if a background resort course build is currently in progress.
     */
    public static boolean isBuilding() {
        return isBuilding;
    }

    private static void updateProgressBar() {
        if (progressBar == null) return;

        float percent = (float) builtCourses / SURROUND_COURSE_COUNT;
        progressBar.setPercent(percent);
        progressBar.setName(Text.literal(
                "Building resort courses... " + builtCourses + "/" + SURROUND_COURSE_COUNT
        ).formatted(Formatting.GREEN));
    }

    private static void finishBuilding() {
        if (progressBar != null) {
            if (builtCourses >= SURROUND_COURSE_COUNT) {
                progressBar.setName(Text.literal(
                        "Resort courses complete! " + builtCourses + "/" + SURROUND_COURSE_COUNT
                ).formatted(Formatting.GREEN));
                progressBar.setPercent(1.0f);
                progressBar.setColor(BossBar.Color.GREEN);
            } else {
                progressBar.setName(Text.literal(
                        "Resort courses: " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built"
                ).formatted(Formatting.YELLOW));
                progressBar.setColor(BossBar.Color.YELLOW);
            }

            // Keep the bar visible for a few seconds, then remove
            // We can't delay easily here, so we'll let it stay until next tick
            // A cleaner approach would be a countdown, but for simplicity:
            // The bar will be removed when players leave or on next server start
        }

        McdgMod.LOGGER.info("Finished building resort courses: {} of {} succeeded",
                builtCourses, SURROUND_COURSE_COUNT);

        // Don't clear immediately - let players see completion
        // The bar will be recreated on next resort build
        isBuilding = false;
        pendingCandidates = null;
    }

    /**
     * Clean up the progress bar. Call on server stopping.
     */
    public static void reset() {
        if (progressBar != null) {
            progressBar.clearPlayers();
            progressBar = null;
        }
        isBuilding = false;
        pendingCandidates = null;
        targetWorld = null;
        resortCenter = null;
        autoCourseService = null;
        practiceCourseStorage = null;
        server = null;
        builtCourses = 0;
        attemptedCandidates = 0;
        random = null;
    }
}
