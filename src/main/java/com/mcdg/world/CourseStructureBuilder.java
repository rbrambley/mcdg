package com.mcdg.world;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ChallengeBlockPalette;
import com.mcdg.game.ChallengeCourseType;
import com.mcdg.game.PlacedCourseState;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;

public final class CourseStructureBuilder {
    private static final int SIGNATURE_RING_RADIUS = CoursePlacementConfig.Islands.SIGNATURE_RING_RADIUS;
    private static final int CAMP_SITE_SCAN_STEP = CoursePlacementConfig.CampSite.SCAN_STEP;
    private static final int CAMP_SITE_MARKER_SEARCH_RADIUS = CoursePlacementConfig.SearchRadii.CAMP_SITE_MARKER;
    private static final BlockState CAMP_SITE_MARKER_BLOCK = CoursePlacementConfig.CampSite.MARKER_BLOCK;

    private CourseStructureBuilder() {}

    static void placeTeePad(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks, BiomeTheme theme) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                PlacementUtils.setTrackedBlock(world, pos, theme.teePadBase(), originalBlocks);
            }
        }
        PlacementUtils.setTrackedBlock(world, center, theme.teePadCenter(), originalBlocks);
    }

    static void placeBasketMarker(ServerWorld world, BlockPos center, Map<BlockPos, BlockState> originalBlocks, int basketHeight, BiomeTheme theme) {
        BlockState ground = world.getBlockState(center);
        if (!SurfaceResolver.isBasketGroundSafe(ground)) {
            PlacementUtils.setTrackedBlock(world, center, theme.basketGround(), originalBlocks);
        }

        BlockPos base = center.up();
        for (int i = 0; i <= basketHeight + 2; i++) {
            BlockPos markerPos = base.up(i);
            if (!world.getFluidState(markerPos).isEmpty()) {
                PlacementUtils.setTrackedBlock(world, markerPos, Blocks.AIR.getDefaultState(), originalBlocks);
            }
        }
        PlacementUtils.setTrackedBlock(world, base, theme.basketBase(), originalBlocks);

        for (int i = 1; i <= basketHeight + 1; i++) {
            PlacementUtils.setTrackedBlock(world, base.up(i), theme.basketPole(), originalBlocks);
        }

        PlacementUtils.setTrackedBlock(world, base.up(basketHeight + 2), theme.basketLantern(), originalBlocks);
    }

    static void placeLanternPost(ServerWorld world, BlockPos ground, int postHeight, Map<BlockPos, BlockState> originalBlocks, BiomeTheme theme) {
        int height = Math.max(1, postHeight);
        PlacementUtils.clearHeadroom(world, ground, 1, height + 2, originalBlocks, null);
        for (int i = 1; i <= height; i++) {
            PlacementUtils.setTrackedBlock(world, ground.up(i), theme.lanternPost(), originalBlocks);
        }
        PlacementUtils.setTrackedBlock(world, ground.up(height + 1), theme.lanternLight(), originalBlocks);
    }

    public static void placeCourseCentralHub(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketSurface,
            String courseName,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        int[] forward = PlacementUtils.teeForwardUnit(teeCenter, basketSurface);
        int[] back = new int[] { -forward[0], -forward[1] };
        int[] side = new int[] { -forward[1], forward[0] };

        BlockPos hubSeed = teeCenter.add(back[0] * 9, 0, back[1] * 9);
        BlockPos hubSurface = SurfaceResolver.normalizePlayableSurface(
            world,
            SurfaceResolver.findPreferredSurfacePos(world, hubSeed.getX(), hubSeed.getZ(), true, 16)
        );
        PlacementUtils.clearHeadroom(world, hubSurface, 9, 6, originalBlocks, protectedPositions);

        buildCourseCentralDeck(world, hubSurface, side, back, originalBlocks, protectedPositions, theme);
        placeRegistrationDesk(world, hubSurface, side, back, originalBlocks, protectedPositions, theme);
        placeMerchCanopy(world, hubSurface, side, back, originalBlocks, protectedPositions, theme);
        placePracticeBaskets(world, hubSurface, side, back, originalBlocks, protectedPositions, theme);

        PlacementUtils.addProtectedColumnArea(protectedPositions, hubSurface, 9, 7);
    }

    private static void buildCourseCentralDeck(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        for (int v = -3; v <= 8; v++) {
            for (int u = -8; u <= 8; u++) {
                BlockPos pos = PlacementUtils.orientedOffset(hubSurface, side, back, u, v, 0);
                if (PlacementUtils.isProtected(protectedPositions, pos)) {
                    continue;
                }
                boolean rim = Math.abs(u) >= 8 || v <= -3 || v >= 8;
                PlacementUtils.setTrackedBlock(world, pos, rim ? theme.hubDeckRim() : theme.hubDeckCenter(), originalBlocks);
            }
        }

        for (int step = 3; step <= 8; step++) {
            BlockPos walkway = hubSurface.add(-back[0] * step, 0, -back[1] * step);
            if (!PlacementUtils.isProtected(protectedPositions, walkway)) {
                PlacementUtils.setTrackedBlock(world, walkway, theme.hubWalkway(), originalBlocks);
            }
        }
    }

    private static void placeRegistrationDesk(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        BlockPos deskOrigin = PlacementUtils.orientedOffset(hubSurface, side, back, -4, 0, 0);

        for (int u = 0; u <= 4; u++) {
            for (int v = 0; v <= 1; v++) {
                BlockPos top = PlacementUtils.orientedOffset(deskOrigin, side, back, u, v, 1);
                if (!PlacementUtils.isProtected(protectedPositions, top)) {
                    PlacementUtils.setTrackedBlock(world, top, theme.registrationDeskTop(), originalBlocks);
                }
            }
        }

        int[][] legs = {
                {0, 0}, {4, 0}, {0, 1}, {4, 1}
        };
        for (int[] leg : legs) {
            BlockPos legPos = PlacementUtils.orientedOffset(deskOrigin, side, back, leg[0], leg[1], 0);
            if (!PlacementUtils.isProtected(protectedPositions, legPos)) {
                PlacementUtils.setTrackedBlock(world, legPos, theme.registrationDeskLegs(), originalBlocks);
            }
        }

        int[][] terminals = {
                {1, 0}, {2, 0}, {3, 0}
        };
        for (int[] terminal : terminals) {
            BlockPos terminalPos = PlacementUtils.orientedOffset(deskOrigin, side, back, terminal[0], terminal[1], 2);
            if (!PlacementUtils.isProtected(protectedPositions, terminalPos)) {
                PlacementUtils.setTrackedBlock(world, terminalPos, theme.registrationDeskTerminals(), originalBlocks);
            }
        }
    }

    private static void placeMerchCanopy(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        BlockPos canopyCenter = PlacementUtils.orientedOffset(hubSurface, side, back, 4, 2, 0);

        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                BlockPos roof = PlacementUtils.orientedOffset(canopyCenter, side, back, u, v, 4);
                if (!PlacementUtils.isProtected(protectedPositions, roof)) {
                    PlacementUtils.setTrackedBlock(world, roof, theme.merchCanopyRoof(), originalBlocks);
                }
            }
        }

        int[][] posts = {
                {-2, -2}, {2, -2}, {-2, 2}, {2, 2}
        };
        for (int[] post : posts) {
            for (int y = 1; y <= 3; y++) {
                BlockPos postPos = PlacementUtils.orientedOffset(canopyCenter, side, back, post[0], post[1], y);
                if (!PlacementUtils.isProtected(protectedPositions, postPos)) {
                    PlacementUtils.setTrackedBlock(world, postPos, theme.merchCanopyPosts(), originalBlocks);
                }
            }
        }

        for (int u = -1; u <= 1; u++) {
            BlockPos merchTableA = PlacementUtils.orientedOffset(canopyCenter, side, back, u, -1, 1);
            BlockPos merchTableB = PlacementUtils.orientedOffset(canopyCenter, side, back, u, 1, 1);
            if (!PlacementUtils.isProtected(protectedPositions, merchTableA)) {
                PlacementUtils.setTrackedBlock(world, merchTableA, theme.merchCanopyTables(), originalBlocks);
            }
            if (!PlacementUtils.isProtected(protectedPositions, merchTableB)) {
                PlacementUtils.setTrackedBlock(world, merchTableB, theme.merchCanopyTables(), originalBlocks);
            }
        }

        // Populate merch barrels with starter disc supplies
        populateMerchBarrels(world, canopyCenter, side, back, protectedPositions);
    }

    private static void populateMerchBarrels(
            ServerWorld world,
            BlockPos canopyCenter,
            int[] side,
            int[] back,
            Set<BlockPos> protectedPositions
    ) {
        java.util.Random random = new java.util.Random();
        for (int u = -1; u <= 1; u++) {
            for (int v : new int[]{-1, 1}) {
                BlockPos barrelPos = PlacementUtils.orientedOffset(canopyCenter, side, back, u, v, 1);
                if (protectedPositions.contains(barrelPos)) {
                    continue;
                }
                BlockEntity blockEntity = world.getBlockEntity(barrelPos);
                if (!(blockEntity instanceof net.minecraft.block.entity.BarrelBlockEntity barrel)) {
                    continue;
                }
                // Add starter discs
                barrel.setStack(0, new net.minecraft.item.ItemStack(com.mcdg.game.McdgItems.TRAINING_DISC, 1));
                // Add random disc enchantment books
                com.mcdg.game.DiscEnchantment[] pool = com.mcdg.game.DiscEnchantment.values();
                com.mcdg.game.DiscEnchantment enchant = pool[random.nextInt(pool.length)];
                int level = 1 + random.nextInt(2);
                barrel.setStack(1, com.mcdg.game.DiscEnchantedBook.create(enchant, level));
                barrel.markDirty();
            }
        }
    }

    private static void placePracticeBaskets(
            ServerWorld world,
            BlockPos hubSurface,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        BlockPos leftPracticeTarget = PlacementUtils.orientedOffset(hubSurface, side, back, -7, 6, 0);
        BlockPos rightPracticeTarget = PlacementUtils.orientedOffset(hubSurface, side, back, 7, 6, 0);

        BlockPos leftSurface = SurfaceResolver.resolveSurfacePos(world, leftPracticeTarget.getX(), leftPracticeTarget.getZ());
        BlockPos rightSurface = SurfaceResolver.resolveSurfacePos(world, rightPracticeTarget.getX(), rightPracticeTarget.getZ());

        if (SurfaceAdaptationHelper.isUnsafeSurface(world, leftSurface)) {
            leftSurface = PlacementUtils.orientedOffset(hubSurface, side, back, -7, 6, 0);
        }
        if (SurfaceAdaptationHelper.isUnsafeSurface(world, rightSurface)) {
            rightSurface = PlacementUtils.orientedOffset(hubSurface, side, back, 7, 6, 0);
        }

        PlacementUtils.clearHeadroom(world, leftSurface, 1, 6, originalBlocks, protectedPositions);
        PlacementUtils.clearHeadroom(world, rightSurface, 1, 6, originalBlocks, protectedPositions);
        placeBasketMarker(world, leftSurface, originalBlocks, 2, theme);
        placeBasketMarker(world, rightSurface, originalBlocks, 2, theme);
        PlacementUtils.addProtectedColumnArea(protectedPositions, leftSurface, 1, 6);
        PlacementUtils.addProtectedColumnArea(protectedPositions, rightSurface, 1, 6);
    }

    static void placeTeeHoleBanner(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketSurface,
            int holeNumber,
            int par,
            int distanceFeet,
            boolean signatureHole,
            String signatureName,
            String routeNote,
            Map<BlockPos, BlockState> originalBlocks,
            BiomeTheme theme
    ) {
        int[] forward = PlacementUtils.teeForwardUnit(teeCenter, basketSurface);
        int[] left = new int[] { -forward[1], forward[0] };
        int[] right = new int[] { -left[0], -left[1] };

        BlockPos signGround = teeCenter.add(forward[0] + left[0], 0, forward[1] + left[1]);
        BlockPos bannerGround = teeCenter.add(forward[0] + right[0], 0, forward[1] + right[1]);

        PlacementUtils.clearHeadroom(world, bannerGround, 1, 4, originalBlocks, null);
        PlacementUtils.setTrackedBlock(world, bannerGround.up(1), theme.bannerPole(), originalBlocks);
        BlockPos bannerPos = bannerGround.up(2);
        PlacementUtils.setTrackedBlock(world, bannerPos, theme.banner(), originalBlocks);
        String hazardNote = PlacementCleanupHelper.teeHazardNote(world, teeCenter, basketSurface);
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

    /**
     * Replaces the normal course banner and sign at each tee with challenge-themed
     * versions and auto-populated hole text.
     */
    public static void applyChallengeTheme(
            ServerWorld world,
            Course course,
            PlacedCourseState placed,
            ChallengeCourseType type
    ) {
        ChallengeBlockPalette.BlockPalette palette = ChallengeBlockPalette.forType(type);
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>(placed.originalBlocks());

        Block teeMarker = palette.teeMarker();
        Block holeSign = palette.holeSign();

        int successCount = 0;
        int failureCount = 0;

        for (Hole hole : course.holes()) {
            try {
                BlockPos tee = placed.holeTees().get(hole.index());
                BlockPos basket = placed.holeBaskets().get(hole.index());
                if (tee == null || basket == null) {
                    failureCount++;
                    continue;
                }

                if (!world.isChunkLoaded(tee) || !world.isChunkLoaded(basket)) {
                    failureCount++;
                    McdgMod.LOGGER.warn("Skipping challenge theme for hole {} in {}: chunks not loaded",
                            hole.index(), course.name());
                    continue;
                }

                int[] forward = PlacementUtils.teeForwardUnit(tee, basket);
                int fx = forward[0];
                int fz = forward[1];
                int lx = -fz;
                int lz = fx;
                int rx = -lx;
                int rz = -lz;

                BlockPos signGround = tee.add(fx + lx, 0, fz + lz);
                BlockPos bannerGround = tee.add(fx + rx, 0, fz + rz);
                int bannerRotation = SignTextGenerator.standingSignRotationForCardinal(-fx, -fz);

                // Replace the themed banner and pole at the normal banner location
                PlacementUtils.clearHeadroom(world, bannerGround, 0, 3, originalBlocks, null);
                PlacementUtils.setTrackedBlock(world, bannerGround.up(1), defaultStateOf(teeMarker), originalBlocks);
                BlockState bannerState = bannerBlockForColor(palette.bannerColor())
                        .with(Properties.ROTATION, bannerRotation);
                PlacementUtils.setTrackedBlock(world, bannerGround.up(2), bannerState, originalBlocks);

                // Replace the normal sign with a challenge-themed sign and auto text
                PlacementUtils.clearHeadroom(world, signGround, 0, 3, originalBlocks, null);
                BlockState signState = defaultStateOf(holeSign)
                        .with(Properties.ROTATION, bannerRotation);
                PlacementUtils.setTrackedBlock(world, signGround.up(1), signState, originalBlocks);

                if (world.getBlockEntity(signGround.up(1)) instanceof SignBlockEntity sign) {
                    String hazard = PlacementCleanupHelper.teeHazardNote(world, tee, basket);
                    int distance = PlacementUtils.placedDistanceFeet(tee, basket);
                    SignText updated = sign.getFrontText()
                            .withMessage(0, Text.literal(palette.bannerPatternName()))
                            .withMessage(1, Text.literal("Hole " + hole.index() + " Par " + hole.par()))
                            .withMessage(2, Text.literal(distance + " ft"))
                            .withMessage(3, Text.literal(hazard));
                    sign.setText(updated, true);
                    sign.setText(updated, false);
                    sign.markDirty();
                }
                successCount++;
            } catch (Exception ex) {
                failureCount++;
                McdgMod.LOGGER.error("Failed to apply challenge theme to hole {} in course {}: {}",
                    hole.index(), course.name(), ex.getMessage());
            }
        }

        if (failureCount > 0) {
            McdgMod.LOGGER.warn("Applied challenge course theme for type {} to course {} with {} successes and {} failures",
                type, course.name(), successCount, failureCount);
        } else {
            McdgMod.LOGGER.info("Applied challenge course theme for type {} to course {} ({} holes)",
                type, course.name(), successCount);
        }
    }

    private static BlockState bannerBlockForColor(DyeColor color) {
        Block banner = switch (color) {
            case PURPLE -> Blocks.PURPLE_BANNER;
            case RED -> Blocks.RED_BANNER;
            case ORANGE -> Blocks.ORANGE_BANNER;
            case CYAN -> Blocks.CYAN_BANNER;
            case LIME -> Blocks.LIME_BANNER;
            default -> Blocks.WHITE_BANNER;
        };
        return banner.getDefaultState();
    }

    private static BlockState defaultStateOf(Block block) {
        return block.getDefaultState();
    }

    static void placeSignatureBasketAccents(
            ServerWorld world,
            BlockPos basketSurface,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        int radius = SIGNATURE_RING_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = (dx * dx) + (dz * dz);
                if (distSq < ((radius - 1) * (radius - 1)) || distSq > (radius * radius + 1)) {
                    continue;
                }

                BlockPos ringPos = basketSurface.add(dx, 0, dz);
                if (PlacementUtils.isProtected(protectedPositions, ringPos)) {
                    continue;
                }
                PlacementUtils.setTrackedBlock(world, ringPos, theme.signatureRing(), originalBlocks);
                PlacementUtils.addProtectedColumnArea(protectedPositions, ringPos, 0, 3);
            }
        }
    }

    static boolean hasNearbyCampSiteMarker(ServerWorld world, BlockPos center, int searchRadius) {
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
}
