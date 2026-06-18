package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server packet sent by an invited player to accept or reject a round invite.
 */
public final class RoundInviteResponse {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "round_invite_response");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private RoundInviteResponse() {
    }

    public record Payload(UUID initiatorId, boolean accepted) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            String initiatorIdStr = buf.readString();
            UUID initiatorId;
            try {
                initiatorId = UUID.fromString(initiatorIdStr);
            } catch (IllegalArgumentException e) {
                initiatorId = new UUID(0L, 0L);
            }
            boolean accepted = buf.readBoolean();
            return new Payload(initiatorId, accepted);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(initiatorId != null ? initiatorId.toString() : new UUID(0L, 0L).toString());
            buf.writeBoolean(accepted);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
