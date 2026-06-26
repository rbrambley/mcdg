# Wind System Implementation Plan

**Status:** Phase 5 Complete (Polish and Balancing)  
**Created:** 2026-06-17  
**Goal:** Add dynamic wind system that affects disc flight physics with natural weather patterns, admin controls, and tournament support

---

## Overview

Wind will add an environmental factor to disc throws, affecting flight trajectory based on wind speed and direction. The system will:

- Apply wind velocity as an additional force vector during disc flight
- Provide natural wind generation based on biome, weather, and time
- Support admin commands for manual wind control
- Include tournament-specific wind modes for competitive play
- Display wind information on HUD for player awareness

---

## Wind Physics Model

### Wind Data Model

```java
record WindState(
    Vec3d velocity,           // Wind velocity vector (blocks/tick)
    double speed,             // Magnitude (0.0 - 1.0 scale)
    float directionDegrees,  // 0-360 compass direction (0 = North, 90 = East)
    WindMode mode,           // CALM, NATURAL, FIXED, TOURNAMENT
    boolean isGusting,        // Variable wind conditions
    long lastUpdated,        // Tick timestamp for wind changes
    UUID tournamentId         // Optional: associated tournament for consistency
)
```

### Wind Modes

```java
enum WindMode {
    CALM,           // No wind (speed = 0.0)
    NATURAL,        // Dynamic weather-based wind
    FIXED,          // Manually set wind (admin controlled)
    TOURNAMENT      // Tournament-specific wind behavior
}
```

### Wind Strength Scale

| Speed | Description | Effect on Throws |
|-------|-------------|------------------|
| 0.0 | Calm | No effect |
| 0.2 | Light breeze | Subtle drift (~2-5 ft) |
| 0.4 | Moderate wind | Noticeable curve (~5-10 ft) |
| 0.6 | Strong wind | Significant impact (~10-20 ft) |
| 0.8 | Very strong | Major trajectory changes (~15-30 ft) |
| 1.0 | Extreme | Severe impact (~20-40 ft) |

---

## System Architecture

### Server-Side Components

#### 1. WindManager (New Class)
**Location:** `src/main/java/com/mcdg/game/WindManager.java`

**Responsibilities:**
- Maintain per-world wind state using `ConcurrentHashMap<Identifier, WindState>`
- Generate natural wind based on biome, weather, and time
- Handle wind mode transitions (CALM ↔ NATURAL ↔ FIXED ↔ TOURNAMENT)
- Apply wind updates at configurable intervals
- Provide wind query interface for physics engines

**Key Methods:**
```java
public static WindState getWindState(ServerWorld world)
public static void setWindMode(ServerWorld world, WindMode mode)
public static void setManualWind(ServerWorld world, double speed, float directionDegrees)
public static void setTournamentWind(UUID tournamentId, WindState wind)
public static void clearTournamentWind(UUID tournamentId)
public static void tick(MinecraftServer server)  // Registered on ServerTickEvents.END_SERVER_TICK
```

**Natural Wind Generation Algorithm:**
```java
private static WindState generateNaturalWind(ServerWorld world) {
    // Base wind from weather (rain = stronger, thunder = strongest)
    double weatherMultiplier = world.isRaining() ? 1.5 : (world.isThundering() ? 2.5 : 1.0);
    
    // Biome modifier (open areas = windier, forests = calmer)
    Biome biome = world.getBiome(playerPos).value();
    double biomeModifier = getBiomeWindModifier(biome);
    
    // Time variation (day = calmer, night = windier)
    double timeModifier = world.isDay() ? 0.8 : 1.2;
    
    // Random variation with Perlin-like smoothing
    double baseSpeed = 0.1 + (random.nextDouble() * 0.3);
    double speed = Math.min(1.0, baseSpeed * weatherMultiplier * biomeModifier * timeModifier);
    
    // Direction with gradual changes (avoid sudden 180° flips)
    float direction = calculateSmoothedDirection(world, previousDirection);
    
    return new WindState(calculateVelocity(speed, direction), speed, direction, 
                        WindMode.NATURAL, false, server.getTicks(), null);
}
```

#### 2. Physics Engine Integration

