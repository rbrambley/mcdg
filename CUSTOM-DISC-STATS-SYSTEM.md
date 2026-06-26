# Custom Disc Stats System Plan

**Status:** Planning Phase — Foundation Exists  
**Created:** 2026-06-17  
**Last Updated:** 2026-06-26  
**Goal:** Extend the existing tiered disc system with realistic per-disc flight ratings and optional customization

---

## Overview

This system evolves the current tiered discs (`DiscTier` / `TieredDiscItem` / `DiscStats`) into specialized equipment with unique flight characteristics, similar to real disc golf where different disc molds have distinct speed, glide, turn, and fade ratings. Players can collect, customize, and optimize their disc bag for different courses and conditions.

**Current Foundation (Phase 3.1 Complete):**
- `DiscTier` already defines flight numbers (speed, glide, turn, fade) and physics modifiers
- `DiscStats` already applies glide, stability, throw speed, and wind resistance multipliers
- `DiscEnchantment` already modifies flight numbers through the Disc Workbench
- `DiscBagItem` already provides a 12-slot portable disc bag
- `SkillUnlock` already gates progression-based abilities
- `AccessoryEffect` already provides passive bonuses (grip, durability, range)
- `WindManager` / `WindManagerClient` already provides wind simulation

**Design Philosophy:**
- Extend the existing tiered system rather than replace it
- Meaningful differences between disc types
- Strategic disc selection for different situations
- Customization gated by existing skill unlocks
- Avoid duplicating the enchantment/accessory systems
- Collection and discovery aspects built on the existing Disc Bag

---

## Existing Foundation

### Current Tiered Disc System

The `DiscTier` enum already defines flight numbers for each material tier:

| Tier | Speed | Glide | Turn | Fade | Notes |
|------|-------|-------|------|------|-------|
| Training | 3 | 4 | 0 | 1 | Indestructible, neutral |
| Wooden | 4 | 3 | -1 | 1 | Slight understability |
| Stone | 5 | 3 | 0 | 1 | Moderate, durable |
| Iron | 6 | 4 | 0 | 2 | Standard fairway |
| Gold | 7 | 5 | -1 | 1 | Faster, less stable |
| Diamond | 9 | 6 | 0 | 3 | Distance driver |
| Netherite | 11 | 7 | 1 | 4 | Overstable, wind resistant |

The `DiscStats` record applies:
- `glideMultiplier`
- `stabilityMultiplier`
- `throwSpeedMultiplier`
- `windResistance` (0.0 = full wind effect, 1.0 = no wind effect)

### Existing Skill Unlocks

| Skill | Requirement | Custom Disc Use |
|-------|-------------|-----------------|
| `POWER_CONTROL` | Earn 100 MCDG skill XP | Access advanced power ratings |
| `RELEASE_CONTROL` | Complete 10 rounds | Unlock custom disc creation |
| `WIND_READING` | Throw 500 discs | Unlock wind-resistance tuning |
| `FOCUS` | Land 50 throws within 10ft | Unlock precision tuning |
| `DISC_MASTERY` | Throw one of each tier | Unlock legendary/rare disc molds |

### Existing Disc Bag

`DiscBagItem` is a 12-slot portable inventory. Phase 3.2 will extend this with:
- Disc categorization
- Bag analysis
- Flight-chart preview
- But **not** a separate library system

---

## Phase 3.2 Scope

### MVP Goals

1. **Per-disc flight ratings** stored on the item stack
2. **Mold system** providing named archetypes beyond material tiers
3. **Physics integration** that reads ratings from the disc stack
4. **Tooltip/HUD improvements** showing the four flight numbers
5. **Disc bag enhancements** for categorization and basic analysis

### Out of Scope for Phase 3.2

- Full custom disc builder UI (retained for Phase 3.3/4)
- Player-created molds
- Player-to-player trading
- Wear/aging system until its relationship with durability is resolved
- Legendary/epic rarity tiers unless they are simple loot-table additions

---

## Disc Statistics Model

### Primary Flight Ratings

Reuses the existing ranges already present in `TieredDiscItem`:

```java
record DiscFlightRatings(
    int speed,          // 1-14: Speed at release (higher = faster)
    int glide,          // 1-7: Ability to maintain altitude
    int turn,           // -5 to +1: High-speed turn
    int fade,           // 0-5: Low-speed fade
    double stability    // Calculated from turn/fade combination
)
```

