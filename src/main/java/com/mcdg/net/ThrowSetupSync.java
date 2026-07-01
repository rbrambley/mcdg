package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client packet for effective throw setup multipliers.
 * Sent whenever the player's held disc, enchantments, skills, or accessories change
 * so the client Setup HUD can show the true totals for the next throw.
 */
public final class ThrowSetupSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_setup");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowSetupSync() {
    }

    public record Payload(
            float powerMultiplier,   // Effective max charge multiplier (hazard penalty, etc.)
            float distanceMultiplier, // Throw speed multiplier including Distance enchant
            float glideMultiplier,   // Glide multiplier including Glide enchant
            float stabilityMultiplier // Stability multiplier including Fade Control enchant
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            float powerMultiplier = buf.readFloat();
            float distanceMultiplier = buf.readFloat();
            float glideMultiplier = buf.readFloat();
            float stabilityMultiplier = buf.readFloat();
            return new Payload(powerMultiplier, distanceMultiplier, glideMultiplier, stabilityMultiplier);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeFloat(powerMultiplier);
            buf.writeFloat(distanceMultiplier);
            buf.writeFloat(glideMultiplier);
            buf.writeFloat(stabilityMultiplier);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
