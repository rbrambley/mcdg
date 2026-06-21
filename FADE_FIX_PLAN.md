# Fade / Hyzer / Anhyzer Fix Plan

## Status: ✅ COMPLETED (2026-06-17)

**Implementation Date:** 2026-06-17
**Key Commits:**
- Part of Phase 1.1 in Multi-Phase Implementation Plan
- Fixed perpendicular direction vector mismatch in TrajectoryCalculator
- Switched to time-based fade curve
- Applied identical fixes to DiscFlightSimulator
- Added physics-based drift tests
- Validated with TrajectoryCalculatorTest

## Problem Statement (Historical)

Backhand and forehand throws always report **0 ft fade** with no visible lateral curve, regardless of release angle (hyzer/flat/anhyzer). Players expect:
- **BACKHAND**: natural fade to the **left** (negative drift)
- **FOREHAND**: natural fade to the **right** (positive drift)
- **HYZER**: stronger fade in the natural direction
- **ANHYZER**: fade opposite to natural direction
- **FLAT**: moderate natural fade
- **OVERHAND**: no glide, no fade (straight drop)

## Root Cause Analysis

### Bug 1: Perpendicular Direction Vector Mismatch

**File:** `src/main/java/com/mcdg/game/TrajectoryCalculator.java` (lines 124–126)

The curve applies lateral velocity along:
```java
double leftX = Math.sin(yawRad);
double leftZ = -Math.cos(yawRad);
```

But `calculateLateralDrift` measures displacement along a perpendicular axis:
```java
double aimX = -Math.sin(yawRad);
double aimZ = Math.cos(yawRad);
double lateralRightX = aimZ;        // = cos(yawRad)
double lateralRightZ = -aimX;       // = sin(yawRad)
```

These two vectors are **perpendicular to each other**, meaning the lateral nudge applied during flight does not register in the drift measurement. The disc curves, but the drift calculation reports 0.

### Bug 2: Velocity-Based Fade Factor Never Activates

**File:** `src/main/java/com/mcdg/game/TrajectoryCalculator.java` (lines 114–119)

```java
double curveFactor = 0.0;
if (initialSpeed > 0 && currentSpeed < initialSpeed) {
    curveFactor = 1.0 - (currentSpeed / initialSpeed);
}
```

The disc lands shortly after the glide phase ends (within a few ticks of `glideProgress >= 1.0`). During this short window, `currentSpeed` never drops significantly below `initialSpeed`. As a result:
- `curveFactor` stays near **0** for the entire flight
- `curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor ≈ 0`
- No lateral velocity is added, so no curve occurs

## Proposed Fixes

### Fix 1: Align Perpendicular Direction Vectors

Use the same perpendicular axis for both curve application and drift measurement.

Since `calculateLateralDrift` uses:
```
aimX = -sin(yawRad)  (forward direction)
aimZ =  cos(yawRad)
rightX =  cos(yawRad)  (right perpendicular)
rightZ =  sin(yawRad)
```

The **left** perpendicular (for fade) should be:
```
leftX = -cos(yawRad)
leftZ = -sin(yawRad)
```

### Fix 2: Switch to Time-Based Fade Curve

Replace velocity-dependent fade with time-based fade that ramps up during the latter portion of the glide phase:

```java
double curveFactor;
if (!hasGlide) {
    curveFactor = 0.0;
} else {
    int fadeStartTick = (int) Math.round(glideTicks * 0.6);
    if (tick < fadeStartTick) {
        curveFactor = 0.0;
    } else if (tick >= glideTicks) {
        curveFactor = 1.0;
    } else {
        curveFactor = (double) (tick - fadeStartTick) / (glideTicks - fadeStartTick);
    }
}
```

This ensures:
- **0–60% of glide:** no fade (stable flight)
- **60–100% of glide:** fade ramps linearly from 0 → 1
- **After glide:** full fade (1.0)

## Code Changes Required

### File: `src/main/java/com/mcdg/game/TrajectoryCalculator.java`

1. **Remove line 74** (no longer needed for time-based fade):
   ```java
   double initialSpeed = vel.horizontalLength();
   ```

2. **Replace lines 114–130** with corrected curve logic:
   ```java
   // Apply time-based fade curve (fade intensifies in latter part of glide)
   double currentSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
   double curveFactor;
   if (!hasGlide) {
       curveFactor = 0.0;
   } else {
       int fadeStartTick = (int) Math.round(glideTicks * 0.6);
       if (tick < fadeStartTick) {
           curveFactor = 0.0;
       } else if (tick >= glideTicks) {
           curveFactor = 1.0;
       } else {
           curveFactor = (double) (tick - fadeStartTick) / (glideTicks - fadeStartTick);
       }
   }

   double curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor;

   // Calculate perpendicular direction for curve (left of facing direction)
   float yawRad = (float) Math.toRadians(launchYawDegrees);
   double leftX = -Math.cos(yawRad);
   double leftZ = -Math.sin(yawRad);

   double velX = vel.x + leftX * curveStrength;
   double velZ = vel.z + leftZ * curveStrength;
   ```

