# Throw Charge & HUD Enhancements Plan

**Status:** ✅ COMPLETED - Merged to master  
**Completed:** 2026-06-14  
**Branch:** `feature/charge-enhancements` → `master`  
**Original Implementation:** Part of abandoned `feature/glide` branch

---

## Overview

General throw improvements independent of glide physics. These enhancements improve the base throwing experience for all stances (overhand, backhand, forehand) and provide better feedback for throw planning.

---

## Implemented Features

### 1. Slower Charge Rate
- **Change:** `MAX_CHARGE_TICKS` increased from 60 to 120 (2 seconds instead of 1 second)
- **Rationale:** Slower charge allows players better control and throw planning
- **Impact:** All throws take longer to reach full power, but provides more precision

### 2. 125% Max Power with Overcharge
- **Change:** `MAX_POWER_MULTIPLIER = 1.25f` allows overcharge beyond 100%
- **Feature:** Charge continues to 125% if held beyond full charge
- **HUD:** Red overcharge zone appears above 100% on power bar
- **Rationale:** Rewards skilled players who master timing, provides risk/reward mechanic

### 3. Power Lock Feature
- **Keybind:** F key (default) to lock/unlock power
- **Behavior:** 
  - Press while charging to lock at current power level
  - "LOCKED" indicator appears on HUD
  - Charge stops increasing while locked
  - Allows aiming without losing charge
  - Press again to unlock and resume charging
- **Network:** `ThrowPowerLockSync` packet for server-to-client state synchronization
- **Rationale:** Improves throw planning by separating power charging from aiming

### 4. Audio Cues at Charge Thresholds
- **Thresholds:** 25%, 50%, 75%, 100% charge levels
- **Sound:** `ENTITY_EXPERIENCE_ORB_PICKUP` with varying pitch
- **Behavior:** 
  - Sound plays when crossing each threshold
  - Pitch increases with threshold (0.8f to 1.2f)
  - No repeated sounds at same threshold
- **Rationale:** Provides audio feedback for charge level without needing to look at HUD

### 5. Distance Markers on HUD
- **Feature:** Power bar shows distance markers at 25%, 50%, 75%, 100% charge levels
- **Display:** Actual distance in feet (e.g., "50ft", "100ft") next to each marker
- **Calculation:** Uses `DiscFlightSimulator.estimateFlight()` for distance estimates
- **Dynamic:** Updates based on current pitch and stance
- **Rationale:** Helps players plan throws based on target distance

### 6. Enhanced HUD Styling
- **Color Changes:** Power bar fill color changes at 50% (green to yellow)
- **Overcharge Zone:** Red zone appears above 100% (100-125%)
- **Percentage Text:** Shows current charge level (e.g., "75%")
- **Lock Indicator:** "LOCKED" text appears when power is locked
- **Rationale:** Better visual feedback for charge state

---

## Files Modified

### Server-Side
- `src/main/java/com/mcdg/game/ChargedDiscItem.java`
  - Increased `MAX_CHARGE_TICKS` from 60 to 120
  - Added `MAX_POWER_MULTIPLIER = 1.25f`
  - Added power lock state management
  - Added audio threshold tracking
  - Modified charge calculation to support 125% max

### Networking
- `src/main/java/com/mcdg/net/ThrowPowerLockSync.java`
  - Server-to-client packet for power lock state
  - Ensures client and server stay synchronized

### Client-Side
- `src/client/java/com/mcdg/client/ClientKeybinds.java`
  - Added F keybind for power lock (`lockPowerKey`)

- `src/client/java/com/mcdg/client/McdgClientMod.java`
  - Added power lock keybind handling in client tick loop
  - Integrated with charge state management

- `src/client/java/com/mcdg/client/HudOverlays.java`
  - Enhanced power bar rendering with distance markers
  - Added color changes at thresholds
  - Added overcharge zone display
  - Added percentage text
  - Added "LOCKED" indicator
  - Integrated with `DiscFlightSimulator.estimateFlight()` for distance calculations

---

## Integration Notes

### Distance Marker Calculation
- Distance markers currently use `DiscFlightSimulator.estimateFlight()`
- For overhand throws, this returns 0 glide/0 fade, just base distance
- Glide branch will leverage these enhancements automatically
- Distance estimates update based on:
  - Current charge level
  - Player pitch angle
  - Selected throw stance (when glide is implemented)

### Power Lock State Management
- Client-side static fields in `ChargedDiscItem`:
  - `powerLocked` - boolean lock state
  - `lockedChargePercent` - stored charge level when locked
- Server-side tracking via `ThrowPowerLockSync` packet
- State reset on throw completion or round end

### Audio Threshold System
- Tracks `lastAudioThreshold` to prevent repeated sounds
- Resets on charge start and throw completion
- Uses vanilla Minecraft sound for compatibility

---

## Testing Requirements

### Manual Testing
- Charge rate feels noticeably slower (2-3 seconds to full)
- Audio cues provide feedback at each threshold without spam
- Power lock allows aiming without losing charge
- Distance markers help players plan throws based on target distance
- Existing flight estimates continue to work correctly
- Power lock does NOT activate when NOT charging

### Automated Testing
- No specific automated tests required
- Existing throw resolution tests should pass
- Charge mechanics are client-side visual feedback
- Server-side throw resolution unchanged

---

## Benefits

1. **Immediate Value** - Improves throwing experience for all players immediately
2. **Lower Risk** - Independent of complex glide physics
3. **Simpler Testing** - Can be tested separately from glide implementation
4. **Better UX** - Provides feedback and control for throw planning
5. **Foundation** - Glide physics will build on these enhancements

---

## Future Integration with Glide

When glide physics is implemented:
- Distance markers will automatically show glide-enhanced distances
- Stance selection will affect distance estimates
- Release angle will be reflected in distance calculations
- Power lock will work with all three stances
- Audio cues will provide feedback for glide throws

---

## Rollout Plan

1. Extract charge enhancements from `feature/glide` branch
2. Create `feature/charge-enhancements` branch
3. Test thoroughly in isolation
4. Merge to master
5. Deploy to test instance
6. Start fresh `feature/glide-v2` branch from enhanced master
7. Glide implementation will benefit from existing charge enhancements
