package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-to-client notification that a named waypoint was removed.
 *  The client should remove any waypoint with that name. */
public final class WaypointRemovedSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "waypoint_removed");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private WaypointRemovedSync() {
    }

    public record Payload(String name) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            return new Payload(buf.readString());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(name != null ? name : "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
