package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-to-client notification that a named course was removed from the catalog.
 *  The client should remove any permanent course waypoint with that name. */
public final class CourseRemovedSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "course_removed");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private CourseRemovedSync() {
    }

    public record Payload(String courseName) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            return new Payload(buf.readString());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(courseName != null ? courseName : "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}