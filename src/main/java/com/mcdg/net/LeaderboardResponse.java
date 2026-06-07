package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class LeaderboardResponse {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "leaderboard_response");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private LeaderboardResponse() {
    }

    public record Entry(String playerName, int score) {
        public static Entry read(RegistryByteBuf buf) {
            return new Entry(buf.readString(), buf.readVarInt());
        }

        public void write(RegistryByteBuf buf) {
            buf.writeString(playerName != null ? playerName : "");
            buf.writeVarInt(score);
        }
    }

    public record Payload(boolean active, String courseName, int totalPar, List<Entry> entries) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            String courseName = buf.readString();
            int totalPar = buf.readVarInt();
            int entryCount = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(Entry.read(buf));
            }
            return new Payload(true, courseName, totalPar, List.copyOf(entries));
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeString(courseName != null ? courseName : "");
            buf.writeVarInt(totalPar);
            buf.writeVarInt(entries.size());
            for (Entry entry : entries) {
                entry.write(buf);
            }
        }

        public static Payload inactive() {
            return new Payload(false, "", 0, List.of());
        }

        public static Payload active(String courseName, int totalPar, List<Entry> entries) {
            return new Payload(true, courseName != null ? courseName : "", totalPar, List.copyOf(entries));
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
