# Disc Glide Physics Plan

**Status:** Post-release initiative (after multiplayer validation + compact course layout)
**Goal:** Replace straight-line Ender Pearl throws with simple, fun curved flight that rewards aim and timing.

---

## Current State

`ChargedDiscItem` spawns an `EnderPearlEntity` with linear velocity:

```java
float velocity = MIN_VELOCITY + (VELOCITY_SPAN * charge); // 0.7–2.3
pearl.setVelocity(serverPlayer, pitch, yaw, 0.0f, velocity, 1.0f);
```

`ThrowResolver` tracks pearl UUID, waits for it to land, then resolves penalties and teleports the player. All throws are straight lines — no curve, no disc-type variation, no release-angle control.

---

## Design Principles

1. **Keep the pearl.** Reuse `EnderPearlEntity` to avoid custom entity registration, networking, and desync risk.
2. **Server-authoritative steering.** Modify pearl velocity each server tick via a tick handler.
3. **Fun over realism.** A simple left/right curve during flight is enough; no lift/drag aerodynamics.
4. **Minimal inventory friction.** One disc item with in-flight mode switching; no cluttered hotbar.

---

## Architecture

### `DiscFlightController`

Server tick handler that watches pearls spawned by `ChargedDiscItem`.

```
Map<UUID pearlId, FlightProfile> activeFlights
```

Each tick:
1. Look up the pearl entity by stored UUID.
2. If pearl still exists and in flight, read `getVelocity()`.
3. Compute lateral deflection based on `FlightProfile`.
4. Write modified velocity back with `setVelocity()`.

`FlightProfile` contains:
- `discType` — Putter, Mid, Driver
- `releaseAngle` — Hyzer (left curve), Flat, Anhyzer (right curve)
- `chargePercent` — 0.0–1.0, determines initial speed
- `ticksAlive` — how long the pearl has been flying

### `DiscMode`

Enum with presets:

| Mode     | Speed Range | Curve Magnitude | Ideal Range |
|----------|-------------|-----------------|-------------|
| Putter   | 0.5–0.9     | Very low        | 0–30 ft     |
| Mid      | 1.0–1.6     | Moderate        | 30–120 ft   |
| Driver   | 1.6–2.3     | High            | 120–300 ft  |

Speed is charge-scaled within the mode’s range. Curve magnitude is a constant lateral velocity vector applied each tick, scaled by mode.

### Release Angle Input

During the charge-up (right-click hold):
- **Mouse scroll up** → bias toward Anhyzer (right curve)
- **Mouse scroll down** → bias toward Hyzer (left curve)
- **No scroll** → Flat

Three discrete angles: `HYZER`, `FLAT`, `ANHYZER`. Stored in `FlightProfile` at throw release.

**Visual feedback:** Small arrow indicator on the charge HUD pointing left/right/flat.

### Simplified Flight Model

No speed-dependent turn/fade phases. Instead:

1. First 60% of expected flight time: apply constant lateral deflection based on release angle and disc mode.
2. Remaining 40%: deflection reduces linearly to zero (disc straightens out before landing).

This gives a smooth arc: curve early, settle late. Predictable and easy to learn.

```
deflectionPerTick = baseCurve * releaseAngleMultiplier * (1 - fadeProgress)
```

Where `fadeProgress` goes 0→1 over the last 40% of flight.

### Integration with Existing Systems

| System         | Impact                                                                 |
|----------------|------------------------------------------------------------------------|
| `ThrowResolver`| No change. Tracks pearl UUID, waits for landing, resolves as before.     |
| Strict penalties| `findLastSolidBeforeOutCrossing` traces throwLie→landingFeet. Landing position is what matters, not flight path. Acceptable. |
| Autotest       | Keep a `FLIGHT_MODE_STRAIGHT` override for `ThrowAutoTestService` so autotest can still aim directly. Or add aim-off compensation. |
| `HoleProgressTracker` | No changes needed. Lie resolution already uses `currentFeet` (landing), not path. |
| Charge HUD     | Add small release-angle arrow next to the charge bar.                 |
| `EnderPearlThrowTracker` | Unaffected — this handles raw ender pearl usage outside the disc item. |

---

## Phased Implementation

### Phase 1: Curved Flight Core

- Create `DiscFlightController` with tick handler registration in `McdgMod`.
- Add `FlightProfile` record.
- Modify `ChargedDiscItem` to generate a `FlightProfile` (default FLAT, default Mid) and register it on pearl spawn.
- Implement basic lateral deflection: constant left/right per tick.
- Test: throw curves left, throw curves right, straight throw.

### Phase 2: Disc Mode Switching

- Add `DiscMode` enum (Putter, Mid, Driver).
- Add mode switch UI to the MCDG menu (or keybind cycle).
- Store selected mode per player in `RoundStateManager` or a client-preference map.
- Scale speed range and curve magnitude by mode.
- Add distinct item textures/models for each mode (reuse existing charged/training textures as placeholders).

### Phase 3: Release Angle Input

- Capture scroll events during `ChargedDiscItem` charge-up.
- Store `ReleaseAngle` (Hyzer, Flat, Anhyzer) in `FlightProfile`.
- Render angle indicator on charge HUD (`McdgClientMod` or a new `DiscChargeHudRenderer`).
- Test: scroll while charging, verify curve direction changes.

### Phase 4: Visual Polish

- Particle trail following pearl path (client-side, spawned each tick at pearl position).
- Trail color matches disc mode (e.g., blue for putter, yellow for mid, red for driver).
- Optional: small sound cue when disc "straightens out" (fade phase begins).

### Phase 5: Balance & Validation

- Playtest 9-hole rounds with each mode.
- Tune curve magnitudes so putters go straight, drivers have noticeable but controllable curve.
- Verify autotest still passes with straight-flight override.
- `gradle quickRegression smokeRegression`.

---

## Acceptance Criteria

- [ ] Putter throws are effectively straight (minimal drift).
- [ ] Driver throws with Anhyzer curve right, with Hyzer curve left.
- [ ] Flat release on any mode has no net curve (deflection cancels over flight).
- [ ] Player still teleports to landing position (existing pearl behavior).
- [ ] `ThrowResolver` lie resolution works unchanged.
- [ ] Autotest passes with straight-flight override.
- [ ] No new entity types introduced.
- [ ] All changes are server-authoritative; clients do not predict flight path.

---

## Backlog / Nice to Have

- Wind (per-hole or global drift vector)
- Elevation-aware curve (uphill throws fade harder, downhill glide farther)
- Disc stability ratings within each mode (e.g., "overstable driver" vs "understable driver")
- Spectator disc cam (follow pearl in 3rd person while player stays at tee)
- Separate visual disc model from player teleport (true disc golf: player walks to lie)
