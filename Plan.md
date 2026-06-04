# MCDG Coordinated Plan

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
  par 4 when a par 5 cannot meet the zero-water-carry policy.
- Finish-zone recovery now expands toward the validator's safe-column threshold
  and prefers land islands when the basket is not water-adjacent.
- The validator respects effective placed par, so fallbacked par 5 slots are
  treated as par 4 for route checks.

Validation status:

- `gradle build`: PASS.
- `gradle smokeRegression`: PASS.
- ATLauncher deploy gate: PASS.
  - Lifecycle smoke: pass=3, fail=0, issues=0.
  - Max landing gap: 10.

  Field validation update (ATLauncher, 2026-06-02):
  - Fresh autotest run completed successfully on the ATLauncher instance.
  - Manual flow validated: created a new course, played a round, disconnected,
    resumed the same course.
  - Persistence/listing check validated: resumed course appears correctly in
    `listcourses`.

Command + generation model update (2026-06-02):

- `/mcdg playcourse <index>` is now the primary saved-course flow (select +
  resume).
- Default command surface is simplified; advanced/debug commands are hidden
  unless enabled.
- Saved-course integrity guard now blocks stale entries (missing tee/basket
  structures) and `listcourses` prunes invalid entries.
- Course generation policy is unified across modes/holes: land-first placement
  with max water carry capped at `91` blocks (~300 ft).

---

## Possible Additions Later

- Add admin shortcut command: `/mcdg usecourse latest`.
  - Behavior: activate the newest reusable catalog entry without requiring an
    index lookup.
  - Goal: speed up iteration when repeatedly testing fallback/reuse flows.
- Multidimension course persistence refactor for the new buildcourse flow.
  - Goal: preserve per-hole dimension data so mixed-dimension builder sessions
    can save, load, and replay correctly in `listcourses`/`playcourse`.
  - Follow-up: expand the course snapshot/storage model once the single-dimension
    Phase 1 flow is validated.

---

## Practicecourse Deprecation Plan (Phased)

Phase 0: Freeze + baseline

- Keep `practicecourse` behavior unchanged while reusable-course flow
  stabilizes.
- Track operator usage of `practicecourse`, `resumecourse`, and `usecourse`.
- Confirm reusable fallback and catalog commands are reliable in live sessions.

Phase 1: Soft deprecation

- Keep `practicecourse` functional.
- Add a lightweight deprecation notice on command use.
- Update help/docs to prioritize unified flow (`createcourse` + `startround` +
  reusable commands).

Phase 2: Behavioral convergence

- Route `practicecourse` through the same lifecycle internals as `startround`.
- Remove command-specific branch differences where possible.
- Keep persistence/snapshot outputs identical between both command paths.

Phase 3: Alias mode

- Convert `practicecourse` into a thin alias/forwarder to unified flow.
- Preserve compatibility for existing scripts/macros.
- Keep deprecation notice active.

Phase 4: Removal window

- Announce target removal release (after alias phase proves stable).
- Keep one release with stronger warning before removal.
- Remove parser/registration entry after migration window closes.

Phase 5: Cleanup

- Remove legacy flags/branches kept only for `practicecourse` terminology.
- Retain snapshot compatibility loaders as needed for older saves.
- Keep reusable catalog commands as the primary management path.

Rollback safety

- If regressions appear in phases 2-4, restore alias behavior immediately.
- Avoid deleting storage/schema compatibility in the same release as command
  removal.
- Keep `resumecourse` stable through the entire transition.

---

## Night Wrap Checkpoint (2026-06-01)

Tonight completed:

- Locked compact-layout direction as default generation target (condensed
  footprint, soft hole-9 return near hole-1, non-crisscross with safe vertical
  overlap policy).
- Added private multiplayer test-pack automation for client/server zip
  distribution from ATLauncher instance content.
- Added test resource-pack scaffold for readability/audio iteration and wired
  bundling script support to include it in client test packs.

Key commits:

- `46faa5f` Prevent flooded basket markers and add validation guard.
- `e7a444c` Update docs for basket water-column finish fix.
- `12d26b5` Add master done/open snapshot to plan.
- `6e2cfca` Add agreed compact default course layout initiative plan.
- `53ff3e2` Add multiplayer test pack bundling script and templates.
- `e6de8af` Scaffold test resource pack and bundle integration.

