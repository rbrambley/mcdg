# Custom Disc Stats System Plan

**Status:** Planning Phase  
**Created:** 2026-06-17  
**Goal:** Create detailed disc statistics system for realistic disc variety and specialization

---

## Overview

This system transforms discs from simple tiered items into specialized equipment with unique flight characteristics, similar to real disc golf where different disc molds have distinct speed, glide, turn, and fade ratings. Players can collect, customize, and optimize their disc bag for different courses and conditions.

**Design Philosophy:**
- Realistic disc flight characteristics based on disc golf physics
- Meaningful differences between disc types
- Strategic disc selection for different situations
- Customization and personalization options
- Collection and discovery aspects

---

## Disc Statistics Model

### **Primary Flight Ratings**

```java
record DiscFlightRatings(
    int speed,          // 1-14: Speed at release (higher = faster)
    int glide,          // 1-7: Ability to maintain altitude (higher = more float)
    int turn,           // -5 to +1: High-speed turn (negative = more turn)
    int fade,           // 0-5: Low-speed fade (higher = more fade)
    double stability   // Calculated from turn/fade combination
)
```

**Rating Descriptions:**

**Speed (1-14):**
- **1-5:** Putters/Approach discs (slow, controlled)
- **6-9:** Fairway drivers (moderate speed)
- **10-12:** Distance drivers (fast)
- **13-14:** Max distance drivers (very fast)

**Glide (1-7):**
- **1-3:** Low glide (drops quickly)
- **4-5:** Moderate glide (standard)
- **6-7:** High glide (floats longer)

**Turn (-5 to +1):**
- **-5 to -3:** High turn (turns right for RHBH)
- **-2 to -1:** Moderate turn
- **0:** Stable (no high-speed turn)
- **+1:** Overstable (resists turning)

**Fade (0-5):**
- **0-1:** Low fade (straight finish)
- **2-3:** Moderate fade (standard finish)
- **4-5:** High fade (strong left finish for RHBH)

### **Secondary Characteristics**

```java
record DiscCharacteristics(
    double weight,         // 150-200 grams (affects stability in wind)
    double rimDepth,       // 0.5-2.0 cm (affects grip and feel)
    double rimWidth,       // 1.0-2.5 cm (affects aerodynamics)
    double stiffness,      // Disc firmness (affects durability and feel)
    DiscPlastic plasticType,  // Material properties
    List<DiscAbility> specialAbilities
)
```

**Plastic Types:**
- **Base:** Basic plastic, good grip, wears quickly
- **Premium:** Durable, consistent flight, less grip
- **Champion:** Very durable, overstable, consistent
- **Star:** Durable, grippy, balanced flight
- **DX:** Grippy, wears to understable over time

### **Derived Statistics**

```java
record DiscDerivedStats(
    double totalDistance,      // Estimated max distance
    double windResistance,    // Resistance to wind effects
    double accuracy,           // Overall accuracy rating
    double predictability,     // Flight consistency
    String flightShape,        // Visual flight shape description
    String recommendedUse      // Best use case
)
```

---

## Disc Archetypes

### **Putters**

**Classic Putter**
- **Ratings:** Speed 2, Glide 3, Turn 0, Fade 1
- **Characteristics:** 170g, 1.2cm rim depth, Base plastic
- **Flight:** Straight, predictable, short range
- **Use:** Putting, approach shots, short drives

**Beaded Putter**
- **Ratings:** Speed 2, Glide 3, Turn -1, Fade 2
- **Characteristics:** 175g, 1.4cm rim depth, Premium plastic
- **Flight:** Slight turn, stable finish, bead for grip
- **Use:** Windy putting, approach shots

### **Midrange Discs**

**Straight Midrange**
- **Ratings:** Speed 4, Glide 4, Turn 0, Fade 2
- **Characteristics:** 175g, 1.1cm rim depth, Star plastic
- **Flight:** Straight flight, reliable, moderate range
- **Use:** Controlled drives, approach shots, tight fairways

**Overstable Midrange**
- **Ratings:** Speed 4, Glide 3, Turn +1, Fade 3
- **Characteristics:** 180g, 1.3cm rim depth, Champion plastic
- **Flight:** No turn, strong fade, predictable
- **Use:** Hyzer shots, spike hyzers, windy conditions

