---

# PROJECT-SETUP.md

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
- `autotestthrows` runs server-driven throw launches for the command player during an active round and logs throw/lie transitions.
- `quickthrowtest` is a one-command in-game path: create course -> start round (skip presentation) -> start throw autotest.
- `cancelautotest` and `cancelthrowtest` stop active automation sessions.
- `ruleset strict` enables tighter throw-from-lie tolerance and applies respawn penalty strokes on in-round death.
- `ruleset casual` restores lenient lie tolerance and disables strict respawn penalty behavior.
- Strict mode now also applies OB/hazard penalties during play:
  - Crossing over OB/hazard during flight is allowed with no penalty if disc lands in bounds.
  - Hazard landing (water/lava): +1 stroke and teleport to the last in-bounds solid block before OB/hazard was first crossed.
  - OB landing (outside fairway corridor): +1 stroke and teleport to the last in-bounds solid block before OB/hazard was first crossed.

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
- Command runtime verification successful in server console:
  - `createcourse`
  - `startround`
  - `endround`
  - `resetcourse`

Manual checks still required:

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
  - Implemented: `/mcdg ruleset` status command, `/mcdg ruleset casual`, `/mcdg ruleset strict`, strict-mode tighter throw-from-lie tolerance, strict-mode respawn penalty strokes, and strict-mode OB/hazard penalties that trigger on landing with placement to the last in-bounds solid block before crossing.
  - Finalize before closeout: complete regression pass on throw-release from new lie and persistent `practicecourse` -> restart -> `resumecourse` safety flow.
- Feature 1 (signature hole builder): Not started.
  - Remaining: exactly one tagged signature hole per 9-hole course from controlled templates.

## Break Handoff Plan (Next Work)

Immediate completion target:
1. Finish Feature 5 stabilization and mark complete only after strict-mode + resume regressions are clean in manual test.

Next best 5 features to implement after Feature 5:
1. Feature 1: Signature Hole Builder
   - Why next: only remaining core Tournament Feel Pack feature; highest gameplay differentiation value.
2. Tee/Basket Placement Hardening v2
   - Expand cave/pit/enclosure protection and safe-standable spawn resolution around tee and basket starts.
3. Persistent Practice Course Robustness v2
   - Add snapshot migration cleanup workflow, stale/legacy snapshot UX, and recovery guardrails for corrupted files.
4. Multiplayer Round Reliability Pass
   - Complete deferred 2-player smoke tests, disconnect/rejoin handling, and synchronized resume behavior.
5. Automated Regression Coverage for Round Flow
   - Extend headless autotest to cover strict penalties, lie updates, restart/resume path, and round completion leaderboard correctness.

Reasoning:
- Front-load immediate visual/tournament feel gains.
- Add presentation and rules controls before touching more complex worldgen specialization.

## v1 Done Criteria

- Round start feels event-like (countdown + "live" transition).
- Every hole has tournament-style tee information.
- Every hole completion gives a golf-style scoring callout.
- End-of-round summary reads like event results, not debug output.
- Casual and Strict presets both function end-to-end.