Operational status:

- Build and deploy gates were green after latest gameplay safety fixes.
- Multiplayer test-pack generation script is validated end-to-end.
- Resource-pack scaffold exists and is ready for art/sound drop-in work.

First actions next session:

1. Run manual validation pass for newest gameplay fixes (watery
   basket/elevation/cleanup/score-window scenarios).
2. Start compact-layout implementation Phase 1 (default compact routing skeleton
   - envelope targeting).
3. Create first playable resource-pack art pass for critical readability assets.

---

## Master Status Snapshot (Current)

Use this section as the primary tracker. Historical sections below remain for
implementation detail and validation evidence.

Done:

- Core 9-hole generation and round flow are implemented and repeatedly green in
  `quickRegression`, `smokeRegression`, and full deploy gate.
- Multiplayer core systems are implemented:
  - explicit participant enrollment,
  - late join (`/mcdg joinround`),
  - reconnect restore,
  - turn-order enforcement and timeout penalties,
  - running multiplayer score panel.
- Cleanup relocation logic is hardened (outside-course safe relocation
  priority).
- Long-water-carry routing and strict corridor handling are hardened for
  alternate-route playability.
- Player-relative course elevation guard is implemented (tee strict floor,
  basket strict+relaxed floor).
- Basket water-column finish blocker is fixed and guarded by validator rule
  (`basket_marker_flooded`).
- Recent fixes are deployed to ATLauncher test instance with successful
  lifecycle smoke.

Open:

- Manual verification closeout for newest fixes:
  - watery basket finish scenarios,
  - elevation-floor behavior on varied terrain,
  - cleanup relocation behavior in live multiplayer,
  - last-3-hole score panel behavior in live multiplayer.
- Multiplayer reliability closeout:
  - repeatable 2-player full round,
  - disconnect/rejoin mid-round validation (single-player reconnect/resume
    validated on 2026-06-02; 2-player verification still pending),
  - state consistency checks across both clients through round completion.
  - current blocker: no second player available for live two-client validation.
- Strict/resume closeout runs:
  - one clean manual strict 9-hole round,
  - `practicecourse` -> restart -> `resumecourse` verification.
- Release-readiness docs and UX completion:
  - compatibility/performance matrix,
  - in-game help/admin lifecycle docs,
  - menu-driven UX + persistence diagnostics/recovery work.
- Optional compatibility mode backlog remains open (modded + vanilla
  mixed-client support matrix).

Next Recommended Sequence:

1. Run manual verification pass for the newest June 2 fixes and check off each
   scenario above.
2. Run strict/resume closeout pair and mark verification standard complete.
3. Continue solo validation coverage and capture evidence while waiting on a
   second player.
4. Run multiplayer reliability closeout (2-player + disconnect/rejoin) once a
   second tester is available.
5. Finish release-readiness docs/UX items before any new feature wave.

---

## Compact Course Layout Initiative (Agreed Plan)

Objective:

- Make compact routing the new default course layout behavior without shortening
  hole lengths.
- Keep holes close together in a condensed footprint.
- Favor hole 9 finishing near hole 1.
- Prevent illegal 2D fairway crisscross; allow overlap only when safely
  separated vertically.

Agreed constraints:

- Default layout mode: compact.
- 9-hole target footprint: about `450 x 450` blocks.
- Hole 9 return objective: soft target near hole 1.
  - preferred: `<= 60` blocks from hole 1 tee,
  - warning threshold: `> 90` blocks.
- Crossing policy:
  - disallow 2D fairway intersections,
  - allow above/below overlap only with enforced vertical safety clearance.

Pros:

- Smaller world footprint with stronger venue feel.
- Better spectator/admin flow and easier round regrouping.
- Preserves long-hole gameplay by routing shape rather than shortening distance.

Tradeoffs:

- Routing/solver complexity increases.
- More constrained generation can increase retry frequency.
- Requires stronger regression metrics and fallback policy to keep reliability
  high.

Implementation phases:

1. Compact default routing skeleton:

- introduce compact envelope targeting in seeded generator path;
- keep existing distance/par goals unchanged.

1. Conflict-aware corridor reservation:

