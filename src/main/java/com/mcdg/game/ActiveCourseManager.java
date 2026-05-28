package com.mcdg.game;

import com.mcdg.data.Course;
import java.util.Optional;

public final class ActiveCourseManager {
    private volatile Course activeCourse;
    private volatile PlacedCourseState placedCourseState;
    private volatile boolean roundActive;
    private volatile boolean persistentPlacedCourse;
    private volatile boolean legacyPracticeSnapshot;

    public void setActiveCourse(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("course is required");
        }
        this.activeCourse = course;
    }

    public Optional<Course> getActiveCourse() {
        return Optional.ofNullable(activeCourse);
    }

    public void setPlacedCourseState(PlacedCourseState placedCourseState) {
        this.placedCourseState = placedCourseState;
    }

    public Optional<PlacedCourseState> getPlacedCourseState() {
        return Optional.ofNullable(placedCourseState);
    }

    public void clearPlacedCourseState() {
        this.placedCourseState = null;
        this.persistentPlacedCourse = false;
        this.legacyPracticeSnapshot = false;
    }

    public boolean isPersistentPlacedCourse() {
        return persistentPlacedCourse;
    }

    public void setPersistentPlacedCourse(boolean persistentPlacedCourse) {
        this.persistentPlacedCourse = persistentPlacedCourse;
    }

    public boolean isLegacyPracticeSnapshot() {
        return legacyPracticeSnapshot;
    }

    public void setLegacyPracticeSnapshot(boolean legacyPracticeSnapshot) {
        this.legacyPracticeSnapshot = legacyPracticeSnapshot;
    }

    public boolean isRoundActive() {
        return roundActive;
    }

    public void setRoundActive(boolean roundActive) {
        this.roundActive = roundActive;
    }

    public void clear() {
        this.activeCourse = null;
        this.placedCourseState = null;
        this.roundActive = false;
        this.persistentPlacedCourse = false;
        this.legacyPracticeSnapshot = false;
    }
}