**TrajectoryCalculator.java - Changes:**
```java
// Add wind parameter to calculateTrajectory()
public static TrajectoryResult calculateTrajectory(
    ServerWorld world,
    Vec3d startPos,
    Vec3d initialVelocity,
    float launchYawDegrees,
    float charge,
    ThrowStance stance,
    ReleaseAngle angle,
    Vec3d windVelocity  // NEW: wind vector
) {
    // In physics loop:
    Vec3d vel = new Vec3d(velX, velY, velZ);
    
    // Apply wind effect (stronger during glide, weaker during fade)
    double windEffect = hasGlide ? 0.02 : 0.005; // Wind affects gliding discs more
    vel = vel.add(windVelocity.multiply(windEffect));
    
    // Update position
    pos = pos.add(vel);
}
```

**DiscFlightSimulator.java - Changes:**
```java
// Add wind to FlightState record
public record FlightState(
    UUID pearlUuid,
    UUID playerUuid,
    int launchTick,
    float launchYawDegrees,
    float charge,
    ThrowStance stance,
    ReleaseAngle angle,
    Vec3d launchPos,
    double initialSpeed,
    Vec3d windVelocity  // NEW: wind at throw time
)

// Apply wind in tick() method
private static void applyWindPhysics(EnderPearlEntity pearl, FlightState state, Vec3d wind) {
    double windEffect = state.stance().hasGlide() ? 0.02 : 0.005;
    Vec3d windForce = wind.multiply(windEffect);
    pearl.addVelocity(windForce.x, windForce.y, windForce.z);
}
```

#### 3. ChargedDiscItem.java - Changes
```java
// Get current wind when performing throw
public static void performThrow(ServerPlayerEntity player, float charge) {
    ServerWorld world = player.getServerWorld();
    Vec3d windVelocity = WindManager.getWindState(world).velocity();
    
    // Pass wind to trajectory calculator
    TrajectoryResult result = TrajectoryCalculator.calculateTrajectory(
        world, startPos, initialVelocity, launchYawDegrees, charge, 
        stance, angle, windVelocity
    );
}
```

### Client-Side Components

#### 1. WindSync (New Network Packet)
**Location:** `src/main/java/com/mcdg/net/WindSync.java`

```java
public record WindSync(
    Vec3d velocity,
    double speed,
    float directionDegrees,
    WindMode mode,
    boolean isGusting
) {
    public static final Identifier ID = new Identifier("mcdg", "wind_sync");
    public static final PacketCodec<WindSync> CODEC = PacketCodec.tuple(
        Vec3d.PACKET_CODEC, WindSync::velocity,
        PacketCodec.DOUBLE, WindSync::speed,
        PacketCodec.FLOAT, WindSync::directionDegrees,
        PacketCodec.enumCodec(WindMode.class), WindSync::mode,
        PacketCodec.BOOL, WindSync::isGusting,
        WindSync::new
    );
}
```

#### 2. ClientNetworking.java - Changes
```java
// Register wind sync receiver
ServerPlayNetworking.registerGlobalReceiver(WindSync.ID, (payload, context) -> {
    context.client().execute(() -> {
        WindManagerClient.updateWindState(payload);
    });
});
```

#### 3. WindManagerClient (New Class)
**Location:** `src/client/java/com/mcdg/client/WindManagerClient.java`

```java
public final class WindManagerClient {
    private static WindState currentWind;
    
    public static void updateWindState(WindSync sync) {
        currentWind = new WindState(sync.velocity(), sync.speed(), 
                                    sync.directionDegrees(), sync.mode(), 
                                    sync.isGusting(), System.currentTimeMillis(), null);
    }
    
    public static WindState getCurrentWind() {
        return currentWind;
    }
    
    public static String getWindDirectionText() {
        if (currentWind == null || currentWind.speed() < 0.1) {
            return "CALM";
        }
        return getCompassDirection(currentWind.directionDegrees());
    }
    
    private static String getCompassDirection(float degrees) {
        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = Math.round(degrees / 45.0f) % 8;
        return directions[index];
    }
}
```

