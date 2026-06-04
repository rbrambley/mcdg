package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HoleTest {
    private static final TeePoint TEE = new TeePoint(0, 64, 0);
    private static final BasketPoint BASKET = new BasketPoint(100, 64, 100, 1);
    private static final FairwaySegment FAIRWAY = new FairwaySegment(0, 0, 100, 100, 4);

    @Test
    void validHole() {
        Hole hole = new Hole(1, 3, 300, TEE, BASKET, List.of(FAIRWAY), SignatureHoleType.NONE);
        assertEquals(1, hole.index());
        assertEquals(3, hole.par());
        assertEquals(300, hole.distanceFeet());
        assertSame(TEE, hole.tee());
        assertSame(BASKET, hole.basket());
        assertEquals(SignatureHoleType.NONE, hole.signatureType());
    }

    @Test
    void nullSignatureTypeDefaultsToNone() {
        Hole hole = new Hole(1, 3, 300, TEE, BASKET, List.of(FAIRWAY), null);
        assertEquals(SignatureHoleType.NONE, hole.signatureType());
    }

    @Test
    void zeroIndexThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(0, 3, 300, TEE, BASKET, List.of(FAIRWAY), null));
    }

    @Test
    void negativeIndexThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(-1, 3, 300, TEE, BASKET, List.of(FAIRWAY), null));
    }

    @Test
    void parBelowTwoThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(1, 1, 300, TEE, BASKET, List.of(FAIRWAY), null));
    }

    @Test
    void zeroDistanceThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(1, 3, 0, TEE, BASKET, List.of(FAIRWAY), null));
    }

    @Test
    void nullTeeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(1, 3, 300, null, BASKET, List.of(FAIRWAY), null));
    }

    @Test
    void nullBasketThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(1, 3, 300, TEE, null, List.of(FAIRWAY), null));
    }

    @Test
    void nullFairwaySegmentsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new Hole(1, 3, 300, TEE, BASKET, null, null));
    }

    @Test
    void isSignatureReturnsTrueForNonNone() {
        Hole hole = new Hole(1, 3, 300, TEE, BASKET, List.of(FAIRWAY), SignatureHoleType.ISLAND_GREEN);
        assertTrue(hole.isSignature());
    }

    @Test
    void isSignatureReturnsFalseForNone() {
        Hole hole = new Hole(1, 3, 300, TEE, BASKET, List.of(FAIRWAY), SignatureHoleType.NONE);
        assertFalse(hole.isSignature());
    }

    @Test
    void fairwaySegmentsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of(FAIRWAY));
        Hole hole = new Hole(1, 3, 300, TEE, BASKET, mutable, null);
        assertThrows(UnsupportedOperationException.class, () -> hole.fairwaySegments().add(FAIRWAY));
    }
}
