package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public final class RegressionCheckRunner {
    private static final int HOLE_COUNT = 9;

    private RegressionCheckRunner() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "quick";
        SeededCourseGenerator generator = new SeededCourseGenerator();

        if ("quick".equalsIgnoreCase(mode)) {
            runQuickChecks(generator);
            return;
        }

        if ("smoke".equalsIgnoreCase(mode)) {
            runSmokeChecks(generator);
            return;
        }

        if ("full".equalsIgnoreCase(mode)) {
            runQuickChecks(generator);
            runSmokeChecks(generator);
            return;
        }

        throw new RuntimeException("Unknown regression mode: " + mode);
    }

    private static void runQuickChecks(SeededCourseGenerator generator) {
        Course one = generator.generate(123456789L, HOLE_COUNT);
        assertExactlyOneSignature(one, "quick-seed-1");

        SignatureHoleType signatureType = one.holes().stream()
                .filter(Hole::isSignature)
                .map(Hole::signatureType)
                .findFirst()
                .orElse(SignatureHoleType.NONE);

        if (!EnumSet.of(
                SignatureHoleType.ISLAND_GREEN,
                SignatureHoleType.TUNNEL_GAP,
                SignatureHoleType.DOWNHILL_BOMBER
        ).contains(signatureType)) {
            throw new RuntimeException("Unexpected signature type in quick check: " + signatureType);
        }

        long seed = 42424242L;
        Course first = generator.generate(seed, HOLE_COUNT);
        Course second = generator.generate(seed, HOLE_COUNT);

        assertEquals(first.name(), second.name(), "Course name must be deterministic.");
        assertEquals(first.holes().size(), second.holes().size(), "Hole count must be deterministic.");

        List<Hole> a = first.holes();
        List<Hole> b = second.holes();
        for (int i = 0; i < a.size(); i++) {
            Hole h1 = a.get(i);
            Hole h2 = b.get(i);
            assertEquals(h1.index(), h2.index(), "Hole index must be deterministic.");
            assertEquals(h1.par(), h2.par(), "Hole par must be deterministic.");
            assertEquals(h1.distanceFeet(), h2.distanceFeet(), "Hole distance must be deterministic.");
            assertEquals(h1.tee(), h2.tee(), "Tee coordinates must be deterministic.");
            assertEquals(h1.basket(), h2.basket(), "Basket coordinates must be deterministic.");
            assertEquals(h1.signatureType(), h2.signatureType(), "Signature assignment must be deterministic.");
        }

        System.out.println("Quick regression checks passed.");
    }

    private static void runSmokeChecks(SeededCourseGenerator generator) {
        Random random = new Random(20260528L);
        for (int i = 0; i < 40; i++) {
            long seed = random.nextLong();
            Course course = generator.generate(seed, HOLE_COUNT);
            assertEquals(HOLE_COUNT, course.holes().size(), "Smoke check hole count mismatch for seed " + seed);
            assertExactlyOneSignature(course, "smoke-seed-" + seed);
        }

        System.out.println("Smoke regression checks passed.");
    }

    private static void assertExactlyOneSignature(Course course, String context) {
        long signatureCount = course.holes().stream().filter(Hole::isSignature).count();
        if (signatureCount != 1L) {
            throw new RuntimeException("Expected exactly one signature hole for " + context + ", got " + signatureCount);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new RuntimeException(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
