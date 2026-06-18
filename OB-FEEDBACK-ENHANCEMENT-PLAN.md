# OB Feedback Enhancement Plan

## Problem Statement

With the implementation of fade, hyzer, and anhyzer mechanics, players are now ending up Out of Bounds (OB) more frequently because shots drift off the fairway. However, players receive minimal feedback about:
- Where their shot went OB
- Why it went OB (which boundary was crossed)
- How far they were from the fairway when they crossed
- What they can do differently on the next throw

Additionally, the current OB crossing detection uses straight-line interpolation from throw lie to landing position, which doesn't account for the actual curved trajectory of disc flight. This can result in players being returned to inaccurate positions that don't reflect where they actually went OB.

## Current State Analysis

### Available Data
The system already collects rich flight data via `TrajectoryCalculator`:
- `pathPoints[]` - Actual curved trajectory points (sampled every 5 ticks)
- `totalDistanceFt` - Total horizontal distance traveled
- `lateralDriftFt` - Left/right drift from aim line
- `stance` and `angle` - Throw parameters (FOREHAND/BACKHAND/OVERHAND + HYZER/FLAT/ANHYZER)

### Current Crossing Detection
`ThrowResolver.findLastSolidBeforeOutCrossing()`:
- Uses **straight-line interpolation** from throw lie to landing
- Samples points along the straight line (24 samples minimum)
- Calls `OutOfBoundsClassifier.classifyOutType()` for each sample
- Returns the last position classified as in-bounds

**Problem**: For a disc that fades right, the straight line might cut across the fairway even though the actual curved path went outside the corridor the entire time.

### Current Feedback
`GolfTitleMessenger.sendStrictPenaltyTitle()`:
- Title: "OB +1" or "Hazard +1"
- Subtitle: "Returned to lie" or "Penalty applied"
- Chat message: "OB landing in strict mode: +1 stroke. Returned to last in-bounds solid block."

**Problem**: No directional information, no crossing location, no context about which boundary was crossed.

### OB Classification Types
`OutOfBoundsClassifier.classifyOutType()` returns:
- `NONE` - In bounds
- `OB` - Out of bounds (fluid or corridor)
- `HAZARD` - Hazard penalty (slope or rough)

Specific OB reasons:
- `FLUID_OB` - Landed in water/lava
- `CORRIDOR_OB` - Exceeded playable corridor width
- `SLOPE_HAZARD` - Steep slope (if enabled)
- `ROUGH_HAZARD` - Dense vegetation (if enabled)

## Proposed Solution

### Core Enhancement: Trajectory-Based Crossing Detection

Replace straight-line interpolation with actual trajectory path analysis for accurate crossing detection.

#### Algorithm
1. When `pathPoints[]` is available (calculated throws), iterate through actual trajectory points
2. For each point, check `OutOfBoundsClassifier.classifyOutType()`
3. Track the last point that returned `NONE` (in bounds)
4. Track the first point that returned `OB` or `HAZARD`
5. Calculate additional metrics: crossing distance, lateral drift at crossing
6. Return enhanced crossing resolution with detailed information

#### Fallback
For legacy pearl throws (no `pathPoints`), maintain current straight-line method to ensure backward compatibility.

### Enhanced Feedback Components

#### 1. Accurate Chat Messages
Use enhanced crossing data to provide detailed, actionable feedback:

**Current:**
```
OB landing in strict mode: +1 stroke. Returned to last in-bounds solid block.
```

**Enhanced:**
```
OB landing in strict mode: +1 stroke.
CORRIDOR_OB: Shot exceeded fairway corridor by 4 blocks.
Crossing: 45 ft from tee, 8 blocks right of center.
Returned to: 42 ft from tee (last in-bounds position).
```

#### 2. Directional Title Overlay
Add directional context to the title overlay:

**Current:**
```
OB +1
Returned to lie
```

**Enhanced:**
```
OB +1
Faded 8 blocks right
```

#### 3. Visual Crossing Marker
Place a temporary visual marker at the actual crossing point:
- Different colors for different penalty types (Red=OB, Yellow=Hazard, Blue=Water)
- Particle effect or temporary block
- Auto-remove after 10-15 seconds
- Sound effect on placement

#### 4. Minimap Trajectory Overlay (Future Enhancement)
Show actual flight path with crossing point on minimap:
- Draw corridor boundaries
- Render trajectory path as colored line
- Mark crossing point with distinctive icon
- Color-code sections (green=in bounds, red=OB section)

## Implementation Plan

### Phase 1: Accurate Crossing Detection (Foundation)
**Priority: HIGH** - Fixes core accuracy issue
**Estimated Effort: 4-6 hours**

