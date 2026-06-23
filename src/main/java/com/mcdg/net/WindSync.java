package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.WindMode;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class WindSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "wind_sync");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private WindSync() {
    }

    public record Payload(
            Vec3d velocity,
            double speed,
            float directionDegrees,
            WindMode mode,
            boolean isGusting
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            Vec3d velocity = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            double speed = buf.readDouble();
            float directionDegrees = buf.readFloat();
            int modeOrdinal = buf.readVarInt();
            WindMode mode = modeOrdinal >= 0 && modeOrdinal < WindMode.values().length 
                ? WindMode.values()[modeOrdinal] 
                : WindMode.CALM;
            boolean isGusting = buf.readBoolean();

            return new Payload(velocity, speed, directionDegrees, mode, isGusting);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeDouble(velocity.x);
            buf.writeDouble(velocity.y);
            buf.writeDouble(velocity.z);
            buf.writeDouble(speed);
            buf.writeFloat(directionDegrees);
            buf.writeVarInt(mode.ordinal());
            buf.writeBoolean(isGusting);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
