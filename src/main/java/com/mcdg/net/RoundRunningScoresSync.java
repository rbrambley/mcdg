package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class RoundRunningScoresSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "round_running_scores");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private RoundRunningScoresSync() {
    }

    public record PlayerRow(String playerName, boolean online, List<Integer> holeScores, int runningTotal) {
        static PlayerRow read(RegistryByteBuf buf) {
            String playerName = buf.readString();
            boolean online = buf.readBoolean();
            int scoreCount = buf.readVarInt();
            List<Integer> holeScores = new ArrayList<>();
            for (int i = 0; i < scoreCount; i++) {
                holeScores.add(buf.readVarInt());
            }
            int runningTotal = buf.readVarInt();
            return new PlayerRow(playerName, online, List.copyOf(holeScores), runningTotal);
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(playerName);
            buf.writeBoolean(online);
            buf.writeVarInt(holeScores.size());
            for (Integer holeScore : holeScores) {
                buf.writeVarInt(holeScore == null ? -1 : holeScore);
            }
            buf.writeVarInt(runningTotal);
        }
    }

    public record Payload(boolean active, int totalHoles, int focusHole, List<PlayerRow> rows) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            int totalHoles = buf.readVarInt();
            int focusHole = buf.readVarInt();
            int rowCount = buf.readVarInt();
            List<PlayerRow> rows = new ArrayList<>();
            for (int i = 0; i < rowCount; i++) {
                rows.add(PlayerRow.read(buf));
            }
            return active(totalHoles, focusHole, rows);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeVarInt(totalHoles);
            buf.writeVarInt(focusHole);
            buf.writeVarInt(rows.size());
            for (PlayerRow row : rows) {
                row.write(buf);
            }
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0, List.of());
        }

        public static Payload active(int totalHoles, int focusHole, List<PlayerRow> rows) {
            return new Payload(true, totalHoles, focusHole, List.copyOf(rows));
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
