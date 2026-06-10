package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Builds the MCDG resort structure — a central hub with lobby, pro shop,
 * scoreboard hall, player housing, and a 3-hole practice green.
 */
public final class ResortBuilder {

    // Overall compound dimensions
    private static final int COMPOUND_RADIUS = 30; // 60x60 total
    private static final int COURTYARD_RADIUS = 15; // 30x30 inner

    // Building dimensions (interior footprint)
    private static final int LOBBY_WIDTH = 12;
    private static final int LOBBY_DEPTH = 16;
    private static final int PROSHOP_SIZE = 10;
    private static final int SCOREBOARD_WIDTH = 16;
    private static final int SCOREBOARD_DEPTH = 10;
    private static final int HOUSING_WIDTH = 20;
    private static final int HOUSING_DEPTH = 12;
    private static final int WALL_HEIGHT = 4;
    private static final int ROOF_PEAK = 3; // additional height for peak

    // Materials
    private static final BlockState WALL_BLOCK = Blocks.OAK_PLANKS.getDefaultState();
    private static final BlockState FLOOR_BLOCK = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState ROOF_BLOCK = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState ROOF_STAIR = Blocks.SPRUCE_STAIRS.getDefaultState();
    private static final BlockState PATH_BLOCK = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState FOUNTAIN_RIM = Blocks.STONE_BRICKS.getDefaultState();
    private static final BlockState FOUNTAIN_WATER = Blocks.WATER.getDefaultState();
    private static final BlockState WINDOW_BLOCK = Blocks.GLASS_PANE.getDefaultState();
    private static final BlockState DOOR_BLOCK = Blocks.OAK_DOOR.getDefaultState();
    private static final BlockState LIGHT_BLOCK = Blocks.LANTERN.getDefaultState();
    private static final BlockState MARKER_BLOCK = Blocks.BEACON.getDefaultState();

    // Tee/basket for practice green
    private static final BlockState TEE_BLOCK = Blocks.SMOOTH_STONE.getDefaultState();
    private static final BlockState BASKET_POLE = Blocks.OAK_FENCE.getDefaultState();
    private static final BlockState BASKET_HOPPER = Blocks.HOPPER.getDefaultState();

    private ResortBuilder() {}

    /**
     * Places the full resort structure centered at the given position.
     */
    public static void placeResort(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Clear and prepare area
        clearCompoundArea(world, center, originalBlocks, protectedPositions);

        // Build courtyard ground
        buildCourtyardGround(world, center, originalBlocks, protectedPositions);

        // Central fountain
        buildFountain(world, center, originalBlocks, protectedPositions);

        // Place marker at center (for future detection)
        BlockPos markerPos = center.down();
        PlacementUtils.setTrackedBlock(world, markerPos, MARKER_BLOCK, originalBlocks);
        protectedPositions.add(markerPos);

        // Buildings around courtyard (clockwise from east/lobby)
        // East: Lobby (facing west into courtyard)
        BlockPos lobbyCenter = center.east(COURTYARD_RADIUS + LOBBY_DEPTH / 2);
        buildLobby(world, lobbyCenter, center, originalBlocks, protectedPositions);

        // North: Scoreboard Hall (facing south into courtyard)
        BlockPos scoreboardCenter = center.north(COURTYARD_RADIUS + SCOREBOARD_DEPTH / 2);
        buildScoreboardHall(world, scoreboardCenter, center, originalBlocks, protectedPositions);

        // West: Pro Shop (facing east into courtyard)
        BlockPos proShopCenter = center.west(COURTYARD_RADIUS + PROSHOP_SIZE / 2);
        buildProShop(world, proShopCenter, center, originalBlocks, protectedPositions);

        // South: Player Housing (facing north into courtyard)
        BlockPos housingCenter = center.south(COURTYARD_RADIUS + HOUSING_DEPTH / 2);
        buildPlayerHousing(world, housingCenter, center, originalBlocks, protectedPositions);

        // Practice green in southeast quadrant of courtyard
        BlockPos practiceOrigin = center.east(8).south(8);
        buildPracticeGreen(world, practiceOrigin, originalBlocks, protectedPositions);

        // Paths connecting buildings to courtyard center
        buildPaths(world, center, lobbyCenter, scoreboardCenter, proShopCenter, housingCenter,
                originalBlocks, protectedPositions);

        // Perimeter wall (low decorative wall around compound)
        buildPerimeterWall(world, center, originalBlocks, protectedPositions);
    }

