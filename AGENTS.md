# MCDG Project Guide

## Build
- `./gradlew build` — compile both client and server
- `./gradlew test` — run JUnit tests
- `./gradlew quickRegression` — fast invariant/determinism checks
- `./gradlew smokeRegression` — pre-deploy smoke tests
- `./gradlew fullRegression` — quick + smoke + headless lifecycle
- `./gradlew pmdMain` — static analysis report
- `./gradlew jacocoTestReport` — coverage report (run after tests)

## Deploy
- ATLauncher instance: `C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods`
- VS Code task "Build + Deploy to ATLauncher Test Instance" is wired with the path above.
- Manual: `powershell -File scripts\deploy-to-atlauncher.ps1` (requires `ATLAUNCHER_TEST_MODS_DIR` env var if run outside VS Code).

## Testing
- **TrajectoryCalculatorTest**: Mathematical unit tests for disc flight physics
  - Tests stance/angle combinations (OVERHAND, BACKHAND, FOREHAND with HYZER/FLAT/ANHYZER)
  - Validates lateral drift calculations and fade behavior
  - Tests stance/angle cycling logic
  - Located in `src/test/java/com/mcdg/game/TrajectoryCalculatorTest.java`
- **ThrowAutoTestService**: Legacy pearl-based autotest (deprecated for new system)
  - Designed for old ender pearl throw mechanics
  - Not compatible with new calculated trajectory system
  - Kept for baseline regression testing if needed

## Code Conventions
- Java 21, Fabric 1.20.6
- Records for DTOs (see `McdgClientMod.MiniMapState`, `WaypointSync.WaypointEntry`)
- `ConcurrentHashMap` for server-side mutable state
- Static fields acceptable for client-side rendering state (single-threaded)
- Use `Optional` for nullable lookups in managers
- Prefer `MathHelper.floor` over raw casting for block position math

## Architecture

### Server (`src/main/java/com/mcdg/`)
- `McdgMod` — server initializer, wires all services, registers networking and commands
- `RoundStateManager` — thread-safe player round state via `ConcurrentHashMap`
- `ActiveCourseManager` — tracks active course, placement state, round status
- `HoleProgressTracker` — throw resolution, pearl tracking, strict landing, turn timeouts (~1536 lines, needs splitting)
- `CoursePlacementService` — world editing, block placement, validation, signs (~2648 lines, needs splitting)
- `McdgAdminCommands` — all admin commands (~2563 lines, needs splitting)
- `SeededCourseGenerator` — procedural course generation
- `ResortBuilder` — resort structure placement (lobby, courtyard, housing, wall, lighting)
- `ResortCoursePlacement` — computes 3 terrain-aware course anchors around a resort
- `WorldSpawnHandler` — auto-builds resort on fresh world start
- `ResortWaypointManager` — resort waypoint broadcast to joining players

### Client (`src/client/java/com/mcdg/client/`)
- `McdgClientMod` — client initializer, thin event wiring (~180 lines, good)
- `MiniMapRenderer` — minimap rendering, terrain sampling, hazard overlays (~1108 lines, recently extracted)
- `WaypointManager` — waypoint CRUD, world labels, minimap arrows
- `ClientNetworking` — packet receivers dispatching to correct threads

### Networking (`src/main/java/com/mcdg/net/`)
- `HoleMiniMapSync` — server-to-client round state sync
- `WaypointSync` — client-to-server waypoint sync
- `RoundRunningScoresSync` — live scoreboard updates
- `AceCinematicSync`, `RoundCompleteCinematicSync` — cinematic overlays

## Known Hotspots
1. `CoursePlacementService` (~2648 lines) — split into `BlockPlacer`, `SignTextGenerator`, `PlacementValidator`, `StructureCleaner`
2. `HoleProgressTracker` (~1536 lines) — extract `ThrowResolver`, `TurnManager`, `MiniMapSyncService`
3. `McdgAdminCommands` (~2563 lines) — split by command domain (course, round, debug)
4. `MiniMapRenderer` (~1108 lines) — could split `TerrainSampler`, `HazardOverlayRenderer`

## Future Work

### Map Mod Integration (Xaero's Minimap)
**Status:** Done
**Priority:** Medium
**Context:** Now that the old minimap has been replaced with the lightweight hole map HUD, players may want a full-featured map mod for general navigation.

**Investigation findings:**
- JourneyMap: More features, heavier performance cost
- Xaero's Minimap: Lightweight, better for performance (recommended)
- Both are safe to add as soft dependencies (no conflicts with MCDG)

