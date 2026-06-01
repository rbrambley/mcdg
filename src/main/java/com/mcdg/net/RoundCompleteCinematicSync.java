package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class RoundCompleteCinematicSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "round_complete_cinematic");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private RoundCompleteCinematicSync() {
    }

    public record Payload(
            boolean active,
            int totalPar,
            int totalPlayers,
            String firstName,
            int firstScore,
            String secondName,
            int secondScore,
            String thirdName,
            int thirdScore,
            int localRank,
            int localScore
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            return active(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeVarInt(totalPar);
            buf.writeVarInt(totalPlayers);
            buf.writeString(firstName);
            buf.writeVarInt(firstScore);
            buf.writeString(secondName);
            buf.writeVarInt(secondScore);
            buf.writeString(thirdName);
            buf.writeVarInt(thirdScore);
            buf.writeVarInt(localRank);
            buf.writeVarInt(localScore);
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0, "", 0, "", 0, "", 0, -1, 0);
        }

        public static Payload active(
                int totalPar,
                int totalPlayers,
                String firstName,
                int firstScore,
                String secondName,
                int secondScore,
                String thirdName,
                int thirdScore,
                int localRank,
                int localScore
        ) {
            return new Payload(
                    true,
                    totalPar,
                    totalPlayers,
                    firstName,
                    firstScore,
                    secondName,
                    secondScore,
                    thirdName,
                    thirdScore,
                    localRank,
                    localScore
            );
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
