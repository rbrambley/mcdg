package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Synchronizes throw stance and release angle from client to server.
 *
 * <p>Following Phase 2 simplified architecture, stance remains client-side
 * until throw time. This packet is sent immediately when the player
 * changes stance (tap R) or release angle (scroll while charging),
 * keeping the server informed of the current preference.
 *
 * <p>This avoids complex state management while ensuring the server
 * knows the correct stance when the throw occurs.
 */
public final class ThrowStanceSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_stance");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowStanceSync() {
    }

    public record Payload(ThrowStance stance, ReleaseAngle angle) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            int stanceOrdinal = buf.readVarInt();
            int angleOrdinal = buf.readVarInt();
            ThrowStance stance = ThrowStance.values()[Math.clamp(stanceOrdinal, 0, ThrowStance.values().length - 1)];
            ReleaseAngle angle = ReleaseAngle.values()[Math.clamp(angleOrdinal, 0, ReleaseAngle.values().length - 1)];
            return new Payload(stance, angle);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeVarInt(stance.ordinal());
            buf.writeVarInt(angle.ordinal());
        }

        @Override
        public Id<Payload> getId() {
            return ID;
        }
    }
}
