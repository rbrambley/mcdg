---

# PROJECT-SETUP.md

## Latest Feature Checkpoint (2026-06-01 Night - Multiplayer Enrollment Hardening)

- Code checkpoint commit: 6496c83.
- Added updates:
  - `startround`, `practicecourse`, and `resumecourse` now use explicit participant enrollment instead of auto-enrolling everyone in the world.
  - Added optional `<players>` targets for those commands.
  - Added `/mcdg joinround [players]` for safe late-join enrollment into a live round.
  - Core command lifecycle now clears round state for tracked participants only instead of global round-state wipes.
- Validation at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle smokeRegression`: PASS.
  - `gradle build`: PASS.

## Multiplayer Compatibility Roadmap (Later)

- Add an optional compatibility mode for mixed clients (server mod required, client mod optional).
- In compatibility mode, keep core server-authoritative gameplay available while disabling client-only HUD/cinematic features for vanilla clients.
- Maintain an explicit support matrix:
  - same-version modded clients: full feature set,
  - vanilla clients: reduced feature set.

## Latest Feature Checkpoint (2026-06-01 Night - Cleanup Relocation + Final Scene Timing)

- Code checkpoint commit: 0141867.
- Added updates:
  - `clearcourse` now uses hybrid safe relocation (near player -> admin anchor -> spawn fallback) instead of fixed spawn-only evacuation.
  - Round-complete cinematic duration set to 20 seconds.
- Validation + deploy at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle build`: PASS.
  - jar deployed to ATLauncher with local/remote hash match.

## Latest Feature Checkpoint (2026-06-01 Night - Countdown Warm-up Music)

- Code checkpoint commit: 480f4c9.
- Added feature:
  - Randomized warm-up music during the existing 30-second round countdown.
  - Shuffle-bag anti-repeat track selection across multiple countdown starts.
  - Final 3-second bell sting before round goes live.
- Validation at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle build`: PASS.

## Latest Feature Checkpoint (2026-06-01 Night - Round Complete Cinematic)

- Code checkpoint commit: f1c4c70.
- Added feature:
  - Round-complete cinematic presentation tied to the existing summary/chat completion flow.
  - New S2C payload `RoundCompleteCinematicSync` for leaderboard snapshot display.
  - Client overlay is timed and skippable (movement/jump), with no gameplay-state blocking.
- Validation at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle build`: PASS.

## Latest Feature Checkpoint (2026-06-01 Night)

- Code checkpoint commit: 3c29765.
- Added feature:
  - Ace cinematic presentation wired to the existing Ace hole-result event path.
  - Server-to-client payload (`AceCinematicSync`) triggers a short client celebration card + particles.
  - Single-source trigger design avoids duplicate Ace detection/drift.
