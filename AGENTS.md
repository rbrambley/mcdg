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
- `McdgAdminCommands` — legacy admin command dispatcher (~140 KB, partially split; domain commands extracted)
- `CourseAdminCommands` — course build, cleanup, and management commands
- `RoundAdminCommands` — round setup, start, and control commands
- `RoundLifecycleCommands` — round completion, scoring, and session flow
- `SessionCommands` — player session persistence and resume
- `ResortAdminCommands` — resort build and relocation commands
- `DebugCommands` — diagnostic and test utilities
- `DiscEnchantmentCommands` — enchantment admin/debug commands
- `SeededCourseGenerator` — procedural course generation
- `ResortBuilder` — resort structure placement (lobby, courtyard, housing, wall, lighting)
- `ResortCoursePlacement` — computes 3 terrain-aware course anchors around a resort
- `WorldSpawnHandler` — auto-builds resort on fresh world start
- `ResortWaypointManager` — resort waypoint broadcast to joining players
- `ThrowResolver` — throw resolution, pearl tracking, strict landing (~31 KB, extracted from HoleProgressTracker)
- `TurnManager` — turn order, timeout enforcement, player rotation (~16 KB, extracted from HoleProgressTracker)
- `HoleMapSyncService` — server-side minimap state sync (~18 KB, extracted from HoleProgressTracker)
- `BuildCourseSessionManager` — course placement orchestration and UI state (~50 KB)
- `TickIncrementalCoursePlacer` — tick-spread block placement to avoid lag spikes
- `StaminaXpService` — stamina exhaustion on overcharge, XP rewards for throws and rounds
- `DiscEnchantment` / `DiscEnchantmentHelper` — disc enchantment types and physics application
- `DiscEnchantedBook` — enchanted book item granting disc-specific enchantments
- `DiscWorkbenchBlock` / `DiscWorkbenchScreenHandler` — block and GUI for applying disc enchantments
- `McdgEntityTypes` — entity type registration infrastructure (preserved for future special disc types)

### Client (`src/client/java/com/mcdg/client/`)
- `McdgClientMod` — client initializer, event wiring (~17 KB)
- `HoleMapRenderer` — minimap rendering, terrain sampling, hazard overlays (~19 KB)
- `HudOverlays` — power meter, stance/angle HUD, after-throw stats, charge enhancements (~24 KB)
- `LeftSideHudLayout` — coordinates MCDG HUD with third-party overlays like Xaero's Minimap
- `WaypointManager` — waypoint CRUD, world labels, minimap arrows
- `ClientNetworking` — packet receivers dispatching to correct threads
- `DiscWorkbenchScreen` — client-side GUI for disc workbench enchantment application
- `XaeroMinimapCompat` — soft-dependency detection and HUD layout coordination

### Networking (`src/main/java/com/mcdg/net/`)
- `WaypointSync` — client-to-server waypoint sync
- `RoundRunningScoresSync` — live scoreboard updates
- `AceCinematicSync`, `RoundCompleteCinematicSync` — cinematic overlays

## Known Hotspots
1. `McdgAdminCommands` (~140 KB) — remaining commands should migrate to domain classes (`CourseAdminCommands`, `RoundAdminCommands`, `DebugCommands`, etc.)
2. `BuildCourseSessionManager` (~50 KB) — could split UI helpers (`BuildCourseUIHelper`), preview logic (`BuildPreviewService`), and placement orchestration
3. `HudOverlays` (~24 KB) — could split into `PowerMeterOverlay`, `StanceAngleOverlay`, `AfterThrowStatsOverlay`
4. `HoleMapRenderer` (~19 KB) — could extract `TerrainSampler`, `HazardOverlayRenderer`

## Recent Refactors (Completed)
- `HoleProgressTracker` — `ThrowResolver`, `TurnManager`, and `HoleMapSyncService` extracted
- `McdgAdminCommands` — `CommandPermission`, `CourseAdminCommands`, `DebugCommands`, `DiscEnchantmentCommands`, `MenuCommands`, `ResortAdminCommands`, `RoundAdminCommands`, `RoundLifecycleCommands`, `RulesetCommands`, `SessionCommands` extracted
- `CoursePlacementService` — significantly slimmed; `TickIncrementalCoursePlacer` and `BuildCourseSessionManager` handle most placement orchestration

## Completed Integrations

The following mod integrations were investigated and are now included as soft dependencies in test packs. No further work required.

- **Xaero's Minimap** — HUD positioning coordinated with left-side overlays
- **EMI** — Recipe viewer (no special integration needed)
- **ClientSort** — Inventory sorting (no special integration needed)
- **Serene Seasons, Biomes O' Plenty, TerraBlender, GlitchCore** — World generation variety
- **Veinminer** — Mining convenience (no special integration needed)

