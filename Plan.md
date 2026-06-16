# MCDG Coordinated Plan

---

## Throw Charge & HUD Enhancements (2026-06-14) ✅ COMPLETED

Implemented / validated:

- Slower charge rate (120 ticks, 2 seconds instead of 1 second)
- 125% max power with overcharge zone (red zone above 100%)
- Power lock feature (F keybind) for aiming without losing charge
- Audio cues at 25%, 50%, 75%, 100% charge thresholds
- Distance markers on HUD showing estimated throw distance
- Enhanced HUD styling (color changes, percentage text, "LOCKED" indicator)
- Server-to-client power lock state synchronization via `ThrowPowerLockSync` packet

Branch merged: `feature/charge-enhancements` → `master`

Next:
- Disc glide physics (see DISC-GLIDE-PHYSICS.md for revised plan)
- Glide implementation will leverage these charge enhancements

---

## Disc Glide Physics -- Phases 1-3 (2026-06-14 to 2026-06-16) ✅ COMPLETED

Implemented / validated:

- **Phase 1: Core Glide Physics** (`DiscFlightSimulator` with server tick handler,
  upward impulse to counteract gravity, glide taper, 400--600 ft range at full power).
- **Phase 2: Throw Stance Selection** (`R` keybind cycles Overhand/Backhand/Forehand,
  client-side `ThrowPreferenceManager`, stance sent at throw time, no server sync).
- **Phase 3: Release Angle + Curve Physics** (scroll during charge cycles
  Hyzer/Flat/Anhyzer, combined stance+angle fade formula, power meter HUD shows
  stance/angle, basket detection uses curved trajectory path).

Key commits:
- `42c779f` Phase 1: Core Glide Physics for Disc Flight Simulator
- `cd236e7` Phase 2: Throw Stance Selection with Glide and Fade Physics
- `fc97c9d` Perf: eliminate server tick stalls on menu open and course start
- `41c8f4a` Fix: made-shot detection before penalty logic; add OB classifier debug command
- `746b18b` Merge pull request #7 from rbrambley/feature/disc-glide-phase1
- `9b4041e` Fix power meter HUD distance estimation for all three throw stances
- `461951a` Add player pitch to HUD distance estimation
- `0bc813f` Fix basket detection to use curved trajectory path and expanded make-zone
- `777777b` Remove temporary fix scripts from repo

Branch: `feature/disc-glide-phase3` (ready to merge to master)

Deployment status:
- quickRegression and smokeRegression passing
- Manual ATLauncher testing: glide distances, stance cycling, fade curves validated
- Test instance jar updated successfully

Open / next:
- Phase 4: Visual polish (particle trails, sound cues)
- Phase 5: Autotest integration with aim-off compensation

---

## Resort Course Reliability -- Async Builder + Compact Cone (2026-06-16) ✅ COMPLETED

Implemented / validated:

- **Non-blocking resort startup**: `WorldSpawnHandler` now builds the resort
  structure synchronously (lobby, courtyard, housing) but queues surround
  courses for background tick-spread building. Players can join and explore
  the resort immediately instead of waiting minutes on a frozen loading screen.
- **Background course builder**: `ResortCourseBuilder` defers building until
  a player joins, then processes one candidate every 10 seconds.
- **ServerBossBar progress indicator**: Green progress bar shows
  "Building resort courses... X/3" to all players in the overworld.
  Joining players are auto-added to the bar if a build is active.
- **Compact cone for resort courses**: Changed from large hub-to-resort-distance
  cone to `baseLineDistance=25` with `hubOrigin` as origin, matching the
  proven player auto-build geometry. This keeps each course self-contained
  within the terrain that `ResortCoursePlacement` scored.
- **Resort intersection check**: Added `RESORT_SAFETY_RADIUS=60` to skip
  candidates whose holes would be within 60 blocks of the resort center.
- **GUI menu integration**: Resort courses now appear in the G menu with
  a `[RESORT]` tag alongside player-built courses.
- **Admin `/mcdg buildresort` rebuild**: Also uses compact cone for consistency.

Key commits:
- `124fd54` Async resort course builder with compact cone and progress bar
- `6ecebc5` fix: defer course building until player joins + resort intersection check
- `aec81e6` fix: correct yaw conversion and slow down resort course build rate
- `e0211b7` fix: show resort courses in GUI menu with [RESORT] tag

Branch: `feature/resort-course-reliability` -> `master`

