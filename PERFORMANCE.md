# MCDG Performance Optimization Plan

**Created:** 2026-06-16  
**Status:** Planning Phase - Not Yet Implemented  
**Goal:** Eliminate choppy gameplay and improve overall performance

---

## 🔴 Critical Performance Issues (Immediate Priority)

### 1. Minimap Rendering - Per-Pixel Terrain Sampling

**Location:** `src/client/java/com/mcdg/client/MiniMapRenderer.java` lines 649-679

**Issue:** 
- Minimap renders 128×128 texture (16,384 pixels) by sampling terrain for every single pixel each frame
- Each sample involves multiple block state lookups, heightmap queries, fluid state checks, biome lookups, and up to 12 downward block checks
- Runs on client render thread causing frame drops

**Current Mitigation:**
- Has caching with `miniMapRenderCache` and 350ms throttle
- May not be sufficient for smooth gameplay

**Recommended Fixes:**
1. Increase cache throttle from 350ms to 750ms
2. Reduce texture resolution from 128×128 to 96×96 (8,296 pixels = 50% reduction)
3. Sample every 2nd pixel and interpolate for intermediate values
4. Add configurable minimap quality settings (Low/Medium/High)

**Expected Impact:** High - Should eliminate most client-side frame drops

---

### 2. Synchronous File I/O on Server Tick

**Location:** `src/main/java/com/mcdg/McdgMod.java` lines 480-487

**Issue:**
- Every 20 ticks (1 second), performs synchronous file I/O operations
- `Files.writeString()` to save round session
- `Files.readString()` to read course data
- Blocks server thread during writes, causing all players to freeze

**Current Settings:**
- `ROUND_SESSION_AUTOSAVE_INTERVAL_TICKS = 20`

**Recommended Fixes:**
1. Increase autosave interval from 20 to 100 ticks (5 seconds)
2. Consider async file I/O for round session saves using CompletableFuture
3. Add file I/O timing metrics to identify problematic operations

**Expected Impact:** High - Should eliminate server-side freezes during gameplay

---

### 3. Particle Trail Performance

**Location:** `src/client/java/com/mcdg/client/DiscTrailRenderer.java` lines 85-129

**Issue:**
- Particle trails spawn individual particles for each path point (potentially 80+ particles per throw)
- Every tick for 3 seconds (60 ticks total)
- Can cause particle system overload with multiple players

**Current Mitigations:**
- Distance check (256 block radius)
- Skip some particles when fading (alpha > 0.5f || i % 2 == 0)

**Recommended Fixes:**
1. Reduce particle count by 50% (sample every 2nd point instead of current skip logic)
2. Reduce trail duration from 3 seconds (60 ticks) to 2 seconds (40 ticks)
3. Consider using a single particle system with trail renderer instead of individual particles
4. Add maximum particle count limit per player

**Expected Impact:** Medium-High - Should reduce particle system overhead

---

## 🟡 Moderate Performance Issues (Medium Priority)

### 4. Multiple Server Tick Handlers

**Location:** `src/main/java/com/mcdg/McdgMod.java` lines 194-202

**Issue:**
- 9 separate tick handlers registered on `END_SERVER_TICK`
- Each runs every server tick even when not needed:
  - `PLACEMENT_AUTO_TEST_SERVICE::tick`
  - `THROW_AUTO_TEST_SERVICE::tick`
  - `ROUND_PRESENTATION_SERVICE::tick`
  - `BUILD_COURSE_SESSION_MANAGER::tick`
  - `AUTO_COURSE_SERVICE::tick`
  - `McdgMod::handlePendingAutoStrictSetup`
  - `McdgMod::autosaveRoundSession`
  - `ResortCourseBuilder::tick`
  - `DiscFlightSimulator::tick`

**Cumulative overhead** even if individual handlers are light

**Recommended Fixes:**
1. Combine related handlers (e.g., all auto-test services into one)
2. Add early-exit conditions when services are inactive
3. Add tick handler timing metrics to identify expensive handlers
4. Consider conditional registration (only register when needed)

**Expected Impact:** Medium - Should reduce server tick overhead

---

### 5. Large Monolithic Classes

**Location:** 
- `src/main/java/com/mcdg/command/McdgAdminCommands.java` (2,264 lines)
- `src/main/java/com/mcdg/world/CoursePlacementService.java` (738 lines)
- `src/main/java/com/mcdg/game/HoleProgressTracker.java` (741 lines)

**Issue:**
- Not a direct performance issue
- Makes optimization difficult and increases code complexity
- Identified in AGENTS.md as needing splitting

**Recommended Fixes:**
1. Split `McdgAdminCommands` by command domain (course, round, debug)
2. Split `CoursePlacementService` into `BlockPlacer`, `SignTextGenerator`, `PlacementValidator`, `StructureCleaner`
3. Split `HoleProgressTracker` into `ThrowResolver`, `TurnManager`, `MiniMapSyncService`
4. Split `MiniMapRenderer` into `TerrainSampler`, `HazardOverlayRenderer`

**Expected Impact:** Low-Medium - Improves maintainability and enables targeted optimizations

---

## 🟢 Positive Performance Notes (Keep These)

1. **Tick-incremental course placement** - Recent implementation spreads course building across ticks to avoid lag spikes (Plan.md lines 110-118)

2. **Trajectory calculation instead of entity physics** - Player throws use mathematical calculation instead of spawning entities, which is more performant

3. **ConcurrentHashMap usage** - Server-side state management uses thread-safe collections appropriately

4. **Minimap caching** - Already has render cache with timeout mechanism

---

## 🎯 Implementation Plan

### Phase 1: Quick Wins (1-2 hours)
1. Increase minimap cache throttle to 750ms
2. Increase autosave interval to 100 ticks
3. Reduce particle trail duration to 2 seconds
4. Add timing metrics for tick handlers

### Phase 2: Structural Improvements (4-6 hours)
1. Implement async file I/O for round session saves
2. Reduce minimap texture resolution to 96×96
3. Consolidate server tick handlers
4. Add configurable performance settings

### Phase 3: Advanced Optimizations (8-12 hours)
1. Implement pixel sampling optimization for minimap
2. Refactor particle trail system
3. Split large monolithic classes
4. Add comprehensive performance monitoring

---

## 📊 Performance Monitoring

**Recommended Metrics to Track:**
- Server tick time (ms per tick)
- Client frame time (FPS)
- File I/O operation duration
- Particle system count
- Minimap render time
- Memory usage patterns

**Implementation:**
- Add timing metrics to critical code paths
- Log performance warnings when thresholds exceeded
- Consider adding `/mcdg perfstats` command for live monitoring

---

## 🧪 Testing

**Performance Validation Steps:**
1. Baseline measurement before changes
2. Test with single player in active round
3. Test with multiple players (2-4)
4. Test during course placement
5. Test during resort building
6. Monitor server tick time and client FPS
7. Profile with Minecraft's built-in profiler (F3 + L)

**Success Criteria:**
- Server tick time < 50ms consistently
- Client FPS > 60 during normal gameplay
- No perceptible stuttering during throws
- Smooth minimap updates
- No lag spikes during autosave

---

## 📝 Notes

- All file I/O operations should be reviewed for synchronous usage
- Consider adding performance configuration options for players with lower-end hardware
- Monitor after each disc glide phase implementation for performance regressions
- The recent particle trail addition (commit 022014b) may have introduced new performance overhead

---

## 🔗 Related Files

- `AGENTS.md` - Architecture notes and known hotspots
- `Plan.md` - Project roadmap and implementation status
- `DISC-GLIDE-PHYSICS.md` - Disc glide physics implementation plan