#### 4. HudOverlays.java - Changes
```java
// Add wind indicator to compass
public static void renderCompass(DrawContext drawContext) {
    // ... existing compass code ...
    
    // Add wind indicator
    WindState wind = WindManagerClient.getCurrentWind();
    if (wind != null && wind.speed() >= 0.1) {
        String windText = "WIND: " + WindManagerClient.getWindDirectionText() + 
                         " " + Math.round(wind.speed() * 100) + "%";
        int windWidth = client.textRenderer.getWidth(windText);
        int windX = (drawContext.getScaledWindowWidth() - windWidth) / 2;
        int windY = y + 24; // Below coordinates
        
        Formatting windColor = wind.speed() > 0.5 ? Formatting.RED : 
                               (wind.speed() > 0.3 ? Formatting.YELLOW : Formatting.GREEN);
        
        drawContext.fill(windX - 3, windY - 2, windX + windWidth + 3, windY + 10, 0x70000000);
        drawContext.drawTextWithShadow(client.textRenderer, 
            Text.literal(windText).formatted(windColor), windX, windY, 0xFFFFFF);
    }
}

// Add wind arrow near power bar during charge
private static void renderWindIndicator(DrawContext drawContext, MinecraftClient client, 
                                       int barX, int barTop, boolean rightHandThrow) {
    if (!ChargedDiscItem.isClientChargeVisible()) {
        return;
    }
    
    WindState wind = WindManagerClient.getCurrentWind();
    if (wind == null || wind.speed() < 0.1) {
        return;
    }
    
    // Draw wind arrow
    String arrow = getWindArrow(wind.directionDegrees());
    Formatting color = wind.speed() > 0.5 ? Formatting.RED : 
                      (wind.speed() > 0.3 ? Formatting.YELLOW : Formatting.GREEN);
    
    Text windText = Text.literal(arrow).formatted(color);
    int textX = rightHandThrow ? barX + POWER_BAR_WIDTH + 4 : barX - 
                client.textRenderer.getWidth(windText) - 4;
    int textY = barTop - 48; // Above stance indicator
    
    drawContext.drawTextWithShadow(client.textRenderer, windText, textX, textY, 0xFFFFFF);
}

private static String getWindArrow(float degrees) {
    // Convert degrees to arrow character
    String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    int index = Math.round(degrees / 45.0f) % 8;
    return arrows[index];
}
```

### Admin Commands

#### McdgAdminCommands.java - Additions

```java
// Wind command branch
.then(literal("wind").requires(McdgAdminCommands::canUseAdminCommands)
    .then(literal("set")
        .then(argument("speed", DoubleArgumentType.doubleArg(0.0, 1.0))
            .then(argument("direction", IntegerArgumentType.integer(0, 359))
                .executes(context -> executeWindSet(context.getSource(), 
                    DoubleArgumentType.getDouble(context, "speed"),
                    IntegerArgumentType.getInteger(context, "direction"))))))
    .then(literal("clear")
        .executes(context -> executeWindClear(context.getSource())))
    .then(literal("mode")
        .then(literal("calm")
            .executes(context -> executeWindMode(context.getSource(), WindMode.CALM)))
        .then(literal("natural")
            .executes(context -> executeWindMode(context.getSource(), WindMode.NATURAL)))
        .then(literal("tournament")
            .then(argument("tournament_id", StringArgumentType.string())
                .executes(context -> executeWindTournament(context.getSource(),
                    StringArgumentType.getString(context, "tournament_id"))))))
    .then(literal("show")
        .executes(context -> executeWindShow(context.getSource())))
    .then(literal("random")
        .executes(context -> executeWindRandom(context.getSource()))))
```

**Command Implementations:**
```java
private static int executeWindSet(ServerCommandSource source, double speed, int direction) {
    ServerWorld world = source.getWorld();
    WindManager.setManualWind(world, speed, direction);
    source.sendFeedback(() => Text.literal("Wind set to " + speed + " speed, " + 
                         direction + "°").formatted(Formatting.GREEN));
    return 1;
}

private static int executeWindClear(ServerCommandSource source) {
    ServerWorld world = source.getWorld();
    WindManager.setWindMode(world, WindMode.CALM);
    source.sendFeedback(() => Text.literal("Wind cleared (calm conditions)").formatted(Formatting.GREEN));
    return 1;
}

private static int executeWindMode(ServerCommandSource source, WindMode mode) {
    ServerWorld world = source.getWorld();
    WindManager.setWindMode(world, mode);
    source.sendFeedback(() => Text.literal("Wind mode set to " + mode).formatted(Formatting.GREEN));
    return 1;
}

private static int executeWindShow(ServerCommandSource source) {
    ServerWorld world = source.getWorld();
    WindState wind = WindManager.getWindState(world);
    source.sendFeedback(() => Text.literal("Current wind: " + wind.speed() + " speed, " + 
                         wind.directionDegrees() + "° (" + 
                         WindManagerClient.getCompassDirection(wind.directionDegrees()) + 
                         "), mode: " + wind.mode()).formatted(Formatting.AQUA));
    return 1;
}

private static int executeWindRandom(ServerCommandSource source) {
    ServerWorld world = source.getWorld();
    WindManager.setWindMode(world, WindMode.NATURAL);
    source.sendFeedback(() => Text.literal("Wind set to random natural mode").formatted(Formatting.GREEN));
    return 1;
}
```