- reserve route volumes per hole during generation;
- reject 2D intersections by default.

1. Vertical overlap safety:

- permit overlap only when vertical clearance rule passes;
- require landing-zone separation at overlap-adjacent regions.

1. Hole 9 return scoring:

- add soft score objective for hole 9 basket proximity to hole 1 tee;
- do not hard-fail solely on return distance.

1. Reliability fallback tiers:

- Tier A: strict compact constraints;
- Tier B: slightly relaxed spacing/envelope;
- Tier C: final legacy fallback to avoid hard generation failure.

1. Regression and smoke coverage:

- add compact footprint metric checks;
- add illegal-crossing checks;
- add vertical-overlap clearance checks;
- add hole 9 return-distance warning metric.

Exit criteria before full release confidence:

- quick/smoke/lifecycle green with compact mode as default.
- no illegal crossing issues in autotest samples.
- acceptable compact placement pass rate with fallback usage tracked.
- manual multiplayer pass confirms readability/playability in condensed layouts.

---

## Basket Water-Column Finish Fix (2026-06-02)

Implemented:

- Hardened basket placement to prevent unfinishable baskets generated inside
  water columns.
- `CoursePlacementService` now rejects basket candidates when the marker column
  (hopper/bars/lantern stack zone) contains fluid.
- Basket marker placement now forcibly clears fluid from the marker column
  before placing hopper/bars/lantern blocks.
- `CoursePlacementValidator` now emits `basket_marker_flooded` when marker
  blocks intersect fluid, so lifecycle smoke catches this regression.

Validation + deploy:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.
- `Build + Deploy to ATLauncher Test Instance`: PASS.
  - Lifecycle smoke: pass=3, fail=0, issues=0.
  - Jar deployed to ATLauncher mods directory.

Manual verification checklist:

1. Generate multiple rounds near oceans/rivers/waterfalls and confirm basket
   marker columns are dry and visually unobstructed.
2. Complete a round on a previously risky watery finish and verify hole
   completion triggers normally.

---

## Player-Relative Elevation Guard for Course Creation (2026-06-02)

Code checkpoint commit:

- b40b709

Implemented:

- Added player-relative minimum Y guardrails during hole anchor placement in
  `CoursePlacementService`.
- Tee placement now enforces a strict floor at `playerY - 20` when selecting
  final tee surface candidates.
- Basket placement now uses a two-step floor:
  - target floor `playerY - 20`,
  - relaxed fallback floor `playerY - 28` when strict floor cannot be satisfied
    nearby.
- Added post-floor basket enclosure recheck and fallback recovery/relocation
  pass to prevent `basket_deeply_enclosed` regressions after elevation
  repositioning.
- Added diagnostic logging for floor fallback and unmet-floor cases.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual verification checklist:

1. Trigger course creation from elevated terrain and verify tees are not placed
   deeper than ~20 blocks below source player Y in normal terrain.
2. Verify baskets prefer the same floor, with occasional relaxed fallback only
   when terrain constraints require it.
3. Confirm long-hole routing still generates and remains playable under strict
   rules.

---

## Cleanup + Long Carry + Score Window Fixes (2026-06-02)

Code checkpoint commit:

- b40b709

Implemented:

- `clearcourse` relocation logic now prioritizes nearest safe positions outside
  the placed-course buffer and removes spawn as the normal fallback path.
- Strict OB corridor classification now supports alternate-route holes by
  evaluating distance to tee->anchor and anchor->basket corridor legs.
  - This preserves strict enforcement while making long-water-carry alternate
    lines playable.
- Multiplayer running score panel window changed to show only the last 3 holes
  (relative to current focus hole) instead of all completed holes.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual verification checklist:

1. Run `/mcdg clearcourse` while players are inside course bounds and confirm
   relocation lands outside the active course area without routine spawn
   teleports.
2. Start a long-water-carry hole with an alternate anchor route and confirm
   strict mode allows in-bounds play along the routed fairway corridor.
3. During multiplayer round HUD, confirm the running score panel shows only a
   3-hole sliding window ending at the current hole.

---

## Multiplayer Running Score Panel Update (2026-06-01 Night)

Code checkpoint commit:

- aa21ba3

Implemented:

- Added live server-to-client running score sync payload:
  `RoundRunningScoresSync`.
