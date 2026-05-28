package com.mcdg.data;

import java.util.List;

public record Hole(
        int index,
        int par,
        int distanceFeet,
        TeePoint tee,
        BasketPoint basket,
        List<FairwaySegment> fairwaySegments,
        SignatureHoleType signatureType
) {
    public Hole {
        if (index < 1) {
            throw new IllegalArgumentException("index must be >= 1");
        }
        if (par < 2) {
            throw new IllegalArgumentException("par must be >= 2");
        }
        if (distanceFeet < 1) {
            throw new IllegalArgumentException("distanceFeet must be >= 1");
        }
        if (tee == null || basket == null || fairwaySegments == null) {
            throw new IllegalArgumentException("tee, basket, and fairwaySegments are required");
        }

        fairwaySegments = List.copyOf(fairwaySegments);
        if (signatureType == null) {
            signatureType = SignatureHoleType.NONE;
        }
    }

    public boolean isSignature() {
        return signatureType != SignatureHoleType.NONE;
    }
}