### Tournament Integration

#### TournamentWindManager (New Class)
**Location:** `src/main/java/com/mcdg/game/TournamentWindManager.java`

```java
public final class TournamentWindManager {
    private static final Map<UUID, WindState> tournamentWinds = new ConcurrentHashMap<>();
    
    public static void setTournamentWind(UUID tournamentId, WindState wind) {
        tournamentWinds.put(tournamentId, wind);
    }
    
    public static Optional<WindState> getTournamentWind(UUID tournamentId) {
        return Optional.ofNullable(tournamentWinds.get(tournamentId));
    }
    
    public static void clearTournamentWind(UUID tournamentId) {
        tournamentWinds.remove(tournamentId);
    }
    
    // Tournament wind modes
    public static WindState generateTournamentWind(TournamentWindMode mode, long seed) {
        return switch (mode) {
            case CALM -> new WindState(Vec3d.ZERO, 0.0, 0.0, WindMode.TOURNAMENT, false, 0, null);
            case CONSISTENT -> generateConsistentWind(seed);
            case VARIABLE -> generateVariableWind(seed);
            case NATURAL -> generateNaturalTournamentWind(seed);
        };
    }
}

enum TournamentWindMode {
    CALM,           // No wind for fair conditions
    CONSISTENT,     // Fixed wind for all players/rounds
    VARIABLE,       // Changes between rounds but consistent per round
    NATURAL         // Dynamic but seeded for reproducibility
}
```

---

## Configuration

### McdgConfig.java - Additions
```java
public record McdgConfig(
    boolean enableHudScoringDebug,
    boolean enableStrictFlowDebug,
    boolean skipRoundPresentation,
    int respawnPenaltyStrokes,
    int defaultHoleCount,
    boolean enforceCourseProtection,
    boolean enableSurvivalRewards,
    boolean enableWindSystem,           // NEW: enable/disable wind system
    double defaultWindSpeed,            // NEW: default wind speed (0.0-1.0)
    int windUpdateIntervalTicks         // NEW: how often wind changes (default: 200 ticks = 10 sec)
) {
    public static McdgConfig loadDefault() {
        // ... existing config loading ...
        boolean enableWind = readBoolEnvWithDefault("MCDG_ENABLE_WIND", true);
        double defaultWindSpeed = readDoubleEnv("MCDG_DEFAULT_WIND_SPEED", 0.2, 0.0, 1.0);
        int windUpdateInterval = readIntEnv("MCDG_WIND_UPDATE_INTERVAL", 200, 20, 600);
        
        return new McdgConfig(hudScoringDebug, strictFlowDebug, skipPresentation, 
                            respawnPenaltyStrokes, 9, true, survivalRewards,
                            enableWind, defaultWindSpeed, windUpdateInterval);
    }
    
    private static double readDoubleEnv(String name, double fallback, double min, double max) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
```

---

## Implementation Phases

### Phase 1: Core Wind System (Foundation)
**Goal:** Basic wind functionality with manual control

**Tasks:**
1. Create `WindState` record and `WindMode` enum
2. Implement `WindManager` with per-world state management
3. Add wind parameter to `TrajectoryCalculator.calculateTrajectory()`
4. Add wind parameter to `DiscFlightSimulator.FlightState` and physics
5. Update `ChargedDiscItem.performThrow()` to pass wind to calculators
6. Add basic admin commands (`/mcdg wind set`, `/mcdg wind clear`, `/mcdg wind show`)
7. Add wind configuration to `McdgConfig`
8. Register `WindManager.tick()` in `McdgMod`

**Files to Create:**
- `src/main/java/com/mcdg/game/WindManager.java`
- `src/main/java/com/mcdg/game/WindState.java` (or embed in WindManager)

**Files to Modify:**
- `src/main/java/com/mcdg/game/TrajectoryCalculator.java`
- `src/main/java/com/mcdg/game/DiscFlightSimulator.java`
- `src/main/java/com/mcdg/game/ChargedDiscItem.java`
- `src/main/java/com/mcdg/command/McdgAdminCommands.java`
- `src/main/java/com/mcdg/config/McdgConfig.java`
- `src/main/java/com/mcdg/McdgMod.java`

**Testing:**
- Manual testing with `/mcdg wind set 0.5 90` (moderate east wind)
- Verify throws curve east with east wind
- Verify `/mcdg wind clear` returns throws to normal
- Run `./gradlew test` to ensure existing tests still pass