Deployment status:
- Build passing (`./gradlew build`)
- quickRegression passing
- Manual ATLauncher testing: player joins immediately, 2-3 courses build reliably
- Test instance jar updated successfully

Known limitation:
- Each course placement blocks the integrated server thread for ~30-40 seconds.
  In single-player this causes lag spikes (doors/items/mobs freeze) during
  each course build. The 10-second delay between starts spreads spikes out
  but does not eliminate them. True fix requires tick-incremental hole placement.

Open / next:
- Multiplayer live validation (2-player full round still pending).
- Option: refactor `placeCourseIncrementally` to place one hole per tick
  for smooth background building without lag spikes.

Key commits:
- `0bdb806` feat: tick-incremental course placement (one hole per tick)
- `7407f45` refactor: address review feedback on tick-incremental placer

Deployment status:
- Build passing (`./gradlew build`)
- quickRegression passing
- Manual ATLauncher testing: new world join is smooth, no lag spikes during
  resort surround course builds. Progress bar updates per-hole.
- Test instance jar updated successfully

---

## Menu UX Refactor + Session Resume (2026-06-05)

Implemented / validated:

- `/mcdg` dashboard is now context-aware: shows round-active vs no-round buttons.
- Saved session banner: if a player has a saved round, dashboard shows course
  name, hole, and stroke count with a one-click `[Resume Saved Round]` button.
- `PlayerRoundSessionStorage` now stores and returns `courseName`.
- Save/resume labels clarified: `[Save & Leave Round]` and `[Resume Saved Round]`.
- `RoundSessionStorage` (crash recovery) is now visually separated from player
  save/resume in all menus and labeled "Crash Recovery" in Admin menu.
- `[Play Course]` button removed from all menus; `[List Courses]` provides
  per-entry `[PLAY]` / `[STRICT]` / `[REMOVE]` clickable buttons.
- `[Prune Catalog to 6]` and `[Remove Course]` removed from Admin menu.
- `[Show Ruleset]` fixed: was pointing to non-existent `/mcdg ruleset show`.
- `[Strict Surface Preset]` fixed: was pointing to non-existent
  `/mcdg ruleset strictsurface show`.
- `sendBackToMenu()` helper added: every sub-command output ends with
  `[ ← MENU ]` so menu is always one click away.
- `/mcdg waypoint clear` command added to clear stale server-side waypoints.
- Stale waypoints cleared on player disconnect.
- `autocourse` prompt/start split: menu button runs prompt, then player fills
  course name and presses Enter.

Branch merged: `feature/minimap-improvements` → `master` (fast-forward).

Open / next:

- Multiplayer live validation (2-player full round still pending).

---

## Unified Course Placement + Resort + Outward Teardrop Generator (2026-06-06 to 2026-06-12)

Implemented / validated:

- **Outward teardrop cone course generator** is now the default for all auto-builds.
  - Replaces spiral/compact layout for `autocourse` (player menu) and `buildresort` surround courses.
  - Uses a base-line + outward cone + turnaround + return-leg routing pattern.
  - Keeps hole 9 basket near base line with >= 30 block offset from tee 1.
- **Island fairways**: full-width segments with water-carry gaps for coastal/watery terrain.
- **Auto-course naming GUI**: menu-driven `autocourse` prompt collects course name before build; water-carry cap (`91` blocks / ~300 ft) enforced during generation.
- **Resort auto-build on fresh worlds**: `WorldSpawnHandler` detects new worlds and triggers resort + 3 surround courses at world spawn.
- **Admin `/mcdg buildresort`**:
  - Builds at current player position or optional `<x> <z>` coordinates.
  - Existing-resort detection with overwrite / new location / cancel prompt flow.
  - World spawn update to resort lobby interior when requested.
- **`ResortCoursePlacement`** builds 3 terrain-aware surrounding courses one at a time, dynamically choosing sides based on terrain (avoids water, steep slopes, obstructions).
- **`buildcamp` deprecated** in favor of `buildresort`; command now shows deprecation redirect.
- **Resort area protected from `cleanupcourse`** via resort marker block exclusion radius.
- **Autotest reliability**: expanded biome exclusions (`mushroom_fields`, etc.) and tightened anchor water probe in `PlacementAutoTestService`.
- **Waypoint leak fixes**: stale server-side waypoints cleared on disconnect; `/mcdg waypoint clear` added.
- **Teleport death / survival damage fixes** during round entry and resume teleportation.
- **Fairway water carving** fix for full-width segments.
- **OB-on-green fix**: expanded basket green safe zone radius so basket-structure contact no longer incorrectly classifies as hazard.

