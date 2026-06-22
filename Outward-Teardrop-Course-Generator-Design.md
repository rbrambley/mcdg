# Outward Teardrop Course Generator Design

**Status:** ✅ Implemented  
**Last Updated:** 2026-06-22

## Overview

Replace the spiral/compact course generator with a universal **outward cone + teardrop turnaround** design. All auto-built courses (resort surround + player builds) use this single generator. Manual `/mcdg createcourse` keeps `SeededCourseGenerator` unchanged.

## Implementation Status

- [x] Compact cone geometry adopted for resort surround courses (`baseLineDistance=25`, hub origin)
- [x] `buildresort` generates 3 surrounding courses with terrain-aware placement
- [x] Resort course async builder defers placement until player joins
- [x] Full outward teardrop turnaround (holes 4-6 return leg, holes 7-9 pull back toward base line)
- [x] Player `autocourse` menu build uses cone generator instead of `SeededCourseGenerator`
- [x] Dead code cleanup: `generateCompactCourse`, `generateHoleSpecsFromOrigin` removed
- [x] Unified `generateOutwardConeCourse` signature and implementation
- [x] `SeededCourseGenerator` retained for manual `/mcdg createcourse` and course name generation

---

## 1. `generateOutwardConeCourse` in `AutoCourseService`

### Method Signature

```java
public Course generateOutwardConeCourse(
    long seed,
    BlockPos origin,
    float facingYaw,
    int baseLineDistance,
    int baseLineWidth
)
```

### Parameters

| Parameter | Description | Resort Value | Player Value |
|-----------|-------------|--------------|--------------|
| `origin` | Resort center or player position | Resort center | Player position |
| `facingYaw` | Direction the cone opens | Cardinal (N/E/S/W) | `player.getYaw()` |
| `baseLineDistance` | Distance from origin to base line | 100-150 blocks | 25 blocks |
| `baseLineWidth` | Width of base line | 80 blocks | 80 blocks |

### Algorithm

1. Compute base line perpendicular to `facingYaw`, centered at `baseLineDistance` from `origin`.
2. Place **tee1** on base line center.
3. **Holes 1-3:** Progressive steps outward from base line, same general direction (60-200 ft).
4. **Holes 4-6:** Turnaround -- start angling back toward the base line area.
5. **Holes 7-9:** Return leg, progressively closer to base line.
6. **Hole 9:** Basket lands near base line, offset >= 30 blocks from tee1.
7. **Cone boundary:** +/- 30 degrees from `facingYaw`; any hole outside cone gets rejected and terrain is re-evaluated.
8. All positions returned as relative coordinates for `placeCourseIncrementally`.

### Visual (top-down, facing north)

```
    BASE LINE (80 blocks wide, north-south in this example)
    ---------------------------------------------------
              [T1]--------->[B1]              <-- tee1 on base line, closest to origin
                 \
                  \
                   [T2]--------->[B2]          <-- holes 1-3: march outward
                      \
                       \
                        [T3]--------->[B3]
                           \
                            \
                         (turnaround area, holes 4-6)
                            / \
                           /   \
                          /     \
                    [B5]<---[T5]   \
                     /              \
                    /         [T6]-->[B6]
                   /                \
                  /                  \
                 /                    [T7]--------->[B7]
                /                          \
               /                            \
              /                              [T8]--------->[B8]
             /                                    \
            /                                      [T9]--------->[B9]
           /                                              (near base line, offset >=30)
          V
    (cone widens south, bounded by +/-30 degree diagonals)
```

---

## 2. Update `buildresort`

Replace current `generateCompactCourse` call with `generateOutwardConeCourse`:
- `baseLineDistance = 100-150` blocks from resort center
- `facingYaw = cardinal direction (N/E/S/W)`, spaced 120 degrees apart
- `baseLineWidth = 80`
- `skipHub = true` (no central hub for resort courses)

---

## 3. Update Player `autocourse` (Menu Build)

Replace current `SeededCourseGenerator` + `placeCourseIncrementally` flow with `generateOutwardConeCourse`:
- `baseLineDistance = 25` blocks forward from player
- `facingYaw = player.getYaw()`
- `baseLineWidth = 80`
- `skipHub = false` (hub builds between player and tee1, behind the base line)

---

## 4. Cleanup -- Remove Dead Code

| Current | Action | Reason |
|---------|--------|--------|
| `generateCompactCourse` | **Replace** body with cone algorithm | Becomes `generateOutwardConeCourse` |
| `generateHoleSpecsFromOrigin` | **Remove** | Only called by old `generateCompactCourse`; dead after replacement |
| `SeededCourseGenerator` | **Keep** | Still used by `/mcdg createcourse` manual command |
| `placeCourseIncrementally` | **Keep** | Universal placement engine, used by all paths |

**End result:** One cone generator for all auto-builds. One grid generator for manual builds. One placement engine. No dead code.

---

## 5. Build, Test, Deploy

1. `./gradlew build`
2. Deploy to ATLauncher test instance
3. Test: `buildresort`, `autocourse` from menu, `removesurroundcourses`, stale session cleanup