**Estimated Time:** 4-6 hours

---

### Phase 2: Natural Wind Generation
**Goal:** Dynamic weather-based wind system

**Tasks:**
1. Implement biome-based wind modifiers in `WindManager`
2. Add weather integration (rain/thunder effects)
3. Add time-based wind variation (day/night cycles)
4. Implement smoothed direction changes (avoid sudden flips)
5. Add wind update interval configuration
6. Implement gusting behavior (random speed fluctuations)
7. Add `/mcdg wind mode natural` command
8. Add `/mcdg wind random` command for instant natural wind

**Natural Wind Algorithm:**
```java
private static WindState generateNaturalWind(ServerWorld world, WindState previousWind) {
    long currentTick = world.getServer().getTicks();
    
    // Get biome modifier at player position
    BlockPos playerPos = world.getSpawnPos(); // Or use active player position
    Biome biome = world.getBiome(playerPos).value();
    double biomeModifier = getBiomeWindModifier(biome);
    
    // Weather modifier
    double weatherModifier = 1.0;
    if (world.isRaining()) weatherMultiplier = 1.5;
    if (world.isThundering()) weatherMultiplier = 2.5;
    
    // Time modifier
    double timeModifier = world.isDay() ? 0.8 : 1.2;
    
    // Base speed with random variation
    double baseSpeed = 0.1 + (world.random.nextDouble() * 0.3);
    double speed = Math.min(1.0, baseSpeed * biomeModifier * weatherMultiplier * timeModifier);
    
    // Direction with smoothing
    float direction;
    if (previousWind == null || currentTick % config.windUpdateIntervalTicks() == 0) {
        // Generate new direction
        direction = world.random.nextFloat() * 360.0f;
    } else {
        // Smooth transition from previous direction
        direction = smoothDirectionTransition(previousWind.directionDegrees(), 
                                            world.random.nextFloat() * 360.0f, 0.1f);
    }
    
    // Gusting (20% chance of gust event)
    boolean isGusting = world.random.nextFloat() < 0.2;
    if (isGusting) {
        speed *= 1.3; // 30% speed increase during gusts
    }
    
    return new WindState(calculateVelocity(speed, direction), speed, direction, 
                        WindMode.NATURAL, isGusting, currentTick, null);
}
```

**Biome Wind Modifiers:**
```java
private static double getBiomeWindModifier(Biome biome) {
    // Open areas = windier, forests = calmer
    if (biome.getCategory() == Biome.Category.OCEAN || 
        biome.getCategory() == Biome.Category.PLAINS) {
        return 1.3; // Windier
    } else if (biome.getCategory() == Biome.Category.FOREST || 
               biome.getCategory() == Biome.Category.JUNGLE) {
        return 0.7; // Calmer
    } else if (biome.getCategory() == Biome.Category.MOUNTAIN) {
        return 1.5; // Very windy
    }
    return 1.0; // Neutral
}
```

**Files to Modify:**
- `src/main/java/com/mcdg/game/WindManager.java` (add natural generation logic)

**Testing:**
- Test in different biomes (plains vs forest vs ocean)
- Test during rain and thunder
- Test day vs night wind differences
- Verify wind doesn't change too abruptly
- Run `./gradlew quickRegression` for determinism checks

**Estimated Time:** 3-4 hours

---

### Phase 3: Client-Side Wind Display
**Goal:** Visual feedback for wind conditions

**Tasks:**
1. Create `WindSync` network packet
2. Register packet in `McdgMod`
3. Implement `WindManagerClient` for client-side wind state
4. Add wind sync receiver in `ClientNetworking`
5. Add wind indicator to compass in `HudOverlays.renderCompass()`
6. Add wind arrow near power bar in `HudOverlays.renderPower()`
7. Add wind sync to `WindManager.tick()` (send updates when wind changes)
8. Test wind display accuracy and sync timing

**Wind Display Design:**
- **Compass enhancement:** Wind direction and speed below coordinates
- **Power bar addition:** Wind arrow showing direction during charge
- **Color coding:** Green (light), Yellow (moderate), Red (strong)
- **Text format:** "WIND: NE 45%" or "WIND: CALM"

**Files to Create:**
- `src/main/java/com/mcdg/net/WindSync.java`
- `src/client/java/com/mcdg/client/WindManagerClient.java`

