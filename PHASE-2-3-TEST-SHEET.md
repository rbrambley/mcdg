# Phase 2 & 3 Test Sheet

**Purpose:** Comprehensive test coverage for Phase 2 (Enhanced Gameplay Systems) and Phase 3 (Progression Systems) features
**Last Updated:** 2026-06-26
**Status:** Ready for testing

---

## Test Session Information

- Date:
- Tester:
- Branch: phase-3
- Commit:
- Environment:
  - ATLauncher instance: C:\Users\rich\AppData\Roaming\ATLauncher\instances\Minecraft1206withFabric\mods
  - Build jar SHA256:
  - ATLauncher jar SHA256:

---

# Phase 2: Enhanced Gameplay Systems

## 2.1 Wind System

### 2.1.1 Basic Wind Mechanics
- [ ] Verify wind HUD indicator appears on screen during gameplay
- [ ] Check wind direction arrow updates correctly when wind changes
- [ ] Verify wind speed magnitude displays correctly (0-100 scale)
- [ ] Test wind affects disc trajectory during throws (throw with and against wind)
- [ ] Confirm wind gusts occur periodically and affect flight unpredictably
- [ ] Test wind effect varies by disc tier (higher tiers more resistant)

### 2.1.2 Environmental Wind Modifiers
- [ ] Test wind in different biomes (plains, forest, desert, mountains)
- [ ] Verify wind behavior during different weather conditions (clear, rain, thunder)
- [ ] Test wind changes at different times of day (day vs night)
- [ ] Confirm biome-specific wind multipliers apply correctly

### 2.1.3 Wind Administration
- [ ] Use `/mcdg wind set <speed> <direction>` to manually set wind
- [ ] Use `/mcdg wind calm` to set wind to calm (0 speed)
- [ ] Use `/mcdg wind random` to generate random wind conditions
- [ ] Use `/mcdg wind gust` to trigger a wind gust
- [ ] Verify wind changes sync to all connected players

### 2.1.4 Round Lifecycle Wind Automation
- [ ] Start a round and verify wind defaults to CALM
- [ ] Play multiple holes and confirm wind remains consistent throughout the round
- [ ] Complete a round and start a new one - verify wind may change between rounds
- [ ] Use `/mcdg wind set` during a round and verify manual override works
- [ ] Test `RoundWindPolicy` enforcement (wind stays set once set for round)

### 2.1.5 Wind Regression
- [ ] Play a full 9-hole round with varying wind conditions
- [ ] Verify no performance degradation from wind system
- [ ] Confirm wind doesn't cause disc flight calculation errors
- [ ] Test wind with all stances (OVERHAND, BACKHAND, FOREHAND)
- [ ] Test wind with all release angles (HYZER, FLAT, ANHYZER)

---

## 2.2 Expanded Hazards

### 2.2.1 Hazard Types
- [ ] Throw into **Sand** hazard and verify slowed movement
- [ ] Throw into **Rough** (tall grass) and verify movement penalty
- [ ] Throw into **Ice** and verify sliding behavior
- [ ] Throw into **Cactus** and verify damage on contact
- [ ] Throw into **Swamp** and verify difficult lie
- [ ] Throw near **Lava** and verify danger proximity
- [ ] Throw near **Cliff** edges and verify fall risk

### 2.2.2 Hazard Placement
- [ ] Build a course in different biomes and verify appropriate hazards spawn
- [ ] Check hazard density varies by biome profile
- [ ] Verify hazards don't spawn on tee pads or baskets
- [ ] Confirm hazard placement respects course boundaries
- [ ] Test hazard placement with `BiomeHazardProfile` multipliers

### 2.2.3 Hazard Minimap Display
- [ ] Verify hazards appear with correct colors on minimap
- [ ] Check sand hazards show as yellow/orange
- [ ] Check water hazards show as blue
- [ ] Check lava hazards show as red/orange
- [ ] Verify hazard grid updates when moving to new areas

### 2.2.4 Hazard Behavior
- [ ] Test penalty strokes for landing in hazards (strict mode)
- [ ] Verify lie updates correctly after hazard escape
- [ ] Test movement speed modifiers in different hazards
- [ ] Confirm hazard effects reset when leaving hazard area
- [ ] Test hazard interaction with disc durability

### 2.2.5 Cave Course Autobuild
- [ ] Use `/mcdg buildcave` to generate a cave course
- [ ] Verify cave structure includes water tunnels
- [ ] Check tee pads are placed correctly in cave
- [ ] Verify basket markers are visible in cave environment
- [ ] Test cave course lighting is adequate
- [ ] Confirm cave hazards (water, stalactites) spawn appropriately
- [ ] Play a full cave course and verify playability

