package com.mcdg.world.cave;

import com.mcdg.McdgMod;
import com.mcdg.world.BiomeTheme;

import java.util.Map;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Structure builder for cave courses.
 * Provides cave-specific implementations of tee pads, baskets, and lighting
 * adapted from surface course structures but designed for cave environments.
 */
public final class CaveStructureBuilder {

    private CaveStructureBuilder() {}

    /**
     * Helper method to set tracked blocks (replaces PlacementUtils.setTrackedBlock).
     */
    private static void setTrackedBlock(ServerWorld world, BlockPos pos, BlockState newState, Map<BlockPos, BlockState> originalBlocks) {
        BlockPos immutablePos = pos.toImmutable();
        BlockState current = world.getBlockState(immutablePos);
        if (current.equals(newState)) {
            return;
        }

        originalBlocks.computeIfAbsent(immutablePos, p -> current);
        world.setBlockState(immutablePos, newState, 3);
    }

    /**
     * Helper method to clear headroom (replaces PlacementUtils.clearHeadroom).
     */
    private static void clearHeadroom(ServerWorld world, BlockPos center, int radius, int height, Map<BlockPos, BlockState> originalBlocks, Set<BlockPos> protectedPositions) {
        int r = Math.max(0, radius);
        int h = Math.max(1, height);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx * dx) + (dz * dz) > (r * r + 1)) {
                    continue;
                }

                for (int y = 1; y <= h; y++) {
                    BlockPos target = center.add(dx, y, dz);
                    if (protectedPositions != null && protectedPositions.contains(target.toImmutable())) {
                        continue;
                    }
                    BlockState state = world.getBlockState(target);
                    if (state.isAir() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    setTrackedBlock(world, target, Blocks.AIR.getDefaultState(), originalBlocks);
                }
            }
        }
    }

    /**
     * Places a tee pad using surface design (3x3 themed concrete pad with center accent).
     * Player stands at Y, so floor is at Y-1. Pad goes on floor.
     * Clears and levels a 5x5 area around the tee to ensure proper placement.
     */
    static void placeTeePad(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        // Clear and level 9x9 area around tee (floor Y-1 to Y+4)
        clearAndLevelArea(world, center, 4, -1, 4, theme.teePadBase(), originalBlocks, protectedPositions);

        // Place center accent on leveled floor
        setTrackedBlock(world, center.add(0, -1, 0), theme.teePadCenter(), originalBlocks);

        McdgMod.LOGGER.info("Cave tee pad placed at ({}, {}, {})", center.getX(), center.getY(), center.getZ());
    }

    /**
     * Places an enhanced cave basket with iron bars pole and glowstone.
     * Player stands at Y, so floor is at Y-1. Basket structure goes on floor.
     * Clears and levels a 5x5 area around the basket to ensure proper placement.
     */
    static void placeBasketMarker(
            ServerWorld world,
            BlockPos center,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        // Clear and level 9x9 area around basket (floor Y-1 to Y+4)
        clearAndLevelArea(world, center, 4, -1, 4, Blocks.GREEN_CONCRETE.getDefaultState(), originalBlocks, protectedPositions);

        // Place hopper (actual basket target) at player level (Y0), one block above green concrete floor
        BlockPos hopperPos = center.add(0, 0, 0);
        setTrackedBlock(world, hopperPos, theme.basketBase(), originalBlocks);

        // Place iron bars pole from Y+1 up to Y+3
        for (int i = 1; i <= 3; i++) {
            BlockPos polePos = center.add(0, i, 0);
            setTrackedBlock(world, polePos, theme.basketPole(), originalBlocks);
        }

        // Place glowstone at top (Y+3) for visibility
        BlockPos glowstonePos = center.add(0, 3, 0);
        setTrackedBlock(world, glowstonePos, Blocks.GLOWSTONE.getDefaultState(), originalBlocks);

        McdgMod.LOGGER.info("Cave basket placed at ({}, {}, {})", center.getX(), center.getY(), center.getZ());
    }

    /**
     * Clears and levels a square area around a structure position.
     * Replaces the floor level with the specified material and clears everything above it to air.
     */
    private static void clearAndLevelArea(
            ServerWorld world,
            BlockPos center,
            int radius,
            int floorOffset,
            int clearHeight,
            BlockState floorState,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int floorY = center.getY() + floorOffset;
        int maxY = center.getY() + clearHeight;
        int padRadius = 1; // 3x3 pad area

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = floorY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (protectedPositions != null && protectedPositions.contains(pos.toImmutable())) {
                        continue;
                    }

                    if (y == floorY) {
                        // Replace floor: pad material in 3x3 center, regular stone outside
                        boolean isPadArea = Math.abs(dx) <= padRadius && Math.abs(dz) <= padRadius;
                        BlockState targetFloorState = isPadArea ? floorState : net.minecraft.block.Blocks.STONE.getDefaultState();
                        setTrackedBlock(world, pos, targetFloorState, originalBlocks);
                    } else {
                        // Clear everything above floor to air
                        BlockState current = world.getBlockState(pos);
                        if (!current.isAir()) {
                            setTrackedBlock(world, pos, Blocks.AIR.getDefaultState(), originalBlocks);
                        }
                    }
                }
            }
        }
    }

    /**
     * Places simplified cave lighting using glowstone blocks directly on walls/floor.
     * Places glowstone at regular intervals along the fairway path.
     */
    static void placeFairwayLighting(
            ServerWorld world,
            BlockPos tee,
            BlockPos basket,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions
    ) {
        int dx = basket.getX() - tee.getX();
        int dz = basket.getZ() - tee.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps == 0) {
            return;
        }

        // Place glowstone every 8 steps along the path (increased from 10 for better cave visibility)
        int lightingInterval = 8;
        int lightsPlaced = 0;

        for (int s = lightingInterval; s < steps; s += lightingInterval) {
            double t = (double) s / steps;
            int x = (int) Math.round(tee.getX() + dx * t);
            int z = (int) Math.round(tee.getZ() + dz * t);

            // Try to place glowstone at player level (Y) or one block above (Y+1)
            BlockPos lightPos = new BlockPos(x, tee.getY(), z);
            BlockState state = world.getBlockState(lightPos);

            // If position is solid, place glowstone on top
            if (state.isSolid()) {
                lightPos = lightPos.up();
            }

            // Place glowstone if position is air or replaceable
            if (world.getBlockState(lightPos).isAir() || world.getBlockState(lightPos).isReplaceable()) {
                setTrackedBlock(world, lightPos, Blocks.GLOWSTONE.getDefaultState(), originalBlocks);
                lightsPlaced++;
            }
        }

        McdgMod.LOGGER.info("Cave fairway lighting: {} glowstone placed", lightsPlaced);
    }

    /**
     * Places tee lighting (glowstone near tee pad).
     */
    static void placeTeeLighting(
            ServerWorld world,
            BlockPos tee,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        // Place glowstone near tee pad for visibility
        BlockPos lightPos = tee.add(2, 0, 0); // 2 blocks to the side
        BlockState state = world.getBlockState(lightPos);

        // If position is solid, place glowstone on top
        if (state.isSolid()) {
            lightPos = lightPos.up();
        }

        // Place glowstone if position is air or replaceable
        if (world.getBlockState(lightPos).isAir() || world.getBlockState(lightPos).isReplaceable()) {
            setTrackedBlock(world, lightPos, Blocks.GLOWSTONE.getDefaultState(), originalBlocks);
        }

        McdgMod.LOGGER.info("Cave tee lighting placed at ({}, {}, {})", lightPos.getX(), lightPos.getY(), lightPos.getZ());
    }

    /**
     * Places basket lighting (glowstone near basket).
     */
    static void placeBasketLighting(
            ServerWorld world,
            BlockPos basket,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        // Place additional glowstone near basket for visibility
        BlockPos lightPos = basket.add(2, 0, 0); // 2 blocks to the side
        BlockState state = world.getBlockState(lightPos);

        // If position is solid, place glowstone on top
        if (state.isSolid()) {
            lightPos = lightPos.up();
        }

        // Place glowstone if position is air or replaceable
        if (world.getBlockState(lightPos).isAir() || world.getBlockState(lightPos).isReplaceable()) {
            setTrackedBlock(world, lightPos, Blocks.GLOWSTONE.getDefaultState(), originalBlocks);
        }

        McdgMod.LOGGER.info("Cave basket lighting placed at ({}, {}, {})", lightPos.getX(), lightPos.getY(), lightPos.getZ());
    }

    /**
     * Places tee hole banner and sign with hole information.
     * Duplicates surface course tee information system for caves.
     */
    static void placeTeeHoleBanner(
            ServerWorld world,
            BlockPos teeCenter,
            BlockPos basketCenter,
            int holeNumber,
            int par,
            int distanceFeet,
            boolean signatureHole,
            String signatureName,
            String routeNote,
            Map<BlockPos, BlockState> originalBlocks,
            BiomeTheme theme
    ) {
        // Calculate forward direction from tee to basket
        int dx = basketCenter.getX() - teeCenter.getX();
        int dz = basketCenter.getZ() - teeCenter.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        
        if (steps == 0) {
            return;
        }
        
        // Forward unit vector
        int forwardX = (int) Math.round((double) dx / steps);
        int forwardZ = (int) Math.round((double) dz / steps);
        
        // Left and right vectors (perpendicular to forward)
        int leftX = -forwardZ;
        int leftZ = forwardX;
        int rightX = forwardZ;
        int rightZ = -forwardX;

        // Sign position (forward-left of tee, at floor level Y-1)
        BlockPos signGround = teeCenter.add(forwardX + leftX, -1, forwardZ + leftZ);
        
        // Banner position (forward-right of tee, at floor level Y-1)
        BlockPos bannerGround = teeCenter.add(forwardX + rightX, -1, forwardZ + rightZ);

        // Place banner pole and banner (pole at Y0, banner at Y+1)
        clearHeadroom(world, bannerGround, 1, 4, originalBlocks, null);
        setTrackedBlock(world, bannerGround.up(1), theme.bannerPole(), originalBlocks);
        BlockPos bannerPos = bannerGround.up(2);
        setTrackedBlock(world, bannerPos, theme.banner(), originalBlocks);

        // Place sign with hole information
        String noteToShow = signatureName.isEmpty()
                ? (routeNote.isEmpty() ? "" : routeNote)
                : "★ " + signatureName;
        
        placeTeeHoleSign(
            world,
            signGround,
            -forwardX,
            -forwardZ,
            holeNumber,
            par,
            distanceFeet,
            signatureHole,
            noteToShow,
            originalBlocks
        );

        McdgMod.LOGGER.info("Cave tee banner and sign placed for hole {}", holeNumber);
    }

    /**
     * Places a sign with hole information.
     * Helper method that mimics SignTextGenerator functionality.
     */
    private static void placeTeeHoleSign(
            ServerWorld world,
            BlockPos signGround,
            int faceDirX,
            int faceDirZ,
            int holeNumber,
            int par,
            int distanceFeet,
            boolean signatureHole,
            String hazardNote,
            Map<BlockPos, BlockState> originalBlocks
    ) {
        clearHeadroom(world, signGround, 0, 3, originalBlocks, null);
        BlockPos signPos = signGround.up(1);
        
        // Calculate rotation for sign based on facing direction
        int rotation = standingSignRotationForCardinal(faceDirX, faceDirZ);
        BlockState signState = Blocks.OAK_SIGN
                .getDefaultState()
                .with(net.minecraft.state.property.Properties.ROTATION, rotation);
        
        setTrackedBlock(world, signPos, signState, originalBlocks);

        // Set sign text
        if (world.getBlockEntity(signPos) instanceof net.minecraft.block.entity.SignBlockEntity signBlockEntity) {
            net.minecraft.block.entity.SignText front = signBlockEntity.getFrontText();
            String holeLine = signatureHole ? ("SIG H" + holeNumber) : ("Hole " + holeNumber);
            net.minecraft.block.entity.SignText updated = front
                    .withMessage(0, net.minecraft.text.Text.literal(holeLine))
                    .withMessage(1, net.minecraft.text.Text.literal("Par " + par))
                    .withMessage(2, net.minecraft.text.Text.literal(distanceFeet + " ft"))
                    .withMessage(3, net.minecraft.text.Text.literal(hazardNote));
            signBlockEntity.setText(updated, true);
            signBlockEntity.setText(updated, false);
            signBlockEntity.markDirty();
        }
    }

    /**
     * Calculates standing sign rotation for cardinal direction.
     * Mimics SignTextGenerator logic.
     */
    private static int standingSignRotationForCardinal(int dirX, int dirZ) {
        if (dirX == 1) return 8;  // East
        if (dirX == -1) return 0; // West
        if (dirZ == 1) return 12; // South
        if (dirZ == -1) return 4; // North
        return 0; // Default
    }

    /**
     * Places signature basket accents (decorative ring around basket).
     * Duplicates surface course signature hole decoration for caves.
     */
    static void placeSignatureBasketAccents(
            ServerWorld world,
            BlockPos basketCenter,
            Map<BlockPos, BlockState> originalBlocks,
            Set<BlockPos> protectedPositions,
            BiomeTheme theme
    ) {
        int radius = 3; // Signature ring radius
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = (dx * dx) + (dz * dz);
                if (distSq < ((radius - 1) * (radius - 1)) || distSq > (radius * radius + 1)) {
                    continue;
                }

                BlockPos ringPos = basketCenter.add(dx, -1, dz); // Place on floor
                if (protectedPositions.contains(ringPos.toImmutable())) {
                    continue;
                }
                setTrackedBlock(world, ringPos, theme.signatureRing(), originalBlocks);
            }
        }

        McdgMod.LOGGER.info("Cave signature basket accents placed");
    }
}