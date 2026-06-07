package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.enums.BedPart;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Builds camp facilities and lodging sites for placed courses.
 */
final class CampBuilder {
    private CampBuilder() {}

    static void placePermanentLodgingSite(
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
            CoursePlacementService.clearHeadroom(world, yurtCenter, 5, 7, originalBlocks, protectedPositions);
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

        CoursePlacementService.addProtectedColumnArea(protectedPositions, campCenter, 8, 8);
    }

    static BlockPos resolveCampSurfaceCenter(
            ServerWorld world,
            BlockPos seed,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos center = SurfaceResolver.normalizePlayableSurface(world, SurfaceResolver.resolveSurfacePos(world, seed.getX(), seed.getZ()));
        if (CoursePlacementService.isUnsafeSurface(world, center)) {
            center = CoursePlacementService.ensureLandIslandSurface(world, center, 2, originalBlocks, protectedPositions);
        }
        return center;
    }

    static void buildCampCommons(
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
                if (CoursePlacementService.isProtected(protectedPositions, ground)) {
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
            if (CoursePlacementService.isProtected(protectedPositions, firePos)) {
                continue;
            }
            PlacementUtils.setTrackedBlock(world, firePos, i == 0 ? Blocks.SOUL_CAMPFIRE.getDefaultState() : Blocks.CAMPFIRE.getDefaultState(), originalBlocks);
            CoursePlacementService.addProtectedColumnArea(protectedPositions, firePos, 0, 4);
        }
    }

    static void placePlayerYurt(
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
                if (!CoursePlacementService.isProtected(protectedPositions, floorPos)) {
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
                        if (!CoursePlacementService.isProtected(protectedPositions, wallPos)) {
                            PlacementUtils.setTrackedBlock(world, wallPos, wall, originalBlocks);
                        }
                    }
                }

