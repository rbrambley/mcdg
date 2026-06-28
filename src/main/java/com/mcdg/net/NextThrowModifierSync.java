package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client packet sent when a player's next-throw power multiplier changes.
 * Used to keep the HUD power bar cap and any trajectory preview in sync with the server.
 */
public final class NextThrowModifierSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "next_throw_modifier");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private NextThrowModifierSync() {
    }

    public record Payload(float nextThrowPowerMultiplier) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            return new Payload(buf.readFloat());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeFloat(nextThrowPowerMultiplier);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
