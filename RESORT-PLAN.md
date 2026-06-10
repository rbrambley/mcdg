# Resort / World Spawn Plan

**Status:** Post-release initiative (after multiplayer validation)
**Goal:** Auto-build a central resort on new worlds, and provide an admin command to build or rebuild it on existing worlds.

---

## Scope

- **Resort** is a distinct large structure built at world spawn on new worlds.
- Includes: lobby, player housing, central courtyard.
- `buildcamp` is deprecated in favor of the resort.
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
| Player Housing | Small individual rooms or bunks |
| Central Courtyard | Open outdoor space connecting all zones |

### Design Notes

- Reuse `CampBuilder` / lodging block palette where appropriate, but layout is unique.
- Use `CoursePlacementService` infrastructure for terrain preparation (clearing, surface normalization, fire protection).
- Protected zone: resort area is marked so `cleanupcourse` does not touch it.
- Resort marker block placed at center for detection (`RESORT_MARKER_BLOCK`).

---

## World Spawn Behavior

### New Worlds

On first player spawn in a fresh world:
1. Detect no resort exists.
2. Generate resort at world spawn coordinates.
3. Set world spawn (`ServerWorld.setSpawnPos`) to resort lobby interior.

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
4. **Update world spawn** (if requested).
   - Set to resort lobby interior.
5. **Feedback.**
   - "Resort built at (X, Y, Z)."

---

## Files to Create / Modify

| File | Action | Purpose |
|------|--------|---------|
| `ResortBuilder.java` | Create | Resort structure placement, room layout, marker block |
| `WorldSpawnHandler.java` | Create | Detect first spawn, trigger auto-build on new worlds |
| `McdgAdminCommands.java` | Modify | Add `/mcdg buildresort` subcommand, deprecate `/mcdg buildcamp` |
| `Plan.md` | Modify | Add to backlog or note buildcamp deprecation |
| `CampBuilder.java` | Modify | Deprecation notice; optionally redirect to resort builder |

---

## Phased Implementation

### Phase 1: Resort Builder Core

- Implement `ResortBuilder` with basic room layout (lobby, courtyard, housing).
- Test: manual `/mcdg buildresort` builds a resort at current position.

### Phase 2: New World Auto-Build

- Implement `WorldSpawnHandler` using `ServerPlayerEvents.AFTER_RESPAWN` or world load event.
- Detect fresh world (no resort marker, no prior player data).
- Trigger resort build on first spawn.
- Set world spawn to resort lobby.

### Phase 3: Admin Command + Overwrite Flow

- Add `/mcdg buildresort` with location arg.
- Implement existing-resort detection and prompt flow.
- Ask about world spawn update on overwrite.
- Deprecate `/mcdg buildcamp` with redirect message.

### Phase 4: Polish

- Protect resort from `cleanupcourse`.
- `gradle quickRegression smokeRegression`.

---

## Integration Notes

| System | Impact |
|--------|--------|
| `CampBuilder` | Deprecated. Existing camps remain; new builds redirect to resort or show deprecation notice. |
| `CoursePlacementService` | Reused for terrain prep, surface resolution, and course placement. |
| `McdgClientMod` | No immediate client changes; menu already shows `listcourses`. Future: resort fast-travel or info screen. |

---

## Acceptance Criteria

- [ ] Fresh single-player world auto-builds resort on first spawn.
- [ ] Fresh server world auto-builds resort on first player join.
- [ ] `/mcdg buildresort` builds at current position on existing worlds.
- [ ] `/mcdg buildresort <x> <z>` builds at specified coordinates.
- [ ] Rebuilding at existing resort shows prompt with overwrite / new location / cancel options.
- [ ] World spawn is updated to resort lobby when requested.
- [ ] `cleanupcourse` does not destroy resort blocks.
- [ ] `gradle quickRegression smokeRegression` passes.

---

## Deprecation Note: `buildcamp`

`/mcdg buildcamp` will be deprecated with a message redirecting to `/mcdg buildresort`. Existing camp sites in old worlds are left intact. No migration required.