**Understable Midrange**
- **Ratings:** Speed 5, Glide 5, Turn -3, Fade 1
- **Characteristics:** 170g, 1.0cm rim depth, DX plastic
- **Flight:** High turn, gentle fade, turnover shots
- **Use:** Turnover shots, anhyzer flips, rollers

### **Fairway Drivers**

**Control Driver**
- **Ratings:** Speed 6, Glide 4, Turn -1, Fade 2
- **Characteristics:** 175g, 1.1cm rim depth, Star plastic
- **Flight:** Straight flight, reliable, moderate distance
- **Use:** Accuracy drives, narrow fairways, tunnel shots

**Distance Driver**
- **Ratings:** Speed 9, Glide 5, Turn -2, Fade 2
- **Characteristics:** 170g, 1.0cm rim depth, Star plastic
- **Flight:** Long turnover, gentle fade, max distance
- **Use:** Long drives, open holes, distance lines

**Overstable Driver**
- **Ratings:** Speed 8, Glide 4, Turn 0, Fade 4
- **Characteristics:** 180g, 1.3cm rim depth, Champion plastic
- **Flight:** No turn, strong fade, predictable finish
- **Use:** Hyzer bombs, windy conditions, spike hyzers

### **Distance Drivers**

**Max Distance Driver**
- **Ratings:** Speed 13, Glide 6, Turn -3, Fade 2
- **Characteristics:** 165g, 0.9cm rim depth, Star plastic
- **Flight:** High speed, big turn, long glide, S-curve
- **Use:** Maximum distance, open holes, downwind throws

**Stable Distance Driver**
- **Ratings:** Speed 11, Glide 5, Turn -1, Fade 3
- **Characteristics:** 175g, 1.1cm rim depth, Champion plastic
- **Flight:** Fast, slight turn, reliable fade, long range
- **Use:** Long drives with control, headwind shots

**Understable Distance Driver**
- **Ratings:** Speed 12, Glide 6, Turn -4, Fade 1
- **Characteristics:** 165g, 0.8cm rim depth, DX plastic
- **Flight:** Very understable, big turnover, long flights
- **Use:** Roller shots, massive turnovers, tailwind throws

---

## Custom Disc Creation

### **Disc Customization System**

```java
record CustomDisc(
    UUID discId,
    String name,
    String moldName,
    DiscFlightRatings ratings,
    DiscCharacteristics characteristics,
    String plasticType,
    String color,
    String stampDesign,
    UUID creatorId,           // Player who created it
    int creationDate,
    int uses,                 // Number of times thrown
    double wearLevel          // 0.0-1.0 (affects flight over time)
)
```

### **Disc Creator Interface**

**Custom Disc Builder:**
```java
public class DiscBuilder {
    private String name;
    private DiscFlightRatings ratings;
    private DiscCharacteristics characteristics;
    private String plasticType;
    private String color;
    private String stampDesign;
    
    public CustomDisc build() {
        validateDisc();
        return new CustomDisc(
            UUID.randomUUID(),
            name,
            determineMoldName(ratings),
            ratings,
            characteristics,
            plasticType,
            color,
            stampDesign,
            getPlayerId(),
            getCurrentDate(),
            0,
            0.0
        );
    }
    
    private void validateDisc() {
        // Validate ratings are within realistic bounds
        // Ensure characteristics match ratings
        // Check for overpowered combinations
    }
}
```

**Builder UI:**
- Sliders for speed, glide, turn, fade
- Plastic type selection
- Color picker
- Stamp design selection
- Preview of flight characteristics
- Cost estimation

### **Disc Crafting Requirements**

**Material Costs:**
- **Base disc:** 1x base material (wood/stone/iron/etc.)
- **Custom ratings:** Additional materials based on complexity
- **Special plastics:** Rare materials (gold/diamond/netherite)
- **Custom stamp:** Dye or special items

**Skill Requirements:**
- **Basic customization:** Complete 10 rounds
- **Advanced customization:** Achieve specific scores
- **Expert customization:** Tournament wins

---

## Disc Collection System

### **Disc Library**