The stability formula is:
```java
stability = fade - (turn * 0.5); // Higher = more overstable
```

### Extended Disc Stats

Extend the existing `DiscStats` record to include flight ratings:

```java
record DiscStats(
    double glideMultiplier,
    double stabilityMultiplier,
    double throwSpeedMultiplier,
    double windResistance,
    int flightSpeed,
    int flightGlide,
    int flightTurn,
    int flightFade
)
```

This keeps the existing physics API intact while adding the new flight numbers.

### Mold / Archetype

```java
enum DiscMold {
    CLASSIC_PUTTER,
    BEADED_PUTTER,
    STRAIGHT_MIDRANGE,
    OVERSTABLE_MIDRANGE,
    UNDERSTABLE_MIDRANGE,
    CONTROL_DRIVER,
    DISTANCE_DRIVER,
    OVERSTABLE_DRIVER,
    STABLE_DISTANCE_DRIVER,
    UNDERSTABLE_DISTANCE_DRIVER,
    MAX_DISTANCE_DRIVER
}
```

Each mold maps to a default `DiscFlightRatings` and a set of valid tiers.

### Tier + Mold Interaction

- Material tier determines **durability**, **base wind resistance**, and **maximum mold speed**
- Mold determines the **flight numbers**
- A Wooden disc cannot be a Max Distance Driver mold
- A Netherite disc can be any mold

Example:

| Tier | Max Mold Speed | Example Mold | Final Speed |
|------|----------------|--------------|-------------|
| Wooden | 6 | Control Driver | 4 |
| Iron | 9 | Distance Driver | 6 |
| Diamond | 12 | Stable Distance Driver | 9 |
| Netherite | 14 | Max Distance Driver | 11 |

---

## Custom Disc Creation (Deferred to Phase 3.3)

### Proposed Builder

When implemented, the custom disc builder should:

- Use a dedicated workbench block (extend or augment `DiscWorkbenchBlock`)
- Require `RELEASE_CONTROL` and `DISC_MASTERY` skill unlocks
- Use the existing tier materials as base cost
- Allow selecting a mold and plastic type
- Validate that the resulting ratings are within the tier's allowed ranges

### Plastic Types

Reused as simple modifiers:

| Plastic | Effect |
|---------|--------|
| Base | Standard, no bonus |
| Premium | +5% durability |
| Champion | +10% wind resistance |
| Star | +5% glide |
| DX | +1 turn (more understable) |

---

## Integration with Existing Systems

### Physics Integration

The existing `TrajectoryCalculator` already receives a `DiscStats` object. Phase 3.2 changes the lookup path:

```java
DiscStats stats = getDiscStats(stack);
Vec3d effectiveWind = stats.applyWindResistance(wind);
```

`TieredDiscItem.getDiscStats()` currently returns `tier.stats()`. For custom/mold discs, this will return the per-disc stats instead.

### Enchantment Integration

Existing enchantments (`GLIDE`, `STABILITY`, `PIERCE`, `DURABILITY`, `RANGE`) already modify flight numbers. The plan preserves this behavior:

- Enchantments are **stacked modifiers** applied to the base mold ratings
- Custom disc **mold ratings** are the base; enchantments enhance them
- This avoids creating a third parallel bonus system

### Accessory Integration

Accessories provide passive bonuses independent of the disc:

- `GRIP_STABILITY` → reduces angle penalty
- `DURABILITY_PRESERVE` → chance to skip durability loss
- `RANGE_FINDER` → future distance feedback (reserved)

Custom disc stats do not duplicate these effects.

### Skill Integration

Skill unlocks gate features rather than modify physics directly:

- `RELEASE_CONTROL` → unlock custom disc creation
- `DISC_MASTERY` → unlock all molds
- `WIND_READING` → unlock wind-resistance tuning in builder
- `POWER_CONTROL` → unlock higher speed caps
- `FOCUS` → unlock precision tuning

### Wind Integration

Wind resistance is already part of `DiscStats`. Mold/plastic choices can adjust this value, but the core wind physics remain in `TrajectoryCalculator`.

---

## Wear and Aging System

**Status: Pending design decision**

