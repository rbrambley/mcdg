package com.mcdg.world;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public final class ResortWaypointManager {

    private static final String RESORT_WAYPOINT_NAME = "MCDG Resort";
    private static Optional<WaypointEntry> resortWaypoint = Optional.empty();

    public record WaypointEntry(String name, int x, int y, int z, int color, String dimensionId) {}

    private static final int RESORT_COLOR = 0x3399FF;

    private ResortWaypointManager() {}

    public static void setResortWaypoint(BlockPos pos, String dimensionId) {
        resortWaypoint = Optional.of(new WaypointEntry(RESORT_WAYPOINT_NAME, pos.getX(), pos.getY(), pos.getZ(), RESORT_COLOR, dimensionId));
    }

    public static void clearResortWaypoint() {
        resortWaypoint = Optional.empty();
    }

    public static Optional<WaypointEntry> getResortWaypoint() {
        return resortWaypoint;
    }
}
