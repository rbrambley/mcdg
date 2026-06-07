package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
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
            int mapSpan,
            String courseWaypointName,
            int courseWaypointX,
            int courseWaypointZ,
            int totalHoles,
            List<Integer> holeTeeXs,
            List<Integer> holeTeeZs,
            int lastThrowDistanceFeet
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            int holeIndex = buf.readVarInt();
            int teeX = buf.readVarInt();
            int teeZ = buf.readVarInt();
            int basketX = buf.readVarInt();
            int basketZ = buf.readVarInt();
            int lieX = buf.readVarInt();
            int lieZ = buf.readVarInt();
            int par = buf.readVarInt();
            int throwNumber = buf.readVarInt();
            int totalStrokes = buf.readVarInt();
            int cumulativeParDelta = buf.readVarInt();
            boolean strictMode = buf.readBoolean();
            int strictSurfacePresetOrdinal = buf.readVarInt();
            int corridorHalfWidth = buf.readVarInt();
            boolean hasAlternateAnchor = buf.readBoolean();
            int alternateAnchorX = buf.readVarInt();
            int alternateAnchorZ = buf.readVarInt();
            int mapSpan = buf.readVarInt();
            String courseWaypointName = buf.readString();
            int courseWaypointX = buf.readVarInt();
            int courseWaypointZ = buf.readVarInt();
            int totalHoles = buf.readVarInt();

            int teeCount = Math.max(0, buf.readVarInt());
            List<Integer> holeTeeXs = new ArrayList<>(teeCount);
            List<Integer> holeTeeZs = new ArrayList<>(teeCount);
            for (int i = 0; i < teeCount; i++) {
                holeTeeXs.add(buf.readVarInt());
                holeTeeZs.add(buf.readVarInt());
            }

            return active(
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
                    mapSpan,
                    courseWaypointName,
                    courseWaypointX,
                    courseWaypointZ,
                    totalHoles,
                    holeTeeXs,
                    holeTeeZs,
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
            buf.writeString(courseWaypointName == null ? "" : courseWaypointName);
            buf.writeVarInt(courseWaypointX);
            buf.writeVarInt(courseWaypointZ);
            buf.writeVarInt(totalHoles);
            int teeCount = Math.min(holeTeeXs == null ? 0 : holeTeeXs.size(), holeTeeZs == null ? 0 : holeTeeZs.size());
            buf.writeVarInt(teeCount);
            for (int i = 0; i < teeCount; i++) {
                buf.writeVarInt(holeTeeXs.get(i));
                buf.writeVarInt(holeTeeZs.get(i));
            }
            buf.writeVarInt(lastThrowDistanceFeet);
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, false, 0, 0, false, 0, 0, 0, "", 0, 0, 0, List.of(), List.of(), 0);
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
                int mapSpan,
                String courseWaypointName,
                int courseWaypointX,
                int courseWaypointZ,
                int totalHoles,
                List<Integer> holeTeeXs,
                List<Integer> holeTeeZs,
                int lastThrowDistanceFeet
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
                    mapSpan,
                    courseWaypointName == null ? "" : courseWaypointName,
                    courseWaypointX,
                    courseWaypointZ,
                    totalHoles,
                    holeTeeXs == null ? List.of() : List.copyOf(holeTeeXs),
                    holeTeeZs == null ? List.of() : List.copyOf(holeTeeZs),
                    Math.max(0, lastThrowDistanceFeet)
            );
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