#### 1.1 Create Enhanced Crossing Resolution Record
**File**: `src/main/java/com/mcdg/game/EnhancedCrossingResolution.java`
```java
public record EnhancedCrossingResolution(
    BlockPos safeLie,              // Last in-bounds position
    BlockPos firstOutCrossing,    // First OB position along trajectory
    double crossingDistanceFt,     // Distance from throw to crossing
    double lateralDriftAtCrossing, // Lateral drift at crossing point
    StrictPenaltyType penaltyType  // Specific penalty (CORRIDOR_OB, FLUID_OB, etc.)
) {}
```

#### 1.2 Implement Trajectory-Based Crossing Detection
**File**: `src/main/java/com/mcdg/game/ThrowResolver.java`

**New Method**: `findCrossingAlongPathPoints()`
```java
private static EnhancedCrossingResolution findCrossingAlongPathPoints(
        Vec3d[] pathPoints,
        BlockPos throwLie,
        ServerWorld world,
        Hole currentHole,
        BlockPos tee,
        BlockPos basket,
        BlockPos alternateAnchor,
        TournamentRulesetManager rulesetManager
) {
    BlockPos lastInBounds = null;
    BlockPos firstOut = null;
    double crossingDistance = 0.0;
    double lateralDrift = 0.0;
    StrictPenaltyType penaltyType = StrictPenaltyType.NONE;

    for (int i = 0; i < pathPoints.length; i++) {
        Vec3d point = pathPoints[i];
        BlockPos blockPos = new BlockPos(
            (int) Math.floor(point.x),
            (int) Math.floor(point.y),
            (int) Math.floor(point.z)
        );

        StrictPenaltyType classification = OutOfBoundsClassifier.classifyOutType(
            world, blockPos, currentHole, tee, basket, alternateAnchor, rulesetManager
        );

        if (classification != StrictPenaltyType.NONE) {
            if (firstOut == null) {
                firstOut = blockPos.toImmutable();
                penaltyType = classification;
                crossingDistance = DistanceUtils.distanceFeet(throwLie, blockPos);
                lateralDrift = OutOfBoundsClassifier.distanceFromPlayableRouteXZ(
                    blockPos, tee, basket, alternateAnchor
                );
            }
        } else {
            lastInBounds = SafePositionFinder.findNearestStandableFeet(world, blockPos);
        }
    }

    return new EnhancedCrossingResolution(
        lastInBounds != null ? lastInBounds : throwLie,
        firstOut,
        crossingDistance,
        lateralDrift,
        penaltyType
    );
}
```

**Update Existing Method**: Modify `findLastSolidBeforeOutCrossing()` to call trajectory-based method when path points available.

#### 1.3 Update ThrowResolver.resolve()
**File**: `src/main/java/com/mcdg/game/ThrowResolver.java`

- Replace `CrossingResolution` with `EnhancedCrossingResolution`
- Call trajectory-based method when `pathPoints` available
- Pass enhanced data to feedback systems
- Add detailed logging for debugging

#### 1.4 Testing
- Test fade throws that go OB
- Test anhyzer throws that go OB
- Test flat throws that go OB
- Verify accurate return positions
- Compare old vs new crossing points
- Test with legacy pearl throws (fallback)

### Phase 2: Enhanced Text Feedback (Quick Win)
**Priority: HIGH** - Immediate player benefit
**Estimated Effort: 2-3 hours**

#### 2.1 Enhance Chat Messages
**File**: `src/main/java/com/mcdg/game/ThrowResolver.java`

**Current Code** (lines 268-276):
```java
String label = landingPenalty == StrictPenaltyType.OB ? "OB" : "Hazard";
String penaltyText = landingPenalty == StrictPenaltyType.OB
        ? "Returned to last in-bounds solid block."
        : "Play next throw from hazard lie.";
player.sendMessage(
    Text.literal(label + " landing in strict mode: +" + penaltyStrokes + " stroke. " + penaltyText),
    true
);
```

**Enhanced Code**:
```java
String label = landingPenalty == StrictPenaltyType.OB ? "OB" : "Hazard";
String penaltyType = enhancedCrossing.penaltyType().name();
String direction = lateralDriftAtCrossing > 0 ? "right" : "left";
String crossingInfo = String.format(
    "%s: Shot exceeded fairway corridor by %.1f blocks.",
    penaltyType,
    Math.abs(lateralDriftAtCrossing) - corridorHalfWidth
);
String locationInfo = String.format(
    "Crossing: %.0f ft from tee, %.1f blocks %s of center.",
    enhancedCrossing.crossingDistanceFt(),
    Math.abs(lateralDriftAtCrossing),
    direction
);
String returnInfo = String.format(
    "Returned to: %.0f ft from tee (last in-bounds position).",
    DistanceUtils.distanceFeet(throwLie, enhancedCrossing.safeLie())
);

player.sendMessage(Text.literal(
    label + " landing in strict mode: +" + penaltyStrokes + " stroke."
), true);
player.sendMessage(Text.literal(crossingInfo), false);
player.sendMessage(Text.literal(locationInfo), false);
player.sendMessage(Text.literal(returnInfo), false);
```

