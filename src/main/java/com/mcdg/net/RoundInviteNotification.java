package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client packet sent to each invited player.
 */
public final class RoundInviteNotification {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "round_invite_notify");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private RoundInviteNotification() {
    }

    public record Payload(UUID initiatorId, String initiatorName, String courseName, int catalogIndex) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            String initiatorIdStr = buf.readString();
            UUID initiatorId;
            try {
                initiatorId = UUID.fromString(initiatorIdStr);
            } catch (IllegalArgumentException e) {
                initiatorId = new UUID(0L, 0L);
            }
            String initiatorName = buf.readString();
            String courseName = buf.readString();
            int catalogIndex = buf.readVarInt();
            return new Payload(initiatorId, initiatorName, courseName, catalogIndex);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(initiatorId != null ? initiatorId.toString() : new UUID(0L, 0L).toString());
            buf.writeString(initiatorName != null ? initiatorName : "");
            buf.writeString(courseName != null ? courseName : "");
            buf.writeVarInt(catalogIndex);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
