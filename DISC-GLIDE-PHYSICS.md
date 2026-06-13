# Disc Glide & Curve Physics Plan

**Status:** Ready for implementation  
**Goal:** Replace vanilla Ender Pearl throws with three distinct throw stances (Overhand, Backhand, Forehand), each with aerodynamic glide physics and release-angle control.

---

## Overview

| Stance | Behavior |
|--------|----------|
| **Overhand** | Current behavior: ballistic pearl arc, no glide, no lateral curve. |
| **Backhand (RHBH)** | Flat glide phase + natural **left fade** at end of flight. |
| **Forehand (RHFH)** | Flat glide phase + natural **right fade** at end of flight. |

**Release angle** (hyzer/flat/anhyzer) tweaks the curve on top of stance:

| Angle | Effect |
|-------|--------|
| **Hyzer** | Exaggerates natural fade direction (more left on backhand, more right on forehand). |
| **Anhyzer** | Counteracts natural fade (backhand drifts right; forehand drifts left). |
| **Flat** | Neutral -- only the stance's natural fade applies. |

---

## Player Input

| Input | Control | Default |
|-------|---------|---------|
| **Tap `R`** | Cycle throw stance: Overhand -> Backhand -> Forehand | `R` |
| **Scroll while charging** | Cycle release angle: Hyzer -> Flat -> Anhyzer | -- |

**Defaults on first use:** Overhand stance, Flat angle.  
**Scroll suppression:** Normal hotbar scrolling is suppressed while `ChargedDiscItem` charge is active (`clientChargeVisible == true`).

---

## Architecture

### Server (`com.mcdg.game`)

#### `DiscFlightSimulator` -- new class

- `Map<UUID, FlightState> activeFlights` keyed by pearl UUID.
- `FlightState` record: `pearlUUID`, `launchTick`, `launchYawDegrees`, `charge` (0.0-1.0), `stance` (Overhand/Backhand/Forehand), `releaseAngle` (Hyzer/Flat/Anhyzer).
- `registerThrow(...)`: Called from `ChargedDiscItem.onStoppedUsing()` after pearl spawn.
- `tick(MinecraftServer)`: Registered on `ServerTickEvents.END_SERVER_TICK` in `McdgMod`.
- `reset()`: Clears map; called alongside `ThrowResolver.reset()`.

#### Per-tick physics

**Glide phase** (tick 0 -> `glideTicks`):
- Apply upward Y impulse of `+0.03` each tick to counteract vanilla gravity (~-0.03/tick).
- Net effect: nearly flat horizontal flight.

**Glide taper** (final 20% of glide):
- Linearly reduce upward impulse from full to zero; pearl naturally arcs down.

**Fade phase** (last ~20 ticks of glide):
- Apply lateral nudge relative to launch yaw:
  - Backhand + Flat: leftward nudge (`yaw + 90 degrees`), increasing toward end.
  - Forehand + Flat: rightward nudge (`yaw - 90 degrees`), increasing toward end.
  - Overhand: no lateral nudge, no upward impulse (vanilla arc).

**Glide duration:** `glideTicks = 10 + (charge * 70)` -> 10 ticks (min) to 80 ticks (full power).

**Lateral deflection formula (combined stance + angle):**
```
naturalFade = stance == BACKHAND ? -1 : (stance == FOREHAND ? +1 : 0)
angleBias   = angle == HY_ZER ? -1 : (angle == ANHYZER ? +1 : 0)
deflection  = (naturalFade + angleBias) * baseCurve * (1 - fadeProgress)
```
- Overhand ignores all deflection regardless of angle.
- `fadeProgress` goes 0->1 over the last 40% of expected flight.

### Client (`com.mcdg.client`)

#### `ClientKeybinds` -- add `cycleThrowStanceKey`
- `GLFW.GLFW_KEY_R`, translation key `key.mcdg.cycle_stance`.
- Poll in `McdgClientMod` client tick loop.

#### `ThrowPreferenceManager` -- new class (client-side)
- Static state for selected `ThrowStance` per player.
- Sends stance + angle to server at throw time.

#### `HudOverlays` -- extend `renderPower()`
- Show current stance name (Overhand/Backhand/Forehand) near the charge bar when holding the disc.
- Show small arrow indicator for release angle during charge (up hyzer, right flat, down anhyzer).

#### `McdgClientMod` -- wire inputs
- Poll `ClientKeybinds.forEachStanceCyclePress(...)` in client tick.
- Capture scroll events during `ChargedDiscItem.isClientChargeVisible()`.
- Suppress normal hotbar scrolling while charging.

---

## Integration with Existing Systems