## Future Work
- Phase 3: Tiered disc crafting progression (wooden→netherite), disc bags, accessories, skill unlocks (completed), custom disc flight ratings, challenge courses
- Phase 4: Quest system core and content, survival mode rounds
- Phase 5: System integration pass, UI/UX polish, performance optimization, balance & tuning
- Phase 6: Tournament system, advanced customization
- Custom throw animation polish (event-based arm animations, deferred from Phase 2.3 placeholder)
- Server-only feasibility analysis for LAN play without client mod
- Async file I/O for round session saves
- Configurable minimap quality settings (Low/Medium/High)

## Website Implementation Plan

Planned GitHub-hosted site for the MCDG Modpack using Astro + GitHub Pages. The site serves both as a **User Guide** and as a **promotional showcase** for the modpack.

### Decisions
- **Framework:** Astro (free, open-source, fast, excellent for content-heavy sites)
- **Hosting:** GitHub Pages (free, works with public repos, custom-domain support)
- **Design:** Gaming-focused dark theme with tier-based accent colors
- **Primary content:** Existing project documentation plus new landing-page copy
- **Total cost:** $0

### Proposed Site Structure
```
mcdg-website/
├── src/
│   ├── pages/
│   │   ├── index.astro              # Landing page
│   │   ├── guide/
│   │   │   ├── installation.astro
│   │   │   ├── gameplay.astro
│   │   │   ├── progression.astro
│   │   │   └── multiplayer.astro
│   │   ├── features/
│   │   │   ├── physics.astro
│   │   │   ├── courses.astro
│   │   │   ├── wind-system.astro
│   │   │   └── progression.astro
│   │   └── about.astro
│   ├── components/
│   │   ├── layout/Header.astro
│   │   ├── layout/Footer.astro
│   │   ├── ui/Hero.astro
│   │   ├── ui/FeatureCard.astro
│   │   ├── ui/DiscStats.astro
│   │   ├── ui/TierComparison.astro
│   │   └── content/CommandBlock.astro
│   ├── layouts/
│   │   ├── MainLayout.astro
│   │   ├── GuideLayout.astro
│   │   └── FeatureLayout.astro
│   └── styles/global.css
├── public/images/
└── .github/workflows/deploy.yml
```

### Content Sources to Migrate
- **README.md** → landing page overview
- **USERGUIDE.md** → installation, gameplay, controls, troubleshooting
- **SERVER-SETUP-GUIDE.md** → multiplayer/server setup
- **CRAFTING-PROGRESSION-SYSTEM.md** → disc crafting, bags, accessories, skills
- **DISC-FLIGHT-SIMULATION.md** → physics deep-dive + interactive disc flight calculator
- **WIND-SYSTEM-PLAN.md** → wind system feature page
- **RESORT-PLAN.md** → resort feature page
- **TOURNAMENT-PLAN.md** → future tournament feature page
- **FEATURE-STATUS.md** → current roadmap / feature overview

### Visual Assets to Reuse
- Disc textures: `src/main/resources/assets/mcdg/textures/item/`
- Block textures: `src/main/resources/assets/mcdg/textures/block/`
- GUI textures: `src/main/resources/assets/mcdg/textures/gui/`
- Item names/descriptions: `src/main/resources/assets/mcdg/lang/en_us.json`
- New in-game screenshots will be needed for hero/course gallery sections

### Design Direction
- Dark background (`#0f0f1a`) with neon green accent (`#00ff88`)
- Tier color palette:
  - Training: copper (`#b87333`)
  - Wooden: brown (`#8b7355`)
  - Stone: gray (`#7a7a7a`)
  - Iron: silver (`#a0a0a0`)
  - Gold: gold (`#ffd700`)
  - Diamond: cyan (`#00ffff`)
  - Netherite: dark purple (`#1a1a2e`)
- Interactive components: disc flight calculator, tier comparison table, feature cards

### Implementation Phases
1. **Project Setup** — Initialize Astro, Tailwind, sitemap, GitHub Pages config
2. **Design System** — Create global styles, color variables, core UI components
3. **Content Migration** — Convert existing docs to Astro pages
4. **Interactive Features** — Build disc flight calculator, tier comparison, course gallery
5. **Asset Preparation** — Copy textures, optimize images, create hero graphics
6. **Deployment & Testing** — Set up GitHub Actions CI/CD, test build and deploy

### Deployment
- Use GitHub Actions to build on push to `main` and deploy to GitHub Pages
- `.github/workflows/deploy.yml` with `actions/upload-pages-artifact` and `actions/deploy-pages`

### Verification
- Lighthouse score 90+ on all metrics
- Responsive on mobile, tablet, and desktop
- All navigation and interactive components functional
- Images and assets load correctly
- GitHub Pages URL live and accessible

### Maintenance
- Update download links when new releases ship
- Sync feature pages with new design docs as systems evolve
- Add new screenshots when new visual features are added
- Add blog/news section if regular updates begin

### Notes
- No code changes to the MCDG mod itself are required for the website
- In-game screenshots are the main missing asset; textures can be used as placeholders
- Astro, GitHub Pages, and all recommended tools are free for public repositories
