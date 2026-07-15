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
- **PlacementAutoTestService**: Course placement validation via headless server testing
  - Tests procedural course generation across random biomes and terrain
  - Currently has known limitations with automated terrain selection
  - Ocean and deep ocean biomes are filtered but some variants may still be selected
  - Manual course placement (ATLauncher) works fine; this is an automated edge case issue
  - Full regression test may not pass 100% due to these terrain selection edge cases

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
- `HoleProgressTracker` — throw resolution, strict landing, turn timeouts (~1536 lines, needs splitting)
- `CoursePlacementService` — world editing, block placement, validation, signs (~2648 lines, needs splitting)
- `McdgAdminCommands` — legacy admin command dispatcher (~140 KB, partially split; domain commands extracted)
- `CourseAdminCommands` — course build, cleanup, and management commands
- `RoundStartCommand` — round start and practice course commands
- `RoundLifecycleCommands` — round completion, scoring, and session flow
- `SessionCommands` — player session persistence and resume
- `ResortAdminCommands` — resort build and relocation commands
- `DebugCommands` — diagnostic and test utilities
- `DiscEnchantmentCommands` — enchantment admin/debug commands
- `SeededCourseGenerator` — procedural course generation
- `ResortBuilder` — resort structure placement (lobby, courtyard, housing, wall, lighting)
- `ResortCoursePlacement` — computes 3 terrain-aware course anchors around a resort
- `WorldSpawnHandler` — auto-builds resort on fresh world start; auto-generates lost-course entrances only on fresh worlds (pre-existing worlds without `lost-courses.nbt` are skipped to avoid conflicts with existing builds)
- `ResortWaypointManager` — resort waypoint broadcast to joining players
- `ThrowResolver` — throw resolution, calculated trajectory tracking, strict landing (~31 KB, extracted from HoleProgressTracker)
- `TurnManager` — turn order, timeout enforcement, player rotation (~16 KB, extracted from HoleProgressTracker)
- `HoleMapSyncService` — server-side minimap state sync (~18 KB, extracted from HoleProgressTracker)
- `BuildCourseSessionManager` — course placement orchestration and UI state (~50 KB)
- `TickIncrementalCoursePlacer` — tick-spread block placement to avoid lag spikes
- `StaminaXpService` — stamina exhaustion on overcharge, XP rewards for throws and rounds
- `DiscEnchantment` / `DiscEnchantmentHelper` — disc enchantment types and physics application
- `DiscEnchantedBook` — enchanted book item granting disc-specific enchantments
- `DiscWorkbenchBlock` / `DiscWorkbenchScreenHandler` — block and GUI for applying disc enchantments
- `McdgEntityTypes` — entity type registration infrastructure (preserved for future special disc types)
- `BossMobSpawner` — lifecycle management for boss hole challenge course mobs
- `BossMobConfig` — mob type/spawn-rate configuration for boss holes
- `BossMobPositioner` — strategic spawn positioning around baskets, fairways, and tees
- `BossRewardGenerator` / `CombatEnchantment` — tiered enchanted equipment rewards for boss holes
- `GuardBasketGoal` / `PatrolFairwayGoal` — custom mob AI goals for guarding and patrolling

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
1. `McdgAdminCommands` (~140 KB) — remaining commands should migrate to domain classes (`CourseAdminCommands`, `DebugCommands`, etc.)
2. `BuildCourseSessionManager` (~50 KB) — could split UI helpers (`BuildCourseUIHelper`), preview logic (`BuildPreviewService`), and placement orchestration
3. `HudOverlays` (~24 KB) — could split into `PowerMeterOverlay`, `StanceAngleOverlay`, `AfterThrowStatsOverlay`
4. `HoleMapRenderer` (~19 KB) — could extract `TerrainSampler`, `HazardOverlayRenderer`

## Recent Refactors (Completed)
- `HoleProgressTracker` — `ThrowResolver`, `TurnManager`, and `HoleMapSyncService` extracted
- `McdgAdminCommands` — `CommandPermission`, `CourseAdminCommands`, `DebugCommands`, `DiscEnchantmentCommands`, `MenuCommands`, `ResortAdminCommands`, `RoundLifecycleCommands`, `RoundStartCommand`, `RulesetCommands`, `SessionCommands` extracted
- `CoursePlacementService` — significantly slimmed; `TickIncrementalCoursePlacer` and `BuildCourseSessionManager` handle most placement orchestration

