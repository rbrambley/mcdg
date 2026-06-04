package com.mcdg.world;

import static org.junit.jupiter.api.Assertions.*;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import java.util.EnumSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SeededCourseGeneratorTest {
    private final SeededCourseGenerator generator = new SeededCourseGenerator();

    @Test
    void generateReturnsCorrectHoleCount() {
        Course course = generator.generate(12345L, 9);
        assertEquals(9, course.holes().size());
    }

    @Test
    void generateSingleHole() {
        Course course = generator.generate(99L, 1);
        assertEquals(1, course.holes().size());
    }

    @Test
    void zeroHoleCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(1L, 0));
    }

    @Test
    void negativeHoleCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(1L, -1));
    }

    @Test
    void deterministicOutput() {
        long seed = 42424242L;
        Course first = generator.generate(seed, 9);
        Course second = generator.generate(seed, 9);

        assertEquals(first.name(), second.name());
        assertEquals(first.holes().size(), second.holes().size());

        for (int i = 0; i < first.holes().size(); i++) {
            Hole h1 = first.holes().get(i);
            Hole h2 = second.holes().get(i);
            assertEquals(h1.index(), h2.index());
            assertEquals(h1.par(), h2.par());
            assertEquals(h1.distanceFeet(), h2.distanceFeet());
            assertEquals(h1.tee(), h2.tee());
            assertEquals(h1.basket(), h2.basket());
            assertEquals(h1.signatureType(), h2.signatureType());
        }
    }

    @Test
    void differentSeedsProduceDifferentCourses() {
        Course a = generator.generate(1L, 9);
        Course b = generator.generate(2L, 9);
        // Extremely unlikely to produce identical courses with different seeds
        boolean anyDifferent = false;
        for (int i = 0; i < a.holes().size(); i++) {
            if (!a.holes().get(i).tee().equals(b.holes().get(i).tee())) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent);
    }

    @Test
    void courseHasName() {
        Course course = generator.generate(12345L, 9);
        assertNotNull(course.name());
        assertFalse(course.name().isBlank());
    }

    @Test
    void holeIndicesAreSequential() {
        Course course = generator.generate(55555L, 9);
        for (int i = 0; i < course.holes().size(); i++) {
            assertEquals(i + 1, course.holes().get(i).index());
        }
    }

    @Test
    void allHolesHaveValidPar() {
        Course course = generator.generate(77777L, 9);
        for (Hole hole : course.holes()) {
            assertTrue(hole.par() >= 3 && hole.par() <= 5,
                    "Par should be 3-5, got " + hole.par() + " for hole " + hole.index());
        }
    }

    @Test
    void allHolesHavePositiveDistance() {
        Course course = generator.generate(88888L, 9);
        for (Hole hole : course.holes()) {
            assertTrue(hole.distanceFeet() >= 180,
                    "Distance should be >= 180ft, got " + hole.distanceFeet());
        }
    }

    @Test
    void exactlyOneSignatureHole() {
        Course course = generator.generate(123456789L, 9);
        long signatureCount = course.holes().stream().filter(Hole::isSignature).count();
        assertEquals(1, signatureCount);
    }

    @Test
    void signatureTypeIsValid() {
        Course course = generator.generate(123456789L, 9);
        SignatureHoleType sigType = course.holes().stream()
                .filter(Hole::isSignature)
                .map(Hole::signatureType)
                .findFirst()
                .orElse(SignatureHoleType.NONE);

        assertTrue(EnumSet.of(
                SignatureHoleType.ISLAND_GREEN,
                SignatureHoleType.TUNNEL_GAP,
                SignatureHoleType.DOWNHILL_BOMBER
        ).contains(sigType));
    }

    @Test
    void par5CapAtMostOnePerCourse() {
        Random random = new Random(20260601L);
        for (int i = 0; i < 50; i++) {
            long seed = random.nextLong();
            Course course = generator.generate(seed, 9);
            long par5Count = course.holes().stream().filter(h -> h.par() >= 5).count();
            assertTrue(par5Count <= 1,
                    "Seed " + seed + " produced " + par5Count + " par-5 holes");
        }
    }

    @Test
    void allHolesHaveFairwaySegments() {
        Course course = generator.generate(44444L, 9);
        for (Hole hole : course.holes()) {
            assertFalse(hole.fairwaySegments().isEmpty(),
                    "Hole " + hole.index() + " should have fairway segments");
        }
    }

    @Test
    void courseImplementsInterface() {
        CourseGenerator gen = generator;
        Course course = gen.generate(1L, 3);
        assertNotNull(course);
    }
}
