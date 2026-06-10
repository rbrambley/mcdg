# Multiplayer Quick Pass (Single Session)

Date: __________
Host: __________
Tester(s): __________
Build/Commit: __________
Mode: LAN / e4mc / Dedicated

## Goal

Run a high-signal multiplayer verification in one sitting (about 45-75 minutes).

## Menu Access

Press G (default) or type /mcdg to open the MCDG menu. All admin actions below can be run via chat commands or the menu — both are valid.

## Pass Criteria

- All Critical tests pass.
- No blocker or high-severity desync issue remains open.

## Evidence to Capture

- Host latest log path/time window.
- Tester latest log path/time window.
- Screenshots of any FAIL or unexpected HUD/state mismatch.

---

## Critical Tests (Run These First)

### QP-01 Start + Enrollment Sanity
Priority: Critical

Steps:
1. /mcdg createcourse 123456789  (or Menu -> Auto Build Course)
2. /mcdg startround <players>  (or Menu -> Admin -> Start Round)

Expected:
- Only selected players are enrolled/staged.
- Non-selected players are unaffected.

Result: PASS / FAIL
Notes:

### QP-02 Late Join + Idempotency
Priority: Critical

Steps:
1. Mid-round: /mcdg joinround <latePlayer>
2. Repeat on already-active player: /mcdg joinround <samePlayer>

Expected:
- Late player joins correctly.
- Re-running on same player causes no duplicate side effects.

Result: PASS / FAIL
Notes:

### QP-03 Disconnect/Rejoin Continuity
Priority: Critical

Steps:
1. Active participant disconnects mid-hole.
2. Reconnect while round is still active.

Expected:
- Player auto-restores.
- Restored lie is within 2 blocks of saved lie.
- Player can throw immediately and continue hole.

Result: PASS / FAIL
Notes:

### QP-04 Turn Order + Timeout Sync
Priority: Critical

Steps:
1. Attempt wrong-player throw in multiplayer round.
2. Let timeout logic trigger naturally once.

Expected:
- Wrong-turn throw is blocked.
- Timeout progression/penalty is consistent across both clients.

Result: PASS / FAIL
Notes:

### QP-05 Save Session Removes Player
Priority: Critical

Steps:
1. Active participant runs /mcdg savesession (or admin runs /mcdg savesession <player>).

Expected:
- Session saved for that player.
- Player removed from active participants/state.
- If last participant saved out, round becomes inactive.

Result: PASS / FAIL
Notes:

### QP-06 Resume Session Default (Manual Preferred)
Priority: Critical

Steps:
1. Resume with /mcdg resumesession <player>.

Expected:
- Manual save is used when present.
- Player restored to saved hole/strokes.
- Teleport lands within 2 blocks of saved lie.

Result: PASS / FAIL
Notes:

### QP-07 Resume Source Override
Priority: Critical

Steps:
1. /mcdg resumesession manual <player>
2. /mcdg resumesession auto <player>

Expected:
- manual only uses manual snapshot path.
- auto only uses auto-state path.

Result: PASS / FAIL
Notes:

### QP-08 Recreate/Restore + Finish Safety
Priority: Critical

Steps:
1. Ensure no active round context.
2. /mcdg resumesession <player>
3. Complete with /mcdg endround then /mcdg cleanupcourse

Expected:
- Context restores when available.
- Player resumes and can continue.
- Cleanup/end do not unexpectedly alter non-participants.

Result: PASS / FAIL
Notes:

---

## Optional Fast Checks (If Time Remains)

### QP-09 Running Score Panel Cross-Client
- Verify last-3-hole window, ordering, and totals match on both clients.

### QP-10 e4mc Current Domain Validity
- Confirm current domain works while host stays online.
- Confirm stale/old domain fails after re-share/restart.

---

## Session Summary

Total Critical Passed: ____ / 8
Blockers Found: Yes / No
High Severity Found: Yes / No

Go/No-Go for next multiplayer session: GO / NO-GO

Action items:
1. 
2. 
3. 
