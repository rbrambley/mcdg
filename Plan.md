---

## MCDG Coordinated Plan (Up To Date)

Date: 2026-05-30

---

## Minimap Baseline Update (2026-06-01)

Status:
- Navigation minimap baseline is now working and checkpointed.
- Baseline and guardrail commits:
  - 8a4e13c (north-up working render without rotation checkpoint)
  - 62314d2 (working minimap navigation baseline)
  - 31d175b (hardened minimap regression baseline checks)

Working baseline behavior (locked):
- Always-on player navigation minimap.
- Circular minimap render (no rotating square corner bleed).
- Player-up map rotation with rotating cardinal labels.
- Directional player icon (triangle) instead of center dot.
- Wider navigation view and higher terrain sample density.
- Enhanced topology readability (hillshade + contour emphasis).
- Heading stability improvements to reduce jump/airborne spin jitter.

Validation gate:
- quickRegression now enforces minimap baseline invariants in RegressionCheckRunner.
- Minimap changes must keep quickRegression green before merge/deploy.

Next focus:
- Begin round-play specific minimap integration changes behind this locked baseline.

---

## Round Play Polish Update (2026-06-01)

Completed this session:
- Basket finish zone is now the hopper plus the block above it; bars and lamp remain bounce-only surfaces.
- Basket green hazard exemption now uses the placed green radius, while water/lava OB and corridor OB still apply normally.
- `Building Course 0%` now stays visible until terrain generation updates begin, so the build state does not look stalled.
- Minimap now renders active-hole basket guides with an upright flag marker plus 100ft/200ft circles (unlabeled).
- Round HUD distance is feet-only and smoothly updates from lie-to-basket.
- Basket-structure contact above the make-zone now forces bounce-to-ring with `CLANK!` feedback.

Validation:
- quickRegression remains green after the gameplay and overlay adjustments.

---

## Placement Hardening Update (2026-06-01)

Completed this session:
- Generation/par rules updated for realistic carry playability:
  - carry cap logic uses 91 blocks (300 ft equivalent),
  - par bands are now 0-400 (Par 3), 401-700 (Par 4), 701-1200 (Par 5),
  - one forced Par 5 slot per 9-hole generation with stronger retry behavior.
- Alternate-route search widened for hard terrain cases and now validates both route legs against carry limits.
- Landing-gap validation is route-aware (tee->anchor->basket when an alternate anchor exists), not direct-line only.
- New enclosure recovery corridor for deeply enclosed baskets (all holes):
  - applies for basket depth 13-35 blocks below local surface,
  - builds an 8-wide stepped fairway back toward tee,
  - rises with max +2 step and prefers +1 when possible,
  - force-fills water to safe fairway,
  - reroutes around lava (up to 3 lateral attempts), then relocates basket if reroute fails.

Validation:
- quickRegression: PASS.
- Clean lifecycle deploy gate: PASS.
- Latest lifecycle smoke summary: pass=3, fail=0, issues=0, warningLandingGaps=0, maxLandingGap=63.

---

## Session Continuity Update (2026-05-31)

Note (2026-06-01):
- This section reflects the pre-fix minimap rebuild plan.
- It is retained for historical context, but the minimap baseline issues described below are now resolved.
- Use the "Minimap Baseline Update (2026-06-01)" section as current ground truth.

### Why this update exists

- Minimap behavior regressed into a mostly tan/beige square and does not match visible world terrain.
- Multiple incremental color/sampling tweaks were attempted and did not produce acceptable results.
- Team decision: stop blind minimap tweaks and move to a planned rebuild with measurable gates.

### Ground truth from current screenshots

- Minimap still fails to show meaningful terrain detail in both shoreline and tee/sign viewpoints.
- Water is visible in-world while minimap remains mostly uniform terrain color.
- This is a rendering/sampling pipeline issue, not day/night lighting.

### Active decision (approved)

- Freeze ad-hoc minimap tweaks.
- Rebuild minimap in phased steps with explicit validation before each next step.
- Keep old minimap path available behind a feature switch until replacement passes.

### Minimap Rebuild Plan (Always-On Navigation + Waypoints)

Goal:
- Always-on minimap (inside and outside rounds), terrain-correct, and waypoint-capable like a navigation tool.

Scope requirements:
- Always visible for player by default.
- Works when no active round payload exists.
- Waypoint add/remove/show workflow.
- Course semantics (hazard/OB/path) rendered as transparent overlays, not base-terrain replacement.

Phase 0: Observability first
- Add temporary debug readout for center sample source:
  - visible-surface sample path,
  - standable-surface fallback,
  - server fallback,
  - water-detected true/false.
- Purpose: stop blind iterations and identify dominant failing path immediately.

Phase 1: Base terrain renderer replacement
- Build terrain layer from visible surface (world-surface view), not standable-only surface.
- Water-first classification over full column between visible and standable tops.
- No course overlays in this phase.

Phase 2: Overlay layer
- Add hazard/OB/course lines as separate transparent pass.
- Keep overlay semantics independent from terrain color map.

