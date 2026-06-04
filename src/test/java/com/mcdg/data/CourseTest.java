package com.mcdg.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CourseTest {
    private static final TeePoint TEE = new TeePoint(0, 64, 0);
    private static final BasketPoint BASKET = new BasketPoint(100, 64, 100, 1);
    private static final FairwaySegment FAIRWAY = new FairwaySegment(0, 0, 100, 100, 4);
    private static final Hole HOLE = new Hole(1, 3, 300, TEE, BASKET, List.of(FAIRWAY), SignatureHoleType.NONE);

    @Test
    void validCourse() {
        Course course = new Course(42L, "Test Course", List.of(HOLE));
        assertEquals(42L, course.seed());
        assertEquals("Test Course", course.name());
        assertEquals(1, course.holes().size());
    }

    @Test
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Course(1L, null, List.of(HOLE)));
    }

    @Test
    void blankNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Course(1L, "  ", List.of(HOLE)));
    }

    @Test
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Course(1L, "", List.of(HOLE)));
    }

    @Test
    void nullHolesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Course(1L, "Test", null));
    }

    @Test
    void emptyHolesThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Course(1L, "Test", Collections.emptyList()));
    }

    @Test
    void holesListIsDefensivelyCopied() {
        var mutableHoles = new java.util.ArrayList<>(List.of(HOLE));
        Course course = new Course(1L, "Test", mutableHoles);
        assertThrows(UnsupportedOperationException.class, () -> course.holes().add(HOLE));
    }
}
