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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MIN_DISTANCE_FEET = 180;
    private static final int MAX_DISTANCE_FEET = 600;
    private static final int PAR3_MAX_FEET = 400;
    private static final int PAR4_MAX_FEET = 700;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;
    private static final int COURSE_RADIUS_MIN = 80;
    private static final int COURSE_RADIUS_MAX = 160;
    private static final int HOLE_DIST_MIN_BLOCKS = 60;
    private static final int HOLE_DIST_MAX_BLOCKS = 200;
    private static final int FINAL_HOLE_RETURN_RADIUS = 40;
    private static final int ANGLE_JITTER_DEG = 18;

    private final CoursePlacementService placementService;
    private final CoursePlacementValidator placementValidator;
    private final PracticeCourseStorage practiceCourseStorage;
    private final HoleLayoutValidator layoutValidator = new HoleLayoutValidator();

    private AutoBuildState state;
    private final ConcurrentHashMap<UUID, BlockPos> pendingNameRequests = new ConcurrentHashMap<>();

    public AutoCourseService(
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            PracticeCourseStorage practiceCourseStorage
    ) {
        this.placementService = placementService;
        this.placementValidator = placementValidator;
        this.practiceCourseStorage = practiceCourseStorage;
    }

    public boolean isActive() {
        return state != null;
    }

    public int executeAutoCoursePrompt(ServerCommandSource source) {
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
        UUID playerId = player.getUuid();
        BlockPos origin = player.getBlockPos();
        pendingNameRequests.put(playerId, origin);
        source.sendFeedback(() -> Text.literal("Type a name for your course in chat and press Enter.").formatted(Formatting.GREEN), false);
        source.sendFeedback(() -> cancelNamePromptButton(), false);
        return 1;
    }
    private static Text cancelButton() {
        return Text.literal("[CANCEL BUILD]").styled(style -> style
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg autocourse cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Run: /mcdg autocourse cancel")))
        );
    }

    private static Text cancelNamePromptButton() {
        return Text.literal("[CANCEL]").styled(style -> style
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg autocourse cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Cancel naming")))
        );
    }

    /**
     * Called by the server chat event handler. If this player has a pending name request,
     * consumes the chat message as the course name and starts the build.
     * Returns true if the message was consumed (suppress normal chat broadcast).
     */
    public boolean handleChatMessage(ServerPlayerEntity player, String message) {
        UUID playerId = player.getUuid();
        BlockPos origin = pendingNameRequests.remove(playerId);
        if (origin == null) {
            return false;
        }
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isBlank()) {
            player.sendMessage(Text.literal("Course name cannot be blank. Type a name and press Enter, or click [CANCEL].").formatted(Formatting.RED), false);
            pendingNameRequests.put(playerId, origin);
            player.sendMessage(cancelNamePromptButton(), false);
            return true;
        }
        Optional<String> duplicate = findDuplicateName(player.getServer(), trimmed);
        if (duplicate.isPresent()) {
            player.sendMessage(Text.literal("A course named '" + duplicate.get() + "' already exists. Try a different name.").formatted(Formatting.RED), false);
            pendingNameRequests.put(playerId, origin);
            player.sendMessage(cancelNamePromptButton(), false);
            return true;
        }
        if (state != null) {
            player.sendMessage(Text.literal("A build is already in progress. Wait for it to finish or use /mcdg autocourse cancel.").formatted(Formatting.RED), false);
            return true;
        }
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        java.util.Random random = new java.util.Random(seed);
        int signatureHoleIndex = random.nextInt(HOLE_COUNT) + 1;
        List<HoleSpec> holeSpecs = generateHoleSpecsFromOrigin(seed, origin);
        state = new AutoBuildState(playerId, trimmed, seed, holeSpecs, player.getServerWorld());
        state.signatureHoleIndex = signatureHoleIndex;
        final String name = trimmed;
        player.sendMessage(Text.literal("Auto-building course '" + name + "' (" + HOLE_COUNT + " holes) starting at your position...").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("Building hole 1/" + HOLE_COUNT + "...").formatted(Formatting.YELLOW), false);
        return true;
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
        int signatureHoleIndex = random.nextInt(HOLE_COUNT) + 1;
        List<HoleSpec> holeSpecs = generateHoleSpecsFromOrigin(seed, player.getBlockPos());
        state = new AutoBuildState(player.getUuid(), trimmed, seed, holeSpecs, player.getServerWorld());
        state.signatureHoleIndex = signatureHoleIndex;

        source.sendFeedback(() -> Text.literal("Auto-building course '" + trimmed + "' (" + HOLE_COUNT + " holes) starting at your position...").formatted(Formatting.GREEN), false);
        source.sendFeedback(() -> Text.literal("Building hole 1/" + HOLE_COUNT + "...").formatted(Formatting.YELLOW), false);
        return 1;
    }

    public int executeCancel(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            pendingNameRequests.remove(player.getUuid());
        }
        if (state == null) {
            if (player == null || !pendingNameRequests.containsKey(player.getUuid())) {
                source.sendError(Text.literal("No autocourse build in progress."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Canceled.").formatted(Formatting.GRAY), false);
            return 1;
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

        if (state.nextHoleIndex > state.holeSpecs.size()) {
            finalizeCourse(server);
            return;
        }

        HoleSpec spec = state.holeSpecs.get(state.nextHoleIndex - 1);
        ServerWorld world = state.world;

        try {
            BlockPos center = new BlockPos(spec.teeX, 64, spec.teeZ);
            int localTeeX = 0;
            int localTeeZ = 0;
            int localBasketX = spec.basketX - spec.teeX;
            int localBasketZ = spec.basketZ - spec.teeZ;
            Hole candidate = new Hole(
                    spec.holeIndex,
                    spec.par,
                    spec.distanceFeet,
                    new TeePoint(localTeeX, 64, localTeeZ),
                    new BasketPoint(localBasketX, 64, localBasketZ, spec.basketHeight),
                    List.of(new FairwaySegment(localTeeX, localTeeZ, localBasketX, localBasketZ, spec.fairwayWidth)),
                    SignatureHoleType.NONE
            );

            Course tempCourse = new Course(spec.seed, "auto-hole-" + spec.holeIndex, List.of(candidate));
            PlacedCourseState placed = placementService.placeCourseAtFixedOrigin(world, center, tempCourse, ignored -> {}, state.globalProtectedPositions);

            BlockPos actualTee = placed.holeTees().get(spec.holeIndex);
            BlockPos actualBasket = placed.holeBaskets().get(spec.holeIndex);
            if (actualTee == null || actualBasket == null) {
                placementService.resetPlacedCourse(world, placed);
                broadcastError(server, "Hole " + spec.holeIndex + " failed: no tee/basket placed. Canceling.");
                rollbackAll(server);
                state = null;
                return;
            }

            int actualDistanceFeet = layoutValidator.distanceFeetFromBlocks(actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ());
            int effectivePar = placed.effectiveHolePars().getOrDefault(spec.holeIndex, computePar(actualDistanceFeet));
            Hole actualHole = new Hole(
                    spec.holeIndex,
                    effectivePar,
                    actualDistanceFeet,
                    new TeePoint(actualTee.getX(), actualTee.getY(), actualTee.getZ()),
                    new BasketPoint(actualBasket.getX(), actualBasket.getY(), actualBasket.getZ(), spec.basketHeight),
                    List.of(new FairwaySegment(actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ(), spec.fairwayWidth)),
                    spec.holeIndex == state.signatureHoleIndex ? SignatureHoleType.ISLAND_GREEN : SignatureHoleType.NONE
            );

            boolean isFirstHole = state.builtHoles.isEmpty();
            state.builtHoles.add(actualHole);
            for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : placed.originalBlocks().entrySet()) {
                state.mergedOriginals.putIfAbsent(entry.getKey(), entry.getValue());
            }
            state.tees.putAll(placed.holeTees());
            state.baskets.putAll(placed.holeBaskets());
            state.alternates.putAll(placed.holeAlternateAnchors());
            state.effectivePars.putAll(placed.effectiveHolePars());

            BlockPos placedTee = placed.holeTees().get(spec.holeIndex);
            BlockPos placedBasket = placed.holeBaskets().get(spec.holeIndex);
            if (placedTee != null && placedBasket != null) {
                CoursePlacementService.addProtectedColumnArea(state.globalProtectedPositions, placedTee, 2, 6);
                CoursePlacementService.addProtectedColumnArea(state.globalProtectedPositions, placedBasket.down(), 2, 8);
                int dx = placedBasket.getX() - placedTee.getX();
                int dz = placedBasket.getZ() - placedTee.getZ();
                int[] forward;
                if (Math.abs(dx) >= Math.abs(dz)) {
                    forward = new int[] { Integer.compare(dx, 0), 0 };
                } else {
                    forward = new int[] { 0, Integer.compare(dz, 0) };
                }
                BlockPos teeLamp = placedTee.add(-forward[0], 0, -forward[1]);
                CoursePlacementService.addProtectedColumnArea(state.globalProtectedPositions, teeLamp, 1, 6);
                if (isFirstHole) {
                    int[] back = new int[] { -forward[0], -forward[1] };
                    BlockPos hubApprox = placedTee.add(back[0] * 9, 0, back[1] * 9);
                    CoursePlacementService.addProtectedColumnArea(state.globalProtectedPositions, hubApprox, 9, 7);
                }
            }

            int builtIndex = state.nextHoleIndex;
            state.nextHoleIndex++;

            if (state.nextHoleIndex <= state.holeSpecs.size()) {
                broadcastProgress(server, "Built hole " + builtIndex + "/" + HOLE_COUNT + ". Building hole " + state.nextHoleIndex + "...");
            } else {
                broadcastProgress(server, "Built hole " + builtIndex + "/" + HOLE_COUNT + ". Finalizing...");
            }

        } catch (Exception ex) {
            broadcastError(server, "Hole " + state.nextHoleIndex + " failed: " + ex.getMessage() + ". Canceling.");
            McdgMod.LOGGER.error("AutoCourseService hole {} failed", state.nextHoleIndex, ex);
            rollbackAll(server);
            state = null;
        }
    }

    private void finalizeCourse(MinecraftServer server) {
        if (state == null || state.builtHoles.isEmpty()) {
            state = null;
            return;
        }

        try {
            long seed = state.seed;
            String name = state.courseName;
            Course course = new Course(seed, name, state.builtHoles);
            PlacedCourseState mergedPlaced = new PlacedCourseState(
                    state.world.getRegistryKey(),
                    state.mergedOriginals,
                    state.tees,
                    state.baskets,
                    state.alternates,
                    state.effectivePars
            );
            int catalogIndex = practiceCourseStorage.saveReusable(server, course, mergedPlaced, "autocourse", false);

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
        if (state == null) {
            return;
        }
        CoursePlacementService.evacuatePlayersFromRestoreArea(state.world, state.mergedOriginals);
        if (!state.mergedOriginals.isEmpty()) {
            for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : state.mergedOriginals.entrySet()) {
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
        java.util.Random random = new java.util.Random(seed);
        int signatureHoleIndex = random.nextInt(HOLE_COUNT) + 1;
        List<HoleSpec> holeSpecs = generateHoleSpecsFromOrigin(seed, origin);

        List<Hole> builtHoles = new ArrayList<>();
        Map<BlockPos, net.minecraft.block.BlockState> mergedOriginals = new HashMap<>();
        Set<BlockPos> globalProtectedPositions = new HashSet<>();
        Map<Integer, BlockPos> tees = new HashMap<>();
        Map<Integer, BlockPos> baskets = new HashMap<>();
        Map<Integer, BlockPos> alternates = new HashMap<>();
        Map<Integer, Integer> effectivePars = new HashMap<>();

        for (HoleSpec spec : holeSpecs) {
            BlockPos center = new BlockPos(spec.teeX, 64, spec.teeZ);
            int localBasketX = spec.basketX - spec.teeX;
            int localBasketZ = spec.basketZ - spec.teeZ;
            Hole candidate = new Hole(
                    spec.holeIndex, spec.par, spec.distanceFeet,
                    new TeePoint(0, 64, 0),
                    new BasketPoint(localBasketX, 64, localBasketZ, spec.basketHeight),
                    List.of(new FairwaySegment(0, 0, localBasketX, localBasketZ, spec.fairwayWidth)),
                    spec.holeIndex == signatureHoleIndex ? SignatureHoleType.ISLAND_GREEN : SignatureHoleType.NONE
            );
            Course tempCourse = new Course(spec.seed, courseName + "-hole-" + spec.holeIndex, List.of(candidate));
            PlacedCourseState placed = placementService.placeCourseAtFixedOrigin(world, center, tempCourse, ignored -> {}, globalProtectedPositions);

            BlockPos actualTee = placed.holeTees().get(spec.holeIndex);
            BlockPos actualBasket = placed.holeBaskets().get(spec.holeIndex);
            if (actualTee == null || actualBasket == null) {
                placementService.resetPlacedCourse(world, placed);
                for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : mergedOriginals.entrySet()) {
                    world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
                }
                throw new RuntimeException("AutoCourse hole " + spec.holeIndex + " produced no tee/basket");
            }

            int actualFeet = layoutValidator.distanceFeetFromBlocks(actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ());
            int effectivePar = placed.effectiveHolePars().getOrDefault(spec.holeIndex, computePar(actualFeet));
            Hole actualHole = new Hole(
                    spec.holeIndex, effectivePar, actualFeet,
                    new TeePoint(actualTee.getX(), actualTee.getY(), actualTee.getZ()),
                    new BasketPoint(actualBasket.getX(), actualBasket.getY(), actualBasket.getZ(), spec.basketHeight),
                    List.of(new FairwaySegment(actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ(), spec.fairwayWidth)),
                    spec.holeIndex == signatureHoleIndex ? SignatureHoleType.ISLAND_GREEN : SignatureHoleType.NONE
            );
            builtHoles.add(actualHole);

            for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : placed.originalBlocks().entrySet()) {
                mergedOriginals.putIfAbsent(entry.getKey(), entry.getValue());
            }
            tees.putAll(placed.holeTees());
            baskets.putAll(placed.holeBaskets());
            alternates.putAll(placed.holeAlternateAnchors());
            effectivePars.putAll(placed.effectiveHolePars());

            CoursePlacementService.addProtectedColumnArea(globalProtectedPositions, actualTee, 2, 6);
            CoursePlacementService.addProtectedColumnArea(globalProtectedPositions, actualBasket.down(), 2, 8);
        }

        Course course = new Course(seed, courseName, builtHoles);
        PlacedCourseState mergedState = new PlacedCourseState(world.getRegistryKey(), mergedOriginals, tees, baskets, alternates, effectivePars);
        return new AutoCourseScenarioResult(course, mergedState);
    }

    List<HoleSpec> generateHoleSpecsFromOrigin(long seed, BlockPos origin) {
        java.util.Random random = new java.util.Random(seed);
        List<HoleSpec> specs = new ArrayList<>();

        int hubX = origin.getX();
        int hubZ = origin.getZ();

        double angleStepRad = (2.0 * Math.PI) / HOLE_COUNT;

        for (int i = 1; i <= HOLE_COUNT; i++) {
            double baseAngle = (i - 1) * angleStepRad;
            double jitterRad = Math.toRadians((random.nextDouble() * 2.0 - 1.0) * ANGLE_JITTER_DEG);
            double teeAngle = baseAngle + jitterRad;

            int teeRadius = COURSE_RADIUS_MIN + random.nextInt(COURSE_RADIUS_MAX - COURSE_RADIUS_MIN + 1);
            int teeX = hubX + (int) Math.round(Math.cos(teeAngle) * teeRadius);
            int teeZ = hubZ + (int) Math.round(Math.sin(teeAngle) * teeRadius);

            int basketX, basketZ;
            if (i == HOLE_COUNT) {
                // Hole 9 basket returns close to hub (near hole 1 tee)
                double returnAngle = teeAngle + Math.PI + Math.toRadians(random.nextInt(40) - 20);
                int returnDist = FINAL_HOLE_RETURN_RADIUS / 2 + random.nextInt(FINAL_HOLE_RETURN_RADIUS / 2);
                basketX = hubX + (int) Math.round(Math.cos(returnAngle) * returnDist);
                basketZ = hubZ + (int) Math.round(Math.sin(returnAngle) * returnDist);
            } else {
                // Basket fires outward and slightly toward next tee angle
                double nextAngle = i * angleStepRad;
                double basketAngle = teeAngle + (nextAngle - teeAngle) * 0.5 + jitterRad * 0.5;
                int distBlocks = HOLE_DIST_MIN_BLOCKS + random.nextInt(HOLE_DIST_MAX_BLOCKS - HOLE_DIST_MIN_BLOCKS + 1);
                basketX = teeX + (int) Math.round(Math.cos(basketAngle) * distBlocks);
                basketZ = teeZ + (int) Math.round(Math.sin(basketAngle) * distBlocks);
            }

            int actualFeet = layoutValidator.distanceFeetFromBlocks(teeX, teeZ, basketX, basketZ);
            actualFeet = Math.max(MIN_DISTANCE_FEET, Math.min(MAX_DISTANCE_FEET, actualFeet));
            int fw = MIN_FAIRWAY_WIDTH + random.nextInt(MAX_FAIRWAY_WIDTH - MIN_FAIRWAY_WIDTH + 1);
            int bh = 1 + random.nextInt(2);
            long holeSeed = ((long) teeX << 32) ^ (teeZ * 341873128712L) ^ (i * 73428767L);

            HoleSpec spec = new HoleSpec(i, teeX, teeZ, basketX, basketZ, actualFeet, computePar(actualFeet), fw, bh, holeSeed);
            specs.add(spec);
        }

        return specs;
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
        private final List<HoleSpec> holeSpecs;
        private final ServerWorld world;
        private int nextHoleIndex = 1;
        private int ticksWaited = TICKS_BETWEEN_HOLES;
        private int signatureHoleIndex = 1;
        private final List<Hole> builtHoles = new ArrayList<>();
        private final Map<BlockPos, net.minecraft.block.BlockState> mergedOriginals = new HashMap<>();
        private final Set<BlockPos> globalProtectedPositions = new HashSet<>();
        private final Map<Integer, BlockPos> tees = new HashMap<>();
        private final Map<Integer, BlockPos> baskets = new HashMap<>();
        private final Map<Integer, BlockPos> alternates = new HashMap<>();
        private final Map<Integer, Integer> effectivePars = new HashMap<>();

        private AutoBuildState(UUID ownerUuid, String courseName, long seed, List<HoleSpec> holeSpecs, ServerWorld world) {
            this.ownerUuid = ownerUuid;
            this.courseName = courseName;
            this.seed = seed;
            this.holeSpecs = holeSpecs;
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