## Completed Integrations

The following mod integrations were investigated and are now included as soft dependencies in test packs. No further work required.

- **Xaero's Minimap** — HUD positioning coordinated with left-side overlays
- **EMI** — Recipe viewer (no special integration needed)
- **ClientSort** — Inventory sorting (no special integration needed)
- **Serene Seasons, Biomes O' Plenty, TerraBlender, GlitchCore** — World generation variety
- **Veinminer** — Mining convenience (no special integration needed)

## Future Work
- Phase 3: Tiered disc crafting progression (wooden→netherite), disc bags, accessories, skill unlocks (completed), challenge courses (completed), custom disc flight ratings
- Phase 4: Quest system core and content, survival mode rounds
- Phase 5: System integration pass, UI/UX polish, performance optimization, balance & tuning
- Phase 6: Tournament system, advanced customization
- Custom throw animation polish (event-based arm animations, deferred from Phase 2.3 placeholder)
- Server-only feasibility analysis for LAN play without client mod
- Async file I/O for round session saves
- Configurable minimap quality settings (Low/Medium/High)
- Range finder caddie feature: right-click during a round to show recommended disc, stance, angle, elevation, and power for the current basket; optional auto-throw variant at an XP cost

## Website Implementation Plan

Planned GitHub-hosted site for the MCDG Modpack using Astro + GitHub Pages. The site serves both as a **User Guide** and as a **promotional showcase** for the modpack.

### Decisions
- **Framework:** Astro (free, open-source, fast, excellent for content-heavy sites)
- **Hosting:** GitHub Pages (free, works with public repos, custom-domain support)
- **Repository:** Site source lives in the existing `rbrambley/mcdg` repo
- **URLs:**
  - **Local dev:** `http://localhost:4321/mcdg/`
  - **GitHub Pages:** `https://rbrambley.github.io/mcdg/`
- **Design:** Gaming-focused dark theme with tier-based accent colors
- **Primary content:** Existing project documentation plus new landing-page copy
- **Total cost:** $0

