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

## Completed Integrations

The following mod integrations were investigated and are now included as soft dependencies in test packs. No further work required.

- **Xaero's Minimap** — HUD positioning coordinated with left-side overlays
- **EMI** — Recipe viewer (no special integration needed)
- **ClientSort** — Inventory sorting (no special integration needed)
- **Serene Seasons, Biomes O' Plenty, TerraBlender, GlitchCore** — World generation variety
- **Veinminer** — Mining convenience (no special integration needed)

## Future Work
