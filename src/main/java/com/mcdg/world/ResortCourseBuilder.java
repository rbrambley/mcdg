package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.TickIncrementalCoursePlacer;
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

public final class ResortCourseBuilder {
    private static final int SURROUND_COURSE_COUNT = 3;
    private static final int TICKS_BEFORE_FIRST_BUILD = 100;
    private static final int TICKS_BETWEEN_COURSES = 200;
    private static final int RESORT_SAFETY_RADIUS = 60;

    private static ServerBossBar progressBar = null;
    private static List<ResortCoursePlacement.Candidate> pendingCandidates = null;
    private static ServerWorld targetWorld = null;
    private static BlockPos resortCenter = null;
    private static AutoCourseService autoCourseService = null;
    private static PracticeCourseStorage practiceCourseStorage = null;
    private static MinecraftServer server = null;
    private static int builtCourses = 0;
    private static int attemptedCandidates = 0;
    private static int ticksWaitingForPlayer = 0;
    private static int ticksSinceLastBuild = 0;
    private static boolean playerHasJoined = false;
    private static boolean buildStarted = false;
    private static Random random = null;
    private static boolean isBuilding = false;
    private static TickIncrementalCoursePlacer activePlacer = null;
    private static BlockPos currentHubOrigin = null;

    private ResortCourseBuilder() {}

    public static void queueSurroundCourses(
            ServerWorld world,
            BlockPos center,
            AutoCourseService courseService,
            PracticeCourseStorage storage,
            MinecraftServer minecraftServer
    ) {
        reset();
        targetWorld = world;
        resortCenter = center;
        autoCourseService = courseService;
        practiceCourseStorage = storage;
        server = minecraftServer;
        builtCourses = 0;
        attemptedCandidates = 0;
        ticksWaitingForPlayer = 0;
        ticksSinceLastBuild = 0;
        playerHasJoined = false;
        buildStarted = false;
        random = new Random(world.getSeed());
        isBuilding = true;

        pendingCandidates = new ArrayList<>(ResortCoursePlacement.selectCourseAnchors(world, center, random));
        if (pendingCandidates.isEmpty()) {
            McdgMod.LOGGER.warn("No suitable surround course locations found for auto-build.");
            isBuilding = false;
            return;
        }

        McdgMod.LOGGER.info("Queued {} candidate locations for {} resort surround courses. Building will start after a player joins.",
                pendingCandidates.size(), SURROUND_COURSE_COUNT);
    }

