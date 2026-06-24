package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.BasketPoint;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.TeePoint;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.BiomeTheme;
import com.mcdg.world.BiomeThemeResolver;
import com.mcdg.world.CoursePlacementConfig;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CourseStructureBuilder;
import com.mcdg.world.HoleLayoutValidator;
import com.mcdg.world.PlacementUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Places one hole per {@link #tick()} call instead of all holes synchronously.
 * This spreads the heavy placement work across server ticks to avoid lag spikes.
 */
public final class TickIncrementalCoursePlacer {
    private final CoursePlacementService placementService;
    private final HoleLayoutValidator layoutValidator;
    private final ServerWorld world;
    private final BlockPos hubOrigin;
    private final Course course;
    private final Consumer<String> progressMessage;
    private final boolean skipHub;
    private final boolean skipWaterEstimation;

    private final List<Hole> builtHoles = new ArrayList<>();
    private final Map<BlockPos, BlockState> mergedOriginals = new HashMap<>();
    private final Set<BlockPos> globalProtectedPositions = new HashSet<>();
    private final Map<Integer, BlockPos> tees = new HashMap<>();
    private final Map<Integer, BlockPos> baskets = new HashMap<>();
    private final Map<Integer, BlockPos> alternates = new HashMap<>();
    private final Map<Integer, Integer> effectivePars = new HashMap<>();

    private int nextHoleIndex = 0;
    private boolean done = false;
    private boolean failed = false;
    private String failureMessage = null;
    private AutoCourseService.AutoCourseScenarioResult result = null;

    /**
     * @param skipHub if false, builds a central hub after all holes are placed (for player autocourse).
     *                if true, skips hub building (for resort courses).
     */
    public TickIncrementalCoursePlacer(
            CoursePlacementService placementService,
            ServerWorld world,
            BlockPos hubOrigin,
            Course course,
            boolean skipHub,
            Consumer<String> progressMessage
    ) {
        this(placementService, world, hubOrigin, course, skipHub, progressMessage, false);
    }

    public TickIncrementalCoursePlacer(
            CoursePlacementService placementService,
            ServerWorld world,
            BlockPos hubOrigin,
            Course course,
            boolean skipHub,
            Consumer<String> progressMessage,
            boolean skipWaterEstimation
    ) {
        if (course == null || course.holes().isEmpty()) {
            throw new IllegalArgumentException("TickIncrementalCoursePlacer requires a course with at least one hole");
        }
        this.placementService = placementService;
        this.layoutValidator = new HoleLayoutValidator();
        this.world = world;
        this.hubOrigin = hubOrigin;
        this.course = course;
        this.progressMessage = progressMessage;
        this.skipHub = skipHub;
        this.skipWaterEstimation = skipWaterEstimation;
    }

    /**
     * Places the next hole. Call once per server tick.
     */
    public void tick() {
        if (done || failed) {
            return;
        }

        if (nextHoleIndex >= course.holes().size()) {
            finish();
            return;
        }

        Hole hole = course.holes().get(nextHoleIndex);
        try {
            placeSingleHole(hole);
            nextHoleIndex++;
        } catch (Exception ex) {
            rollbackPartial();
            failed = true;
            failureMessage = ex.getMessage();
            McdgMod.LOGGER.error("TickIncrementalCoursePlacer failed on hole {}", hole.index(), ex);
        }
    }

    private void placeSingleHole(Hole hole) {
        int hubX = hubOrigin.getX();
        int hubZ = hubOrigin.getZ();

        int absTeeX = hubX + hole.tee().x();
        int absTeeZ = hubZ + hole.tee().z();
        int absBasketX = hubX + hole.basket().x();
        int absBasketZ = hubZ + hole.basket().z();

        int localBasketX = absBasketX - absTeeX;
        int localBasketZ = absBasketZ - absTeeZ;

        // Water-crossing shrink logic (same as original placeCourseIncrementally)
        // Skip for resort courses to avoid chunk generation in ungenerated terrain
        if (!skipWaterEstimation) {
            int waterDx = absBasketX - absTeeX;
            int waterDz = absBasketZ - absTeeZ;
            int waterSteps = Math.max(Math.abs(waterDx), Math.abs(waterDz));
            if (waterSteps > 0) {
                int maxWaterRun = 0;
                int currentWaterRun = 0;
                for (int s = 0; s <= waterSteps; s++) {
                    double t = s / (double) waterSteps;
                    int sx = (int) Math.round(absTeeX + waterDx * t);
                    int sz = (int) Math.round(absTeeZ + waterDz * t);
                    if (CoursePlacementService.isWaterCrossingColumn(world, sx, sz)) {
                        currentWaterRun++;
                        maxWaterRun = Math.max(maxWaterRun, currentWaterRun);
                    } else {
                        currentWaterRun = 0;
                    }
                }
                if (maxWaterRun > CoursePlacementConfig.WaterLanding.MAX_CARRY_BLOCKS) {
                    double shrink = (double) CoursePlacementConfig.WaterLanding.MAX_CARRY_BLOCKS / maxWaterRun;
                    localBasketX = (int) Math.round(localBasketX * shrink);
                    localBasketZ = (int) Math.round(localBasketZ * shrink);
                    absBasketX = absTeeX + localBasketX;
                    absBasketZ = absTeeZ + localBasketZ;
                }
            }
        }

        BlockPos center = new BlockPos(absTeeX, 64, absTeeZ);
        Hole candidate = new Hole(
                hole.index(), hole.par(), hole.distanceFeet(),
                new TeePoint(0, 64, 0),
                new BasketPoint(localBasketX, 64, localBasketZ, hole.basket().basketHeight()),
                List.of(new FairwaySegment(0, 0, localBasketX, localBasketZ,
                        hole.fairwaySegments().isEmpty() ? 4 : hole.fairwaySegments().get(0).width())),
                hole.signatureType()
        );
        Course tempCourse = new Course(course.seed(), course.name() + "-hole-" + hole.index(), List.of(candidate));
        PlacedCourseState placed = placementService.placeCourseAtFixedOrigin(
                world, center, tempCourse, ignored -> {}, globalProtectedPositions, true, skipWaterEstimation);

        BlockPos actualTee = placed.holeTees().get(hole.index());
        BlockPos actualBasket = placed.holeBaskets().get(hole.index());
        if (actualTee == null || actualBasket == null) {
            placementService.resetPlacedCourse(world, placed);
            rollbackPartial();
            throw new RuntimeException("Incremental placement hole " + hole.index() + " produced no tee/basket");
        }

        int actualFeet = layoutValidator.distanceFeetFromBlocks(
                actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ());
        int effectivePar = placed.effectiveHolePars().getOrDefault(hole.index(), AutoCourseService.computePar(actualFeet));
        Hole actualHole = new Hole(
                hole.index(), effectivePar, actualFeet,
                new TeePoint(actualTee.getX(), actualTee.getY(), actualTee.getZ()),
                new BasketPoint(actualBasket.getX(), actualBasket.getY(), actualBasket.getZ(), hole.basket().basketHeight()),
                List.of(new FairwaySegment(actualTee.getX(), actualTee.getZ(), actualBasket.getX(), actualBasket.getZ(),
                        hole.fairwaySegments().isEmpty() ? 4 : hole.fairwaySegments().get(0).width())),
                hole.signatureType()
        );
        builtHoles.add(actualHole);

        for (Map.Entry<BlockPos, BlockState> entry : placed.originalBlocks().entrySet()) {
            mergedOriginals.putIfAbsent(entry.getKey(), entry.getValue());
        }
        tees.putAll(placed.holeTees());
        baskets.putAll(placed.holeBaskets());
        alternates.putAll(placed.holeAlternateAnchors());
        effectivePars.putAll(placed.effectiveHolePars());

        PlacementUtils.addProtectedColumnArea(globalProtectedPositions, actualTee, 2, 6);
        PlacementUtils.addProtectedColumnArea(globalProtectedPositions, actualBasket.down(), 2, 8);

        if (progressMessage != null) {
            progressMessage.accept("Placed hole " + hole.index() + " of " + course.holes().size());
        }
    }

    private void finish() {
        Course builtCourse = new Course(course.seed(), course.name(), builtHoles);
        PlacedCourseState mergedState = new PlacedCourseState(
                world.getRegistryKey(), mergedOriginals, tees, baskets, alternates, effectivePars);

        // Build central hub if requested (for player autocourse, not resort courses)
        if (!skipHub && !builtHoles.isEmpty()) {
            Hole firstHole = builtHoles.get(0);
            BlockPos firstTee = tees.get(firstHole.index());
            BlockPos firstBasket = baskets.get(firstHole.index());
            if (firstTee != null && firstBasket != null) {
                BiomeTheme theme = BiomeThemeResolver.resolve(world.getBiome(firstTee));
                CourseStructureBuilder.placeCourseCentralHub(
                    world, firstTee, firstBasket, course.name(), 
                    mergedOriginals, globalProtectedPositions, theme
                );
            }
        }

        // Compute hazard grids for all holes (for hole map rendering)
        HoleHazardGridService.reset();
        TournamentRulesetManager rulesetManager = McdgMod.getRulesetManager();
        String courseKey = HoleHazardGridService.courseKey(builtCourse.name(), builtCourse.seed());
        for (Hole hole : builtHoles) {
            BlockPos tee = tees.get(hole.index());
            BlockPos basket = baskets.get(hole.index());
            if (tee != null && basket != null) {
                HoleHazardGridService.CachedHazardGrid grid =
                        HoleHazardGridService.computeGrid(world, hole, tee, basket, rulesetManager);
                HoleHazardGridService.cacheGrid(courseKey, hole.index(), grid);
            }
        }

        result = new AutoCourseService.AutoCourseScenarioResult(builtCourse, mergedState);
        done = true;
    }

    private void rollbackPartial() {
        for (Map.Entry<BlockPos, BlockState> entry : mergedOriginals.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), net.minecraft.block.Block.NOTIFY_ALL);
        }
    }

    public boolean isDone() { return done; }
    public boolean isFailed() { return failed; }
    public AutoCourseService.AutoCourseScenarioResult getResult() { return result; }
    public String getFailureMessage() { return failureMessage; }
    public float getProgress() { return (float) nextHoleIndex / course.holes().size(); }
    public Map<BlockPos, BlockState> getMergedOriginals() { return java.util.Collections.unmodifiableMap(mergedOriginals); }
}
