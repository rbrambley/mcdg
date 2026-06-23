package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Server-to-client packet for elytra disc flight state synchronization.
 * Broadcasts flight position and velocity to all nearby players for visual consistency.
 */
public final class ElytraFlightSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "elytra_flight");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ElytraFlightSync() {
    }

    public record Payload(
            UUID playerId,           // Player in flight
            Vec3d position,          // Current position
            Vec3d velocity,          // Current velocity
            int currentTick,         // Current tick in flight
            int totalFlightTicks,    // Total flight duration
            boolean playerControlEnabled  // Whether player has control (netherite upgraded)
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            UUID playerId = buf.readUuid();
            double posX = buf.readDouble();
            double posY = buf.readDouble();
            double posZ = buf.readDouble();
            Vec3d position = new Vec3d(posX, posY, posZ);
            double velX = buf.readDouble();
            double velY = buf.readDouble();
            double velZ = buf.readDouble();
            Vec3d velocity = new Vec3d(velX, velY, velZ);
            int currentTick = buf.readVarInt();
            int totalFlightTicks = buf.readVarInt();
            boolean playerControlEnabled = buf.readBoolean();
            return new Payload(playerId, position, velocity, currentTick, totalFlightTicks, playerControlEnabled);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeUuid(playerId);
            buf.writeDouble(position.x);
            buf.writeDouble(position.y);
            buf.writeDouble(position.z);
            buf.writeDouble(velocity.x);
            buf.writeDouble(velocity.y);
            buf.writeDouble(velocity.z);
            buf.writeVarInt(currentTick);
            buf.writeVarInt(totalFlightTicks);
            buf.writeBoolean(playerControlEnabled);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
