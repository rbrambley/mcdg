package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.PlacedCourseState;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class CoursePlacementService {
    private static final int FAIRWAY_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.FAIRWAY;
    private static final int HOLE_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.HOLE;
    private static final int ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ANCHOR;
    private static final int FAIRWAY_CLEAR_BOTTOM_PADDING = CoursePlacementConfig.Fairway.CLEAR_BOTTOM_PADDING;
    private static final int FAIRWAY_CLEAR_TOP_PADDING = CoursePlacementConfig.Fairway.CLEAR_TOP_PADDING;
    private static final int FAIRWAY_LOG_SWEEP_EXTRA_RADIUS = CoursePlacementConfig.Fairway.LOG_SWEEP_EXTRA_RADIUS;
    private static final int TREE_CLUSTER_CLEAR_LIMIT = CoursePlacementConfig.Fairway.TREE_CLUSTER_CLEAR_LIMIT;
    private static final int WATER_LANDING_PATCH_INTERVAL = CoursePlacementConfig.WaterLanding.PATCH_INTERVAL;
    private static final int WATER_LANDING_PATCH_RADIUS = CoursePlacementConfig.WaterLanding.PATCH_RADIUS;
    private static final int WATER_LANDING_PATCH_MAX_CARRY = CoursePlacementConfig.WaterLanding.PATCH_MAX_CARRY;
    private static final int SAFE_FAIRWAY_HALF_WIDTH = CoursePlacementConfig.WaterLanding.SAFE_FAIRWAY_HALF_WIDTH;
    private static final int SAFE_FAIRWAY_MIN_LENGTH = CoursePlacementConfig.WaterLanding.SAFE_FAIRWAY_MIN_LENGTH;
    private static final int WATER_LANDING_ENFORCE_SCAN_RADIUS = CoursePlacementConfig.WaterLanding.ENFORCE_SCAN_RADIUS;
    private static final int WATER_LANDING_ENFORCE_MAX_GAP = CoursePlacementConfig.WaterLanding.ENFORCE_MAX_GAP;
    private static final int WATER_ADJACENT_BASKET_GREEN_RADIUS = CoursePlacementConfig.WaterLanding.ADJACENT_BASKET_GREEN_RADIUS;
    private static final int WATER_ADJACENT_SCAN_RADIUS = CoursePlacementConfig.WaterLanding.ADJACENT_SCAN_RADIUS;
    private static final int WATER_ADJACENT_MIN_COLUMNS = CoursePlacementConfig.WaterLanding.ADJACENT_MIN_COLUMNS;
    private static final int FINISH_GREEN_MIN_SAFE_COLUMNS = CoursePlacementConfig.FinishGreen.MIN_SAFE_COLUMNS;
    private static final int FINISH_GREEN_MAX_RADIUS = CoursePlacementConfig.FinishGreen.MAX_RADIUS;
    private static final int FINISH_APPROACH_SCAN_DISTANCE = CoursePlacementConfig.FinishGreen.APPROACH_SCAN_DISTANCE;
    private static final int FINISH_APPROACH_SAMPLE_INTERVAL = CoursePlacementConfig.FinishGreen.APPROACH_SAMPLE_INTERVAL;
    private static final int FINISH_APPROACH_BASE_RADIUS = CoursePlacementConfig.FinishGreen.APPROACH_BASE_RADIUS;
    private static final int FINISH_APPROACH_MAX_EXTRA_RADIUS = CoursePlacementConfig.FinishGreen.APPROACH_MAX_EXTRA_RADIUS;
    private static final int FINISH_APPROACH_WIDEN_DISTANCE = CoursePlacementConfig.FinishGreen.APPROACH_WIDEN_DISTANCE;
    private static final int FINISH_HAZARD_SCAN_HALF_WIDTH = CoursePlacementConfig.FinishGreen.HAZARD_SCAN_HALF_WIDTH;
    private static final int TEE_ISLAND_RADIUS = CoursePlacementConfig.Islands.TEE_RADIUS;
    private static final int BASKET_ISLAND_RADIUS = CoursePlacementConfig.Islands.BASKET_RADIUS;
    private static final int SIGNATURE_RING_RADIUS = CoursePlacementConfig.Islands.SIGNATURE_RING_RADIUS;
    private static final int TEE_LAUNCH_CLEAR_DISTANCE = CoursePlacementConfig.Tee.LAUNCH_CLEAR_DISTANCE;
    private static final int TEE_LAUNCH_CLEAR_HALF_WIDTH = CoursePlacementConfig.Tee.LAUNCH_CLEAR_HALF_WIDTH;
    private static final int TEE_RELOCATION_RADIUS = CoursePlacementConfig.Tee.RELOCATION_RADIUS;
    private static final int BASKET_RELOCATION_RADIUS = CoursePlacementConfig.Basket.RELOCATION_RADIUS;
    private static final int BASKET_ENCLOSURE_SCAN_RADIUS = CoursePlacementConfig.Basket.ENCLOSURE_SCAN_RADIUS;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_FAIL = CoursePlacementConfig.Basket.ENCLOSURE_CENTER_DEPTH_FAIL;
    private static final int BASKET_ENCLOSURE_CENTER_DEPTH_CHECK = CoursePlacementConfig.Basket.ENCLOSURE_CENTER_DEPTH_CHECK;
    private static final int BASKET_ENCLOSURE_RECOVERY_MIN_DEPTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MIN_DEPTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MAX_DEPTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_WIDTH = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_WIDTH;
    private static final int BASKET_ENCLOSURE_RECOVERY_HEADROOM = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_HEADROOM;
    private static final int BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_LATERAL_STEP;
    private static final int BASKET_ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS = CoursePlacementConfig.Basket.ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS;
    private static final int BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD = CoursePlacementConfig.Basket.ENCLOSURE_WALL_DEPTH_THRESHOLD;
    private static final double BASKET_ENCLOSURE_HIGH_WALL_RATIO = CoursePlacementConfig.Basket.ENCLOSURE_HIGH_WALL_RATIO;
    private static final int TEE_WALL_SCAN_RADIUS = CoursePlacementConfig.Tee.WALL_SCAN_RADIUS;
    private static final int TEE_MAX_ENCLOSURE_SCORE = CoursePlacementConfig.Tee.MAX_ENCLOSURE_SCORE;
    private static final int TEE_PREFILTER_ENCLOSURE_DEPTH_FAIL = CoursePlacementConfig.Tee.PREFILTER_ENCLOSURE_DEPTH_FAIL;
    private static final int TEE_PIT_DEPTH_THRESHOLD = CoursePlacementConfig.Tee.PIT_DEPTH_THRESHOLD;
    private static final int PLAYER_RELATIVE_TEE_MIN_Y_OFFSET = CoursePlacementConfig.Surface.PLAYER_RELATIVE_TEE_MIN_Y_OFFSET;
    private static final int PLAYER_RELATIVE_BASKET_TARGET_MIN_Y_OFFSET = CoursePlacementConfig.Surface.PLAYER_RELATIVE_BASKET_TARGET_MIN_Y_OFFSET;
    private static final int PLAYER_RELATIVE_BASKET_ABSOLUTE_MIN_Y_OFFSET = CoursePlacementConfig.Surface.PLAYER_RELATIVE_BASKET_ABSOLUTE_MIN_Y_OFFSET;
    private static final int PLAYER_RELATIVE_Y_REPOSITION_RADIUS = CoursePlacementConfig.Surface.PLAYER_RELATIVE_Y_REPOSITION_RADIUS;
    private static final int TEE_MAX_DIRECT_CARRY_GAP = CoursePlacementConfig.Tee.MAX_DIRECT_CARRY_GAP;
    private static final int ALT_FAIRWAY_TARGET_ROUTE_GAP = CoursePlacementConfig.AltFairway.TARGET_ROUTE_GAP;
    private static final int ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ALT_FAIRWAY_ANCHOR;
    private static final int ALT_FAIRWAY_FIRST_LEG_MAX_GAP = CoursePlacementConfig.AltFairway.FIRST_LEG_MAX_GAP;
    private static final int ALT_FAIRWAY_FIRST_LEG_MAX_GAP_FALLBACK = CoursePlacementConfig.AltFairway.FIRST_LEG_MAX_GAP_FALLBACK;
    private static final int ALT_FAIRWAY_MIN_ADVANCE = CoursePlacementConfig.AltFairway.MIN_ADVANCE;
    private static final int ALT_FAIRWAY_MAX_FIRST_LEG = CoursePlacementConfig.AltFairway.MAX_FIRST_LEG;
    private static final int ALT_FAIRWAY_EMERGENCY_ANCHOR_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.ALT_FAIRWAY_EMERGENCY_ANCHOR;
    private static final int ALT_FAIRWAY_EMERGENCY_MAX_FIRST_LEG = CoursePlacementConfig.AltFairway.EMERGENCY_MAX_FIRST_LEG;
    private static final int COURSE_ANCHOR_MAX_RETRIES = CoursePlacementConfig.CourseAnchor.MAX_RETRIES;
    private static final double COURSE_ANCHOR_HARD_REJECT_WATER_RATIO = CoursePlacementConfig.CourseAnchor.HARD_REJECT_WATER_RATIO;
    private static final double COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO = CoursePlacementConfig.CourseAnchor.MAX_WATER_SAMPLE_RATIO;
    private static final int COURSE_ANCHOR_WATER_RATIO_SCORE_WEIGHT = CoursePlacementConfig.CourseAnchor.WATER_RATIO_SCORE_WEIGHT;
    private static final int COURSE_ANCHOR_WATER_REJECT_PENALTY = CoursePlacementConfig.CourseAnchor.WATER_REJECT_PENALTY;
    private static final int ROUTE_POLICY_MAX_RETRIES = CoursePlacementConfig.RoutePolicy.MAX_RETRIES;
    // About 300 ft max carry (1 block ~= 3.28 ft); keep all hole types on the same carry policy.
    private static final int MAX_WATER_CARRY_BLOCKS = CoursePlacementConfig.WaterLanding.MAX_CARRY_BLOCKS;
    private static final int PAR5_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR5_MAX_WATER_CARRY;
    private static final int PAR34_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR34_MAX_WATER_CARRY;
    private static final int BASKET_APPROACH_ENFORCE_DISTANCE = CoursePlacementConfig.Basket.APPROACH_ENFORCE_DISTANCE;
    private static final int BASKET_APPROACH_MIN_WIDTH = CoursePlacementConfig.Basket.APPROACH_MIN_WIDTH;
    private static final String ALT_ROUTE_DIAG_ENV = CoursePlacementConfig.EnvVars.ALT_ROUTE_DIAG;
    private static final int CAMP_SITE_SCAN_STEP = CoursePlacementConfig.CampSite.SCAN_STEP;
    private static final int CAMP_SITE_MARKER_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.CAMP_SITE_MARKER;
    private static final BlockState CAMP_SITE_MARKER_BLOCK = CoursePlacementConfig.CampSite.MARKER_BLOCK;
    private boolean enforceHeightmapSurfaceRule;
    private boolean useFixedAnchor;

    public PlacedCourseState placeCourseWithHeightmapSurfaceRule(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback) {
        boolean previous = enforceHeightmapSurfaceRule;
        enforceHeightmapSurfaceRule = true;
        try {
            return placeCourse(world, origin, course, progressCallback);
        } finally {
            enforceHeightmapSurfaceRule = previous;
        }
    }

    /**
     * Places a course using {@code origin} as the exact anchor — no anchor search radius is applied.
     * Use this for buildcourse hole placement so the result matches the preview position.
     */
    public PlacedCourseState placeCourseAtFixedOrigin(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback) {
        return placeCourseAtFixedOrigin(world, origin, course, progressCallback, null, false, false);
    }

    public PlacedCourseState placeCourseAtFixedOrigin(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions) {
        return placeCourseAtFixedOrigin(world, origin, course, progressCallback, externalProtectedPositions, false, false);
    }

    public PlacedCourseState placeCourseAtFixedOrigin(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions, boolean skipHub) {
        return placeCourseAtFixedOrigin(world, origin, course, progressCallback, externalProtectedPositions, skipHub, false);
    }

    public PlacedCourseState placeCourseAtFixedOrigin(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions, boolean skipHub, boolean skipWaterEstimation) {
        boolean previous = useFixedAnchor;
        useFixedAnchor = true;
        try {
            return placeCourse(world, origin, course, progressCallback, externalProtectedPositions, skipHub, skipWaterEstimation);
        } finally {
            useFixedAnchor = previous;
        }
    }

    public PlacedCourseState placeCourse(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback) {
        return placeCourse(world, origin, course, progressCallback, null, false, false);
    }

    private PlacedCourseState placeCourse(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions, boolean skipHub, boolean skipWaterEstimation) {
        // Current MVP behavior: place relative to the player's surface location.
        CourseAnchorFinder.CourseBounds courseBounds = CourseAnchorFinder.findCourseBounds(course);
        Set<Long> rejectedAnchorKeys = new HashSet<>();
        BlockPos anchor = null;
        double projectedWaterRatio = 0.0;
        String anchorBiome = "unknown";
        if (useFixedAnchor) {
            // buildcourse: use origin directly so placement matches preview position exactly.
            anchor = SurfaceResolver.resolveSurfacePos(world, origin.getX(), origin.getZ());
            anchorBiome = PlacementUtils.biomeId(world.getBiome(anchor));
            
            // Reject ocean biomes even for fixed anchors
            if (anchorBiome.toLowerCase().contains("ocean")) {
                McdgMod.LOGGER.warn(
                        "Course anchor rejected: ocean biome unsuitable for placement biome={}",
                        anchorBiome
                );
                throw new IllegalStateException("Cannot place course in ocean biome: " + anchorBiome);
            }
            
            if (!skipWaterEstimation) {
                projectedWaterRatio = CourseAnchorFinder.estimateProjectedWaterRatio(world, course, anchor, courseBounds);
            }
            McdgMod.LOGGER.info(
                    "Course anchor fixed (buildcourse) anchor=({}, {}, {}) biome={} projectedWaterRatio={}",
                    anchor.getX(), anchor.getY(), anchor.getZ(), anchorBiome,
                    String.format(java.util.Locale.ROOT, "%.3f", projectedWaterRatio)
            );
        } else {
            for (int attempt = 1; attempt <= COURSE_ANCHOR_MAX_RETRIES; attempt++) {
                anchor = CourseAnchorFinder.findPreferredCourseAnchor(world, origin, course, courseBounds, rejectedAnchorKeys);
                projectedWaterRatio = CourseAnchorFinder.estimateProjectedWaterRatio(world, course, anchor, courseBounds);
                anchorBiome = PlacementUtils.biomeId(world.getBiome(anchor));
                McdgMod.LOGGER.info(
                        "Course anchor candidate attempt={}/{} anchor=({}, {}, {}) biome={} projectedWaterRatio={}",
                        attempt,
                        COURSE_ANCHOR_MAX_RETRIES,
                        anchor.getX(),
                        anchor.getY(),
                        anchor.getZ(),
                        anchorBiome,
                        String.format(java.util.Locale.ROOT, "%.3f", projectedWaterRatio)
                );

                // Apply stricter water ratio limits for beach biomes
                double effectiveHardRejectRatio = COURSE_ANCHOR_HARD_REJECT_WATER_RATIO;
                String biomeId = anchorBiome.toLowerCase();
                if (biomeId.contains("beach") || biomeId.contains("stony_shore")) {
                    effectiveHardRejectRatio = 0.10; // Stricter limit for beaches (10% vs 22%)
                }
                
                // Reject ocean biomes entirely
                if (biomeId.contains("ocean")) {
                    rejectedAnchorKeys.add(CourseAnchorFinder.anchorClusterKey(anchor));
                    continue; // Skip this anchor and try next position
                }

                if (projectedWaterRatio <= effectiveHardRejectRatio) {
                    break;
                }

                rejectedAnchorKeys.add(CourseAnchorFinder.anchorClusterKey(anchor));
                if (attempt == COURSE_ANCHOR_MAX_RETRIES) {
                    McdgMod.LOGGER.warn(
                            "Course anchor retries exhausted; using water-heavy anchor biome={} projectedWaterRatio={}",
                            anchorBiome,
                            String.format(java.util.Locale.ROOT, "%.3f", projectedWaterRatio)
                    );
                }
            }
        }

        McdgMod.LOGGER.info(
                "Course anchor selected anchor=({}, {}, {}) biome={} projectedWaterRatio={}",
                anchor.getX(),
                anchor.getY(),
                anchor.getZ(),
                anchorBiome,
                String.format(java.util.Locale.ROOT, "%.3f", projectedWaterRatio)
        );
        BiomeTheme theme = BiomeThemeResolver.resolve(world.getBiome(anchor));
        McdgMod.LOGGER.info("Course theme resolved: {}", theme.name());
        int teeMinY = origin.getY() - PLAYER_RELATIVE_TEE_MIN_Y_OFFSET;
        int basketTargetMinY = origin.getY() - PLAYER_RELATIVE_BASKET_TARGET_MIN_Y_OFFSET;
        int basketAbsoluteMinY = origin.getY() - PLAYER_RELATIVE_BASKET_ABSOLUTE_MIN_Y_OFFSET;

        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        Map<Integer, BlockPos> holeTees = new HashMap<>();
        Map<Integer, BlockPos> holeBaskets = new HashMap<>();
        Map<Integer, BlockPos> holeAlternateAnchors = new HashMap<>();
        Map<Integer, Integer> holeEffectivePars = new HashMap<>();
        Map<Integer, String> holeRoutingNotes = new HashMap<>();
        BlockPos hole1Tee = null;
        BlockPos hole1Basket = null;
        Set<BlockPos> protectedPositions = externalProtectedPositions != null ? externalProtectedPositions : new HashSet<>();
        int startingHoleIndex = course.holes().isEmpty() ? 1 : course.holes().get(0).index();

        int offsetX = anchor.getX() - courseBounds.centerX();
        int offsetZ = anchor.getZ() - courseBounds.centerZ();

        // Phase 1: resolve all tee/basket surfaces and prepare no-overwrite protection zones.
        for (Hole hole : course.holes()) {
            int teeX = hole.tee().x() + offsetX;
            int teeZ = hole.tee().z() + offsetZ;
            int basketX = hole.basket().x() + offsetX;
            int basketZ = hole.basket().z() + offsetZ;
            int fairwayWidth = FairwayCarver.resolveHoleFairwayWidth(hole);

                // Use exact tee target position; if over water, ensureLandIslandSurface
                // will build an island at that exact location rather than pulling toward shore.
                BlockPos teeTarget = SurfaceResolver.resolveSurfacePos(world, teeX, teeZ);
                BlockPos teeSurface = SurfaceAdaptationHelper.ensureLandIslandSurface(
                    world,
                    teeTarget,
                    TEE_ISLAND_RADIUS,
                    originalBlocks,
                    protectedPositions
                );
                // Use exact basket target position; if over water, ensureLandIslandSurface
                // will build an island at that exact location rather than pulling toward shore.
                BlockPos basketTarget = SurfaceResolver.resolveSurfacePos(world, basketX, basketZ);
                BlockPos basketSurface = SurfaceAdaptationHelper.ensureLandIslandSurface(
                    world,
                    basketTarget,
                    BASKET_ISLAND_RADIUS,
                    originalBlocks,
                    protectedPositions
                );

            basketSurface = SurfaceAdaptationHelper.relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
            teeSurface = SurfaceAdaptationHelper.relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
            if (!SurfaceResolver.isPlayableTeeSurface(world, teeSurface)) {
                teeSurface = SurfaceAdaptationHelper.ensureLandIslandSurface(world, teeSurface, TEE_ISLAND_RADIUS, originalBlocks, protectedPositions);
            }
            basketSurface = SurfaceAdaptationHelper.expandBasketGreenIfWaterNearby(
                world,
                teeSurface,
                basketSurface,
                fairwayWidth,
                originalBlocks,
                protectedPositions
            );
            if (SurfaceAdaptationHelper.isDeeplyEnclosedBasketSurface(world, basketSurface)) {
                BlockPos recoveredBasket = SurfaceAdaptationHelper.tryRecoverEnclosedBasketSurface(
                    world,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                if (recoveredBasket != null) {
                    basketSurface = SurfaceAdaptationHelper.expandBasketGreenIfWaterNearby(
                        world,
                        teeSurface,
                        recoveredBasket,
                        fairwayWidth,
                        originalBlocks,
                        protectedPositions
                    );
                } else {
                    BlockPos relocatedBasket = SurfaceAdaptationHelper.relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
                    basketSurface = SurfaceAdaptationHelper.expandBasketGreenIfWaterNearby(
                        world,
                        teeSurface,
                        relocatedBasket,
                        fairwayWidth,
                        originalBlocks,
                        protectedPositions
                    );
                }
            }

            teeSurface = SurfaceResolver.enforceMinimumSurfaceY(
                world,
                teeSurface,
                teeMinY,
                PLAYER_RELATIVE_Y_REPOSITION_RADIUS,
                true
            );
            if (teeSurface.getY() < teeMinY) {
                McdgMod.LOGGER.warn(
                    "Tee elevation floor unmet after search | hole={} teeY={} minY={} playerY={}",
                    hole.index(),
                    teeSurface.getY(),
                    teeMinY,
                    origin.getY()
                );
            }

            BlockPos strictBasketSurface = SurfaceResolver.enforceMinimumSurfaceY(
                world,
                basketSurface,
                basketTargetMinY,
                PLAYER_RELATIVE_Y_REPOSITION_RADIUS,
                false
            );
            if (strictBasketSurface.getY() < basketTargetMinY) {
                basketSurface = SurfaceResolver.enforceMinimumSurfaceY(
                    world,
                    strictBasketSurface,
                    basketAbsoluteMinY,
                    PLAYER_RELATIVE_Y_REPOSITION_RADIUS,
                    false
                );
                if (basketSurface.getY() < basketAbsoluteMinY) {
                    McdgMod.LOGGER.warn(
                        "Basket elevation floor unmet after relaxed search | hole={} basketY={} targetMinY={} absoluteMinY={} playerY={}",
                        hole.index(),
                        basketSurface.getY(),
                        basketTargetMinY,
                        basketAbsoluteMinY,
                        origin.getY()
                    );
                } else {
                    McdgMod.LOGGER.info(
                        "Basket elevation used relaxed floor fallback | hole={} basketY={} targetMinY={} absoluteMinY={} playerY={}",
                        hole.index(),
                        basketSurface.getY(),
                        basketTargetMinY,
                        basketAbsoluteMinY,
                        origin.getY()
                    );
                }
            } else {
                basketSurface = strictBasketSurface;
            }

            if (SurfaceAdaptationHelper.isDeeplyEnclosedBasketSurface(world, basketSurface)) {
                BlockPos recoveredBasket = SurfaceAdaptationHelper.tryRecoverEnclosedBasketSurface(
                    world,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                if (recoveredBasket != null && !SurfaceAdaptationHelper.isDeeplyEnclosedBasketSurface(world, recoveredBasket)) {
                    basketSurface = recoveredBasket;
                } else {
                    BlockPos relocatedBasket = SurfaceAdaptationHelper.relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
                    relocatedBasket = SurfaceResolver.enforceMinimumSurfaceY(
                        world,
                        relocatedBasket,
                        basketAbsoluteMinY,
                        PLAYER_RELATIVE_Y_REPOSITION_RADIUS,
                        false
                    );
                    if (!SurfaceAdaptationHelper.isDeeplyEnclosedBasketSurface(world, relocatedBasket)) {
                        basketSurface = relocatedBasket;
                    }
                }
            }

            RoutePolicyHelper.HoleRoutePolicyResult routeResult = RoutePolicyHelper.enforceHoleRoutePolicy(
                    world,
                    hole,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions,
                    skipWaterEstimation
            );

            teeSurface = routeResult.teeSurface();
            basketSurface = routeResult.basketSurface();
            holeEffectivePars.put(hole.index(), routeResult.effectivePar());

            BlockPos alternateAnchor = routeResult.alternateAnchor();
            if (alternateAnchor != null) {
                holeAlternateAnchors.put(hole.index(), alternateAnchor.toImmutable());
                holeRoutingNotes.put(hole.index(), RoutePolicyHelper.alternateRouteNote(teeSurface, basketSurface, alternateAnchor));
            }
            if (routeResult.routingNote() != null && !routeResult.routingNote().isBlank()) {
                holeRoutingNotes.put(hole.index(), routeResult.routingNote());
            }

            holeTees.put(hole.index(), teeSurface.toImmutable());
            holeBaskets.put(hole.index(), basketSurface.up().toImmutable());

            // Protect tee pad area, basket green area, and tee lantern area from later fairway/island writes.
            PlacementUtils.addProtectedColumnArea(protectedPositions, teeSurface, 2, 6);
            PlacementUtils.addProtectedColumnArea(protectedPositions, basketSurface, 2, 8);
            int[] teeForward = PlacementUtils.teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            PlacementUtils.addProtectedColumnArea(protectedPositions, teeLampGround, 1, 6);
        }

        // Phase 2: build one contiguous fairway per hole (faster and more reliable than many segment passes).
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()).down();
            BlockPos alternateAnchor = holeAlternateAnchors.get(hole.index());

            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            int fairwayWidth = FairwayCarver.resolveHoleFairwayWidth(hole);

                if (alternateAnchor != null) {
                FairwayCarver.carveFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false,
                    theme
                );
                FairwayCarver.carveFairway(
                    world,
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false,
                    theme
                );
                // Create safe landing zone at alternate anchor for long water carries
                FairwayCarver.createSafeFairwayLandingZone(
                    world,
                    alternateAnchor,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                } else {
                FairwayCarver.carveFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false,
                    theme
                );
                }

                BlockPos finalApproachStart = alternateAnchor != null ? alternateAnchor : teeSurface;
                FairwayCarver.enforceBasketApproachLandingZone(
                    world,
                    finalApproachStart,
                    basketSurface,
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    theme
                );

                    progressCallback.accept(Math.max(1, hole.index() / 2));
        }

        // Phase 2.5: place biome-specific hazards along fairways
        BiomeHazardProfile hazardProfile = BiomeHazardResolver.resolve(theme);
        McdgMod.LOGGER.info("Hazard profile resolved: {} (density: {})", hazardProfile.name(), hazardProfile.hazardDensity());
        
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index());
            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            int fairwayWidth = FairwayCarver.resolveHoleFairwayWidth(hole);
            BlockPos alternateAnchor = holeAlternateAnchors.get(hole.index());

            // Use hole-specific seed for deterministic hazard placement
            long holeSeed = course.seed() + (hole.index() * 7919); // Prime number for good distribution

            if (alternateAnchor != null) {
                // Place hazards on both fairway segments for alternate anchor routes
                HazardPlacementService.placeHazardsAlongFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    hazardProfile,
                    holeSeed,
                    teeSurface,
                    basketSurface
                );
                HazardPlacementService.placeHazardsAlongFairway(
                    world,
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    hazardProfile,
                    holeSeed + 1,
                    teeSurface,
                    basketSurface
                );
            } else {
                // Place hazards on single fairway segment
                HazardPlacementService.placeHazardsAlongFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    hazardProfile,
                    holeSeed,
                    teeSurface,
                    basketSurface
                );
            }
        }

        // Phase 3: place all tee pads, baskets, and tee lanterns after fairways so they remain visible.
        // Pre-protect all basket columns so hub and other structures can't overwrite them.
        for (Hole hole : course.holes()) {
            BlockPos bsProtect = holeBaskets.get(hole.index());
            if (bsProtect != null) {
                PlacementUtils.addProtectedColumnArea(protectedPositions, bsProtect.down(), 1, 6);
            }
        }
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()) == null ? null : holeBaskets.get(hole.index()).down();
            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            PlacementUtils.clearHeadroom(world, teeSurface, 2, 5, originalBlocks, null);
            PlacementUtils.clearHeadroom(world, basketSurface, 2, 6, originalBlocks, null);
            PlacementCleanupHelper.clearTeeLaunchLane(world, teeSurface, basketSurface, originalBlocks, protectedPositions);

            CourseStructureBuilder.placeTeePad(world, teeSurface, originalBlocks, theme);
            int[] teeForward = PlacementUtils.teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            CourseStructureBuilder.placeLanternPost(world, teeLampGround, 2, originalBlocks, theme);
            CourseStructureBuilder.placeTeeHoleBanner(
                world, teeSurface, basketSurface,
                hole.index(), hole.par(), PlacementUtils.placedDistanceFeet(teeSurface, holeBaskets.get(hole.index())),
                hole.isSignature(),
                hole.signatureType().displayName(),
                holeRoutingNotes.getOrDefault(hole.index(), ""),
                originalBlocks,
                theme
            );
            if (hole.index() == startingHoleIndex && startingHoleIndex == 1) {
                hole1Tee = teeSurface;
                hole1Basket = basketSurface;
            }
            CourseStructureBuilder.placeBasketMarker(world, basketSurface, originalBlocks, hole.basket().basketHeight(), theme);
            if (hole.isSignature()) {
                CourseStructureBuilder.placeSignatureBasketAccents(world, basketSurface, originalBlocks, protectedPositions, theme);
            }

            progressCallback.accept(hole.index());
        }

        if (!skipHub && hole1Tee != null && hole1Basket != null) {
            CourseStructureBuilder.placeCourseCentralHub(world, hole1Tee, hole1Basket, course.name(), originalBlocks, protectedPositions, theme);
            // Hub construction can overlap the starting hole footprint; enforce the tee pad shape afterwards.
            CourseStructureBuilder.placeTeePad(world, hole1Tee, originalBlocks, theme);
        }

        // Compute hazard grids for all holes (for hole map rendering)
        com.mcdg.game.HoleHazardGridService.reset();
        com.mcdg.rules.TournamentRulesetManager rulesetManager = com.mcdg.McdgMod.getRulesetManager();
        String courseKey = com.mcdg.game.HoleHazardGridService.courseKey(course.name(), course.seed());
        for (Hole hole : course.holes()) {
            BlockPos tee = holeTees.get(hole.index());
            BlockPos basket = holeBaskets.get(hole.index());
            if (tee != null && basket != null) {
                com.mcdg.game.HoleHazardGridService.CachedHazardGrid grid =
                        com.mcdg.game.HoleHazardGridService.computeGrid(world, hole, tee, basket, rulesetManager);
                com.mcdg.game.HoleHazardGridService.cacheGrid(courseKey, hole.index(), grid);
            }
        }

        // Phase 4 intentionally disabled for now (fairway lantern pass) to avoid long generation stalls.

        return new PlacedCourseState(world.getRegistryKey(), originalBlocks, holeTees, holeBaskets, holeAlternateAnchors, holeEffectivePars);
    }





    public static void evacuatePlayersFromRestoreArea(ServerWorld world, Map<BlockPos, BlockState> originalBlocks) {
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (!player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                continue;
            }
            BlockPos feet = player.getBlockPos();
            if (originalBlocks.containsKey(feet) || originalBlocks.containsKey(feet.up())) {
                BlockPos spawn = world.getSpawnPos();
                player.teleport(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            }
        }
    }

    public void resetPlacedCourse(ServerWorld world, PlacedCourseState placedCourseState) {
        evacuatePlayersFromRestoreArea(world, placedCourseState.originalBlocks());
        for (Map.Entry<BlockPos, BlockState> entry : placedCourseState.originalBlocks().entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
        }
    }


    private BlockPos resolveHoleSurface(ServerWorld world, int x, int z) {
        if (!enforceHeightmapSurfaceRule) {
            return SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.findPreferredSurfacePos(world, x, z, true, HOLE_SEARCH_RADIUS));
        }

        BlockPos direct = SurfaceResolver.resolveWorldSurfaceGround(world, x, z);
        if (SurfaceResolver.isValidHeightmapRuleGround(world, direct)) {
            return direct;
        }

        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        int step = 2;
        for (int radius = step; radius <= HOLE_SEARCH_RADIUS; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidate = SurfaceResolver.resolveWorldSurfaceGround(world, x + dx, z + dz);
                    if (!SurfaceResolver.isValidHeightmapRuleGround(world, candidate)) {
                        continue;
                    }

                    int score = Math.abs(dx) + Math.abs(dz);
                    score += Math.abs(candidate.getY() - direct.getY()) * 2;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }

            if (best != null) {
                return best;
            }
        }

        return SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.findPreferredSurfacePos(world, x, z, true, HOLE_SEARCH_RADIUS));
    }









    /**
     * Ensures the given position is on safe, walkable land.
     * If the position is underwater or in fluid, builds a grass island up to sea level.
     * Returns the BlockPos of the actual safe surface (may be higher than the input center).
     */


        
    /**
     * Creates an elongated island-style safe fairway landing zone at the given anchor point.
     * This is used at alternate anchors on holes with long water carries to give players a
     * reliable lay-up target that is 7 blocks wide and at least 20 blocks long.
     *
     * The fairway has sand edges with grass interior and extends towards the basket.
     */
































    public static boolean isWaterCrossingColumn(ServerWorld world, int x, int z) {
        int worldSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos worldSurface = new BlockPos(x, worldSurfaceY, z);
        if (!world.getBlockState(worldSurface).getFluidState().isEmpty()) {
            return true;
        }

        int seaY = world.getSeaLevel();
        for (int y = seaY - 2; y <= seaY + 1; y++) {
            BlockPos sample = new BlockPos(x, y, z);
            if (!world.getBlockState(sample).getFluidState().isEmpty()) {
                return true;
            }
        }

        return SurfaceAdaptationHelper.isWaterBiome(world, SurfaceResolver.resolveSurfacePos(world, x, z));
    }

    static boolean isWaterAdjacentArea(ServerWorld world, BlockPos center, int radius, int minWaterColumns) {
        int waterColumns = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }
                if (isWaterCrossingColumn(world, center.getX() + dx, center.getZ() + dz)) {
                    waterColumns++;
                    if (waterColumns >= minWaterColumns) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean hasAnyWalkableLandingNearby(ServerWorld world, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = x + dx;
                int sampleZ = z + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (SurfaceAdaptationHelper.isWalkableGround(world, sample)) {
                    return true;
                }
            }
        }

        return false;
    }

    static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return 0;
        }

        int longestGap = 0;
        int currentGap = 0;

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(start.getX() + (dx * t));
            int z = (int) Math.round(start.getZ() + (dz * t));

            if (!isWaterCrossingColumn(world, x, z)) {
                currentGap = 0;
                continue;
            }

            if (hasAnyWalkableLandingNearby(world, x, z, WATER_LANDING_ENFORCE_SCAN_RADIUS)) {
                currentGap = 0;
                continue;
            }

            currentGap++;
            longestGap = Math.max(longestGap, currentGap);
        }

        return longestGap;
    }



}
