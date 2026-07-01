package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.game.StrictPenaltyType;
import com.mcdg.game.ThrowStance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client payload for the schematic hole map screen.
 * Replaces the old HoleMiniMapSync (terrain-sampling minimap) and HoleTeeMapManager
 * (inventory treasure map) with a lightweight schematic layout.
 */
public final class HoleMapSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "hole_map");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private HoleMapSync() {
    }

    private static int clampOrdinal(int ordinal, int enumLength) {
        if (ordinal < 0 || ordinal >= enumLength) {
            return 0;
        }
        return ordinal;
    }

    public record FairwaySegmentEntry(int startX, int startZ, int endX, int endZ, int width) {
    }

    public record Payload(
            boolean active,
            // --- static hole layout ---
            int holeIndex,
            int par,
            int distanceFeet,
            int teeX,
            int teeZ,
            int basketX,
            int basketZ,
            List<FairwaySegmentEntry> fairwaySegments,
            int corridorHalfWidth,
            int signatureTypeOrdinal,
            // --- hazard grid ---
            int hazardGridMinX,
            int hazardGridMinZ,
            int hazardGridWidth,
            int hazardGridHeight,
            byte[] hazardGridData,
            // --- dynamic round state ---
            int lieX,
            int lieZ,
            int headingYaw,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            int lastThrowDistanceFeet,
            int corridorEntryFeet,
            int corridorEntryBearing,
            int waterGapStartFeet,
            int waterGapEndFeet,
            boolean hasWaterGap,
            // --- course waypoint (for WaypointManager) ---
            String courseWaypointName,
            int courseWaypointX,
            int courseWaypointZ,
            // --- last throw stats ---
            boolean hasLastThrowStats,
            double lastThrowTotalDistanceFt,
            double lastThrowLateralDriftFt,
            double lastThrowApexHeightFt,
            ThrowStance lastThrowStance,
            ReleaseAngle lastThrowAngle,
            int lastThrowFlightTicks,
            StrictPenaltyType lastThrowPenaltyType,
            int lastThrowPenaltyStrokes,
            String lastThrowPenaltyReason,
            int lastThrowObCrossingFeet,
            int lastThrowReturnedToFeet,
            // --- ruleset info ---
            String rulesetName,
            String presetName
    ) implements CustomPayload {

        public static Payload read(RegistryByteBuf buf) {
            boolean active = buf.readBoolean();
            if (!active) {
                return inactive();
            }

            int holeIndex = buf.readVarInt();
            int par = buf.readVarInt();
            int distanceFeet = buf.readVarInt();
            int teeX = buf.readVarInt();
            int teeZ = buf.readVarInt();
            int basketX = buf.readVarInt();
            int basketZ = buf.readVarInt();

            int segCount = Math.max(0, buf.readVarInt());
            List<FairwaySegmentEntry> segments = new ArrayList<>(segCount);
            for (int i = 0; i < segCount; i++) {
                segments.add(new FairwaySegmentEntry(
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt()
                ));
            }

            int corridorHalfWidth = buf.readVarInt();
            int signatureTypeOrdinal = buf.readVarInt();

            int hazardGridMinX = buf.readVarInt();
            int hazardGridMinZ = buf.readVarInt();
            int hazardGridWidth = Math.max(0, buf.readVarInt());
            int hazardGridHeight = Math.max(0, buf.readVarInt());
            int hazardGridSize = hazardGridWidth * hazardGridHeight;
            byte[] hazardGridData = new byte[hazardGridSize];
            if (hazardGridSize > 0) {
                buf.readBytes(hazardGridData);
            }

            int lieX = buf.readVarInt();
            int lieZ = buf.readVarInt();
            int headingYaw = buf.readVarInt();
            int throwNumber = buf.readVarInt();
            int totalStrokes = buf.readVarInt();
            int cumulativeParDelta = buf.readVarInt();
            int lastThrowDistanceFeet = buf.readVarInt();
            int corridorEntryFeet = buf.readVarInt();
            int corridorEntryBearing = buf.readVarInt();
            int waterGapStartFeet = buf.readVarInt();
            int waterGapEndFeet = buf.readVarInt();
            boolean hasWaterGap = buf.readBoolean();

            String courseWaypointName = buf.readString(64);
            int courseWaypointX = buf.readVarInt();
            int courseWaypointZ = buf.readVarInt();

            boolean hasLastThrowStats = buf.readBoolean();
            double lastThrowTotalDistanceFt = 0.0;
            double lastThrowLateralDriftFt = 0.0;
            double lastThrowApexHeightFt = 0.0;
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
                lastThrowApexHeightFt = buf.readDouble();
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

            String rulesetName = buf.readString(32);
            String presetName = buf.readString(32);

            return new Payload(
                    true,
                    holeIndex, par, distanceFeet,
                    teeX, teeZ, basketX, basketZ,
                    segments, corridorHalfWidth, signatureTypeOrdinal,
                    hazardGridMinX, hazardGridMinZ, hazardGridWidth, hazardGridHeight, hazardGridData,
                    lieX, lieZ, headingYaw,
                    throwNumber, totalStrokes, cumulativeParDelta,
                    lastThrowDistanceFeet,
                    corridorEntryFeet, corridorEntryBearing,
                    waterGapStartFeet, waterGapEndFeet, hasWaterGap,
                    courseWaypointName, courseWaypointX, courseWaypointZ,
                    hasLastThrowStats,
                    lastThrowTotalDistanceFt, lastThrowLateralDriftFt, lastThrowApexHeightFt,
                    lastThrowStance, lastThrowAngle, lastThrowFlightTicks,
                    lastThrowPenaltyType, lastThrowPenaltyStrokes, lastThrowPenaltyReason,
                    lastThrowObCrossingFeet, lastThrowReturnedToFeet,
                    rulesetName, presetName
            );
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            if (!active) {
                return;
            }

            buf.writeVarInt(holeIndex);
            buf.writeVarInt(par);
            buf.writeVarInt(distanceFeet);
            buf.writeVarInt(teeX);
            buf.writeVarInt(teeZ);
            buf.writeVarInt(basketX);
            buf.writeVarInt(basketZ);

            int segCount = fairwaySegments != null ? fairwaySegments.size() : 0;
            buf.writeVarInt(segCount);
            for (FairwaySegmentEntry seg : (fairwaySegments != null ? fairwaySegments : List.<FairwaySegmentEntry>of())) {
                buf.writeVarInt(seg.startX());
                buf.writeVarInt(seg.startZ());
                buf.writeVarInt(seg.endX());
                buf.writeVarInt(seg.endZ());
                buf.writeVarInt(seg.width());
            }

            buf.writeVarInt(corridorHalfWidth);
            buf.writeVarInt(clampOrdinal(signatureTypeOrdinal, SignatureHoleType.values().length));

            buf.writeVarInt(hazardGridMinX);
            buf.writeVarInt(hazardGridMinZ);
            int expectedSize = hazardGridWidth * hazardGridHeight;
            if (hazardGridData != null && hazardGridData.length == expectedSize && expectedSize > 0) {
                buf.writeVarInt(hazardGridWidth);
                buf.writeVarInt(hazardGridHeight);
                buf.writeBytes(hazardGridData);
            } else {
                buf.writeVarInt(0);
                buf.writeVarInt(0);
            }

            buf.writeVarInt(lieX);
            buf.writeVarInt(lieZ);
            buf.writeVarInt(headingYaw);
            buf.writeVarInt(throwNumber);
            buf.writeVarInt(totalStrokes);
            buf.writeVarInt(cumulativeParDelta);
            buf.writeVarInt(lastThrowDistanceFeet);
            buf.writeVarInt(corridorEntryFeet);
            buf.writeVarInt(corridorEntryBearing);
            buf.writeVarInt(waterGapStartFeet);
            buf.writeVarInt(waterGapEndFeet);
            buf.writeBoolean(hasWaterGap);

            buf.writeString(courseWaypointName, 64);
            buf.writeVarInt(courseWaypointX);
            buf.writeVarInt(courseWaypointZ);

            buf.writeBoolean(hasLastThrowStats);
            if (hasLastThrowStats) {
                buf.writeDouble(lastThrowTotalDistanceFt);
                buf.writeDouble(lastThrowLateralDriftFt);
                buf.writeDouble(lastThrowApexHeightFt);
                buf.writeVarInt(clampOrdinal(lastThrowStance.ordinal(), ThrowStance.values().length));
                buf.writeVarInt(clampOrdinal(lastThrowAngle.ordinal(), ReleaseAngle.values().length));
                buf.writeVarInt(lastThrowFlightTicks);
                buf.writeVarInt(clampOrdinal(lastThrowPenaltyType.ordinal(), StrictPenaltyType.values().length));
                buf.writeVarInt(lastThrowPenaltyStrokes);
                buf.writeString(lastThrowPenaltyReason, 64);
                buf.writeVarInt(lastThrowObCrossingFeet);
                buf.writeVarInt(lastThrowReturnedToFeet);
            }

            buf.writeString(rulesetName != null ? rulesetName : "", 32);
            buf.writeString(presetName != null ? presetName : "", 32);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static Payload inactive() {
            return new Payload(
                    false,
                    0, 0, 0,
                    0, 0, 0, 0,
                    java.util.Collections.<FairwaySegmentEntry>emptyList(), 0, 0,
                    0, 0, 0, 0, new byte[0],
                    0, 0, 0,
                    0, 0, 0,
                    0,
                    0, 0,
                    0, 0, false,
                    "", 0, 0,
                    false,
                    0.0, 0.0, 0.0,
                    ThrowStance.OVERHAND, ReleaseAngle.FLAT, 0,
                    StrictPenaltyType.NONE, 0, "",
                    0, 0,
                    "", ""
            );
        }
    }
}