```java
public class DiscLibrary {
    private static final Map<UUID, List<CustomDisc>> playerCollections = new ConcurrentHashMap<>();
    
    public static void addDisc(UUID playerId, CustomDisc disc) {
        playerCollections.computeIfAbsent(playerId, k -> new ArrayList<>()).add(disc);
    }
    
    public static List<CustomDisc> getPlayerDiscs(UUID playerId) {
        return playerCollections.getOrDefault(playerId, new ArrayList<>());
    }
    
    public static List<CustomDisc> getDiscsByType(UUID playerId, DiscType type) {
        return getPlayerDiscs(playerId).stream()
            .filter(disc -> getDiscType(disc.ratings()) == type)
            .collect(Collectors.toList());
    }
}
```

### **Disc Categories**

**By Speed:**
- Putters (Speed 1-3)
- Midrange (Speed 4-5)
- Fairway Drivers (Speed 6-9)
- Distance Drivers (Speed 10-14)

**By Stability:**
- Understable (Turn ≤ -2)
- Stable (Turn -1 to 0)
- Overstable (Turn ≥ +1)

**By Use Case:**
- Putting/Approach
- Controlled Drives
- Distance
- Special Purpose (rollers, thumbers, etc.)

### **Disc Bag Management**

**Optimal Bag Composition:**
- 1-2 Putters
- 2-3 Midrange discs
- 3-4 Fairway drivers
- 2-3 Distance drivers
- 1-2 Special purpose discs

**Bag Analysis Tool:**
```java
public class BagAnalyzer {
    public static BagAnalysis analyzeBag(List<CustomDisc> discs) {
        return new BagAnalysis(
            calculateCoverage(discs),
            calculateStabilitySpread(discs),
            calculateDistanceRange(discs),
            identifyGaps(discs),
            recommendAdditions(discs)
        );
    }
}
```

---

## Special Abilities

### **Unique Disc Abilities**

**Wind Cutter**
- **Effect:** 50% wind resistance
- **Requirement:** Overstable ratings, Champion plastic
- **Visual:** Blue particle trail

**Glide Boost**
- **Effect:** +20% glide duration
- **Requirement:** High glide rating, Star plastic
- **Visual:** Green particle trail

**Turnover Master**
- **Effect:** Enhanced turn characteristics
- **Requirement:** High turn rating, DX plastic
- **Visual:** Orange particle trail

**Fade Enhancer**
- **Effect:** Stronger fade for spike hyzers
- **Requirement:** High fade rating, Champion plastic
- **Visual:** Red particle trail

**Precision**
- **Effect:** +15% accuracy, reduced charge variability
- **Requirement:** Stable ratings, Premium plastic
- **Visual:** White particle trail

### **Ability Combinations**

**Common Combinations:**
- Wind Cutter + Precision (windy accuracy)
- Glide Boost + Turnover Master (max distance)
- Fade Enhancer + Overstable (hyzer bombs)

**Rare Combinations:**
- Wind Cutter + Glide Boost (all conditions)
- Precision + Fade Enhancer (accurate hyzers)

**Legendary Combinations:**
- All abilities (ultimate disc, very rare)

---

## Wear and Aging System

### **Disc Wear Mechanics**

```java
public class DiscWearSystem {
    public static void applyWear(CustomDisc disc, int throwCount) {
        double wearRate = calculateWearRate(disc);
        double newWear = disc.wearLevel() + (wearRate * throwCount);
        
        // Wear affects flight characteristics
        DiscFlightRatings wornRatings = applyWearEffects(disc.ratings(), newWear);
        
        // Update disc with worn characteristics
        disc = new CustomDisc(
            disc.discId(),
            disc.name(),
            disc.moldName(),
            wornRatings,
            disc.characteristics(),
            disc.plasticType(),
            disc.color(),
            disc.stampDesign(),
            disc.creatorId(),
            disc.creationDate(),
            disc.uses() + throwCount,
            Math.min(1.0, newWear)
        );
    }
    
    private static DiscFlightRatings applyWearEffects(DiscFlightRatings ratings, double wearLevel) {
        // Worn discs become more understable
        int turn = ratings.turn() - (int) (wearLevel * 2);
        int fade = Math.max(0, ratings.fade() - (int) (wearLevel * 1));
        
        return new DiscFlightRatings(
            ratings.speed(),
            ratings.glide(),
            Math.max(-5, turn),
            fade
        );
    }
}
```

