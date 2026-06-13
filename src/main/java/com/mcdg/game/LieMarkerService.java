package com.mcdg.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LieMarkerService {
    private LieMarkerService() {}

    private static final Map<UUID, Map<BlockPos, LieMarkerState>> LIE_MARKER_HISTORY = new HashMap<>();
    private static int AUTOTEST_MARKER_TRAIL_REFCOUNT = 0;

    public static void beginAutotestLieMarkerTrail() {
        AUTOTEST_MARKER_TRAIL_REFCOUNT++;
    }

    public static void endAutotestLieMarkerTrail(MinecraftServer server) {
        AUTOTEST_MARKER_TRAIL_REFCOUNT = Math.max(0, AUTOTEST_MARKER_TRAIL_REFCOUNT - 1);
        if (AUTOTEST_MARKER_TRAIL_REFCOUNT == 0) {
            clearAllLieMarkers(server);
        }
    }

    public static void updateLieMarker(ServerPlayerEntity player, BlockPos lieFeet) {
        ServerWorld world = player.getServerWorld();
        BlockPos markerPos = lieFeet.down();
        UUID playerId = player.getUuid();
        boolean keepTrail = AUTOTEST_MARKER_TRAIL_REFCOUNT > 0;

        Map<BlockPos, LieMarkerState> history = LIE_MARKER_HISTORY.computeIfAbsent(playerId, ignored -> new HashMap<>());
        BlockPos markerKey = markerPos.toImmutable();

        if (!keepTrail && !history.isEmpty()) {
            clearPlayerLieMarkers(player.getServer(), playerId);
            history = LIE_MARKER_HISTORY.computeIfAbsent(playerId, ignored -> new HashMap<>());
        }

        if (history.containsKey(markerKey)) {
            if (!world.getBlockState(markerKey).isOf(Blocks.LIME_WOOL)) {
                world.setBlockState(markerKey, Blocks.LIME_WOOL.getDefaultState(), 3);
            }
            return;
        }

        BlockState original = world.getBlockState(markerPos);
        world.setBlockState(markerPos, Blocks.LIME_WOOL.getDefaultState(), 3);
        history.put(markerKey, new LieMarkerState(world.getRegistryKey(), markerKey, original));
    }

    public static void clearPlayerLieMarkers(MinecraftServer server, UUID playerId) {
        Map<BlockPos, LieMarkerState> markerStates = LIE_MARKER_HISTORY.get(playerId);
        if (markerStates == null || markerStates.isEmpty()) {
            return;
        }

        for (LieMarkerState markerState : markerStates.values()) {
            ServerWorld world = server.getWorld(markerState.worldKey());
            if (world == null) {
                continue;
            }
            world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), Block.NOTIFY_ALL);
        }

        markerStates.clear();
    }

    public static void clearAllLieMarkers(MinecraftServer server) {
        for (Map<BlockPos, LieMarkerState> markerStates : LIE_MARKER_HISTORY.values()) {
            for (LieMarkerState markerState : markerStates.values()) {
                ServerWorld world = server.getWorld(markerState.worldKey());
                if (world == null) {
                    continue;
                }
                world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), Block.NOTIFY_ALL);
            }
        }
        LIE_MARKER_HISTORY.clear();
    }

    public static void spawnBreadcrumbLine(ServerWorld world, ServerPlayerEntity player, BlockPos to) {
        double sx = player.getX();
        double sy = player.getY() + 6.5;
        double sz = player.getZ();
        double tx = to.getX() + 0.5;
        double ty = to.getY() + 6.5;
        double tz = to.getZ() + 0.5;

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(32, Math.max(8, (int) (distance / 3.0)));

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double px = sx + (dx * t);
            double py = sy + (dy * t);
            double pz = sz + (dz * t);
            world.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    public static void reset() {
        LIE_MARKER_HISTORY.clear();
        AUTOTEST_MARKER_TRAIL_REFCOUNT = 0;
    }

    private record LieMarkerState(
            RegistryKey<World> worldKey,
            BlockPos markerPos,
            BlockState previousGroundState
    ) {}
}
