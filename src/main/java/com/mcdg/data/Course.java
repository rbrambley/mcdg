package com.mcdg.data;

import java.util.List;

public record Course(long seed, String name, List<Hole> holes) {
    public Course {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (holes == null || holes.isEmpty()) {
            throw new IllegalArgumentException("holes must not be empty");
        }

        holes = List.copyOf(holes);
    }
}