**Wear Effects by Plastic:**
- **Base:** Wears quickly, becomes significantly understable
- **Premium:** Moderate wear, gradual changes
- **Champion:** Slow wear, maintains characteristics
- **Star:** Balanced wear, predictable changes
- **DX:** Wears to understable, popular for "seasoned" discs

### **Disc Maintenance**

**Repair Options:**
- **Disc Polish:** Reduces wear level by 25%
- **Disc Renewal:** Resets wear to 0 (consumes special item)
- **Plastic Restoration:** Restores original plastic characteristics

---

## Discovery and Acquisition

### **Disc Discovery Methods**

**Crafting:**
- Create custom discs with Disc Builder
- Requires materials and skill unlocks

**Finding:**
- Discover rare discs in structures (villages, temples, etc.)
- Find discs as tournament rewards
- Receive discs from quest completions

**Trading:**
- Trade with other players
- Villager disc golf trades
- Special event disc drops

**Special Events:**
- Limited edition discs
- Seasonal discs
- Community event rewards

### **Rare Disc System**

**Rarity Tiers:**
- **Common:** Standard flight ratings, base materials
- **Uncommon:** Slightly enhanced stats, premium materials
- **Rare:** Unique flight characteristics, special plastics
- **Epic:** Multiple special abilities, legendary materials
- **Legendary:** Unique abilities, perfect stats, very rare

**Visual Indicators:**
- Color-coded item borders
- Special particle effects
- Unique stamp designs
- Glowing effects for rare discs

---

## Integration with Physics System

### **Physics Integration**

```java
// In TrajectoryCalculator.java
public static TrajectoryResult calculateTrajectory(
    ServerWorld world,
    Vec3d startPos,
    Vec3d initialVelocity,
    float launchYawDegrees,
    float charge,
    ThrowStance stance,
    ReleaseAngle angle,
    CustomDisc disc  // NEW: Custom disc with stats
) {
    // Apply disc-specific physics
    double speedMultiplier = calculateSpeedMultiplier(disc.ratings().speed());
    double glideMultiplier = calculateGlideMultiplier(disc.ratings().glide());
    double turnModifier = calculateTurnModifier(disc.ratings().turn());
    double fadeModifier = calculateFadeModifier(disc.ratings().fade());
    
    // Apply plastic characteristics
    double windResistance = calculateWindResistance(disc.characteristics().plasticType());
    double stability = calculateStability(disc.characteristics().weight());
    
    // Apply wear effects
    double wearEffect = 1.0 - (disc.wearLevel() * 0.15); // 15% reduction at max wear
    
    // Combine all modifiers
    double combinedGlide = glideMultiplier * speedMultiplier * wearEffect;
    double combinedFade = fadeModifier * turnModifier * wearEffect;
    
    // Calculate trajectory with custom disc physics
    // ... existing trajectory calculation with custom modifiers
}
```

### **Stance Interaction**

**Disc-Stance Synergy:**
- **Overhand:** Works best with overstable discs (fade compensation)
- **Backhand:** Works best with stable to understable discs
- **Forehand:** Works best with overstable discs (natural fade compensation)

**Optimal Combinations:**
- Overhand + Overstable Driver = Straight flight
- Backhand + Understable Driver = Max distance S-curve
- Forehand + Stable Midrange = Accurate approaches

---

## UI/UX Design

### **Disc Inspector**

**Disc Details Display:**
- Flight ratings (speed, glide, turn, fade)
- Flight shape visualization
- Recommended use cases
- Wear level indicator
- Special abilities display

**Flight Chart:**
- Visual flight path prediction
- Turn and fade indicators
- Distance estimation
- Wind effect preview

### **Disc Bag Manager**

**Bag Configuration:**
- Drag-and-drop disc organization
- Bag slot management
- Quick access slots
- Bag analysis and recommendations

**Bag Optimization:**
- Suggest optimal discs for current course
- Identify gaps in coverage
- Recommend disc additions

### **Disc Creator UI**

**Customization Interface:**
- Rating sliders with real-time feedback
- Plastic type selector with descriptions
- Color picker with preview
- Stamp design gallery
- Cost and requirement display

**Flight Preview:**
- 3D flight path visualization
- Comparison with similar discs
- Performance prediction

---

## Configuration and Balance

### **Server Configuration**

