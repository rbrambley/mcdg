# Resort / World Spawn Plan

**Status:** Post-release initiative (after multiplayer validation)
**Goal:** Auto-build a central resort on new worlds with 3 surrounding courses, and provide an admin command to build or rebuild it on existing worlds.

---

## Scope

- **Resort** is a distinct large structure built at world spawn on new worlds.
- Includes: lobby, pro shop, practice green, scoreboard hall, player housing.
- `buildcamp` is deprecated in favor of the resort.
- **3 auto-generated courses** surround the resort without overlapping.
  - Auto-named, no central hub structure, varying difficulty.
  - Automatically added to the reusable course catalog and menu.
- **Admin command** `/mcdg buildresort` for existing worlds or relocation.
  - Accepts optional `[x] [z]` location argument.
  - Updates world spawn to the resort interior.
  - If a resort already exists: notifies, offers overwrite or new location, asks about world spawn change, prompts for confirmation.

---

## Resort Structure

### Rooms / Zones

| Zone | Purpose |
|------|---------|
| Lobby | Player arrival point, information signs |
| Pro Shop | Decorative shop front (future: disc/item exchange) |
| Practice Green | Short putting area with a single basket |
| Scoreboard Hall | Decorative leaderboard walls (future: tournament results) |
| Player Housing | Small individual rooms or bunks |
| Central Courtyard | Open outdoor space connecting all zones |

### Design Notes

- Reuse `CampBuilder` / lodging block palette where appropriate, but layout is unique.
- Use `CoursePlacementService` infrastructure for terrain preparation (clearing, surface normalization, fire protection).
- Protected zone: resort area is marked so `cleanupcourse` does not touch it.
- Resort marker block placed at center for detection (`RESORT_MARKER_BLOCK`).

---

## Auto-Course Generation

### Placement Rules

- 3 courses placed at compass directions from resort (e.g., North, East, South).
- Minimum distance from resort center: configurable (default 96 blocks).
- Minimum distance between course anchors: configurable (default 192 blocks).
- No overlap with resort footprint or each other.
- Each course uses the existing `createcourse` + `placeCourse` flow with fixed anchors.

### Course Presets

| Course | Auto-Name Pattern | Difficulty | Par Target | Features |
|--------|-------------------|------------|------------|----------|
| Front Nine | "{ResortName} Front" | Easy | 32–34 | Shorter holes, wide fairways |
| Back Nine | "{ResortName} Back" | Medium | 34–36 | Standard layout |
| Signature | "{ResortName} Signature" | Hard | 36–38 | Longer holes, signature hole enabled |

### Post-Placement

- Courses saved to `PracticeCourseStorage` as persistent catalog entries.
- No central hub structure placed (resort serves as the shared hub).
- Added to `ActiveCourseManager` catalog index so they appear in `listcourses` / menu.

---

## World Spawn Behavior

### New Worlds

On first player spawn in a fresh world:
1. Detect no resort exists.
2. Generate resort at world spawn coordinates.
3. Build 3 surrounding courses.
4. Set world spawn (`ServerWorld.setSpawnPos`) to resort lobby interior.

### Existing Worlds

- No automatic build. Admin runs `/mcdg buildresort` to trigger.

---

## Admin Command: `/mcdg buildresort`

### Syntax

```
/mcdg buildresort                    # build at current player position
/mcdg buildresort <x> <z>            # build at specified coordinates
```

### Behavior

1. **Check for existing resort.**
   - Search within configured radius for `RESORT_MARKER_BLOCK`.
   - If found: send message — "A resort already exists at (X, Y, Z)."
     - Prompt: `[OVERWRITE]` `[NEW LOCATION]` `[CANCEL]`
     - If Overwrite: ask "Update world spawn to new resort? [YES] [NO]" → confirm → destroy old resort blocks (restore original terrain if possible) or simply build over it.
     - If New Location: accept the provided coordinates, proceed as fresh build.
2. **Validate location.**
   - Surface-resolve the anchor.
   - Ensure no other resort or camp marker within exclusion radius.