### File: `src/main/java/com/mcdg/game/DiscFlightSimulator.java`

Apply **identical changes** to `DiscFlightSimulator.java` — it has the same buggy curve math and is used for pearl-based auto-tests.

## Expected Impact

With `BASE_CURVE_STRENGTH = 0.06`, `curveMultiplier ≈ 1.0–2.5`, and `totalBias = ±1 or ±2`:

| Charge | Glide Ticks | Fade Window Ticks | Max Curve Strength | Approx Lateral Drift |
|--------|-------------|-------------------|-------------------|----------------------|
| 1.0 (full) | 50 | 20 | ±0.15/tick | ~3–6 ft |
| 0.5 | 30 | 12 | ±0.23/tick | ~3–5 ft |
| 0.25 | 20 | 8 | ±0.29/tick | ~2–4 ft |

These are reasonable disc-golf fade distances. If drift feels too weak/strong in-game, tune `BASE_CURVE_STRENGTH` (try `0.08` or `0.10`).

## Test Plan

### 1. Run Existing Tests
```bash
./gradlew test
```
- `TrajectoryCalculatorTest` should still pass (it only tests enum bias arithmetic, not trajectory physics)

### 2. Add Physics-Based Tests to `TrajectoryCalculatorTest.java`

Create a mock flat world (no collisions) and verify:
- **BACKHAND + FLAT:** negative lateral drift (left fade)
- **FOREHAND + FLAT:** positive lateral drift (right fade)
- **BACKHAND + HYZER:** stronger negative drift
- **FOREHAND + ANHYZER:** negative drift (opposite natural)
- **OVERHAND (any angle):** ~0 drift (no glide = no fade)

Example test structure:
```java
@Test
void testBackhandFlatProducesLeftFade() {
    // Mock world with flat terrain at y=64
    // Throw at full charge, flat angle
    // Assert lateralDriftFt < -2.0
}

@Test
void testForehandFlatProducesRightFade() {
    // Mock world with flat terrain at y=64
    // Throw at full charge, flat angle
    // Assert lateralDriftFt > 2.0
}
```

### 3. Run Regression Tests
```bash
./gradlew quickRegression
```
- Verify determinism and invariants still hold

### 4. In-Game Verification
Deploy to ATLauncher and test:
- Throw backhand/forehand with hyzer/flat/anhyzer
- Observe visible lateral curve in flight trail
- Verify HUD shows non-zero fade values
- Confirm direction matches expected behavior

## Implementation Summary

### Completed Implementation

All planned fixes have been successfully implemented and validated:

1. **Perpendicular Direction Vector Fix**: Aligned curve application and drift measurement to use the same perpendicular axis
2. **Time-Based Fade Curve**: Replaced velocity-dependent fade with time-based fade that ramps up during the latter portion of the glide phase
3. **Consistent Application**: Applied identical fixes to both `TrajectoryCalculator.java` and `DiscFlightSimulator.java`
4. **Test Coverage**: Added physics-based drift tests to `TrajectoryCalculatorTest.java`
5. **Validation**: All tests pass, including new physics-based tests and regression tests

### Testing Results

- ✅ All existing tests pass
- ✅ New physics-based drift tests pass
- ✅ Regression tests pass (determinism verified)
- ✅ In-game testing confirms visible curve and non-zero fade HUD
- ✅ Directional behavior matches expectations (backhand fades left, forehand fades right)

### Performance Impact

- No performance degradation observed
- Time-based fade is more predictable and tunable
- Perpendicular direction fix ensures accurate drift measurements

---

## Original Implementation Checklist (Historical)

- [x] Apply fixes to `TrajectoryCalculator.java`
- [x] Apply identical fixes to `DiscFlightSimulator.java`
- [x] Run `./gradlew test` — verify existing tests pass
- [x] Add physics-based drift tests to `TrajectoryCalculatorTest.java`
- [x] Run `./gradlew test` — verify new tests pass
- [x] Run `./gradlew quickRegression` — verify determinism
- [x] Deploy to ATLauncher
- [x] In-game test: verify visible curve and non-zero fade HUD
- [x] Tune `BASE_CURVE_STRENGTH` if needed
- [x] Commit changes with descriptive message

## Branch Status

- **Current branch:** Merged to `master`
- **Base branch:** `master`
- **Status:** ✅ COMPLETED (2026-06-17)

## Notes

- The fix decouples fade from velocity, making curve behavior more predictable and tunable
- Time-based fade aligns with real disc physics: stability during glide, fade as lift decays
- The perpendicular direction fix ensures drift measurements match actual flight path
- Both `TrajectoryCalculator` and `DiscFlightSimulator` need identical fixes for consistency
