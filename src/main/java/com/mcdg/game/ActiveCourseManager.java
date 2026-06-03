package com.mcdg.game;

import com.mcdg.data.Course;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

public final class ActiveCourseManager {
    private volatile Course activeCourse;
    private volatile PlacedCourseState placedCourseState;
    private volatile boolean roundActive;
    private volatile boolean persistentPlacedCourse;
    private volatile boolean legacyPracticeSnapshot;
    private final Set<UUID> activeParticipantIds = ConcurrentHashMap.newKeySet();

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

    public void setActiveParticipantIds(Iterable<UUID> participantIds) {
        activeParticipantIds.clear();
        if (participantIds == null) {
            return;
        }
        for (UUID participantId : participantIds) {
            if (participantId != null) {
                activeParticipantIds.add(participantId);
            }
        }
    }

    public Set<UUID> getActiveParticipantIds() {
        return Set.copyOf(activeParticipantIds);
    }

    public void addActiveParticipantIds(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return;
        }
        for (UUID participantId : participantIds) {
            if (participantId != null) {
                activeParticipantIds.add(participantId);
            }
        }
    }

    public void addActiveParticipantId(UUID participantId) {
        if (participantId != null) {
            activeParticipantIds.add(participantId);
        }
    }

    public void removeActiveParticipantId(UUID participantId) {
        if (participantId != null) {
            activeParticipantIds.remove(participantId);
        }
    }

    public void removeActiveParticipantIds(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return;
        }
        for (UUID participantId : participantIds) {
            if (participantId != null) {
                activeParticipantIds.remove(participantId);
            }
        }
    }

    public void clearActiveParticipantIds() {
        activeParticipantIds.clear();
    }

    public void clear() {
        this.activeCourse = null;
        this.placedCourseState = null;
        this.roundActive = false;
        this.persistentPlacedCourse = false;
        this.legacyPracticeSnapshot = false;
        this.activeParticipantIds.clear();
    }
}
