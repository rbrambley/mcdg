package com.mcdg.game;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the outward teardrop auto-course generator.
 * Verifies hole distances and par distribution after balancing changes.
 */
public class AutoCourseServiceTest {

    private static AutoCourseService createService() {
        return new AutoCourseService(null, null, null, null, null);
    }

    @Test
    @DisplayName("Generated courses should balance distances: first 8 holes average ~350 ft and hole 9 stays under 2500 ft")
    public void testDistanceBalancing() {
        AutoCourseService service = createService();
        Random random = new Random(20260625L);
        int sampleCount = 100;
        int holesUnder150 = 0;
        int holesOver2500 = 0;
        long firstEightTotal = 0;
        long firstEightHoles = 0;
        long hole9Total = 0;

        for (int i = 0; i < sampleCount; i++) {
            long seed = random.nextLong();
            Course course = service.generateOutwardConeCourse(seed, new BlockPos(0, 64, 0), 0.0f, 25, 80);

            for (Hole hole : course.holes()) {
                int dist = hole.distanceFeet();
                if (dist < 150) {
                    holesUnder150++;
                }
                if (dist > 2500) {
                    holesOver2500++;
                }

                if (hole.number() < 9) {
                    firstEightTotal += dist;
                    firstEightHoles++;
                } else {
                    hole9Total += dist;
                }
            }
        }

        double firstEightAverage = (double) firstEightTotal / firstEightHoles;
        double hole9Average = (double) hole9Total / sampleCount;

        assertTrue(holesUnder150 == 0,
                "Expected no holes under 150 ft, found " + holesUnder150);
        assertTrue(holesOver2500 == 0,
                "Expected no holes over 2500 ft, found " + holesOver2500);
        assertTrue(firstEightAverage >= 300 && firstEightAverage <= 400,
                "Expected first 8 holes to average 300-400 ft, got " + firstEightAverage);
        assertTrue(hole9Average >= 900 && hole9Average <= 2500,
                "Expected hole 9 average between 900 and 2500 ft, got " + hole9Average);
    }
}
