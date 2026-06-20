package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.ThrowStance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Server-to-client packet sent immediately when a player throws the disc.
 * Contains trajectory data for real-time progressive trail rendering.
 * This allows all players to see the trail as the disc flies, not after landing.
 */
public final class ThrowTrailStartSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_trail_start");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowTrailStartSync() {
    }

    public record Payload(
            UUID throwerId,          // Player who threw the disc
            Vec3d[] pathPoints,      // Full trajectory path for progressive rendering
            int flightTicks,         // Total flight duration in ticks
            ThrowStance stance,      // Throw stance for particle color
            ReleaseAngle angle       // Release angle for stats display
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
            int flightTicks = buf.readVarInt();
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
            return new Payload(throwerId, pathPoints, flightTicks, stance, angle);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeUuid(throwerId);
            buf.writeVarInt(pathPoints.length);
            for (Vec3d point : pathPoints) {
                buf.writeDouble(point.x);
                buf.writeDouble(point.y);
                buf.writeDouble(point.z);
            }
            buf.writeVarInt(flightTicks);
            buf.writeVarInt(stance.ordinal());
            buf.writeVarInt(angle.ordinal());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}