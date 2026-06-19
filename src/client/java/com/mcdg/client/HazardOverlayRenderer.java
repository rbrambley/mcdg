package com.mcdg.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Renders strict-surface hazard overlays on the minimap.
 */
final class HazardOverlayRenderer {
    private static final int HAZARD_OVERLAY_ARGB = 0x8CFF9A32;
    private static final int HAZARD_SAMPLE_STEP_PX = 2;
    private static final int BASKET_GREEN_RADIUS_BLOCKS = 7;
    private static final int BASKET_GREEN_HEIGHT_BLOCKS = 8;

    private HazardOverlayRenderer() {}

    enum StrictSurfacePresetClient {
        FAST,
        BALANCED,
        TOURNAMENT
    }

    static StrictSurfacePresetClient strictPresetFromOrdinal(int ordinal) {
        return switch (ordinal) {
            case 0 -> StrictSurfacePresetClient.FAST;
            case 2 -> StrictSurfacePresetClient.TOURNAMENT;
            default -> StrictSurfacePresetClient.BALANCED;
        };
    }

    static boolean isBasketGreenSafeClient(BlockPos feet, BlockPos basketSurface) {
        int dx = feet.getX() - basketSurface.getX();
        int dz = feet.getZ() - basketSurface.getZ();
        int dy = feet.getY() - basketSurface.getY();
        return (dx * dx) + (dz * dz) <= (BASKET_GREEN_RADIUS_BLOCKS * BASKET_GREEN_RADIUS_BLOCKS + 1)
                && dy >= 0
                && dy <= BASKET_GREEN_HEIGHT_BLOCKS;
    }

    static boolean isFluidPenaltyZoneClient(ClientWorld world, BlockPos feet) {
        return world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA);
    }

    static boolean isSteepSlopeHazardClient(ClientWorld world, BlockPos feet, int slopeDeltaThreshold) {
        int centerY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        int[] offsets = { -2, 0, 2 };
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz) - 1;
                if (Math.abs(sampleY - centerY) >= slopeDeltaThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isDenseRoughHazardClient(ClientWorld world, BlockPos feet, int threshold) {
        int roughHits = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surface = new BlockPos(x, topY, z);
                BlockState surfaceState = world.getBlockState(surface);
                BlockState headState = world.getBlockState(surface.up());
                if (isRoughMaterialClient(surfaceState) || isRoughMaterialClient(headState)) {
                    roughHits++;
                }
            }
        }
        return roughHits >= threshold;
    }

    static boolean isRoughMaterialClient(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CACTUS);
    }

    static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = Math.max(0.0, Math.min(1.0, ((px - sx) * dx + (pz - sz) * dz) / lengthSquared));
        double projectionX = sx + t * dx;
        double projectionZ = sz + t * dz;
        double distanceX = px - projectionX;
        double distanceZ = pz - projectionZ;
        return Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);
    }

    static boolean isHazardPenaltyAt(
            ClientWorld world,
            BlockPos feet,
            BlockPos tee,
            BlockPos basket,
            BlockPos basketSurface,
            int corridorHalfWidth,
            StrictSurfacePresetClient preset
    ) {
        if (isFluidPenaltyZoneClient(world, feet)) {
            return false;
        }

        if (distanceFromPointToSegmentXZ(feet, tee, basket) > corridorHalfWidth) {
            return false;
        }

        if (isBasketGreenSafeClient(feet, basketSurface)) {
            return false;
        }

        boolean slopeHazard = preset != StrictSurfacePresetClient.FAST
                && isSteepSlopeHazardClient(world, feet, preset == StrictSurfacePresetClient.TOURNAMENT ? 3 : 4);
        if (slopeHazard) {
            return true;
        }

        return preset == StrictSurfacePresetClient.TOURNAMENT
                && isDenseRoughHazardClient(world, feet, 11);
    }

    static void drawMiniMapStrictHazardOverlay(
            DrawContext drawContext,
            MinecraftClient client,
            McdgClientMod.MiniMapState state,
            double centerWorldX,
            double centerWorldZ,
            int mapCenterX,
            int mapCenterY,
            float mapScale,
            float mapRotationDegrees,
            float hudAlpha,
            float clipCenterX,
            float clipCenterY,
            float clipRadius
    ) {
        if (client.world == null) {
            return;
        }

        ClientWorld world = client.world;
        int overlayColor = HudUtil.withAlpha(HAZARD_OVERLAY_ARGB, hudAlpha);
        int sampleStep = Math.max(2, HAZARD_SAMPLE_STEP_PX);
        float clipRadiusSq = clipRadius * clipRadius;
        int minY = Math.round(mapCenterY - clipRadius);
        int maxY = Math.round(mapCenterY + clipRadius);
        int minX = Math.round(mapCenterX - clipRadius);
        int maxX = Math.round(mapCenterX + clipRadius);

        BlockPos tee = new BlockPos(state.teeX(), 0, state.teeZ());
        BlockPos basket = new BlockPos(state.basketX(), 0, state.basketZ());
        BlockPos basketSurface = basket.down();
        StrictSurfacePresetClient preset = strictPresetFromOrdinal(state.strictSurfacePresetOrdinal());

        for (int py = minY; py <= maxY; py += sampleStep) {
            for (int px = minX; px <= maxX; px += sampleStep) {
                if (!MiniMapDrawingUtils.isPointInsideCircle(px, py, clipCenterX, clipCenterY, clipRadiusSq)) {
                    continue;
                }

                float screenDx = px - mapCenterX;
                float screenDz = py - mapCenterY;
                float[] worldOffsetScaled = MiniMapRenderer.rotateMiniMapVector(screenDx, screenDz, -mapRotationDegrees);
                double worldX = centerWorldX + (worldOffsetScaled[0] / mapScale);
                double worldZ = centerWorldZ + (worldOffsetScaled[1] / mapScale);
                int blockX = net.minecraft.util.math.MathHelper.floor(worldX);
                int blockZ = net.minecraft.util.math.MathHelper.floor(worldZ);
                int feetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
                BlockPos feet = new BlockPos(blockX, feetY, blockZ);

                if (!isHazardPenaltyAt(world, feet, tee, basket, basketSurface, state.corridorHalfWidth(), preset)) {
                    continue;
                }

                MiniMapDrawingUtils.fillRectClipped(drawContext, px, py, sampleStep, sampleStep, overlayColor, clipCenterX, clipCenterY, clipRadiusSq);
            }
        }
    }

}
