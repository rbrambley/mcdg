package com.mcdg.world;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Modern Resort Spawn Compound Builder.
 * Clean geometry, flat roofs, large glass surfaces, horizontal emphasis.
 */
public final class ResortBuilder {

    // Compound dimensions
    private static final int COMPOUND_SIZE = 80;
    private static final int PLAZA_SIZE = 20;
    private static final int WALL_HEIGHT = 4;
    private static final int GATEWAY_WIDTH = 5;
    private static final int GATEWAY_HEIGHT = 4;

    // Modern palette
    private static final BlockState WHITE_CONCRETE = Blocks.WHITE_CONCRETE.getDefaultState();
    private static final BlockState STONE_BRICK_WALL = Blocks.STONE_BRICK_WALL.getDefaultState();
    private static final BlockState LIGHT_GRAY_CONCRETE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
    private static final BlockState SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ.getDefaultState();
    private static final BlockState POLISHED_DEEPSLATE = Blocks.POLISHED_DEEPSLATE.getDefaultState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.getDefaultState();
    private static final BlockState DARK_OAK_PLANKS = Blocks.DARK_OAK_PLANKS.getDefaultState();
    private static final BlockState SPRUCE_PLANKS = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState COPPER_BLOCK = Blocks.COPPER_BLOCK.getDefaultState();
    private static final BlockState TINTED_GLASS = Blocks.TINTED_GLASS.getDefaultState();
    private static final BlockState GLASS_PANE = Blocks.GLASS_PANE.getDefaultState();
    private static final BlockState STRIPPED_SPRUCE = Blocks.STRIPPED_SPRUCE_WOOD.getDefaultState();
    private static final BlockState POLISHED_ANDESITE = Blocks.POLISHED_ANDESITE.getDefaultState();
    private static final BlockState POLISHED_DIORITE = Blocks.POLISHED_DIORITE.getDefaultState();
    private static final BlockState GLOWSTONE = Blocks.GLOWSTONE.getDefaultState();
    private static final BlockState SOUL_LANTERN = Blocks.SOUL_LANTERN.getDefaultState();
    private static final BlockState LANTERN = Blocks.LANTERN.getDefaultState();
    private static final BlockState END_ROD = Blocks.END_ROD.getDefaultState();
    private static final BlockState WATER = Blocks.WATER.getDefaultState();
    private static final BlockState SLAB_SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState();
    private static final BlockState TRAPDOOR_DARK_OAK = Blocks.DARK_OAK_TRAPDOOR.getDefaultState();
    private static final BlockState AZALEA_LEAVES = Blocks.AZALEA_LEAVES.getDefaultState();
    private static final BlockState BAMBOO = Blocks.BAMBOO.getDefaultState();
    private static final BlockState GLOW_LICHEN = Blocks.GLOW_LICHEN.getDefaultState();

    private ResortBuilder() {}

    public static void placeResort(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        BlockPos surfaceCenter = SurfaceResolver.resolveSurfacePos(world, center.getX(), center.getZ());
        int baseY = surfaceCenter.getY();
        BlockPos flatCenter = new BlockPos(center.getX(), baseY, center.getZ());

        flattenTerrain(world, flatCenter, originalBlocks, protectedPositions);
        buildPlazaGround(world, flatCenter, originalBlocks, protectedPositions);
        buildModernFountain(world, flatCenter, originalBlocks, protectedPositions);

        // Four modern buildings in cardinal directions - facing courtyard
        int buildingDist = PLAZA_SIZE / 2 + 10; // 5 blocks closer to center
        
        // Lobby faces west (toward center)
        BlockPos lobbyCenter = flatCenter.east(buildingDist);
        buildModernBuildingOriented(world, lobbyCenter, 16, 12, Direction.WEST, originalBlocks, protectedPositions);
        addLobbyInterior(world, lobbyCenter, originalBlocks, protectedPositions);

        // Housing faces east (toward center)
        BlockPos housingCenter = flatCenter.west(buildingDist);
        buildModernBuildingOriented(world, housingCenter, 18, 14, Direction.EAST, originalBlocks, protectedPositions);
        addHousingInterior(world, housingCenter, originalBlocks, protectedPositions);

        // ProShop faces south (toward center)
        BlockPos shopCenter = flatCenter.north(buildingDist);
        buildModernBuildingOriented(world, shopCenter, 12, 12, Direction.SOUTH, originalBlocks, protectedPositions);
        addShopInterior(world, shopCenter, originalBlocks, protectedPositions);

        // Lounge faces north (toward center)
        BlockPos loungeCenter = flatCenter.south(buildingDist);
        buildModernBuildingOriented(world, loungeCenter, 14, 12, Direction.NORTH, originalBlocks, protectedPositions);
        addLoungeInterior(world, loungeCenter, originalBlocks, protectedPositions);

        buildModernPaths(world, flatCenter, lobbyCenter, housingCenter, shopCenter, loungeCenter,
                originalBlocks, protectedPositions);
        buildModernPerimeterWall(world, flatCenter, originalBlocks, protectedPositions);
        buildPlazaFeatures(world, flatCenter, originalBlocks, protectedPositions);

        // Register and broadcast resort waypoint
        BlockPos fountainCenter = new BlockPos(center.getX(), baseY + 1, center.getZ());
        String dimensionId = world.getRegistryKey().getValue().toString();
        ResortWaypointManager.setResortWaypoint(fountainCenter, dimensionId);
        ResortWaypointManager.broadcastToAllPlayers(world);
    }

