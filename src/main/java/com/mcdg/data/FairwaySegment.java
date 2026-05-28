package com.mcdg.data;

public record FairwaySegment(int startX, int startZ, int endX, int endZ, int width) {
    public FairwaySegment {
        if (width < 2) {
            throw new IllegalArgumentException("width must be >= 2");
        }
    }
}
