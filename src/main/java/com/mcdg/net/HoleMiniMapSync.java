package com.mcdg.net;

import com.mcdg.McdgMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class HoleMiniMapSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "hole_minimap");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);
    public static final float MAP_OVERSCAN_FACTOR = 1.42f;

    private HoleMiniMapSync() {
    }

    public record Payload(
            boolean active,
            int holeIndex,
            int teeX,
            int teeZ,
            int basketX,
            int basketZ,
            int lieX,
            int lieZ,
            int par,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            boolean strictMode,
            int strictSurfacePresetOrdinal,
            int corridorHalfWidth,
            boolean hasAlternateAnchor,
            int alternateAnchorX,
            int alternateAnchorZ,
            int mapSpan
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            return active(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeVarInt(holeIndex);
            buf.writeVarInt(teeX);
            buf.writeVarInt(teeZ);
            buf.writeVarInt(basketX);
            buf.writeVarInt(basketZ);
            buf.writeVarInt(lieX);
            buf.writeVarInt(lieZ);
            buf.writeVarInt(par);
            buf.writeVarInt(throwNumber);
            buf.writeVarInt(totalStrokes);
            buf.writeVarInt(cumulativeParDelta);
            buf.writeBoolean(strictMode);
            buf.writeVarInt(strictSurfacePresetOrdinal);
            buf.writeVarInt(corridorHalfWidth);
            buf.writeBoolean(hasAlternateAnchor);
            buf.writeVarInt(alternateAnchorX);
            buf.writeVarInt(alternateAnchorZ);
            buf.writeVarInt(mapSpan);
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, false, 0, 0, false, 0, 0, 0);
        }

        public static Payload active(
                int holeIndex,
                int teeX,
                int teeZ,
                int basketX,
                int basketZ,
                int lieX,
                int lieZ,
                int par,
                int throwNumber,
                int totalStrokes,
                int cumulativeParDelta,
                boolean strictMode,
                int strictSurfacePresetOrdinal,
                int corridorHalfWidth,
                boolean hasAlternateAnchor,
                int alternateAnchorX,
                int alternateAnchorZ,
                int mapSpan
        ) {
            return new Payload(
                    true,
                    holeIndex,
                    teeX,
                    teeZ,
                    basketX,
                    basketZ,
                    lieX,
                    lieZ,
                    par,
                    throwNumber,
                    totalStrokes,
                    cumulativeParDelta,
                    strictMode,
                    strictSurfacePresetOrdinal,
                    corridorHalfWidth,
                    hasAlternateAnchor,
                    alternateAnchorX,
                    alternateAnchorZ,
                    mapSpan
            );
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