### 2.2.6 Hazard Regression
- [ ] Play courses in multiple biomes with different hazard profiles
- [ ] Verify no crashes when entering/exiting hazards
- [ ] Test hazard interaction with wind system
- [ ] Confirm hazard penalties apply correctly in multiplayer
- [ ] Check hazard minimap colors don't conflict with other overlays

---

## 2.3 Custom Throw Animations (Placeholder)

### 2.3.1 UseAction Mappings
- [ ] Throw with OVERHAND stance and verify SPEAR animation plays
- [ ] Throw with BACKHAND stance and verify CROSSBOW animation plays
- [ ] Throw with FOREHAND stance and verify BOW animation plays
- [ ] Cycle through stances with R key and verify animation changes
- [ ] Confirm no crashes or animation glitches

### 2.3.2 Placeholder Limitations
- [ ] Note: Event-based arm animations are deferred to future polish
- [ ] Verify current placeholder provides adequate visual feedback
- [ ] Confirm placeholder doesn't interfere with gameplay

---

# Phase 3: Progression Systems

## 3.1 Disc Crafting Progression (Core)

### 3.1.1 Tiered Discs
- [ ] Craft **Training Disc** (8 arrows + copper ingot) - verify permanent, no durability
- [ ] Craft **Wooden Disc** - verify basic stats and durability
- [ ] Craft **Stone Disc** - verify improved stats over wooden
- [ ] Craft **Iron Disc** - verify improved stats over stone
- [ ] Craft **Gold Disc** - verify improved stats over iron
- [ ] Craft **Diamond Disc** - verify improved stats over gold
- [ ] Craft **Netherite Disc** - verify best stats in game
- [ ] Verify each tier has unique flight characteristics (speed, glide, turn, fade)
- [ ] Test durability loss on throws for all tiers except Training
- [ ] Confirm discs break when durability reaches 0

### 3.1.2 Disc Flight Stats
- [ ] Check disc tooltips for flight numbers (speed, glide, turn, fade)
- [ ] Verify higher tiers have better flight ratings
- [ ] Test actual flight matches advertised stats
- [ ] Confirm flight stats integrate with TrajectoryCalculator
- [ ] Test disc stats with different stances and release angles

### 3.1.3 Crafting Recipes
- [ ] Verify all tiered disc recipes are available in crafting table
- [ ] Check recipe ingredients are appropriate for each tier
- [ ] Test recipe unlocks are correct (no premature unlocks)
- [ ] Confirm recipes work with EMI recipe viewer
- [ ] Verify recipe output is correct quantity (1 disc per craft)

### 3.1.4 Scorecard Souvenirs
- [ ] Complete a round and verify scorecard item is received
- [ ] Check scorecard displays round stats (score, par, aces)
- [ ] Verify scorecard can be placed as item frame decoration
- [ ] Test scorecard persistence across server restarts
- [ ] Confirm scorecard doesn't interfere with gameplay

### 3.1.5 Disc Physics Integration
- [ ] Throw each disc tier and verify speed differences
- [ ] Test glide differences between tiers
- [ ] Verify turn and fade characteristics match tier
- [ ] Confirm higher tiers throw farther with same power
- [ ] Test disc stats with enchantments
- [ ] Verify disc stats with wind system

---

## 3.1 Disc Crafting Progression (Follow-Up)

### 3.1.1 Disc Bag
- [ ] Craft **Disc Bag** (leather + string + redstone)
- [ ] Right-click bag while holding - verify 12-slot GUI opens
- [ ] Place discs of different tiers into bag slots
- [ ] Verify non-disc items (dirt, sword) cannot be inserted
- [ ] Shift-click discs from inventory into bag
- [ ] Shift-click discs from bag to inventory
- [ ] Close and reopen bag - verify contents persist
- [ ] Move bag to different inventory slot while GUI open - verify it closes
- [ ] Place bag in chest, retrieve, verify contents persist
- [ ] Test bag with full inventory - verify items drop if no space
- [ ] Use `/mcdg bag` command to open bag without holding it

### 3.1.2 Accessories

#### Disc Golf Glove
- [ ] Craft **Disc Golf Glove** (leather + green dye)
- [ ] Hold glove and verify tooltip shows effect
- [ ] Keep glove in inventory and throw disc - verify reduced fade
- [ ] Test glove with different disc tiers
- [ ] Verify glove effect stacks with enchantments
- [ ] Confirm glove doesn't break or have durability

