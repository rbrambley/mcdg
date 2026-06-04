package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TeePointTest {
    @Test
    void storesCoordinates() {
        TeePoint tp = new TeePoint(10, 64, 20);
        assertEquals(10, tp.x());
        assertEquals(64, tp.y());
        assertEquals(20, tp.z());
    }

    @Test
    void negativeCoordinatesAllowed() {
        TeePoint tp = new TeePoint(-100, -50, -200);
        assertEquals(-100, tp.x());
        assertEquals(-50, tp.y());
        assertEquals(-200, tp.z());
    }

    @Test
    void equalityBasedOnCoordinates() {
        TeePoint a = new TeePoint(1, 2, 3);
        TeePoint b = new TeePoint(1, 2, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityWhenDifferent() {
        TeePoint a = new TeePoint(1, 2, 3);
        TeePoint b = new TeePoint(4, 5, 6);
        assertNotEquals(a, b);
    }
}