- Added bottom-left multiplayer running score panel on client HUD (round-only
  visibility).
- Panel content:
  - all enrolled participants (online + offline marker),
  - per-hole running values,
  - running total,
  - focus on completed holes + current hole only.
- Panel sort order:
  - running total (lowest first),
  - tie-break by previous-hole chain,
  - final tie-break by randomized hole-1 seed order.
- Update cadence:
  - network updates sent only when score-state hash changes (hole-score
    progression driven),
  - join-time snapshot sync added for reconnecting participants.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual multiplayer verification:

1. Start a multiplayer round and verify panel appears bottom-left during round
   and hides outside round.
2. Confirm panel shows enrolled players including offline entries.
3. Confirm columns show completed holes + current hole only.
4. Confirm row sorting updates correctly after hole completions.
5. Reconnect an enrolled player and verify scoreboard panel/state appears
   without waiting for an extra hole completion.

---

## Multiplayer Turn HUD Update (2026-06-01 Night)

Code checkpoint commit:

- 44dc79d

Implemented:

- Added in-round actionbar turn HUD for multiplayer participants.
- Every second, players see:
  - `Your turn | M:SS left` when they are the active thrower,
  - `Turn: <player> | M:SS left` when another participant is up.
- HUD uses the existing enforced turn engine and the 2-minute timeout clock.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual verification:

1. Start multiplayer round with 3+ players and confirm all participants receive
   turn HUD updates every second.
2. Confirm timer decrements and resets correctly when turn changes.
3. Confirm HUD aligns with throw gating (out-of-turn player should see the other
   thrower as active).

---

## Multiplayer Turn Order Enforcement Update (2026-06-01 Night)

Code checkpoint commit:

- 5997fb7

Implemented:

- Enforced one-player-at-a-time throws during active multiplayer rounds.
- Turn gating now blocks out-of-turn throw attempts with a user-facing wait
  message.
- Hole 1 start order is auto-randomized each round.
- Hole tee order for hole `N` uses prior-hole results:
  - compare hole `N-1` score (lowest throws first),
  - if tied, compare `N-2`, then earlier holes,
  - if still tied, use hole-1 randomized order as final tie-break.
- Within a hole after tee throws, turn selection switches to
  furthest-from-basket first.
- Added turn timeout handling:
  - 2-minute throw window for the active player,
  - on timeout: +1 stroke, lie reset to current hole tee, turn passes to next
    player.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual multiplayer verification (next pass):

1. Start a 3-5 player round and confirm out-of-turn throws are blocked.
2. On hole 1, confirm throw order is randomized between rounds.
3. On hole 2+, confirm tee order follows previous-hole score ranking with
   tie-break chain.
4. After all players tee, confirm furthest-from-basket throw priority applies.
5. Let one active player timeout for 2 minutes and confirm +1, tee reset, and
   turn pass behavior.

---

## Multiplayer Reconnect + Status Update (2026-06-01 Night)

Code checkpoint commit:

- c058aac

Implemented:

- Added reconnect-safe participant restore on player join while a round is live:
  - restores round inventory (`training_disc` + scorecard),
  - restores/retains round state for tracked participants,
  - teleports rejoining participant to safe lie when rejoining in the course
    world.
- Added `/mcdg roundstatus` for admin visibility:
  - round active flag,
  - tracked participant count,
  - online vs offline participants,
  - round-state presence and hole/stroke summary lines.
- Hardened `/mcdg joinround` idempotency:
  - detects already-active participants,
  - restores round inventory without duplicate enrollment,
  - still supports explicit player selectors.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Manual multiplayer acceptance checklist (next live pass):

1. Start a 3-5 player dedicated test session and run `/mcdg startround
   <players>`; verify only selected players are enrolled.
2. Disconnect one active participant mid-hole, reconnect, and verify
   auto-restore + round lie continuity.
3. Run `/mcdg roundstatus` during play and confirm online/offline + hole summary
   lines match observed players.
4. Add a late joiner with `/mcdg joinround <player>` and verify no duplicate
   enroll for already-active participants.
5. Run `/mcdg endround` and `/mcdg cleanupcourse`; confirm non-participants are
   not modified unexpectedly.

---

