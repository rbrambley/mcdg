package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server packet sent by a player who wants to start a round
 * and invite other players to join.
 */
public final class RoundInviteRequest {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "round_invite_request");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private RoundInviteRequest() {
    }

    public record Payload(List<UUID> targetPlayerIds, int catalogIndex) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            int count = Math.max(0, buf.readVarInt());
            List<UUID> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String uuidStr = buf.readString();
                try {
                    targets.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    // Skip malformed UUIDs
                }
            }
            int catalogIndex = buf.readVarInt();
            return new Payload(List.copyOf(targets), catalogIndex);
        }

        public void write(RegistryByteBuf buf) {
            List<UUID> safeTargets = targetPlayerIds == null ? List.of() : targetPlayerIds;
            buf.writeVarInt(safeTargets.size());
            for (UUID id : safeTargets) {
                buf.writeString(id != null ? id.toString() : new UUID(0L, 0L).toString());
            }
            buf.writeVarInt(catalogIndex);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