    private static void flattenTerrain(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int r = COMPOUND_SIZE / 2 + 2;
        int baseY = center.getY();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = center.getX() + dx, z = center.getZ() + dz;
                BlockPos surface = SurfaceResolver.resolveSurfacePos(world, x, z);
                int sy = surface.getY();
                int diff = sy - baseY;
                if (diff > 0) {
                    for (int y = baseY + 1; y <= sy; y++) {
                        BlockPos clearPos = new BlockPos(x, y, z);
                        if (!protectedPositions.contains(clearPos)) {
                            PlacementUtils.setTrackedBlock(world, clearPos, Blocks.AIR.getDefaultState(), originalBlocks);
                        }
                    }
                }
                // Clear vegetation above surface (trees, vines, leaves) within compound
                int clearUpTo = Math.max(sy, baseY) + 25;
                for (int y = sy + 1; y <= clearUpTo; y++) {
                	BlockPos clearPos = new BlockPos(x, y, z);
                	if (!protectedPositions.contains(clearPos) && !world.getBlockState(clearPos).isAir()) {
                		PlacementUtils.setTrackedBlock(world, clearPos, Blocks.AIR.getDefaultState(), originalBlocks);
                	}
                }
                if (diff < 0) {
                    for (int y = sy + 1; y <= baseY; y++) {
                        BlockPos fillPos = new BlockPos(x, y, z);
                        if (!protectedPositions.contains(fillPos)) {
                            PlacementUtils.setTrackedBlock(world, fillPos, Blocks.DIRT.getDefaultState(), originalBlocks);
                        }
                    }
                }
            }
        }
    }

    private static void buildPlazaGround(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int r = PLAZA_SIZE / 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                if (!protectedPositions.contains(pos)) {
                    PlacementUtils.setTrackedBlock(world, pos, POLISHED_ANDESITE, originalBlocks);
                    protectedPositions.add(pos);
                }
                if ((Math.abs(dx) + Math.abs(dz)) % 2 == 0) {
                    BlockPos accent = center.add(dx, -1, dz);
                    if (!protectedPositions.contains(accent)) {
                        PlacementUtils.setTrackedBlock(world, accent, POLISHED_DIORITE, originalBlocks);
                    }
                }
            }
        }
    }

    private static void buildModernFountain(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Square geometric fountain
        int size = 4;
        for (int dx = -size; dx <= size; dx++) {
            for (int dz = -size; dz <= size; dz++) {
                boolean edge = Math.abs(dx) == size || Math.abs(dz) == size;
                BlockPos pos = center.add(dx, 0, dz);
                if (edge) {
                    PlacementUtils.setTrackedBlock(world, pos, SMOOTH_QUARTZ, originalBlocks);
                } else {
                    PlacementUtils.setTrackedBlock(world, pos, WATER, originalBlocks);
                }
                protectedPositions.add(pos);
                protectedPositions.add(pos.down());
            }
        }
        // Center pillar
        for (int y = 0; y < 3; y++) {
            BlockPos pillar = center.up(y);
            PlacementUtils.setTrackedBlock(world, pillar, COPPER_BLOCK, originalBlocks);
            protectedPositions.add(pillar);
        }
        // Water spout
        PlacementUtils.setTrackedBlock(world, center.up(3), WATER, originalBlocks);
        protectedPositions.add(center.up(3));
        
        // Hidden lighting under water
        BlockPos lightPos = center.down();
        PlacementUtils.setTrackedBlock(world, lightPos, GLOWSTONE, originalBlocks);
        protectedPositions.add(lightPos);
    }

    private static void buildModernBuildingOriented(ServerWorld world, BlockPos center, int width, int depth,
            Direction facing, Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int halfW = width / 2, halfD = depth / 2;
        int wallHeight = 5;
        
        // Foundation and floor
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                for (int y = -2; y <= 0; y++) {
                    BlockPos pos = center.add(dx, y, dz);
                    if (!protectedPositions.contains(pos)) {
                        if (y == 0) {
                            PlacementUtils.setTrackedBlock(world, pos, STRIPPED_SPRUCE, originalBlocks);
                        } else {
                            PlacementUtils.setTrackedBlock(world, pos, WHITE_CONCRETE, originalBlocks);
                        }
                        protectedPositions.add(pos);
                    }
                }
            }
        }
        
        // Entrance is on the wall matching the facing direction
        boolean entranceWest = (facing == Direction.WEST);
        boolean entranceEast = (facing == Direction.EAST);
        boolean entranceNorth = (facing == Direction.NORTH);
        boolean entranceSouth = (facing == Direction.SOUTH);
        
        // Walls with large glass windows
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                boolean isEdge = (Math.abs(dx) == halfW || Math.abs(dz) == halfD);
                if (!isEdge) continue;
                
                boolean isEntranceWall = (entranceEast && dx == halfW) || 
                                        (entranceWest && dx == -halfW) ||
                                        (entranceSouth && dz == halfD) ||
                                        (entranceNorth && dz == -halfD);
                
                for (int dy = 1; dy <= wallHeight; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    boolean isCorner = (Math.abs(dx) == halfW && Math.abs(dz) == halfD);
                    boolean isDoor = isEntranceWall && dy <= 2 && 
                                     ((entranceEast || entranceWest) ? Math.abs(dz) <= 0 : Math.abs(dx) <= 0);
                    
                    if (isCorner) {
                        PlacementUtils.setTrackedBlock(world, pos, WHITE_CONCRETE, originalBlocks);
                        protectedPositions.add(pos);
                    } else if (isDoor) {
                        // Open doorway - leave completely empty
                        protectedPositions.add(pos);
                    } else if (dy >= 2 && dy <= 4 && (Math.abs(dx) % 3 == 0 || Math.abs(dz) % 3 == 0)) {
                        PlacementUtils.setTrackedBlock(world, pos, TINTED_GLASS, originalBlocks);
                        protectedPositions.add(pos);
                    } else {
                        PlacementUtils.setTrackedBlock(world, pos, WHITE_CONCRETE, originalBlocks);
                        protectedPositions.add(pos);
                    }
                }
            }
        }
        
        // Flat roof with overhang
        for (int dx = -halfW - 1; dx <= halfW + 1; dx++) {
            for (int dz = -halfD - 1; dz <= halfD + 1; dz++) {
                BlockPos roofPos = center.add(dx, wallHeight + 1, dz);
                boolean isOverhang = Math.abs(dx) > halfW || Math.abs(dz) > halfD;
                if (isOverhang) {
                    PlacementUtils.setTrackedBlock(world, roofPos, SLAB_SMOOTH_QUARTZ, originalBlocks);
                } else {
                    PlacementUtils.setTrackedBlock(world, roofPos, SMOOTH_QUARTZ, originalBlocks);
                }
                protectedPositions.add(roofPos);
            }
        }
        
        // Entrance awning based on facing
        int awningX = 0, awningZ = 0;
        if (entranceEast) awningX = halfW + 1;
        else if (entranceWest) awningX = -halfW - 1;
        else if (entranceSouth) awningZ = halfD + 1;
        else if (entranceNorth) awningZ = -halfD - 1;
        
        for (int i = -1; i <= 1; i++) {
            BlockPos awningPos = center.add(awningX == 0 ? i : awningX, 3, awningZ == 0 ? i : awningZ);
            PlacementUtils.setTrackedBlock(world, awningPos, TRAPDOOR_DARK_OAK, originalBlocks);
            protectedPositions.add(awningPos);
        }
        
        // Interior ceiling corner end rods (hang down from roof)
        int[][] ceilingCorners = {
            {halfW - 1, halfD - 1}, {-(halfW - 1), halfD - 1},
            {halfW - 1, -(halfD - 1)}, {-(halfW - 1), -(halfD - 1)}
        };
        for (int[] corner : ceilingCorners) {
            BlockPos rodPos = center.add(corner[0], wallHeight, corner[1]);
            if (!protectedPositions.contains(rodPos)) {
                PlacementUtils.setTrackedBlock(world, rodPos, END_ROD, originalBlocks);
                protectedPositions.add(rodPos);
            }
        }
        
        // Interior lighting
        BlockPos lightPos = center.up(wallHeight);
        PlacementUtils.setTrackedBlock(world, lightPos, END_ROD, originalBlocks);
        protectedPositions.add(lightPos);
    }

    private static void addLobbyInterior(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Reception desk
        for (int dx = -3; dx <= 3; dx++) {
            BlockPos deskPos = center.add(dx, 1, -2);
            PlacementUtils.setTrackedBlock(world, deskPos, DARK_OAK_PLANKS, originalBlocks);
            protectedPositions.add(deskPos);
        }
        
        // Sign
        BlockPos signPos = center.up(4).north(5);
        PlacementUtils.setTrackedBlock(world, signPos, LANTERN, originalBlocks);
        protectedPositions.add(signPos);
    }

    private static void addHousingInterior(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Room dividers with beds
        int roomWidth = 4;
        for (int i = -1; i <= 1; i++) {
            int offsetX = i * roomWidth;
            BlockPos bedPos = center.add(offsetX, 1, 2);
            PlacementUtils.setTrackedBlock(world, bedPos, Blocks.WHITE_BED.getDefaultState(), originalBlocks);
            BlockPos chestPos = center.add(offsetX + 1, 1, 2);
            PlacementUtils.setTrackedBlock(world, chestPos, Blocks.CHEST.getDefaultState(), originalBlocks);
            protectedPositions.add(bedPos);
            protectedPositions.add(chestPos);

            // Register the first chest (i == -1) for starter item dispenser
            if (i == -1) {
                ResortChestReplenisher.setChestPosition(chestPos);
            }
        }
    }

    /**
     * Computes the starter-chest position from a stored resort center and registers it
     * with {@link ResortChestReplenisher}. Used to re-establish the dispenser on existing
     * worlds where the resort was built before this feature existed (no rebuild required).
     * Scans the Y column at the exact chest X/Z so it is robust to base-Y differences.
     */
    public static void registerStarterChestFromCenter(ServerWorld world, BlockPos center) {
        int buildingDist = PLAZA_SIZE / 2 + 10;
        int chestX = center.getX() - buildingDist - 3;
        int chestZ = center.getZ() + 2;
        for (int y = world.getTopY(); y >= world.getBottomY(); y--) {
            BlockPos candidate = new BlockPos(chestX, y, chestZ);
            if (world.getBlockState(candidate).isOf(Blocks.CHEST)) {
                ResortChestReplenisher.setChestPosition(candidate);
                com.mcdg.McdgMod.LOGGER.info(
                        "Registered resort starter chest at ({}, {}, {}).",
                        candidate.getX(), candidate.getY(), candidate.getZ());
                return;
            }
        }
        com.mcdg.McdgMod.LOGGER.warn(
                "Resort starter chest not found in column at ({}, ?, {}); dispenser not registered.",
                chestX, chestZ);
    }

    private static void addShopInterior(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Display cases
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                BlockPos casePos = center.add(dx, 1, dz);
                PlacementUtils.setTrackedBlock(world, casePos, GLASS_PANE, originalBlocks);
                protectedPositions.add(casePos);
            }
        }
    }

    private static void addLoungeInterior(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Lounge seating
        int[][] seatOffsets = {{-3, 0}, {0, 0}, {3, 0}};
        for (int[] offset : seatOffsets) {
            BlockPos seatPos = center.add(offset[0], 1, offset[1]);
            PlacementUtils.setTrackedBlock(world, seatPos, SPRUCE_PLANKS, originalBlocks);
            protectedPositions.add(seatPos);
        }
    }

    private static void buildModernPaths(ServerWorld world, BlockPos courtyardCenter,
            BlockPos lobby, BlockPos housing, BlockPos shop, BlockPos lounge,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Cross-shaped paths - skip plaza center
        int plazaEdge = PLAZA_SIZE / 2 + 1; // Start path outside plaza
        int wallEdge = COMPOUND_SIZE / 2 - 2;
        
        // North path (from plaza edge to wall)
        for (int dz = -wallEdge; dz <= -plazaEdge; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos pos = courtyardCenter.add(dx, 0, dz);
                if (!protectedPositions.contains(pos)) {
                    PlacementUtils.setTrackedBlock(world, pos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(pos);
                }
            }
        }
        
        // South path
        for (int dz = plazaEdge; dz <= wallEdge; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos pos = courtyardCenter.add(dx, 0, dz);
                if (!protectedPositions.contains(pos)) {
                    PlacementUtils.setTrackedBlock(world, pos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(pos);
                }
            }
        }
        
        // East path
        for (int dx = plazaEdge; dx <= wallEdge; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = courtyardCenter.add(dx, 0, dz);
                if (!protectedPositions.contains(pos)) {
                    PlacementUtils.setTrackedBlock(world, pos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(pos);
                }
            }
        }
        
        // West path
        for (int dx = -wallEdge; dx <= -plazaEdge; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = courtyardCenter.add(dx, 0, dz);
                if (!protectedPositions.contains(pos)) {
                    PlacementUtils.setTrackedBlock(world, pos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(pos);
                }
            }
        }
    }

    private static void buildModernPerimeterWall(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int r = COMPOUND_SIZE / 2;
        int height = WALL_HEIGHT;
        
        // Wall perimeter
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean isEdge = (Math.abs(dx) == r || Math.abs(dz) == r);
                if (!isEdge) continue;
                
                // Check for gateway openings
                boolean isGateway = (Math.abs(dx) <= GATEWAY_WIDTH / 2 && Math.abs(dz) == r) ||
                                   (Math.abs(dz) <= GATEWAY_WIDTH / 2 && Math.abs(dx) == r);
                
                for (int dy = 0; dy < height; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (isGateway && dy < GATEWAY_HEIGHT) continue; // Open gateway
                    
                    if (!protectedPositions.contains(pos)) {
                        // Alternating smooth quartz and deepslate for modern look
                        if (dy == height - 1 || dy == 0) {
                            PlacementUtils.setTrackedBlock(world, pos, POLISHED_DEEPSLATE, originalBlocks);
                        } else {
                            PlacementUtils.setTrackedBlock(world, pos, SMOOTH_QUARTZ, originalBlocks);
                        }
                        protectedPositions.add(pos);
                    }
                }
            }
        }
        
        // End rods on wall top corners
        int[][] wallCorners = {{r, r}, {r, -r}, {-r, r}, {-r, -r}};
        for (int[] wc : wallCorners) {
            BlockPos cornerRod = center.add(wc[0], WALL_HEIGHT, wc[1]);
            if (!protectedPositions.contains(cornerRod)) {
                PlacementUtils.setTrackedBlock(world, cornerRod, END_ROD, originalBlocks);
                protectedPositions.add(cornerRod);
            }
        }
        
        // End rods every 5 blocks on top of wall
        for (int i = -r; i <= r; i += 5) {
            if (Math.abs(i) == r) continue; // skip corners (handled above)
            if (Math.abs(i) <= GATEWAY_WIDTH / 2) continue; // skip gateway openings
            
            BlockPos nRod = center.add(i, WALL_HEIGHT, -r);
            if (!protectedPositions.contains(nRod)) {
                PlacementUtils.setTrackedBlock(world, nRod, END_ROD, originalBlocks);
                protectedPositions.add(nRod);
            }
            BlockPos sRod = center.add(i, WALL_HEIGHT, r);
            if (!protectedPositions.contains(sRod)) {
                PlacementUtils.setTrackedBlock(world, sRod, END_ROD, originalBlocks);
                protectedPositions.add(sRod);
            }
            BlockPos eRod = center.add(r, WALL_HEIGHT, i);
            if (!protectedPositions.contains(eRod)) {
                PlacementUtils.setTrackedBlock(world, eRod, END_ROD, originalBlocks);
                protectedPositions.add(eRod);
            }
            BlockPos wRod = center.add(-r, WALL_HEIGHT, i);
            if (!protectedPositions.contains(wRod)) {
                PlacementUtils.setTrackedBlock(world, wRod, END_ROD, originalBlocks);
                protectedPositions.add(wRod);
            }
        }
        
        // Gateway horizontal lintels and side pillars
        int[][] gatewayCenters = {{0, r}, {0, -r}, {r, 0}, {-r, 0}};
        for (int[] gw : gatewayCenters) {
            boolean isNorthSouth = (gw[1] == r || gw[1] == -r); // entrance on north/south wall
            
            // Horizontal lintel across top
            for (int i = -GATEWAY_WIDTH / 2; i <= GATEWAY_WIDTH / 2; i++) {
                BlockPos lintelPos = isNorthSouth
                    ? center.add(i, GATEWAY_HEIGHT, gw[1])
                    : center.add(gw[0], GATEWAY_HEIGHT, i);
                if (!protectedPositions.contains(lintelPos)) {
                    PlacementUtils.setTrackedBlock(world, lintelPos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(lintelPos);
                }
            }
            
            // Side pillars
            int pillarLeft = -GATEWAY_WIDTH / 2;
            int pillarRight = GATEWAY_WIDTH / 2;
            for (int dy = 0; dy <= GATEWAY_HEIGHT; dy++) {
                // Left pillar
                BlockPos leftPillar = isNorthSouth
                    ? center.add(pillarLeft, dy, gw[1])
                    : center.add(gw[0], dy, pillarLeft);
                if (!protectedPositions.contains(leftPillar)) {
                    PlacementUtils.setTrackedBlock(world, leftPillar, SMOOTH_QUARTZ, originalBlocks);
                    protectedPositions.add(leftPillar);
                }
                // Right pillar
                BlockPos rightPillar = isNorthSouth
                    ? center.add(pillarRight, dy, gw[1])
                    : center.add(gw[0], dy, pillarRight);
                if (!protectedPositions.contains(rightPillar)) {
                    PlacementUtils.setTrackedBlock(world, rightPillar, SMOOTH_QUARTZ, originalBlocks);
                    protectedPositions.add(rightPillar);
                }
            }
            
            // Threshold at bottom
            for (int i = -GATEWAY_WIDTH / 2 + 1; i <= GATEWAY_WIDTH / 2 - 1; i++) {
                BlockPos threshPos = isNorthSouth
                    ? center.add(i, 0, gw[1])
                    : center.add(gw[0], 0, i);
                if (!protectedPositions.contains(threshPos)) {
                    PlacementUtils.setTrackedBlock(world, threshPos, POLISHED_DEEPSLATE, originalBlocks);
                    protectedPositions.add(threshPos);
                }
            }
            
            // Recessed lighting inside lintel
            for (int i = -GATEWAY_WIDTH / 2 + 1; i <= GATEWAY_WIDTH / 2 - 1; i++) {
                BlockPos lightPos = isNorthSouth
                    ? center.add(i, GATEWAY_HEIGHT - 1, gw[1] == r ? gw[1] - 1 : gw[1] + 1)
                    : center.add(gw[0] == r ? gw[0] - 1 : gw[0] + 1, GATEWAY_HEIGHT - 1, i);
                if (!protectedPositions.contains(lightPos)) {
                    PlacementUtils.setTrackedBlock(world, lightPos, END_ROD, originalBlocks);
                    protectedPositions.add(lightPos);
                }
            }
        }
    }

    private static void buildPlazaFeatures(ServerWorld world, BlockPos center,
            Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        // Modern benches around fountain
        int benchDist = 7;
        int[][] benchOffsets = {{benchDist, 0}, {-benchDist, 0}, {0, benchDist}, {0, -benchDist}};
        for (int[] offset : benchOffsets) {
            BlockPos benchPos = center.add(offset[0], 0, offset[1]);
            PlacementUtils.setTrackedBlock(world, benchPos, SLAB_SMOOTH_QUARTZ, originalBlocks);
            protectedPositions.add(benchPos);
            
            // Lantern post support
            BlockPos postPos = benchPos.up();
            PlacementUtils.setTrackedBlock(world, postPos, STONE_BRICK_WALL, originalBlocks);
            protectedPositions.add(postPos);
            
            // Lantern on top of post
            BlockPos lanternPos = benchPos.up(2);
            PlacementUtils.setTrackedBlock(world, lanternPos, LANTERN, originalBlocks);
            protectedPositions.add(lanternPos);
        }
        
        // Planters at corners
        int planterDist = PLAZA_SIZE / 2 - 2;
        int[][] planterOffsets = {{planterDist, planterDist}, {planterDist, -planterDist},
                                 {-planterDist, planterDist}, {-planterDist, -planterDist}};
        for (int[] offset : planterOffsets) {
            BlockPos planterPos = center.add(offset[0], 0, offset[1]);
            PlacementUtils.setTrackedBlock(world, planterPos, WHITE_CONCRETE, originalBlocks);
            BlockPos plantPos = planterPos.up();
            PlacementUtils.setTrackedBlock(world, plantPos, AZALEA_LEAVES, originalBlocks);
            protectedPositions.add(planterPos);
            protectedPositions.add(plantPos);
        }
    }
}