    private static void clearCompoundArea(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int r = COMPOUND_RADIUS + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos ground = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                // Clear air above ground for building height
                for (int dy = 1; dy <= WALL_HEIGHT + ROOF_PEAK + 3; dy++) {
                    BlockPos airPos = ground.up(dy);
                    if (!protectedPositions.contains(airPos)) {
                        PlacementUtils.setTrackedBlock(world, airPos, Blocks.AIR.getDefaultState(), originalBlocks);
                    }
                }
            }
        }
    }

    private static void buildCourtyardGround(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int dx = -COURTYARD_RADIUS; dx <= COURTYARD_RADIUS; dx++) {
            for (int dz = -COURTYARD_RADIUS; dz <= COURTYARD_RADIUS; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                pos = SurfaceResolver.resolveSurfacePos(world, pos.getX(), pos.getZ());
                // Use grass for courtyard, with stone paths added later
                if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    // Cross paths
                    PlacementUtils.setTrackedBlock(world, pos, PATH_BLOCK, originalBlocks);
                } else {
                    PlacementUtils.setTrackedBlock(world, pos, Blocks.GRASS_BLOCK.getDefaultState(), originalBlocks);
                }
                protectedPositions.add(pos);
            }
        }
    }

    private static void buildFountain(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // 3x3 fountain with water center
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos rim = center.add(dx, -1, dz); // at ground level
                BlockPos water = center.add(dx, 0, dz); // above ground

                PlacementUtils.setTrackedBlock(world, rim, FOUNTAIN_RIM, originalBlocks);
                protectedPositions.add(rim);

                if (dx == 0 && dz == 0) {
                    PlacementUtils.setTrackedBlock(world, water, FOUNTAIN_WATER, originalBlocks);
                } else {
                    PlacementUtils.setTrackedBlock(world, water, FOUNTAIN_RIM, originalBlocks);
                }
                protectedPositions.add(water);
            }
        }
    }

    private static void buildLobby(
            ServerWorld world,
            BlockPos center,
            BlockPos courtyardCenter,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Facing west (toward courtyard)
        int[] facing = {-1, 0}; // west
        buildRectangularBuilding(world, center, LOBBY_WIDTH, LOBBY_DEPTH, WALL_HEIGHT,
                facing, "Lobby", originalBlocks, protectedPositions);

        // Add interior details
        BlockPos interiorCenter = center.up(1).west(2);
        // Reception desk
        PlacementUtils.setTrackedBlock(world, interiorCenter.east(2), Blocks.OAK_SLAB.getDefaultState(), originalBlocks);
        // Info signs
        placeSign(world, interiorCenter.north(2), "Welcome to MCDG", originalBlocks);
        placeSign(world, interiorCenter.north(2).down(1), "Menu: Press G", originalBlocks);
    }

    private static void buildScoreboardHall(
            ServerWorld world,
            BlockPos center,
            BlockPos courtyardCenter,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Facing south (toward courtyard)
        int[] facing = {0, 1}; // south
        buildRectangularBuilding(world, center, SCOREBOARD_WIDTH, SCOREBOARD_DEPTH, WALL_HEIGHT,
                facing, "Scoreboard Hall", originalBlocks, protectedPositions);

        // Interior - podium
        BlockPos podium = center.up(1).south(2);
        PlacementUtils.setTrackedBlock(world, podium, Blocks.LECTERN.getDefaultState(), originalBlocks);

        // Wall signs for results (blank for now)
        for (int i = -3; i <= 3; i++) {
            BlockPos signPos = center.west(i).up(2).south(SCOREBOARD_DEPTH / 2 - 1);
            placeSign(world, signPos, "Tournament Results", originalBlocks);
        }
    }

    private static void buildProShop(
            ServerWorld world,
            BlockPos center,
            BlockPos courtyardCenter,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Facing east (toward courtyard)
        int[] facing = {1, 0}; // east
        buildSquareBuilding(world, center, PROSHOP_SIZE, WALL_HEIGHT,
                facing, "Pro Shop", originalBlocks, protectedPositions);

        // Interior - shop counter
        BlockPos counter = center.up(1).east(2);
        PlacementUtils.setTrackedBlock(world, counter, Blocks.OAK_SLAB.getDefaultState(), originalBlocks);

        placeSign(world, counter.north(1), "Equipment", originalBlocks);
        placeSign(world, counter.south(1), "Coming Soon", originalBlocks);
    }

    private static void buildPlayerHousing(
            ServerWorld world,
            BlockPos center,
            BlockPos courtyardCenter,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Facing north (toward courtyard)
        int[] facing = {0, -1}; // north
        buildRectangularBuilding(world, center, HOUSING_WIDTH, HOUSING_DEPTH, WALL_HEIGHT,
                facing, "Player Housing", originalBlocks, protectedPositions);

        // 4 individual rooms along the south wall
        int roomSpacing = HOUSING_WIDTH / 4;
        for (int i = 0; i < 4; i++) {
            int offsetX = (i - 1) * roomSpacing;
            BlockPos roomCenter = center.add(offsetX, 1, 2);

            // Door
            BlockPos doorPos = center.add(offsetX, 1, HOUSING_DEPTH / 2 - 1);
            PlacementUtils.setTrackedBlock(world, doorPos, DOOR_BLOCK, originalBlocks);

            // Bed
            PlacementUtils.setTrackedBlock(world, roomCenter.west(1), Blocks.WHITE_BED.getDefaultState(), originalBlocks);

            // Chest
            PlacementUtils.setTrackedBlock(world, roomCenter.east(1), Blocks.CHEST.getDefaultState(), originalBlocks);

            // Window
            BlockPos windowPos = roomCenter.south(2).up(1);
            PlacementUtils.setTrackedBlock(world, windowPos, WINDOW_BLOCK, originalBlocks);
        }

        // Shared hallway lanterns
        for (int x = -HOUSING_WIDTH / 2 + 2; x <= HOUSING_WIDTH / 2 - 2; x += 4) {
            BlockPos lanternPos = center.add(x, 3, 0);
            PlacementUtils.setTrackedBlock(world, lanternPos, LIGHT_BLOCK, originalBlocks);
        }
    }

    private static void buildPracticeGreen(
            ServerWorld world,
            BlockPos origin,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // 3 short holes in southeast courtyard area
        int[][] holeOffsets = {{2, 2}, {6, 4}, {4, 8}}; // relative tee positions

        for (int i = 0; i < 3; i++) {
            BlockPos teePos = origin.add(holeOffsets[i][0], 0, holeOffsets[i][1]);
            teePos = SurfaceResolver.resolveSurfacePos(world, teePos.getX(), teePos.getZ());

            // Tee pad
            PlacementUtils.setTrackedBlock(world, teePos, TEE_BLOCK, originalBlocks);
            protectedPositions.add(teePos);

            // Basket 5-8 blocks away toward southeast
            BlockPos basketPos = teePos.east(3 + i).south(2 + i);
            basketPos = SurfaceResolver.resolveSurfacePos(world, basketPos.getX(), basketPos.getZ());

            // Pole + hopper
            PlacementUtils.setTrackedBlock(world, basketPos, BASKET_POLE, originalBlocks);
            PlacementUtils.setTrackedBlock(world, basketPos.up(1), BASKET_HOPPER, originalBlocks);
            protectedPositions.add(basketPos);
            protectedPositions.add(basketPos.up(1));

            // Putt path as short grass strip
            int steps = 5;
            for (int s = 1; s < steps; s++) {
                BlockPos pathPos = teePos.east((3 + i) * s / steps).south((2 + i) * s / steps);
                pathPos = SurfaceResolver.resolveSurfacePos(world, pathPos.getX(), pathPos.getZ());
                PlacementUtils.setTrackedBlock(world, pathPos, Blocks.SHORT_GRASS.getDefaultState(), originalBlocks);
            }
        }
    }

    private static void buildRectangularBuilding(
            ServerWorld world,
            BlockPos center,
            int width,
            int depth,
            int wallHeight,
            int[] facing,
            String label,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int halfW = width / 2;
        int halfD = depth / 2;

        // Walls
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                // Only build perimeter
                if (Math.abs(dx) == halfW || Math.abs(dz) == halfD) {
                    for (int dy = 0; dy < wallHeight; dy++) {
                        BlockPos pos = center.add(dx, dy, dz);
                        PlacementUtils.setTrackedBlock(world, pos, WALL_BLOCK, originalBlocks);
                        protectedPositions.add(pos);
                    }
                }
            }
        }

        // Floor
        for (int dx = -halfW + 1; dx < halfW; dx++) {
            for (int dz = -halfD + 1; dz < halfD; dz++) {
                BlockPos pos = center.add(dx, -1, dz);
                PlacementUtils.setTrackedBlock(world, pos, FLOOR_BLOCK, originalBlocks);
                protectedPositions.add(pos);
            }
        }

        // Door on facing side
        if (facing[0] != 0) { // east/west facing
            int doorX = facing[0] > 0 ? halfW : -halfW;
            BlockPos doorPos = center.add(doorX, 0, 0);
            PlacementUtils.setTrackedBlock(world, doorPos, DOOR_BLOCK, originalBlocks);
            PlacementUtils.setTrackedBlock(world, doorPos.up(1), DOOR_BLOCK, originalBlocks);
        } else { // north/south facing
            int doorZ = facing[1] > 0 ? halfD : -halfD;
            BlockPos doorPos = center.add(0, 0, doorZ);
            PlacementUtils.setTrackedBlock(world, doorPos, DOOR_BLOCK, originalBlocks);
            PlacementUtils.setTrackedBlock(world, doorPos.up(1), DOOR_BLOCK, originalBlocks);
        }

        // Peaked roof (A-frame along the longer axis)
        boolean widthIsLonger = width >= depth;
        int longSide = widthIsLonger ? width : depth;
        int shortSide = widthIsLonger ? depth : width;
        int longHalf = longSide / 2 + 1; // overhang

        for (int peak = 0; peak <= ROOF_PEAK; peak++) {
            int span = longHalf - peak;
            for (int along = -shortSide / 2; along <= shortSide / 2; along++) {
                for (int across = -span; across <= span; across++) {
                    int rx = widthIsLonger ? across : along;
                    int rz = widthIsLonger ? along : across;
                    BlockPos roofPos = center.add(rx, wallHeight + peak, rz);

                    // Use stairs on edges, planks in middle
                    BlockState roofState = (Math.abs(across) == span && peak < ROOF_PEAK)
                            ? ROOF_STAIR
                            : ROOF_BLOCK;
                    PlacementUtils.setTrackedBlock(world, roofPos, roofState, originalBlocks);
                    protectedPositions.add(roofPos);
                }
            }
        }

        // Lanterns at door
        if (facing[0] != 0) {
            int lanternX = facing[0] > 0 ? halfW + 1 : -halfW - 1;
            PlacementUtils.setTrackedBlock(world, center.add(lanternX, 2, 1), LIGHT_BLOCK, originalBlocks);
            PlacementUtils.setTrackedBlock(world, center.add(lanternX, 2, -1), LIGHT_BLOCK, originalBlocks);
        } else {
            int lanternZ = facing[1] > 0 ? halfD + 1 : -halfD - 1;
            PlacementUtils.setTrackedBlock(world, center.add(1, 2, lanternZ), LIGHT_BLOCK, originalBlocks);
            PlacementUtils.setTrackedBlock(world, center.add(-1, 2, lanternZ), LIGHT_BLOCK, originalBlocks);
        }
    }

    private static void buildSquareBuilding(
            ServerWorld world,
            BlockPos center,
            int size,
            int wallHeight,
            int[] facing,
            String label,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        buildRectangularBuilding(world, center, size, size, wallHeight, facing, label,
                originalBlocks, protectedPositions);
    }

    private static void buildPaths(
            ServerWorld world,
            BlockPos courtyardCenter,
            BlockPos lobby,
            BlockPos scoreboard,
            BlockPos proShop,
            BlockPos housing,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Simple straight paths from each building door to courtyard center
        buildPathSegment(world, courtyardCenter, lobby, originalBlocks, protectedPositions);
        buildPathSegment(world, courtyardCenter, scoreboard, originalBlocks, protectedPositions);
        buildPathSegment(world, courtyardCenter, proShop, originalBlocks, protectedPositions);
        buildPathSegment(world, courtyardCenter, housing, originalBlocks, protectedPositions);
    }

    private static void buildPathSegment(
            ServerWorld world,
            BlockPos from,
            BlockPos to,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Simple linear path
        int steps = Math.max(Math.abs(to.getX() - from.getX()), Math.abs(to.getZ() - from.getZ()));
        if (steps == 0) return;

        for (int i = 0; i <= steps; i++) {
            int x = from.getX() + (to.getX() - from.getX()) * i / steps;
            int z = from.getZ() + (to.getZ() - from.getZ()) * i / steps;
            BlockPos pos = SurfaceResolver.resolveSurfacePos(world, x, z);
            PlacementUtils.setTrackedBlock(world, pos, PATH_BLOCK, originalBlocks);
        }
    }

    private static void buildPerimeterWall(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        // Low stone brick wall around the compound edge
        int r = COMPOUND_RADIUS;
        BlockState wallBlock = Blocks.STONE_BRICK_WALL.getDefaultState();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) == r || Math.abs(dz) == r) {
                    BlockPos pos = SurfaceResolver.resolveSurfacePos(world, center.getX() + dx, center.getZ() + dz);
                    // Only place if there's space above
                    if (world.getBlockState(pos.up(1)).isAir()) {
                        PlacementUtils.setTrackedBlock(world, pos.up(1), wallBlock, originalBlocks);
                        protectedPositions.add(pos.up(1));
                    }
                }
            }
        }
    }

    private static void placeSign(
            ServerWorld world,
            BlockPos pos,
            String text,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        // Place a sign - text setting would require sign block entity access
        // For now, just place the sign block
        PlacementUtils.setTrackedBlock(world, pos, Blocks.OAK_SIGN.getDefaultState(), originalBlocks);
    }
}