    public static void tick(MinecraftServer minecraftServer) {
        if (!isBuilding) return;

        if (!playerHasJoined) {
            if (!minecraftServer.getPlayerManager().getPlayerList().isEmpty()) {
                playerHasJoined = true;
                McdgMod.LOGGER.info("Player joined. Waiting {} ticks before starting course builds...", TICKS_BEFORE_FIRST_BUILD);
            }
            return;
        }

        if (!buildStarted) {
            ticksWaitingForPlayer++;
            if (ticksWaitingForPlayer < TICKS_BEFORE_FIRST_BUILD) return;
            buildStarted = true;
            createProgressBar(minecraftServer);
            broadcastToPlayers(Text.literal("Building resort surround courses in the background...").formatted(Formatting.YELLOW));
        }

        // Drive active tick-incremental placer one hole per tick
        if (activePlacer != null) {
            activePlacer.tick();

            if (progressBar != null) {
                float holeProgress = activePlacer.getProgress();
                float totalProgress = (builtCourses + holeProgress) / SURROUND_COURSE_COUNT;
                progressBar.setPercent(totalProgress);
                int holeNum = Math.min(9, (int) Math.ceil(holeProgress * 9));
                progressBar.setName(Text.literal("Building resort courses... " + builtCourses + "/" + SURROUND_COURSE_COUNT + " (hole " + holeNum + "/9)").formatted(Formatting.GREEN));
            }

            if (activePlacer.isDone()) {
                try {
                    AutoCourseService.AutoCourseScenarioResult result = activePlacer.getResult();
                    McdgMod.LOGGER.info("Placed course '{}' with {} holes", result.course().name(), result.course().holes().size());
                    int catalogIndex = practiceCourseStorage.saveReusable(
                            server, result.course(), result.placedState(), "resort-surround", false);
                    builtCourses++;
                    McdgMod.LOGGER.info("SUCCESS: Resort course {} placed at ({}, {}), catalog #{}",
                            builtCourses, currentHubOrigin.getX(), currentHubOrigin.getZ(), catalogIndex);
                    updateProgressBar();
                    broadcastToPlayers(Text.literal("Resort course " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built!").formatted(Formatting.GREEN));
                } catch (Exception ex) {
                    McdgMod.LOGGER.warn("FAILED: Course at ({}, {}) failed to save: {}",
                            currentHubOrigin.getX(), currentHubOrigin.getZ(), ex.getMessage(), ex);
                }
                activePlacer = null;
                currentHubOrigin = null;
                ticksSinceLastBuild = 0;
            } else if (activePlacer.isFailed()) {
                McdgMod.LOGGER.warn("FAILED: Course at ({}, {}) failed: {}",
                        currentHubOrigin.getX(), currentHubOrigin.getZ(), activePlacer.getFailureMessage());
                activePlacer = null;
                currentHubOrigin = null;
                ticksSinceLastBuild = 0;
            }
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
        if (ticksSinceLastBuild < TICKS_BETWEEN_COURSES) return;
        ticksSinceLastBuild = 0;

        ResortCoursePlacement.Candidate candidate = pendingCandidates.get(attemptedCandidates);
        attemptedCandidates++;

        BlockPos hubOrigin = candidate.pos();
        currentHubOrigin = hubOrigin;
        double angle = candidate.angle();
        long seed = random.nextLong();
        float facingYaw = (float) (-90.0 - Math.toDegrees(angle));

        McdgMod.LOGGER.info("Attempting resort course {} of {} at hub ({}, {}) with facingYaw={} (candidate {} of {})",
                builtCourses + 1, SURROUND_COURSE_COUNT,
                hubOrigin.getX(), hubOrigin.getZ(),
                facingYaw, attemptedCandidates, pendingCandidates.size());

        try {
            var course = autoCourseService.generateOutwardConeCourse(seed, hubOrigin, facingYaw, 25, 80);
            McdgMod.LOGGER.info("Generated course '{}' with {} holes", course.name(), course.holes().size());

            if (resortCenter != null && courseIntersectsResort(course, hubOrigin, resortCenter)) {
                McdgMod.LOGGER.warn("Skipping candidate at ({}, {}): course would intersect resort area",
                        hubOrigin.getX(), hubOrigin.getZ());
                currentHubOrigin = null;
                return;
            }

            activePlacer = autoCourseService.createTickIncrementalPlacer(
                    targetWorld, hubOrigin, course, true,
                    msg -> McdgMod.LOGGER.info("Resort course progress: {}", msg));

        } catch (Exception ex) {
            McdgMod.LOGGER.warn("FAILED: Course at ({}, {}) failed: {}",
                    hubOrigin.getX(), hubOrigin.getZ(), ex.getMessage(), ex);
            currentHubOrigin = null;
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        if (!isBuilding || targetWorld == null
                || !player.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey())) {
            return;
        }

        if (!playerHasJoined) {
            playerHasJoined = true;
            McdgMod.LOGGER.info("Player joined (via join event). Waiting {} ticks before starting course builds...", TICKS_BEFORE_FIRST_BUILD);
        }

        if (progressBar != null) {
            progressBar.addPlayer(player);
            if (buildStarted) {
                player.sendMessage(Text.literal(
                        "Resort courses are being built in the background (" + builtCourses + "/" + SURROUND_COURSE_COUNT + " done)."
                ).formatted(Formatting.YELLOW), false);
            }
        }
    }

    public static boolean isBuilding() { return isBuilding; }

    private static void createProgressBar(MinecraftServer minecraftServer) {
        progressBar = new ServerBossBar(
                Text.literal("Building resort courses... 0/" + SURROUND_COURSE_COUNT),
                BossBar.Color.GREEN, BossBar.Style.PROGRESS);
        progressBar.setPercent(0.0f);
        for (ServerPlayerEntity player : minecraftServer.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey().equals(targetWorld.getRegistryKey())) {
                progressBar.addPlayer(player);
            }
        }
    }

    private static void updateProgressBar() {
        if (progressBar == null) return;
        float percent = (float) builtCourses / SURROUND_COURSE_COUNT;
        progressBar.setPercent(percent);
        progressBar.setName(Text.literal("Building resort courses... " + builtCourses + "/" + SURROUND_COURSE_COUNT).formatted(Formatting.GREEN));
    }

    private static boolean courseIntersectsResort(Course course, BlockPos hubOrigin, BlockPos resortCenter) {
        int minSafeDistSq = RESORT_SAFETY_RADIUS * RESORT_SAFETY_RADIUS;
        for (var hole : course.holes()) {
            int absTeeX = hubOrigin.getX() + hole.tee().x();
            int absTeeZ = hubOrigin.getZ() + hole.tee().z();
            int absBasketX = hubOrigin.getX() + hole.basket().x();
            int absBasketZ = hubOrigin.getZ() + hole.basket().z();
            int teeDx = absTeeX - resortCenter.getX();
            int teeDz = absTeeZ - resortCenter.getZ();
            int basketDx = absBasketX - resortCenter.getX();
            int basketDz = absBasketZ - resortCenter.getZ();
            if (teeDx * teeDx + teeDz * teeDz < minSafeDistSq) {
                McdgMod.LOGGER.warn("Hole {} tee at ({}, {}) is within {} blocks of resort",
                        hole.index(), absTeeX, absTeeZ, RESORT_SAFETY_RADIUS);
                return true;
            }
            if (basketDx * basketDx + basketDz * basketDz < minSafeDistSq) {
                McdgMod.LOGGER.warn("Hole {} basket at ({}, {}) is within {} blocks of resort",
                        hole.index(), absBasketX, absBasketZ, RESORT_SAFETY_RADIUS);
                return true;
            }
        }
        return false;
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
                progressBar.setName(Text.literal("Resort courses complete! " + builtCourses + "/" + SURROUND_COURSE_COUNT).formatted(Formatting.GREEN));
                progressBar.setPercent(1.0f);
                progressBar.setColor(BossBar.Color.GREEN);
                broadcastToPlayers(Text.literal("All " + builtCourses + " resort courses built! Use /mcdg listcourses to play.").formatted(Formatting.GREEN));
            } else {
                progressBar.setName(Text.literal("Resort courses: " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built").formatted(Formatting.YELLOW));
                progressBar.setColor(BossBar.Color.YELLOW);
                broadcastToPlayers(Text.literal("Resort building finished: " + builtCourses + "/" + SURROUND_COURSE_COUNT + " built.").formatted(Formatting.YELLOW));
            }
        }
        McdgMod.LOGGER.info("Finished building resort courses: {} of {} succeeded", builtCourses, SURROUND_COURSE_COUNT);

        if (builtCourses >= SURROUND_COURSE_COUNT && server != null) {
            WorldSpawnHandler.markCoursesBuilt(server);
        }

        isBuilding = false;
        pendingCandidates = null;
    }

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
        ticksWaitingForPlayer = 0;
        ticksSinceLastBuild = 0;
        playerHasJoined = false;
        buildStarted = false;
        random = null;
        activePlacer = null;
        currentHubOrigin = null;
    }
}