Phase 3: Always-on navigation mode
- Player-centered minimap state when no active round exists.
- Preserve round-aware behavior when round payload is present.

Phase 4: Waypoints v1
- Add waypoint keybinds, in-memory list, and map rendering.
- Add persistence format for waypoints (client-side save/load).

Phase 5: Tile-cache and performance pass
- Incremental chunk/tile updates.
- Cache invalidation rules and low-jitter redraw cadence.

### Validation gates (must pass before advancing)

Navigation-first validation (no fixed screenshot gate):
- Validate while moving continuously through mixed terrain (shoreline, forest edge, elevation transitions, and open ground).
- Use minimap debug telemetry as the primary decision input:
  - center source type (`visible-surface`, `heightmap-fallback`, `chunk-unloaded`),
  - center fluid type (`solid`, `water`, `lava`),
  - center sampled Y,
  - source pixel counts (`vis`, `fb`, `miss`) from the live render summary.

Per-phase pass criteria:
- Phase 1 passes only when live navigation consistently shows distinct terrain features (water/shore/ground boundaries) and telemetry indicates expected source dominance (`visible-surface` preferred, fallback paths explainable).
- Phase 2 passes only when overlays are visible but do not flatten or recolor base terrain during movement.
- Phase 3 passes only when minimap remains active outside rounds with stable player-centered behavior during free navigation.
- Phase 4 passes only when waypoints can be created, rendered, removed, and reloaded during free navigation.

### Current status

- Phase 0 not implemented yet (planned next).
- New minimap renderer has not reached acceptable output quality.
- Strict deploy gate remains independently blocked at times by stochastic placement issue `basket_deeply_enclosed`; minimap testing currently uses artifact-only deploy when needed.

### Next session starting checklist

1. Keep Phase 0 observability active while running navigation-only tests (no round workflow required).
2. Traverse mixed terrain paths and collect live debug summaries (`src`, `fluid`, `y`, `vis/fb/miss`).
3. Decide exact Phase 1 sampling corrections using telemetry trends and in-motion visual correctness.
4. Only after Phase 1 passes navigation validation, proceed to overlays and always-on mode hardening.

### Current State Snapshot

Overall:
- Core gameplay loop is complete and stable in single-player.
- Strict mode, scoring, HUD/minimap, persistence/resume, and admin lifecycle are implemented.
- Regression tooling (quick + smoke + lifecycle/deploy gates) is active and in regular use.

Completed feature sets:
- Deterministic 9-hole generation and placement flow.
- Strict/casual ruleset switching and strict penalties.
- Tournament-feel pack v1 core: signature hole, tee package, round presentation, hole result callouts, final summary.
- Practice course persistence and resume.
- ATLauncher build-and-deploy workflow.
- Command-only permanent camp builder (`/mcdg buildcamp`) with 6-player yurt site, central campfires, pool, tennis, basketball, and bathhouse.
- Terrain-adaptive camp placement: each structure anchors to local surface Y (village-style), with local footprint clearing only.

Remaining focus:
- Closeout and verification for strict flow and resume safety.
- Reliability and release readiness (multiplayer, regressions, performance/compatibility).
- Operator/player usability completion (in-game help, menu-driven UX, persistence diagnostics).

Deferred by design:
- Dome generation.
- Physical OB markers (full world markers).
- League/tournament expansion systems.

---

## Execution Order (Best Order)

### 1) Dev Session Reliability (Immediate)

Goal:
- Remove strict-session startup deadlock risk so local testing is one-command reliable.

Tasks:
- Update strict session launcher flow so client launch does not depend on a player-joined completion event.
- Keep local dev auth settings forced (`online-mode=false`, `enforce-secure-profile=false`, localhost bind).
- Verify one clean server-up -> client-join sequence.

Exit criteria:
- `Run Strict Dev Session` reliably reaches in-world without manual recovery.

### 2) Strict Ruleset Final Closeout

Goal:
- Mark strict gameplay as fully closed and verified.

Tasks:
- Run one clean manual strict 9-hole round (`balanced` preset).
- Validate strict penalties and lie/throw progression on real throw events.
- Re-run persistent safety path: `practicecourse` -> restart -> `resumecourse`.

Exit criteria:
- No strict-flow regressions observed in manual run.
- Resume path verified in a fresh session.

### 3) Placement Hardening v2

Goal:
- Reduce placement edge-case failures while preserving generation speed/playability.

Tasks:
- Tighten tee/basket standable safety and enclosed-space checks.
- Keep alternate-route requirement for long water carries.
- Preserve known successful patterns:
  - resolve stable ground first, then clear headroom;
  - add periodic reachable landing options across long carries;
  - enforce short tee launch-lane clearing.

Exit criteria:
- Improved pass rate in placement autotest with no critical blockers.

### 4) Regression Coverage Expansion

Goal:
- Make remaining high-risk paths enforceable in automation.

