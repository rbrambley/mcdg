# Tournament System Plan

**Status:** Post-release initiative (after multiplayer validation + compact course layout)
**Goal:** Add stroke-play tournaments where players compete across 1–4 admin-selected rounds.

---

## Scope

- **Format:** Stroke play only. Total strokes across all rounds, lowest wins.
- **Player count:** Solo (personal best chasing) and multiplayer (2+).
- **Course selection:** Admin picks each round’s course from the reusable catalog at creation time.
- **Spectators:** All online players receive live leaderboard updates.
- **Handicaps:** None. Raw scores only.

---

## Data Model

```java
record Tournament(
    UUID id,
    String name,
    List<TournamentRound> rounds,      // 1–4 courses, admin-defined
    List<UUID> participants,
    Map<UUID, TournamentScorecard> scorecards,
    TournamentStatus status,           // REGISTERING -> IN_PROGRESS -> COMPLETE
    long createdAt
);

record TournamentRound(
    int roundNumber,
    Integer catalogIndex,              // null if course no longer exists
    String courseName,                 // snapshot for display
    boolean completed
);

record TournamentScorecard(
    UUID playerId,
    int totalStrokes,
    Map<Integer, Integer> roundTotals, // roundNumber -> strokes
    List<Integer> perHoleScores        // flat list across all rounds
);
```

---

## Lifecycle

1. **Create**
   - Admin opens MCDG menu → Tournament → Create.
   - Enter name, pick number of rounds (1–4).
   - Select each round’s course from the reusable catalog (`listcourses`).
   - Tournament enters `REGISTERING` status.

2. **Register**
   - Players join via menu or `/mcdg tournament join <name>`.
   - Admin can manually add/remove participants from the tournament menu.

3. **Start Round N**
   - Admin presses Start Round in the tournament menu.
   - System calls existing `startround` flow but tags the round as tournament-bound.
   - Only registered participants are enrolled.
   - Status becomes `IN_PROGRESS`.

4. **Score Aggregation**
   - Each hole completion feeds into the tournament scorecard.
   - `RoundRunningScoresSync` extended with optional tournament context.
   - Cumulative totals computed server-side.

5. **Between Rounds**
   - Leaderboard broadcast to all online clients via `TournamentLeaderboardSync`.
   - Admin starts next round when ready.

6. **Finish**
   - Final round completes.
   - Winner(s) declared in chat and on leaderboard screen.
   - Tournament archived to disk and status set to `COMPLETE`.

---

## Menu Integration

New **Tournament** button in the main MCDG menu:

- **Create Tournament** (admin only)
- **Join Tournament**
- **Current Standings**
- **My History** (past tournaments + personal bests)

---

## Admin Commands

```
/mcdg tournament create <name> <roundCount>
/mcdg tournament addplayer <name> <player>
/mcdg tournament removeplayer <name> <player>
/mcdg tournament startround <name> [roundNumber]
/mcdg tournament standings <name>
/mcdg tournament archive <name>
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `TournamentManager.java` | Registration, lifecycle, score aggregation, status transitions |
| `TournamentStorage.java` | JSON persistence, load/save tournaments, archival |
| `TournamentLeaderboardSync.java` | Server-to-client leaderboard packet |
| `TournamentScreen.java` | Client standings UI (leaderboard display) |
| `TournamentHistoryScreen.java` | Client "My History" archive view |

---

## Phased Implementation

### Phase 1: Core Data Model + Solo Create/Join/Play

- Implement `Tournament`, `TournamentRound`, `TournamentScorecard` records.
- Implement `TournamentManager` with create, register, start round, finish.
- Wire into `McdgAdminCommands` with basic chat commands.
- Solo-only for initial validation.
- Test: create 2-round tournament, complete solo, verify total score.

### Phase 2: Score Aggregation + Leaderboard Sync

- Extend `RoundRunningScoresSync` or add `TournamentLeaderboardSync` packet.
- Compute cumulative scores per player across rounds.
- Build basic `TournamentScreen` client UI showing standings.
- Spectators receive leaderboard updates.

### Phase 3: Multiplayer Support

- Ensure only registered participants are enrolled in tournament rounds.
- Test 2-player tournament end-to-end.
- Verify non-participants are not affected.
- Menu integration: Create / Join / Standings buttons.

### Phase 4: Persistence + History

- `TournamentStorage` saves active and completed tournaments to `tournaments/` JSON.
- Resume in-progress tournaments after server restart.
- `My History` screen showing past tournaments, final standings, and personal bests.
- Archive / cleanup old tournaments.

---

## Integration Notes

| System | Impact |
|--------|--------|
| `RoundStateManager` | No changes. Tournament reads scores after round completion. |
| `HoleProgressTracker` | No changes. Hole scoring already feeds into round totals. |
| `RoundRunningScoresSync` | Extend with optional `tournamentId` field, or send separate `TournamentLeaderboardSync` packet. |
| `PracticeCourseStorage` | Tournament stores catalog index per round; uses same storage pattern. |
| `McdgAdminCommands` | Add `tournament` subcommand branch. |
| `McdgClientMod` | Add Tournament menu button and screen wiring. |
| `MiniMapRenderer` | No changes. Minimap shows current hole independently of tournament state. |

---

## Acceptance Criteria

- [ ] Admin can create a 2-round tournament with specific catalog courses.
- [ ] Solo player completes both rounds and sees correct cumulative score.
- [ ] 2-player tournament completes; winner is the player with lowest total strokes.
- [ ] Spectators see live standings updates during the tournament.
- [ ] Tournament survives server restart and resumes in-progress state.
- [ ] Completed tournaments are archived and viewable in "My History."
- [ ] `gradle quickRegression smokeRegression` passes.

---

## Backlog / Nice to Have

- Match play brackets (post-MVP)
- Team best ball (post-MVP)
- Tournament "packs" — pre-defined multi-course sequences for quick setup
- Scheduled tournaments (start at specific time, auto-close registration)
- Tie-breaker rules (card playoff, sudden-death hole replay)
