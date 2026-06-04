package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BasketPointTest {
    @Test
    void validBasketPoint() {
        BasketPoint bp = new BasketPoint(10, 64, 20, 2);
        assertEquals(10, bp.x());
        assertEquals(64, bp.y());
        assertEquals(20, bp.z());
        assertEquals(2, bp.basketHeight());
    }

    @Test
    void minimumBasketHeightIsOne() {
        BasketPoint bp = new BasketPoint(0, 0, 0, 1);
        assertEquals(1, bp.basketHeight());
    }

    @Test
    void zeroBasketHeightThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BasketPoint(0, 0, 0, 0));
    }

    @Test
    void negativeBasketHeightThrows() {
        assertThrows(IllegalArgumentException.class, () -> new BasketPoint(0, 0, 0, -1));
    }

    @Test
    void negativeCoordinatesAllowed() {
        BasketPoint bp = new BasketPoint(-100, -50, -200, 1);
        assertEquals(-100, bp.x());
        assertEquals(-50, bp.y());
        assertEquals(-200, bp.z());
    }
}