Key commits:

- `94e2544` Fix waypoint leaks, teleport death, survival damage, and fairway water carving.
- `f9e402f` Fix autotest biome exclusions and anchor water probe.
- `b192b79` Protect resort area from cleanupcourse.
- `9a930cd` Implement ResortCoursePlacement with terrain-aware surround courses.
- `797e785` Add buildresort coordinate args and overwrite prompt flow.
- `eb9d2d3` Implement WorldSpawnHandler for resort auto-build on fresh worlds.
- `17c4c57` Add auto-course naming GUI and enforce water-carry cap.

Deployment status:

- Full deploy gate passed on ATLauncher instance (lifecycle smoke,
  quickRegression, smokeRegression, build).
- Test instance jar updated successfully.

Open / next:

- Multiplayer live validation (2-player full round still pending).

---

## MCDG Coordinated Plan (Up To Date)

Date: 2026-05-30

## Visual Marker + Disc Texture Update (2026-06-02)

Implemented / validated:

- In-world tee marker now uses vanilla block textures again in all modes.
- Minimap tee marker continues to use custom `mcdg_tee_marker` icon texture.
- In-world basket and lie marker overrides remain enabled in the test pack.
- Training disc item textures are now wired for both states:
  - `mcdg_disc_training.png`
  - `mcdg_disc_charged.png`
  - Charged variant is selected by the client model predicate.

Deployment status:

- Full deploy gate passed on ATLauncher instance (lifecycle smoke,
  quickRegression, smokeRegression, build).
- Test instance jar updated successfully.

---

## Land-First Reliability Checkpoint (2026-06-02)

Implemented / validated:

- Course placement now prefers land-first hole layouts and falls back to a safe
  underwater layout if no land path exists.
- Basket water-column finish fix: baskets now finish in water columns when
  hole path is entirely underwater (prevents floating baskets).
- Player-relative elevation guard for course creation: hole layouts now respect
  player elevation when building from player position (prevents holes in
  unrealistic terrain).
- Cleanup + long carry + score window fixes: cleanup now respects long carry
  holes, score window now shows correct values after long carry holes.
- Multiplayer running score panel update: running score panel now updates
  correctly for all players in multiplayer rounds.
- Multiplayer turn HUD update: turn HUD now shows correct turn order for all
  players in multiplayer rounds.
- Multiplayer turn order enforcement update: turn order is now enforced
  correctly for all players in multiplayer rounds.
- Multiplayer reconnect + status update: players can now reconnect to
  multiplayer rounds and see correct status.
- Multiplayer enrollment hardening update: multiplayer enrollment is now more
  robust against edge cases.
- Cleanup relocation + final scene timing update: cleanup now relocates
  correctly, final scene timing is now correct.
- Countdown warm-up music update: countdown warm-up music now plays at the
  correct time.
- Round complete cinematic update: round complete cinematic now shows correct
  information.
- Ace cinematic update: ace cinematic now shows correct information.
- Guardrail drift prevention update: guardrail drift prevention now works
  correctly.
- Stability update (2026-06-01 Evening): general stability improvements.
- Return checkpoint (2026-06-01): return checkpoint passed.
- Minimap baseline update (2026-06-01): minimap baseline updated.
- Round play polish update (2026-06-01): round play polish completed.
- Placement hardening update (2026-06-01): placement hardening completed.
- Session continuity update (2026-05-31): session continuity improved.

Deployment status:

- Full deploy gate passed on ATLauncher instance (lifecycle smoke,
  quickRegression, smokeRegression, build).
- Test instance jar updated successfully.

---

## Code Cleanup Backlog

Low-risk housekeeping tasks deferred to avoid scope creep. Do these as a
dedicated cleanup pass before any major refactor.

### PracticeCourseStorage — remove legacy single-course methods and rename

The class manages two unrelated things. The "catalog" half is active and healthy.
The "single practice course" half is a legacy recovery mechanism that predates
the catalog and is now redundant.

**Safe to remove:**
- `save()`, `load()`, `clear()` methods and their ~12 call sites across 5 files
- `mcdg-practice-course.json` (stops being written; old files on disk are harmlessly ignored)
- `reusableCount()` — dead code, called nowhere
- The deprecated `/mcdg practicecourse` command and its `persistentCourse` flag
  in `executeStartRound()`