#### 2.2 Enhance Title Overlay
**File**: `src/main/java/com/mcdg/game/GolfTitleMessenger.java`

**Update Method Signature**:
```java
static void sendStrictPenaltyTitle(
    ServerPlayerEntity player,
    StrictPenaltyType landingPenalty,
    int penaltyStrokes,
    String directionalSubtitle  // New parameter
)
```

**Enhanced Implementation**:
```java
static void sendStrictPenaltyTitle(
    ServerPlayerEntity player,
    StrictPenaltyType landingPenalty,
    int penaltyStrokes,
    String directionalSubtitle
) {
    String titleText = landingPenalty == StrictPenaltyType.OB ? "OB +" + penaltyStrokes : "Hazard +" + penaltyStrokes;
    String subtitleText = directionalSubtitle != null
        ? directionalSubtitle
        : (landingPenalty == StrictPenaltyType.OB ? "Returned to lie" : "Penalty applied");

    player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(Formatting.RED, Formatting.BOLD)));
    player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(Formatting.WHITE)));
}
```

**Update Call Site** in `ThrowResolver.resolve()`:
```java
String direction = lateralDriftAtCrossing > 0 ? "right" : "left";
String directionalSubtitle = String.format("Faded %.1f blocks %s", Math.abs(lateralDriftAtCrossing), direction);
GolfTitleMessenger.sendStrictPenaltyTitle(player, landingPenalty, penaltyStrokes, directionalSubtitle);
```

#### 2.3 Testing
- Test various OB scenarios
- Verify message formatting
- Check title overlay readability
- Test with different penalty types

### Phase 3: Visual Crossing Marker (Medium Effort)
**Priority: MEDIUM** - Visual reinforcement
**Estimated Effort: 3-4 hours**

#### 3.1 Create Crossing Marker Sync Packet
**File**: `src/main/java/com/mcdg/net/CrossingMarkerSync.java`
```java
public final class CrossingMarkerSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "crossing_marker");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    public record Payload(
        BlockPos crossingPosition,
        StrictPenaltyType penaltyType,
        int durationTicks  // How long to show marker (default 200 ticks = 10 seconds)
    ) implements CustomPayload {
        public static Payload read(RegistryByteBuf buf) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            BlockPos crossingPosition = new BlockPos(x, y, z);
            int penaltyOrdinal = buf.readVarInt();
            StrictPenaltyType penaltyType = StrictPenaltyType.values()[penaltyOrdinal];
            int durationTicks = buf.readVarInt();
            return new Payload(crossingPosition, penaltyType, durationTicks);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeInt(crossingPosition.getX());
            buf.writeInt(crossingPosition.getY());
            buf.writeInt(crossingPosition.getZ());
            buf.writeVarInt(penaltyType.ordinal());
            buf.writeVarInt(durationTicks);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
```

#### 3.2 Register Server-Side Networking
**File**: `src/main/java/com/mcdg/McdgMod.java`

Add to networking registration:
```java
ServerPlayNetworking.registerGlobalReceiver(CrossingMarkerSync.ID, (payload, context) -> {
    // Server-to-client packet, no server-side processing needed
});
```

#### 3.3 Add Client-Side Receiver
**File**: `src/main/java/com/mcdg/client/ClientNetworking.java`

```java
ClientPlayNetworking.registerGlobalReceiver(CrossingMarkerSync.ID, (payload, context) ->
    context.client().execute(() -> CrossingMarkerRenderer.showMarker(
        payload.crossingPosition(),
        payload.penaltyType(),
        payload.durationTicks()
    ))
);
```

#### 3.4 Implement Marker Rendering
**File**: `src/main/java/com/mcdg/client/CrossingMarkerRenderer.java` (new file)

