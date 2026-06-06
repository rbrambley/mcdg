# MCDG Project Guide

## Build
- `./gradlew build` — compile both client and server
- `./gradlew test` — run JUnit tests
- `./gradlew quickRegression` — fast invariant/determinism checks
- `./gradlew smokeRegression` — pre-deploy smoke tests
- `./gradlew fullRegression` — quick + smoke + headless lifecycle
- `./gradlew pmdMain` — static analysis report
- `./gradlew jacocoTestReport` — coverage report (run after tests)

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
- `HoleProgressTracker` — throw resolution, pearl tracking, strict landing, turn timeouts (~1900 lines, needs splitting)
- `CoursePlacementService` — world editing, block placement, validation, signs (~3500 lines, needs splitting)
- `McdgAdminCommands` — all admin commands (~2100 lines, needs splitting)
- `SeededCourseGenerator` — procedural course generation

### Client (`src/client/java/com/mcdg/client/`)
- `McdgClientMod` — client initializer, thin event wiring (~180 lines, good)
- `MiniMapRenderer` — minimap rendering, terrain sampling, hazard overlays (~1285 lines, recently extracted)
- `WaypointManager` — waypoint CRUD, world labels, minimap arrows
- `ClientNetworking` — packet receivers dispatching to correct threads

### Networking (`src/main/java/com/mcdg/net/`)
- `HoleMiniMapSync` — server-to-client round state sync
- `WaypointSync` — client-to-server waypoint sync
- `RoundRunningScoresSync` — live scoreboard updates
- `AceCinematicSync`, `RoundCompleteCinematicSync` — cinematic overlays

## Known Hotspots
1. `CoursePlacementService` (~3500 lines) — split into `BlockPlacer`, `SignTextGenerator`, `PlacementValidator`, `StructureCleaner`
2. `HoleProgressTracker` (~1900 lines) — extract `ThrowResolver`, `TurnManager`, `MiniMapSyncService`
3. `McdgAdminCommands` (~2100 lines) — split by command domain (course, round, debug)
4. `MiniMapRenderer` (~1285 lines) — could split `TerrainSampler`, `HazardOverlayRenderer`
