package com.mcdg.client;

import java.util.List;

/**
 * Client-side snapshot of the current hole map data and round state.
 * Updated by HoleMapSync payloads; consumed by HoleMapScreen and overlays.
 */
public final class HoleMapState {

    public record FairwaySegment(int startX, int startZ, int endX, int endZ, int width) {}

    // --- static hole layout ---
    public final int holeIndex;
    public final int par;
    public final int distanceFeet;
    public final int teeX;
    public final int teeZ;
    public final int basketX;
    public final int basketZ;
    public final List<FairwaySegment> fairwaySegments;
    public final int corridorHalfWidth;
    public final int signatureTypeOrdinal;
    // --- hazard grid ---
    public final int hazardGridMinX;
    public final int hazardGridMinZ;
    public final int hazardGridWidth;
    public final int hazardGridHeight;
    public final byte[] hazardGridData;

    // --- dynamic round state ---
    public final int lieX;
    public final int lieZ;
    public final int headingYaw;
    public final int throwNumber;
    public final int totalStrokes;
    public final int cumulativeParDelta;
    public final int lastThrowDistanceFeet;
    public final int corridorEntryFeet;
    public final int corridorEntryBearing;
    public final int waterGapStartFeet;
    public final int waterGapEndFeet;
    public final boolean hasWaterGap;

    // --- last throw stats ---
    public final boolean hasLastThrowStats;
    public final double lastThrowTotalDistanceFt;
    public final double lastThrowLateralDriftFt;
    public final com.mcdg.game.ThrowStance lastThrowStance;
    public final com.mcdg.game.ReleaseAngle lastThrowAngle;
    public final int lastThrowFlightTicks;
    public final com.mcdg.game.StrictPenaltyType lastThrowPenaltyType;
    public final int lastThrowPenaltyStrokes;
    public final String lastThrowPenaltyReason;
    public final int lastThrowObCrossingFeet;
    public final int lastThrowReturnedToFeet;

    public HoleMapState(com.mcdg.net.HoleMapSync.Payload payload) {
        this.holeIndex = payload.holeIndex();
        this.par = payload.par();
        this.distanceFeet = payload.distanceFeet();
        this.teeX = payload.teeX();
        this.teeZ = payload.teeZ();
        this.basketX = payload.basketX();
        this.basketZ = payload.basketZ();
        this.fairwaySegments = payload.fairwaySegments() != null
                ? payload.fairwaySegments().stream()
                        .map(s -> new FairwaySegment(s.startX(), s.startZ(), s.endX(), s.endZ(), s.width()))
                        .toList()
                : List.of();
        this.corridorHalfWidth = payload.corridorHalfWidth();
        this.signatureTypeOrdinal = payload.signatureTypeOrdinal();
        this.hazardGridMinX = payload.hazardGridMinX();
        this.hazardGridMinZ = payload.hazardGridMinZ();
        this.hazardGridWidth = payload.hazardGridWidth();
        this.hazardGridHeight = payload.hazardGridHeight();
        this.hazardGridData = payload.hazardGridData() != null ? payload.hazardGridData() : new byte[0];
        this.lieX = payload.lieX();
        this.lieZ = payload.lieZ();
        this.headingYaw = payload.headingYaw();
        this.throwNumber = payload.throwNumber();
        this.totalStrokes = payload.totalStrokes();
        this.cumulativeParDelta = payload.cumulativeParDelta();
        this.lastThrowDistanceFeet = payload.lastThrowDistanceFeet();
        this.corridorEntryFeet = payload.corridorEntryFeet();
        this.corridorEntryBearing = payload.corridorEntryBearing();
        this.waterGapStartFeet = payload.waterGapStartFeet();
        this.waterGapEndFeet = payload.waterGapEndFeet();
        this.hasWaterGap = payload.hasWaterGap();
        this.hasLastThrowStats = payload.hasLastThrowStats();
        this.lastThrowTotalDistanceFt = payload.lastThrowTotalDistanceFt();
        this.lastThrowLateralDriftFt = payload.lastThrowLateralDriftFt();
        this.lastThrowStance = payload.lastThrowStance();
        this.lastThrowAngle = payload.lastThrowAngle();
        this.lastThrowFlightTicks = payload.lastThrowFlightTicks();
        this.lastThrowPenaltyType = payload.lastThrowPenaltyType();
        this.lastThrowPenaltyStrokes = payload.lastThrowPenaltyStrokes();
        this.lastThrowPenaltyReason = payload.lastThrowPenaltyReason();
        this.lastThrowObCrossingFeet = payload.lastThrowObCrossingFeet();
        this.lastThrowReturnedToFeet = payload.lastThrowReturnedToFeet();
    }

    public boolean isActive() {
        return holeIndex > 0;
    }
}