3. **Build resort.**
   - Use `ResortBuilder` (new class, modeled after `CampBuilder` but with distinct layout).
   - Place `RESORT_MARKER_BLOCK` at center.
4. **Build 3 courses.**
   - Compute 3 anchor points at minimum distance from resort.
   - Validate no overlap with each other or resort.
   - Generate courses with seeded generator (deterministic seeds from world seed + resort position).
   - Place courses at fixed anchors.
   - Save to catalog.
5. **Update world spawn** (if requested).
   - Set to resort lobby interior.
6. **Feedback.**
   - "Resort built at (X, Y, Z). 3 courses generated and added to catalog."

---

## Files to Create / Modify

| File | Action | Purpose |
|------|--------|---------|
| `ResortBuilder.java` | Create | Resort structure placement, room layout, marker block |
| `ResortPlacementService.java` | Create | Location validation, course anchor computation, overlap checks |
| `WorldSpawnHandler.java` | Create | Detect first spawn, trigger auto-build on new worlds |
| `McdgAdminCommands.java` | Modify | Add `/mcdg buildresort` subcommand, deprecate `/mcdg buildcamp` |
| `Plan.md` | Modify | Add to backlog or note buildcamp deprecation |
| `CampBuilder.java` | Modify | Deprecation notice; optionally redirect to resort builder |

---

## Phased Implementation

### Phase 1: Resort Builder Core

- Implement `ResortBuilder` with basic room layout (lobby, courtyard, housing).
- Implement `ResortPlacementService` with surface resolution and marker placement.
- Test: manual `/mcdg buildresort` builds a resort at current position.

### Phase 2: Auto-Course Placement

- Compute 3 anchor points around resort.
- Generate and place courses with varying difficulty.
- Save to catalog.
- Verify courses appear in `listcourses` and menu.

### Phase 3: New World Auto-Build

- Implement `WorldSpawnHandler` using `ServerPlayerEvents.AFTER_RESPAWN` or world load event.
- Detect fresh world (no resort marker, no prior player data).
- Trigger resort + course build on first spawn.
- Set world spawn to resort lobby.

### Phase 4: Admin Command + Overwrite Flow

- Add `/mcdg buildresort` with location arg.
- Implement existing-resort detection and prompt flow.
- Ask about world spawn update on overwrite.
- Deprecate `/mcdg buildcamp` with redirect message.

### Phase 5: Polish

- Add practice green with a single basket.
- Add pro shop and scoreboard hall decorative blocks.
- Protect resort from `cleanupcourse`.
- `gradle quickRegression smokeRegression`.

---

## Integration Notes

| System | Impact |
|--------|--------|
| `CampBuilder` | Deprecated. Existing camps remain; new builds redirect to resort or show deprecation notice. |
| `CoursePlacementService` | Reused for terrain prep, surface resolution, and course placement. |
| `PracticeCourseStorage` | 3 new catalog entries added on resort build. |
| `ActiveCourseManager` | Courses available for `listcourses` / `playcourse` immediately after build. |
| `SeededCourseGenerator` | Used with deterministic seeds derived from world seed + resort position. |
| `McdgClientMod` | No immediate client changes; menu already shows `listcourses`. Future: resort fast-travel or info screen. |

---

## Acceptance Criteria

- [ ] Fresh single-player world auto-builds resort + 3 courses on first spawn.
- [ ] Fresh server world auto-builds resort + 3 courses on first player join.
- [ ] `/mcdg buildresort` builds at current position on existing worlds.
- [ ] `/mcdg buildresort <x> <z>` builds at specified coordinates.
- [ ] Rebuilding at existing resort shows prompt with overwrite / new location / cancel options.
- [ ] World spawn is updated to resort lobby when requested.
- [ ] 3 generated courses vary in difficulty and are listed in the catalog menu.
- [ ] No course overlap with resort or each other.
- [ ] `cleanupcourse` does not destroy resort blocks.
- [ ] `gradle quickRegression smokeRegression` passes.

---

## Deprecation Note: `buildcamp`

`/mcdg buildcamp` will be deprecated with a message redirecting to `/mcdg buildresort`. Existing camp sites in old worlds are left intact. No migration required.