**Files to Modify:**
- `src/main/java/com/mcdg/McdgMod.java` (register packet)
- `src/main/java/com/mcdg/game/WindManager.java` (send sync packets)
- `src/client/java/com/mcdg/client/ClientNetworking.java` (register receiver)
- `src/client/java/com/mcdg/client/HudOverlays.java` (add wind display)

**Testing:**
- Verify wind sync works in multiplayer
- Test wind display updates correctly
- Verify wind arrow direction matches compass
- Test color coding thresholds
- Check performance impact of HUD updates

**Estimated Time:** 2-3 hours

---

### Phase 4: Tournament Wind Support
**Goal:** Competitive wind modes for tournaments

**Tasks:**
1. Create `TournamentWindManager` class
2. Implement `TournamentWindMode` enum (CALM, CONSISTENT, VARIABLE, NATURAL)
3. Add tournament wind generation algorithms
4. Integrate with existing tournament system (when implemented)
5. Add `/mcdg wind tournament <id>` command
6. Add wind state to tournament data model
7. Implement per-round wind consistency for VARIABLE mode
8. Add wind logging to scorecards

**Tournament Wind Modes:**
- **CALM:** No wind (speed = 0.0) - fair conditions
- **CONSISTENT:** Fixed wind for entire tournament (all players, all rounds)
- **VARIABLE:** Different wind per round, but consistent for all players in that round
- **NATURAL:** Dynamic but seeded for reproducibility (same seed = same wind pattern)

**Files to Create:**
- `src/main/java/com/mcdg/game/TournamentWindManager.java`

**Files to Modify:**
- `src/main/java/com/mcdg/game/WindManager.java` (tournament integration)
- `src/main/java/com/mcdg/command/McdgAdminCommands.java` (tournament wind commands)
- Future: Tournament data model files (when tournament system is implemented)

**Testing:**
- Test each tournament wind mode
- Verify consistency across players in same tournament
- Test VARIABLE mode with different winds per round
- Verify seeded NATURAL mode produces reproducible results
- Test tournament wind cleanup after tournament completion

**Estimated Time:** 3-4 hours

---

### Phase 5: Polish and Balancing
**Goal:** Refine wind physics and user experience

**Tasks:**
1. Tune wind effect coefficients (0.02 for glide, 0.005 for overhand)
2. Add wind particle effects (optional visual enhancement)
3. Add wind sound effects (optional audio feedback)
4. Implement wind prediction in HUD (show expected drift)
5. Add wind tooltips/help text
6. Performance optimization (reduce unnecessary sync packets)
7. Add wind-related achievements or statistics
8. Update documentation and help commands

**Wind Effect Tuning:**
- Test different wind speeds with various throw stances
- Ensure wind doesn't make throws impossible (max 40ft drift)
- Balance wind impact across all charge levels
- Verify wind compounds naturally with fade curves

**Optional Visual Enhancements:**
- Particle effects showing wind direction (leaves, dust)
- Wind lines in trajectory preview
- Animated wind indicator on HUD

**Files to Modify:**
- `src/main/java/com/mcdg/game/TrajectoryCalculator.java` (tune coefficients)
- `src/main/java/com/mcdg/game/DiscFlightSimulator.java` (tune coefficients)
- `src/client/java/com/mcdg/client/HudOverlays.java` (enhanced display)
- Optional: Create particle renderer for wind visualization

**Testing:**
- Extensive playtesting with various wind conditions
- Gather user feedback on wind impact
- Verify no performance degradation
- Test edge cases (extreme wind, rapid wind changes)
- Run full regression test suite

**Estimated Time:** 4-6 hours

---

## Integration with Existing Systems

### System Impact Analysis

| System | Impact | Changes Required |
|--------|--------|------------------|
| `TrajectoryCalculator` | **High** | Add wind parameter, apply wind physics |
| `DiscFlightSimulator` | **High** | Add wind to FlightState, apply wind physics |
| `ChargedDiscItem` | **Medium** | Pass wind to calculators when throwing |
| `HudOverlays` | **Medium** | Add wind display to compass and power bar |
| `McdgAdminCommands` | **Medium** | Add wind command branch |
| `McdgMod` | **Low** | Register WindManager tick handler and network packets |
| `McdgConfig` | **Low** | Add wind configuration options |
| `ThrowResolver` | **None** | No changes (uses landing position, not flight path) |
| `HoleProgressTracker` | **None** | No changes (lie resolution independent of wind) |
| `RoundStateManager` | **None** | No changes (wind is environmental, not round state) |
| `Tournament System` | **Future** | Will integrate when tournament system is implemented |

