package com.mcdg.world.cave;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.world.BiomeTheme;
import com.mcdg.world.PlacementUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Standalone course placement service for cave environments.
 * Bypasses all surface resolution logic to preserve player's Y coordinate.
 */
public final class CaveCoursePlacementService {
    private static final int TEE_CLEAR_RADIUS = 2; // Reduced from 3 for tighter cave spaces
    private static final int BASKET_CLEAR_RADIUS = 3; // Reduced from 4 for tighter cave spaces
    private static final int FAIRWAY_CLEAR_RADIUS = 5; // Reduced from 7 for tighter cave spaces
    private static final int FAIRWAY_TUNNEL_HEIGHT = 7; // Raised for better disc flight clearance

    private CaveCoursePlacementService() {}

    /**
     * Places a course in a cave environment at the specified origin.
     * All placement uses the origin's Y coordinate without surface resolution.
     */
    public static PlacedCourseState placeCaveCourse(
            ServerWorld world,
            BlockPos origin,
            Course course,
            IntConsumer progressCallback
    ) {
        Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        Map<Integer, BlockPos> holeTees = new HashMap<>();
        Map<Integer, BlockPos> holeBaskets = new HashMap<>();
        Set<BlockPos> protectedPositions = new HashSet<>();

        int baseY = origin.getY();
        BiomeTheme theme = BiomeTheme.DEFAULT; // Use default theme for cave courses

        for (Hole hole : course.holes()) {
            // Calculate absolute positions using origin as base
            int absTeeX = origin.getX() + hole.tee().x();
            int absTeeZ = origin.getZ() + hole.tee().z();
            int absBasketX = origin.getX() + hole.basket().x();
            int absBasketZ = origin.getZ() + hole.basket().z();

            // Use origin's Y for both tee and basket (no surface resolution)
            BlockPos teePos = new BlockPos(absTeeX, baseY, absTeeZ);
            BlockPos basketPos = new BlockPos(absBasketX, baseY, absBasketZ);

            // Clear area around tee
            clearCaveArea(world, teePos, TEE_CLEAR_RADIUS, originalBlocks, protectedPositions);

            // Place tee structure using CaveStructureBuilder (before fairway clearing to prevent overwriting)
            CaveStructureBuilder.placeTeePad(world, teePos, originalBlocks, protectedPositions, theme);

            // Protect full tee structure area from fairway clearing (9x9 from Y-1 to Y+4)
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        protectedPositions.add(teePos.add(dx, dy, dz).toImmutable());
                    }
                }
            }

            // Clear area around basket
            clearCaveArea(world, basketPos, BASKET_CLEAR_RADIUS, originalBlocks, protectedPositions);

            // Place basket structure using CaveStructureBuilder (before fairway clearing to prevent overwriting)
            CaveStructureBuilder.placeBasketMarker(world, basketPos, originalBlocks, protectedPositions, theme);

            // Protect full basket structure area from fairway clearing (9x9 from Y-1 to Y+4)
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 4; dy++) {
                        protectedPositions.add(basketPos.add(dx, dy, dz).toImmutable());
                    }
                }
            }

            // Clear fairway path between tee and basket
            clearCaveFairway(world, teePos, basketPos, FAIRWAY_CLEAR_RADIUS, originalBlocks, protectedPositions);

            // Place cave hazards along fairway
            CaveHazardPlacementService.placeCaveHazards(world, teePos, basketPos, originalBlocks, protectedPositions, course.seed() + hole.index());

            // Place tee hole banner and sign with hole information
            CaveStructureBuilder.placeTeeHoleBanner(
                world,
                teePos,
                basketPos,
                hole.index(),
                hole.par(),
                hole.distanceFeet(),
                hole.isSignature(),
                hole.signatureType().displayName(),
                "", // routeNote - could be enhanced later
                originalBlocks,
                theme
            );

            // Place signature basket accents if this is a signature hole
            if (hole.isSignature()) {
                CaveStructureBuilder.placeSignatureBasketAccents(world, basketPos, originalBlocks, protectedPositions, theme);
            }

            // Place tee lighting
            CaveStructureBuilder.placeTeeLighting(world, teePos, originalBlocks);

            // Place basket lighting
            CaveStructureBuilder.placeBasketLighting(world, basketPos, originalBlocks);

            // Place fairway lighting
            CaveStructureBuilder.placeFairwayLighting(world, teePos, basketPos, originalBlocks, protectedPositions);

            // Store positions
            holeTees.put(hole.index(), teePos);
            holeBaskets.put(hole.index(), basketPos);

            if (progressCallback != null) {
                progressCallback.accept(hole.index());
            }
        }

        // Return placed course state
        return new PlacedCourseState(
                world.getRegistryKey(),
                originalBlocks,
                holeTees,
                holeBaskets,
                new HashMap<>(), // No alternate anchors for cave courses
                new HashMap<>()  // Default effective pars
        );
    }

    /**
     * Clears a cave area by removing vegetation and obstacles.
     */
    private static void clearCaveArea(
            ServerWorld world,
            BlockPos center,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 4; dy++) {
                    BlockPos target = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(target);

                    if (isClearableBlock(state)) {
                        if (!originalBlocks.containsKey(target.toImmutable())) {
                            originalBlocks.put(target.toImmutable(), state);
                        }
                        world.setBlockState(target, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    }

                    // Drain water: replace water/lava with stone at floor level to prevent flow-back
                    if (dy == -1 && isWaterOrLava(state)) {
                        if (!originalBlocks.containsKey(target.toImmutable())) {
                            originalBlocks.put(target.toImmutable(), state);
                        }
                        world.setBlockState(target, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
                    }

                    // Drain water above floor level
                    if (dy != -1 && !state.getFluidState().isEmpty() && state.isOf(Blocks.WATER)) {
                        if (!originalBlocks.containsKey(target.toImmutable())) {
                            originalBlocks.put(target.toImmutable(), state);
                        }
                        world.setBlockState(target, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }

        // Note: protection is added explicitly after structure placement, not here
        // so that clearAndLevelArea can properly replace the floor material
    }

    /**
     * Checks if a block should be cleared during cave preparation.
     */
    private static boolean isClearableBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        // Clear vegetation
        if (state.getBlock() instanceof net.minecraft.block.PlantBlock) {
            return true;
        }

        // Clear small obstacles
        if (state.isOf(net.minecraft.block.Blocks.SHORT_GRASS) || state.isOf(net.minecraft.block.Blocks.TALL_GRASS)) {
            return true;
        }

        // Clear vines and hanging vegetation
        if (state.isOf(net.minecraft.block.Blocks.VINE) || state.isOf(net.minecraft.block.Blocks.CAVE_VINES) ||
            state.isOf(net.minecraft.block.Blocks.CAVE_VINES_PLANT) || state.isOf(net.minecraft.block.Blocks.WEEPING_VINES) ||
            state.isOf(net.minecraft.block.Blocks.WEEPING_VINES_PLANT) || state.isOf(net.minecraft.block.Blocks.TWISTING_VINES) ||
            state.isOf(net.minecraft.block.Blocks.TWISTING_VINES_PLANT)) {
            return true;
        }

        // Clear mushrooms and fungi
        if (state.isOf(net.minecraft.block.Blocks.BROWN_MUSHROOM) || state.isOf(net.minecraft.block.Blocks.RED_MUSHROOM)) {
            return true;
        }

        // Clear roots
        if (state.isOf(net.minecraft.block.Blocks.HANGING_ROOTS)) {
            return true;
        }

        return false;
    }

    /**
     * Clears a fairway path between tee and basket by removing obstacles along the line.
     * Creates a tunnel-like clearing at the player's Y level so discs have a clear throwing path.
     * Aggressively clears headroom to create a tunnel, but preserves walkable floor.
     * Includes lava detection and bridge building for cave environments.
     */
    private static void clearCaveFairway(
            ServerWorld world,
            BlockPos tee,
            BlockPos basket,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basket.getX() - tee.getX();
        int dz = basket.getZ() - tee.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps == 0) {
            return;
        }

        McdgMod.LOGGER.info("Cave fairway clearing: tee=({}, {}, {}) basket=({}, {}, {}) steps={} radius={} tunnelHeight={}",
            tee.getX(), tee.getY(), tee.getZ(), basket.getX(), basket.getY(), basket.getZ(), steps, radius, FAIRWAY_TUNNEL_HEIGHT);

        int blocksCleared = 0;
        int bridgesBuilt = 0;

        // Walk along the line from tee to basket, clearing obstacles at each step
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            int x = (int) Math.round(tee.getX() + dx * t);
            int z = (int) Math.round(tee.getZ() + dz * t);

            // Check for large lava body at this position
            if (isLargeLavaBody(world, x, tee.getY(), z, radius)) {
                // Build stone bridge over lava
                buildStoneBridge(world, x, tee.getY(), z, radius, originalBlocks, protectedPositions);
                bridgesBuilt++;
                continue; // Skip normal clearing for this section
            }

            // Check for significant water within the tunnel volume
            boolean waterInTunnel = isWaterInTunnelVolume(world, x, tee.getY(), z, radius);
            if (waterInTunnel) {
                // Build enclosed tunnel from Y-1 to Y+8 to prevent water from flowing in
                buildEnclosedWaterTunnel(world, x, tee.getY(), z, radius, originalBlocks, protectedPositions);
                bridgesBuilt++;
                continue; // Skip normal clearing for this section
            }

            // Clear a small radius around each point along the path
            for (int rx = -radius; rx <= radius; rx++) {
                for (int rz = -radius; rz <= radius; rz++) {
                    // Skip corners to make it more rounded
                    if (rx * rx + rz * rz > radius * radius + 1) {
                        continue;
                    }

                    // Ensure solid floor at Y-1 - replace water/lava/non-solid with stone (unless protected)
                    BlockPos floorTarget = new BlockPos(x + rx, tee.getY() - 1, z + rz);
                    if (protectedPositions == null || !protectedPositions.contains(floorTarget.toImmutable())) {
                        BlockState floorState = world.getBlockState(floorTarget);
                        if (isWaterOrLava(floorState) || (!floorState.isSolid() && !floorState.isAir())) {
                            // Replace water/lava/non-solid floor with stone to prevent flow-back
                            if (!originalBlocks.containsKey(floorTarget.toImmutable())) {
                                originalBlocks.put(floorTarget.toImmutable(), floorState);
                            }
                            world.setBlockState(floorTarget, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
                            blocksCleared++;
                        } else if (floorState.isAir()) {
                            // Fill air gaps in floor with stone
                            if (!originalBlocks.containsKey(floorTarget.toImmutable())) {
                                originalBlocks.put(floorTarget.toImmutable(), floorState);
                            }
                            world.setBlockState(floorTarget, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);
                            blocksCleared++;
                        }
                    }

                    // Clear air space (dy = 0) - remove all non-air blocks to create player headroom (unless protected)
                    BlockPos airTarget = new BlockPos(x + rx, tee.getY(), z + rz);
                    if (protectedPositions == null || !protectedPositions.contains(airTarget.toImmutable())) {
                        BlockState airState = world.getBlockState(airTarget);
                        if (!airState.isAir() && !airState.isOf(Blocks.LAVA)) {
                            if (!originalBlocks.containsKey(airTarget.toImmutable())) {
                                originalBlocks.put(airTarget.toImmutable(), airState);
                            }
                            world.setBlockState(airTarget, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                            blocksCleared++;
                        }
                    }

                    // Clear headroom (dy >= 1) - remove ALL blocks to create tunnel, drain water (unless protected)
                    for (int dy = 1; dy <= FAIRWAY_TUNNEL_HEIGHT; dy++) {
                        BlockPos target = new BlockPos(x + rx, tee.getY() + dy, z + rz);
                        if (protectedPositions != null && protectedPositions.contains(target.toImmutable())) {
                            continue;
                        }
                        BlockState state = world.getBlockState(target);

                        // Don't clear through lava at head level
                        if (state.isOf(Blocks.LAVA)) {
                            continue;
                        }

                        // Drain water in headroom
                        if (!state.getFluidState().isEmpty() && state.getBlock() == Blocks.WATER) {
                            if (!originalBlocks.containsKey(target.toImmutable())) {
                                originalBlocks.put(target.toImmutable(), state);
                            }
                            world.setBlockState(target, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                            blocksCleared++;
                        }

                        if (!state.isAir()) {
                            if (!originalBlocks.containsKey(target.toImmutable())) {
                                originalBlocks.put(target.toImmutable(), state);
                            }
                            world.setBlockState(target, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                            blocksCleared++;
                        }
                    }
                }
            }
        }

        McdgMod.LOGGER.info("Cave fairway clearing complete: {} blocks cleared, {} stone bridges built", blocksCleared, bridgesBuilt);
    }

    /**
     * Checks if there's a large lava body at the specified position.
     * Returns true if lava covers more than 50% of the check area.
     */
    private static boolean isLargeLavaBody(ServerWorld world, int x, int y, int z, int radius) {
        int lavaCount = 0;
        int totalCheck = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos checkPos = new BlockPos(x + dx, y - 1, z + dz);
                totalCheck++;
                if (world.getBlockState(checkPos).isOf(Blocks.LAVA)) {
                    lavaCount++;
                }
            }
        }

        return totalCheck > 0 && (double) lavaCount / totalCheck > 0.5;
    }

    /**
     * Builds a stone bridge over lava at the specified position.
     */
    private static void buildStoneBridge(
            ServerWorld world,
            int x,
            int y,
            int z,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Skip corners to make it more rounded
                if (dx * dx + dz * dz > radius * radius + 1) {
                    continue;
                }

                // Build stone floor at Y-1
                BlockPos floorPos = new BlockPos(x + dx, y - 1, z + dz);
                if (!originalBlocks.containsKey(floorPos.toImmutable())) {
                    originalBlocks.put(floorPos.toImmutable(), world.getBlockState(floorPos));
                }
                world.setBlockState(floorPos, net.minecraft.block.Blocks.STONE.getDefaultState(), 3);

                // Clear headroom above bridge
                for (int dy = 0; dy <= FAIRWAY_TUNNEL_HEIGHT; dy++) {
                    BlockPos headroomPos = new BlockPos(x + dx, y + dy, z + dz);
                    BlockState state = world.getBlockState(headroomPos);
                    if (!state.isAir() && !state.isOf(Blocks.LAVA)) {
                        if (!originalBlocks.containsKey(headroomPos.toImmutable())) {
                            originalBlocks.put(headroomPos.toImmutable(), state);
                        }
                        world.setBlockState(headroomPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Checks if there's significant water within the tunnel volume.
     * Returns true if water covers more than 30% of the tunnel volume (Y-1 to Y+7).
     */
    private static boolean isWaterInTunnelVolume(ServerWorld world, int x, int y, int z, int radius) {
        int waterCount = 0;
        int totalCheck = 0;

        // Check entire tunnel volume from Y-1 to Y+7
        for (int checkY = y - 1; checkY <= y + FAIRWAY_TUNNEL_HEIGHT; checkY++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Skip corners to make it rounded
                    if (dx * dx + dz * dz > radius * radius + 1) {
                        continue;
                    }

                    BlockPos checkPos = new BlockPos(x + dx, checkY, z + dz);
                    totalCheck++;
                    if (world.getBlockState(checkPos).isOf(Blocks.WATER)) {
                        waterCount++;
                    }
                }
            }
        }

        return totalCheck > 0 && (double) waterCount / totalCheck > 0.30; // 30% threshold
    }

    /**
     * Builds an enclosed tunnel through a water body.
     * Creates tunnel walls from Y-1 to Y+8 with mostly stone and some glass accents.
     * Interior air space is Y0 to Y+7.
     */
    private static void buildEnclosedWaterTunnel(
            ServerWorld world,
            int x,
            int y,
            int z,
            int radius,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        java.util.Random random = new java.util.Random(x + y * 31 + z * 17);
        int wallRadius = radius + 1;
        int enclosureFloor = -1; // Y-1
        int enclosureCeiling = FAIRWAY_TUNNEL_HEIGHT + 1; // Y+8

        for (int dx = -wallRadius; dx <= wallRadius; dx++) {
            for (int dz = -wallRadius; dz <= wallRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > wallRadius * wallRadius + 1) {
                    continue;
                }

                // Build enclosure from Y-1 to Y+8
                for (int dy = enclosureFloor; dy <= enclosureCeiling; dy++) {
                    BlockPos tunnelPos = new BlockPos(x + dx, y + dy, z + dz);
                    if (protectedPositions != null && protectedPositions.contains(tunnelPos.toImmutable())) {
                        continue;
                    }

                    // Inside tunnel: Y0 to Y+7 (interior), clear it
                    boolean isInsideTunnel = distSq <= radius * radius + 1 && dy >= 0 && dy <= FAIRWAY_TUNNEL_HEIGHT;
                    if (isInsideTunnel) {
                        BlockState state = world.getBlockState(tunnelPos);
                        if (!state.isAir() && !state.isOf(Blocks.LAVA)) {
                            if (!originalBlocks.containsKey(tunnelPos.toImmutable())) {
                                originalBlocks.put(tunnelPos.toImmutable(), state);
                            }
                            world.setBlockState(tunnelPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                        }
                    } else {
                        // Build wall/ceiling with mostly stone and some glass
                        boolean useGlass = (dy == enclosureCeiling) && random.nextInt(4) == 0; // 25% glass at ceiling
                        BlockState wallState = useGlass ? net.minecraft.block.Blocks.GLASS.getDefaultState() : net.minecraft.block.Blocks.STONE.getDefaultState();

                        // Mix in some cobblestone for variety
                        if (!useGlass && random.nextInt(5) == 0) {
                            wallState = net.minecraft.block.Blocks.COBBLESTONE.getDefaultState();
                        }

                        BlockState state = world.getBlockState(tunnelPos);
                        if (!originalBlocks.containsKey(tunnelPos.toImmutable())) {
                            originalBlocks.put(tunnelPos.toImmutable(), state);
                        }
                        world.setBlockState(tunnelPos, wallState, 3);
                    }
                }
            }
        }
    }

    /**
     * Checks if a block should be cleared for fairway path.
     * More aggressive than area clearing - removes any non-solid block that could obstruct disc flight.
     * Also removes small solid obstacles that could block the path.
     */
    private static boolean isFairwayClearableBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }

        // Clear all vegetation
        if (state.getBlock() instanceof net.minecraft.block.PlantBlock) {
            return true;
        }

        // Clear vines, roots, mushrooms
        if (state.isOf(net.minecraft.block.Blocks.VINE) || state.isOf(net.minecraft.block.Blocks.CAVE_VINES) ||
            state.isOf(net.minecraft.block.Blocks.CAVE_VINES_PLANT) || state.isOf(net.minecraft.block.Blocks.WEEPING_VINES) ||
            state.isOf(net.minecraft.block.Blocks.WEEPING_VINES_PLANT) || state.isOf(net.minecraft.block.Blocks.TWISTING_VINES) ||
            state.isOf(net.minecraft.block.Blocks.TWISTING_VINES_PLANT) ||
            state.isOf(net.minecraft.block.Blocks.HANGING_ROOTS) ||
            state.isOf(net.minecraft.block.Blocks.BROWN_MUSHROOM) || state.isOf(net.minecraft.block.Blocks.RED_MUSHROOM)) {
            return true;
        }

        // Clear cobwebs
        if (state.isOf(net.minecraft.block.Blocks.COBWEB)) {
            return true;
        }

        // Clear snow layers
        if (state.isOf(net.minecraft.block.Blocks.SNOW)) {
            return true;
        }

        // Clear small solid obstacles that could block disc flight
        if (state.isOf(net.minecraft.block.Blocks.DIRT) || state.isOf(net.minecraft.block.Blocks.COARSE_DIRT) ||
            state.isOf(net.minecraft.block.Blocks.GRAVEL) || state.isOf(net.minecraft.block.Blocks.SAND) ||
            state.isOf(net.minecraft.block.Blocks.CLAY) || state.isOf(net.minecraft.block.Blocks.MUD)) {
            return true;
        }

        // Clear small stone blocks
        if (state.isOf(net.minecraft.block.Blocks.COBBLESTONE) || state.isOf(net.minecraft.block.Blocks.MOSSY_COBBLESTONE)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a block is water or lava (fluid that needs to be drained).
     */
    private static boolean isWaterOrLava(BlockState state) {
        return !state.getFluidState().isEmpty() && !state.isAir();
    }
}