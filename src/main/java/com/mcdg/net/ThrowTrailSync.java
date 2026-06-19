package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Server-to-client packet for disc throw visual trail and stats.
 * Sent after a player throws the disc, containing the calculated trajectory
 * for particle trail rendering and flight statistics for HUD display.
 */
public final class ThrowTrailSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_trail");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowTrailSync() {
    }

    public record Payload(
            UUID throwerId,          // Player who threw the disc
            Vec3d[] pathPoints,      // Trajectory points for particle trail
            double totalDistanceFt,  // Total horizontal distance in feet
            double lateralDriftFt,   // Left/right drift from aim line in feet
            ThrowStance stance,      // Throw stance for trail color + stats
            ReleaseAngle angle,      // Release angle for stats
            int flightTicks,         // Flight duration for fade sound timing
            StrictPenaltyType penaltyType, // Penalty classification
            int penaltyStrokes,      // Stroke penalty applied
            String penaltyReason,    // Human-readable penalty reason
            int obCrossingFeet,      // Distance from throw lie to first OB crossing
            int returnedToFeet       // Distance from throw lie to resulting lie
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            UUID throwerId = buf.readUuid();
            int pathLength = buf.readVarInt();
            if (pathLength < 0 || pathLength > 1000) {
                pathLength = 0;
            }
            Vec3d[] pathPoints = new Vec3d[pathLength];
            for (int i = 0; i < pathLength; i++) {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                pathPoints[i] = new Vec3d(x, y, z);
            }
            double totalDistanceFt = buf.readDouble();
            double lateralDriftFt = buf.readDouble();
            int stanceOrdinal = buf.readVarInt();
            if (stanceOrdinal < 0 || stanceOrdinal >= ThrowStance.values().length) {
                stanceOrdinal = 0;
            }
            ThrowStance stance = ThrowStance.values()[stanceOrdinal];
            int angleOrdinal = buf.readVarInt();
            if (angleOrdinal < 0 || angleOrdinal >= ReleaseAngle.values().length) {
                angleOrdinal = 0;
            }
            ReleaseAngle angle = ReleaseAngle.values()[angleOrdinal];
            int flightTicks = buf.readVarInt();
            int penaltyOrdinal = buf.readVarInt();
            if (penaltyOrdinal < 0 || penaltyOrdinal >= StrictPenaltyType.values().length) {
                penaltyOrdinal = 0;
            }
            StrictPenaltyType penaltyType = StrictPenaltyType.values()[penaltyOrdinal];
            int penaltyStrokes = buf.readVarInt();
            String penaltyReason = buf.readString(64);
            int obCrossingFeet = buf.readVarInt();
            int returnedToFeet = buf.readVarInt();
            return new Payload(throwerId, pathPoints, totalDistanceFt, lateralDriftFt, stance, angle, flightTicks, penaltyType, penaltyStrokes, penaltyReason, obCrossingFeet, returnedToFeet);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeUuid(throwerId);
            buf.writeVarInt(pathPoints.length);
            for (Vec3d point : pathPoints) {
                buf.writeDouble(point.x);
                buf.writeDouble(point.y);
                buf.writeDouble(point.z);
            }
            buf.writeDouble(totalDistanceFt);
            buf.writeDouble(lateralDriftFt);
            buf.writeVarInt(stance.ordinal());
            buf.writeVarInt(angle.ordinal());
            buf.writeVarInt(flightTicks);
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
