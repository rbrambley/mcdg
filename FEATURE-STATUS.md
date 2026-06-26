# MCDG Feature Status Truth Table

**Purpose:** Single source of truth mapping every planned feature to its actual code status.
**Last Updated:** 2026-06-26  
**How to read:**
- **Plan Status** — what the design documents claim
- **Code Status** — what actually exists in `src/main/java` and `src/client/java`
- **Risk** — drift between plan and reality (Low = in sync, High = plan overstates progress)

---

## Phase 1: Critical Fixes & Foundation

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Fade/Hyzer/Anhyzer fix | ✅ Complete | `TrajectoryCalculator.java`, `DiscFlightSimulator.java` have time-based fade | Accurate | Low |
| Performance quick wins | ✅ Complete | Cache throttle 750ms, autosave 100 ticks, particle duration 2s, tick metrics | Accurate | Low |
| Real-time progressive trail | ✅ Complete | `ThrowTrailSync`, progressive client rendering, memory leak fixes | Accurate | Low |
| HUD scaling & minimap | ✅ Complete | Lightweight hole HUD, GUI scaling, Xaero's soft-dependency | Accurate | Low |
| Outward teardrop generator | ✅ Complete | `generateOutwardConeCourse` fully implemented; used for all auto-builds | Accurate | Low |
| Disc Workbench | ✅ Complete | `DiscWorkbenchBlock` with GUI for applying disc enchantments via `DiscWorkbenchScreenHandler` | Accurate | Low |
| Training Discs | ✅ Complete | Crafting recipe (8 arrows + copper ingot), permanent reusable disc | Accurate | Low |
| DiscEnchantedBook | ✅ Complete | Enchanted book item integrated with round rewards and merch barrels | Accurate | Low |
| Grappling Disc | ❌ Removed | Removed June 2024 due to reliability issues; entity type infrastructure preserved for future retry | Accurate | Low |
| Boomerang Disc | ❌ Removed | Removed June 2024 due to reliability issues; entity type infrastructure preserved for future retry | Accurate | Low |
| Resort builder | ✅ Complete | `ResortBuilder`, `ResortCourseBuilder`, `WorldSpawnHandler` | Accurate | Low |
| Disc glide physics (phases 1-6) | ✅ Complete | `DiscFlightSimulator`, `ThrowStance`, `ReleaseAngle`, trails, HUD | Accurate | Low |
| Charge enhancements | ✅ Complete | Slower charge, 125% overcharge, power lock, audio cues, distance markers | Accurate | Low |
| Round rewards | ✅ Complete | `RoundRewardService.java` grants enchanted tools, armor, and books based on round score vs par | Accurate | Low |
| Disc enchantments | ✅ Complete | `DiscEnchantment` enum, `DiscEnchantmentHelper`, physics hooks in `TrajectoryCalculator` and `DiscFlightSimulator` | Accurate | Low |
| Biome-themed courses | ✅ Complete | `SeededCourseGenerator` biome profiles with per-biome hazard/elevation/water/vegetation multipliers | Accurate | Low |
| Stamina / XP integration | ✅ Complete | `StaminaXpService` handles exhaustion on overcharge, XP rewards for throws and round completion | Accurate | Low |

---

## Phase 2: Enhanced Gameplay Systems

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Wind system | ✅ Complete | `WindManager`, `WindManagerClient`, `WindSync`, biome/weather/time modifiers, gusts, HUD indicator, admin commands | Accurate | Low |
| Round lifecycle wind automation | ✅ Complete | `RoundWindPolicy`, `RoundWindService`, CALM default, consistent per round, manual override, hooks in round commands | Accurate | Low |
| Tournament wind modes | 📋 Post-release | `TournamentWindManager` not implemented; placeholder remains in `WindManager` | Accurate | Low |
| Custom throw animations | ⏸️ Placeholder | UseAction mappings only (Overhand=SPEAR, Backhand=CROSSBOW, Forehand=BOW); event-based arm animations deferred | Accurate | Low |
| Expanded hazards (sand, rough, ice, cactus, swamp, lava, cliff) | ✅ Complete | `HazardType` enum, `HazardBehavior` record, `HazardManager`, `BiomeHazardProfile`/`HazardPlacementService`, minimap grid colors | Accurate | Low |
| Cave course autobuild | ✅ Complete | `CaveCoursePlacementService`, `CaveStructureBuilder`, `CaveHazardPlacementService` with water tunnels, tee pads, basket markers | Accurate | Low |

---