**Implementation approach:**
1. Add Xaero's Minimap as a soft dependency in `fabric.mod.json` (`suggests` field)
2. Detect if Xaero's is loaded via `FabricLoader.getInstance().isModLoaded("xaerominimap")`
3. Provide user guidance to set minimap position to top-left in Xaero's settings
4. Adjust MCDG's left-side HUDs (HoleMapOverlay, RunningScoreboardOverlay) to middle and bottom thirds respectively
5. MCDG works normally without Xaero's installed

**Files to modify:**
- `src/main/resources/fabric.mod.json` — add soft dependency
- `src/client/java/com/mcdg/client/McdgClientMod.java` — add detection and configuration logic

**Pack inclusion:** Xaero's Minimap and Xaero's World Map are included in the current test packs.

### Recipe Viewer Mod (EMI vs REI)
**Status:** Done
**Priority:** Low
**Context:** Players may want a recipe viewer for general Minecraft gameplay, but MCDG doesn't require recipe viewing for disc golf gameplay.

**Investigation findings:**
- **EMI (Almost Enough Items):** Modern, more performant, better API (recommended)
- **REI (Roughly Enough Items):** More established, slightly heavier performance cost
- Both are safe to add as soft dependencies (no conflicts with MCDG)
- No special integration needed (unlike map mods where positioning matters)

**Recommendation:**
- Add EMI as a soft dependency in `fabric.mod.json` (`suggests` field)
- No code changes required — MCDG doesn't need to interact with recipe viewers
- Players can manually install EMI or REI as preferred
- Document recommended mod pack in README

**Pack inclusion:** EMI is included in the current test packs.

### Inventory Sorter Mod
**Status:** Done
**Priority:** Low
**Context:** Players may want inventory sorting for convenience, but MCDG doesn't have complex inventory management needs (mainly discs and tools).

**Investigation findings:**
- **ClientSort:** Lightweight, simple sorting (recommended, used in current packs)
- **Inventory Tweaks Renewed:** More features, heavier performance cost
- **Item Scroller:** Scroll-based sorting, moderate performance cost
- All are safe to add as soft dependencies (no conflicts with MCDG)
- No special integration needed (just UI convenience)

**Recommendation:**
- Add ClientSort as a soft dependency in `fabric.mod.json`
- No code changes required — MCDG doesn't need to interact with inventory sorters
- Players can manually install any inventory sorter as preferred
- Document recommended mod pack in README

**Pack inclusion:** ClientSort is included in the current test packs.

### World Generation Mods (Seasons, Biomes, Weather)
**Status:** Done
**Priority:** Low
**Context:** Players may want enhanced world generation for variety, but MCDG's procedural course generation works with vanilla terrain. These mods could add visual variety to courses.

**Investigation findings:**
- **Serene Seasons:** Adds seasons (spring/summer/autumn/winter) with weather changes (recommended)
- **Biomes O' Plenty:** Adds 80+ new biomes, well-established
- **Oh The Biomes You'll Go (BYG):** Modern, more biomes, better performance (recommended)
- **Weather2:** Enhanced weather effects (storms, seasons)
- All are safe to add as soft dependencies (no conflicts with MCDG)
- MCDG's course generation works with any terrain (vanilla or modded)

**Potential integration:**
- Serene Seasons could affect disc flight physics (wind, snow friction)
- Biome variety could make courses more visually interesting
- Weather could add challenge (rain affecting grip, wind affecting trajectory)

**Recommendation:**
- Add Serene Seasons and Biomes O' Plenty as soft dependencies in `fabric.mod.json`
- Optional: Add weather integration for disc physics (wind, friction changes)
- Document recommended mod pack in README
- Test course generation with modded biomes

**Pack inclusion:** Serene Seasons, Biomes O' Plenty, TerraBlender, and GlitchCore are included in the current test packs.

### Vein Miner Mod
**Status:** Done
**Priority:** Low
**Context:** Players may want vein mining for resource gathering, but MCDG is focused on disc golf gameplay and doesn't have significant mining requirements.

**Investigation findings:**
- **Veinminer (Fabric):** Lightweight, simple (recommended, used in current packs)
- **Ore Excavation:** More features, highly configurable
- All are safe to add as soft dependencies (no conflicts with MCDG)
- No special integration needed (just gameplay convenience)

**Potential uses in MCDG:**
- Clearing terrain for custom course building
- Gathering resources for resort construction
- Speeding up material collection

**Recommendation:**
- Add Veinminer as a soft dependency in `fabric.mod.json`
- No code changes required — MCDG doesn't need to interact with vein miners
- Players can manually install any vein miner as preferred
- Document recommended mod pack in README

**Pack inclusion:** Veinminer is included in the current test packs.