```java
public record DiscStatsConfig(
    boolean enableCustomDiscs,
    boolean enableWearSystem,
    boolean enableSpecialAbilities,
    boolean enableRareDiscs,
    double maxSpeedRating,
    double maxGlideRating,
    int maxCustomDiscsPerPlayer,
    double wearRateMultiplier,
    boolean allowDiscTrading
)
```

### **Balance Considerations**

**Rating Limits:**
- Speed: 1-14 (realistic disc golf range)
- Glide: 1-7 (realistic disc golf range)
- Turn: -5 to +1 (realistic disc golf range)
- Fade: 0-5 (realistic disc golf range)

**Power Limits:**
- No disc should be strictly better than all others
- Trade-offs between speed, glide, turn, fade
- Special abilities balanced by drawbacks

**Wear Balance:**
- Wear should be noticeable but not crippling
- Different wear rates by plastic type
- Maintenance options available

---

## Testing Strategy

### **Unit Tests**

**Disc Physics Tests:**
- Rating calculation accuracy
- Flight characteristic validation
- Wear effect modeling
- Physics integration testing

### **Integration Tests**

**System Integration:**
- Custom disc physics with trajectory calculator
- Wear system with gameplay
- Bag management with inventory
- Trading system with economy

### **Balance Testing**

**Gameplay Balance:**
- Disc performance across different conditions
- Balance between custom and standard discs
- Wear system impact on gameplay
- Special ability balance

---

## Implementation Timeline

### **Phase 1: Core Disc Stats (6-8 hours)**
- Disc statistics data model
- Flight rating system
- Physics integration
- Basic disc types

### **Phase 2: Custom Disc Creation (4-6 hours)**
- Disc builder system
- Customization UI
- Crafting requirements
- Validation system

### **Phase 3: Collection System (4-6 hours)**
- Disc library management
- Bag management tools
- Organization features
- Analysis tools

### **Phase 4: Advanced Features (6-8 hours)**
- Special abilities system
- Wear and aging mechanics
- Rare disc system
- Discovery methods

### **Phase 5: UI and Polish (4-6 hours)**
- Disc inspector UI
- Bag manager UI
- Creator UI polish
- Performance optimization

**Total Estimated Time:** 24-34 hours

---

## Future Enhancements

### **Advanced Customization**

**Disc Molds:**
- Player-created disc molds
- Community mold sharing
- Mold rating system

**Custom Stamps:**
- Player-designed stamp art
- Community stamp gallery
- Limited edition stamps

### **Economy Integration**

**Disc Marketplace:**
- Player-to-player trading
- Auction system
- Rarity-based pricing

**Professional Services:**
- Disc tuning services
- Custom disc commissions
- Disc appraisal system

### **Social Features**

**Disc Sharing:**
- Gift discs to other players
- Disc recommendations
- Community disc reviews

**Competitions:**
- Disc design contests
- Custom disc tournaments
- Collection showcases

---

## Success Criteria

### **Functional Requirements**
- [x] Custom disc stats integrate with physics system
- [x] Flight characteristics feel realistic and varied
- [x] Customization system is intuitive and balanced
- [x] Wear system adds depth without frustration
- [x] Collection system provides meaningful goals

### **User Experience Requirements**
- [x] Disc variety enhances strategic gameplay
- [x] Custom discs feel personal and rewarding
- [x] Bag management is convenient and informative
- [x] Discovery system is exciting and fair
- [x] Balance between custom and standard discs

### **Technical Requirements**
- [x] No performance degradation
- [x] Stable disc data management
- [x] Accurate physics simulation
- [x] Scalable collection system
- [x] Robust customization validation

---

## Conclusion

This custom disc stats system transforms discs from simple tiered items into specialized equipment with unique flight characteristics, similar to real disc golf. Players can collect, customize, and optimize their disc bag for different courses and conditions, adding strategic depth and personalization to the game.

The system provides:
- **Realistic flight characteristics** based on disc golf physics
- **Strategic disc selection** for different situations
- **Customization options** for personal expression
- **Collection goals** for long-term engagement
- **Wear mechanics** for equipment management
- **Balance** between variety and fairness

This gives players the authentic disc golf experience of building and optimizing their disc bag while maintaining the sport-focused gameplay that makes MCDG unique.