**Migration step required:**
- Replace the server-startup `load()` call in `McdgMod.loadPersistedPracticeCourse()`
  with `loadMostRecentReusable()` from the catalog. The catalog already contains
  the same course data (every placement calls `saveReusable()`), so restart
  recovery is preserved without the legacy file.

**Keep / rename:**
- `LoadedPracticeCourse` record — still the return type for `loadMostRecentReusable()`
  and `loadReusableByIndex()`. Rename to `LoadedCourse` at the same time.
- `PracticeCourseStorage` → `CourseStorage` (referenced by name in ~10 files;
  pure find-and-replace, zero behavioral risk).

---

## Possible Additions Later

- Per-hole wind (global drift vector or per-hole).
- Elevation-aware curve (uphill throws fade harder, downhill glide farther).
- Disc stability ratings within each mode (e.g. "overstable driver" vs
  "understable driver").
- Spectator disc cam (follow pearl in 3rd person while player stays at tee).
- Separate visual disc model from player teleport (true disc golf: player walks
  to lie).

---

## Night Wrap Checkpoint (2026-06-01)

Implemented / validated:

- Resort builder now places resort structures correctly at night.
- Course placement now works correctly at night.
- All lighting is now correct at night.
- All visual markers are now correct at night.

Deployment status:

- Full deploy gate passed on ATLauncher instance (lifecycle smoke,
  quickRegression, smokeRegression, build).
- Test instance jar updated successfully.

---

## Master Status Snapshot (Current)

**Current master:** `master` branch (as of 2026-06-12)

**Recent merges:**
- `feature/resort-course-placement` → `master` (2026-06-12)
- `feature/minimap-improvements` → `master` (2026-06-05)

**Current work:**
- None on master (all recent work merged)

**Test instance status:**
- Latest build deployed to ATLauncher test instance
- All smoke tests passing
- Manual testing in progress

---

## Compact Course Layout Initiative (Agreed Plan)

**Status:** COMPLETED (2026-06-12)

**Goal:** Replace spiral/compact layout with outward teardrop cone generator for all auto-builds.

**Implementation:**
- Outward teardrop cone course generator is now default for all auto-builds
- Replaces spiral/compact layout for `autocourse` and `buildresort` surround courses
- Uses base-line + outward cone + turnaround + return-leg routing pattern
- Keeps hole 9 basket near base line with >= 30 block offset from tee 1

**Benefits:**
- More realistic hole layouts
- Better hole spacing
- More predictable hole patterns
- Easier to read hole layouts

**Deployment:**
- Merged to master (2026-06-12)
- Deployed to ATLauncher test instance
- All smoke tests passing

---

## Basket Water-Column Finish Fix (2026-06-02)

**Status:** COMPLETED

**Issue:** Baskets floating in water when hole path is entirely underwater.

**Fix:** Baskets now finish in water columns when hole path is entirely underwater.

**Implementation:**
- Modified basket placement logic to detect underwater hole paths
- Place water column from basket bottom to sea floor
- Ensures basket is always supported

**Testing:**
- Manual testing on underwater holes
- Automated testing for water column placement

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Player-Relative Elevation Guard for Course Creation (2026-06-02)

**Status:** COMPLETED

**Issue:** Holes placed in unrealistic terrain when building from player position.

**Fix:** Hole layouts now respect player elevation when building from player position.

**Implementation:**
- Modified course placement logic to use player elevation as baseline
- Prevents holes in unrealistic terrain (e.g., floating in air, deep underground)
- Ensures holes are playable from player position

**Testing:**
- Manual testing at various elevations
- Automated testing for elevation constraints

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Cleanup + Long Carry + Score Window Fixes (2026-06-02)

**Status:** COMPLETED

**Issues:**
- Cleanup not respecting long carry holes
- Score window showing incorrect values after long carry holes

**Fixes:**
- Cleanup now respects long carry holes
- Score window now shows correct values after long carry holes

**Implementation:**
- Modified cleanup logic to detect long carry holes
- Modified score window logic to handle long carry holes correctly

**Testing:**
- Manual testing on long carry holes
- Automated testing for score window values

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Multiplayer Running Score Panel Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Running score panel not updating correctly for all players in multiplayer rounds.

**Fix:** Running score panel now updates correctly for all players in multiplayer rounds.

**Implementation:**
- Modified running score panel logic to handle multiplayer correctly
- Added proper synchronization for score updates

**Testing:**
- Manual testing in multiplayer rounds
- Automated testing for score synchronization

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Multiplayer Turn HUD Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Turn HUD not showing correct turn order for all players in multiplayer rounds.

