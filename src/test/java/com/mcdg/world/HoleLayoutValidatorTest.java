package com.mcdg.world;

import static org.junit.jupiter.api.Assertions.*;

import com.mcdg.data.BasketPoint;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.data.TeePoint;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class HoleLayoutValidatorTest {
    private final HoleLayoutValidator validator = new HoleLayoutValidator();

    private Hole holeAt(int teeX, int teeZ, int basketX, int basketZ) {
        return new Hole(
                1, 3, 300,
                new TeePoint(teeX, 64, teeZ),
                new BasketPoint(basketX, 64, basketZ, 1),
                List.of(new FairwaySegment(teeX, teeZ, basketX, basketZ, 4)),
                SignatureHoleType.NONE
        );
    }

    @Test
    void distanceValidInRange() {
        assertTrue(validator.isDistanceValid(300, 180, 1200));
    }

    @Test
    void distanceValidAtMinBoundary() {
        assertTrue(validator.isDistanceValid(180, 180, 1200));
    }

    @Test
    void distanceValidAtMaxBoundary() {
        assertTrue(validator.isDistanceValid(1200, 180, 1200));
    }

    @Test
    void distanceInvalidBelowMin() {
        assertFalse(validator.isDistanceValid(179, 180, 1200));
    }

    @Test
    void distanceInvalidAboveMax() {
        assertFalse(validator.isDistanceValid(1201, 180, 1200));
    }

    @Test
    void nonOverlappingWithEmptyList() {
        Hole candidate = holeAt(0, 0, 100, 100);
        assertTrue(validator.isNonOverlapping(candidate, Collections.emptyList()));
    }

    @Test
    void nonOverlappingFarApart() {
        Hole existing = holeAt(0, 0, 100, 0);
        Hole candidate = holeAt(500, 500, 600, 500);
        assertTrue(validator.isNonOverlapping(candidate, List.of(existing)));
    }

    @Test
    void overlappingTeesTooClose() {
        Hole existing = holeAt(0, 0, 200, 200);
        Hole candidate = holeAt(10, 10, 500, 500);
        assertFalse(validator.isNonOverlapping(candidate, List.of(existing)));
    }

    @Test
    void overlappingBasketsTooClose() {
        Hole existing = holeAt(0, 0, 100, 100);
        Hole candidate = holeAt(500, 500, 110, 110);
        assertFalse(validator.isNonOverlapping(candidate, List.of(existing)));
    }

    @Test
    void teesExactlyAtMinSpacingIsValid() {
        // MIN_HOLE_SPACING_BLOCKS = 48; distance must be >= 48
        Hole existing = holeAt(0, 0, 200, 200);
        Hole candidate = holeAt(48, 0, 400, 400);
        assertTrue(validator.isNonOverlapping(candidate, List.of(existing)));
    }

    @Test
    void teesJustBelowMinSpacingIsInvalid() {
        Hole existing = holeAt(0, 0, 200, 200);
        Hole candidate = holeAt(47, 0, 400, 400);
        assertFalse(validator.isNonOverlapping(candidate, List.of(existing)));
    }

    @Test
    void distanceFeetFromBlocks() {
        int feet = validator.distanceFeetFromBlocks(0, 0, 100, 0);
        assertEquals(300, feet);
    }

    @Test
    void distanceFeetFromBlocksDiagonal() {
        int feet = validator.distanceFeetFromBlocks(0, 0, 3, 4);
        // hypot(3,4) = 5.0, * 3 = 15
        assertEquals(15, feet);
    }

    @Test
    void distanceFeetFromBlocksSamePoint() {
        assertEquals(0, validator.distanceFeetFromBlocks(0, 0, 0, 0));
    }
}