### Expanded Site Structure
```
docs/                          # Site source folder for GitHub Pages
├── src/
│   ├── pages/
│   │   ├── index.astro                      # Landing page
│   │   ├── guide/
│   │   │   ├── installation.astro           # Installation guide
│   │   │   ├── gameplay.astro               # Core gameplay mechanics
│   │   │   ├── stances-angles.astro         # Throw stances and release angles
│   │   │   ├── progression.astro            # Crafting and skill progression
│   │   │   ├── multiplayer.astro            # Multiplayer setup
│   │   │   ├── admin-commands.astro         # Complete admin command reference
│   │   │   ├── hazards.astro                # Hazard types and strategies
│   │   │   ├── biome-courses.astro          # Biome-specific course characteristics
│   │   │   └── challenge-courses.astro       # Challenge courses and boss holes
│   │   ├── features/
│   │   │   ├── physics.astro                 # Physics system deep-dive
│   │   │   ├── courses.astro                 # Course generation and types
│   │   │   ├── wind-system.astro            # Wind system mechanics
│   │   │   ├── enchantments.astro            # Disc enchantment system
│   │   │   ├── accessories.astro             # Disc bags and accessories
│   │   │   ├── round-rewards.astro          # Survival mode rewards
│   │   │   ├── resort.astro                 # Resort system
│   │   │   └── progression.astro             # Progression systems
│   │   ├── admin/
│   │   │   ├── server-setup.astro           # Enhanced server setup guide
│   │   │   ├── permissions.astro             # Permission system guide
│   │   │   └── course-management.astro       # Course admin deep-dive
│   │   ├── progression/
│   │   │   ├── skill-unlocks.astro          # Skill-based unlock system
│   │   │   ├── questline.astro              # Quest system guide
│   │   │   └── crafting-tree.astro           # Interactive crafting tree
│   │   ├── reference/
│   │   │   ├── physics.astro                # Complete physics reference
│   │   │   ├── config.astro                 # Configuration options
│   │   │   └── api.astro                    # API reference for modders
│   │   ├── community/
│   │   │   ├── showcase.astro               # Player and server showcase
│   │   │   ├── contributing.astro           # Contribution guide
│   │   │   └── faq.astro                    # Comprehensive FAQ
│   │   └── about.astro                      # About the project
│   ├── components/
│   │   ├── layout/
│   │   │   ├── Header.astro
│   │   │   ├── Footer.astro
│   │   │   └── Sidebar.astro                # Quick reference sidebar
│   │   ├── ui/
│   │   │   ├── Hero.astro
│   │   │   ├── FeatureCard.astro
│   │   │   ├── DiscStats.astro
│   │   │   ├── TierComparison.astro
│   │   │   ├── CommandBlock.astro
│   │   │   ├── FlightCalculator.astro       # Interactive disc flight calculator
│   │   │   ├── CoursePlanner.astro          # Visual course layout designer
│   │   │   ├── TierWizard.astro             # Disc tier comparison wizard
│   │   │   ├── ScoreTracker.astro           # Personal score logging
│   │   │   └── CraftingTree.astro           # Interactive crafting tree
│   │   ├── content/
│   │   │   ├── CourseGallery.astro          # User-submitted course screenshots
│   │   │   ├── ScreenshotCarousel.astro     # Feature screenshot gallery
│   │   │   └── VideoEmbed.astro             # Video tutorial component
│   │   └── search/
│   │       ├── SearchBar.astro              # Full-text search
│   │       └── TagFilter.astro              # Tag-based content filtering
│   ├── layouts/
│   │   ├── MainLayout.astro
│   │   ├── GuideLayout.astro
│   │   ├── FeatureLayout.astro
│   │   ├── AdminLayout.astro
│   │   └── ReferenceLayout.astro
│   └── styles/
│       ├── global.css
│       ├── themes/
│       │   ├── dark.css
│       │   └── tier-colors.css
│       └── components/
│           ├── interactive.css
│           └── mobile.css
├── public/
│   ├── images/
│   │   ├── screenshots/                    # In-game screenshots
│   │   ├── textures/                       # Disc/block/GUI textures
│   │   ├── diagrams/                       # Physics diagrams, course layouts
│   │   └── hero/                           # Landing page graphics
│   └── videos/                             # Tutorial videos
└── .github/
    └── workflows/
        └── deploy.yml
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
- **DISC-GOLF-QUESTLINE-PLAN.md** → quest system guide
- **SURVIVAL-ENHANCEMENTS-PLAN.md** → survival mode features
- **DISC-GLIDE-PHYSICS.md** → stance and angle mechanics

### New Content to Create
- **Admin command reference** - Complete `/mcdg` command documentation
- **Hazard strategy guide** - How to play each hazard type
- **Biome course guide** - Biome-specific course characteristics
- **Challenge course guide** - Boss holes and lost courses
- **Enchantment system guide** - Disc enchanting mechanics
- **Accessories guide** - Disc bags and accessory items
- **Round rewards guide** - Survival mode reward system
- **Skill unlock guide** - Achievement-based progression
- **Quest system guide** - Storyline and exploration quests
- **Server admin guides** - Permissions, course management, troubleshooting
- **Physics reference** - Complete physics constants and formulas
- **Configuration guide** - All server and client config options
- **API documentation** - For modders integrating with MCDG
- **FAQ** - Common gameplay and technical questions
- **Community showcase** - Featured servers and builds

### Visual Assets to Reuse
- Disc textures: `src/main/resources/assets/mcdg/textures/item/`
- Block textures: `src/main/resources/assets/mcdg/textures/block/`
- GUI textures: `src/main/resources/assets/mcdg/textures/gui/`
- Item names/descriptions: `src/main/resources/assets/mcdg/lang/en_us.json`
- New in-game screenshots needed for:
  - Hero section and course gallery
  - Feature showcases (stances, angles, hazards)
  - UI element close-ups
  - Before/after comparisons
  - Biome course examples
  - Challenge course highlights

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
- Mobile-responsive design with collapsible navigation
- Touch-friendly interactive tools for mobile users

### Interactive Features
- **Disc Flight Calculator** - Enhanced with wind effects, enchantments, terrain impact
- **Course Planner Tool** - Visual course layout designer with distance/par calculator
- **Tier Comparison Wizard** - Interactive disc tier comparison with upgrade recommendations
- **Score Tracker** - Personal score logging with local storage
- **Crafting Tree** - Interactive crafting progression visualization
- **Course Gallery** - User-submitted course screenshots with ratings
- **Full-text Search** - Search across all guides with tag filtering
- **Contextual Help** - Tooltip definitions and related content suggestions

### Implementation Phases

#### Phase 1: Core Player Experience (High Priority)
1. **Project Setup** — Initialize Astro, Tailwind, sitemap, GitHub Pages config
2. **Design System** — Create global styles, color variables, core UI components
3. **Content Migration** — Convert existing docs to Astro pages (README, USERGUIDE, SERVER-SETUP)
4. **Core Guides** — Enhanced gameplay guide with stances, angles, and hazards
5. **Interactive Calculator** - Disc flight calculator with enchantments
6. **Admin Reference** - Comprehensive admin command reference
7. **Progression Guide** - Tier comparison and crafting progression
8. **Asset Preparation** - Copy textures, optimize images, create hero graphics
9. **Deployment & Testing** — Set up GitHub Actions CI/CD, test build and deploy

#### Phase 2: Advanced Features (Medium Priority)
1. **Challenge Courses Guide** - Boss holes and lost courses documentation
2. **Resort System** - Resort feature page with screenshots
3. **Enchantments Deep-Dive** - Complete enchantment system guide
4. **Accessories Guide** - Disc bags and accessories documentation
5. **Biome Courses** - Biome-specific course characteristics
6. **Course Planner Tool** - Visual course layout designer
7. **Tier Wizard** - Interactive disc tier comparison
8. **Video Tutorials** - Installation and gameplay walkthrough videos

#### Phase 3: Community & Tools (Medium Priority)
1. **Course Gallery** - User-submitted course screenshots
2. **Score Tracker** - Personal score logging tool
3. **FAQ** - Comprehensive troubleshooting FAQ
4. **Community Guide** - Contribution and showcase pages
5. **Search Implementation** - Full-text search with filtering
6. **Mobile Optimization** - Touch-friendly tools and responsive design
7. **Contextual Help** - Tooltips and related content suggestions

#### Phase 4: Technical & Reference (Low Priority)
1. **Physics Reference** - Complete physics constants and formulas
2. **API Documentation** - Modder integration guide
3. **Configuration Guide** - All server and client config options
4. **Architecture Overview** - Internal system documentation
5. **Advanced Admin** - Permissions, course management, security

### Deployment
- Use GitHub Actions to build on push to `main` and deploy to GitHub Pages
- `.github/workflows/deploy.yml` with `actions/upload-pages-artifact` and `actions/deploy-pages`
- Astro config (`docs/astro.config.mjs`) uses `site: 'https://rbrambley.github.io'` and `base: '/mcdg/'` so dev and production URLs resolve correctly
- Automated content sync from markdown docs
- Release notes integration with mod releases

### Verification
- Lighthouse score 90+ on all metrics
- Responsive on mobile, tablet, and desktop
- All navigation and interactive components functional
- Images and assets load correctly
- GitHub Pages URL live and accessible
- Search functionality working across all content
- Interactive tools functioning correctly
- Mobile touch interactions working properly

### Maintenance
- Update download links when new releases ship
- Sync feature pages with new design docs as systems evolve
- Add new screenshots when new visual features are added
- Version-specific guides matching mod releases
- Migration guides between versions
- Automated content sync from markdown documentation
- Feature status dashboard with real-time implementation status
- Changelog integration with release notes

### Notes
- No code changes to the MCDG mod itself are required for the website
- In-game screenshots are the main missing asset; textures can be used as placeholders initially
- Astro, GitHub Pages, and all recommended tools are free for public repositories
- Progressive enhancement approach: core content first, interactive features second
- Community contributions can expand course gallery and FAQ sections over time
