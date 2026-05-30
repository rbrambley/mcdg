---

## MCDG Coordinated Plan (Up To Date)

Date: 2026-05-30

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