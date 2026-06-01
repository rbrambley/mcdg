package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class AceCinematicSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "ace_cinematic");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private AceCinematicSync() {
    }

    public record Payload(
            boolean active,
            int holeIndex,
            int distanceFeet
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            return active(
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeVarInt(holeIndex);
            buf.writeVarInt(distanceFeet);
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0);
        }

        public static Payload active(int holeIndex, int distanceFeet) {
            return new Payload(true, holeIndex, distanceFeet);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