The existing tiered discs use Minecraft durability. Adding a separate wear system would create two degradation mechanics on the same item.

Options:

1. **Replace durability with wear** (simpler, but breaks existing repair/enchantment expectations)
2. **Keep durability, add wear as flavor text** (wear affects flight numbers only at very high use counts)
3. **Defer wear system entirely** until Phase 3.3 or later

**Recommendation:** Option 3. Resolve after the MVP is stable.

---

## UI/UX Design

### Disc Inspector

- Tooltip already shows flight numbers via `TieredDiscItem.appendTooltip`
- Add a keybind or right-click action to show a detailed panel:
  - Flight ratings
  - Mold name
  - Plastic type
  - Recommended use case
  - Wear level (if enabled later)

### Disc Bag Manager

- Extend `DiscBagScreen` with:
  - Sort buttons (by speed, stability, distance)
  - Filter buttons (putters, midranges, drivers)
  - Basic "bag coverage" indicator showing gaps in stability/distance

### Flight Chart Preview

- Client-side only
- Renders a predicted flight path in a UI panel
- Uses the same `TrajectoryCalculator` logic with zero-wind assumption

---

## Configuration and Balance

### Server Configuration

```java
public record DiscStatsConfig(
    boolean enableMoldSystem,
    boolean enableCustomDiscBuilder,
    boolean enableDiscBagAnalysis,
    int maxMoldSpeedForTier,
    double maxWindResistanceBonus,
    int maxCustomDiscsPerPlayer
)
```

### Balance Considerations

- No mold should be strictly better than all others
- Material tier still gates maximum performance
- Enchantments and accessories remain the primary power customization
- Custom builder is gated behind skill unlocks to prevent early-game power spikes

---

## Testing Strategy

### Unit Tests

- `DiscFlightRatings` validation
- Mold-to-tier compatibility
- `DiscStats` physics integration
- Enchantment + mold interaction

### Integration Tests

- Custom disc physics with `TrajectoryCalculator`
- Bag categorization with `DiscBagScreen`
- Skill gating with `PlayerSkillManager`

### Balance Testing

- Compare distance and accuracy across molds
- Verify no single mold dominates all holes
- Check that tiered progression still feels meaningful

---

## Implementation Timeline

### Phase 3.2 MVP (10-14 hours)

1. **Stats data model** (2-3 hours)
   - Extend `DiscStats` with flight numbers
   - Create `DiscMold` enum
   - Add mold-to-tier validation

2. **Physics integration** (2-3 hours)
   - Update `TieredDiscItem.getDiscStats()` to consider mold
   - Wire flight numbers into `TrajectoryCalculator`
   - Ensure wind resistance still works

3. **Mold generation** (2-3 hours)
   - Add mold tags to recipes or loot
   - Generate default mold for each tier
   - Ensure existing discs remain compatible

4. **Tooltip and inspector** (2-3 hours)
   - Improve tooltip formatting
   - Add detailed inspector panel
   - Show mold name and plastic

5. **Disc bag enhancements** (2-3 hours)
   - Sort/filter buttons
   - Basic coverage indicator

### Phase 3.3 Custom Builder (8-12 hours, deferred)

- Custom disc builder UI
- Plastic selection
- Stamp/color customization
- Advanced validation

---

## Success Criteria

### Functional Requirements
- [ ] Mold system extends existing tiered discs without breaking them
- [ ] Flight ratings integrate with `TrajectoryCalculator`
- [ ] Enchantments and accessories remain relevant
- [ ] Disc bag categorization works
- [ ] Skill gating is functional

### User Experience Requirements
- [ ] Disc variety feels meaningful
- [ ] Tooltips clearly communicate flight numbers
- [ ] Bag management is convenient
- [ ] Tier progression remains satisfying

### Technical Requirements
- [ ] No performance regression
- [ ] Existing save data remains compatible
- [ ] Wind physics remain consistent
- [ ] `quickRegression` passes

---

## Conclusion

Phase 3.2 builds directly on the Phase 3.1 tiered disc system. Rather than replacing it, the custom disc stats system adds molds, flight ratings, and bag management on top of the existing `DiscTier`, `DiscStats`, `DiscEnchantment`, and `DiscBagItem` foundations. This keeps the scope focused and avoids duplicating mechanics already handled by enchantments and accessories.