## Multiplayer Enrollment Hardening Update (2026-06-01 Night)

Code checkpoint commit:

- 6496c83

Implemented:

- Replaced world-wide implicit enrollment with explicit participant enrollment
  for:
  - `/mcdg startround`
  - `/mcdg practicecourse`
  - `/mcdg resumecourse`
- Added optional player target argument support for the above commands
  (`<players>` selector).
- Added `/mcdg joinround` (plus optional `<players>`) so late joiners can be
  added safely without restarting the round.
- Round cleanup/end/reset flows now clear tracked participant round state only
  (no global `clearAll` wipe in the core command lifecycle).
- Active participant IDs are now tracked in `ActiveCourseManager` for safer
  round-scoped cleanup and inventory cleanup.

Validation:

- `gradle quickRegression`: PASS.
- `gradle smokeRegression`: PASS.
- `gradle build`: PASS.

Follow-up verification:

1. In multiplayer, run `/mcdg startround <players>` and confirm only selected
   players are staged/teleported.
2. Join a new player mid-round and run `/mcdg joinround <player>`; confirm they
   receive throw item, scorecard, and tee teleport.
3. End or cleanup round and confirm non-participant players do not have round
   state or inventory unexpectedly modified.

Later backlog:

1. Add an optional compatibility mode for mixed clients (server mod required,
   client mod optional).
2. In compatibility mode, keep core server-authoritative gameplay available
   while disabling client-only HUD/cinematic features for vanilla clients.
3. Define and document an explicit support matrix: same-version modded clients
   (full feature set) vs vanilla clients (reduced feature set).

---

## Cleanup Relocation + Final Scene Timing Update (2026-06-01 Night)

Code checkpoint commit:

- 0141867

Implemented:

- `clearcourse` evacuation now uses hybrid relocation instead of fixed
  world-spawn teleport:
  - safe relocation near each player first,
  - fallback near command-source safe anchor,
  - final fallback to world-spawn safe anchor.
- Added placed-course footprint buffer checks so relocated targets are forced
  outside active course bounds.
- Round-complete cinematic duration updated to 20 seconds.

Validation + deploy:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.
- Latest jar deployed to ATLauncher with hash match confirmation.

Follow-up verification:

1. Run `/mcdg clearcourse` from different course locations and confirm players
   are no longer always moved to the same spawn area.
2. Confirm round-complete cinematic remains skippable and auto-times out at 20s.

---

## Countdown Warm-up Music Update (2026-06-01 Night)

Feature checkpoint commit:

- 480f4c9

Implemented:

- Added randomized warm-up music selection for the existing 30-second round
  countdown.
- Added shuffle-bag track selection with anti-repeat behavior (no immediate
  same-track replay when multiple tracks exist).
- Added final 3-second countdown sting (`note_block_bell`) before `Round Live!`.
- Music remains presentation-only and does not block round activation flow.

Current warm-up track pool (vanilla discs):

- `chirp`, `blocks`, `stal`, `strad`, `mall`, `wait`.

Validation:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.

Follow-up verification:

1. Run multiple rounds and confirm random track rotation and no immediate
   repeats.
2. Confirm bell sting fires once at 3 seconds remaining.
3. Tune volumes if countdown voice/title readability needs adjustment.

---

## Round Complete Cinematic Update (2026-06-01 Night)

Feature checkpoint commit:

- f1c4c70

Implemented:

- Added a lightweight end-of-round cinematic overlay that is triggered from the
  existing round-summary/chat flow (no separate completion detector).
- Added server-to-client payload `RoundCompleteCinematicSync` with leaderboard
  snapshot fields for presentation.
- Cinematic only fires at true round end when active round states are empty.
- Client presentation includes:
  - `Round Complete` header card,
  - top-3 leaderboard lines,
  - local rank/score line,
  - skip hint.
- Cinematic is non-blocking and dismisses by timeout or player movement/jump
  input.

Validation:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.

Follow-up verification:

1. Manual full-round run confirms cinematic triggers exactly once after final
   hole.
2. Confirm chat summary remains visible and unchanged.
3. Tune timing/visual density after first gameplay pass if needed.

---

## Ace Cinematic Update (2026-06-01 Night)

Feature checkpoint commit:

- 3c29765

Implemented:

