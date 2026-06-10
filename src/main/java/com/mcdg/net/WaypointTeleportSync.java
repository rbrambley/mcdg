package com.mcdg.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointTeleportSync(String name) implements CustomPayload {
    public static final CustomPayload.Id<WaypointTeleportSync> ID = new CustomPayload.Id<>(Identifier.of("mcdg", "waypoint_teleport"));

    public static final PacketCodec<RegistryByteBuf, WaypointTeleportSync> CODEC = PacketCodec.of(
        (value, buf) -> PacketCodecs.STRING.encode(buf, value.name()),
        buf -> new WaypointTeleportSync(PacketCodecs.STRING.decode(buf))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
