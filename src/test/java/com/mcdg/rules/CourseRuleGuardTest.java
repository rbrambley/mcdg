package com.mcdg.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CourseRuleGuardTest {
    private final CourseRuleGuard guard = new CourseRuleGuard();

    @Test
    void buildBreakIsBlocked() {
        assertTrue(guard.isBuildBreakBlocked());
    }

    @Test
    void pvpIsBlocked() {
        assertTrue(guard.isPvpBlocked());
    }
}