```java
public final class CrossingMarkerRenderer {
    private static final Map<BlockPos, MarkerData> ACTIVE_MARKERS = new ConcurrentHashMap<>();

    private static record MarkerData(
        StrictPenaltyType penaltyType,
        int remainingTicks,
        int creationTick
    ) {}

    private CrossingMarkerRenderer() {}

    public static void showMarker(BlockPos position, StrictPenaltyType penaltyType, int durationTicks) {
        ACTIVE_MARKERS.put(position, new MarkerData(penaltyType, durationTicks, 0));
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null) return;

        Iterator<Map.Entry<BlockPos, MarkerData>> iterator = ACTIVE_MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, MarkerData> entry = iterator.next();
            MarkerData data = entry.getValue();
            BlockPos pos = entry.getKey();

            // Spawn particles
            spawnParticles(client, pos, data.penaltyType());

            // Decrement ticks
            MarkerData updated = new MarkerData(data.penaltyType(), data.remainingTicks() - 1, data.creationTick() + 1);
            if (updated.remainingTicks() <= 0) {
                iterator.remove();
            } else {
                ACTIVE_MARKERS.put(pos, updated);
            }
        }
    }

    private static void spawnParticles(MinecraftClient client, BlockPos pos, StrictPenaltyType penaltyType) {
        // Color based on penalty type
        ParticleType particleType = switch (penaltyType) {
            case OB -> ParticleTypes.SMOKE;  // Gray/white for OB
            case HAZARD -> ParticleTypes.FLAME;  // Orange for hazard
            case NONE -> ParticleTypes.END_ROD;  // Shouldn't happen
        };

        if (client.world != null) {
            client.world.addParticle(
                particleType,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                0, 0.2, 0
            );
        }
    }

    public static void clearAll() {
        ACTIVE_MARKERS.clear();
    }
}
```

#### 3.5 Hook into Client Tick
**File**: `src/main/java/com/mcdg/client/McdgClientMod.java`

Add to client tick handler:
```java
CrossingMarkerRenderer.tick(client);
```

#### 3.6 Send Marker from Server
**File**: `src/main/java/com/mcdg/game/ThrowResolver.java`

After detecting OB:
```java
if (landingPenalty == StrictPenaltyType.OB && enhancedCrossing.firstOutCrossing() != null) {
    ServerPlayNetworking.send(player, CrossingMarkerSync.ID, new CrossingMarkerSync.Payload(
        enhancedCrossing.firstOutCrossing(),
        enhancedCrossing.penaltyType(),
        200  // 10 seconds
    ));
}
```

#### 3.7 Testing
- Test marker appears at correct location
- Verify color coding by penalty type
- Test marker auto-removal
- Test with multiple OB events
- Verify performance impact

### Phase 4: Minimap Trajectory Overlay (Future Enhancement)
**Priority: LOW** - Nice-to-have visualization
**Estimated Effort: 6-8 hours**

#### 4.1 Extend Trajectory Sync
**Option A**: Extend existing `ThrowTrailSync` payload
**Option B**: Create dedicated `TrajectoryOverlaySync` packet

Include additional data:
- Corridor half-width
- Crossing position
- Penalty type

#### 4.2 Enhance Minimap Rendering
**File**: `src/main/java/com/mcdg/client/MiniMapRenderer.java`

Add rendering methods:
- `drawCorridorBoundaries()` - Draw semi-transparent corridor limits
- `drawTrajectoryPath()` - Render actual flight path as colored line
- `drawCrossingMarker()` - Mark crossing point with distinctive icon
- Color-code sections (green=in bounds, red=OB section)

#### 4.3 Performance Optimization
- Cache corridor calculations
- Limit rendering frequency
- Test with multiple players
- Monitor memory usage

#### 4.4 Testing
- Test corridor boundary accuracy
- Verify trajectory path rendering
- Test crossing point visibility
- Performance testing
- Multiplayer testing

## Data Flow Diagram

```
TrajectoryCalculator.calculateTrajectory()
↓
Returns: TrajectoryResult {
  pathPoints[], totalDistanceFt, lateralDriftFt, stance, angle
}
↓
ThrowResolver.resolve() receives pathPoints
↓
Calls: findCrossingAlongPathPoints(pathPoints, ...)
↓
Returns: EnhancedCrossingResolution {
  safeLie, firstOutCrossing, crossingDistanceFt,
  lateralDriftAtCrossing, penaltyType
}
↓
Enhanced Feedback Systems:
├─ Chat Messages: Detailed crossing information
├─ Title Overlay: Directional context
├─ Visual Marker: Particle effect at crossing point
└─ Minimap Overlay: Trajectory visualization (future)
```

## Testing Strategy

### Unit Testing
- Test `findCrossingAlongPathPoints()` with various path configurations
- Test edge cases (empty path, single point, all in-bounds, all OB)
- Test penalty type classification accuracy

