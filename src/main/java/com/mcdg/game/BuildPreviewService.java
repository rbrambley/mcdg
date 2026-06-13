package com.mcdg.game;

import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class BuildPreviewService {
    private static final int MIN_DISTANCE_FEET = 180;
    private static final int MAX_DISTANCE_FEET = 780;
    private static final int PAR3_MAX_FEET = 400;
    private static final int PAR4_MAX_FEET = 700;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;

    private BuildPreviewService() {}

    public static PreviewSpec computePreview(ServerPlayerEntity player, int holeIndex) {
        float yawRad = (float) Math.toRadians(player.getYaw());
        int dx = (int) Math.round(-Math.sin(yawRad));
        int dz = (int) Math.round(Math.cos(yawRad));
        if (dx == 0 && dz == 0) {
            dz = 1;
        }
        BlockPos feet = player.getBlockPos();
        BlockPos teeAnchor = feet.add(dx * 2, 0, dz * 2);

        long seed = (((long) teeAnchor.getX()) << 32) ^ (teeAnchor.getZ() * 341873128712L) ^ (holeIndex * 73428767L);
        java.util.Random random = new java.util.Random(seed);

        int distanceFeet = MIN_DISTANCE_FEET + random.nextInt((MAX_DISTANCE_FEET - MIN_DISTANCE_FEET) + 1);
        int distanceBlocks = Math.max(1, Math.round(distanceFeet / 3.0f));
        int fairwayWidth = MIN_FAIRWAY_WIDTH + random.nextInt((MAX_FAIRWAY_WIDTH - MIN_FAIRWAY_WIDTH) + 1);
        int basketHeight = 1 + random.nextInt(2);

        BlockPos basketAnchor = teeAnchor.add(dx * distanceBlocks, 0, dz * distanceBlocks);
        int par = computePar(distanceFeet);

        return new PreviewSpec(seed, holeIndex, teeAnchor, basketAnchor, feet, distanceFeet, par, fairwayWidth, basketHeight);
    }

    public static int computePar(int distanceFeet) {
        if (distanceFeet <= PAR3_MAX_FEET) {
            return 3;
        }
        if (distanceFeet <= PAR4_MAX_FEET) {
            return 4;
        }
        return 5;
    }

    public static void spawnMarker(ServerWorld world, ServerPlayerEntity player, BlockPos pos, double r, double g, double b) {
        world.spawnParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                6, 0.35, 0.35, 0.35, 0.0);
    }

    public static void spawnPath(ServerWorld world, ServerPlayerEntity player, BlockPos from, BlockPos to, double r, double g, double b) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        int steps = Math.max(6, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 6.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.getX() + 0.5 + (dx * t);
            double y = from.getY() + 1.0 + (dy * t);
            double z = from.getZ() + 0.5 + (dz * t);
            world.spawnParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }

    public static final class PreviewSpec {
        public final long seed;
        public final int holeIndex;
        public final BlockPos teeAnchor;
        public final BlockPos basketAnchor;
        public final BlockPos originalTeeAnchor;
        public final int distanceFeet;
        public final int par;
        public final int fairwayWidth;
        public final int basketHeight;

        private PreviewSpec(long seed, int holeIndex, BlockPos teeAnchor, BlockPos basketAnchor, BlockPos originalTeeAnchor,
                              int distanceFeet, int par, int fairwayWidth, int basketHeight) {
            this.seed = seed;
            this.holeIndex = holeIndex;
            this.teeAnchor = teeAnchor;
            this.basketAnchor = basketAnchor;
            this.originalTeeAnchor = originalTeeAnchor;
            this.distanceFeet = distanceFeet;
            this.par = par;
            this.fairwayWidth = fairwayWidth;
            this.basketHeight = basketHeight;
        }
    }
}