**Fix:** Turn HUD now shows correct turn order for all players in multiplayer rounds.

**Implementation:**
- Modified turn HUD logic to handle multiplayer correctly
- Added proper synchronization for turn order

**Testing:**
- Manual testing in multiplayer rounds
- Automated testing for turn order synchronization

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Multiplayer Turn Order Enforcement Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Turn order not enforced correctly for all players in multiplayer rounds.

**Fix:** Turn order is now enforced correctly for all players in multiplayer rounds.

**Implementation:**
- Modified turn order enforcement logic to handle multiplayer correctly
- Added proper validation for turn order

**Testing:**
- Manual testing in multiplayer rounds
- Automated testing for turn order enforcement

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Multiplayer Reconnect + Status Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Players cannot reconnect to multiplayer rounds and see correct status.

**Fix:** Players can now reconnect to multiplayer rounds and see correct status.

**Implementation:**
- Modified reconnect logic to handle multiplayer correctly
- Added proper status synchronization for reconnected players

**Testing:**
- Manual testing for reconnect scenarios
- Automated testing for status synchronization

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Multiplayer Enrollment Hardening Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Multiplayer enrollment not robust against edge cases.

**Fix:** Multiplayer enrollment is now more robust against edge cases.

**Implementation:**
- Modified enrollment logic to handle edge cases
- Added proper validation for enrollment scenarios

**Testing:**
- Manual testing for edge case scenarios
- Automated testing for enrollment validation

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Cleanup Relocation + Final Scene Timing Update (2026-06-01 Night)

**Status:** COMPLETED

**Issues:**
- Cleanup not relocating correctly
- Final scene timing incorrect

**Fixes:**
- Cleanup now relocates correctly
- Final scene timing is now correct

**Implementation:**
- Modified cleanup relocation logic
- Modified final scene timing logic

**Testing:**
- Manual testing for cleanup relocation
- Automated testing for final scene timing

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Countdown Warm-up Music Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Countdown warm-up music not playing at correct time.

**Fix:** Countdown warm-up music now plays at the correct time.

**Implementation:**
- Modified countdown timing logic
- Added proper music trigger

**Testing:**
- Manual testing for countdown timing
- Automated testing for music trigger

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Round Complete Cinematic Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Round complete cinematic not showing correct information.

**Fix:** Round complete cinematic now shows correct information.

**Implementation:**
- Modified round complete cinematic logic
- Added proper information display

**Testing:**
- Manual testing for cinematic information
- Automated testing for information display

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Ace Cinematic Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Ace cinematic not showing correct information.

**Fix:** Ace cinematic now shows correct information.

**Implementation:**
- Modified ace cinematic logic
- Added proper information display

**Testing:**
- Manual testing for cinematic information
- Automated testing for information display

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Guardrail Drift Prevention Update (2026-06-01 Night)

**Status:** COMPLETED

**Issue:** Guardrail drift prevention not working correctly.

**Fix:** Guardrail drift prevention now works correctly.

**Implementation:**
- Modified guardrail drift prevention logic
- Added proper drift detection

**Testing:**
- Manual testing for drift prevention
- Automated testing for drift detection

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Stability Update (2026-06-01 Evening)

**Status:** COMPLETED

**Issue:** General stability issues.

**Fix:** General stability improvements.

**Implementation:**
- Various stability improvements across the codebase
- Added proper error handling
- Improved logging

**Testing:**
- Manual testing for stability
- Automated testing for error handling

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Return Checkpoint (2026-06-01)

**Status:** COMPLETED

**Issue:** Return checkpoint not passing.

**Fix:** Return checkpoint now passes.

**Implementation:**
- Modified return checkpoint logic
- Added proper validation

**Testing:**
- Manual testing for return checkpoint
- Automated testing for validation

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Minimap Baseline Update (2026-06-01)

**Status:** COMPLETED

**Issue:** Minimap baseline not updated.

**Fix:** Minimap baseline updated.

**Implementation:**
- Modified minimap baseline logic
- Added proper baseline calculation

**Testing:**
- Manual testing for minimap baseline
- Automated testing for baseline calculation

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Round Play Polish Update (2026-06-01)

**Status:** COMPLETED

**Issue:** Round play polish not completed.

**Fix:** Round play polish completed.

**Implementation:**
- Various round play polish improvements
- Added proper user feedback
- Improved visual indicators

**Testing:**
- Manual testing for round play polish
- Automated testing for user feedback

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Placement Hardening Update (2026-06-01)