                BlockPos roofPos = yurtCenter.add(u, 4, v);
                if (!CoursePlacementService.isProtected(protectedPositions, roofPos)) {
                    PlacementUtils.setTrackedBlock(world, roofPos, Blocks.SPRUCE_SLAB.getDefaultState(), originalBlocks);
                }
            }
        }

        for (int u = -2; u <= 1; u++) {
            for (int v = -2; v <= 1; v++) {
                BlockPos roofCrown = yurtCenter.add(u, 5, v);
                if (!CoursePlacementService.isProtected(protectedPositions, roofCrown)) {
                    PlacementUtils.setTrackedBlock(world, roofCrown, wall, originalBlocks);
                }
            }
        }

        BlockPos lanternAnchor = yurtCenter.up(4);
        if (!CoursePlacementService.isProtected(protectedPositions, lanternAnchor)) {
            PlacementUtils.setTrackedBlock(world, lanternAnchor, Blocks.CHAIN.getDefaultState(), originalBlocks);
        }
        if (!CoursePlacementService.isProtected(protectedPositions, lanternAnchor.down())) {
            PlacementUtils.setTrackedBlock(world, lanternAnchor.down(), Blocks.LANTERN.getDefaultState(), originalBlocks);
        }

        int bedSide = Math.max(-2, Math.min(1, PlacementUtils.directionToSideStep(interiorFacing) * 2));
        int bedForward = Math.max(-2, Math.min(1, PlacementUtils.directionToForwardStep(interiorFacing) * 2));
        BlockPos bedFoot = yurtCenter.add(bedSide, 1, bedForward);
        if (!CoursePlacementService.isProtected(protectedPositions, bedFoot)) {
            PlacementUtils.setTrackedBlock(world, bedFoot, bed.with(Properties.HORIZONTAL_FACING, interiorFacing).with(Properties.BED_PART, BedPart.FOOT), originalBlocks);
        }
        BlockPos bedHead = bedFoot.offset(interiorFacing);
        if (!CoursePlacementService.isProtected(protectedPositions, bedHead)) {
            PlacementUtils.setTrackedBlock(world, bedHead, bed.with(Properties.HORIZONTAL_FACING, interiorFacing).with(Properties.BED_PART, BedPart.HEAD), originalBlocks);
        }

        BlockPos chestPos = yurtCenter.add(-2, 1, -2);
        BlockPos craftingPos = yurtCenter.add(2, 1, -2);
        BlockPos furnacePos = yurtCenter.add(-2, 1, 2);
        BlockPos smelterPos = yurtCenter.add(2, 1, 2);
        if (!CoursePlacementService.isProtected(protectedPositions, chestPos)) {
            PlacementUtils.setTrackedBlock(world, chestPos, yurtIndex % 2 == 0 ? Blocks.CHEST.getDefaultState() : Blocks.BARREL.getDefaultState(), originalBlocks);
        }
        if (!CoursePlacementService.isProtected(protectedPositions, craftingPos)) {
            PlacementUtils.setTrackedBlock(world, craftingPos, Blocks.CRAFTING_TABLE.getDefaultState(), originalBlocks);
        }
        if (!CoursePlacementService.isProtected(protectedPositions, furnacePos)) {
            PlacementUtils.setTrackedBlock(world, furnacePos, Blocks.FURNACE.getDefaultState(), originalBlocks);
        }
        if (!CoursePlacementService.isProtected(protectedPositions, smelterPos)) {
            PlacementUtils.setTrackedBlock(world, smelterPos, Blocks.BLAST_FURNACE.getDefaultState(), originalBlocks);
        }

        placeYurtUniqueAccent(world, yurtCenter, yurtIndex, originalBlocks, protectedPositions);
        CoursePlacementService.addProtectedColumnArea(protectedPositions, yurtCenter, 5, 8);
    }

    static void placeYurtUniqueAccent(
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

    static void placeSwimmingPool(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos poolCenter = center;
        CoursePlacementService.clearHeadroom(world, poolCenter, 11, 6, originalBlocks, protectedPositions);

        for (int u = -7; u <= 7; u++) {
            for (int v = -5; v <= 5; v++) {
                BlockPos floor = PlacementUtils.orientedOffset(poolCenter, side, back, u, v, -2);
                BlockPos mid = floor.up(1);
                BlockPos top = floor.up(2);
                if (!CoursePlacementService.isProtected(protectedPositions, floor)) {
                    PlacementUtils.setTrackedBlock(world, floor, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                }

                boolean edge = Math.abs(u) == 7 || Math.abs(v) == 5;
                if (edge) {
                    if (!CoursePlacementService.isProtected(protectedPositions, mid)) {
                        PlacementUtils.setTrackedBlock(world, mid, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                    }
                    if (!CoursePlacementService.isProtected(protectedPositions, top)) {
                        PlacementUtils.setTrackedBlock(world, top, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                    }
                } else {
                    if (!CoursePlacementService.isProtected(protectedPositions, mid)) {
                        PlacementUtils.setTrackedBlock(world, mid, Blocks.WATER.getDefaultState(), originalBlocks);
                    }
                    if (!CoursePlacementService.isProtected(protectedPositions, top)) {
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

        CoursePlacementService.addProtectedColumnArea(protectedPositions, poolCenter, 11, 6);
    }

    static void placeTennisCourt(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos courtCenter = center;
        CoursePlacementService.clearHeadroom(world, courtCenter, 15, 8, originalBlocks, protectedPositions);

        for (int u = -11; u <= 11; u++) {
            for (int v = -6; v <= 6; v++) {
                BlockPos pos = PlacementUtils.orientedOffset(courtCenter, side, back, u, v, 0);
                if (CoursePlacementService.isProtected(protectedPositions, pos)) {
                    continue;
                }

                boolean line = Math.abs(u) == 11 || Math.abs(v) == 6 || u == 0 || Math.abs(v) == 4;
                PlacementUtils.setTrackedBlock(world, pos, line ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.GREEN_CONCRETE.getDefaultState(), originalBlocks);
            }
        }

        for (int v = -6; v <= 6; v++) {
            BlockPos netLeft = PlacementUtils.orientedOffset(courtCenter, side, back, -1, v, 1);
            BlockPos netRight = PlacementUtils.orientedOffset(courtCenter, side, back, 1, v, 1);
            if (!CoursePlacementService.isProtected(protectedPositions, netLeft)) {
                PlacementUtils.setTrackedBlock(world, netLeft, Blocks.IRON_BARS.getDefaultState(), originalBlocks);
            }
            if (!CoursePlacementService.isProtected(protectedPositions, netRight)) {
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

        CoursePlacementService.addProtectedColumnArea(protectedPositions, courtCenter, 15, 8);
    }

    static void placeBasketballCourt(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos courtCenter = center;
        CoursePlacementService.clearHeadroom(world, courtCenter, 13, 8, originalBlocks, protectedPositions);

        for (int u = -9; u <= 9; u++) {
            for (int v = -5; v <= 5; v++) {
                BlockPos pos = PlacementUtils.orientedOffset(courtCenter, side, back, u, v, 0);
                if (CoursePlacementService.isProtected(protectedPositions, pos)) {
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

        CoursePlacementService.addProtectedColumnArea(protectedPositions, courtCenter, 13, 8);
    }

    static void placeBathhouse(
            ServerWorld world,
            BlockPos center,
            int[] side,
            int[] back,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        BlockPos bathCenter = center;
        CoursePlacementService.clearHeadroom(world, bathCenter, 13, 9, originalBlocks, protectedPositions);

        for (int u = -9; u <= 9; u++) {
            for (int v = -6; v <= 6; v++) {
                BlockPos floor = PlacementUtils.orientedOffset(bathCenter, side, back, u, v, 0);
                if (!CoursePlacementService.isProtected(protectedPositions, floor)) {
                    PlacementUtils.setTrackedBlock(world, floor, Blocks.SMOOTH_QUARTZ.getDefaultState(), originalBlocks);
                }

                boolean wall = Math.abs(u) == 9 || Math.abs(v) == 6;
                if (wall) {
                    for (int y = 1; y <= 4; y++) {
                        BlockPos wallPos = floor.up(y);
                        if (!CoursePlacementService.isProtected(protectedPositions, wallPos)) {
                            PlacementUtils.setTrackedBlock(world, wallPos, Blocks.QUARTZ_BRICKS.getDefaultState(), originalBlocks);
                        }
                    }
                }

                if (!wall) {
                    BlockPos roof = floor.up(5);
                    if (!CoursePlacementService.isProtected(protectedPositions, roof)) {
                        PlacementUtils.setTrackedBlock(world, roof, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState(), originalBlocks);
                    }
                }
            }
        }

        for (int u = -1; u <= 1; u++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos doorPos = PlacementUtils.orientedOffset(bathCenter, side, back, u, -6, y);
                if (!CoursePlacementService.isProtected(protectedPositions, doorPos)) {
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

        CoursePlacementService.addProtectedColumnArea(protectedPositions, bathCenter, 13, 9);
    }

    static void placeInteriorBlock(
            ServerWorld world,
            BlockPos pos,
            BlockState state,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        if (CoursePlacementService.isProtected(protectedPositions, pos)) {
            return;
        }
        PlacementUtils.setTrackedBlock(world, pos, state, originalBlocks);
    }

    static void placeFacilityLights(
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
            BlockPos lightGround = CoursePlacementService.ensureLandIslandSurface(
                    world,
                    SurfaceResolver.resolveSurfacePos(world, lightSeed.getX(), lightSeed.getZ()),
                    1,
                    originalBlocks,
                    protectedPositions
            );
            CoursePlacementService.placeLanternPost(world, lightGround, postHeight, originalBlocks);
            CoursePlacementService.addProtectedColumnArea(protectedPositions, lightGround, 1, postHeight + 3);
        }
    }

}
