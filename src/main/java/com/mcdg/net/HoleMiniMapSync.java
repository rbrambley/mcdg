package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
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
            int lastThrowDistanceFeet,
            int corridorEntryFeet,
            int corridorEntryBearing,
            int waterGapStartFeet,
            int waterGapEndFeet,
            boolean hasWaterGap,
            boolean hasLastThrowStats,
            double lastThrowTotalDistanceFt,
            double lastThrowLateralDriftFt,
            ThrowStance lastThrowStance,
            ReleaseAngle lastThrowAngle,
            int lastThrowFlightTicks,
            StrictPenaltyType lastThrowPenaltyType,
            int lastThrowPenaltyStrokes,
            String lastThrowPenaltyReason,
            int lastThrowObCrossingFeet,
            int lastThrowReturnedToFeet
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

            int lastThrowDistanceFeet = buf.readVarInt();
            int corridorEntryFeet = buf.readVarInt();
            int corridorEntryBearing = buf.readVarInt();
            int waterGapStartFeet = buf.readVarInt();
            int waterGapEndFeet = buf.readVarInt();
            boolean hasWaterGap = buf.readBoolean();

            boolean hasLastThrowStats = buf.readBoolean();
            double lastThrowTotalDistanceFt = 0.0;
            double lastThrowLateralDriftFt = 0.0;
            ThrowStance lastThrowStance = ThrowStance.OVERHAND;
            ReleaseAngle lastThrowAngle = ReleaseAngle.FLAT;
            int lastThrowFlightTicks = 0;
            StrictPenaltyType lastThrowPenaltyType = StrictPenaltyType.NONE;
            int lastThrowPenaltyStrokes = 0;
            String lastThrowPenaltyReason = "";
            int lastThrowObCrossingFeet = 0;
            int lastThrowReturnedToFeet = 0;
            if (hasLastThrowStats) {
                lastThrowTotalDistanceFt = buf.readDouble();
                lastThrowLateralDriftFt = buf.readDouble();
                int stanceOrd = buf.readVarInt();
                if (stanceOrd >= 0 && stanceOrd < ThrowStance.values().length) {
                    lastThrowStance = ThrowStance.values()[stanceOrd];
                }
                int angleOrd = buf.readVarInt();
                if (angleOrd >= 0 && angleOrd < ReleaseAngle.values().length) {
                    lastThrowAngle = ReleaseAngle.values()[angleOrd];
                }
                lastThrowFlightTicks = buf.readVarInt();
                int penaltyOrd = buf.readVarInt();
                if (penaltyOrd >= 0 && penaltyOrd < StrictPenaltyType.values().length) {
                    lastThrowPenaltyType = StrictPenaltyType.values()[penaltyOrd];
                }
                lastThrowPenaltyStrokes = buf.readVarInt();
                lastThrowPenaltyReason = buf.readString(64);
                lastThrowObCrossingFeet = buf.readVarInt();
                lastThrowReturnedToFeet = buf.readVarInt();
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
                    lastThrowDistanceFeet,
                    corridorEntryFeet,
                    corridorEntryBearing,
                    waterGapStartFeet,
                    waterGapEndFeet,
                    hasWaterGap,
                    hasLastThrowStats,
                    lastThrowTotalDistanceFt,
                    lastThrowLateralDriftFt,
                    lastThrowStance,
                    lastThrowAngle,
                    lastThrowFlightTicks,
                    lastThrowPenaltyType,
                    lastThrowPenaltyStrokes,
                    lastThrowPenaltyReason,
                    lastThrowObCrossingFeet,
                    lastThrowReturnedToFeet
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
            buf.writeVarInt(corridorEntryFeet);
            buf.writeVarInt(corridorEntryBearing);
            buf.writeVarInt(waterGapStartFeet);
            buf.writeVarInt(waterGapEndFeet);
            buf.writeBoolean(hasWaterGap);
            buf.writeBoolean(hasLastThrowStats);
            if (hasLastThrowStats) {
                buf.writeDouble(lastThrowTotalDistanceFt);
                buf.writeDouble(lastThrowLateralDriftFt);
                buf.writeVarInt(lastThrowStance.ordinal());
                buf.writeVarInt(lastThrowAngle.ordinal());
                buf.writeVarInt(lastThrowFlightTicks);
                buf.writeVarInt(lastThrowPenaltyType.ordinal());
                buf.writeVarInt(lastThrowPenaltyStrokes);
                buf.writeString(lastThrowPenaltyReason);
                buf.writeVarInt(lastThrowObCrossingFeet);
                buf.writeVarInt(lastThrowReturnedToFeet);
            }
        }

        public static Payload inactive() {
            return new Payload(false, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, false, 0, 0, false, 0, 0, 0, "", 0, 0, 0, List.of(), List.of(), 0, 0, 0, 0, 0, false, false, 0.0, 0.0, ThrowStance.OVERHAND, ReleaseAngle.FLAT, 0, StrictPenaltyType.NONE, 0, "", 0, 0);
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
                int lastThrowDistanceFeet,
                int corridorEntryFeet,
                int corridorEntryBearing,
                int waterGapStartFeet,
                int waterGapEndFeet,
                boolean hasWaterGap,
                boolean hasLastThrowStats,
                double lastThrowTotalDistanceFt,
                double lastThrowLateralDriftFt,
                ThrowStance lastThrowStance,
                ReleaseAngle lastThrowAngle,
                int lastThrowFlightTicks,
                StrictPenaltyType lastThrowPenaltyType,
                int lastThrowPenaltyStrokes,
                String lastThrowPenaltyReason,
                int lastThrowObCrossingFeet,
                int lastThrowReturnedToFeet
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
                    Math.max(0, lastThrowDistanceFeet),
                    Math.max(0, corridorEntryFeet),
                    ((corridorEntryBearing % 360) + 360) % 360,
                    Math.max(0, waterGapStartFeet),
                    Math.max(0, waterGapEndFeet),
                    hasWaterGap,
                    hasLastThrowStats,
                    lastThrowTotalDistanceFt,
                    lastThrowLateralDriftFt,
                    lastThrowStance,
                    lastThrowAngle,
                    lastThrowFlightTicks,
                    lastThrowPenaltyType,
                    lastThrowPenaltyStrokes,
                    lastThrowPenaltyReason != null ? lastThrowPenaltyReason : "",
                    lastThrowObCrossingFeet,
                    lastThrowReturnedToFeet
            );
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}

