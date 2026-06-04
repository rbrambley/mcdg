package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class WaypointSync {
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "waypoint_sync");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private static final Map<UUID, List<WaypointEntry>> LAST_SYNCED_WAYPOINTS = new ConcurrentHashMap<>();

    private WaypointSync() {
    }

    public static void update(ServerPlayerEntity player, List<WaypointEntry> waypoints) {
        if (player == null) {
            return;
        }

        if (waypoints == null || waypoints.isEmpty()) {
            LAST_SYNCED_WAYPOINTS.remove(player.getUuid());
            return;
        }

        LAST_SYNCED_WAYPOINTS.put(player.getUuid(), List.copyOf(waypoints));
    }

    public static void clear(ServerPlayerEntity player) {
        if (player != null) {
            LAST_SYNCED_WAYPOINTS.remove(player.getUuid());
        }
    }

    public static List<WaypointEntry> getWaypoints(ServerPlayerEntity player) {
        if (player == null) {
            return List.of();
        }

        List<WaypointEntry> entries = LAST_SYNCED_WAYPOINTS.get(player.getUuid());
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        return entries;
    }

    public record WaypointEntry(String name, int x, int y, int z, int color, String dimensionId) {
        public WaypointEntry {
            name = name == null ? "" : name;
            dimensionId = dimensionId == null ? "" : dimensionId;
        }

        public static WaypointEntry read(RegistryByteBuf buf) {
            return new WaypointEntry(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readString());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(name == null ? "" : name);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(z);
            buf.writeVarInt(color);
            buf.writeString(dimensionId == null ? "" : dimensionId);
        }
    }

    public record Payload(List<WaypointEntry> waypoints) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            int count = Math.max(0, buf.readVarInt());
            List<WaypointEntry> waypoints = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                waypoints.add(WaypointEntry.read(buf));
            }
            return new Payload(waypoints);
        }

        public void write(RegistryByteBuf buf) {
            List<WaypointEntry> safeWaypoints = waypoints == null ? List.of() : waypoints;
            buf.writeVarInt(safeWaypoints.size());
            for (WaypointEntry waypoint : safeWaypoints) {
                waypoint.write(buf);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}