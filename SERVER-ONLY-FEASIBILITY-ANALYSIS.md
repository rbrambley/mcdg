# Server-Only Mod Feasibility Analysis

**Status:** Technical Assessment  
**Created:** 2026-06-17  
**Goal:** Evaluate feasibility of converting MCDG from client+server mod to server-only mod

---

## Executive Summary

**Feasibility: LOW** - Converting MCDG to server-only would be technically possible but would significantly degrade the user experience and require substantial architectural changes. The current client-side components are integral to the mod's core gameplay experience.

**Recommendation:** **NOT RECOMMENDED** - The client-side components provide essential gameplay feedback and user experience that cannot be adequately replaced with vanilla alternatives.

---

## Current Architecture Analysis

### **Current Split**

**Server-Side (Core Game Logic):**
- ✅ `McdgMod` - Server initialization and service wiring
- ✅ `TrajectoryCalculator` - Physics calculations for throws
- ✅ `DiscFlightSimulator` - Flight simulation and physics
- ✅ `RoundStateManager` - Round state and player management
- ✅ `CoursePlacementService` - Course generation and placement
- ✅ `HoleProgressTracker` - Throw resolution and scoring
- ✅ All game mechanics, rules, and data persistence

**Client-Side (User Experience):**
- ✅ `McdgClientMod` - Client initialization and event handling
- ✅ `HudOverlays` - Power bar, compass, stance indicators
- ✅ `MiniMapRenderer` - Course minimap with terrain rendering
- ✅ `DiscTrailRenderer` - Particle trails for throw visualization
- ✅ `ThrowPreferenceManager` - Stance/angle selection
- ✅ `ClientKeybinds` - Custom keybinds for disc golf controls
- ✅ Various UI screens - Menus, scorecards, leaderboards
- ✅ `CinematicOverlay` - Ace/round completion cinematics
- ✅ `WaypointManager` - Custom waypoint system
- ✅ Mixins - Client-side item behavior modifications

### **Current Configuration**
```json
{
  "environment": "*",           // Runs on both client and server
  "entrypoints": {
    "main": ["com.mcdg.McdgMod"],
    "client": ["com.mcdg.client.McdgClientMod"]
  }
}
```

---

## What Would Be Lost

### **Critical User Experience Components**

**1. Power Charge HUD (HIGH IMPACT)**
- **Current:** Visual power bar with distance markers, percentage, color zones
- **Loss:** Players would have no visual feedback during charging
- **Vanilla Alternative:** Use vanilla bow charging animation (poor fit for disc golf)
- **Impact:** SEVERE - Core gameplay mechanic becomes guesswork

**2. Stance/Angle Selection (HIGH IMPACT)**
- **Current:** R key to cycle stances, scroll wheel for angles, visual indicators
- **Loss:** No easy way to select throw stances and release angles
- **Vanilla Alternative:** Chat commands `/mcdg stance backhand`, `/mcdg angle hyzer`
- **Impact:** SEVERE - Becomes cumbersome and breaks flow

**3. Course Minimap (MEDIUM-HIGH IMPACT)**
- **Current:** Real-time minimap with terrain, hazards, basket location
- **Loss:** No visual course layout information
- **Vanilla Alternative:** Use maps/signs (static, less informative)
- **Impact:** HIGH - Navigation and course awareness significantly degraded

**4. Throw Particle Trails (MEDIUM IMPACT)**
- **Current:** Colored particle trails showing flight path
- **Loss:** No visual feedback on throw trajectory
- **Vanilla Alternative:** None (ender pearl trail is different)
- **Impact:** MEDIUM - Loss of visual feedback and "feel"

**5. Custom Menus and UI (MEDIUM IMPACT)**
- **Current:** Custom GUI for course selection, round management, settings
- **Loss:** Clunky command-based interface
- **Vanilla Alternative:** Chat commands only
- **Impact:** MEDIUM - User experience becomes less intuitive

**6. Cinematic Overlays (LOW-MEDIUM IMPACT)**
- **Current:** Ace celebrations, round completion cinematics
- **Loss:** No special visual feedback for achievements
- **Vanilla Alternative:** Chat messages only
- **Impact:** LOW-MEDIUM - Loss of polish and reward feedback

**7. Scorecard/Leaderboard Displays (MEDIUM IMPACT)**
- **Current:** Real-time scorecard overlays, live leaderboards
- **Loss:** No visual scoring information during play
- **Vanilla Alternative:** Vanilla scoreboard (limited functionality)
- **Impact:** MEDIUM - Competitive play becomes harder to follow

**8. Compass and Coordinates (LOW IMPACT)**
- **Current:** Custom compass with coordinates display
- **Loss:** Vanilla F3 debug screen only
- **Vanilla Alternative:** F3 debug screen (already available)
- **Impact:** LOW - Minor convenience loss

---

## Technical Challenges