#### Disc Towel
- [ ] Craft **Disc Towel** (white wool + string)
- [ ] Hold towel and verify tooltip shows effect
- [ ] Keep towel in inventory and throw multiple times - verify occasional durability preservation
- [ ] Test towel with different disc tiers
- [ ] Verify towel effect is random (not every throw)
- [ ] Confirm towel doesn't break or have durability

#### Range Finder
- [ ] Craft **Range Finder** (glass pane + iron ingot + copper ingot + redstone)
- [ ] Hold range finder and verify tooltip shows effect
- [ ] Keep range finder in inventory while holding disc - confirm no crash
- [ ] Note: HUD distance feedback is reserved for future implementation
- [ ] Verify range finder doesn't interfere with other systems

### 3.1.3 Skill Unlocks

#### Skill System Basics
- [ ] Use `/mcdg skills` command - verify skills list displays
- [ ] Check locked skills show in red with requirement descriptions
- [ ] Check unlocked skills show in green
- [ ] Verify skill progress bars display correctly
- [ ] Test `/mcdg skills gui` command - verify GUI opens

#### Wind Reading Skill
- [ ] Throw 10+ discs - verify progress toward Wind Reading
- [ ] Throw 500+ discs (or use admin debug) - confirm Wind Reading unlocks
- [ ] Verify Wind Reading reduces wind effect on disc flight
- [ ] Test wind reading with different wind speeds
- [ ] Confirm skill persists after logout/login

#### Release Control Skill
- [ ] Complete a full round - verify progress toward Release Control
- [ ] Complete 10+ rounds - confirm Release Control unlocks
- [ ] Test Release Control with HYZER/ANHYZER throws - verify reduced angle penalty
- [ ] Verify skill works with all stances
- [ ] Confirm skill persists after logout/login

#### Disc Mastery Skill
- [ ] Throw with Training Disc - verify progress tracking
- [ ] Throw with Wooden, Stone, Iron, Gold, Diamond, Netherite discs
- [ ] Confirm Disc Mastery unlocks after throwing all tiers
- [ ] Verify Disc Mastery boosts all tier stats slightly
- [ ] Test with different disc combinations
- [ ] Confirm skill persists after logout/login

#### Power Control Skill
- [ ] Earn XP through throws and round completion
- [ ] Verify progress toward Power Control displays
- [ ] Earn 100+ XP - confirm Power Control unlocks
- [ ] Test Power Control - verify slightly higher throw velocity
- [ ] Verify skill works with overcharge
- [ ] Confirm skill persists after logout/login

#### Focus Skill
- [ ] Earn additional XP - verify progress toward Focus
- [ ] Earn sufficient XP - confirm Focus unlocks
- [ ] Test Focus - verify reduced stamina (hunger) exhaustion
- [ ] Verify skill works with overcharge
- [ ] Confirm skill persists after logout/login

### 3.1.4 Skill Data Persistence
- [ ] Complete skill progress tasks
- [ ] Log out of server
- [ ] Check `mcdg-player-skills.json` in world folder
- [ ] Log back in - verify skill progress restored
- [ ] Test with multiple players - verify separate skill data per player
- [ ] Delete skill file and verify reset to default

### 3.1.5 Integration Testing
- [ ] Test skills with all disc tiers
- [ ] Test skills with all accessories
- [ ] Test skills with enchantments
- [ ] Test skills with wind system
- [ ] Test skills with hazards
- [ ] Verify no conflicts between skill effects

---

## 3.2 Custom Disc Stats (Not Implemented)

**Status:** Planning phase - not yet implemented
**Action:** Skip testing for this feature

---

## 3.3 Challenge Courses

**Status:** Partially implemented - discovery-based system with debug/admin commands.

**Discovery flow:**
- Challenge course entrances are generated in the world as a mossy cobblestone marker with a hidden chest containing a `Map Fragment`.
- Interact with the chest or break the marker to discover the course.
- Entrances are placed 500–2000 blocks from world spawn.
- On server restart, lost course entrance data is persisted so undiscovered courses can still be discovered.

**Player commands:**
- `/mcdg menu challenge` - View discovered challenge courses.
- `/mcdg startchallenge <courseId>` - Build and start a discovered challenge course.

**Admin/debug commands (for testing):**
- `/mcdg debug placetestlostcourse` - Place a lost course entrance at your position.
- `/mcdg debug lostcourses` - List all placed lost course entrances.
- `/mcdg debug discovercourse <courseId>` - Discover a specific course by ID.