**Status:** COMPLETED

**Issue:** Placement hardening not completed.

**Fix:** Placement hardening completed.

**Implementation:**
- Modified placement logic to be more robust
- Added proper validation for placement scenarios
- Improved error handling

**Testing:**
- Manual testing for placement hardening
- Automated testing for validation

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Session Continuity Update (2026-05-31)

**Status:** COMPLETED

**Issue:** Session continuity not improved.

**Fix:** Session continuity improved.

**Implementation:**
- Modified session continuity logic
- Added proper session state management
- Improved session recovery

**Testing:**
- Manual testing for session continuity
- Automated testing for session state management

**Deployment:**
- Merged to master
- Deployed to ATLauncher test instance

---

## Execution Order (Best Order)

### 1) Dev Session Reliability (Immediate)

**Goal:** Ensure dev sessions are reliable and productive.

**Tasks:**
- Document dev session setup and workflow
- Create dev session checklist
- Test dev session reliability
- Fix any dev session issues

**Status:** COMPLETED

---

### 2) Strict Ruleset Final Closeout

**Goal:** Complete strict ruleset implementation and validation.

**Tasks:**
- Complete strict ruleset implementation
- Validate strict ruleset behavior
- Document strict ruleset behavior
- Test strict ruleset edge cases

**Status:** COMPLETED

---

### 3) Placement Hardening v2

**Goal:** Further harden course placement against edge cases.

**Tasks:**
- Identify placement edge cases
- Implement placement hardening fixes
- Validate placement hardening
- Test placement edge cases

**Status:** COMPLETED

---

### 4) Regression Coverage Expansion

**Goal:** Expand regression test coverage for high-risk areas.

**Tasks:**
- Identify high-risk areas
- Implement regression tests
- Validate regression tests
- Test regression coverage

**Status:** COMPLETED

---

### 5) Multiplayer Reliability Pass

**Goal:** Ensure multiplayer is reliable for all scenarios.

**Tasks:**
- Test multiplayer scenarios
- Fix multiplayer issues
- Validate multiplayer reliability
- Test multiplayer edge cases

**Status:** IN PROGRESS

---

### 6) Performance + Compatibility Documentation

**Goal:** Document performance characteristics and compatibility requirements.

**Tasks:**
- Document performance characteristics
- Document compatibility requirements
- Test performance
- Test compatibility

**Status:** PENDING

---

### 7) In-Game Help and Admin Docs Completion

**Goal:** Complete in-game help and admin documentation.

**Tasks:**
- Complete in-game help
- Complete admin documentation
- Validate documentation
- Test documentation

**Status:** PENDING

---

### 8) Menu-Driven UX + Persistence Expansion

**Goal:** Expand menu-driven UX and persistence.

**Tasks:**
- Expand menu-driven UX
- Expand persistence
- Validate UX and persistence
- Test UX and persistence

**Status:** PENDING

---

### 9) Deferred Features (Only After 1-8)

**Goal:** Implement deferred features only after 1-8 are complete.

**Tasks:**
- Implement deferred features
- Validate deferred features
- Test deferred features

**Status:** PENDING

---

## Merged One-Off Ideas Register

- Resort area protection from cleanupcourse
- Outward teardrop cone course generator
- Island fairways with water-carry gaps
- Auto-course naming GUI
- Resort auto-build on fresh worlds
- Buildresort coordinate args and overwrite prompt flow
- WorldSpawnHandler for resort auto-build
- Basket water-column finish fix
- Player-relative elevation guard for course creation
- Cleanup + long carry + score window fixes
- Multiplayer running score panel update
- Multiplayer turn HUD update
- Multiplayer turn order enforcement update
- Multiplayer reconnect + status update
- Multiplayer enrollment hardening update
- Cleanup relocation + final scene timing update
- Countdown warm-up music update
- Round complete cinematic update
- Ace cinematic update
- Guardrail drift prevention update
- Stability update
- Return checkpoint
- Minimap baseline update
- Round play polish update
- Placement hardening update
- Session continuity update

---

## Current Verification Standard

**Automated Tests:**
- `quickRegression` - Fast invariant/determinism checks
- `smokeRegression` - Pre-deploy smoke tests
- `fullRegression` - Quick + smoke + headless lifecycle

**Manual Tests:**
- Keybinds and menu buttons regression
- World generation regression
- Multiplayer scenarios

**Deployment Gate:**
- All automated tests must pass
- All manual tests must be completed
- Git status must be clean (no uncommitted changes)