### **1. Client-Side Input Handling**
**Challenge:** Throw stance/angle selection currently uses client-side keybinds
**Server-Only Solution:** 
- Use chat commands: `/mcdg stance <overhand|backhand|forehand>`
- Use item cycling or other vanilla mechanisms
**Complexity:** MEDIUM - Requires complete input redesign

### **2. Real-Time Visual Feedback**
**Challenge:** Power bar, charge indicators, stance feedback all client-side rendering
**Server-Only Solution:**
- Use action bar for power indication
- Use chat messages for stance changes
- Use boss bar for round information
**Complexity:** HIGH - Limited vanilla UI options

### **3. Course Visualization**
**Challenge:** Minimap requires client-side terrain sampling and rendering
**Server-Only Solution:**
- Provide written course descriptions
- Use map items (static, limited detail)
- Place signs/markers in world
**Complexity:** HIGH - Cannot replicate dynamic minimap functionality

### **4. Throw Visualization**
**Challenge:** Particle trails require client-side particle system
**Server-Only Solution:**
- Use vanilla ender pearl trail (different aesthetic)
- Place temporary markers along flight path
- No visual flight path (rely on landing position only)
**Complexity:** MEDIUM - Limited vanilla particle options

### **5. UI/UX Design**
**Challenge:** Custom screens and menus provide intuitive interface
**Server-Only Solution:**
- Command-based interface
- Books and written documentation
- Sign-based information displays
**Complexity:** HIGH - Significant UX degradation

---

## Proposed Server-Only Architecture

### **Modified fabric.mod.json**
```json
{
  "environment": "server",           // Changed from "*" to "server"
  "entrypoints": {
    "main": ["com.mcdg.McdgMod"]
    // Remove "client" entrypoint
  },
  "mixins": []                      // Remove client mixins
}
```

### **Required Code Changes**

**1. Remove Client-Side Code**
- Delete entire `src/client/` directory
- Remove client entrypoint from fabric.mod.json
- Remove client-side mixins

**2. Add Server-Side Alternatives**
```java
// Add command-based stance selection
public class StanceCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("mcdg")
            .then(literal("stance")
                .then(literal("overhand").executes(ctx -> setStance(ctx, ThrowStance.OVERHAND)))
                .then(literal("backhand").executes(ctx -> setStance(ctx, ThrowStance.BACKHAND)))
                .then(literal("forehand").executes(ctx -> setStance(ctx, ThrowStance.FOREHAND)))
            )
            .then(literal("angle")
                .then(literal("hyzer").executes(ctx -> setAngle(ctx, ReleaseAngle.HYZER)))
                .then(literal("flat").executes(ctx -> setAngle(ctx, ReleaseAngle.FLAT)))
                .then(literal("anhyzer").executes(ctx -> setAngle(ctx, ReleaseAngle.ANHYZER)))
            )
        );
    }
}
```

**3. Add Server-Side Player State**
```java
// Move stance/angle preference to server-side
public class ServerThrowPreferenceManager {
    private static final Map<UUID, ThrowPreference> preferences = new ConcurrentHashMap<>();
    
    public static void setPreference(UUID playerId, ThrowStance stance, ReleaseAngle angle) {
        preferences.put(playerId, new ThrowPreference(stance, angle));
    }
    
    public static ThrowPreference getPreference(UUID playerId) {
        return preferences.getOrDefault(playerId, new ThrowPreference(ThrowStance.OVERHAND, ReleaseAngle.FLAT));
    }
}
```

**4. Add Vanilla UI Integration**
```java
// Use boss bar for round information
public void updateRoundBossBar(ServerPlayerEntity player, RoundState round) {
    BossBar bossBar = new BossBar(
        Text.literal("Hole " + round.holeNumber() + " - Par " + round.par()),
        BossBar.Color.GREEN,
        BossBar.Style.PROGRESS
    );
    player.getServer().getBossBarManager().add(player, bossBar);
}

// Use action bar for power indication
public void showPowerIndicator(ServerPlayerEntity player, float charge) {
    player.sendMessage(Text.literal("Power: " + Math.round(charge * 100) + "%")
        .formatted(Formatting.GREEN), true);
}
```

---

## User Experience Comparison

### **Current (Client+Server)**
```
Player Experience:
1. Hold right-click to charge disc
2. See visual power bar with distance markers
3. Press R to cycle through stances (Overhand → Backhand → Forehand)
4. See stance indicator on HUD
5. Scroll to adjust release angle (Hyzer → Flat → Anhyzer)
6. See angle indicator on HUD
7. Release to throw
8. See colored particle trail showing flight path
9. See minimap with course layout and hazards
10. See real-time scorecard overlay
11. Get cinematic feedback on aces/round completion
```

### **Server-Only**
```
Player Experience:
1. Hold right-click to charge disc
2. No visual power feedback (guess timing)
3. Type "/mcdg stance backhand" to change stance
4. See chat message "Stance set to BACKHAND"
5. Type "/mcdg angle hyzer" to change angle
6. See chat message "Angle set to HYZER"
7. Release to throw
8. See vanilla ender pearl trail (different aesthetic)
9. No minimap (rely on memory or written descriptions)
10. Type "/mcdg score" to see current score
11. See chat message "Hole complete! Score: 3"
```

