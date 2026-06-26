# MCDG Disc Flight Distance Simulation

Reference table of theoretical maximum disc flight distances for each tier, computed from the current trajectory physics constants.

**Last updated:** 2026-06-25

---

## Assumptions

- **100% power** (`charge = 1.0`)
- **No enchantments**
- **No wind**
- **Flat ground** (landing height = release height offset)
- **Horizontal aim** for the "Flat aim" table; **optimized launch angle** for the "Optimized aim" table
- **BACKHAND and FOREHAND are symmetric** for forward distance, so only BACKHAND is shown for glide stances
- **Lateral fade/curve is ignored** for these tables; in-game it will add slight drift and marginally reduce forward distance

## Physics constants used

| Constant | Value | Source |
|----------|-------|--------|
| `MIN_VELOCITY` | 0.7 blocks/tick | `ChargedDiscItem` |
| `VELOCITY_SPAN` | 1.6 blocks/tick | `ChargedDiscItem` |
| Base velocity at 100% power | `0.7 + 1.6 = 2.3` blocks/tick | Calculation |
| `UPWARD_IMPULSE` | 0.06 blocks/tick | `TrajectoryCalculator` |
| `GRAVITY` | 0.08 blocks/tick² | `TrajectoryCalculator` |
| `RELEASE_HEIGHT_OFFSET` | 1.5 blocks | `TrajectoryCalculator` |
| `GLIDE_TAPER_START` | 0.6 | `TrajectoryCalculator` |
| `MAX_SIMULATION_TICKS` | 400 | `TrajectoryCalculator` |

## Raw tier physics and tooltip flight numbers

| Disc | `throwSpeedMultiplier` | Velocity (blocks/tick) | `glideMultiplier` | Tooltip Flight (Speed/Glide/Turn/Fade) |
|------|------------------------|------------------------|-------------------|----------------------------------------|
| Training | 1.0 | 2.30 | 1.0 | 3 / 4 / 0 / 1 |
| Wooden | 1.0 | 2.30 | 0.8 | 4 / 3 / -1 / 1 |
| Stone | 1.0 | 2.30 | 0.9 | 5 / 3 / 0 / 1 |
| Iron | 1.0 | 2.30 | 1.0 | 6 / 4 / 0 / 2 |
| Gold | 1.1 | 2.53 | 1.1 | 7 / 5 / -1 / 1 |
| Diamond | 1.0 | 2.30 | 1.2 | 9 / 6 / 0 / 3 |
| Netherite | 1.0 | 2.30 | 1.3 | 11 / 7 / 1 / 4 |

## Distance at horizontal aim (0° pitch)

### OVERHAND (default stance, no glide)

| Disc | Velocity | Time to land | Distance | Feet |
|------|----------|--------------|----------|------|
| Training | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |
| Wooden | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |
| Stone | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |
| Iron | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |
| Gold | 2.53 | 6.12 ticks | 15.5 blocks | ~51 ft |
| Diamond | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |
| Netherite | 2.30 | 6.12 ticks | 14.1 blocks | ~46 ft |

### BACKHAND / FOREHAND (glide stance)

| Disc | Glide | Velocity | Time to land | Distance | Feet |
|------|-------|----------|--------------|----------|------|
| Training | 1.0 | 2.30 | 11.8 ticks | 27.1 blocks | ~89 ft |
| Wooden | 0.8 | 2.30 | 9.2 ticks | 21.2 blocks | ~69 ft |
| Stone | 0.9 | 2.30 | 10.3 ticks | 23.6 blocks | ~77 ft |
| Iron | 1.0 | 2.30 | 11.8 ticks | 27.1 blocks | ~89 ft |
| Gold | 1.1 | 2.53 | 14.1 ticks | 35.8 blocks | ~117 ft |
| Diamond | 1.2 | 2.30 | 18.9 ticks | 43.4 blocks | ~142 ft |
| Netherite | 1.3 | 2.30 | 38.2 ticks | 87.9 blocks | ~288 ft |

## Distance with optimized upward aim

A numerical simulation searched for the launch pitch (in 0.5° steps) that maximizes forward distance.

### OVERHAND

| Disc | Optimal angle | Distance |
|------|---------------|----------|
| Training | +45.0° | ~213 ft |
| Wooden | +45.0° | ~213 ft |
| Stone | +45.0° | ~213 ft |
| Iron | +45.0° | ~213 ft |
| Gold | +45.0° | ~258 ft |
| Diamond | +45.0° | ~213 ft |
| Netherite | +45.0° | ~213 ft |

### BACKHAND / FOREHAND

| Disc | Optimal angle | Distance |
|------|---------------|----------|
| Training | +36.5° | ~471 ft |
| Wooden | +39.0° | ~375 ft |
| Stone | +36.0° | ~421 ft |
| Iron | +36.5° | ~471 ft |
| Gold | +35.0° | ~611 ft |
| Diamond | +33.5° | ~598 ft |
| Netherite | +31.5° | ~675 ft |

## Notes

- **OVERHAND distance is almost tier-independent.** Without glide, only Gold's `throwSpeedMultiplier` boost matters.
- **BACKHAND/FOREHAND distance is dominated by glide.** Higher `glideMultiplier` keeps the disc aloft longer and allows a lower, faster launch angle.
- **Tooltip Speed vs actual velocity:** The tooltip Speed number is a tier-progression rating, not an exact velocity multiplier. Only Gold has a different actual velocity (2.53 vs 2.30 for everyone else). This is why the tooltip numbers don't perfectly predict distance.
- **Glide is the best distance predictor.** Training (Glide 4) flies farther than Wooden (Glide 3) even though Wooden has a higher tooltip Speed number.
- **Real distances will differ** due to terrain, wind, lateral fade, and the player not throwing at the exact optimal angle.

## How to reproduce

The simulation uses the constants in `ChargedDiscItem.java` and `TrajectoryCalculator.java` and iterates pitch angles to find the maximum forward distance before the disc reaches ground level. See the inline Python script used to generate these tables in the project conversation history.