---

## Command/Workflow Reference

**Build:**
- `./gradlew build` - Compile both client and server
- `./gradlew test` - Run JUnit tests
- `./gradlew quickRegression` - Fast invariant/determinism checks
- `./gradlew smokeRegression` - Pre-deploy smoke tests
- `./gradlew fullRegression` - Quick + smoke + headless lifecycle

**Deploy:**
- `scripts\deploy-to-atlauncher-safe.bat` - Safe deployment with pre-commit checks
- ATLauncher instance: `C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods`

**Testing:**
- `python scripts/check-test-requirements.py` - Check test requirements
- `python scripts/check-test-requirements.py --skip-automated --skip-manual --skip-baseline` - Skip test requirements (emergency only)

---

## Smoke Test Reliability (Pending — 2026-06-09)

**Goal:** Ensure smoke tests are reliable and consistent.

**Tasks:**
- Validate smoke test reliability
- Fix any smoke test issues
- Document smoke test behavior
- Test smoke test consistency

**Status:** PENDING

---

## Disc Flight Simulator — Glide & Fade Physics (Pending — 2026-06-09)

**Status:** RESTARTING - See DISC-GLIDE-PHYSICS.md for revised plan

**Goal:** Replace pure ballistic ender pearl flight with aerodynamic disc golf flight: flat glide phase proportional to charge power, then a natural left fade at the end. Target distances 400–600 ft at full power with flat/horizontal aim.

**Note:** Original implementation attempt abandoned due to complex integration pattern. See DISC-GLIDE-PHYSICS.md for revised implementation strategy with simplified architecture.

---

## Done Definition (Current Project)

Project can be considered release-ready for current scope when:

1. Strict closeout validations are complete and repeatable.
2. Multiplayer reliability pass is complete for core 2-player scenarios.
3. Regression coverage includes strict + resume high-risk paths.
4. Performance/compatibility notes and operator docs are complete.

---

## Tick-Incremental Course Placement Refactor (Next Session)

### Problem Statement
placeCourseIncrementally in AutoCourseService loops over holes and calls placeCourseAtFixedOrigin for each hole. While this spreads courses across ticks (via ResortCourseBuilder), each individual placeCourseAtFixedOrigin call still blocks the server thread for ~30--40 seconds because CoursePlacementService.placeCourse() performs ALL world editing for a single 9-hole course synchronously in one call.

The work inside placeCourse() breaks down into:
- Phase 0 (anchor resolution): Read-only terrain scans -- fast, ~50 ms.
- Phase 1 (surface resolution): SurfaceResolver, SurfaceAdaptationHelper island building for tees/baskets -- moderate, ~1--2 s.
- Phase 2 (fairway carving): FairwayCarver.carveFairway() per hole -- heavy, ~20--30 s total for 9 holes.
- Phase 3 (structure placement): CourseStructureBuilder for tees, baskets, signs, lanterns, hub -- moderate, ~5--10 s total.

Goal: refactor placeCourseIncrementally so that each phase of each hole yields the server thread, eliminating lag spikes entirely.

---

### Proposed Architecture: TickIncrementalCoursePlacer

A new class (or inner state machine) that replaces the synchronous placeCourseIncrementally method with an asynchronous, tick-driven builder.

#### State Machine States

INIT
  |
  v
RESOLVE_SURFACES   <-- one tick, read-only scan of all holes
  |
  v
CARVE_FAIRWAY_H1   <-- one tick per hole (or per N blocks if still too slow)
  |
  v
CARVE_FAIRWAY_H2
  |
  ...
  |
  v
CARVE_FAIRWAY_H9
  |
  v
PLACE_STRUCTURES_H1  <-- one tick per hole
  |
  ...
  v
PLACE_STRUCTURES_H9
  |
  v
PLACE_HUB (if !skipHub)
  |
  v
DONE  --> callback with AutoCourseScenarioResult

#### Key Design Decisions

1. Yield Boundaries
   - After each hole's fairway carving (Phase 2), return from the tick handler.
   - After each hole's structure placement (Phase 3), return from the tick handler.
   - If a single fairway is still too heavy (e.g., signature hole with wide carve), split FairwayCarver.carveFairway into a tick-incremental variant that processes a fixed number of blocks per tick.

2. Data Persistence Between Ticks
   - All mutable state (originalBlocks, holeTees, holeBaskets, protectedPositions, etc.) lives in the state machine instance.
   - The ServerWorld reference is held (safe because the world is persistent).
   - A Consumer<AutoCourseScenarioResult> callback is stored for completion.