Tasks:
- Extend checks for strict penalties, lie progression, round completion summary correctness.
- Add restart/resume validation coverage to regression path.
- Keep deploy gate strict (block on lifecycle smoke failures).

Exit criteria:
- quick/smoke/lifecycle suites cover strict + resume core risks.

### 5) Multiplayer Reliability Pass

Goal:
- Move multiplayer from partial to verified core reliability.

Tasks:
- 2-player smoke sessions.
- Disconnect/rejoin and mid-round resume behavior checks.
- Verify scoreboard/hole/round state consistency for both players.

Exit criteria:
- Repeatable 2-player round with no state desync.

### 6) Performance + Compatibility Documentation

Goal:
- Close remaining project-level release readiness gaps.

Tasks:
- Capture generation/runtime performance notes and limits.
- Produce compatibility matrix and known constraints.
- Record recommended server/client settings for stable sessions.

Exit criteria:
- Clear compatibility and performance guidance documented.

### 7) In-Game Help and Admin Docs Completion

Goal:
- Make usage discoverable without relying on external tribal knowledge.

Tasks:
- Add concise in-game help/tutorial UX for key commands and strict penalties.
- Update admin lifecycle docs with current scripts, command order, and debug bundle collection.

Exit criteria:
- New operator can run full lifecycle from docs/help only.

### 8) Menu-Driven UX + Persistence Expansion

Goal:
- Merge command-driven workflows with guided in-game menus and complete persistence quality-of-life features.

Tasks:
- Add admin/player menu entry points for common lifecycle actions (create/start/practice/resume/end/reset/ruleset).
- Add menu surfaces for strict presets and quick validation actions.
- Add persistence v2 features:
  - explicit snapshot status/health UI;
  - recovery actions for stale/corrupt snapshot;
  - scoped export/import design for course metadata (seed/layout/state) with safety checks.
- Keep command parity so all menu actions map to existing command behavior.

Exit criteria:
- Core lifecycle can be run via menu or commands with equivalent outcomes.
- Persistence diagnostics and recovery are usable without manual file edits.

### 9) Deferred Features (Only After 1-8)

Potential next wave:
- Dome generation.
- Optional minimal OB marker mode.
- Expanded signature templates.
- League/tournament systems.
- Course editor tooling.

---

## Merged One-Off Ideas Register

This register consolidates one-off plans and scattered ideas from setup/status notes into a single implementation view.

Implemented:
- Tournament Feel Pack v1 core:
  - signature hole builder;
  - tournament tee sign package;
  - event start sequence;
  - hole-result callouts and final summary;
  - ruleset toggle foundation (`casual`/`strict`).
- Practice course persistence/resume command lifecycle.
- Strict surface presets and ruleset command controls.
- ATLauncher safe deploy workflow and throw-debug bundle workflow.

In active closeout:
- Strict ruleset stabilization final pass (manual 9-hole strict verification).
- Resume safety re-validation in fresh session.
- Strict dev session launch-order reliability fix.

Planned next (post-closeout):
- Multiplayer reliability pass (2-player smoke + disconnect/rejoin + resume consistency).
- Placement hardening v2 and regression expansion.
- Menu-driven lifecycle/admin/player UX.
- Persistence v2 diagnostics/recovery and export/import design.

Deferred/optional ideas:
- Dome generation.
- Physical OB marker mode.
- Expanded signature templates.
- Course editor.
- League/tournament infrastructure.

---

## Current Verification Standard

Minimum green state before any major feature expansion:
1. `gradle build`
2. `gradle quickRegression smokeRegression`
3. lifecycle smoke/deploy gate passes
4. one manual strict 9-hole validation run
5. one `practicecourse` -> restart -> `resumecourse` validation run

---

## Command/Workflow Reference

Primary lifecycle commands:
- `/mcdg createcourse <seed>`
- `/mcdg startround`
- `/mcdg practicecourse`
- `/mcdg resumecourse`
- `/mcdg endround`
- `/mcdg resetcourse`
- `/mcdg cleanupcourse`
- `/mcdg ruleset <casual|strict>`
- `/mcdg buildcamp`

Camp command behavior:
- `/mcdg buildcamp` creates a separate (non-course-central) lodging site.
- Build is command-triggered only, never automatic during course placement.
- Uses unique-site checks to avoid building too near an existing camp marker.
- Structures are surface-first and can sit at slightly different Y levels.

Primary automation workflows:
- Build: `Build Mod`
- Strict local run: `Run Strict Dev Session`
- Manual strict debug: `Run Strict Manual Debug Session`
- Deploy gate + test deploy: `Build + Deploy to ATLauncher Test Instance`
- Throw debug bundle: `Collect Throw Debug Bundle`

---

## Done Definition (Current Project)

Project can be considered release-ready for current scope when:
1. Strict closeout validations are complete and repeatable.
2. Multiplayer reliability pass is complete for core 2-player scenarios.
3. Regression coverage includes strict + resume high-risk paths.
4. Performance/compatibility notes and operator docs are complete.
5. No P1/P2 issues in latest smoke/lifecycle runs.

---