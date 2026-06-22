# MCDG Feature Status Truth Table

**Purpose:** Single source of truth mapping every planned feature to its actual code status.  
**Last Updated:** 2026-06-22  
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
| Outward teardrop generator | ⚠️ Partial (plans) | `generateOutwardConeCourse` fully implemented; used for all auto-builds | **Done, plans understated** | Low |
| Resort builder | ✅ Complete | `ResortBuilder`, `ResortCourseBuilder`, `WorldSpawnHandler` | Accurate | Low |
| Disc glide physics (phases 1-6) | ✅ Complete | `DiscFlightSimulator`, `ThrowStance`, `ReleaseAngle`, trails, HUD | Accurate | Low |
| Charge enhancements | ✅ Complete | Slower charge, 125% overcharge, power lock, audio cues, distance markers | Accurate | Low |
| Round rewards | 🔄 In progress (plans) | `RoundRewardService.java` exists and is fully functional | **Done, plans missed it** | Low |
| Disc enchantments | 🔄 In progress | No `DiscEnchantment` enum or enchantment physics integration | Accurate | Low |
| Biome-themed courses | 🔄 In progress | No `BiomeCourseProfiles` class or biome-aware generation | Accurate | Low |
| Stamina / XP integration | 🔄 In progress | No stamina system; XP rewards not wired to disc golf | Accurate | Low |

---

## Phase 2: Enhanced Gameplay Systems

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Wind system | 📋 Planning | No `WindManager`, `WindState`, or wind physics hooks | Accurate | Low |
| Custom throw animations | ⏸️ Placeholder | UseAction mappings only (Overhand=SPEAR, Backhand=CROSSBOW, Forehand=BOW) | Accurate | Low |
| Expanded hazards (sand, rough, ice) | 📋 Planning | Basic `OutOfBoundsClassifier` exists; no new hazard types | Accurate | Low |

---

## Phase 3: Progression Systems

| Feature | Plan Status | Code Status | Verdict | Risk |
|---------|-------------|-------------|---------|------|
| Disc crafting progression | 📋 Planning | No crafting recipes for wooden/stone/iron/gold/diamond/netherite discs | Accurate | Low |
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
| Monolithic class split | 📋 Planning | `CoursePlacementService` (~2,648 lines), `HoleProgressTracker` (~1,536 lines), `McdgAdminCommands` (~2,563 lines), `MiniMapRenderer` (~1,108 lines) all still intact | Accurate | Low |
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