- Validation at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle build`: PASS.

## Latest Engineering Checkpoint (2026-06-01 Evening)

- Code checkpoint commit: fe5ca6f.
- Included fixes:
  - Par 5 cap behavior for 9-hole generation (forced slot only).
  - `startround` retry expansion for route-gap and alternate-route placement failures.
  - Strict minimap hazard overlay visibility correction (alpha + sampling + surface-Y fix).
- Validation at checkpoint:
  - `gradle quickRegression`: PASS.
  - `gradle build`: PASS.
- Deploy verification:
  - jar copied to the ATLauncher test instance mods directory after passing build.

## Prerequisites & Environment

- **Java Development Kit (JDK):**  
  - Install the latest LTS version (e.g., JDK 17 or 21).
- **Minecraft Java Edition:**  
  - Install the version you want to mod (e.g., 1.20.x).
- **Minecraft Mod Loader:**  
  - Choose and install Forge or Fabric for your target MC version.
- **IDE:**  
  - Install Visual Studio Code (VS Code) or IntelliJ IDEA.
- **Build Tools:**  
  - Gradle (usually included with modding templates).
- **Git:**  
  - For version control (optional but recommended).

## VS Code & Copilot Setup

- **GitHub Copilot Extension:**  
  - Install and sign in with your GitHub account.
- **Copilot Chat/Autopilot (if available):**  
  - Enable for interactive coding and file editing.
- **Java Extension Pack:**  
  - For Java support in VS Code.

## Project Initialization

- **Modding Template:**  
  - Use a Forge or Fabric mod template to scaffold the project.
- **Project Structure:**  
  - Set up folders as described in Plan.md (src/main/java, resources, etc.).
- **Gradle Build File:**  
  - Configure build.gradle for your mod loader and MC version.

## Copilot Usage Guidelines

- **How to Request Features:**  
  - Clearly describe features, changes, or bugs in plain language.
- **How to Review Code:**  
  - Copilot will provide code or instructions; review and copy/paste as needed.
- **How to Run & Test:**  
  - Copilot will guide you through build and test steps.

## Asset Generation (Optional)

- **Textures, Sounds, Art:**  
  - Use AI art tools (DALL-E, Stable Diffusion, etc.) or free resources for custom assets.

## Documentation & Help

- **Reference Links:**  
  - Minecraft Forge/Fabric docs  
  - Java tutorials  
  - Copilot documentation  
- **In-Game Help:**  
  - Plan for in-game tutorials or help menus as described in Plan.md.

## Troubleshooting

- **Common Issues:**  
  - Build errors, missing dependencies, mod loader mismatches.
- **How to Get Help:**  
  - Use Copilot for troubleshooting, or search Minecraft modding forums.

## Session Handoff Protocol (Required)

- At the end of any substantial debugging/feature session:
  - Update Plan.md with current status, decisions, and next actions.
  - Record validated findings and failed approaches in repository memory notes.
  - Include exact validation checkpoints used (same viewpoints, commands, or tests).
- Do not continue implementation across sessions without first reading the latest plan/handoff updates.
- For minimap work specifically, always use the two fixed screenshot viewpoints captured in the current cycle:
  - shoreline view,
  - tee/sign view.
- Any minimap change is considered incomplete until both fixed viewpoints pass visual checks.

Minimap baseline lock (2026-06-01):
- Current working navigation baseline commits:
  - 62314d2 (working minimap navigation baseline)
  - 31d175b (hardened minimap regression baseline)
- Treat this as rollback-safe ground truth before round-play-specific minimap work.
- Do not merge minimap changes that break quickRegression minimap invariants.

## Optional: Version Control

- **GitHub Repository:**  
  - Create a repo for your project.
  - Use Git for tracking changes and backups.

---

## V1 Locked Decisions (Kickoff)

- **Mod Loader:** Fabric
- **Minecraft Version:** 1.20.6
- **Java Version:** 21
- **Language:** Java (Kotlin optional later, not in v1)

## Active Epic (2026-05-31)

- Minimap rewrite to an always-on navigational tool with waypoints.
- Replacement strategy and phase gates are maintained in Plan.md under Session Continuity Update.
- Do not ship additional minimap tweaks without passing the phase validation gates.

Update (2026-06-01):
- The navigation minimap rewrite baseline is now implemented and checkpointed.
- Continue from this baseline for round-play integration work, not from pre-fix rebuild assumptions.

Why this baseline:
- Fast iteration for gameplay systems.
- Stable ecosystem and docs.
- Good fit for deterministic server-side logic.

## MVP Contract (Do This First)

The first playable release includes only:
- 1 generated course with 9 holes.
- Generation in one forest biome category.
- Tee -> throw tracking -> basket completion -> next tee flow.
- Per-player stroke count and total score for the round.
- Server-authoritative throw rules (position enforcement).

Not in MVP:
- 18-hole mode.
- Dome generation.
- Tournament/league systems.
- Course export/import.
- Complex broadcast HUD styling.

## Determinism & Safety Rules

- **Seed-first generation:** same seed should produce same course layout.
- **Hard constraints:**
  - Hole distance range fixed for v1.
  - Fairway width range fixed for v1.
  - Basket placement must pass accessibility checks.
- **Fail-safe retries:** if a hole fails constraints, retry with capped attempts.
- **No world grief:** all block edits are tracked and reversible for resets.

## Week 1 Execution Plan

### Day 1: Scaffold & Build Health

- Create Fabric mod scaffold for MC 1.20.6, Java 21.
- Confirm runClient and runServer tasks work.
- Add package layout:
  - game
  - world
  - rules
  - ui
  - data
- Add a minimal logger and config loader.

Acceptance:
- Mod loads with no errors.
- Clean build from terminal.

### Day 2: Course Data Model

- Create immutable data models:
  - Course
  - Hole
  - TeePoint
  - BasketPoint
  - FairwaySegment
- Add seed-based generator interface and basic implementation.
- Add validation methods for distance and overlap limits.

Acceptance:
- Given a seed, generator returns deterministic hole coordinates.

### Day 3: World Placement (Single Biome)

- Detect a valid forest area.
- Place tees and baskets for 9 holes.
- Carve simple fairway corridors with conservative block edits.
- Store placed-course metadata for reset.

Acceptance:
- New world can generate one playable 9-hole course without overlap failures.

### Day 4: Throw Loop & Scoring Polish

- Track active player state:
  - current hole
  - lie position
  - strokes this hole
  - total strokes
- Ender pearl throw event increments stroke count.
- On basket completion: lock score, advance to next hole.
- Tighten round flow for single-player testing:
  - clearer status updates
  - smoother hole-complete messaging
  - cleaner round completion behavior

Acceptance:
- Full 9-hole round can be completed with correct stroke totals and readable in-game feedback.

Status:
- Complete (accepted from manual single-player round).
- Cave-tee regression was discovered later and is being handled as placement hardening under Day 3/Day 7 stabilization.

### Day 5: Rule Enforcement

- Enforce throw-from-lie logic server-side.
- Add scouting mode + return-to-lie action.
- Disable breaking/placing blocks inside active course bounds.

Acceptance:
- Players cannot bypass lie enforcement or alter course blocks during a round.

### Day 5: Finished HUD & Single-Player UX

- Replace the current minimal action-bar/chat feedback with a more finished in-game HUD.
- Show at minimum:
  - course name
  - hole/par/distance
  - throw number
  - total score
  - player status
  - basket direction/distance hint
- Keep the HUD optimized for solo play first.

Acceptance:
- Solo round state is readable without relying on debug-style chat spam.

### Day 6: Commands & Rule Enforcement Cleanup

- Add minimal HUD text:
- Add admin commands:
  - create course
  - start round
  - reset course
  - end round
- Finish remaining rule-enforcement cleanup needed for stable solo rounds.

Acceptance:
- Admin can run full lifecycle without manual world edits.

### Day 7: Stabilization & Test Pass

- Run single-player and 2-player smoke tests.
- Test disconnect/rejoin behavior.
- Verify no critical lag spikes during generation.
- Fix blockers only; defer enhancements.

Acceptance:
- MVP loop is stable and repeatable.

Current priority adjustment:
- Multiplayer testing is deferred until a test setup is available.
- Near-term focus is single-player polish, HUD quality, and round-flow clarity.
- Multiplayer support remains in scope, but moves behind HUD/UX completion.

## Done Criteria for Week 1

- Playable 9-hole loop from tee to final score.
- Deterministic course generation from seed.
- Basic anti-cheat throw enforcement.
- Admin commands for create/start/reset/end.
- No critical crashes in smoke tests.

## Current Admin Commands

- `/mcdg createcourse <seed>`
- `/mcdg startround`
- `/mcdg practicecourse`
- `/mcdg endround`
- `/mcdg resetcourse`
- `/mcdg cleanupcourse`
- `/mcdg resumecourse`
- `/mcdg ruleset` (shows current mode)
- `/mcdg ruleset <casual|strict>`
- `/mcdg buildcamp`
- `/mcdg autotestplacement <runs> <holes>`
- `/mcdg cancelautotest`
- `/mcdg autotestthrows <count>`
- `/mcdg quickthrowtest <seed> <count>`
- `/mcdg cancelthrowtest`

Behavior notes:
- `startround` places the active course in a nearby forest biome and tracks changed blocks.
- `practicecourse` does the same placement flow but also saves course + placement metadata to disk so the course can be resumed after restart.
- `endround` ends round state but keeps placed structures until cleanup/reset.
- `resetcourse` and `cleanupcourse` both restore original blocks from tracked edit history and clear any persisted practice-course snapshot.
- `resumecourse` starts a round on an already placed course without rebuilding it, including a persisted practice course reloaded on server start.
- `autotestplacement` runs repeat placement validation loops and writes a report under `run/logs`.
- `buildcamp` creates a separate permanent lodging site only when explicitly requested by the player.

Camp build notes:
- Camp is separate from course-start tournament central and is never auto-built.
- Includes 6 yurts (8x8 minimum footprint) in a circular formation, each with unique interior accents.
- Includes central campfires plus nearby pool, tennis court, basketball court, and bathhouse.
- Basketball rims use hoppers.
- Pool, tennis, and basketball include perimeter lighting.
- Terrain-adaptive placement keeps structures near natural surface heights; each structure uses local footprint clearing rather than large camp-wide flattening.

## Project Status Snapshot (2026-05-30)

Current phase:
- Late-stage stabilization and release readiness closeout.
- Core gameplay loop is complete and repeatedly validated.

Completed and stable:
- Deterministic 9-hole seeded course generation and placement.
- Strict mode as default ruleset.
- Throw/lie enforcement and strict landing penalty resolution.
- Basket completion flow with tightened acceptance window.
- Anti walk-in hole completion (must resolve from throw lie).
- Round scoring, hole progression, and round completion handling.
- Practice course persistence/resume support.
- Minimap + right-side HUD + scorecard overlays.
- HUD polish pass (smoothing, alignment, style presets, animations).
- Hole finish result messaging updated to title/subtitle format.
- Strict surface presets implemented: `fast`, `balanced` (default), `tournament`.
- Admin command support for strict surface preset selection.

In progress / tuning:
- Strict-flow final closeout validation (manual 9-hole strict pass + resume safety re-check).
- Dev-session reliability hardening so strict session reaches in-world consistently.
- Additional multiplayer verification once test setup is available.

Intentionally deferred:
- Physical in-world OB markers/stakes (deferred to protect generation speed and natural terrain look).
- Dome generation.
- Advanced tournament/league systems.
- Full in-game tutorial/help UX.

Recommended next-session checklist:
1. Fix strict dev session launch ordering so client startup does not depend on player-joined completion.
2. Run one clean manual strict 9-hole round in `balanced` and confirm no throw-gate regressions.
3. Re-validate `practicecourse` -> restart -> `resumecourse` in a fresh session.
4. Add regression coverage for strict penalty flow and resume safety checks.
- `autotestthrows` runs server-driven throw launches for the command player during an active round and logs throw/lie transitions.
- `quickthrowtest` is a one-command in-game path: create course -> start round (skip presentation) -> start throw autotest.
- `cancelautotest` and `cancelthrowtest` stop active automation sessions.
- `ruleset strict` enables tighter throw-from-lie tolerance and applies respawn penalty strokes on in-round death.
- `ruleset casual` restores lenient lie tolerance and disables strict respawn penalty behavior.
- Strict mode now also applies OB/hazard penalties during play:
  - Crossing over OB/hazard during flight is allowed with no penalty if disc lands in bounds.
  - Hazard landing (water/lava): +1 stroke and teleport to the last in-bounds solid block before OB/hazard was first crossed. A centered `Hazard +1` title overlay appears on screen.
  - OB landing (outside fairway corridor): +1 stroke and teleport to the last in-bounds solid block before OB/hazard was first crossed. A centered red bold `OB +1` title overlay with subtitle `Returned to lie` appears on screen.
  - After any strict penalty teleport, the throw-from-lie gate is bypassed for the immediate next throw so the player can throw from the returned lie without being blocked.

Recovery note:
- Practice-course snapshots are stored at `world/data/mcdg/mcdg-practice-course.json` and loaded at server startup.
- If the snapshot file is deleted or corrupted, automatic stale-course resume is no longer available.

Practice course note:
- A persistent practice course can now be resumed across server restarts via `/mcdg practicecourse` and `/mcdg resumecourse`.

Strict respawn penalty tuning:
- Set `MCDG_RESPAWN_PENALTY_STROKES` (default `1`, clamped `0..5`).
- Penalty strokes are applied only while ruleset is `strict`.

## Verification Snapshot

Automated verification completed:
- Build successful.
- Dedicated server startup successful.
- Strict dev session launches server successfully; auto setup can time out if no player joins.
- Command runtime verification successful in server console:
  - `createcourse`
  - `startround`
  - `endround`
  - `resetcourse`
  - Strict dev session report writes to `run/logs/mcdg-autotest-latest.txt` when setup completes.

Manual checks still required:
- Player-in-world strict-mode throw-release from new lie (live throw event path) still required.
  - Note: a throw-gate bug that blocked the second throw after a strict penalty teleport was found in manual testing (2026-05-28) and fixed. Needs one clean 9-hole strict-mode pass to confirm resolved.
- Practice-course resume safety re-check in a fresh strict validation session still required.

Headless command validation completed (2026-05-28):
- `ruleset strict` command path verified in dedicated server console.
- `practicecourse` -> restart -> `resumecourse` safety flow verified in dedicated server console.
- Persisted practice-course snapshot reload verified on server startup.

Regression automation status:
- `quickRegression` and `smokeRegression` run via Gradle `JavaExec` using `com.mcdg.world.RegressionCheckRunner` (no JUnit dependency required).
- `fullRegression` chains quick + smoke + headless lifecycle smoke and report validation.

## Scoring Rules (Current HUD)

- Hole score is tracked as throws made on the current hole.
- Round score is tracked as total throws made across the round.
- HUD hole segment shows `Hole <throws> (<delta>)`.
- HUD round segment shows `Round Throws <made>/<expected> <delta>`.
- HUD expected round throws are live per throw:
  - expected = completed-hole par total + current-hole throws made
  - this means expected starts at your completed-hole par when a new hole begins and increases by 1 each throw
- Hole pace delta is per-throw (not walking movement):
  - computed from throw lie progress to basket versus hole length/par
  - updates when throws are recorded

## HUD Scoring Debug Toggle

- Set environment variable `MCDG_DEBUG_HUD_SCORING=true` before launching server/client to enable debug logs.
- Accepted true values: `1`, `true`, `yes`, `on` (case-insensitive).
- When enabled, server log prints once per second per tracked player with raw HUD scoring inputs and deltas.

- Additional single-player regression checks for tee placement edge cases (cave/pit adjacency).
- 2-player smoke test and disconnect/rejoin handling are deferred until multiplayer testing is available.

## ATLauncher Safe Test Workflow

Goal:
- Keep development testing isolated from your personal play instance.

One-time setup:
- Create a dedicated ATLauncher instance for this mod (example name: MCDG-Test-1.20.6).
- Open that instance folder and find its mods directory.
- Set user environment variable ATLAUNCHER_TEST_MODS_DIR to that exact mods path.

PowerShell (one-time command):

```powershell
[Environment]::SetEnvironmentVariable(
  'ATLAUNCHER_TEST_MODS_DIR',
  'PASTE_YOUR_TEST_INSTANCE_MODS_PATH_HERE',
  'User'
)
```

Daily test loop:
- Run VS Code task: Build + Deploy to ATLauncher Test Instance.
- Start only the test instance in ATLauncher.
- Validate features there before touching any personal world.

Repo tools added for this:
- scripts/deploy-to-atlauncher.ps1
- .vscode/tasks.json

Notes:
- Deployment script builds the mod, finds the latest non-sources jar, removes older jars for this mod in the test mods folder, then copies the new jar.
- If needed, you can run the script manually and override path:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy-to-atlauncher.ps1 -InstanceModsDir 'D:\Path\To\TestInstance\mods'
```

