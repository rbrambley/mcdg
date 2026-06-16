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
 * Processes one course every N server ticks to avoid blocking world startup.
 * Displays a ServerBossBar to players showing build progress.
 */
public final class ResortCourseBuilder {
    private static final int SURROUND_COURSE_COUNT = 3;
    private static final int TICKS_BETWEEN_COURSES = 20; // 1 second at 20 TPS
    private static final int CHAT_NOTIFY_INTERVAL_TICKS = 100; // 5 seconds

    private static ServerBossBar progressBar = null;
    private static List<ResortCoursePlacement.Candidate> pendingCandidates = null;
    private static ServerWorld targetWorld = null;
    private static AutoCourseService autoCourseService = null;
    private static PracticeCourseStorage practiceCourseStorage = null;
    private static MinecraftServer server = null;
    private static int builtCourses = 0;
    private static int attemptedCandidates = 0;
    private static int ticksSinceLastBuild = 0;
    private static int ticksSinceLastChat = 0;
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
        reset(); // Clear any stale state from previous sessions

        targetWorld = world;
        autoCourseService = courseService;
        practiceCourseStorage = storage;
        server = minecraftServer;
        builtCourses = 0;
        attemptedCandidates = 0;
        ticksSinceLastBuild = TICKS_BETWEEN_COURSES; // Start immediately on first tick
        ticksSinceLastChat = 0;
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

        McdgMod.LOGGER.info("Queued {} candidate locations for {} resort surround courses (compact cone, baseLineDistance=25)", 
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

        ticksSinceLastBuild++;
        if (ticksSinceLastBuild < TICKS_BETWEEN_COURSES) {
            return;
        }
        ticksSinceLastBuild = 0;

        // Try the next candidate
        ResortCoursePlacement.Candidate candidate = pendingCandidates.get(attemptedCandidates);
        attemptedCandidates++;

        BlockPos hubOrigin = candidate.pos();
        double angle = candidate.angle();
        long seed = random.nextLong();

        // CRITICAL FIX: Convert standard math angle to Minecraft yaw convention.
        // candidate.angle() is a standard math angle (0 = east, CCW).
        // Minecraft yaw: 0 = south, -90 = east, 180 = north, 90 = west.
        // Conversion: mcYaw = -90 - mathAngle_degrees
        float facingYaw = (float) (-90.0 - Math.toDegrees(angle));

        McdgMod.LOGGER.info("Attempting resort course {} of {} at hub ({}, {}) with facingYaw={:.1f} (candidate {} of {})",
                builtCourses + 1, SURROUND_COURSE_COUNT,
                hubOrigin.getX(), hubOrigin.getZ(),
                facingYaw, attemptedCandidates, pendingCandidates.size());

        try {
            // Use compact cone with baseLineDistance=25, same as player auto-build
            var course = autoCourseService.generateOutwardConeCourse(seed, hubOrigin, facingYaw, 25, 80);
            McdgMod.LOGGER.info("Generated course '{}' with {} holes for hub ({}, {})",
                    course.name(), course.holes().size(), hubOrigin.getX(), hubOrigin.getZ());

            AutoCourseService.AutoCourseScenarioResult result = autoCourseService.placeCourseIncrementally(
                    targetWorld, hubOrigin, course, true
            );
            McdgMod.LOGGER.info("Placed course '{}' with {} holes at hub ({}, {})",
                    result.course().name(), result.course().holes().size(), hubOrigin.getX(), hubOrigin.getZ());

            int catalogIndex = practiceCourseStorage.saveReusable(
                    server, result.course(), result.placedState(), "resort-surround", false
            );
            builtCourses++;
            McdgMod.LOGGER.info("SUCCESS: Resort surround course {} placed at ({}, {}), saved as catalog #{}",
                    builtCourses, hubOrigin.getX(), hubOrigin.getZ(), catalogIndex);

            // Update boss bar
            updateProgressBar();

            // Send chat notification to all players
            broadcastToPlayers(Text.literal(
                    "Resort course " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built!"
            ).formatted(Formatting.GREEN));

        } catch (Exception ex) {
            McdgMod.LOGGER.warn("FAILED: Surround course candidate at ({}, {}) failed: {}",
                    hubOrigin.getX(), hubOrigin.getZ(), ex.getMessage(), ex);
            // Continue to next candidate after delay
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
            player.sendMessage(Text.literal(
                    "Resort courses are being built in the background (" + builtCourses + "/" + SURROUND_COURSE_COUNT + " done)."
            ).formatted(Formatting.YELLOW), false);
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

    private static void broadcastToPlayers(Text message) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (targetWorld != null && player.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey())) {
                player.sendMessage(message, false);
            }
        }
    }

    private static void finishBuilding() {
        if (progressBar != null) {
            if (builtCourses >= SURROUND_COURSE_COUNT) {
                progressBar.setName(Text.literal(
                        "Resort courses complete! " + builtCourses + "/" + SURROUND_COURSE_COUNT
                ).formatted(Formatting.GREEN));
                progressBar.setPercent(1.0f);
                progressBar.setColor(BossBar.Color.GREEN);
                broadcastToPlayers(Text.literal(
                        "All " + builtCourses + " resort courses have been built! Use /mcdg listcourses to play."
                ).formatted(Formatting.GREEN));
            } else {
                progressBar.setName(Text.literal(
                        "Resort courses: " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built"
                ).formatted(Formatting.YELLOW));
                progressBar.setColor(BossBar.Color.YELLOW);
                broadcastToPlayers(Text.literal(
                        "Resort course building finished: " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built."
                ).formatted(Formatting.YELLOW));
            }
        }

        McdgMod.LOGGER.info("Finished building resort courses: {} of {} succeeded",
                builtCourses, SURROUND_COURSE_COUNT);

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
        autoCourseService = null;
        practiceCourseStorage = null;
        server = null;
        builtCourses = 0;
        attemptedCandidates = 0;
        ticksSinceLastBuild = 0;
        ticksSinceLastChat = 0;
        random = null;
    }
}
