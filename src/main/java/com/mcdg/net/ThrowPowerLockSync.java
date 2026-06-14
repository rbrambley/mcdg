package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ThrowPowerLockSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "throw_power_lock");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private ThrowPowerLockSync() {
    }

    public record Payload(boolean locked, float lockedChargePercent) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean locked = buf.readBoolean();
            float lockedChargePercent = buf.readFloat();
            return new Payload(locked, lockedChargePercent);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(locked);
            buf.writeFloat(lockedChargePercent);
        }

        @Override
        public Id<Payload> getId() {
            return ID;
        }
    }
}
