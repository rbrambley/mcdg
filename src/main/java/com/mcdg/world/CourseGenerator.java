package com.mcdg.world;

import com.mcdg.data.Course;

public interface CourseGenerator {
    Course generate(long seed, int holeCount);
}