- Added lightweight Ace cinematic that is triggered from the existing Ace
  hole-result path (no duplicate detector).
- Server sends an Ace-specific S2C payload only when hole score is `1` on hole
  completion.
- Client plays a short celebration sequence:
  - center-screen `ACE!` card with hole index and hole distance,
  - timed particle celebration around the player,
  - automatic timeout/cleanup.

Integration details:

- Networking payload: `AceCinematicSync`.
- Server registration: payload registered during mod init.
- Trigger point: existing hole-finish progression path in `HoleProgressTracker`
  right before hole-finish title display.
- Client runtime: receiver + transient cinematic state + render/tick handlers in
  `McdgClientMod`.

Validation:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.

Follow-up verification:

1. Manual run: produce an Ace and confirm single trigger (no duplicate
   celebration).
2. Confirm cinematic does not block hole progression/teleport to next tee.
3. Tune card duration/particle density if needed after gameplay pass.

---

## Guardrail Drift Prevention Update (2026-06-01 Night)

Guardrail checkpoint commit:

- 2aa3576

New anti-drift gates added:

- Quick regression now enforces Par 5 composition cap across seeded 9-hole
  generation samples (`<= 1` Par 5).
- Quick regression now enforces validator/retry issue-code sync for high-risk
  placement failures:
  - `tee_deeply_enclosed`
  - `basket_deeply_enclosed`
  - `par5_alternate_route_missing`
  - `alternate_route_missing`
  - `landing_gap_too_long`
- Quick regression now locks strict minimap hazard-overlay visibility baseline
  values:
  - `HAZARD_OVERLAY_ARGB = 0x8CFF9A32`
  - `HAZARD_SAMPLE_STEP_PX = 2`
- Lifecycle report verification now hard-fails if the latest smoke report
  contains any disallowed placement issue code above.

Validation at guardrail checkpoint:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.

Operational effect:

- Regressions can no longer silently drift in Par 5 composition, start-round
  retry gating coverage, or strict hazard overlay visibility without failing
  CI/local verification.

---

## Stability Update (2026-06-01 Evening)

Code checkpoint commit:

- fe5ca6f

Implemented in this checkpoint:

- Par 5 composition guardrail: only the forced slot can produce Par 5 in a
  9-hole round.
- `startround` retry gate expanded beyond enclosure-only to include
  alternate-route and landing-gap placement failures.
- Minimap strict hazard overlay visibility fix:
  - stronger overlay alpha,
  - denser sample pass,
  - corrected sampled surface block Y for hazard classification.

Validation status at checkpoint:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.
- Updated jar deployed to ATLauncher test mods folder.

Next verification focus:

1. Manual strict round: confirm no extra Par 5 generation and no acceptance of
   Par 5 without alternate route.
2. In-round minimap check: verify orange hazard zones are visible in strict mode
   on known hazard-heavy lines.
3. If overlay still appears absent, add temporary on-screen sampled-hazard
   counter for one run, then remove.

---

## Return Checkpoint (2026-06-01)

Where we paused:

- User-reported `startround` stall reproduced in integrated singleplayer logs
  (long server-thread block before round presentation).
- Root cause addressed in current working tree by:
  - reducing generator footprint (3-column grid layout) to lower chunk-gen
    pressure,
  - adding immediate `startround` placement-start feedback,
  - adding tee deep-enclosure validation (`tee_deeply_enclosed`) and retry
    gating in `startround` (previously basket-only).

Current local state (not yet committed):

- Modified files:
  - `src/main/java/com/mcdg/command/McdgAdminCommands.java`
  - `src/main/java/com/mcdg/world/CoursePlacementValidator.java`
  - `src/main/java/com/mcdg/world/SeededCourseGenerator.java`

Current verification status:

- `gradle quickRegression`: PASS.
- `gradle build`: PASS.
- Latest jar deployed to ATLauncher mods folder (timestamp seen during deploy:
  2026-06-01 14:06:50 local).

First actions on return:

1. Manually run ATLauncher: `/mcdg createcourse <seed>` then `/mcdg startround`
   and watch for immediate placement message + non-stalling build.
2. If stable, commit current working-tree patch set.
3. If still stalling, add timing instrumentation around `placeCourse` phases and
   tighten fallback search bounds.

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