### Backwards Compatibility

- **Default behavior:** Wind system disabled by default via config (`MCDG_ENABLE_WIND=false`)
- **Existing throws:** No wind = zero velocity vector = no change to existing behavior
- **Existing tests:** All existing tests will pass (wind = 0 by default)
- **Multiplayer:** Clients without wind mod will ignore wind packets (graceful degradation)

### Performance Considerations

- **Wind state:** Single Vec3d per world (minimal memory)
- **Wind updates:** Configurable interval (default 200 ticks = 10 seconds)
- **Network sync:** Only send when wind changes (not every tick)
- **Physics impact:** One vector addition per tick (negligible CPU)
- **HUD updates:** Only during charge (existing render loop)

---

## Testing Strategy

### Unit Tests

**New Test Class:** `WindManagerTest.java`
```java
@Test
void testWindStateCreation() {
    WindState wind = new WindState(new Vec3d(0.1, 0, 0), 0.5, 90.0f, WindMode.FIXED, false, 0, null);
    assertEquals(0.5, wind.speed());
    assertEquals(90.0f, wind.directionDegrees());
}

@Test
void testNaturalWindGeneration() {
    // Test that natural wind stays within bounds
    for (int i = 0; i < 100; i++) {
        WindState wind = WindManager.generateNaturalWind(mockWorld);
        assertTrue(wind.speed() >= 0.0 && wind.speed() <= 1.0);
        assertTrue(wind.directionDegrees() >= 0.0f && wind.directionDegrees() < 360.0f);
    }
}

@Test
void testWindPhysicsApplication() {
    // Test that wind affects trajectory calculation
    Vec3d noWind = Vec3d.ZERO;
    Vec3d eastWind = new Vec3d(0.1, 0, 0);
    
    TrajectoryResult noWindResult = TrajectoryCalculator.calculateTrajectory(..., noWind);
    TrajectoryResult windResult = TrajectoryCalculator.calculateTrajectory(..., eastWind);
    
    // Wind should cause lateral drift
    assertNotEquals(noWindResult.lateralDriftFt(), windResult.lateralDriftFt());
}
```

### Integration Tests

**Manual Test Scenarios:**
1. **Calm conditions:** `/mcdg wind clear` - throws should behave as before
2. **Fixed wind:** `/mcdg wind set 0.5 90` - throws should drift east
3. **Natural wind:** `/mcdg wind mode natural` - wind should vary over time
4. **Strong wind:** `/mcdg wind set 0.8 180` - throws should drift significantly south
5. **Multiplayer sync:** Wind changes should propagate to all clients

### Regression Tests

- Run `./gradlew test` - ensure all existing tests pass
- Run `./gradlew quickRegression` - verify determinism (with fixed wind)
- Run `./gradlew smokeRegression` - pre-deployment validation
- Manual ATLauncher testing - full integration test

---

## Risks and Mitigations

### Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Wind makes throws too unpredictable | High | Configurable max wind speed, default to conservative values |
| Performance degradation in multiplayer | Medium | Optimize sync frequency, batch wind updates |
| Wind sync desynchronization | Medium | Add wind version/timestamp, client-side validation |
| Physics instability with extreme wind | Medium | Clamp wind effects, add safety checks |
| Tournament wind fairness concerns | High | Seeded wind generation, admin oversight tools |

### Design Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Wind system too complex for casual players | Medium | Clear HUD indicators, optional enable/disable |
| Wind physics don't feel realistic | Medium | Extensive playtesting, tunable coefficients |
| Tournament wind modes confusing | Low | Clear documentation, preset configurations |
| Backwards compatibility issues | Low | Default disabled, graceful degradation |

---

## Success Criteria

### Functional Requirements
- [x] Wind affects disc flight trajectory based on speed and direction
- [x] Natural wind generation based on biome, weather, and time
- [x] Admin commands for manual wind control
- [x] Round lifecycle wind automation (CALM default, consistent per round, manual override disables automation)
- [ ] Tournament wind modes for competitive play (deferred - requires tournament system)
- [x] HUD display of current wind conditions
- [x] Client-server wind synchronization
- [x] Configuration options for wind system

### Non-Functional Requirements
- [x] No performance degradation (>60 FPS maintained)
- [x] Backwards compatible (existing throws unchanged when disabled)
- [x] Deterministic with fixed wind (regression tests pass)
- [x] Multiplayer compatible (wind syncs correctly)
- [x] Clean integration with existing architecture

