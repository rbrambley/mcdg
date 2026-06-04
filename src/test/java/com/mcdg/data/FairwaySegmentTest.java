package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FairwaySegmentTest {
    @Test
    void validFairwaySegment() {
        FairwaySegment fs = new FairwaySegment(0, 0, 100, 100, 5);
        assertEquals(0, fs.startX());
        assertEquals(0, fs.startZ());
        assertEquals(100, fs.endX());
        assertEquals(100, fs.endZ());
        assertEquals(5, fs.width());
    }

    @Test
    void minimumWidthIsTwo() {
        FairwaySegment fs = new FairwaySegment(0, 0, 10, 10, 2);
        assertEquals(2, fs.width());
    }

    @Test
    void widthOneThrows() {
        assertThrows(IllegalArgumentException.class, () -> new FairwaySegment(0, 0, 10, 10, 1));
    }

    @Test
    void widthZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> new FairwaySegment(0, 0, 10, 10, 0));
    }

    @Test
    void negativeWidthThrows() {
        assertThrows(IllegalArgumentException.class, () -> new FairwaySegment(0, 0, 10, 10, -3));
    }

    @Test
    void negativeCoordinatesAllowed() {
        FairwaySegment fs = new FairwaySegment(-10, -20, -5, -15, 4);
        assertEquals(-10, fs.startX());
        assertEquals(-20, fs.startZ());
        assertEquals(-5, fs.endX());
        assertEquals(-15, fs.endZ());
    }
}