- quickRegression now enforces minimap baseline invariants in
  RegressionCheckRunner.
- Minimap changes must keep quickRegression green before merge/deploy.

Next focus:

- Begin round-play specific minimap integration changes behind this locked
  baseline.

---

## Round Play Polish Update (2026-06-01)

Completed this session:

- Basket finish zone is now the hopper plus the block above it; bars and lamp
  remain bounce-only surfaces.
- Basket green hazard exemption now uses the placed green radius, while
  water/lava OB and corridor OB still apply normally.
- `Building Course 0%` now stays visible until terrain generation updates begin,
  so the build state does not look stalled.
- Minimap now renders active-hole basket guides with an upright flag marker plus
  100ft/200ft circles (unlabeled).
- Round HUD distance is feet-only and smoothly updates from lie-to-basket.
- Basket-structure contact above the make-zone now forces bounce-to-ring with
  `CLANK!` feedback.

Validation:

- quickRegression remains green after the gameplay and overlay adjustments.

---

## Placement Hardening Update (2026-06-01)

Completed this session:

- Generation/par rules updated for realistic carry playability:
  - carry cap logic uses 91 blocks (300 ft equivalent),
  - par bands are now 0-400 (Par 3), 401-700 (Par 4), 701-1200 (Par 5),
  - one forced Par 5 slot per 9-hole generation with stronger retry behavior.
- Alternate-route search widened for hard terrain cases and now validates both
  route legs against carry limits.
- Landing-gap validation is route-aware (tee->anchor->basket when an alternate
  anchor exists), not direct-line only.
- New enclosure recovery corridor for deeply enclosed baskets (all holes):
  - applies for basket depth 13-35 blocks below local surface,
  - builds an 8-wide stepped fairway back toward tee,
  - rises with max +2 step and prefers +1 when possible,
  - force-fills water to safe fairway,
  - reroutes around lava (up to 3 lateral attempts), then relocates basket if
    reroute fails.

Validation:

- quickRegression: PASS.
- Clean lifecycle deploy gate: PASS.
- Latest lifecycle smoke summary: pass=3, fail=0, issues=0,
  warningLandingGaps=0, maxLandingGap=63.

---

## Session Continuity Update (2026-05-31)

Note (2026-06-01):

- This section reflects the pre-fix minimap rebuild plan.
- It is retained for historical context, but the minimap baseline issues
  described below are now resolved.
- Use the "Minimap Baseline Update (2026-06-01)" section as current ground
  truth.

### Why this update exists

- Minimap behavior regressed into a mostly tan/beige square and does not match
  visible world terrain.
- Multiple incremental color/sampling tweaks were attempted and did not produce
  acceptable results.
- Team decision: stop blind minimap tweaks and move to a planned rebuild with
  measurable gates.

### Ground truth from current screenshots

- Minimap still fails to show meaningful terrain detail in both shoreline and
  tee/sign viewpoints.
- Water is visible in-world while minimap remains mostly uniform terrain color.
- This is a rendering/sampling pipeline issue, not day/night lighting.

### Active decision (approved)

- Freeze ad-hoc minimap tweaks.
- Rebuild minimap in phased steps with explicit validation before each next
  step.
- Keep old minimap path available behind a feature switch until replacement
  passes.

### Minimap Rebuild Plan (Always-On Navigation + Waypoints)

Goal:

- Always-on minimap (inside and outside rounds), terrain-correct, and
  waypoint-capable like a navigation tool.

Scope requirements:

- Always visible for player by default.
- Works when no active round payload exists.
- Waypoint add/remove/show workflow.
- Course semantics (hazard/OB/path) rendered as transparent overlays, not
  base-terrain replacement.

Phase 0: Observability first

- Add temporary debug readout for center sample source:
  - visible-surface sample path,
  - standable-surface fallback,
  - server fallback,
  - water-detected true/false.
- Purpose: stop blind iterations and identify dominant failing path immediately.

Phase 1: Base terrain renderer replacement

- Build terrain layer from visible surface (world-surface view), not
  standable-only surface.
- Water-first classification over full column between visible and standable
  tops.
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

- Validate while moving continuously through mixed terrain (shoreline, forest
  edge, elevation transitions, and open ground).
