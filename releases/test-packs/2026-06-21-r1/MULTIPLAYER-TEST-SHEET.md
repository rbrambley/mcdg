# Multiplayer Reliability Test Sheet

Date: __________
Tester(s): __________
Build/Commit: bf27d8b (2026-06-17)
Host Environment: ATLauncher / Dev / Other
Network Mode: LAN / e4mc / Dedicated

## Scope for This Pass

This sheet targets multiplayer scenarios that are still open or recently changed:
- 2-player full-round reliability and state consistency.
- Disconnect/rejoin continuity.
- Late join and join idempotency.
- Turn order and timeout behavior across clients.
- Running score panel and HUD sync across clients.
- Cleanup and non-participant safety.
- New player session commands: savesession and resumesession.

## Required Setup

1. Host and at least one tester are online in the same version/mod set.
2. Host can run admin commands.
3. Use a fresh world or known-clean test world.
4. If using e4mc, capture the exact hosted domain shown in chat.
5. Press G (default) or type /mcdg to open the MCDG menu. All admin actions are available via the menu or via chat commands — both are valid for testing.

## Evidence to Capture

- Host log file path and timestamp window.
- Tester log file path and timestamp window.
- Screenshot of:
  - running score panel,
  - actionbar turn HUD,
  - savesession and resumesession command feedback,
  - any errors/exceptions.

## Results Key

PASS = behavior exactly matches expected.
FAIL = mismatch or blocked flow.
N/A = not run.

---

## Test Cases

### MP-01 Participant Enrollment Isolation
Priority: High

Steps:
1. Run /mcdg createcourse 123456789 (or Menu -> Auto Build Course)
2. Run /mcdg startround <players> with only a subset selected (or Menu -> Admin -> Start Round)
3. Observe selected and non-selected players.

Expected:
- Only selected players are enrolled/staged.
- Non-selected players are not teleported, not given round items, and not tracked as round participants.

Result: PASS / FAIL / N/A
Notes:

### MP-02 Late Join Enrollment
Priority: High

Steps:
1. Start a round with 1-2 participants.
2. Mid-round, run /mcdg joinround <latePlayer>.

Expected:
- Late player receives round inventory and scorecard.
- Late player is teleported for entry and becomes tracked.
- Existing participants are unchanged.

Result: PASS / FAIL / N/A
Notes:

### MP-03 Join Idempotency
Priority: High

Steps:
1. With an already-active participant, run /mcdg joinround <samePlayer> again.

Expected:
- No duplicate enrollment side effects.
- Player remains valid and playable.
- Command feedback reports already-active behavior.

Result: PASS / FAIL / N/A
Notes:

### MP-04 Disconnect/Rejoin Continuity (Active Round)
Priority: High

Steps:
1. During a hole, have a participant disconnect.
2. Reconnect that participant while round remains active.

Expected:
- Player auto-restores as active participant.
- Player lie after rejoin is within 2 blocks of saved lie.
- Player can immediately throw and continue.
- Hole/stroke state remains consistent with pre-disconnect state.

Result: PASS / FAIL / N/A
Notes:

### MP-05 Turn Order Enforcement and Timeout
Priority: High

Steps:
1. In a 2+ player round, try throwing with the wrong player.
2. Let timeout flow occur where applicable.

Expected:
- Wrong-turn throw is blocked.
- Timeout penalties/turn progression are applied correctly and shown to both clients.
- No desync between host and tester turn state.

Result: PASS / FAIL / N/A
Notes:

### MP-06 Running Score Panel Sync (Last 3 Holes)
Priority: Medium

Steps:
1. Play through at least 4 holes with 2 players.
2. Compare both clients' running score panel.

Expected:
- Panel appears only during active round context.
- Sliding last-3-hole window behavior is correct.
- Totals and ordering match on both clients.

Result: PASS / FAIL / N/A
Notes:

### MP-07 Round Status Accuracy
Priority: Medium

Steps:
1. During multiplayer round, run /mcdg roundstatus.
2. Compare command output with observed players and states.

Expected:
- Active flag, participant counts, online/offline counts, and hole/stroke summaries are accurate.

Result: PASS / FAIL / N/A
Notes:

### MP-08 Save Session (Self) Removes Player from Round
Priority: High

Steps:
1. Active participant runs /mcdg savesession.
2. Check round participant list/status.

Expected:
- Manual session snapshot is saved for that player.
- Player is removed from active participants and in-memory round state.
- Player can leave safely without active-round tracking.

Result: PASS / FAIL / N/A
Notes:

### MP-09 Save Session (Admin Target)
Priority: High

Steps:
1. Admin runs /mcdg savesession <player> on an active participant.

Expected:
- Target player session is saved.
- Target is removed from active round tracking.
- No unrelated participant state changes.

Result: PASS / FAIL / N/A
Notes:

### MP-10 Resume Session Default Precedence (Manual then Auto)
Priority: High

Steps:
1. Ensure target has a manual savesession snapshot.
2. Run /mcdg resumesession <player>.

Expected:
- Manual snapshot is preferred by default.
- Player is restored to hole/strokes from manual snapshot.
- Player lie after resume is within 2 blocks of saved lie.
- Player can immediately throw and continue.

Result: PASS / FAIL / N/A
Notes:

### MP-11 Resume Session Source Override
Priority: High

Steps:
1. Run /mcdg resumesession manual <player>.
2. Run /mcdg resumesession auto <player>.

Expected:
- manual uses only manual snapshot path.
- auto uses only active auto-state path.
- Command behavior/feedback matches selected source.

Result: PASS / FAIL / N/A
Notes:

### MP-12 Resume Session Recreate/Restore Context
Priority: High

Steps:
1. Save a player session.
2. Ensure no active round is currently live.
3. Run /mcdg resumesession <player>.

Expected:
- Course/placement context is restored when available.
- Player is re-added as active participant.
- Round becomes active for resumed participants.

Result: PASS / FAIL / N/A
Notes:

### MP-13 Strict Penalty Sync in Multiplayer
Priority: Medium

Steps:
1. In strict ruleset, trigger hazard landing and OB landing scenarios.
2. Observe both clients.

Expected:
- Penalty stroke application is consistent on both clients.
- Teleport/lie correction occurs and remains playable.
- HUD/title feedback appears correctly.

Result: PASS / FAIL / N/A
Notes:

### MP-14 End Round/Cleanup Non-Participant Safety
Priority: High

Steps:
1. Keep at least one player as non-participant.
2. Run /mcdg endround then /mcdg cleanupcourse.

Expected:
- Participant cleanup is correct.
- Non-participants are not unexpectedly modified (inventory/state/teleport).

Result: PASS / FAIL / N/A
Notes:

### MP-15 e4mc Session Reliability Window
Priority: Medium

Steps:
1. Open to LAN with e4mc and share the exact current domain.
2. Tester joins.
3. Repeat after host restarts LAN share (new domain expected).

Expected:
- Current domain works while host remains online.
- Stale/old domain no longer works after restart or re-share.
- Any timeout/refused errors are captured with timestamps on both sides.

Result: PASS / FAIL / N/A
Notes:

---

## Defect Log

ID | Test Case | Summary | Severity | Repro Rate | Owner | Status
---|---|---|---|---|---|---
MP-BUG-001 |  |  |  |  |  | Open

## Exit Criteria for Multiplayer Closeout

- [ ] MP-01 through MP-05 are PASS.
- [ ] MP-08 through MP-12 are PASS.
- [ ] No high-severity open multiplayer defects.
- [ ] Logs/screenshots captured for any FAIL results.
- [ ] Follow-up fixes converted into explicit action items.