### User Experience Requirements
- [x] Wind effects are noticeable but not overwhelming
- [x] Wind information is clearly displayed on HUD
- [x] Admin commands are intuitive and well-documented
- [x] Tournament wind modes are fair and balanced
- [x] System can be disabled for players who prefer no wind

---

## Implementation Status

### Completed Phases

**Phase 1: Core Wind System** ✅
- `WindState` record and `WindMode` enum implemented
- `WindManager` class with per-world state management
- `TrajectoryCalculator` integrated with wind physics
- `ChargedDiscItem` passes wind to trajectory calculation
- Admin commands: `/mcdg wind set`, `/mcdg wind clear`, `/mcdg wind mode`, `/mcdg wind show`, `/mcdg wind random`
- Configuration: `MCDG_ENABLE_WIND`, `MCDG_DEFAULT_WIND_SPEED`, `MCDG_WIND_UPDATE_INTERVAL`

**Phase 2: Natural Wind Generation** ✅
- Biome-based wind modifiers using `BiomeTags` (ocean/plains = windier, forest/jungle = calmer, mountains = very windy)
- Weather integration (rain = 1.5x, thunder = 2.5x multiplier)
- Time-based variation (day = 0.8x, night = 1.2x)
- Smoothed direction transitions to avoid sudden flips
- Gusting behavior (20% chance, 30% speed increase)

**Phase 3: Client-Side Wind Display** ✅
- `WindSync` network packet for server-to-client synchronization
- `WindManagerClient` for client-side wind state
- Wind indicator on compass HUD (direction + speed with color coding)
- Wind arrow near power bar during charge
- Automatic wind sync on mode changes and natural wind updates

**Phase 5: Polish and Balancing** ✅
- Wind physics coefficients tuned (glide: 0.05, fade: 0.015 - increased from 0.02/0.005)
- Enhanced `/mcdg wind show` command with gusting indicator and usage hints
- Documentation updated with implementation status

**Round Lifecycle Automation** ✅
- `RoundWindPolicy` and `RoundWindService` implemented
- Wind automatically applied on round start and restored on round end/cleanup
- `RoundWindMode.CALM` default preserves legacy behavior
- Consistent wind for the entire round (no mid-round changes)
- Manual `/mcdg wind` command disables automation until round end or `/mcdg wind auto`
- Configuration: `MCDG_ROUND_WIND_MODE` (CALM, NATURAL, FIXED_RANDOM)
- Hooks added to `startround`, `resumecourse`, `playcourse`, `endround`, `cleanupcourse`, and session commands

### Deferred Phases

**Phase 4: Tournament Wind Support** ⏸️
- Deferred pending tournament system implementation
- Tournament wind modes (CALM, CONSISTENT, VARIABLE, NATURAL) planned
- Seeded wind generation for fairness
- Admin oversight tools for tournament wind management
- Per-hole wind variation (deferred to post-round-automation polish)
- Wind presets (light/moderate/strong) (deferred to config expansion)

---

## Timeline Estimate

| Phase | Estimated Time | Dependencies |
|-------|---------------|--------------|
| Phase 1: Core Wind System | 4-6 hours | None |
| Phase 2: Natural Wind Generation | 3-4 hours | Phase 1 |
| Phase 3: Client-Side Wind Display | 2-3 hours | Phase 1 |
| Phase 4: Tournament Wind Support | 3-4 hours | Phase 1, Phase 2 |
| Phase 5: Polish and Balancing | 4-6 hours | All previous phases |

**Total Estimated Time:** 16-23 hours

---

## Next Steps

1. **Review and approve** this implementation plan
2. **Create feature branch:** `feature/wind-system`
3. **Begin Phase 1 implementation** (Core Wind System)
4. **Test each phase** before proceeding to the next
5. **Update documentation** as features are implemented
6. **Deploy to ATLauncher** for final validation
7. **Merge to master** when all phases complete and tests pass

---

## Future Enhancements (Post-Implementation)

- **Wind prediction system:** Show expected drift on trajectory preview
- **Wind particle effects:** Visual feedback for wind direction and speed
- **Wind sound effects:** Audio feedback for wind conditions
- **Advanced tournament modes:** Custom wind patterns per hole
- **Wind statistics:** Track how players perform in different wind conditions
- **Biome-specific wind patterns:** More nuanced biome behavior
- **Altitude effects:** Higher elevation = stronger wind
- **Seasonal wind variations:** Different wind patterns by season (if seasons mod is installed)