- Use minimap debug telemetry as the primary decision input:
  - center source type (`visible-surface`, `heightmap-fallback`,
    `chunk-unloaded`),
  - center fluid type (`solid`, `water`, `lava`),
  - center sampled Y,
  - source pixel counts (`vis`, `fb`, `miss`) from the live render summary.

Per-phase pass criteria:

- Phase 1 passes only when live navigation consistently shows distinct terrain
  features (water/shore/ground boundaries) and telemetry indicates expected
  source dominance (`visible-surface` preferred, fallback paths explainable).
- Phase 2 passes only when overlays are visible but do not flatten or recolor
  base terrain during movement.
- Phase 3 passes only when minimap remains active outside rounds with stable
  player-centered behavior during free navigation.
- Phase 4 passes only when waypoints can be created, rendered, removed, and
  reloaded during free navigation.

### Current status

- Phase 0 not implemented yet (planned next).
- New minimap renderer has not reached acceptable output quality.
- Strict deploy gate remains independently blocked at times by stochastic
  placement issue `basket_deeply_enclosed`; minimap testing currently uses
  artifact-only deploy when needed.

### Next session starting checklist

1. Keep Phase 0 observability active while running navigation-only tests (no
   round workflow required).
2. Traverse mixed terrain paths and collect live debug summaries (`src`,
   `fluid`, `y`, `vis/fb/miss`).
3. Decide exact Phase 1 sampling corrections using telemetry trends and
   in-motion visual correctness.
4. Only after Phase 1 passes navigation validation, proceed to overlays and
   always-on mode hardening.

### Current State Snapshot

Overall:

- Core gameplay loop is complete and stable in single-player.
- Strict mode, scoring, HUD/minimap, persistence/resume, and admin lifecycle are
  implemented.
- Regression tooling (quick + smoke + lifecycle/deploy gates) is active and in
  regular use.

Completed feature sets:

- Deterministic 9-hole generation and placement flow.
- Strict/casual ruleset switching and strict penalties.
- Tournament-feel pack v1 core: signature hole, tee package, round presentation,
  hole result callouts, final summary.
- Practice course persistence and resume.
- ATLauncher build-and-deploy workflow.
- Command-only permanent camp builder (`/mcdg buildcamp`) with 6-player yurt
  site, central campfires, pool, tennis, basketball, and bathhouse.
- Terrain-adaptive camp placement: each structure anchors to local surface Y
  (village-style), with local footprint clearing only.

Remaining focus:

- Closeout and verification for strict flow and resume safety.
- Reliability and release readiness (multiplayer, regressions,
  performance/compatibility).
- Operator/player usability completion (in-game help, menu-driven UX,
  persistence diagnostics).

Deferred by design:

- Dome generation.
- Physical OB markers (full world markers).
- League/tournament expansion systems.

---

## Execution Order (Best Order)

### 1) Dev Session Reliability (Immediate)

Goal:

- Remove strict-session startup deadlock risk so local testing is one-command
  reliable.

Tasks:

- Update strict session launcher flow so client launch does not depend on a
  player-joined completion event.
- Keep local dev auth settings forced (`online-mode=false`,
  `enforce-secure-profile=false`, localhost bind).
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

- Reduce placement edge-case failures while preserving generation
  speed/playability.

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

- Extend checks for strict penalties, lie progression, round completion summary
  correctness.
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
- Update admin lifecycle docs with current scripts, command order, and debug
  bundle collection.

Exit criteria:

- New operator can run full lifecycle from docs/help only.

### 8) Menu-Driven UX + Persistence Expansion

Goal:

- Merge command-driven workflows with guided in-game menus and complete
  persistence quality-of-life features.

Tasks:

- Add admin/player menu entry points for common lifecycle actions
  (create/start/practice/resume/end/reset/ruleset).
- Add menu surfaces for strict presets and quick validation actions.
- Add persistence v2 features:
  - explicit snapshot status/health UI;
  - recovery actions for stale/corrupt snapshot;
  - scoped export/import design for course metadata (seed/layout/state) with
    safety checks.
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

This register consolidates one-off plans and scattered ideas from setup/status
notes into a single implementation view.

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

- Multiplayer reliability pass (2-player smoke + disconnect/rejoin + resume
  consistency).
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
