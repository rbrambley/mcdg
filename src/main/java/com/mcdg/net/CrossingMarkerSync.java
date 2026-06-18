package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.StrictPenaltyType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server-to-client packet for visual OB crossing marker.
 * Sent when a player goes OB, containing the crossing position and penalty type
 * for client-side particle effect rendering.
 */
public final class CrossingMarkerSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "crossing_marker");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private CrossingMarkerSync() {
    }

    public record Payload(
        BlockPos crossingPosition,
        StrictPenaltyType penaltyType,
        int durationTicks  // How long to show marker (default 200 ticks = 10 seconds)
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            BlockPos crossingPosition = new BlockPos(x, y, z);
            int penaltyOrdinal = buf.readVarInt();
            if (penaltyOrdinal < 0 || penaltyOrdinal >= StrictPenaltyType.values().length) {
                penaltyOrdinal = 0;
            }
            StrictPenaltyType penaltyType = StrictPenaltyType.values()[penaltyOrdinal];
            int durationTicks = buf.readVarInt();
            return new Payload(crossingPosition, penaltyType, durationTicks);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeInt(crossingPosition.getX());
            buf.writeInt(crossingPosition.getY());
            buf.writeInt(crossingPosition.getZ());
            buf.writeVarInt(penaltyType.ordinal());
            buf.writeVarInt(durationTicks);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
