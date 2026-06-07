package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.game.PlacedCourseState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.block.entity.SignText;
import net.minecraft.world.biome.Biome;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
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
    private static final int BASKET_DRY_COLUMN_CHECK_HEIGHT = CoursePlacementConfig.Basket.DRY_COLUMN_CHECK_HEIGHT;
    private static final int TEE_EXIT_Y_TOLERANCE = CoursePlacementConfig.Tee.EXIT_Y_TOLERANCE;
    private static final int TEE_MIN_NEARBY_EXITS = CoursePlacementConfig.Tee.MIN_NEARBY_EXITS;
    private static final int TEE_WALL_SCAN_RADIUS = CoursePlacementConfig.Tee.WALL_SCAN_RADIUS;
    private static final int TEE_MAX_ENCLOSURE_SCORE = CoursePlacementConfig.Tee.MAX_ENCLOSURE_SCORE;
    private static final int TEE_PREFILTER_ENCLOSURE_DEPTH_FAIL = CoursePlacementConfig.Tee.PREFILTER_ENCLOSURE_DEPTH_FAIL;
    private static final int TEE_PIT_DEPTH_THRESHOLD = CoursePlacementConfig.Tee.PIT_DEPTH_THRESHOLD;
    private static final int SURFACE_SEARCH_DEPTH_LIMIT = CoursePlacementConfig.Surface.SEARCH_DEPTH_LIMIT;
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
    private static final int CAMP_SITE_RADIUS = CoursePlacementConfig.CampSite.RADIUS;
    private static final int CAMP_SITE_SCAN_STEP = CoursePlacementConfig.CampSite.SCAN_STEP;
    private static final int CAMP_SITE_MAX_Y_DELTA = CoursePlacementConfig.CampSite.MAX_Y_DELTA;
    private static final int CAMP_SITE_MIN_SAFE_PERCENT = CoursePlacementConfig.CampSite.MIN_SAFE_PERCENT;
    private static final int CAMP_SITE_MARKER_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.CAMP_SITE_MARKER;
    private static final BlockState CAMP_SITE_MARKER_BLOCK = CoursePlacementConfig.CampSite.MARKER_BLOCK;
    private boolean enforceHeightmapSurfaceRule;
    private boolean useFixedAnchor;

    public record LodgingBuildResult(boolean success, String message, BlockPos center) {
    }

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
        return placeCourseAtFixedOrigin(world, origin, course, progressCallback, null);
    }

    public PlacedCourseState placeCourseAtFixedOrigin(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions) {
        boolean previous = useFixedAnchor;
        useFixedAnchor = true;
        try {
            return placeCourse(world, origin, course, progressCallback, externalProtectedPositions);
        } finally {
            useFixedAnchor = previous;
        }
    }

    public PlacedCourseState placeCourse(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback) {
        return placeCourse(world, origin, course, progressCallback, null);
    }

    private PlacedCourseState placeCourse(ServerWorld world, BlockPos origin, Course course, IntConsumer progressCallback, Set<BlockPos> externalProtectedPositions) {
        // Current MVP behavior: place relative to the player's surface location.
        CourseBounds courseBounds = findCourseBounds(course);
        Set<Long> rejectedAnchorKeys = new HashSet<>();
        BlockPos anchor = null;
        double projectedWaterRatio = 0.0;
        String anchorBiome = "unknown";
        if (useFixedAnchor) {
            // buildcourse: use origin directly so placement matches preview position exactly.
            anchor = SurfaceResolver.resolveSurfacePos(world, origin.getX(), origin.getZ());
            projectedWaterRatio = estimateProjectedWaterRatio(world, course, anchor, courseBounds);
            anchorBiome = PlacementUtils.biomeId(world.getBiome(anchor));
            McdgMod.LOGGER.info(
                    "Course anchor fixed (buildcourse) anchor=({}, {}, {}) biome={} projectedWaterRatio={}",
                    anchor.getX(), anchor.getY(), anchor.getZ(), anchorBiome,
                    String.format(java.util.Locale.ROOT, "%.3f", projectedWaterRatio)
            );
        } else {
            for (int attempt = 1; attempt <= COURSE_ANCHOR_MAX_RETRIES; attempt++) {
                anchor = findPreferredCourseAnchor(world, origin, course, courseBounds, rejectedAnchorKeys);
                projectedWaterRatio = estimateProjectedWaterRatio(world, course, anchor, courseBounds);
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

                if (projectedWaterRatio <= COURSE_ANCHOR_HARD_REJECT_WATER_RATIO) {
                    break;
                }

                rejectedAnchorKeys.add(anchorClusterKey(anchor));
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
        int teeMinY = origin.getY() - PLAYER_RELATIVE_TEE_MIN_Y_OFFSET;
        int basketTargetMinY = origin.getY() - PLAYER_RELATIVE_BASKET_TARGET_MIN_Y_OFFSET;
        int basketAbsoluteMinY = origin.getY() - PLAYER_RELATIVE_BASKET_ABSOLUTE_MIN_Y_OFFSET;

        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        Map<Integer, BlockPos> holeTees = new HashMap<>();
        Map<Integer, BlockPos> holeBaskets = new HashMap<>();
        Map<Integer, BlockPos> holeAlternateAnchors = new HashMap<>();
        Map<Integer, Integer> holeEffectivePars = new HashMap<>();
        Map<Integer, String> holeRoutingNotes = new HashMap<>();
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
            int fairwayWidth = resolveHoleFairwayWidth(hole);

                // Build islands if unsafe and use normalized top-surface positions for reliable tee/basket visibility.
                BlockPos teeSurface = ensureLandIslandSurface(
                    world,
                    resolveHoleSurface(world, teeX, teeZ),
                    TEE_ISLAND_RADIUS,
                    originalBlocks,
                    protectedPositions
                );
                BlockPos basketSurface = ensureLandIslandSurface(
                    world,
                    resolveHoleSurface(world, basketX, basketZ),
                    BASKET_ISLAND_RADIUS,
                    originalBlocks,
                    protectedPositions
                );

            basketSurface = relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
            teeSurface = relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
            if (!SurfaceResolver.isPlayableTeeSurface(world, teeSurface)) {
                teeSurface = ensureLandIslandSurface(world, teeSurface, TEE_ISLAND_RADIUS, originalBlocks, protectedPositions);
            }
            basketSurface = expandBasketGreenIfWaterNearby(
                world,
                teeSurface,
                basketSurface,
                fairwayWidth,
                originalBlocks,
                protectedPositions
            );
            if (isDeeplyEnclosedBasketSurface(world, basketSurface)) {
                BlockPos recoveredBasket = tryRecoverEnclosedBasketSurface(
                    world,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                if (recoveredBasket != null) {
                    basketSurface = expandBasketGreenIfWaterNearby(
                        world,
                        teeSurface,
                        recoveredBasket,
                        fairwayWidth,
                        originalBlocks,
                        protectedPositions
                    );
                } else {
                    BlockPos relocatedBasket = relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
                    basketSurface = expandBasketGreenIfWaterNearby(
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

            if (isDeeplyEnclosedBasketSurface(world, basketSurface)) {
                BlockPos recoveredBasket = tryRecoverEnclosedBasketSurface(
                    world,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                if (recoveredBasket != null && !isDeeplyEnclosedBasketSurface(world, recoveredBasket)) {
                    basketSurface = recoveredBasket;
                } else {
                    BlockPos relocatedBasket = relocateBasketSurfaceIfNeeded(world, teeSurface, basketSurface);
                    relocatedBasket = SurfaceResolver.enforceMinimumSurfaceY(
                        world,
                        relocatedBasket,
                        basketAbsoluteMinY,
                        PLAYER_RELATIVE_Y_REPOSITION_RADIUS,
                        false
                    );
                    if (!isDeeplyEnclosedBasketSurface(world, relocatedBasket)) {
                        basketSurface = relocatedBasket;
                    }
                }
            }

            HoleRoutePolicyResult routeResult = enforceHoleRoutePolicy(
                    world,
                    hole,
                    teeSurface,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
            );

            teeSurface = routeResult.teeSurface();
            basketSurface = routeResult.basketSurface();
            holeEffectivePars.put(hole.index(), routeResult.effectivePar());

            BlockPos alternateAnchor = routeResult.alternateAnchor();
            if (alternateAnchor != null) {
                holeAlternateAnchors.put(hole.index(), alternateAnchor.toImmutable());
                holeRoutingNotes.put(hole.index(), alternateRouteNote(teeSurface, basketSurface, alternateAnchor));
            }
            if (routeResult.routingNote() != null && !routeResult.routingNote().isBlank()) {
                holeRoutingNotes.put(hole.index(), routeResult.routingNote());
            }

            holeTees.put(hole.index(), teeSurface.toImmutable());
            holeBaskets.put(hole.index(), basketSurface.up().toImmutable());

            // Protect tee pad area, basket green area, and tee lantern area from later fairway/island writes.
            addProtectedColumnArea(protectedPositions, teeSurface, 2, 6);
            addProtectedColumnArea(protectedPositions, basketSurface, 2, 8);
            int[] teeForward = teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            addProtectedColumnArea(protectedPositions, teeLampGround, 1, 6);
        }

        // Phase 2: build one contiguous fairway per hole (faster and more reliable than many segment passes).
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()).down();
            BlockPos alternateAnchor = holeAlternateAnchors.get(hole.index());

            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            int fairwayWidth = resolveHoleFairwayWidth(hole);

                if (alternateAnchor != null) {
                carveFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false
                );
                carveFairway(
                    world,
                    alternateAnchor.getX(),
                    alternateAnchor.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false
                );
                // Create safe landing zone at alternate anchor for long water carries
                createSafeFairwayLandingZone(
                    world,
                    alternateAnchor,
                    basketSurface,
                    originalBlocks,
                    protectedPositions
                );
                } else {
                carveFairway(
                    world,
                    teeSurface.getX(),
                    teeSurface.getZ(),
                    basketSurface.getX(),
                    basketSurface.getZ(),
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions,
                    false
                );
                }

                BlockPos finalApproachStart = alternateAnchor != null ? alternateAnchor : teeSurface;
                enforceBasketApproachLandingZone(
                    world,
                    finalApproachStart,
                    basketSurface,
                    fairwayWidth,
                    originalBlocks,
                    protectedPositions
                );

                    progressCallback.accept(Math.max(1, hole.index() / 2));
        }

        // Phase 3: place all tee pads, baskets, and tee lanterns after fairways so they remain visible.
        // Pre-protect all basket columns so hub and other structures can't overwrite them.
        for (Hole hole : course.holes()) {
            BlockPos bsProtect = holeBaskets.get(hole.index());
            if (bsProtect != null) {
                addProtectedColumnArea(protectedPositions, bsProtect.down(), 1, 6);
            }
        }
        for (Hole hole : course.holes()) {
            BlockPos teeSurface = holeTees.get(hole.index());
            BlockPos basketSurface = holeBaskets.get(hole.index()) == null ? null : holeBaskets.get(hole.index()).down();
            if (teeSurface == null || basketSurface == null) {
                continue;
            }

            clearHeadroom(world, teeSurface, 2, 5, originalBlocks, null);
            clearHeadroom(world, basketSurface, 2, 6, originalBlocks, null);
            clearTeeLaunchLane(world, teeSurface, basketSurface, originalBlocks, protectedPositions);

            placeTeePad(world, teeSurface, originalBlocks);
            int[] teeForward = teeForwardUnit(teeSurface, basketSurface);
            BlockPos teeLampGround = teeSurface.add(-teeForward[0], 0, -teeForward[1]);
            placeLanternPost(world, teeLampGround, 2, originalBlocks);
            placeTeeHoleBanner(
                world, teeSurface, basketSurface,
                hole.index(), hole.par(), placedDistanceFeet(teeSurface, holeBaskets.get(hole.index())),
                hole.isSignature(),
                hole.signatureType().displayName(),
                holeRoutingNotes.getOrDefault(hole.index(), ""),
                originalBlocks
            );
            if (hole.index() == startingHoleIndex && startingHoleIndex == 1) {
                placeCourseCentralHub(world, teeSurface, basketSurface, course.name(), originalBlocks, protectedPositions);
                // Hub construction can overlap the starting hole footprint; enforce the tee pad shape afterwards.
                placeTeePad(world, teeSurface, originalBlocks);
            }
            placeBasketMarker(world, basketSurface, originalBlocks, hole.basket().basketHeight());
            if (hole.isSignature()) {
                placeSignatureBasketAccents(world, basketSurface, originalBlocks, protectedPositions);
            }

            progressCallback.accept(hole.index());
        }

        // Phase 4 intentionally disabled for now (fairway lantern pass) to avoid long generation stalls.

        return new PlacedCourseState(world.getRegistryKey(), originalBlocks, holeTees, holeBaskets, holeAlternateAnchors, holeEffectivePars);
    }

    private HoleRoutePolicyResult enforceHoleRoutePolicy(
            ServerWorld world,
            Hole hole,
            BlockPos initialTee,
            BlockPos initialBasket,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos teeSurface = initialTee;
        BlockPos basketSurface = initialBasket;

        if (hole.par() >= 5) {
            for (int attempt = 1; attempt <= ROUTE_POLICY_MAX_RETRIES; attempt++) {
                BlockPos alternateAnchor = findAlternateFairwayAnchor(world, teeSurface, basketSurface, true);
                int routeGap = routeLongestCarryGap(world, teeSurface, basketSurface, alternateAnchor);
                if (routeGap <= PAR5_ROUTE_MAX_WATER_CARRY) {
                    basketSurface = ensureBasketGreenLandingZone(
                            world,
                            teeSurface,
                            teeSurface,
                            basketSurface,
                            resolveHoleFairwayWidth(hole),
                            originalBlocks,
                            protectedPositions
                    );
                    return new HoleRoutePolicyResult(teeSurface, basketSurface, alternateAnchor, 5, "");
                }

                basketSurface = chooseLowerCarryBasketSurface(world, teeSurface, basketSurface, PAR5_ROUTE_MAX_WATER_CARRY);
                basketSurface = ensureBasketGreenLandingZone(
                        world,
                        teeSurface,
                    teeSurface,
                        basketSurface,
                        resolveHoleFairwayWidth(hole),
                        originalBlocks,
                        protectedPositions
                );
                teeSurface = relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
            }

            McdgMod.LOGGER.info(
                    "Par 5 route policy fallback applied | hole={} seedPar={} effectivePar=4 reason=water_carry_over_limit",
                    hole.index(),
                    hole.par()
            );
        }

        for (int attempt = 1; attempt <= ROUTE_POLICY_MAX_RETRIES; attempt++) {
            BlockPos alternateAnchor = findAlternateFairwayAnchor(world, teeSurface, basketSurface, false);
            int routeGap = routeLongestCarryGap(world, teeSurface, basketSurface, alternateAnchor);
            if (routeGap <= PAR34_ROUTE_MAX_WATER_CARRY) {
                basketSurface = ensureBasketGreenLandingZone(
                        world,
                        teeSurface,
                    teeSurface,
                        basketSurface,
                        resolveHoleFairwayWidth(hole),
                        originalBlocks,
                        protectedPositions
                );
                String note = hole.par() >= 5 ? "Par 5 fallback: safe par 4 layout" : "";
                return new HoleRoutePolicyResult(teeSurface, basketSurface, alternateAnchor, Math.min(hole.par(), 4), note);
            }

            basketSurface = chooseLowerCarryBasketSurface(world, teeSurface, basketSurface, PAR34_ROUTE_MAX_WATER_CARRY);
            basketSurface = ensureBasketGreenLandingZone(
                    world,
                    teeSurface,
                    teeSurface,
                    basketSurface,
                    resolveHoleFairwayWidth(hole),
                    originalBlocks,
                    protectedPositions
            );
            teeSurface = relocateTeeSurfaceIfNeeded(world, teeSurface, basketSurface);
        }

        McdgMod.LOGGER.warn(
            "Route policy could not fully satisfy carry target after {} attempts; using best land-safe fallback | hole={} seedPar={} bestAllowedCarry={}",
            ROUTE_POLICY_MAX_RETRIES,
            hole.index(),
            hole.par(),
            PAR34_ROUTE_MAX_WATER_CARRY
        );
        basketSurface = ensureBasketGreenLandingZone(
            world,
            teeSurface,
            teeSurface,
            basketSurface,
            resolveHoleFairwayWidth(hole),
            originalBlocks,
            protectedPositions
        );
        return new HoleRoutePolicyResult(teeSurface, basketSurface, null, Math.min(hole.par(), 4), "Land-safe fallback");
    }

    private static int routeLongestCarryGap(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            BlockPos alternateAnchor
    ) {
        int directGap = computeLongestWaterCarryGap(world, teeSurface, basketSurface);
        if (alternateAnchor == null) {
            return directGap;
        }

        int firstGap = computeLongestWaterCarryGap(world, teeSurface, alternateAnchor);
        int secondGap = computeLongestWaterCarryGap(world, alternateAnchor, basketSurface);
        return Math.max(firstGap, secondGap);
    }

    private static BlockPos chooseLowerCarryBasketSurface(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos baselineBasket,
            int targetCarryGap
    ) {
        BlockPos best = baselineBasket;
        int bestGap = computeLongestWaterCarryGap(world, teeSurface, baselineBasket);
        int bestScore = Integer.MAX_VALUE;
        int scanRadius = BASKET_RELOCATION_RADIUS;

        for (int dx = -scanRadius; dx <= scanRadius; dx += 4) {
            for (int dz = -scanRadius; dz <= scanRadius; dz += 4) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (scanRadius * scanRadius)) {
                    continue;
                }

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, baselineBasket.getX() + dx, baselineBasket.getZ() + dz)
                );
                if (!SurfaceResolver.isPlayableBasketSurface(world, candidate)) {
                    continue;
                }

                int candidateGap = computeLongestWaterCarryGap(world, teeSurface, candidate);
                int score = (candidateGap * 1000) + distSq + Math.abs(candidate.getY() - baselineBasket.getY()) * 8;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestGap = candidateGap;
                    if (candidateGap <= targetCarryGap) {
                        return best;
                    }
                }
            }
        }

        return bestGap < computeLongestWaterCarryGap(world, teeSurface, baselineBasket) ? best : baselineBasket;
    }

    private static BlockPos ensureBasketGreenLandingZone(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos expanded = basketSurface;
        int targetRadius = Math.max(WATER_ADJACENT_BASKET_GREEN_RADIUS, resolveFinishGreenRadius(0, fairwayWidth));
        int currentRadius = Math.max(1, targetRadius);

        for (int pass = 0; pass < 5; pass++) {
            boolean waterAdjacent = isWaterAdjacentArea(
                world,
                expanded,
                WATER_ADJACENT_SCAN_RADIUS,
                WATER_ADJACENT_MIN_COLUMNS
            );
            if (waterAdjacent) {
            expanded = ensureWaterLandingSurface(
                world,
                expanded,
                currentRadius,
                originalBlocks,
                protectedPositions
            );
            } else {
            expanded = ensureLandIslandSurface(
                world,
                expanded,
                currentRadius,
                originalBlocks,
                protectedPositions
            );
            }

            clearHeadroom(
                    world,
                    expanded,
                    currentRadius,
                    6,
                    originalBlocks,
                    protectedPositions
            );
            shapePlayableFinishApproach(
                    world,
                    finishOrigin,
                    expanded,
                    fairwayWidth,
                    currentRadius,
                    countFinishHazardColumns(world, finishOrigin, expanded),
                    originalBlocks,
                    protectedPositions
            );

            if (countSafeLandingColumns(world, expanded, 8) >= FINISH_GREEN_MIN_SAFE_COLUMNS) {
                if (isDeeplyEnclosedBasketSurface(world, expanded)) {
                    BlockPos recovered = tryRecoverEnclosedBasketSurface(
                            world,
                            teeSurface,
                            expanded,
                            originalBlocks,
                            protectedPositions
                    );
                    if (recovered != null && !isDeeplyEnclosedBasketSurface(world, recovered)) {
                        return recovered;
                    }

                    BlockPos relocated = relocateBasketSurfaceIfNeeded(world, teeSurface, expanded);
                    if (!isDeeplyEnclosedBasketSurface(world, relocated)) {
                        return relocated;
                    }
                } else {
                    return expanded;
                }
            }

            currentRadius = Math.min(FINISH_GREEN_MAX_RADIUS, currentRadius + 2);
            if (currentRadius >= FINISH_GREEN_MAX_RADIUS) {
                break;
            }
        }

        return expanded;
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

    public LodgingBuildResult tryBuildPermanentLodgingSite(ServerWorld world, BlockPos preferredOrigin) {
        BlockPos campCenter = SurfaceResolver.findPreferredSurfacePos(world, preferredOrigin.getX(), preferredOrigin.getZ(), true, ANCHOR_SEARCH_RADIUS);
        if (hasNearbyCampSiteMarker(world, campCenter, CAMP_SITE_MARKER_SEARCH_RADIUS)) {
            return new LodgingBuildResult(false, "A lodging site already exists nearby. Find a unique location farther away.", campCenter);
        }
        if (!isSuitableCampSite(world, campCenter)) {
            return new LodgingBuildResult(false, "This area is not suitable for a full camp footprint. Pick a flatter, safer site.", campCenter);
        }

        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        Set<BlockPos> protectedPositions = new HashSet<>();
        int[] side = new int[] { 1, 0 };
        int[] back = new int[] { 0, 1 };
        placePermanentLodgingSite(world, campCenter, side, back, originalBlocks, protectedPositions);

        BlockPos markerPos = campCenter.down();
        PlacementUtils.setTrackedBlock(world, markerPos, CAMP_SITE_MARKER_BLOCK, originalBlocks);
        return new LodgingBuildResult(true, "Permanent lodging site built.", campCenter);
    }

    private static CourseBounds findCourseBounds(Course course) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Hole hole : course.holes()) {
            minX = Math.min(minX, Math.min(hole.tee().x(), hole.basket().x()));
            minZ = Math.min(minZ, Math.min(hole.tee().z(), hole.basket().z()));
            maxX = Math.max(maxX, Math.max(hole.tee().x(), hole.basket().x()));
            maxZ = Math.max(maxZ, Math.max(hole.tee().z(), hole.basket().z()));
        }

        if (minX == Integer.MAX_VALUE) {
            return new CourseBounds(0, 0, 0, 0, 0, 0);
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        return new CourseBounds(minX, minZ, maxX, maxZ, centerX, centerZ);
    }

    private static BlockPos findPreferredCourseAnchor(
            ServerWorld world,
            BlockPos origin,
            Course course,
            CourseBounds courseBounds,
            Set<Long> rejectedAnchorKeys
    ) {
        int x = origin.getX();
        int z = origin.getZ();
        BlockPos best = SurfaceResolver.resolveSurfacePos(world, x, z);
        int bestScore = scoreCourseAnchor(world, best, x, z, course, courseBounds, rejectedAnchorKeys);

        int ringStep = 4;
        for (int radius = ringStep; radius <= ANCHOR_SEARCH_RADIUS; radius += ringStep) {
            for (int sx = x - radius; sx <= x + radius; sx += ringStep) {
                BlockPos north = SurfaceResolver.resolveSurfacePos(world, sx, z - radius);
                int northScore = scoreCourseAnchor(world, north, x, z, course, courseBounds, rejectedAnchorKeys);
                if (northScore < bestScore) {
                    bestScore = northScore;
                    best = north;
                }

                BlockPos south = SurfaceResolver.resolveSurfacePos(world, sx, z + radius);
                int southScore = scoreCourseAnchor(world, south, x, z, course, courseBounds, rejectedAnchorKeys);
                if (southScore < bestScore) {
                    bestScore = southScore;
                    best = south;
                }
            }

            for (int sz = z - radius + ringStep; sz <= z + radius - ringStep; sz += ringStep) {
                BlockPos west = SurfaceResolver.resolveSurfacePos(world, x - radius, sz);
                int westScore = scoreCourseAnchor(world, west, x, z, course, courseBounds, rejectedAnchorKeys);
                if (westScore < bestScore) {
                    bestScore = westScore;
                    best = west;
                }

                BlockPos east = SurfaceResolver.resolveSurfacePos(world, x + radius, sz);
                int eastScore = scoreCourseAnchor(world, east, x, z, course, courseBounds, rejectedAnchorKeys);
                if (eastScore < bestScore) {
                    bestScore = eastScore;
                    best = east;
                }
            }
        }

        return SurfaceResolver.refineLandCandidate(world, best, x, z);
    }

    private static int scoreCourseAnchor(
            ServerWorld world,
            BlockPos candidate,
            int targetX,
            int targetZ,
            Course course,
            CourseBounds courseBounds,
            Set<Long> rejectedAnchorKeys
    ) {
        int score = SurfaceResolver.scoreSurface(world, candidate, targetX, targetZ, true) + localWaterPenalty(world, candidate);
        if (rejectedAnchorKeys.contains(anchorClusterKey(candidate))) {
            score += 2_000_000;
        }

        double waterRatio = estimateProjectedWaterRatio(world, course, candidate, courseBounds);
        score += (int) Math.round(waterRatio * COURSE_ANCHOR_WATER_RATIO_SCORE_WEIGHT);

        if (waterRatio > COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO) {
            score += COURSE_ANCHOR_WATER_REJECT_PENALTY;
            score += (int) Math.round((waterRatio - COURSE_ANCHOR_MAX_WATER_SAMPLE_RATIO) * 100000.0);
        }

        return score;
    }

    private static double estimateProjectedWaterRatio(
            ServerWorld world,
            Course course,
            BlockPos anchor,
            CourseBounds courseBounds
    ) {
        if (course.holes().isEmpty()) {
            return 0.0;
        }

        int offsetX = anchor.getX() - courseBounds.centerX();
        int offsetZ = anchor.getZ() - courseBounds.centerZ();
        int waterSamples = 0;
        int totalSamples = 0;

        for (Hole hole : course.holes()) {
            waterSamples += projectedWaterSample(world, hole.tee().x(), hole.tee().z(), offsetX, offsetZ);
            waterSamples += projectedWaterSample(world, hole.basket().x(), hole.basket().z(), offsetX, offsetZ);
            waterSamples += projectedWaterSample(
                    world,
                    (hole.tee().x() + hole.basket().x()) / 2,
                    (hole.tee().z() + hole.basket().z()) / 2,
                    offsetX,
                    offsetZ
            );
            totalSamples += 3;
        }

        return totalSamples == 0 ? 0.0 : (waterSamples / (double) totalSamples);
    }

    private static int projectedWaterSample(ServerWorld world, int templateX, int templateZ, int offsetX, int offsetZ) {
        int worldX = templateX + offsetX;
        int worldZ = templateZ + offsetZ;
        return isWaterCrossingColumn(world, worldX, worldZ) ? 1 : 0;
    }

    private static long anchorClusterKey(BlockPos pos) {
        // Use 16-block clusters so that rejecting a refined anchor also penalises
        // nearby raw candidates that refine to the same spot.
        long cx = (long) (pos.getX() >> 4);
        long cz = (long) (pos.getZ() >> 4);
        return (cx << 32) ^ (cz & 0xffffffffL);
    }

    private record CourseBounds(int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ) {
    }

        private record HoleRoutePolicyResult(
            BlockPos teeSurface,
            BlockPos basketSurface,
            BlockPos alternateAnchor,
            int effectivePar,
            String routingNote
        ) {
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









    private static void placeTeePad(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                PlacementUtils.setTrackedBlock(world, pos, Blocks.SMOOTH_STONE.getDefaultState(), originalBlocks);
            }
        }
        PlacementUtils.setTrackedBlock(world, center, Blocks.LIME_CONCRETE.getDefaultState(), originalBlocks);
    }

    private static void placeBasketMarker(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks, int basketHeight) {
        BlockState ground = world.getBlockState(center);
        if (!SurfaceResolver.isBasketGroundSafe(ground)) {
            PlacementUtils.setTrackedBlock(world, center, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
        }

        BlockPos base = center.up();
        // Keep the marker column dry so the basket remains playable and visible.
        for (int i = 0; i <= basketHeight + 2; i++) {
            BlockPos markerPos = base.up(i);
            if (!world.getFluidState(markerPos).isEmpty()) {
                PlacementUtils.setTrackedBlock(world, markerPos, Blocks.AIR.getDefaultState(), originalBlocks);
            }
        }
        PlacementUtils.setTrackedBlock(world, base, Blocks.HOPPER.getDefaultState(), originalBlocks);

        for (int i = 1; i <= basketHeight + 1; i++) {
            PlacementUtils.setTrackedBlock(world, base.up(i), Blocks.IRON_BARS.getDefaultState(), originalBlocks);
        }

        PlacementUtils.setTrackedBlock(world, base.up(basketHeight + 2), Blocks.LANTERN.getDefaultState(), originalBlocks);
    }


    private static void carveFairway(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            int width,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            boolean placeLanterns
    ) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        if (steps < 1) {
            steps = 1;
        }
        int stepStride = 1;

        int radius = Math.max(1, width / 2);
        int lastLanternStep = Integer.MIN_VALUE;
        int lastWaterPatchStep = Integer.MIN_VALUE;
        int waterCarryStreak = 0;
        Set<BlockPos> clearedTreeNodes = new HashSet<>();
        int dirX = Integer.compare(endX, startX);
        int dirZ = Integer.compare(endZ, startZ);
        int sideX = -dirZ;
        int sideZ = dirX;
        if (sideX == 0 && sideZ == 0) {
            sideX = 1;
            sideZ = 0;
        }

        for (int i = 0; i <= steps; i += stepStride) {
            double t = i / (double) steps;
            int x = (int) Math.round(startX + (endX - startX) * t);
            int z = (int) Math.round(startZ + (endZ - startZ) * t);

            boolean waterColumn = isWaterCrossingColumn(world, x, z);
            if (waterColumn) {
                waterCarryStreak++;
            } else {
                waterCarryStreak = 0;
            }

            if (i > 0
                    && i < steps
                    && waterColumn
                    && (waterCarryStreak >= WATER_LANDING_PATCH_MAX_CARRY || i - lastWaterPatchStep >= WATER_LANDING_PATCH_INTERVAL)) {
                BlockPos waterSurface = SurfaceResolver.resolveSurfacePos(world, x, z);
                BlockPos landingCenter = ensureWaterLandingSurface(
                        world,
                        waterSurface,
                        WATER_LANDING_PATCH_RADIUS,
                        originalBlocks,
                        protectedPositions
                );
                clearHeadroom(world, landingCenter, WATER_LANDING_PATCH_RADIUS, 5, originalBlocks, protectedPositions);
                addProtectedColumnArea(protectedPositions, landingCenter, WATER_LANDING_PATCH_RADIUS, 5);
                lastWaterPatchStep = i;
                waterCarryStreak = 0;
            }

            BlockPos center = SurfaceResolver.findPreferredSurfacePos(world, x, z, true, FAIRWAY_SEARCH_RADIUS);
            int tunedRadius = Math.min(2, tunedPathRadius(world, center, radius));
            if (steps - i <= FINISH_APPROACH_WIDEN_DISTANCE
                    && isWaterAdjacentArea(world, center, WATER_LANDING_ENFORCE_SCAN_RADIUS, WATER_ADJACENT_MIN_COLUMNS)) {
                tunedRadius = Math.max(tunedRadius, Math.min(3, radius + 1));
            }
            BlockState pathState = selectPathMaterial(world, center);

            for (int dx = -tunedRadius; dx <= tunedRadius; dx++) {
                for (int dz = -tunedRadius; dz <= tunedRadius; dz++) {
                    if ((dx * dx) + (dz * dz) > (tunedRadius * tunedRadius)) {
                        continue;
                    }

                    int sampleX = center.getX() + dx;
                    int sampleZ = center.getZ() + dz;
                    BlockPos surface = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, sampleX, sampleZ));
                    int pathY = surface.getY();

                    // Keep fairway natural: only remove vegetation/tree material up to local canopy height.
                    clearFairwayColumnVegetation(
                            world,
                            sampleX,
                            sampleZ,
                            pathY,
                            originalBlocks,
                            protectedPositions,
                            clearedTreeNodes
                    );

                    BlockPos pathPos = new BlockPos(sampleX, pathY, sampleZ);
                    if (isProtected(protectedPositions, pathPos)) {
                        continue;
                    }
                    if (world.getBlockState(pathPos).equals(pathState)) {
                        continue;
                    }

                    PlacementUtils.setTrackedBlock(world, pathPos, pathState, originalBlocks);
                }
            }

                    // Run an explicit sweep around the step center so trunk columns adjacent to the path do not survive.
                    clearFairwaySweepVolume(
                        world,
                        center,
                        tunedRadius + FAIRWAY_LOG_SWEEP_EXTRA_RADIUS,
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                    );

            // Add occasional light posts for navigation, keeping spacing so paths do not feel cluttered.
            if (placeLanterns && i - lastLanternStep >= 12 && shouldPlaceFairwayLantern(center.getX(), center.getZ())) {
                int lanternSide = (coordinateNoise(center.getX() * 31, center.getZ() * 17) & 1) == 0 ? 1 : -1;
                int lanternX = center.getX() + (sideX * (tunedRadius + 1) * lanternSide);
                int lanternZ = center.getZ() + (sideZ * (tunedRadius + 1) * lanternSide);
                BlockPos lanternBase = ensureLandIslandSurface(world,
                        SurfaceResolver.findPreferredSurfacePos(world, lanternX, lanternZ, true, FAIRWAY_SEARCH_RADIUS), 1, originalBlocks, protectedPositions);
                if (isProtected(protectedPositions, lanternBase.up())) {
                    continue;
                }
                placeLanternPost(world, lanternBase, 2, originalBlocks);
                lastLanternStep = i;
            }
        }

        enforceWaterLandingContinuity(
                world,
                startX,
                startZ,
                endX,
                endZ,
                originalBlocks,
                protectedPositions
        );
    }

    private static void enforceBasketApproachLandingZone(
            ServerWorld world,
            BlockPos approachStart,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int approachGap = computeLongestWaterCarryGap(world, approachStart, basketSurface);
        if (approachGap <= TEE_MAX_DIRECT_CARRY_GAP) {
            return;
        }

        int dx = basketSurface.getX() - approachStart.getX();
        int dz = basketSurface.getZ() - approachStart.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int zoneSteps = Math.min(steps, BASKET_APPROACH_ENFORCE_DISTANCE);
        int zoneStartStep = Math.max(0, steps - zoneSteps);
        double startT = zoneStartStep / (double) steps;
        int zoneStartX = (int) Math.round(approachStart.getX() + (dx * startT));
        int zoneStartZ = (int) Math.round(approachStart.getZ() + (dz * startT));

        carveFairway(
                world,
                zoneStartX,
                zoneStartZ,
                basketSurface.getX(),
                basketSurface.getZ(),
                Math.max(fairwayWidth, BASKET_APPROACH_MIN_WIDTH),
                originalBlocks,
                protectedPositions,
                false
        );
    }

    private static void enforceWaterLandingContinuity(
            ServerWorld world,
            int startX,
            int startZ,
            int endX,
            int endZ,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = endX - startX;
        int dz = endZ - startZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int currentGap = 0;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(startX + (dx * t));
            int z = (int) Math.round(startZ + (dz * t));

            if (!isWaterCrossingColumn(world, x, z)) {
                currentGap = 0;
                continue;
            }

            if (hasAnyWalkableLandingNearby(world, x, z, WATER_LANDING_ENFORCE_SCAN_RADIUS)) {
                currentGap = 0;
                continue;
            }

            currentGap++;
            if (currentGap <= WATER_LANDING_ENFORCE_MAX_GAP) {
                continue;
            }

            BlockPos landingCenter = ensureLandIslandSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, x, z),
                    WATER_LANDING_PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
            );
                landingCenter = ensureWaterLandingSurface(
                    world,
                    landingCenter,
                    WATER_LANDING_PATCH_RADIUS,
                    originalBlocks,
                    protectedPositions
                );
            clearHeadroom(world, landingCenter, WATER_LANDING_PATCH_RADIUS, 5, originalBlocks, protectedPositions);
            addProtectedColumnArea(protectedPositions, landingCenter, WATER_LANDING_PATCH_RADIUS, 5);
            currentGap = 0;
        }
    }

    private static void clearFairwaySweepVolume(
            ServerWorld world,
            BlockPos center,
            int sweepRadius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        int radius = Math.max(1, sweepRadius);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                BlockPos surface = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, x, z));
                clearFairwayColumnVegetation(
                        world,
                        x,
                        z,
                        surface.getY(),
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                );
            }
        }
    }

    private static void clearFairwayColumnVegetation(
            ServerWorld world,
            int x,
            int z,
            int pathY,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        int floorY = Math.max(world.getBottomY(), pathY - FAIRWAY_CLEAR_BOTTOM_PADDING);
        int ceilingY = Math.max(pathY, topY) + FAIRWAY_CLEAR_TOP_PADDING;

        for (int y = floorY; y <= ceilingY; y++) {
            BlockPos target = new BlockPos(x, y, z);
            if (isProtected(protectedPositions, target)) {
                continue;
            }

            BlockState state = world.getBlockState(target);
            if (!isClearable(state)) {
                continue;
            }

            if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
                clearConnectedTreeCluster(
                        world,
                        target,
                        floorY - 8,
                        ceilingY + 16,
                        originalBlocks,
                        protectedPositions,
                        clearedTreeNodes
                );
                continue;
            }

            PlacementUtils.setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
            clearedTreeNodes.add(target.toImmutable());
        }
    }

    private static void clearConnectedTreeCluster(
            ServerWorld world,
            BlockPos root,
            int minY,
            int maxY,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            Set<BlockPos> clearedTreeNodes
    ) {
        if (clearedTreeNodes.contains(root)) {
            return;
        }

        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root.toImmutable());

        int cleared = 0;
        while (!queue.isEmpty() && cleared < TREE_CLUSTER_CLEAR_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!clearedTreeNodes.add(pos)) {
                continue;
            }
            if (pos.getY() < minY || pos.getY() > maxY) {
                continue;
            }
            if (isProtected(protectedPositions, pos)) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            boolean treeMaterial = state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
            if (!treeMaterial) {
                continue;
            }

            PlacementUtils.setTrackedBlock(world, pos, Blocks.AIR.getDefaultState(), originalBlocks);
            cleared++;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = pos.add(dx, dy, dz).toImmutable();
                        if (!clearedTreeNodes.contains(next)) {
                            queue.addLast(next);
                        }
                    }
                }
            }
        }
    }

    private static int resolveHoleFairwayWidth(Hole hole) {
        int width = 4;
        for (FairwaySegment segment : hole.fairwaySegments()) {
            width = Math.max(width, segment.width());
        }
        return Math.max(3, Math.min(5, width));
    }

    private static boolean shouldPlaceFairwayLantern(int x, int z) {
        int noise = coordinateNoise(x, z);
        return Math.floorMod(noise, 100) < 17;
    }

    private static int coordinateNoise(int x, int z) {
        int hash = x * 73428767;
        hash ^= z * 912673;
        hash ^= (hash >>> 13);
        hash *= 1274126177;
        return hash;
    }

    private static void placeLanternPost(ServerWorld world, BlockPos ground, int postHeight, Map<BlockPos, BlockState> originalBlocks) {
        int height = Math.max(1, postHeight);
        clearHeadroom(world, ground, 1, height + 2, originalBlocks, null);
        for (int i = 1; i <= height; i++) {
            PlacementUtils.setTrackedBlock(world, ground.up(i), Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
        }
        PlacementUtils.setTrackedBlock(world, ground.up(height + 1), Blocks.LANTERN.getDefaultState(), originalBlocks);
    }

    private static void placeCourseCentralHub(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketSurface,
            String courseName,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int[] forward = teeForwardUnit(teeCenter, basketSurface);
        int[] back = new int[] { -forward[0], -forward[1] };
        int[] side = new int[] { -forward[1], forward[0] };

        BlockPos hubSeed = teeCenter.add(back[0] * 9, 0, back[1] * 9);
        BlockPos hubSurface = SurfaceResolver.normalizePlayableSurface(
            world,
            SurfaceResolver.findPreferredSurfacePos(world, hubSeed.getX(), hubSeed.getZ(), true, 16)
        );
        clearHeadroom(world, hubSurface, 9, 6, originalBlocks, protectedPositions);

        buildCourseCentralDeck(world, hubSurface, side, back, originalBlocks, protectedPositions);
        placeRegistrationDesk(world, hubSurface, side, back, originalBlocks, protectedPositions);
        placeMerchCanopy(world, hubSurface, side, back, originalBlocks, protectedPositions);
        placePracticeBaskets(world, hubSurface, side, back, originalBlocks, protectedPositions);
        SignTextGenerator.placeLeaderboardSign(world, hubSurface, side, back, courseName, originalBlocks, protectedPositions);

        addProtectedColumnArea(protectedPositions, hubSurface, 9, 7);
    }

    private static void buildCourseCentralDeck(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int v = -3; v <= 8; v++) {
            for (int u = -8; u <= 8; u++) {
                BlockPos pos = PlacementUtils.orientedOffset(hubSurface, side, back, u, v, 0);
                if (isProtected(protectedPositions, pos)) {
                    continue;
                }
                boolean rim = Math.abs(u) >= 8 || v <= -3 || v >= 8;
                PlacementUtils.setTrackedBlock(world, pos, rim ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.SPRUCE_PLANKS.getDefaultState(), originalBlocks);
            }
        }

        for (int step = 3; step <= 8; step++) {
            BlockPos walkway = hubSurface.add(-back[0] * step, 0, -back[1] * step);
            if (!isProtected(protectedPositions, walkway)) {
                PlacementUtils.setTrackedBlock(world, walkway, Blocks.SMOOTH_STONE.getDefaultState(), originalBlocks);
            }
        }
    }

    private static void placeRegistrationDesk(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos deskOrigin = PlacementUtils.orientedOffset(hubSurface, side, back, -4, 0, 0);

        for (int u = 0; u <= 4; u++) {
            for (int v = 0; v <= 1; v++) {
                BlockPos top = PlacementUtils.orientedOffset(deskOrigin, side, back, u, v, 1);
                if (!isProtected(protectedPositions, top)) {
                    PlacementUtils.setTrackedBlock(world, top, Blocks.SMOOTH_STONE_SLAB.getDefaultState(), originalBlocks);
                }
            }
        }

        int[][] legs = {
                {0, 0}, {4, 0}, {0, 1}, {4, 1}
        };
        for (int[] leg : legs) {
            BlockPos legPos = PlacementUtils.orientedOffset(deskOrigin, side, back, leg[0], leg[1], 0);
            if (!isProtected(protectedPositions, legPos)) {
                PlacementUtils.setTrackedBlock(world, legPos, Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
            }
        }

        int[][] terminals = {
                {1, 0}, {2, 0}, {3, 0}
        };
        for (int[] terminal : terminals) {
            BlockPos terminalPos = PlacementUtils.orientedOffset(deskOrigin, side, back, terminal[0], terminal[1], 2);
            if (!isProtected(protectedPositions, terminalPos)) {
                PlacementUtils.setTrackedBlock(world, terminalPos, Blocks.DAYLIGHT_DETECTOR.getDefaultState(), originalBlocks);
            }
        }
    }

    private static void placeMerchCanopy(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos canopyCenter = PlacementUtils.orientedOffset(hubSurface, side, back, 4, 2, 0);

        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                BlockPos roof = PlacementUtils.orientedOffset(canopyCenter, side, back, u, v, 4);
                if (!isProtected(protectedPositions, roof)) {
                    PlacementUtils.setTrackedBlock(world, roof, Blocks.WHITE_WOOL.getDefaultState(), originalBlocks);
                }
            }
        }

        int[][] posts = {
                {-2, -2}, {2, -2}, {-2, 2}, {2, 2}
        };
        for (int[] post : posts) {
            for (int y = 1; y <= 3; y++) {
                BlockPos postPos = PlacementUtils.orientedOffset(canopyCenter, side, back, post[0], post[1], y);
                if (!isProtected(protectedPositions, postPos)) {
                    PlacementUtils.setTrackedBlock(world, postPos, Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
                }
            }
        }

        for (int u = -1; u <= 1; u++) {
            BlockPos merchTableA = PlacementUtils.orientedOffset(canopyCenter, side, back, u, -1, 1);
            BlockPos merchTableB = PlacementUtils.orientedOffset(canopyCenter, side, back, u, 1, 1);
            if (!isProtected(protectedPositions, merchTableA)) {
                PlacementUtils.setTrackedBlock(world, merchTableA, Blocks.BARREL.getDefaultState(), originalBlocks);
            }
            if (!isProtected(protectedPositions, merchTableB)) {
                PlacementUtils.setTrackedBlock(world, merchTableB, Blocks.BARREL.getDefaultState(), originalBlocks);
            }
        }
    }

    private static void placePracticeBaskets(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos leftPracticeTarget = PlacementUtils.orientedOffset(hubSurface, side, back, -7, 6, 0);
        BlockPos rightPracticeTarget = PlacementUtils.orientedOffset(hubSurface, side, back, 7, 6, 0);

        BlockPos leftSurface = SurfaceResolver.resolveSurfacePos(world, leftPracticeTarget.getX(), leftPracticeTarget.getZ());
        BlockPos rightSurface = SurfaceResolver.resolveSurfacePos(world, rightPracticeTarget.getX(), rightPracticeTarget.getZ());

        if (isUnsafeSurface(world, leftSurface)) {
            leftSurface = PlacementUtils.orientedOffset(hubSurface, side, back, -7, 6, 0);
        }
        if (isUnsafeSurface(world, rightSurface)) {
            rightSurface = PlacementUtils.orientedOffset(hubSurface, side, back, 7, 6, 0);
        }

        clearHeadroom(world, leftSurface, 1, 6, originalBlocks, protectedPositions);
        clearHeadroom(world, rightSurface, 1, 6, originalBlocks, protectedPositions);
        placeBasketMarker(world, leftSurface, originalBlocks, 2);
        placeBasketMarker(world, rightSurface, originalBlocks, 2);
        addProtectedColumnArea(protectedPositions, leftSurface, 1, 6);
        addProtectedColumnArea(protectedPositions, rightSurface, 1, 6);
    }


    private static void placePermanentLodgingSite(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos campSeed = PlacementUtils.orientedOffset(hubSurface, side, back, 0, 22, 0);
        BlockPos campCenter = resolveCampSurfaceCenter(world, campSeed, originalBlocks, protectedPositions);
        buildCampCommons(world, campCenter, side, back, originalBlocks, protectedPositions);

        int[][] yurtOffsets = {
                {0, 14},
                {12, 7},
                {12, -7},
                {0, -14},
                {-12, -7},
                {-12, 7}
        };

        for (int i = 0; i < yurtOffsets.length; i++) {
            BlockPos yurtSeed = PlacementUtils.orientedOffset(campCenter, side, back, yurtOffsets[i][0], yurtOffsets[i][1], 0);
            BlockPos yurtCenter = resolveCampSurfaceCenter(world, yurtSeed, originalBlocks, protectedPositions);
            clearHeadroom(world, yurtCenter, 5, 7, originalBlocks, protectedPositions);
            placePlayerYurt(world, yurtCenter, campCenter, i, originalBlocks, protectedPositions);
        }

        BlockPos poolCenter = resolveCampSurfaceCenter(
            world,
            PlacementUtils.orientedOffset(campCenter, side, back, -28, 22, 0),
            originalBlocks,
            protectedPositions
        );
        BlockPos tennisCenter = resolveCampSurfaceCenter(
            world,
            PlacementUtils.orientedOffset(campCenter, side, back, 28, 22, 0),
            originalBlocks,
            protectedPositions
        );
        BlockPos basketballCenter = resolveCampSurfaceCenter(
            world,
            PlacementUtils.orientedOffset(campCenter, side, back, 0, 46, 0),
            originalBlocks,
            protectedPositions
        );
        BlockPos bathhouseCenter = resolveCampSurfaceCenter(
            world,
            PlacementUtils.orientedOffset(campCenter, side, back, -28, 46, 0),
            originalBlocks,
            protectedPositions
        );

        placeSwimmingPool(world, poolCenter, side, back, originalBlocks, protectedPositions);
        placeTennisCourt(world, tennisCenter, side, back, originalBlocks, protectedPositions);
        placeBasketballCourt(world, basketballCenter, side, back, originalBlocks, protectedPositions);
        placeBathhouse(world, bathhouseCenter, side, back, originalBlocks, protectedPositions);

        addProtectedColumnArea(protectedPositions, campCenter, 8, 8);
    }

    private static BlockPos resolveCampSurfaceCenter(
            ServerWorld world,
            BlockPos seed,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos center = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, seed.getX(), seed.getZ()));
        if (isUnsafeSurface(world, center)) {
            center = ensureLandIslandSurface(world, center, 2, originalBlocks, protectedPositions);
        }
        return center;
    }

    private static void buildCampCommons(
            ServerWorld world,
            BlockPos campCenter,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int u = -7; u <= 7; u++) {
            for (int v = -7; v <= 7; v++) {
                int distSq = (u * u) + (v * v);
                if (distSq > 52) {
                    continue;
                }

                BlockPos ground = PlacementUtils.orientedOffset(campCenter, side, back, u, v, 0);
                if (isProtected(protectedPositions, ground)) {
                    continue;
                }
                BlockState material = distSq > 36
                        ? Blocks.POLISHED_ANDESITE.getDefaultState()
                        : Blocks.GRAVEL.getDefaultState();
                PlacementUtils.setTrackedBlock(world, ground, material, originalBlocks);
            }
        }

        int[][] fireOffsets = {
                {0, 0},
                {-2, 2},
                {2, 2},
                {-2, -2},
                {2, -2}
        };
        for (int i = 0; i < fireOffsets.length; i++) {
            BlockPos firePos = PlacementUtils.orientedOffset(campCenter, side, back, fireOffsets[i][0], fireOffsets[i][1], 0);
            if (isProtected(protectedPositions, firePos)) {
                continue;
            }
            PlacementUtils.setTrackedBlock(world, firePos, i == 0 ? Blocks.SOUL_CAMPFIRE.getDefaultState() : Blocks.CAMPFIRE.getDefaultState(), originalBlocks);
            addProtectedColumnArea(protectedPositions, firePos, 0, 4);
        }
    }

    private static void placePlayerYurt(
            ServerWorld world,
            BlockPos yurtCenter,
            BlockPos campCenter,
            int yurtIndex,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockState[] floorMaterials = {
                Blocks.SPRUCE_PLANKS.getDefaultState(),
                Blocks.BIRCH_PLANKS.getDefaultState(),
                Blocks.DARK_OAK_PLANKS.getDefaultState(),
                Blocks.JUNGLE_PLANKS.getDefaultState(),
                Blocks.MANGROVE_PLANKS.getDefaultState(),
                Blocks.BAMBOO_PLANKS.getDefaultState()
        };
        BlockState[] wallMaterials = {
                Blocks.WHITE_WOOL.getDefaultState(),
                Blocks.LIGHT_BLUE_WOOL.getDefaultState(),
                Blocks.YELLOW_WOOL.getDefaultState(),
                Blocks.ORANGE_WOOL.getDefaultState(),
                Blocks.LIME_WOOL.getDefaultState(),
                Blocks.RED_WOOL.getDefaultState()
        };
        BlockState[] bedMaterials = {
                Blocks.WHITE_BED.getDefaultState(),
                Blocks.BLUE_BED.getDefaultState(),
                Blocks.YELLOW_BED.getDefaultState(),
                Blocks.ORANGE_BED.getDefaultState(),
                Blocks.LIME_BED.getDefaultState(),
                Blocks.RED_BED.getDefaultState()
        };

        BlockState floor = floorMaterials[Math.floorMod(yurtIndex, floorMaterials.length)];
        BlockState wall = wallMaterials[Math.floorMod(yurtIndex, wallMaterials.length)];
        BlockState bed = bedMaterials[Math.floorMod(yurtIndex, bedMaterials.length)];

        Direction doorFacing = PlacementUtils.cardinalDirectionToward(yurtCenter, campCenter);
        Direction interiorFacing = doorFacing.getOpposite();
        int doorSide = PlacementUtils.directionToSideStep(doorFacing);
        int doorForward = PlacementUtils.directionToForwardStep(doorFacing);

        for (int u = -4; u <= 3; u++) {
            for (int v = -4; v <= 3; v++) {
                BlockPos floorPos = yurtCenter.add(u, 0, v);
                if (!isProtected(protectedPositions, floorPos)) {
                    PlacementUtils.setTrackedBlock(world, floorPos, floor, originalBlocks);
                }

                boolean edge = u == -4 || u == 3 || v == -4 || v == 3;
                if (!edge) {
                    continue;
                }

                boolean doorColumn = u == doorSide && v == doorForward;
                if (!doorColumn) {
                    for (int y = 1; y <= 3; y++) {
                        BlockPos wallPos = yurtCenter.add(u, y, v);
                        if (!isProtected(protectedPositions, wallPos)) {
                            PlacementUtils.setTrackedBlock(world, wallPos, wall, originalBlocks);
                        }
                    }
                }

                BlockPos roofPos = yurtCenter.add(u, 4, v);
                if (!isProtected(protectedPositions, roofPos)) {
                    PlacementUtils.setTrackedBlock(world, roofPos, Blocks.SPRUCE_SLAB.getDefaultState(), originalBlocks);
                }
            }
        }

        for (int u = -2; u <= 1; u++) {
            for (int v = -2; v <= 1; v++) {
                BlockPos roofCrown = yurtCenter.add(u, 5, v);
                if (!isProtected(protectedPositions, roofCrown)) {
                    PlacementUtils.setTrackedBlock(world, roofCrown, wall, originalBlocks);
                }
            }
        }

        BlockPos lanternAnchor = yurtCenter.up(4);
        if (!isProtected(protectedPositions, lanternAnchor)) {
            PlacementUtils.setTrackedBlock(world, lanternAnchor, Blocks.CHAIN.getDefaultState(), originalBlocks);
        }
        if (!isProtected(protectedPositions, lanternAnchor.down())) {
            PlacementUtils.setTrackedBlock(world, lanternAnchor.down(), Blocks.LANTERN.getDefaultState(), originalBlocks);
        }

        int bedSide = Math.max(-2, Math.min(1, PlacementUtils.directionToSideStep(interiorFacing) * 2));
        int bedForward = Math.max(-2, Math.min(1, PlacementUtils.directionToForwardStep(interiorFacing) * 2));
        BlockPos bedFoot = yurtCenter.add(bedSide, 1, bedForward);
        if (!isProtected(protectedPositions, bedFoot)) {
            PlacementUtils.setTrackedBlock(world, bedFoot, bed.with(Properties.HORIZONTAL_FACING, interiorFacing).with(Properties.BED_PART, BedPart.FOOT), originalBlocks);
        }
        BlockPos bedHead = bedFoot.offset(interiorFacing);
        if (!isProtected(protectedPositions, bedHead)) {
            PlacementUtils.setTrackedBlock(world, bedHead, bed.with(Properties.HORIZONTAL_FACING, interiorFacing).with(Properties.BED_PART, BedPart.HEAD), originalBlocks);
        }

        BlockPos chestPos = yurtCenter.add(-2, 1, -2);
        BlockPos craftingPos = yurtCenter.add(2, 1, -2);
        BlockPos furnacePos = yurtCenter.add(-2, 1, 2);
        BlockPos smelterPos = yurtCenter.add(2, 1, 2);
        if (!isProtected(protectedPositions, chestPos)) {
            PlacementUtils.setTrackedBlock(world, chestPos, yurtIndex % 2 == 0 ? Blocks.CHEST.getDefaultState() : Blocks.BARREL.getDefaultState(), originalBlocks);
        }
        if (!isProtected(protectedPositions, craftingPos)) {
            PlacementUtils.setTrackedBlock(world, craftingPos, Blocks.CRAFTING_TABLE.getDefaultState(), originalBlocks);
        }
        if (!isProtected(protectedPositions, furnacePos)) {
            PlacementUtils.setTrackedBlock(world, furnacePos, Blocks.FURNACE.getDefaultState(), originalBlocks);
        }
        if (!isProtected(protectedPositions, smelterPos)) {
            PlacementUtils.setTrackedBlock(world, smelterPos, Blocks.BLAST_FURNACE.getDefaultState(), originalBlocks);
        }

        placeYurtUniqueAccent(world, yurtCenter, yurtIndex, originalBlocks, protectedPositions);
        addProtectedColumnArea(protectedPositions, yurtCenter, 5, 8);
    }

    private static void placeYurtUniqueAccent(
            ServerWorld world,
            BlockPos yurtCenter,
            int yurtIndex,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int style = Math.floorMod(yurtIndex, 6);
        switch (style) {
            case 0 -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.BOOKSHELF.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.LECTERN.getDefaultState(), originalBlocks, protectedPositions);
            }
            case 1 -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.POTTED_DANDELION.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.FLOWERING_AZALEA.getDefaultState(), originalBlocks, protectedPositions);
            }
            case 2 -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.CARTOGRAPHY_TABLE.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.LOOM.getDefaultState(), originalBlocks, protectedPositions);
            }
            case 3 -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.SMITHING_TABLE.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.ANVIL.getDefaultState(), originalBlocks, protectedPositions);
            }
            case 4 -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.BREWING_STAND.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.CAULDRON.getDefaultState(), originalBlocks, protectedPositions);
            }
            default -> {
                placeInteriorBlock(world, yurtCenter.add(-1, 1, 0), Blocks.JUKEBOX.getDefaultState(), originalBlocks, protectedPositions);
                placeInteriorBlock(world, yurtCenter.add(0, 1, 0), Blocks.NOTE_BLOCK.getDefaultState(), originalBlocks, protectedPositions);
            }
        }
    }

    private static void placeSwimmingPool(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos poolCenter = center;
        clearHeadroom(world, poolCenter, 11, 6, originalBlocks, protectedPositions);

        for (int u = -7; u <= 7; u++) {
            for (int v = -5; v <= 5; v++) {
                BlockPos floor = PlacementUtils.orientedOffset(poolCenter, side, back, u, v, -2);
                BlockPos mid = floor.up(1);
                BlockPos top = floor.up(2);
                if (!isProtected(protectedPositions, floor)) {
                    PlacementUtils.setTrackedBlock(world, floor, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                }

                boolean edge = Math.abs(u) == 7 || Math.abs(v) == 5;
                if (edge) {
                    if (!isProtected(protectedPositions, mid)) {
                        PlacementUtils.setTrackedBlock(world, mid, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                    }
                    if (!isProtected(protectedPositions, top)) {
                        PlacementUtils.setTrackedBlock(world, top, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                    }
                } else {
                    if (!isProtected(protectedPositions, mid)) {
                        PlacementUtils.setTrackedBlock(world, mid, Blocks.WATER.getDefaultState(), originalBlocks);
                    }
                    if (!isProtected(protectedPositions, top)) {
                        PlacementUtils.setTrackedBlock(world, top, Blocks.WATER.getDefaultState(), originalBlocks);
                    }
                }
            }
        }

        placeFacilityLights(
                world,
                poolCenter,
                side,
                back,
                new int[][] { { -10, -8 }, { 10, -8 }, { -10, 8 }, { 10, 8 } },
                3,
                originalBlocks,
                protectedPositions
        );

        addProtectedColumnArea(protectedPositions, poolCenter, 11, 6);
    }

    private static void placeTennisCourt(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos courtCenter = center;
        clearHeadroom(world, courtCenter, 15, 8, originalBlocks, protectedPositions);

        for (int u = -11; u <= 11; u++) {
            for (int v = -6; v <= 6; v++) {
                BlockPos pos = PlacementUtils.orientedOffset(courtCenter, side, back, u, v, 0);
                if (isProtected(protectedPositions, pos)) {
                    continue;
                }

                boolean line = Math.abs(u) == 11 || Math.abs(v) == 6 || u == 0 || Math.abs(v) == 4;
                PlacementUtils.setTrackedBlock(world, pos, line ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.GREEN_CONCRETE.getDefaultState(), originalBlocks);
            }
        }

        for (int v = -6; v <= 6; v++) {
            BlockPos netLeft = PlacementUtils.orientedOffset(courtCenter, side, back, -1, v, 1);
            BlockPos netRight = PlacementUtils.orientedOffset(courtCenter, side, back, 1, v, 1);
            if (!isProtected(protectedPositions, netLeft)) {
                PlacementUtils.setTrackedBlock(world, netLeft, Blocks.IRON_BARS.getDefaultState(), originalBlocks);
            }
            if (!isProtected(protectedPositions, netRight)) {
                PlacementUtils.setTrackedBlock(world, netRight, Blocks.IRON_BARS.getDefaultState(), originalBlocks);
            }
        }

        placeFacilityLights(
                world,
                courtCenter,
                side,
                back,
                new int[][] { { -14, -9 }, { 14, -9 }, { -14, 9 }, { 14, 9 } },
                4,
                originalBlocks,
                protectedPositions
        );

        addProtectedColumnArea(protectedPositions, courtCenter, 15, 8);
    }

    private static void placeBasketballCourt(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos courtCenter = center;
        clearHeadroom(world, courtCenter, 13, 8, originalBlocks, protectedPositions);

        for (int u = -9; u <= 9; u++) {
            for (int v = -5; v <= 5; v++) {
                BlockPos pos = PlacementUtils.orientedOffset(courtCenter, side, back, u, v, 0);
                if (isProtected(protectedPositions, pos)) {
                    continue;
                }

                boolean line = Math.abs(u) == 9 || Math.abs(v) == 5 || u == 0 || (Math.abs(u) == 6 && Math.abs(v) <= 2);
                PlacementUtils.setTrackedBlock(world, pos, line ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.ORANGE_CONCRETE.getDefaultState(), originalBlocks);
            }
        }

        int[][] hoopOffsets = {
                {-8, 0},
                {8, 0}
        };
        for (int[] hoop : hoopOffsets) {
            BlockPos base = PlacementUtils.orientedOffset(courtCenter, side, back, hoop[0], hoop[1], 0);
            for (int y = 1; y <= 4; y++) {
                placeInteriorBlock(world, base.up(y), Blocks.IRON_BARS.getDefaultState(), originalBlocks, protectedPositions);
            }
            placeInteriorBlock(world, base.up(5), Blocks.WHITE_CONCRETE.getDefaultState(), originalBlocks, protectedPositions);
            Direction rimDirection = hoop[0] < 0 ? Direction.EAST : Direction.WEST;
            placeInteriorBlock(
                    world,
                    base.up(4).offset(rimDirection),
                    Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.DOWN),
                    originalBlocks,
                    protectedPositions
            );
        }

        placeFacilityLights(
                world,
                courtCenter,
                side,
                back,
                new int[][] { { -12, -8 }, { 12, -8 }, { -12, 8 }, { 12, 8 } },
                4,
                originalBlocks,
                protectedPositions
        );

        addProtectedColumnArea(protectedPositions, courtCenter, 13, 8);
    }

    private static void placeBathhouse(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos bathCenter = center;
        clearHeadroom(world, bathCenter, 13, 9, originalBlocks, protectedPositions);

        for (int u = -9; u <= 9; u++) {
            for (int v = -6; v <= 6; v++) {
                BlockPos floor = PlacementUtils.orientedOffset(bathCenter, side, back, u, v, 0);
                if (!isProtected(protectedPositions, floor)) {
                    PlacementUtils.setTrackedBlock(world, floor, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                }

                boolean wall = Math.abs(u) == 9 || Math.abs(v) == 6;
                if (wall) {
                    for (int y = 1; y <= 4; y++) {
                        BlockPos wallPos = floor.up(y);
                        if (!isProtected(protectedPositions, wallPos)) {
                            PlacementUtils.setTrackedBlock(world, wallPos, Blocks.QUARTZ_BRICKS.getDefaultState(), originalBlocks);
                        }
                    }
                }

                if (!wall) {
                    BlockPos roof = floor.up(5);
                    if (!isProtected(protectedPositions, roof)) {
                        PlacementUtils.setTrackedBlock(world, roof, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState(), originalBlocks);
                    }
                }
            }
        }

        for (int u = -1; u <= 1; u++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos doorPos = PlacementUtils.orientedOffset(bathCenter, side, back, u, -6, y);
                if (!isProtected(protectedPositions, doorPos)) {
                    PlacementUtils.setTrackedBlock(world, doorPos, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }

        // Toilet row.
        for (int i = -6; i <= 6; i += 4) {
            BlockPos toilet = PlacementUtils.orientedOffset(bathCenter, side, back, i, -3, 1);
            placeInteriorBlock(world, toilet, Blocks.QUARTZ_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH), originalBlocks, protectedPositions);
            placeInteriorBlock(world, toilet.up(), Blocks.OAK_TRAPDOOR.getDefaultState(), originalBlocks, protectedPositions);
        }

        // Sink row.
        for (int i = -6; i <= 6; i += 4) {
            BlockPos sink = PlacementUtils.orientedOffset(bathCenter, side, back, i, 0, 1);
            placeInteriorBlock(world, sink, Blocks.CAULDRON.getDefaultState(), originalBlocks, protectedPositions);
            placeInteriorBlock(world, sink.up(), Blocks.IRON_TRAPDOOR.getDefaultState(), originalBlocks, protectedPositions);
        }

        // Shower bays.
        for (int i = -6; i <= 6; i += 4) {
            BlockPos showerBase = PlacementUtils.orientedOffset(bathCenter, side, back, i, 3, 1);
            placeInteriorBlock(world, showerBase, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks, protectedPositions);
            placeInteriorBlock(world, showerBase.up(3), Blocks.WATER.getDefaultState(), originalBlocks, protectedPositions);
            placeInteriorBlock(world, showerBase.up(2), Blocks.IRON_BARS.getDefaultState(), originalBlocks, protectedPositions);
        }

        addProtectedColumnArea(protectedPositions, bathCenter, 13, 9);
    }

    private static void placeInteriorBlock(
            ServerWorld world,
            BlockPos pos,
            BlockState state,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        if (isProtected(protectedPositions, pos)) {
            return;
        }
        PlacementUtils.setTrackedBlock(world, pos, state, originalBlocks);
    }

    private static void placeFacilityLights(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            int[][] offsets,
            int postHeight,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int[] offset : offsets) {
            BlockPos lightSeed = PlacementUtils.orientedOffset(center, side, back, offset[0], offset[1], 0);
            BlockPos lightGround = ensureLandIslandSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, lightSeed.getX(), lightSeed.getZ()),
                    1,
                    originalBlocks,
                    protectedPositions
            );
            placeLanternPost(world, lightGround, postHeight, originalBlocks);
            addProtectedColumnArea(protectedPositions, lightGround, 1, postHeight + 3);
        }
    }




    private static boolean hasNearbyCampSiteMarker(ServerWorld world, BlockPos center, int searchRadius) {
        for (int dx = -searchRadius; dx <= searchRadius; dx += CAMP_SITE_SCAN_STEP) {
            for (int dz = -searchRadius; dz <= searchRadius; dz += CAMP_SITE_SCAN_STEP) {
                BlockPos sample = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                if (world.getBlockState(sample).isOf(CAMP_SITE_MARKER_BLOCK.getBlock())
                        || world.getBlockState(sample.down()).isOf(CAMP_SITE_MARKER_BLOCK.getBlock())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSuitableCampSite(ServerWorld world, BlockPos center) {
        int safeColumns = 0;
        int totalColumns = 0;

        for (int dx = -CAMP_SITE_RADIUS; dx <= CAMP_SITE_RADIUS; dx += CAMP_SITE_SCAN_STEP) {
            for (int dz = -CAMP_SITE_RADIUS; dz <= CAMP_SITE_RADIUS; dz += CAMP_SITE_SCAN_STEP) {
                if ((dx * dx) + (dz * dz) > (CAMP_SITE_RADIUS * CAMP_SITE_RADIUS)) {
                    continue;
                }

                totalColumns++;
                BlockPos sample = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                if (Math.abs(sample.getY() - center.getY()) > CAMP_SITE_MAX_Y_DELTA) {
                    continue;
                }
                if (isUnsafeSurface(world, sample)) {
                    continue;
                }
                safeColumns++;
            }
        }

        if (totalColumns <= 0) {
            return false;
        }
        int safePercent = (safeColumns * 100) / totalColumns;
        return safePercent >= CAMP_SITE_MIN_SAFE_PERCENT;
    }


    private static void placeTeeHoleBanner(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketSurface,
            int holeNumber,
            int par,
            int distanceFeet,
            boolean signatureHole,
            String signatureName,
            String routeNote,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        int[] forward = teeForwardUnit(teeCenter, basketSurface);
        int[] left = new int[] { -forward[1], forward[0] };
        int[] right = new int[] { -left[0], -left[1] };

        BlockPos signGround = teeCenter.add(forward[0] + left[0], 0, forward[1] + left[1]);
        BlockPos bannerGround = teeCenter.add(forward[0] + right[0], 0, forward[1] + right[1]);

        clearHeadroom(world, bannerGround, 1, 4, originalBlocks, null);
        PlacementUtils.setTrackedBlock(world, bannerGround.up(1), Blocks.OAK_FENCE.getDefaultState(), originalBlocks);
        BlockPos bannerPos = bannerGround.up(2);
        PlacementUtils.setTrackedBlock(world, bannerPos, Blocks.WHITE_BANNER.getDefaultState(), originalBlocks);
        String hazardNote = teeHazardNote(world, teeCenter, basketSurface);
        String noteToShow = signatureName.isEmpty()
                ? (routeNote.isEmpty() ? hazardNote : routeNote)
                : "\u2605 " + signatureName;
        SignTextGenerator.placeTeeHoleSign(
            world,
            signGround,
            -forward[0],
            -forward[1],
            holeNumber,
            par,
            distanceFeet,
            signatureHole,
            noteToShow,
            originalBlocks
        );
    }

    private static int[] teeForwardUnit(BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, Integer.compare(dz, 0) };
    }


    private static void placeSignatureBasketAccents(
            ServerWorld world,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int radius = SIGNATURE_RING_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = (dx * dx) + (dz * dz);
                if (distSq < ((radius - 1) * (radius - 1)) || distSq > (radius * radius + 1)) {
                    continue;
                }

                BlockPos ringPos = basketSurface.add(dx, 0, dz);
                if (isProtected(protectedPositions, ringPos)) {
                    continue;
                }
                PlacementUtils.setTrackedBlock(world, ringPos, Blocks.YELLOW_CONCRETE.getDefaultState(), originalBlocks);
                addProtectedColumnArea(protectedPositions, ringPos, 0, 3);
            }
        }
    }

    private static String teeHazardNote(ServerWorld world, BlockPos teeCenter, BlockPos basketSurface) {
        int dx = basketSurface.getX() - teeCenter.getX();
        int dz = basketSurface.getZ() - teeCenter.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        int[] forward = teeForwardUnit(teeCenter, basketSurface);
        int[] left = new int[] { -forward[1], forward[0] };
        int[] right = new int[] { -left[0], -left[1] };

        int waterColumns = 0;
        int maxWaterRun = 0;
        int currentWaterRun = 0;
        int obEdgeColumns = 0;
        int mandoGateColumns = 0;
        int mandoScanLimit = Math.max(8, (int) (steps * 0.45f));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int sampleX = (int) Math.round(teeCenter.getX() + (dx * t));
            int sampleZ = (int) Math.round(teeCenter.getZ() + (dz * t));

            if (isWaterCrossingColumn(world, sampleX, sampleZ)) {
                waterColumns++;
                currentWaterRun++;
                maxWaterRun = Math.max(maxWaterRun, currentWaterRun);
            } else {
                currentWaterRun = 0;
            }

            int pathY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
            int leftY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX + (left[0] * 3), sampleZ + (left[1] * 3)) - 1;
            int rightY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX + (right[0] * 3), sampleZ + (right[1] * 3)) - 1;
            if (Math.abs(pathY - leftY) >= 6 || Math.abs(pathY - rightY) >= 6) {
                obEdgeColumns++;
            }

            if (i <= mandoScanLimit && isMandoGapColumn(world, sampleX, sampleZ, pathY, left, right)) {
                mandoGateColumns++;
            }
        }

        if (maxWaterRun >= 6 || waterColumns >= 10) {
            return "Haz: OB";
        }

        if (mandoGateColumns >= Math.max(3, mandoScanLimit / 5)) {
            return "Haz: Mando";
        }

        if (obEdgeColumns >= Math.max(4, steps / 6)) {
            return "Haz: OB";
        }
        return "";
    }

    private static boolean isMandoGapColumn(
            ServerWorld world,
            int x,
            int z,
            int pathY,
            int[] left,
            int[] right
    ) {
        int eyeY = pathY + 2;
        int leftX = x + (left[0] * 2);
        int leftZ = z + (left[1] * 2);
        int rightX = x + (right[0] * 2);
        int rightZ = z + (right[1] * 2);

        boolean leftBlocked = false;
        boolean rightBlocked = false;
        for (int y = eyeY; y <= eyeY + 2; y++) {
            BlockState leftState = world.getBlockState(new BlockPos(leftX, y, leftZ));
            BlockState rightState = world.getBlockState(new BlockPos(rightX, y, rightZ));
            if (isTunnelObstacle(leftState)) {
                leftBlocked = true;
            }
            if (isTunnelObstacle(rightState)) {
                rightBlocked = true;
            }
        }

        boolean centerClear = true;
        for (int y = eyeY; y <= eyeY + 2; y++) {
            BlockState center = world.getBlockState(new BlockPos(x, y, z));
            if (!center.isAir() && center.getFluidState().isEmpty()) {
                centerClear = false;
                break;
            }
        }

        return centerClear && leftBlocked && rightBlocked;
    }

    private static boolean isTunnelObstacle(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        return isTallVegetationObstacle(state);
    }


    private static int tunedPathRadius(ServerWorld world, BlockPos pos, int baseRadius) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = PlacementUtils.biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Math.max(1, baseRadius - 1);
        }
        if (PlacementUtils.isBiome(biomeId, "desert", "badlands", "eroded_badlands", "wooded_badlands")) {
            return Math.max(1, baseRadius + 1);
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Math.max(1, baseRadius + 1);
        }
        if (PlacementUtils.isBiome(biomeId, "savanna", "windswept_savanna", "plains", "sunflower_plains")) {
            return Math.max(1, baseRadius + 1);
        }

        return baseRadius;
    }

    private static BlockState selectPathMaterial(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        String biomeId = PlacementUtils.biomeId(biome);

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return Blocks.GRAVEL.getDefaultState();
        }
        if (PlacementUtils.isBiome(biomeId, "desert")) {
            return Blocks.SANDSTONE.getDefaultState();
        }
        if (PlacementUtils.isBiome(biomeId, "badlands", "eroded_badlands", "wooded_badlands")) {
            return Blocks.RED_SANDSTONE.getDefaultState();
        }
        if (PlacementUtils.isBiome(
                biomeId,
                "snowy_plains",
                "snowy_taiga",
                "snowy_slopes",
                "ice_spikes",
                "frozen_river",
                "frozen_peaks",
                "jagged_peaks"
        )) {
            return Blocks.PACKED_ICE.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_NETHER)) {
            return Blocks.BLACKSTONE.getDefaultState();
        }
        if (biome.isIn(BiomeTags.IS_FOREST) || biome.isIn(BiomeTags.IS_JUNGLE)) {
            return Blocks.COARSE_DIRT.getDefaultState();
        }

        return Blocks.DIRT_PATH.getDefaultState();
    }



    /**
     * Ensures the given position is on safe, walkable land.
     * If the position is underwater or in fluid, builds a grass island up to sea level.
     * Returns the BlockPos of the actual safe surface (may be higher than the input center).
     */
    private static BlockPos ensureLandIslandSurface(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos groundedCenter = SurfaceResolver.resolveSurfacePos(world, center.getX(), center.getZ());
        if (!isUnsafeSurface(world, groundedCenter)) {
            return groundedCenter;
        }

        // Build the island from seabed level so we never create floating canopy dirt.
        int seabedY = Math.max(
                world.getBottomY() + 1,
                world.getTopY(Heightmap.Type.OCEAN_FLOOR, center.getX(), center.getZ()) - 1
        );
        int islandY = Math.max(seabedY, world.getSeaLevel()) + 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int bx = center.getX() + dx;
                int bz = center.getZ() + dz;
                int columnSeabedY = Math.max(
                        world.getBottomY() + 1,
                        world.getTopY(Heightmap.Type.OCEAN_FLOOR, bx, bz) - 1
                );

                // Fill the column upward from the seabed to just below the island surface with dirt.
                for (int y = columnSeabedY; y < islandY; y++) {
                    BlockPos fillPos = new BlockPos(bx, y, bz);
                    if (isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }
                // Grass on top.
                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    PlacementUtils.setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

    private static BlockPos ensureWaterLandingSurface(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int seaLevel = world.getSeaLevel();
        int islandY = Math.max(
                world.getBottomY() + 2,
                Math.min(seaLevel, center.getY())
        );

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 2)) {
                    continue;
                }

                int bx = center.getX() + dx;
                int bz = center.getZ() + dz;
                int columnSeabedY = Math.max(
                        world.getBottomY() + 1,
                        world.getTopY(Heightmap.Type.OCEAN_FLOOR, bx, bz) - 1
                );

                for (int y = columnSeabedY; y < islandY; y++) {
                    BlockPos fillPos = new BlockPos(bx, y, bz);
                    if (isProtected(protectedPositions, fillPos)) {
                        continue;
                    }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }

                BlockPos topPos = new BlockPos(bx, islandY, bz);
                if (isProtected(protectedPositions, topPos)) {
                    continue;
                }
                BlockState topState = world.getBlockState(topPos);
                if (isFillReplaceable(topState)) {
                    PlacementUtils.setTrackedBlock(world, topPos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
            }
        }

        return new BlockPos(center.getX(), islandY, center.getZ());
    }

        
    /**
     * Creates an elongated island-style safe fairway landing zone at the given anchor point.
     * This is used at alternate anchors on holes with long water carries to give players a
     * reliable lay-up target that is 7 blocks wide and at least 20 blocks long.
     *
     * The fairway has sand edges with grass interior and extends towards the basket.
     */
    private static void createSafeFairwayLandingZone(
            ServerWorld world,
            BlockPos anchor,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int halfWidth = SAFE_FAIRWAY_HALF_WIDTH;
        int minLength = SAFE_FAIRWAY_MIN_LENGTH;
        int sandThickness = 1;

        int dx = basketSurface.getX() - anchor.getX();
        int dz = basketSurface.getZ() - anchor.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 1) { return; }

        double dirX = dx / distance;
        double dirZ = dz / distance;
        double perpX = -dirZ;
        double perpZ = dirX;

        // Leave a clear margin before the basket so the landing zone never overwrites basket blocks.
        int basketMargin = halfWidth + 2;
        int actualLength = (int) Math.min(minLength, Math.max(0, distance - basketMargin));
        int surfaceY = SurfaceResolver.resolveSurfacePos(world, anchor.getX(), anchor.getZ()).getY();
        int platformY = Math.max(surfaceY, world.getSeaLevel());

        // Protect the basket column so fill never clobbers the hopper/bars/lantern placed later.
        addProtectedColumnArea(protectedPositions, basketSurface, halfWidth + 1, 6);

        for (int step = -halfWidth; step <= actualLength + halfWidth; step++) {
            int centerX = (int) Math.round(anchor.getX() + dirX * step);
            int centerZ = (int) Math.round(anchor.getZ() + dirZ * step);

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx = (int) Math.round(centerX + perpX * w);
                int wz = (int) Math.round(centerZ + perpZ * w);

                boolean isEdge = (Math.abs(w) >= halfWidth - sandThickness) || (step < 0) || (step > actualLength);

                int seabedY = Math.max(world.getBottomY() + 1, world.getTopY(Heightmap.Type.OCEAN_FLOOR, wx, wz) - 1);

                for (int y = seabedY; y < platformY; y++) {
                    BlockPos fillPos = new BlockPos(wx, y, wz);
                    if (isProtected(protectedPositions, fillPos)) { continue; }
                    BlockState fillState = world.getBlockState(fillPos);
                    if (isFillReplaceable(fillState)) {
                        PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                    }
                }

                BlockPos surfacePos = new BlockPos(wx, platformY, wz);
                if (isProtected(protectedPositions, surfacePos)) { continue; }
                BlockState surfaceState = world.getBlockState(surfacePos);
                if (isFillReplaceable(surfaceState)) {
                    BlockState newSurface = isEdge ? Blocks.SAND.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
                    PlacementUtils.setTrackedBlock(world, surfacePos, newSurface, originalBlocks);
                }
            }
        }

        BlockPos fairwayCenter = new BlockPos(
                (int) Math.round(anchor.getX() + dirX * (actualLength / 2.0)),
                platformY,
                (int) Math.round(anchor.getZ() + dirZ * (actualLength / 2.0)));
        int clearRadius = halfWidth + 2;
        clearHeadroom(world, fairwayCenter, clearRadius, 8, originalBlocks, protectedPositions);
        addProtectedColumnArea(protectedPositions, fairwayCenter, clearRadius, 8);
    }

        private static BlockPos expandBasketGreenIfWaterNearby(
            ServerWorld world,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        return ensureBasketGreenLandingZone(
            world,
            finishOrigin,
            finishOrigin,
            basketSurface,
            fairwayWidth,
            originalBlocks,
            protectedPositions
        );
    }

    private static int resolveFinishGreenRadius(int finishHazardColumns, int fairwayWidth) {
        int hazardBonus = Math.min(FINISH_APPROACH_MAX_EXTRA_RADIUS, finishHazardColumns / 20);
        int widthBonus = fairwayWidth >= 5 ? 1 : 0;
        return Math.max(
                WATER_ADJACENT_BASKET_GREEN_RADIUS,
                BASKET_ISLAND_RADIUS + 2 + hazardBonus + widthBonus
        );
    }

    private static void shapePlayableFinishApproach(
            ServerWorld world,
            BlockPos finishOrigin,
            BlockPos basketSurface,
            int fairwayWidth,
            int greenRadius,
            int finishHazardColumns,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basketSurface.getX() - finishOrigin.getX();
        int dz = basketSurface.getZ() - finishOrigin.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return;
        }

        int startStep = Math.max(0, steps - FINISH_APPROACH_SCAN_DISTANCE);
        int approachRadius = Math.max(
                FINISH_APPROACH_BASE_RADIUS,
                Math.min(
                        greenRadius,
                        (fairwayWidth / 2) + FINISH_APPROACH_BASE_RADIUS + Math.min(FINISH_APPROACH_MAX_EXTRA_RADIUS, finishHazardColumns / 24)
                )
        );

        for (int i = startStep; i < steps; i += FINISH_APPROACH_SAMPLE_INTERVAL) {
            double t = i / (double) steps;
            int x = (int) Math.round(finishOrigin.getX() + (dx * t));
            int z = (int) Math.round(finishOrigin.getZ() + (dz * t));
            int distanceToBasket = steps - i;
            int radius = Math.max(
                    FINISH_APPROACH_BASE_RADIUS,
                    approachRadius - Math.max(0, distanceToBasket - 12) / 10
            );

                boolean reinforceFinalWindow = distanceToBasket <= 18;
                if (!reinforceFinalWindow
                    && !isWaterCrossingColumn(world, x, z)
                    && hasAnyWalkableLandingNearby(world, x, z, Math.max(3, radius - 1))) {
                continue;
            }

            BlockPos apronCenter = ensureWaterLandingSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, x, z),
                    radius,
                    originalBlocks,
                    protectedPositions
            );
            clearHeadroom(world, apronCenter, radius, 5, originalBlocks, protectedPositions);
            addProtectedColumnArea(protectedPositions, apronCenter, Math.max(2, radius - 1), 5);
        }
    }

    private static int countFinishHazardColumns(ServerWorld world, BlockPos finishOrigin, BlockPos basketSurface) {
        int dx = basketSurface.getX() - finishOrigin.getX();
        int dz = basketSurface.getZ() - finishOrigin.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps < 1) {
            return 0;
        }

        int startStep = Math.max(0, steps - FINISH_APPROACH_SCAN_DISTANCE);
        double length = Math.max(1.0d, Math.sqrt((dx * (double) dx) + (dz * (double) dz)));
        double sideX = -dz / length;
        double sideZ = dx / length;
        int waterColumns = 0;

        for (int i = startStep; i <= steps; i += 2) {
            double t = i / (double) steps;
            double centerX = finishOrigin.getX() + (dx * t);
            double centerZ = finishOrigin.getZ() + (dz * t);

            for (int offset = -FINISH_HAZARD_SCAN_HALF_WIDTH; offset <= FINISH_HAZARD_SCAN_HALF_WIDTH; offset += 2) {
                int sampleX = (int) Math.round(centerX + (sideX * offset));
                int sampleZ = (int) Math.round(centerZ + (sideZ * offset));
                if (isWaterCrossingColumn(world, sampleX, sampleZ)) {
                    waterColumns++;
                }
            }
        }

        return waterColumns;
    }

    private static int countSafeLandingColumns(ServerWorld world, BlockPos center, int radius) {
        int safeColumns = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = center.getX() + dx;
                int sampleZ = center.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (isWalkableGround(world, sample)) {
                    safeColumns++;
                }
            }
        }
        return safeColumns;
    }

    static boolean isUnsafeSurface(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return true;
        }
        if (state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.WATER)
                || state.isOf(Blocks.KELP)
                || state.isOf(Blocks.SEAGRASS)
                || state.isOf(Blocks.TALL_SEAGRASS)) {
            return true;
        }
        if (state.getBlock() instanceof PlantBlock || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        // Solid ground but submerged — the block above is fluid, so standing here would be underwater.
        return !world.getBlockState(pos.up()).getFluidState().isEmpty();
    }

    static boolean isWalkableGround(ServerWorld world, BlockPos pos) {
        BlockState ground = world.getBlockState(pos);
        if (isUnsafeSurface(world, pos)) {
            return false;
        }
        if (!ground.isSolidBlock(world, pos)) {
            return false;
        }

        BlockState above = world.getBlockState(pos.up());
        BlockState above2 = world.getBlockState(pos.up(2));
        return SurfaceResolver.isOpenHeadspace(above) && SurfaceResolver.isOpenHeadspace(above2);
    }

    static BlockPos relocateTeeSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (SurfaceResolver.isPlayableTeeSurface(world, teeSurface)) {
            return teeSurface;
        }

        BlockPos best = teeSurface;
        int bestScore = Integer.MAX_VALUE;
        for (int searchRadius : new int[] { TEE_RELOCATION_RADIUS, TEE_RELOCATION_RADIUS * 2 }) {
            int step = searchRadius <= TEE_RELOCATION_RADIUS ? 2 : 4;
            for (int dx = -searchRadius; dx <= searchRadius; dx += step) {
                for (int dz = -searchRadius; dz <= searchRadius; dz += step) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq > (searchRadius * searchRadius)) {
                        continue;
                    }

                    BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                            world,
                            SurfaceResolver.resolveSurfacePos(world, teeSurface.getX() + dx, teeSurface.getZ() + dz)
                    );
                    if (!SurfaceResolver.isPlayableTeeSurface(world, candidate)) {
                        continue;
                    }

                    int score = distSq;
                    score += Math.max(0, Math.abs(candidate.getY() - teeSurface.getY()) - 1) * 10;
                    score += Math.abs(candidate.getX() - basketSurface.getX()) + Math.abs(candidate.getZ() - basketSurface.getZ());
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }

            if (bestScore != Integer.MAX_VALUE) {
                return best;
            }
        }

        return best;
    }

    static BlockPos findAlternateFairwayAnchor(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface, boolean forceAlternateRoute) {
        boolean routeDiag = isAltRouteDiagEnabled();
        int directCarryGap = computeLongestWaterCarryGap(world, teeSurface, basketSurface);
        if (routeDiag) {
            McdgMod.LOGGER.info(
                    "AltRouteDiag start tee=({}, {}, {}) basket=({}, {}, {}) directCarryGap={} threshold={}",
                    teeSurface.getX(), teeSurface.getY(), teeSurface.getZ(),
                    basketSurface.getX(), basketSurface.getY(), basketSurface.getZ(),
                    directCarryGap,
                    TEE_MAX_DIRECT_CARRY_GAP
            );
        }
        if (!forceAlternateRoute && directCarryGap <= ALT_FAIRWAY_TARGET_ROUTE_GAP) {
            if (routeDiag) {
                McdgMod.LOGGER.info("AltRouteDiag skipped: direct carry does not exceed threshold.");
            }
            return null;
        }

        AlternateAnchorSearchResult strictResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            ALT_FAIRWAY_FIRST_LEG_MAX_GAP,
            ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("strict", strictResult);
        }
        if (strictResult.anchor() != null) {
            return strictResult.anchor();
        }

        AlternateAnchorSearchResult fallbackResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            ALT_FAIRWAY_FIRST_LEG_MAX_GAP_FALLBACK,
            ALT_FAIRWAY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("fallback", fallbackResult);
        }
        if (fallbackResult.anchor() != null) {
            return fallbackResult.anchor();
        }

        AlternateAnchorSearchResult emergencyResult = searchAlternateFairwayAnchor(
            world,
            teeSurface,
            basketSurface,
            TEE_MAX_DIRECT_CARRY_GAP,
            ALT_FAIRWAY_EMERGENCY_ANCHOR_SEARCH_RADIUS,
            ALT_FAIRWAY_EMERGENCY_MAX_FIRST_LEG
        );
        if (routeDiag) {
            logAltRouteDiagResult("emergency", emergencyResult);
        }
        return emergencyResult.anchor();
    }

    private static AlternateAnchorSearchResult searchAlternateFairwayAnchor(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            int firstLegGapLimit,
            int anchorSearchRadius,
            int maxFirstLeg
    ) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        int candidatesChecked = 0;
        int rejectedUnwalkable = 0;
        int rejectedFirstLeg = 0;
        int rejectedNoAdvance = 0;
        int rejectedFirstGap = 0;
        int viableCandidates = 0;
        int bestFirstGap = -1;
        int bestSecondGap = -1;

        for (int dx = -anchorSearchRadius; dx <= anchorSearchRadius; dx += 2) {
            for (int dz = -anchorSearchRadius; dz <= anchorSearchRadius; dz += 2) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (anchorSearchRadius * anchorSearchRadius)) {
                    continue;
                }
                candidatesChecked++;

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, teeSurface.getX() + dx, teeSurface.getZ() + dz)
                );
                if (!isWalkableGround(world, candidate) || isLikelyPitSurface(world, candidate)) {
                    rejectedUnwalkable++;
                    continue;
                }

                int firstLeg = Math.max(
                        Math.abs(candidate.getX() - teeSurface.getX()),
                        Math.abs(candidate.getZ() - teeSurface.getZ())
                );
                if (firstLeg < ALT_FAIRWAY_MIN_ADVANCE || firstLeg > maxFirstLeg) {
                    rejectedFirstLeg++;
                    continue;
                }

                int distTeeToBasket = Math.abs(basketSurface.getX() - teeSurface.getX()) + Math.abs(basketSurface.getZ() - teeSurface.getZ());
                int distAnchorToBasket = Math.abs(basketSurface.getX() - candidate.getX()) + Math.abs(basketSurface.getZ() - candidate.getZ());
                if (distAnchorToBasket > (distTeeToBasket + ALT_FAIRWAY_MIN_ADVANCE)) {
                    rejectedNoAdvance++;
                    continue;
                }

                int firstGap = computeLongestWaterCarryGap(world, teeSurface, candidate);
                if (firstGap > firstLegGapLimit) {
                    rejectedFirstGap++;
                    continue;
                }

                int secondGap = computeLongestWaterCarryGap(world, candidate, basketSurface);
                if (secondGap > ALT_FAIRWAY_TARGET_ROUTE_GAP) {
                    continue;
                }
                viableCandidates++;

                int score = distSq;
                score += firstGap * 600;
                score += secondGap * 40;
                score += Math.max(0, Math.abs(candidate.getY() - teeSurface.getY()) - 2) * 20;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestFirstGap = firstGap;
                    bestSecondGap = secondGap;
                }
            }
        }

        return new AlternateAnchorSearchResult(
                best,
                candidatesChecked,
                viableCandidates,
                rejectedUnwalkable,
                rejectedFirstLeg,
                rejectedNoAdvance,
                rejectedFirstGap,
                bestScore,
                bestFirstGap,
                bestSecondGap
        );
    }

    static void logAltRouteDiagResult(String passName, AlternateAnchorSearchResult result) {
        BlockPos anchor = result.anchor();
        McdgMod.LOGGER.info(
                "AltRouteDiag {} anchorFound={} checked={} viable={} rejectUnwalkable={} rejectFirstLeg={} rejectNoAdvance={} rejectFirstGap={} bestScore={} bestFirstGap={} bestSecondGap={} anchor=({}, {}, {})",
                passName,
                anchor != null,
                result.candidatesChecked(),
                result.viableCandidates(),
                result.rejectedUnwalkable(),
                result.rejectedFirstLeg(),
                result.rejectedNoAdvance(),
                result.rejectedFirstGap(),
                result.bestScore(),
                result.bestFirstGap(),
                result.bestSecondGap(),
                anchor == null ? 0 : anchor.getX(),
                anchor == null ? 0 : anchor.getY(),
                anchor == null ? 0 : anchor.getZ()
        );
    }

    private record AlternateAnchorSearchResult(
            BlockPos anchor,
            int candidatesChecked,
            int viableCandidates,
            int rejectedUnwalkable,
            int rejectedFirstLeg,
            int rejectedNoAdvance,
            int rejectedFirstGap,
            int bestScore,
            int bestFirstGap,
            int bestSecondGap
    ) {
    }

    static boolean isAltRouteDiagEnabled() {
        String value = System.getenv(ALT_ROUTE_DIAG_ENV);
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim();
        return normalized.equalsIgnoreCase("1")
                || normalized.equalsIgnoreCase("true")
                || normalized.equalsIgnoreCase("yes")
                || normalized.equalsIgnoreCase("on");
    }

    static String alternateRouteNote(BlockPos teeSurface, BlockPos basketSurface, BlockPos anchor) {
        int dx = basketSurface.getX() - teeSurface.getX();
        int dz = basketSurface.getZ() - teeSurface.getZ();
        int ax = anchor.getX() - teeSurface.getX();
        int az = anchor.getZ() - teeSurface.getZ();
        int cross = (dx * az) - (dz * ax);

        if (Math.abs(cross) < 16) {
            return "Alt fairway";
        }
        return cross > 0 ? "Alt lane: right" : "Alt lane: left";
    }

    static int placedDistanceFeet(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return 0;
        }

        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        int meters = Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        return Math.max(0, Math.round(meters * 3.28084f));
    }

    static BlockPos relocateBasketSurfaceIfNeeded(ServerWorld world, BlockPos teeSurface, BlockPos basketSurface) {
        if (SurfaceResolver.isPlayableBasketSurface(world, basketSurface)) {
            return basketSurface;
        }

        BlockPos best = basketSurface;
        int bestScore = Integer.MAX_VALUE;
        int step = 2;
        int baselineSpan = Math.abs(basketSurface.getX() - teeSurface.getX()) + Math.abs(basketSurface.getZ() - teeSurface.getZ());

        for (int dx = -BASKET_RELOCATION_RADIUS; dx <= BASKET_RELOCATION_RADIUS; dx += step) {
            for (int dz = -BASKET_RELOCATION_RADIUS; dz <= BASKET_RELOCATION_RADIUS; dz += step) {
                int distSq = dx * dx + dz * dz;
                if (distSq > (BASKET_RELOCATION_RADIUS * BASKET_RELOCATION_RADIUS)) {
                    continue;
                }

                BlockPos candidate = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, basketSurface.getX() + dx, basketSurface.getZ() + dz)
                );
                if (!SurfaceResolver.isPlayableBasketSurface(world, candidate)) {
                    continue;
                }

                int span = Math.abs(candidate.getX() - teeSurface.getX()) + Math.abs(candidate.getZ() - teeSurface.getZ());
                int score = distSq;
                score += Math.max(0, Math.abs(candidate.getY() - basketSurface.getY()) - 1) * 8;
                score += Math.abs(span - baselineSpan) * 3;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        return best;
    }

    static BlockPos tryRecoverEnclosedBasketSurface(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, basketSurface.getX(), basketSurface.getZ()) - 1;
        int centerDepth = centerSurfaceY - basketSurface.getY();
        if (centerDepth < BASKET_ENCLOSURE_RECOVERY_MIN_DEPTH || centerDepth > BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH) {
            return null;
        }

        int[] lateralOffsets = {
                0,
                BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP,
                -BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP,
                BASKET_ENCLOSURE_RECOVERY_LATERAL_STEP * 2
        };

        int maxAttempts = Math.min(lateralOffsets.length - 1, BASKET_ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS);
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            if (buildBasketRecoveryCorridor(
                    world,
                    teeSurface,
                    basketSurface,
                    lateralOffsets[attempt],
                    originalBlocks,
                    protectedPositions
            )) {
                return basketSurface;
            }
        }

        return null;
    }

    static boolean buildBasketRecoveryCorridor(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            int lateralOffset,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = teeSurface.getX() - basketSurface.getX();
        int dz = teeSurface.getZ() - basketSurface.getZ();
        int stepsToTee = Math.max(Math.abs(dx), Math.abs(dz));
        if (stepsToTee < 4) {
            return false;
        }

        int maxSteps = Math.min(stepsToTee, Math.max(24, BASKET_ENCLOSURE_RECOVERY_MAX_DEPTH * 3));
        int halfWidth = Math.max(1, BASKET_ENCLOSURE_RECOVERY_WIDTH / 2);
        int sideX = -Integer.compare(dz, 0);
        int sideZ = Integer.compare(dx, 0);
        if (sideX == 0 && sideZ == 0) {
            sideX = 1;
            sideZ = 0;
        }

        int currentY = basketSurface.getY();
        int emergedSteps = 0;

        for (int step = 0; step <= maxSteps; step++) {
            double t = step / (double) stepsToTee;
            int rowX = (int) Math.round(basketSurface.getX() + (dx * t)) + (sideX * lateralOffset);
            int rowZ = (int) Math.round(basketSurface.getZ() + (dz * t)) + (sideZ * lateralOffset);
            int localSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, rowX, rowZ) - 1;

            if (currentY < localSurfaceY) {
                int remainingSteps = Math.max(1, maxSteps - step + 1);
                int remainingRise = localSurfaceY - currentY;
                int climb = 1;
                if (remainingRise > remainingSteps) {
                    climb = 2;
                }
                currentY += Math.min(2, climb);
            }

            if (rowHasLava(world, rowX, rowZ, currentY, halfWidth)) {
                return false;
            }

            carveRecoveryRow(
                    world,
                    rowX,
                    rowZ,
                    currentY,
                    halfWidth,
                    originalBlocks,
                    protectedPositions
            );

            if (currentY >= localSurfaceY) {
                emergedSteps++;
            } else {
                emergedSteps = 0;
            }

            if (emergedSteps >= 4) {
                return true;
            }
        }

        return false;
    }

    static boolean rowHasLava(ServerWorld world, int rowX, int rowZ, int rowY, int halfWidth) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = rowX + dx;
                int z = rowZ + dz;
                if (isLavaColumn(world, x, z, rowY)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isLavaColumn(ServerWorld world, int x, int z, int referenceY) {
        int worldSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos worldSurface = new BlockPos(x, worldSurfaceY, z);
        BlockState surfaceState = world.getBlockState(worldSurface);
        if (surfaceState.isOf(Blocks.LAVA) || surfaceState.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA)) {
            return true;
        }

        for (int y = Math.max(world.getBottomY() + 1, referenceY - 2); y <= referenceY + 2; y++) {
            BlockState state = world.getBlockState(new BlockPos(x, y, z));
            if (state.isOf(Blocks.LAVA) || state.getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA)) {
                return true;
            }
        }

        return false;
    }

    static void carveRecoveryRow(
            ServerWorld world,
            int rowX,
            int rowZ,
            int rowY,
            int halfWidth,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int x = rowX + dx;
                int z = rowZ + dz;

                BlockPos center = new BlockPos(x, rowY, z);
                if (isProtected(protectedPositions, center)) {
                    continue;
                }

                if (isWaterCrossingColumn(world, x, z)) {
                    ensureWaterLandingSurface(world, center, 1, originalBlocks, protectedPositions);
                }

                PlacementUtils.setTrackedBlock(world, center, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);

                BlockPos below = center.down();
                if (!world.getBlockState(below).isSolidBlock(world, below) || !world.getBlockState(below).getFluidState().isEmpty()) {
                    PlacementUtils.setTrackedBlock(world, below, Blocks.DIRT.getDefaultState(), originalBlocks);
                }

                int localSurfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                int clearTop = Math.max(rowY + BASKET_ENCLOSURE_RECOVERY_HEADROOM, localSurfaceY + 2);
                for (int y = rowY + 1; y <= clearTop; y++) {
                    BlockPos clearPos = new BlockPos(x, y, z);
                    if (isProtected(protectedPositions, clearPos)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(clearPos);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    PlacementUtils.setTrackedBlock(world, clearPos, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }
    }


    static boolean hasFluidInBasketMarkerColumn(ServerWorld world, BlockPos basketSurface, int height) {
        BlockPos base = basketSurface.up();
        for (int i = 0; i <= Math.max(1, height); i++) {
            if (!world.getFluidState(base.up(i)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean isDeeplyEnclosedBasketSurface(ServerWorld world, BlockPos basketSurface) {
        int centerSurfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, basketSurface.getX(), basketSurface.getZ()) - 1;
        int centerDepth = centerSurfaceY - basketSurface.getY();
        if (centerDepth >= BASKET_ENCLOSURE_CENTER_DEPTH_FAIL) {
            return true;
        }

        if (centerDepth < BASKET_ENCLOSURE_CENTER_DEPTH_CHECK) {
            return false;
        }

        int highWallSamples = 0;
        int totalSamples = 0;
        for (int dx = -BASKET_ENCLOSURE_SCAN_RADIUS; dx <= BASKET_ENCLOSURE_SCAN_RADIUS; dx += 2) {
            for (int dz = -BASKET_ENCLOSURE_SCAN_RADIUS; dz <= BASKET_ENCLOSURE_SCAN_RADIUS; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int distSq = dx * dx + dz * dz;
                if (distSq > (BASKET_ENCLOSURE_SCAN_RADIUS * BASKET_ENCLOSURE_SCAN_RADIUS)) {
                    continue;
                }

                int sampleSurfaceY = world.getTopY(
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        basketSurface.getX() + dx,
                        basketSurface.getZ() + dz
                ) - 1;
                if ((sampleSurfaceY - basketSurface.getY()) >= BASKET_ENCLOSURE_WALL_DEPTH_THRESHOLD) {
                    highWallSamples++;
                }
                totalSamples++;
            }
        }

        return totalSamples > 0
                && highWallSamples >= Math.max(12, (int) Math.ceil(totalSamples * BASKET_ENCLOSURE_HIGH_WALL_RATIO));
    }


    static boolean isDeeplyEnclosedTeeSurface(ServerWorld world, BlockPos teeSurface) {
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, teeSurface.getX(), teeSurface.getZ()) - 1;
        return (surfaceY - teeSurface.getY()) >= TEE_PREFILTER_ENCLOSURE_DEPTH_FAIL;
    }

    static boolean isLikelyPitSurface(ServerWorld world, BlockPos center) {
        int total = 0;
        int count = 0;
        int minNeighborY = Integer.MAX_VALUE;

        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                BlockPos sample = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
                );
                total += sample.getY();
                count++;
                minNeighborY = Math.min(minNeighborY, sample.getY());
            }
        }

        if (count == 0) {
            return false;
        }

        int averageNeighborY = total / count;
        int depthFromAverage = averageNeighborY - center.getY();
        int depthFromMin = minNeighborY - center.getY();
        return depthFromAverage >= TEE_PIT_DEPTH_THRESHOLD && depthFromMin >= 2;
    }

    static boolean hasExcessiveTeeEnclosure(ServerWorld world, BlockPos center) {
        int enclosureScore = 0;

        for (int dx = -TEE_WALL_SCAN_RADIUS; dx <= TEE_WALL_SCAN_RADIUS; dx += 2) {
            for (int dz = -TEE_WALL_SCAN_RADIUS; dz <= TEE_WALL_SCAN_RADIUS; dz += 2) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int distSq = dx * dx + dz * dz;
                if (distSq < 12 || distSq > (TEE_WALL_SCAN_RADIUS * TEE_WALL_SCAN_RADIUS)) {
                    continue;
                }

                BlockPos sample = SurfaceResolver.normalizePlayableSurface(
                        world,
                        SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz)
                );
                int heightDelta = sample.getY() - center.getY();
                if (heightDelta >= 3) {
                    enclosureScore += 2;
                } else if (heightDelta >= 2) {
                    enclosureScore += 1;
                }
            }
        }

        return enclosureScore >= TEE_MAX_ENCLOSURE_SCORE;
    }




    static int localWaterPenalty(ServerWorld world, BlockPos center) {
        int penalty = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos sample = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                if (isUnsafeSurface(world, sample) || isWaterBiome(world, sample)) {
                    penalty += 220;
                }
            }
        }
        return penalty;
    }

    static boolean isWaterBiome(ServerWorld world, BlockPos pos) {
        String id = PlacementUtils.biomeId(world.getBiome(pos));
        return id.contains("ocean") || id.contains("river") || id.contains("beach");
    }

    private static boolean isWaterCrossingColumn(ServerWorld world, int x, int z) {
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

        return isWaterBiome(world, SurfaceResolver.resolveSurfacePos(world, x, z));
    }

    private static boolean isWaterAdjacentArea(ServerWorld world, BlockPos center, int radius, int minWaterColumns) {
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

    private static boolean hasAnyWalkableLandingNearby(ServerWorld world, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > (radius * radius + 1)) {
                    continue;
                }

                int sampleX = x + dx;
                int sampleZ = z + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) - 1;
                BlockPos sample = new BlockPos(sampleX, topY, sampleZ);
                if (isWalkableGround(world, sample)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
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

    private static boolean isFillReplaceable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.getBlock() instanceof PlantBlock;
    }

    static void clearHeadroom(
            ServerWorld world,
            BlockPos center,
            int radius,
            int height,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx * dx) + (dz * dz) > (r * r + 1)) {
                    continue;
                }

                for (int y = 1; y <= h; y++) {
                    BlockPos target = center.add(dx, y, dz);
                    if (isProtected(protectedPositions, target)) {
                        continue;
                    }
                    BlockState state = world.getBlockState(target);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    PlacementUtils.setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }
    }

    private static void clearTeeLaunchLane(
            ServerWorld world,
            BlockPos teeSurface,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basketSurface.getX() - teeSurface.getX();
        int dz = basketSurface.getZ() - teeSurface.getZ();
        int total = Math.max(Math.abs(dx), Math.abs(dz));
        if (total < 1) {
            return;
        }

        int laneLength = Math.min(TEE_LAUNCH_CLEAR_DISTANCE, Math.max(8, total / 3));
        Set<BlockPos> clearedTreeNodes = new HashSet<>();

        for (int step = 1; step <= laneLength; step++) {
            double t = step / (double) total;
            int lineX = (int) Math.round(teeSurface.getX() + (dx * t));
            int lineZ = (int) Math.round(teeSurface.getZ() + (dz * t));

            for (int wx = -TEE_LAUNCH_CLEAR_HALF_WIDTH; wx <= TEE_LAUNCH_CLEAR_HALF_WIDTH; wx++) {
                for (int wz = -TEE_LAUNCH_CLEAR_HALF_WIDTH; wz <= TEE_LAUNCH_CLEAR_HALF_WIDTH; wz++) {
                    int sampleX = lineX + wx;
                    int sampleZ = lineZ + wz;
                    clearFairwayColumnVegetation(
                            world,
                            sampleX,
                            sampleZ,
                            teeSurface.getY(),
                            originalBlocks,
                            null,
                            clearedTreeNodes
                    );

                    for (int y = teeSurface.getY() + 1; y <= teeSurface.getY() + 4; y++) {
                        BlockPos obstructionPos = new BlockPos(sampleX, y, sampleZ);
                        if (isProtected(protectedPositions, obstructionPos)) {
                            continue;
                        }
                        BlockState obstruction = world.getBlockState(obstructionPos);
                        if (isTeeLaunchObstruction(obstruction)) {
                            PlacementUtils.setTrackedBlock(world, obstructionPos, Blocks.AIR.getDefaultState(), originalBlocks);
                        }
                    }
                }
            }
        }
    }

    private static boolean isTeeLaunchObstruction(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        if (isTallVegetationObstacle(state)) {
            return true;
        }
        return state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.MUD)
                || state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.SAND)
                || state.isOf(Blocks.GRAVEL)
                || state.isOf(Blocks.SNOW_BLOCK)
                || state.isOf(Blocks.POWDER_SNOW);
    }

    public static void addProtectedColumnArea(Set<BlockPos> protectedPositions, BlockPos center, int radius, int height) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx * dx) + (dz * dz) > (r * r + 1)) {
                    continue;
                }
                for (int y = 0; y <= h; y++) {
                    protectedPositions.add(center.add(dx, y, dz).toImmutable());
                }
            }
        }
    }

    private static boolean isProtected(Set<BlockPos> protectedPositions, BlockPos pos) {
        return protectedPositions != null && protectedPositions.contains(pos);
    }

    private static boolean isClearable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof PlantBlock) {
            return true;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
            return true;
        }
        return isTallVegetationObstacle(state);
    }

    private static boolean isTallVegetationObstacle(BlockState state) {
        return state.isOf(Blocks.BAMBOO)
                || state.isOf(Blocks.BAMBOO_SAPLING)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.BIG_DRIPLEAF)
                || state.isOf(Blocks.BIG_DRIPLEAF_STEM)
                || state.isOf(Blocks.SMALL_DRIPLEAF)
                || state.isOf(Blocks.MANGROVE_ROOTS)
                || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.isOf(Blocks.NETHER_SPROUTS)
                || state.isOf(Blocks.CRIMSON_ROOTS)
                || state.isOf(Blocks.WARPED_ROOTS)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.CAVE_VINES)
                || state.isOf(Blocks.CAVE_VINES_PLANT)
                || state.isOf(Blocks.WEEPING_VINES)
                || state.isOf(Blocks.WEEPING_VINES_PLANT)
                || state.isOf(Blocks.TWISTING_VINES)
                || state.isOf(Blocks.TWISTING_VINES_PLANT);
    }

}
