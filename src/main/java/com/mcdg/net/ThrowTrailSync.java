package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

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
            Vec3d[] pathPoints,      // Trajectory points for particle trail
            double totalDistanceFt,  // Total horizontal distance in feet
            double lateralDriftFt,   // Left/right drift from aim line in feet
            ThrowStance stance,      // Throw stance for trail color + stats
            ReleaseAngle angle,      // Release angle for stats
            int flightTicks          // Flight duration for fade sound timing
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
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
            return new Payload(pathPoints, totalDistanceFt, lateralDriftFt, stance, angle, flightTicks);
        }

        public void write(RegistryByteBuf buf) {
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
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