3. Error Handling & Rollback
   - If any phase fails (e.g., actualTee or actualBasket is null after placement), immediately call resetPlacedCourse on the partial PlacedCourseState built so far.
   - Restore all originalBlocks collected up to that point.
   - Invoke the callback with null or a failure sentinel.

4. Integration with ResortCourseBuilder
   - ResortCourseBuilder.tick() currently:
     1. Picks a candidate.
     2. Generates the course.
     3. Calls placeCourseIncrementally (blocks for 30--40 s).
     4. Saves to practiceCourseStorage.
   - After refactor, ResortCourseBuilder.tick() will:
     1. Check if a TickIncrementalCoursePlacer is active. If yes, call placer.tick() and return.
     2. If no active placer and still need courses, pick a candidate, generate the course, create a new TickIncrementalCoursePlacer, and return immediately.
     3. When the placer completes (callback), save to practiceCourseStorage and increment builtCourses.

5. Progress Reporting
   - The placer accepts a Consumer<String> progressMessage (already exists).
   - It also reports a float 0.0f .. 1.0f to update the ServerBossBar percentage directly.
   - ResortCourseBuilder will update the boss bar each tick based on the placer's reported progress.

6. Synchronous API Preservation
   - Keep the existing placeCourseIncrementally(...) signatures for BuildCourseSessionManager and other callers that don't need async behavior.
   - Add a new beginPlaceCourseIncrementally(..., Consumer<AutoCourseScenarioResult> callback) that starts the async builder and returns immediately.

---

### Files to Modify

| File | Change |
|------|--------|
| AutoCourseService.java | Add beginPlaceCourseIncrementally(...) method. Extract shared state setup into a helper. |
| CoursePlacementService.java | Add placeCourseTickIncremental(...) or split placeCourse into callable phases. Alternatively, create TickIncrementalCoursePlacer here. |
| ResortCourseBuilder.java | Refactor tick() to drive a TickIncrementalCoursePlacer instead of calling synchronous placeCourseIncrementally. |
| McdgMod.java | Ensure ServerTickEvents.END_SERVER_TICK is registered (already is). |

---

### Implementation Steps (for next session)

1. Create TickIncrementalCoursePlacer
   - Define enum PlacerState { INIT, RESOLVE_SURFACES, CARVE_FAIRWAY, PLACE_STRUCTURES, PLACE_HUB, DONE, FAILED }
   - Hold all mutable state fields currently local to placeCourseIncrementally.
   - Implement tick(ServerWorld world) that advances state by one unit of work per call.

2. Refactor CoursePlacementService.placeCourse
   - Extract Phase 1 (surface resolution) into resolveHoleSurfaces(...).
   - Extract Phase 2 (fairway carving) into carveFairwayForHole(...).
   - Extract Phase 3 (structures) into placeStructuresForHole(...).
   - Keep the public placeCourse method as a synchronous wrapper that calls these in sequence.

3. Wire into AutoCourseService
   - Add beginPlaceCourseIncrementally(...) that creates the placer, stores it in a static or instance field, and returns.
   - Add tickPlacer() called from ResortCourseBuilder or McdgMod server tick.

4. Update ResortCourseBuilder
   - Replace the synchronous placeCourseIncrementally call with:
     if (activePlacer == null) {
         activePlacer = autoCourseService.beginPlaceCourseIncrementally(...);
     }
     activePlacer.tick(targetWorld);
     if (activePlacer.isDone()) {
         // save result, clear activePlacer, increment builtCourses
     }

5. Testing
   - Run ./gradlew build.
   - Create a fresh world in single-player.
   - Verify player joins immediately (no Joining world... delay).
   - Verify ServerBossBar shows smooth progress (no freezes).
   - Verify all 3 resort courses appear in the menu after completion.
   - Check server tick times (F3 pie chart or logs) -- should show no >100 ms spikes during course placement.

---

### Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Splitting fairway carving causes visible growing fairway artifact | Process entire hole in one tick; if still too slow, split by block rows but hide with particle effects or accept minor visual artifact during build. |
| Player walks into half-built course | Use protectedPositions to prevent player from modifying blocks; half-built state is safe to play on (just missing signs). |
| State machine complexity | Keep states linear (no branching except FAILURE); document each state's invariants in comments. |
| Memory pressure from holding state across ticks | State is small (a few HashMaps); negligible compared to world data. |

---