## Strict Dev Session

Goal:
- Run the full local test loop in one command: start the server, wait for readiness, launch the client, and let the throw autotest finish.

How to run:
- VS Code task: Run Strict Dev Session
- Or PowerShell: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-strict-dev-session.ps1`

Notes:
- The server launcher forces local dev auth with `online-mode=false` and `enforce-secure-profile=false` so the client can join without Mojang session checks.
- The session launcher waits for the server `Done (` log line before starting the client.
- Current limitation: depending on launch ordering, auto setup may still wait for a player before the client is started. If this happens, start a client manually (`gradle runClient`) or connect from ATLauncher to `127.0.0.1:25565`.
- The client uses `--quickPlayMultiplayer` only; no extra reconnect hook is needed.
- Successful runs write the placement report to `run/logs/mcdg-autotest-latest.txt`.

## Strict Manual Throw Debug Session

Goal:
- Start server + world + client connection automatically, then run course setup and throw tests manually for deep debugging.

How to run:
- VS Code task: Run Strict Manual Debug Session
- Or PowerShell: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-strict-manual-debug-session.ps1`

What this session does automatically:
- Starts dedicated server with local dev auth and local IPv4 bind (`127.0.0.1`).
- Waits for server/world readiness markers before launching the client.
- Auto-connects the client to `127.0.0.1:25565`.
- Enables strict-flow and HUD scoring debug logs by default.
- Captures server/client redirected logs under `run/logs/strict-manual-debug-<timestamp>/`.

Important limitation discovered during debugging:
- The `/mcdg` command flow is available on the dedicated server path used by these scripts.
- If you restart into a separate single-player world and then open that world to LAN, do not assume this reproduces the same command/runtime path that the dedicated-server automation uses.
- That mismatch likely explains part of the earlier automation confusion.

Recommended in-game flow for throw debugging:
- `/mcdg ruleset strict`
- `/mcdg createcourse <seed>`
- `/mcdg startround`
- Reproduce the throw issue manually (especially second throw after strict penalty teleport).
- `/mcdg autotestthrows 25`
- Optional baseline: `/mcdg quickthrowtest <seed> 25`

Primary artifacts to share when a run reproduces the issue:
- `run/logs/latest.log`
- `run/logs/debug.log`
- `run/logs/mcdg-throw-autotest-latest.txt`
- `run/logs/strict-manual-debug-<timestamp>/server.out.log`
- `run/logs/strict-manual-debug-<timestamp>/server.err.log`
- `run/logs/strict-manual-debug-<timestamp>/client.out.log`
- `run/logs/strict-manual-debug-<timestamp>/client.err.log`

Bundle command after manual repro:
- VS Code task: Collect Throw Debug Bundle
- Or PowerShell: `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\collect-throw-debug-bundle.ps1`

Bundle behavior:
- Auto-selects the most recent `run/logs/strict-manual-debug-<timestamp>/` session folder.
- Copies current shared logs (`latest.log`, `debug.log`, latest throw/autotest reports, `server.properties`).
- Writes a manifest and zips everything to `run/logs/throw-debug-bundles/mcdg-throw-debug-<timestamp>.zip`.
- Optional flags: `-IncludeArchivedLogs` and `-IncludeCrashReports`.

## Tournament Feel Pack v1 (Execution Ready)

Goal:
- Make rounds feel like real disc golf events without requiring full multiplayer tournament infrastructure yet.

Scope:
- Single-player first.
- Multiplayer-compatible data model where practical.
- No breaking changes to existing /mcdg commands.

### Feature 1: Signature Hole Builder

What to add:
- Generate exactly 1 signature hole per 9-hole course from a controlled set:
  - island-green style
  - tunnel-gap style
  - downhill bomber style
- Mark that hole in tee signage and HUD (for example: "Signature Hole").

Acceptance:
- Every generated 9-hole course includes exactly one signature hole tag.
- Hole remains playable under existing validation checks.

### Feature 2: Tournament Tee Sign Package

What to add:
- Upgrade tee signs to display:
  - hole number
  - par
  - distance
  - optional hazard note (OB / mando / water carry)
- Keep current physical placement standard:
  - lamp at back of tee
  - sign left-front facing tee box
  - banner right-front

Acceptance:
- All holes show readable sign info from tee box.
- Sign orientation and placement remain consistent with current tee marker rules.

### Feature 3: Event Start Sequence (Round Presentation)

What to add:
- Add a pre-round flow on /mcdg startround:
  - 5-second countdown
  - course name + layout card in action bar/chat
  - "Round Live" message when tracking begins
- Add optional config toggle to skip presentation for quick testing.

Acceptance:
- Round starts with clear sequence and no gameplay desync.
- Existing scoring and hole progression continue unchanged.

### Feature 4: Live Leaderboard + Hole Result Callouts

What to add:
- After each basket completion, show:
  - hole result (birdie/par/bogey)
  - running total vs par
- On round finish, show ranked summary (single-player now, list-friendly for multiplayer later).

Acceptance:
- Per-hole callout appears on every completed hole.
- Final summary always appears and matches tracked strokes.

### Feature 5: Tournament Rules Preset (Casual vs Strict)

What to add:
- Add simple rules preset setting:
  - Casual: current behavior baseline.
  - Strict: enables penalty for OB/hazard and requires throw-from-lie enforcement with tighter tolerance.
- Add command hook:
  - /mcdg ruleset <casual|strict>

Acceptance:
- Ruleset can be changed without restart.
- Strict mode penalties and lie checks are consistently enforced.

## Suggested Implementation Order

1. Feature 2 (tee sign package)
2. Feature 4 (hole result callouts)
3. Feature 3 (event start sequence)
4. Feature 5 (rules preset)
5. Feature 1 (signature hole builder)

Current progress against implementation order:
- Feature 2 (tee sign package): Complete.
  - Implemented: tee sign content (hole/par/distance), tee lamp/sign/banner placement package, and hazard note variants (`Haz: Water`, `Haz: Mando`, `Haz: OB`).
- Feature 4 (hole result callouts): Complete.
  - Implemented: per-hole callouts (birdie/par/bogey + running vs par) and event-style ranked final summary output.
- Feature 3 (event start sequence): Complete.
  - Implemented: 5-second countdown, layout card broadcast, round-live transition, and optional skip toggle via `MCDG_SKIP_ROUND_PRESENTATION=true`.
- Feature 5 (rules preset): In final stabilization.
  - Implemented: `/mcdg ruleset` status command, `/mcdg ruleset casual`, `/mcdg ruleset strict`, strict-mode tighter throw-from-lie tolerance, strict-mode respawn penalty strokes, strict-mode OB/hazard penalties that trigger on landing with placement to the last in-bounds solid block before crossing, centered OB/hazard title overlay on penalty, and throw-gate bypass for the throw immediately following a strict penalty teleport.
  - Finalize before closeout: one clean 9-hole strict-mode pass to confirm throw-gate fix, and persistent `practicecourse` -> restart -> `resumecourse` safety flow.
- Feature 1 (signature hole builder): Complete.
  - Implemented: exactly one signature hole per 9-hole course, signature visibility in world/scorecard/round messaging, and persistence across practice-course resume.

## Break Handoff Plan (Next Work)

Immediate completion target:
1. Finish Feature 5 stabilization and mark complete only after strict-mode + resume regressions are clean in manual test.

Next best 5 features to implement after Feature 5:
1. Tee/Basket Placement Hardening v2
   - Expand cave/pit/enclosure protection and safe-standable spawn resolution around tee and basket starts.
2. Persistent Practice Course Robustness v2
   - Add snapshot migration cleanup workflow, stale/legacy snapshot UX, and recovery guardrails for corrupted files.
3. Multiplayer Round Reliability Pass
   - Complete deferred 2-player smoke tests, disconnect/rejoin handling, and synchronized resume behavior.
4. Automated Regression Coverage for Round Flow
   - Extend headless autotest to cover strict penalties, lie updates, restart/resume path, and round completion leaderboard correctness.
5. Signature Template Variety v2
  - Expand signature-hole template variety and hazard-note polish while preserving exactly-one-signature guarantee.

Reasoning:
- Front-load immediate visual/tournament feel gains.
- Add presentation and rules controls before touching more complex worldgen specialization.

## v1 Done Criteria

- Round start feels event-like (countdown + "live" transition).
- Every hole has tournament-style tee information.
- Every hole completion gives a golf-style scoring callout.
- End-of-round summary reads like event results, not debug output.
- Casual and Strict presets both function end-to-end.