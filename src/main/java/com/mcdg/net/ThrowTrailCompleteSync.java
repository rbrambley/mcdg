package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.StrictPenaltyType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/**
 * Server-to-client packet sent after throw landing resolution.
 * Contains final flight statistics and penalty information.
 * This completes the trail lifecycle after the progressive rendering finishes.
 */
public final class ThrowTrailCompleteSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_trail_complete");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowTrailCompleteSync() {
    }

    public record Payload(
            UUID throwerId,          // Player who threw the disc
            double totalDistanceFt,  // Total horizontal distance in feet
            double lateralDriftFt,   // Left/right drift from aim line in feet
            StrictPenaltyType penaltyType, // Penalty classification
            int penaltyStrokes,      // Stroke penalty applied
            String penaltyReason,    // Human-readable penalty reason
            int obCrossingFeet,      // Distance from throw lie to first OB crossing
            int returnedToFeet       // Distance from throw lie to resulting lie
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            UUID throwerId = buf.readUuid();
            double totalDistanceFt = buf.readDouble();
            double lateralDriftFt = buf.readDouble();
            int penaltyOrdinal = buf.readVarInt();
            if (penaltyOrdinal < 0 || penaltyOrdinal >= StrictPenaltyType.values().length) {
                penaltyOrdinal = 0;
            }
            StrictPenaltyType penaltyType = StrictPenaltyType.values()[penaltyOrdinal];
            int penaltyStrokes = buf.readVarInt();
            String penaltyReason = buf.readString(64);
            int obCrossingFeet = buf.readVarInt();
            int returnedToFeet = buf.readVarInt();
            return new Payload(throwerId, totalDistanceFt, lateralDriftFt, penaltyType, penaltyStrokes, penaltyReason, obCrossingFeet, returnedToFeet);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeUuid(throwerId);
            buf.writeDouble(totalDistanceFt);
            buf.writeDouble(lateralDriftFt);
            buf.writeVarInt(penaltyType.ordinal());
            buf.writeVarInt(penaltyStrokes);
            buf.writeString(penaltyReason);
            buf.writeVarInt(obCrossingFeet);
            buf.writeVarInt(returnedToFeet);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}