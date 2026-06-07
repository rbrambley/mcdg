package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class LeaderboardRequest {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "leaderboard_request");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private LeaderboardRequest() {
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
