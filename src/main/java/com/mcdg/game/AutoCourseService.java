package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.BasketPoint;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.data.TeePoint;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.CoursePlacementConfig;
import com.mcdg.world.PlacementUtils;
import com.mcdg.world.HoleLayoutValidator;
import com.mcdg.game.PlacedCourseState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class AutoCourseService {
    private static final int HOLE_COUNT = 9;
    private static final int TICKS_BETWEEN_HOLES = 20;
    private static final int MIN_DISTANCE_FEET = 90;
    private static final int MAX_DISTANCE_FEET = 1400;
    private static final int PAR3_MAX_FEET = 450;
    private static final int PAR4_MAX_FEET = 900;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;
    private static final int COURSE_RADIUS_MIN = 80;
    private static final int COURSE_RADIUS_MAX = 160;
    private static final int HOLE_DIST_MIN_BLOCKS = 60;
    private static final int HOLE_DIST_MAX_BLOCKS = 200;
    private static final int FINAL_HOLE_HUB_CLEARANCE = 40;
    private static final int FINAL_HOLE_CORNER_DIAGONAL = 50;
    private static final int ANGLE_JITTER_DEG = 18;

    private final CoursePlacementService placementService;
    private final CoursePlacementValidator placementValidator;
    private final PracticeCourseStorage practiceCourseStorage;
    private final CourseGenerator courseGenerator;
    private final HoleLayoutValidator layoutValidator = new HoleLayoutValidator();

    private AutoBuildState state;

    public AutoCourseService(
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            CourseGenerator courseGenerator,
            PracticeCourseStorage practiceCourseStorage
    ) {
        this.placementService = placementService;
        this.placementValidator = placementValidator;
        this.courseGenerator = courseGenerator;
        this.practiceCourseStorage = practiceCourseStorage;
    }

    public boolean isActive() {
        return state != null;
    }

    private static Text cancelButton() {
        return Text.literal("[CANCEL BUILD]").styled(style -> style
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg autocourse cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Run: /mcdg autocourse cancel")))
        );
    }

    public int executeAutoCourseNoName(ServerCommandSource source) {
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        float yaw = source.getPlayer() != null ? source.getPlayer().getYaw() : 0.0f;
        Course preview = generateOutwardConeCourse(seed, source.getPlayer().getBlockPos(), yaw, 25, 80);
        return executeAutoCourseNamed(source, preview.name());
    }

    public int executeAutoCourseNamed(ServerCommandSource source, String courseName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("autocourse must be run by a player."));
            return 0;
        }
        if (state != null) {
            source.sendFeedback(() -> Text.literal("An autocourse build is already in progress.").formatted(Formatting.YELLOW), false);
            source.sendFeedback(() -> cancelButton(), false);
            return 1;
        }
        String trimmed = courseName == null ? "" : courseName.trim();
        if (trimmed.isBlank()) {
            source.sendError(Text.literal("Course name is required. Use: /mcdg autocourse <name>"));
            return 0;
        }
        Optional<String> duplicate = findDuplicateName(source.getServer(), trimmed);
        if (duplicate.isPresent()) {
            source.sendError(Text.literal("A course named '" + duplicate.get() + "' already exists. Choose a different name."));
            return 0;
        }
        UUID playerId = player.getUuid();
        BlockPos origin = player.getBlockPos();
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        Course course = generateOutwardConeCourse(seed, origin, player.getYaw(), 25, 80);
        state = new AutoBuildState(playerId, trimmed, seed, course, origin, player.getServerWorld());

        final String name = trimmed;
        source.sendFeedback(() -> Text.literal("Auto-building course '" + name + "' (" + HOLE_COUNT + " holes) starting at your position...").formatted(Formatting.GREEN), false);
        source.sendFeedback(() -> Text.literal("Placing course holes, this may take a few seconds...").formatted(Formatting.YELLOW), false);
        return 1;
    }
    public int executeAutoCourse(ServerCommandSource source, String courseName, long seed) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("autocourse must be run by a player."));
            return 0;
        }
        if (state != null) {
            source.sendError(Text.literal("An autocourse build is already in progress. Use /mcdg autocourse cancel to stop it."));
            return 0;
        }

        String trimmed = courseName == null ? "" : courseName.trim();
        if (trimmed.isBlank()) {
            source.sendError(Text.literal("Course name is required."));
            return 0;
        }

        Optional<String> duplicate = findDuplicateName(source.getServer(), trimmed);
        if (duplicate.isPresent()) {
            source.sendError(Text.literal("A course named '" + duplicate.get() + "' already exists. Choose a different name."));
            return 0;
        }

        java.util.Random random = new java.util.Random(seed);
        Course course = generateOutwardConeCourse(seed, player.getBlockPos(), player.getYaw(), 25, 80);


        source.sendFeedback(() -> Text.literal("Auto-building course '" + trimmed + "' (" + HOLE_COUNT + " holes) starting at your position...").formatted(Formatting.GREEN), false);
        source.sendFeedback(() -> Text.literal("Placing course holes, this may take a few seconds...").formatted(Formatting.YELLOW), false);
        return 1;
    }

    public int executeCancel(ServerCommandSource source) {
        if (state == null) {
            source.sendError(Text.literal("No autocourse build in progress."));
            return 0;
        }
        rollbackAll(source.getServer());
        state = null;
        source.sendFeedback(() -> Text.literal("Autocourse build canceled and rolled back."), false);
        return 1;
    }

    public void tick(MinecraftServer server) {
        if (state == null) {
            return;
        }

        state.ticksWaited++;
        if (state.ticksWaited < TICKS_BETWEEN_HOLES) {
            return;
        }
        state.ticksWaited = 0;

        if (state.placer == null) {
            state.placer = new TickIncrementalCoursePlacer(
                    placementService, state.world, state.origin, state.course, false,
                    msg -> broadcastProgress(server, msg));
        }

        state.placer.tick();

        if (state.placer.isDone()) {
            try {
                AutoCourseScenarioResult result = state.placer.getResult();
                Course namedCourse = new Course(result.course().seed(), state.courseName, result.course().holes());
                int catalogIndex = practiceCourseStorage.saveReusable(server, namedCourse, result.placedState(), "autocourse", false);
                broadcastSuccess(server, "Course '" + state.courseName + "' built and saved as #" + catalogIndex + ". Use [LIST COURSES] to start a round.");
                broadcastListCoursesButton(server);
            } catch (Exception ex) {
                broadcastError(server, "Course build failed: " + ex.getMessage());
                McdgMod.LOGGER.error("AutoCourseService build failed", ex);
                rollbackAll(server);
            }
            state = null;
        } else if (state.placer.isFailed()) {
            broadcastError(server, "Course build failed: " + state.placer.getFailureMessage());
            McdgMod.LOGGER.error("AutoCourseService build failed", state.placer.getFailureMessage());
            rollbackAll(server);
            state = null;
        }
    }
    private void finalizeCourse(MinecraftServer server) {
        if (state == null || state.placer == null || !state.placer.isDone()) {
            state = null;
            return;
        }

        try {
            long seed = state.seed;
            String name = state.courseName;
            AutoCourseScenarioResult result = state.placer.getResult();
            Course course = new Course(seed, name, result.course().holes());
            int catalogIndex = practiceCourseStorage.saveReusable(server, course, result.placedState(), "autocourse", false);

            broadcastSuccess(server, "Course '" + name + "' built and saved as #" + catalogIndex + ". Use [LIST COURSES] to start a round.");
            broadcastListCoursesButton(server);
        } catch (Exception ex) {
            broadcastError(server, "Failed to save course: " + ex.getMessage());
            McdgMod.LOGGER.error("AutoCourseService finalize failed", ex);
            rollbackAll(server);
        }

        state = null;
    }

    private void rollbackAll(MinecraftServer server) {
        if (state == null || state.placer == null) {
            return;
        }
        java.util.Map<BlockPos, net.minecraft.block.BlockState> mergedOriginals = state.placer.getMergedOriginals();
        CoursePlacementService.evacuatePlayersFromRestoreArea(state.world, mergedOriginals);
        if (!mergedOriginals.isEmpty()) {
            for (java.util.Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : mergedOriginals.entrySet()) {
                state.world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
            }
        }
    }

    /** Result of a synchronous autotest scenario run via runSynchronousScenario(). */
    public record AutoCourseScenarioResult(Course course, PlacedCourseState placedState) {}

    /**
     * Runs the full AutoCourseService build logic synchronously from a fixed origin,
     * without needing a player or tick loop. Used by PlacementAutoTestService.
     * Callers are responsible for resetting the placed state via CoursePlacementService.resetPlacedCourse().
     */
    public AutoCourseScenarioResult runSynchronousScenario(ServerWorld world, BlockPos origin, long seed, String courseName) {
        Course course = generateOutwardConeCourse(seed, origin, 0.0f, 25, 80);
        return placeCourseIncrementally(world, origin, course, true, null);
    }

    /**
     * Places a full Course incrementally, one hole at a time, using independent terrain resolution.
     * Each hole is placed at its computed absolute position relative to the hub origin.
     * No central hub is built (skipHub=true for all holes).
     */
    public AutoCourseScenarioResult placeCourseIncrementally(ServerWorld world, BlockPos hubOrigin, Course course) {
        return placeCourseIncrementally(world, hubOrigin, course, true);
    }

    public AutoCourseScenarioResult placeCourseIncrementally(ServerWorld world, BlockPos hubOrigin, Course course, boolean skipHub) {
        return placeCourseIncrementally(world, hubOrigin, course, skipHub, null);
    }

    /**
     * Creates a tick-incremental placer for background/async course building.
     * Callers should drive {@link TickIncrementalCoursePlacer#tick()} once per server tick.
     */
    public TickIncrementalCoursePlacer createTickIncrementalPlacer(
            ServerWorld world, BlockPos hubOrigin, Course course, boolean skipHub, java.util.function.Consumer<String> progressMessage) {
        return new TickIncrementalCoursePlacer(placementService, world, hubOrigin, course, skipHub, progressMessage);
    }

    public AutoCourseScenarioResult placeCourseIncrementally(ServerWorld world, BlockPos hubOrigin, Course course, boolean skipHub, java.util.function.Consumer<String> progressMessage) {
        TickIncrementalCoursePlacer placer = new TickIncrementalCoursePlacer(
                placementService, world, hubOrigin, course, skipHub, progressMessage);
        while (!placer.isDone() && !placer.isFailed()) {
            placer.tick();
        }
        if (placer.isFailed()) {
            throw new RuntimeException(placer.getFailureMessage());
        }
        return placer.getResult();
    }

    /**
     * Generates a 9-hole Course using an outward teardrop cone layout.
     * All hole positions are relative to the origin so placeCourseIncrementally can place them.
     *
     * @param origin              the reference point (resort center or player position)
     * @param facingYaw           the direction the cone opens (degrees)
     * @param baseLineDistance    distance from origin to the base line (100-150 for resort, 25 for player)
     * @param baseLineWidth       width of the base line (80 blocks)
     */
    public Course generateOutwardConeCourse(long seed, BlockPos origin, float facingYaw, int baseLineDistance, int baseLineWidth) {
        java.util.Random random = new java.util.Random(seed);
        int signatureHoleIndex = random.nextInt(HOLE_COUNT) + 1;

        double yawRad = Math.toRadians(facingYaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = fwdZ;
        double rightZ = -fwdX;

        int baseLineHalf = baseLineWidth / 2;
        double coneAngle = Math.toRadians(30.0);
        double tanCone = Math.tan(coneAngle);

        int ox = origin.getX();
        int oz = origin.getZ();

        int baseCenterX = ox + (int) Math.round(fwdX * baseLineDistance);
        int baseCenterZ = oz + (int) Math.round(fwdZ * baseLineDistance);

        java.util.function.BiPredicate<Integer, Integer> inCone = (px, pz) -> {
            double dx = px - ox;
            double dz = pz - oz;
            double forward = dx * fwdX + dz * fwdZ;
            double right = dx * rightX + dz * rightZ;
            if (forward < baseLineDistance - 20) {
                return false;
            }
            double allowedRight = baseLineHalf + tanCone * Math.max(0, forward - baseLineDistance);
            return Math.abs(right) <= allowedRight;
        };

        // Phase-based distance ranges (blocks)
        int[] minDistBlocks = { 0, 150, 180, 200, 100, 100,  80,  70,  60 };
        int[] maxDistBlocks = { 0, 300, 400, 450, 250, 220, 180, 160, 140 };

        // Phase-based basket angle base (radians), relative to forward direction
        // Outbound (1-3): mostly forward, small spread
        // Turnaround (4-6): angled back ~60-120 degrees
        // Return (7-9): pulling back ~120-180 degrees
        double[] basketAngleBase = {
            0.0,
            0.0,            // hole 1: straight out
            0.15,           // hole 2: slight spread
            0.25,           // hole 3: more spread
            Math.PI * 0.45, // hole 4: ~80deg turn
            Math.PI * 0.55, // hole 5: ~100deg turn
            Math.PI * 0.65, // hole 6: ~117deg turn
            Math.PI * 0.70, // hole 7: ~126deg back
            Math.PI * 0.78, // hole 8: ~140deg back
            Math.PI * 0.86  // hole 9: ~155deg back toward baseline
        };

        final int MIN_HOLE_SPACING = 40;
        final int MIN_TEE_TEE_BLOCKS = 45;
        final int MIN_BASKET_BASKET_BLOCKS = 35;
        final int MIN_TEE_PREV_BASKET_BLOCKS = 20;
        final int MIN_BASKET_PREV_TEE_BLOCKS = 35;
        List<int[]> midpoints = new ArrayList<>(); // {midX, midZ}
        List<int[]> previousTees = new ArrayList<>();
        List<int[]> previousBaskets = new ArrayList<>();

        List<Hole> holes = new ArrayList<>();
        int prevBasketX = baseCenterX;
        double prevHeadingX = fwdX;
        double prevHeadingZ = fwdZ;

        // Pick the teardrop loop direction once for the whole course
        double globalAngleSign = random.nextBoolean() ? 1.0 : -1.0;
        int prevBasketZ = baseCenterZ;

        for (int i = 1; i <= HOLE_COUNT; i++) {
            int teeX, teeZ;
            double teeFwdX = (i == 1) ? fwdX : prevHeadingX;
            double teeFwdZ = (i == 1) ? fwdZ : prevHeadingZ;
            double teeRightX = teeFwdZ;
            double teeRightZ = -teeFwdX;
            if (i == 1) {
                // Hole 1 tee on base line, slight random offset
                int rightOffset = (random.nextInt(11) - 5); // -5 to +5
                teeX = baseCenterX + (int) Math.round(rightX * rightOffset);
                teeZ = baseCenterZ + (int) Math.round(rightZ * rightOffset);
            } else {
                // Sequential: tee near previous basket, stepping along previous hole trajectory
                int teeForward = 35 + random.nextInt(21); // 35-55 blocks past basket
                int teeRight = (random.nextInt(31) - 15); // +/-15 blocks
                teeX = prevBasketX + (int) Math.round(teeFwdX * teeForward + teeRightX * teeRight);
                teeZ = prevBasketZ + (int) Math.round(teeFwdZ * teeForward + teeRightZ * teeRight);
            }

            // Ensure tee is inside cone
            if (!inCone.test(teeX, teeZ)) {
                for (int attempt = 0; attempt < 10 && !inCone.test(teeX, teeZ); attempt++) {
                    teeX = baseCenterX + (int) Math.round(fwdX * (20 + random.nextInt(21)));
                    teeZ = baseCenterZ + (int) Math.round(fwdZ * (20 + random.nextInt(21)));
                }
            }

            int basketX = 0, basketZ = 0;
            boolean placed = false;
            int placementAttempts = 0;
            final int MAX_PLACEMENT_ATTEMPTS = 5;

            while (!placed && placementAttempts < MAX_PLACEMENT_ATTEMPTS) {
                placementAttempts++;
            if (i == HOLE_COUNT) {
                // Hole 9 basket lands near base line, offset >= 30 from tee1
                int hole9RightOffset = 30 + random.nextInt(31);
                if (random.nextBoolean()) {
                    hole9RightOffset = -hole9RightOffset;
                }
                int hole9Forward = random.nextInt(21);
                basketX = baseCenterX + (int) Math.round(fwdX * hole9Forward + rightX * hole9RightOffset);
                basketZ = baseCenterZ + (int) Math.round(fwdZ * hole9Forward + rightZ * hole9RightOffset);
                if (!inCone.test(basketX, basketZ)) {
                    basketX = baseCenterX + (int) Math.round(rightX * hole9RightOffset);
                    basketZ = baseCenterZ + (int) Math.round(rightZ * hole9RightOffset);
                }
            } else {
                double angleBase = basketAngleBase[i];
                double angleSign = globalAngleSign;
                double jitter = (random.nextDouble() - 0.5) * 0.4;
                double basketAngle = Math.toRadians(facingYaw) + (angleBase * angleSign) + jitter;

                int distBlocks;
                if (placementAttempts > 1) {
                    distBlocks = maxDistBlocks[i]; // use max for retries
                } else {
                    distBlocks = minDistBlocks[i] + random.nextInt(maxDistBlocks[i] - minDistBlocks[i] + 1);
                }

                basketX = teeX + (int) Math.round(Math.cos(basketAngle) * distBlocks);
                basketZ = teeZ + (int) Math.round(Math.sin(basketAngle) * distBlocks);

                // Shrink distance if out of cone
                if (!inCone.test(basketX, basketZ)) {
                    for (int attempt = 0; attempt < 10 && !inCone.test(basketX, basketZ); attempt++) {
                        distBlocks = (int) (distBlocks * 0.85);
                        basketX = teeX + (int) Math.round(Math.cos(basketAngle) * distBlocks);
                        basketZ = teeZ + (int) Math.round(Math.sin(basketAngle) * distBlocks);
                    }
                }
            }

            // Spacing checks against all previous holes
            int midX = (teeX + basketX) / 2;
            int midZ = (teeZ + basketZ) / 2;
            boolean tooClose = false;

            for (int[] prev : midpoints) {
                double dist = Math.hypot(midX - prev[0], midZ - prev[1]);
                if (dist < MIN_HOLE_SPACING) {
                    tooClose = true;
                    break;
                }
            }

            if (!tooClose) {
                for (int[] prevTee : previousTees) {
                    double dist = Math.hypot(teeX - prevTee[0], teeZ - prevTee[1]);
                    if (dist < MIN_TEE_TEE_BLOCKS) {
                        tooClose = true;
                        break;
                    }
                }
            }

            if (!tooClose) {
                for (int[] prevBasket : previousBaskets) {
                    double dist = Math.hypot(basketX - prevBasket[0], basketZ - prevBasket[1]);
                    if (dist < MIN_BASKET_BASKET_BLOCKS) {
                        tooClose = true;
                        break;
                    }
                }
            }

            if (!tooClose) {
                for (int[] prevTee : previousTees) {
                    double dist = Math.hypot(basketX - prevTee[0], basketZ - prevTee[1]);
                    if (dist < MIN_BASKET_PREV_TEE_BLOCKS) {
                        tooClose = true;
                        break;
                    }
                }
            }

            if (!tooClose) {
                for (int b = 0; b < previousBaskets.size(); b++) {
                    if (b == previousBaskets.size() - 1) continue; // allow chaining from immediate predecessor
                    int[] prevBasket = previousBaskets.get(b);
                    double dist = Math.hypot(teeX - prevBasket[0], teeZ - prevBasket[1]);
                    if (dist < MIN_TEE_PREV_BASKET_BLOCKS) {
                        tooClose = true;
                        break;
                    }
                }
            }

            if (tooClose && i > 1) {
                if (placementAttempts < MAX_PLACEMENT_ATTEMPTS) {
                    // Retry: shift tee further along trajectory and re-roll basket
                    int retryForward = 50 + random.nextInt(26);
                    int retryRight = (random.nextInt(21) - 10);
                    teeX = prevBasketX + (int) Math.round(teeFwdX * retryForward + teeRightX * retryRight);
                    teeZ = prevBasketZ + (int) Math.round(teeFwdZ * retryForward + teeRightZ * retryRight);
                    if (!inCone.test(teeX, teeZ)) {
                        teeX = prevBasketX + (int) Math.round(teeFwdX * 20);
                        teeZ = prevBasketZ + (int) Math.round(teeFwdZ * 20);
                    }
                    continue;
                } else {
                    McdgMod.LOGGER.warn("Hole {} could not achieve ideal spacing after {} attempts; accepting best position", i, MAX_PLACEMENT_ATTEMPTS);
                }
            }

            placed = true;
            }

            midpoints.add(new int[]{(teeX + basketX) / 2, (teeZ + basketZ) / 2});
            previousTees.add(new int[]{teeX, teeZ});
            previousBaskets.add(new int[]{basketX, basketZ});
            prevBasketX = basketX;
            prevBasketZ = basketZ;

            // Update trajectory heading for next hole
            double headingX = basketX - teeX;
            double headingZ = basketZ - teeZ;
            double headingLen = Math.hypot(headingX, headingZ);
            if (headingLen > 0) {
                prevHeadingX = headingX / headingLen;
                prevHeadingZ = headingZ / headingLen;
            }

            int relTeeX = teeX - ox;
            int relTeeZ = teeZ - oz;
            int relBasketX = basketX - ox;
            int relBasketZ = basketZ - oz;
            int localBasketX = basketX - teeX;
            int localBasketZ = basketZ - teeZ;

            int distanceFeet = layoutValidator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);
            distanceFeet = Math.max(MIN_DISTANCE_FEET, Math.min(MAX_DISTANCE_FEET, distanceFeet));
            int par = computePar(distanceFeet);
            int fw = MIN_FAIRWAY_WIDTH + random.nextInt(MAX_FAIRWAY_WIDTH - MIN_FAIRWAY_WIDTH + 1);
            int bh = 1 + random.nextInt(2);

            Hole hole = new Hole(
                    i,
                    par,
                    distanceFeet,
                    new TeePoint(relTeeX, 64, relTeeZ),
                    new BasketPoint(relBasketX, 64, relBasketZ, bh),
                    List.of(new FairwaySegment(0, 0, localBasketX, localBasketZ, fw)),
                    i == signatureHoleIndex ? SignatureHoleType.ISLAND_GREEN : SignatureHoleType.NONE
            );
            holes.add(hole);
        }


        String name = com.mcdg.world.SeededCourseGenerator.generateCourseName(random);
        return new Course(seed, name, holes);
    }

    private static int computePar(int distanceFeet) {
        if (distanceFeet <= PAR3_MAX_FEET) {
            return 3;
        }
        if (distanceFeet <= PAR4_MAX_FEET) {
            return 4;
        }
        return 5;
    }

    private Optional<String> findDuplicateName(MinecraftServer server, String name) {
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        for (PracticeCourseStorage.ReusableCourseEntry entry : practiceCourseStorage.listReusable(server)) {
            if (entry.name() != null && entry.name().trim().toLowerCase(java.util.Locale.ROOT).equals(normalized)) {
                return Optional.of(entry.name());
            }
        }
        return Optional.empty();
    }

    private void broadcastProgress(MinecraftServer server, String message) {
        ServerPlayerEntity player = state != null ? server.getPlayerManager().getPlayer(state.ownerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), false);
        }
    }

    private void broadcastSuccess(MinecraftServer server, String message) {
        ServerPlayerEntity player = state != null ? server.getPlayerManager().getPlayer(state.ownerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal(message).formatted(Formatting.GREEN), false);
        }
    }

    private void broadcastError(MinecraftServer server, String message) {
        ServerPlayerEntity player = state != null ? server.getPlayerManager().getPlayer(state.ownerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal(message).formatted(Formatting.RED), false);
        }
    }

    private void broadcastListCoursesButton(MinecraftServer server) {
        ServerPlayerEntity player = state != null ? server.getPlayerManager().getPlayer(state.ownerUuid) : null;
        if (player != null) {
            player.sendMessage(Text.literal("[LIST COURSES]").styled(style -> style
                    .withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg listcourses"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Run: /mcdg listcourses")))
            ), false);
        }
    }

    private static final class AutoBuildState {
        private final UUID ownerUuid;
        private final String courseName;
        private final long seed;
        private final Course course;
        private final BlockPos origin;
        private final ServerWorld world;
        private int ticksWaited = TICKS_BETWEEN_HOLES;
        private TickIncrementalCoursePlacer placer = null;

        private AutoBuildState(UUID ownerUuid, String courseName, long seed, Course course, BlockPos origin, ServerWorld world) {
            this.ownerUuid = ownerUuid;
            this.courseName = courseName;
            this.seed = seed;
            this.course = course;
            this.origin = origin;
            this.world = world;
        }
    }

    private static final class HoleSpec {
        private final int holeIndex;
        private final int teeX;
        private final int teeZ;
        private final int basketX;
        private final int basketZ;
        private final int distanceFeet;
        private final int par;
        private final int fairwayWidth;
        private final int basketHeight;
        private final long seed;

        private HoleSpec(int holeIndex, int teeX, int teeZ, int basketX, int basketZ,
                         int distanceFeet, int par, int fairwayWidth, int basketHeight, long seed) {
            this.holeIndex = holeIndex;
            this.teeX = teeX;
            this.teeZ = teeZ;
            this.basketX = basketX;
            this.basketZ = basketZ;
            this.distanceFeet = distanceFeet;
            this.par = par;
            this.fairwayWidth = fairwayWidth;
            this.basketHeight = basketHeight;
            this.seed = seed;
        }
    }
}