| System | Impact |
|--------|--------|
| `ThrowResolver` | No change. Tracks pearl UUID, waits for landing, resolves penalties as before. |
| `HoleProgressTracker` | No change. Lie resolution uses `currentFeet` (landing), not flight path. |
| Strict penalties | `findLastSolidBeforeOutCrossing` traces throwLie -> landingFeet. Path doesn't matter. |
| Autotest | Add `FLIGHT_MODE_STRAIGHT` override for `ThrowAutoTestService`. Or add aim-off compensation. |
| `EnderPearlThrowTracker` | Unaffected -- handles raw ender pearl usage outside the disc item. |
| `MAX_THROW_RESOLUTION_WAIT_TICKS` | 320 ticks > 80 glide ticks. No issue. |

---

## Phased Implementation

### Phase 1: Core Glide Physics
- Create `DiscFlightSimulator` with `FlightState` and server tick handler.
- Modify `ChargedDiscItem.onStoppedUsing()`: register throw with default `OVERHAND` + `FLAT`.
- Register tick handler in `McdgMod`.
- Implement glide phase (upward impulse) and glide taper. No lateral curve yet.
- Validate: 400-600 ft at full power with flat/horizontal aim.

### Phase 2: Throw Stance Selection
- Add `ThrowStance` enum (`OVERHAND`, `BACKHAND`, `FOREHAND`).
- Add `cycleThrowStanceKey` to `ClientKeybinds` (`R` default).
- Create `ThrowPreferenceManager` (client-side).
- Send stance to server at throw time (extend `ThrowResolver.registerThrowRelease` or use pearl NBT).
- `DiscFlightSimulator`: apply stance-specific physics (Overhand = vanilla, Backhand = left fade, Forehand = right fade).
- Render stance name on HUD when holding disc.

### Phase 3: Release Angle Input
- Add `ReleaseAngle` enum (`HYZER`, `FLAT`, `ANHYZER`).
- Capture scroll events during charge in `McdgClientMod`.
- Store angle in `FlightState`.
- Apply combined stance + angle deflection formula.
- Render angle arrow on charge HUD.

### Phase 4: Visual Polish (Particles & HUD)
- Client-side particle trail following pearl path.
- Trail color per stance (or per angle).
- Sound cue when fade phase begins (optional).
- Clean HUD stance/angle indicator.

### Phase 5: Balance, Autotest & Validation
- Tune `glideTicks` and curve magnitude:
  - Overhand: unchanged from current behavior.
  - Backhand/Forehand flat: 400-600 ft at full power.
  - Backhand/Forehand with angle: controllable left/right drift.
- Add `FLIGHT_MODE_STRAIGHT` override for `ThrowAutoTestService`.
- Run `./gradlew quickRegression smokeRegression`.
- Acceptance:
  - Overhand throws are identical to today.
  - Backhand flat fades left; Backhand anhyzer curves right.
  - Forehand flat fades right; Forehand hyzer curves left.
  - `ThrowResolver` lie resolution works unchanged.
  - No new entity types introduced.
  - All physics server-authoritative.

### Phase 6: Custom Arm Animations (Post-physics polish)
- **Third-person:** Mixin `BipedEntityModel.setAngles` or `PlayerEntityRenderer`. Rotate arm bones per stance.
- **First-person:** Mixin `HeldItemRenderer.renderFirstPersonItem`. Adjust item position/rotation.
- **Placeholder until then:** Use vanilla `UseAction` values per stance:
  - Overhand -> `BOW`
  - Backhand -> `SPEAR`
  - Forehand -> `CROSSBOW`

---

## Files to Create / Modify

**Create:**
- `src/main/java/com/mcdg/game/DiscFlightSimulator.java`
- `src/main/java/com/mcdg/game/ThrowStance.java`
- `src/main/java/com/mcdg/game/ReleaseAngle.java`
- `src/client/java/com/mcdg/client/ThrowPreferenceManager.java`

**Modify:**
- `src/main/java/com/mcdg/game/ChargedDiscItem.java` -- send stance/angle to server on throw
- `src/main/java/com/mcdg/McdgMod.java` -- register `DiscFlightSimulator::tick`
- `src/main/java/com/mcdg/game/HoleProgressTracker.java` -- call `DiscFlightSimulator.reset()`
- `src/main/java/com/mcdg/game/ThrowResolver.java` -- accept stance/angle in `registerThrowRelease` (or read from pearl)
- `src/main/java/com/mcdg/game/ThrowAutoTestService.java` -- add straight-flight override
- `src/client/java/com/mcdg/client/ClientKeybinds.java` -- add `cycleThrowStanceKey`
- `src/client/java/com/mcdg/client/McdgClientMod.java` -- poll keybind, capture scroll, send preferences
- `src/client/java/com/mcdg/client/HudOverlays.java` -- render stance + angle indicators
- `src/main/resources/assets/mcdg/lang/en_us.json` -- add translation keys for stance, angle, keybind

---

## Backlog / Nice to Have

- Wind (per-hole or global drift vector)
- Elevation-aware curve (uphill throws fade harder, downhill glide farther)
- Disc stability ratings within each mode (e.g. "overstable driver" vs "understable driver")
- Spectator disc cam (follow pearl in 3rd person while player stays at tee)
- Separate visual disc model from player teleport (true disc golf: player walks to lie)
