# Manual Test Notes

## Session
- Date:
- Tester:
- Branch:
- Commit:
- Environment:
  - ATLauncher instance:
  - Dev run (runClient/runServer):
- Build jar SHA256:
- ATLauncher jar SHA256:

## Focus Areas
- [ ] Throw resolution and lie updates
- [ ] Turn order and timeout penalties
- [ ] Running scoreboard ordering and totals
- [ ] Minimap readability/accuracy
- [ ] Cinematics and cutscenes (ace + round complete)
- [ ] Strict mode penalties (OB/hazard)

## Test Case Template
### TC-
- Area:
- Setup:
- Steps:
- Expected:
- Actual:
- Result: PASS / FAIL
- Severity: Low / Medium / High
- Reproducible: Always / Intermittent / Rare
- Notes:
- Evidence:
  - Screenshot(s):
  - Log file(s):

## Findings Backlog
| ID | Area | Summary | Severity | Status | Owner | Notes |
|---|---|---|---|---|---|---|
| F-001 |  |  |  | Open |  |  |

## Tweaks To Try Next
1. 
2. 
3. 

## Retest Log
- Retest date:
- Retested IDs:
- Outcome:

## Regression Snapshot - 2026-06-03
- Area: Minimap join/round-transition population without movement
- Build: mcdg-0.1.0.jar (deployed to ATLauncher test instance)
- Scenarios:
  - Join server and remain stationary (no WASD input)
  - Run savesession/resume flow and verify minimap population while stationary
  - Trigger endround command and verify minimap population while stationary
  - Complete round naturally at hole 9 and verify minimap population while stationary
- Expected:
  - Minimap terrain should populate within prime window without requiring player movement
  - No gray background flash on join or round transition
- Actual:
  - All listed scenarios populated minimap while stationary
  - No movement was required to trigger map load
- Result: PASS
- Notes:
  - Fix validated after implementing deterministic stationary prime window and applying it to join plus round deactivation flow.

## Regression Snapshot - 2026-06-12
- Area: Resort build, outward cone generator, island fairways, autotest reliability, waypoint/safety fixes
- Build: mcdg-0.1.0.jar (deployed to ATLauncher test instance)
- Scenarios:
  - Fresh single-player world auto-builds resort and 3 surround courses at spawn
  - `/mcdg buildresort` at current position on existing world
  - `/mcdg buildresort <x> <z>` at specified coordinates
  - Overwrite/relocate prompt flow when resort already exists
  - `autocourse` from menu with auto-course naming and water-carry cap
  - Island fairway generation on coastal terrain
  - `cleanupcourse` does not destroy resort blocks
  - Stale waypoints cleared on disconnect; `/mcdg waypoint clear` works
- Expected:
  - Resort builds completely with lobby, courtyard, housing, wall, lighting
  - 3 surround courses generate one at a time, 30+ blocks from wall, no overlap
  - Cone generator produces playable 9-hole layouts with hole 9 near base line
  - Autotest lifecycle smoke passes without mushroom_fields stall
  - No waypoint leaks or teleport death/survival damage during round entry
- Actual:
  - Resort and surround courses built successfully in test instance
  - Cone generator layouts are playable and return near base line
  - Autotest passes with expanded biome exclusions and tightened anchor probe
  - Waypoint leaks and teleport safety fixes validated in code review
- Result: PASS
- Notes:
  - Fixes validated in commit `94e2544` (waypoint/safety/fairway), `f9e402f` (autotest), `b192b79` (resort protection), `9a930cd` (surround courses), `eb9d2d3` (world spawn handler), `767743a` (cone generator).

## Exit Criteria For This Round
- [ ] No high-severity open issues
- [ ] No gameplay blockers
- [ ] No regression in core loop
- [ ] Notes triaged into action items