### Integration Testing
- Test full throw flow from release to OB resolution
- Test with different stances (FOREHAND, BACKHAND, OVERHAND)
- Test with different angles (HYZER, FLAT, ANHYZER)
- Test legacy pearl throw fallback

### Manual Testing
- Test various OB scenarios (corridor, water, hazard)
- Verify chat message accuracy and readability
- Test title overlay directional information
- Test visual marker placement and duration
- Test minimap overlay (if implemented)

### Performance Testing
- Monitor server tick time impact
- Test with multiple players simultaneously
- Measure memory usage for path point storage
- Test particle rendering performance

## Risk Mitigation

### Backward Compatibility
- Keep straight-line fallback for legacy pearl throws
- Maintain existing `CrossingResolution` for other code paths
- Add configuration option to enable/disable new behavior

### Testing Coverage
- Extensive testing before deployment
- Gradual rollout with monitoring
- Ability to quickly revert if issues arise

### Performance Impact
- Path points already calculated, minimal overhead
- Particle effects limited to OB events only
- Minimap rendering optimized with caching

### Data Validation
- Add debug logging for crossing detection
- Validate crossing positions are reasonable
- Check for edge cases (null positions, invalid distances)

## Success Criteria

### Phase 1 Success
- [ ] Players return to accurate last in-bounds positions
- [ ] Crossing detection uses actual trajectory when available
- [ ] Legacy pearl throws still work correctly
- [ ] No performance degradation
- [ ] Debug logging provides useful information

### Phase 2 Success
- [ ] Chat messages provide clear, actionable feedback
- [ ] Title overlay includes directional information
- [ ] Players can understand where and why they went OB
- [ ] Message formatting is readable and not overwhelming

### Phase 3 Success
- [ ] Visual markers appear at correct crossing positions
- [ ] Markers are color-coded by penalty type
- [ ] Markers auto-remove after specified duration
- [ ] No performance impact from particle effects

### Phase 4 Success (Future)
- [ ] Minimap shows corridor boundaries accurately
- [ ] Trajectory path is rendered correctly
- [ ] Crossing point is clearly visible
- [ ] Performance remains acceptable with multiple players

## Configuration Options

Consider adding configuration options for:
```java
// In McdgConfig.java
public boolean enableTrajectoryBasedCrossingDetection = true;
public boolean enableEnhancedObFeedback = true;
public boolean enableVisualCrossingMarkers = true;
public boolean enableMinimapTrajectoryOverlay = false;  // Future
public int crossingMarkerDurationTicks = 200;  // 10 seconds
```

## Future Enhancements

### Throw Analysis Command
Add `/mcdg lastthrow` command to provide detailed analysis:
```
=== Throw Analysis ===
Distance: 285 ft
Lateral Drift: 12 ft right
Stance: FOREHAND, Angle: HYZER
Corridor Width: 12 blocks (6 blocks each side)
Your Lateral Position: 20 blocks right
Result: CORRIDOR_OB - exceeded corridor by 8 blocks
Crossing Point: [X,Y,Z] (45 blocks from tee)
Safe Return: [X,Y,Z] (38 blocks from tee)
Suggestion: Try FLAT angle or reduce power to decrease fade
```

### Historical Throw Data
Track throw history to identify patterns:
- Common OB locations for specific holes
- Frequent penalty types per player
- Improvement suggestions based on history

### Replay System
Record and replay throws with trajectory visualization:
- Show flight path in 3D
- Mark crossing points
- Compare similar throws
- Learning tool for players

## Dependencies

### Existing Code
- `TrajectoryCalculator` - Already provides path points
- `OutOfBoundsClassifier` - Already provides penalty classification
- `ThrowResolver` - Already handles OB resolution
- `GolfTitleMessenger` - Already handles title overlays
- Client networking infrastructure - Already in place

### New Code
- `EnhancedCrossingResolution` record
- `findCrossingAlongPathPoints()` method
- `CrossingMarkerSync` packet
- `CrossingMarkerRenderer` client component
- Enhanced message formatting

## Timeline Estimate

- **Phase 1**: 4-6 hours (core algorithm fix)
- **Phase 2**: 2-3 hours (text feedback enhancements)
- **Phase 3**: 3-4 hours (visual marker system)
- **Phase 4**: 6-8 hours (minimap overlay - future)
- **Testing & Refinement**: 4-6 hours across all phases

**Total for Phases 1-3**: 9-13 hours of development + testing

## Notes

- This plan focuses on the most impactful feedback improvements first
- Phases can be implemented independently
- Phase 4 is optional and can be deferred based on player feedback
- All changes maintain backward compatibility with existing systems
- Performance impact is expected to be minimal