### 3.3.1 Challenge Course Generation
- [ ] Use `/mcdg debug placetestlostcourse` to create a test lost course entrance
- [ ] Verify the generated course has parameter-based characteristics (distance, hazard density, etc.) via its type
- [ ] Check challenge course catalog updates when a course is discovered
- [ ] Verify different challenge types exist (Lost Course, Boss Hole, Time Trial, Accuracy Challenge, Distance Challenge)
- [ ] Verify challenge courses use stricter parameters than standard courses

### 3.3.2 Challenge Course Features
- [ ] Verify challenge courses are playable and fair
- [ ] Confirm challenge courses integrate with the scoring system
- [ ] Verify a placed challenge course can be started and completed
- [ ] (Future) Unique visual theming and special hazards are not yet implemented

### 3.3.3 Challenge Course Rewards
- [ ] Complete a challenge course and verify rewards are given
- [ ] Verify under-par performance grants bonus diamonds
- [ ] Verify a single-hole ace can grant an enchanted disc reward
- [ ] (Future) Unique souvenirs and reward scaling by difficulty are not yet implemented

### 3.3.4 Challenge Course Catalog
- [ ] Use `/mcdg menu challenge` to view the catalog of discovered courses
- [ ] Verify catalog shows available challenge courses
- [ ] Verify catalog updates when a new course is discovered
- [ ] (Future) Catalog difficulty ratings and filtering/sorting are not yet implemented

### 3.3.5 Challenge Course Regression
- [ ] Play multiple challenge courses in sequence
- [ ] Verify no performance issues with challenge generation
- [ ] Test challenge courses in multiplayer
- [ ] Confirm challenge courses don't interfere with standard courses
- [ ] Check challenge course persistence across server restarts (catalog and lost course data)

---

# Cross-Phase Integration Tests

## Multi-System Integration
- [ ] Play a full round with wind, hazards, tiered discs, accessories, and skills active
- [ ] Verify all systems work together without conflicts
- [ ] Test performance with all Phase 2 & 3 features enabled
- [ ] Check for memory leaks or performance degradation
- [ ] Verify no crashes or error logs with full feature set

## Multiplayer Testing
- [ ] Test all Phase 2 & 3 features with 2+ players
- [ ] Verify wind syncs correctly to all players
- [ ] Check skill progress is per-player (not shared)
- [ ] Test disc bag and accessories in multiplayer
- [ ] Verify challenge courses work for all players
- [ ] Confirm no desync issues in multiplayer

## Regression Testing
- [ ] Run `./gradlew quickRegression` - verify all tests pass
- [ ] Run `./gradlew smokeRegression` - verify smoke tests pass
- [ ] Run `./gradlew test` - verify all unit tests pass
- [ ] Run `./gradlew pmdMain` - verify no new static analysis warnings
- [ ] Play a full 9-hole standard course - verify no regressions
- [ ] Play a full cave course - verify no regressions
- [ ] Play a full challenge course - verify no regressions

## Performance Testing
- [ ] Monitor server TPS with all features active
- [ ] Check client FPS with all features active
- [ ] Verify no lag spikes during wind gusts
- [ ] Test performance with multiple players
- [ ] Check memory usage over extended play session
- [ ] Verify no performance degradation over time

---

# Known Issues & Limitations

## Phase 2
- Custom throw animations are placeholder only (event-based arm animations deferred)
- Tournament wind modes not implemented (post-release feature)

## Phase 3
- Custom disc stats not yet implemented (planning phase)
- Range Finder HUD distance feedback reserved for future implementation
- Skill GUI may need visual polish before release

---

# Exit Criteria

Phase 2 & 3 testing is complete when:
- [ ] All Phase 2.1 (Wind System) tests pass
- [ ] All Phase 2.2 (Expanded Hazards) tests pass
- [ ] All Phase 2.3 (Custom Throw Animations - placeholder) tests pass
- [ ] All Phase 3.1 (Disc Crafting Progression) tests pass
- [ ] All Phase 3.3 (Challenge Courses) tests pass
- [ ] All cross-phase integration tests pass
- [ ] All regression tests pass (quickRegression, smokeRegression, test)
- [ ] No high-severity bugs remain
- [ ] Performance is acceptable with all features enabled
- [ ] Multiplayer functionality verified
- [ ] Documentation updated with any discovered issues

---

# Test Notes

Use this section for general observations, issues found, or areas needing additional testing:

- 
- 
- 