## Phase 3: Progression Systems

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Disc crafting progression (core) | ✅ Complete | Wooden→Netherite tiered discs, recipes, durability, tier physics, flight stats integration, scorecard souvenirs implemented | Accurate | Low |
| Disc crafting progression (follow-up) | ✅ Complete | Disc bag (`DiscBagItem`, `DiscBagScreenHandler`), accessories (`AccessoryItem`, `AccessoryManager` with glove/towel/range finder), skill unlocks (`SkillUnlock`, `PlayerSkillManager`, `/mcdg skills`) | Accurate | Low |
| Custom disc stats | 📋 Planning | No `DiscFlightRatings`, `DiscCharacteristics`, or disc builder | Accurate | Low |
| Challenge courses | 📋 Planning | No challenge course generation or special rewards | Accurate | Low |

---

## Phase 4: Advanced Features

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Quest system | 📋 Planning | No `QuestManager`, quest data model, or questline structure | Accurate | Low |
| Tournament system | 📋 Post-release | No `TournamentManager`, `TournamentStorage`, or leaderboard sync | Accurate | Low |

---

## Polish & Engineering

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| OB feedback enhancement | 📋 Pending | Straight-line crossing detection still used; no trajectory-based analysis | Accurate | Low |
| Monolithic class split | � Partial | `HoleProgressTracker` split into `ThrowResolver`, `TurnManager`, `HoleMapSyncService`; `McdgAdminCommands` split into domain command classes; `CoursePlacementService` slimmed to ~39 KB with `BuildCourseSessionManager` and `TickIncrementalCoursePlacer` extracted; `MiniMapRenderer` still intact | Accurate | Low |
| Async file I/O | 📋 Pending | Round session saves still synchronous | Accurate | Low |
| Configurable minimap quality | 📋 Pending | No quality settings (Low/Medium/High) | Accurate | Low |

---

## Mod Integrations

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Xaero's Minimap soft-dependency | ✅ Done | `suggests` in `fabric.mod.json`, detection in `McdgClientMod` | Accurate | Low |
| EMI soft-dependency | ✅ Done | `suggests` in `fabric.mod.json` | Accurate | Low |
| ClientSort soft-dependency | ✅ Done | Included in test packs | Accurate | Low |
| Biomes O' Plenty / Serene Seasons | ✅ Done | Included in test packs, no hard dependency | Accurate | Low |
| Veinminer soft-dependency | ✅ Done | Included in test packs | Accurate | Low |

---

## Key Drift Discoveries

### 1. `RoundRewardService` — Implemented but Invisible in Plans
- **Location:** `src/main/java/com/mcdg/game/RoundRewardService.java`
- **What it does:** Grants enchanted tools, armor, and books to survival-mode players based on round score vs par, ace count, and strict ruleset
- **Plan gap:** Not mentioned in `Plan.md`, `MULTI-PHASE-IMPLEMENTATION-PLAN.md`, or `SURVIVAL-ENHANCEMENTS-PLAN.md`
- **Impact:** Phase 1.5 is partially complete; plans should build on this foundation rather than replace it

### 2. `generateOutwardConeCourse` — Fully Implemented but Plans Called it Partial
- **Location:** `src/main/java/com/mcdg/game/AutoCourseService.java`
- **What it does:** Universal cone+teardrop generator for all auto-builds (player `autocourse`, resort surround, regression tests)
- **Plan gap:** `Outward-Teardrop-Course-Generator-Design.md` had no status checkboxes until now
- **Impact:** No code risk; just documentation lag

### 3. Overall Progress Overstated at "33%"
- **Reality:** Phase 1 itself is ~85% complete (11 of 12 sub-features done)
- **The 33% figure counts Phase 1 as 1 of 6 phases**, ignoring that Phase 1 is the foundation and is nearly finished
- **Recommendation:** Use sub-feature completion %, not phase count, for progress tracking

---

## Maintenance Notes

- Update this table **after every feature commit**
- When a feature is implemented, update the corresponding plan document(s) at the same time
- If plan status and code status diverge for more than one week, flag it as high-risk

---

## Lessons Learned: Grappling & Boomerang Discs (June 2024)

### What Went Wrong
- **Compounding errors**: Multiple regression triage and testing variations created unreliable behavior
- **Complex projectile physics**: Both discs required custom entity tick logic that proved difficult to debug
- **State management**: Entity state (returning vs outgoing, distance tracking) became complex and error-prone
- **Limited testing infrastructure**: No dedicated test framework for projectile entity behavior

### What Was Preserved
- **Entity type registration**: Commented out in `McdgEntityTypes.java` for future re-implementation
- **Registration pattern**: Preserved as reference for future special disc types
- **No data migration needed**: Items were not persisted in player data, simplifying removal

### Recommendations for Future Retry
1. **Start with simpler mechanics**: Begin with basic projectile before adding complex behaviors
2. **Dedicated test framework**: Build entity-specific tests before implementation
3. **Incremental state management**: Add state tracking gradually, not all at once
4. **Visual debugging**: Add client-side visualization of entity state during development
5. **Consider server-only approach**: Evaluate if complex projectile logic can be server-side only