---

## Performance Impact Analysis

### **Current (Client+Server)**
- **Server:** Core game logic only (efficient)
- **Client:** Rendering, UI, input handling (moderate overhead)
- **Network:** Minimal sync packets (efficient architecture)

### **Server-Only**
- **Server:** Core game logic + additional state management (slightly higher)
- **Client:** Vanilla only (no mod overhead)
- **Network:** Slightly higher (more chat messages instead of UI)

**Performance Conclusion:** Server-only would reduce client performance impact but increase server complexity slightly. Net performance gain would be minimal.

---

## Development Effort Estimate

### **Required Changes**
1. **Remove client-side code:** 2-4 hours
2. **Implement server-side preference management:** 3-5 hours
3. **Add command-based UI alternatives:** 4-6 hours
4. **Add vanilla UI integration:** 3-5 hours
5. **Testing and debugging:** 8-12 hours
6. **Documentation updates:** 2-3 hours

**Total Estimated Effort:** 22-35 hours

### **Ongoing Maintenance**
- More complex command interface to maintain
- Player education required for command-based system
- Limited ability to add visual features
- Dependence on vanilla UI limitations

---

## Alternative Approaches

### **Option 1: Lightweight Client (RECOMMENDED)**
Keep minimal client-side components for essential feedback:
- Power bar HUD
- Stance/angle keybinds and indicators
- Basic throw feedback
- Remove complex features (minimap, cinematics, etc.)

**Benefits:**
- Retains core gameplay experience
- Reduces client complexity
- Still requires client mod (but lighter)

**Effort:** 8-12 hours to streamline client code

### **Option 2: Optional Client Components**
Make client-side components optional:
- Server runs without client mod
- Client mod enhances experience when present
- Graceful degradation for vanilla clients

**Benefits:**
- Allows vanilla clients to join (limited functionality)
- Enhanced experience with client mod
- Flexible deployment

**Effort:** 15-20 hours to implement graceful degradation

### **Option 3: Data Pack Integration**
Use data packs for some client-side features:
- Custom models via resource pack
- Sounds via resource pack
- Some UI via data pack features

**Benefits:**
- Reduces client mod complexity
- Leverages vanilla systems

**Limitations:**
- Still requires client-side changes
- Limited functionality compared to mod

**Effort:** 10-15 hours

---

## Conclusion

### **Feasibility: TECHNICALLY POSSIBLE BUT NOT RECOMMENDED**

**Reasons Against Server-Only:**

1. **Severe UX Degradation:** Core gameplay mechanics (power charging, stance selection) become cumbersome
2. **Loss of Visual Feedback:** No power bar, minimap, particle trails, or UI indicators
3. **Command-Based Interface:** Replaces intuitive keybinds with chat commands
4. **Competitive Disadvantage:** Harder to play effectively without visual feedback
5. **Development Effort:** 22-35 hours to implement with worse results
6. **Maintenance Burden:** More complex system with limited functionality

**When Server-Only Might Make Sense:**

1. **Technical Constraints:** If client-side modding is not possible
2. **Server-Only Environment:** If targeting server-side only mod loaders
3. **Minimal Viable Product:** For testing core mechanics without UI
4. **Specific Use Cases:** Where command-based interface is acceptable

### **Recommendation**

**DO NOT convert to server-only.** The current client+server architecture provides essential user experience that cannot be adequately replaced with vanilla alternatives.

**Instead consider:**
1. **Streamline client code** for better performance
2. **Add configuration options** to disable resource-intensive features
3. **Implement optional client components** for flexible deployment
4. **Optimize existing client code** (already in PERFORMANCE.md)

The current architecture is well-designed and provides the best user experience for a disc golf simulation mod.

---

## Decision Matrix

| Factor | Current (Client+Server) | Server-Only | Winner |
|--------|------------------------|-------------|---------|
| User Experience | Excellent | Poor | Current |
| Ease of Use | Intuitive | Cumbersome | Current |
| Visual Feedback | Rich | Minimal | Current |
| Installation | Client+Server | Server only | Server-Only |
| Performance | Moderate client overhead | No client overhead | Server-Only |
| Development Effort | Baseline | +22-35 hours | Current |
| Maintenance | Standard | Complex | Current |
| Feature Set | Complete | Degraded | Current |
| Player Adoption | Requires client mod | Vanilla clients | Server-Only |

**Overall Winner:** Current (Client+Server) architecture

---

## Next Steps

If server-only is still desired despite recommendations:

1. **Assess user base** - Will players accept command-based interface?
2. **Prototype command system** - Test stance/angle commands with players
3. **Implement incremental changes** - Start with non-critical features
4. **Gather feedback** - Get player input on UX changes
5. **Consider hybrid approach** - Optional client components

However, **strong recommendation** to maintain current architecture and focus on optimization instead.
