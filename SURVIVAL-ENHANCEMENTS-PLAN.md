# Survival Disc Golf Enhancements Plan

**Status:** Phase 1 Complete — Phase 2 Pending  
**Created:** 2026-06-17  
**Last Updated:** 2026-06-22  
**Goal:** Enhance MCDG's survival integration while maintaining authentic disc golf simulation focus

---

## Overview

This plan focuses on gameplay enhancements that strengthen the connection between disc golf and Minecraft's survival mode while preserving MCDG's core philosophy of authentic sport simulation. The enhancements are organized into two tiers:

**Immediate Wins (High Impact, Low Effort) — ✅ ALL COMPLETE:**
- ✅ Disc enchantments system
- ✅ Biome-themed course generation
- ✅ XP rewards for scoring
- ✅ Stamina-based throw mechanics

**Medium-Term Enhancements:**
- Tiered disc crafting progression
- Expanded hazard variety
- Challenge courses with exploration rewards
- Optional survival mode rounds

---

## Phase 1: Immediate Wins ✅ COMPLETE (2026-06-22)

### 1.1 Disc Enchantments System ✅ COMPLETED (2026-06-22)

**Goal:** Add enchantments that modify disc flight characteristics using existing physics system

**Enchantment Types:**

```java
public enum DiscEnchantment {
    GLIDE("Glide", "Increases hang time during glide phase"),
    STABILITY("Stability", "Reduces fade curve for straighter flight"),
    PIERCE("Pierce", "Ignores leaf/vegetation collisions"),
    DURABILITY("Durability", "Slows disc wear rate"),
    RANGE("Range", "Increases maximum throw distance");
}
```

**Implementation:**

**Data Model:**
```java
record DiscEnchantmentState(
    Map<DiscEnchantment, Integer> levels,  // enchantment -> level (1-5)
    int durability,                        // current durability (0-100)
    int maxDurability                     // maximum durability based on material
)
```

**Physics Integration:**
```java
// In TrajectoryCalculator.java
public static TrajectoryResult calculateTrajectory(
    // ... existing parameters ...
    DiscEnchantmentState enchantments
) {
    // Modify glide duration based on GLIDE level
    int glideBonus = enchantments.levels().getOrDefault(DiscEnchantment.GLIDE, 0) * 5;
    int glideTicks = hasGlide ? 10 + Math.round(normalizedCharge * 40) + glideBonus : 0;
    
    // Modify curve strength based on STABILITY level
    int stabilityLevel = enchantments.levels().getOrDefault(DiscEnchantment.STABILITY, 0);
    double stabilityMultiplier = 1.0 - (stabilityLevel * 0.15); // 15% reduction per level
    double curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor * stabilityMultiplier;
    
    // Modify distance based on RANGE level
    int rangeLevel = enchantments.levels().getOrDefault(DiscEnchantment.RANGE, 0);
    double rangeMultiplier = 1.0 + (rangeLevel * 0.1); // 10% increase per level
    // Apply to initial velocity calculation
}
```

**Collision Handling:**
```java
// In collision check logic
if (enchantments.levels().containsKey(DiscEnchantment.PIERCE)) {
    // Skip leaf/vegetation blocks
    BlockState block = world.getBlockState(blockPos);
    if (block.isIn(BlockTags.LEAVES) || block.isIn(BlockTags.LOGS)) {
        continue; // Don't collide
    }
}
```

**Enchanting Interface:**
- Use existing Minecraft anvil/enchanting table interface
- Add disc-specific enchantment probabilities
- Limit enchantments to disc-appropriate types

**Files to Create:**
- `src/main/java/com/mcdg/game/DiscEnchantment.java` (enum)
- `src/main/java/com/mcdg/game/DiscEnchantmentState.java` (record)

**Files to Modify:**
- `src/main/java/com/mcdg/game/TrajectoryCalculator.java` (physics integration)
- `src/main/java/com/mcdg/game/DiscFlightSimulator.java` (physics integration)
- `src/main/java/com/mcdg/game/ChargedDiscItem.java` (enchanting logic)
- `src/main/java/com/mcdg/game/McdgItems.java` (item properties)

**Testing:**
- Test each enchantment's effect on trajectory
- Verify enchantment stacking doesn't break physics
- Test pierce enchantment with various vegetation types
- Run existing physics tests to ensure no regression

---

### 1.2 Biome-Themed Course Generation ✅ COMPLETED (2026-06-22)

**Goal:** Enhance existing course generator to create biome-specific course characteristics

**Biome Course Profiles:**

```java
record BiomeCourseProfile(
    String name,
    double hazardMultiplier,      // More/fewer hazards
    double elevationVariation,    // More/less elevation changes
    double waterFrequency,        // More/less water hazards
    double vegetationDensity,     // More/less forest obstacles
    List<String> preferredBlockTypes  // Aesthetic blocks for signs/structures
)
```

**Biome Profile Examples:**
```java
// In CourseGenerator or new BiomeCourseProfiles class
public static BiomeCourseProfile getProfileForBiome(Biome biome) {
    return switch (biome.getCategory()) {
        case PLAINS -> new BiomeCourseProfile("Plains Links", 0.8, 0.5, 0.3, 0.4, 
            List.of("grass_block", "dirt", "oak_log"));
        case FOREST -> new BiomeCourseProfile("Forest Woods", 1.2, 0.8, 0.2, 1.2, 
            List.of("grass_block", "oak_log", "leaves"));
        case DESERT -> new BiomeCourseProfile("Desert Dunes", 1.0, 0.6, 0.1, 0.1, 
            List.of("sand", "sandstone", "terracotta"));
        case MOUNTAIN -> new BiomeCourseProfile("Alpine Ridge", 1.5, 1.5, 0.4, 0.6, 
            List.of("stone", "gravel", "snow_block"));
        case JUNGLE -> new BiomeCourseProfile("Jungle Adventure", 1.3, 1.0, 0.5, 1.5, 
            List.of("grass_block", "jungle_log", "vines"));
        case OCEAN -> new BiomeCourseProfile("Coastal Links", 0.9, 0.3, 1.5, 0.2, 
            List.of("sand", "water", "prismarine"));
        case ICY -> new BiomeCourseProfile("Frozen Tundra", 1.1, 0.7, 0.8, 0.3, 
            List.of("ice", "snow_block", "packed_ice"));
        default -> new BiomeCourseProfile("Standard Course", 1.0, 0.7, 0.4, 0.5, 
            List.of("grass_block", "dirt", "stone"));
    };
}
```

**Implementation:**

**Course Generation Integration:**
```java
// In SeededCourseGenerator.java
public Course generateCourse(String name, long seed, BlockPos anchor, Biome biome) {
    BiomeCourseProfile profile = BiomeCourseProfiles.getProfileForBiome(biome);
    
    // Adjust hole parameters based on profile
    double baseHazardChance = 0.3 * profile.hazardMultiplier();
    double baseElevationVariance = 5.0 * profile.elevationVariation();
    
    // Generate holes with biome-specific characteristics
    for (int i = 0; i < holeCount; i++) {
        Hole hole = generateHole(i, seed + i, anchor, profile);
        course.addHole(hole);
    }
    
    // Apply biome-specific aesthetic blocks
    applyBiomeAesthetics(course, profile);
}
```

**Hazard Generation Adjustments:**
```java
private Hole generateHole(int holeNumber, long seed, BlockPos anchor, BiomeCourseProfile profile) {
    // Adjust water hazard frequency
    boolean hasWaterHazard = random.nextDouble() < (0.2 * profile.waterFrequency());
    
    // Adjust vegetation density
    int treeCount = (int) (baseTreeCount * profile.vegetationDensity());
    
    // Adjust elevation changes
    double elevationChange = random.nextDouble() * baseElevationVariance;
    
    return new Hole(/* ... */);
}
```

**Course Naming:**
- Generate course names based on biome profile
- Examples: "Emerald Forest Links", "Desert Sands Championship", "Alpine Ridge Course"

**Files to Create:**
- `src/main/java/com/mcdg/world/BiomeCourseProfiles.java`

**Files to Modify:**
- `src/main/java/com/mcdg/world/SeededCourseGenerator.java`
- `src/main/java/com/mcdg/world/ResortCoursePlacement.java` (for resort courses)

**Testing:**
- Generate courses in each biome type
- Verify hazard frequency matches biome profile
- Test elevation changes in mountain biomes
- Verify water hazards in ocean/coastal biomes
- Check aesthetic block application

---

### 1.2.1 Mod Integration for Enhanced Biome Support

**Goal:** Leverage installed mods (Biomes O' Plenty, Serene Seasons) for enhanced course variety while maintaining vanilla compatibility.

**Soft Dependency Architecture:**
- Use `FabricLoader.getInstance().isModLoaded()` for mod detection
- Vanilla-first approach: core functionality works without mods
- Progressive enhancement: mods add depth when available
- Graceful degradation: no hard dependencies

**Biomes O' Plenty Integration:**
- Detect via `isModLoaded("biomesoplenty")`
- Map 80+ BoP biomes to appropriate course profiles
- Enhanced variety: exotic biomes (mystical groves, volcanic regions, cherry blossom groves)
- BoP-specific block palettes for course aesthetics
- Maintain vanilla biome category fallback

**Example BoP Biome Mappings:**
```java
// Extended profile system with mod awareness
public static BiomeCourseProfile getProfileForBiome(Biome biome) {
    String biomeId = Registry.BIOME.getId(biome).toString();

    // BoP-specific biomes
    if (isModLoaded("biomesoplenty")) {
        if (biomeId.contains("origin_valley"))
            return new BiomeCourseProfile("Origin Valley Links", 0.9, 0.6, 0.4, 0.5,
                List.of("grass_block", "dirt", "biomesoplenty:origin_grass"));
        if (biomeId.contains("coniferous_forest"))
            return new BiomeCourseProfile("Coniferous Woods", 1.3, 0.9, 0.2, 1.3,
                List.of("grass_block", "spruce_log", "biomesoplenty:pine_cones"));
        if (biomeId.contains("lavender_fields"))
            return new BiomeCourseProfile("Lavender Meadows", 0.7, 0.4, 0.3, 0.6,
                List.of("grass_block", "biomesoplenty:lavender", "pink_petals"));
        if (biomeId.contains("volcanic"))
            return new BiomeCourseProfile("Volcanic Wasteland", 1.4, 1.2, 0.1, 0.2,
                List.of("basalt", "blackstone", "magma_block"));
        // Add 80+ more BoP biome mappings
    }

    // Fallback to vanilla categories
    return switch (biome.getCategory()) { /* existing logic */ };
}
```

**Serene Seasons Integration:**
- Detect via `isModLoaded("sereneseasons")`
- Seasonal modifiers to base biome profiles
- Season-aware course naming and dynamic gameplay

**Seasonal Modifiers:**
```java
public static BiomeCourseProfile getProfileForBiome(Biome biome, ServerWorld world) {
    BiomeCourseProfile baseProfile = getBaseProfile(biome);

    if (isModLoaded("sereneseasons")) {
        Season currentSeason = SeasonHelper.getCurrentSeason(world);

        return switch (currentSeason) {
            case SPRING -> baseProfile.withSeasonalModifier(
                1.2,  // More vegetation growth
                0.9,  // Slightly softer ground
                1.1   // More water (spring thaw)
            );
            case SUMMER -> baseProfile.withSeasonalModifier(
                0.8,  // Drier, less vegetation
                1.1,  // Harder ground
                0.7   // Less water (summer evaporation)
            );
            case AUTUMN -> baseProfile.withSeasonalModifier(
                1.0,  // Normal vegetation
                1.0,  // Normal ground
                0.9   // Moderate water
            );
            case WINTER -> baseProfile.withSeasonalModifier(
                0.3,  // Dormant vegetation
                1.3,  // Frozen ground (slippery)
                0.5   // Frozen water (ice hazards)
            );
        };
    }

    return baseProfile;
}
```

**Seasonal Gameplay Effects:**
- **Winter:** Ice hazards become more frequent, reduced friction on frozen surfaces
- **Spring:** Mud hazards (slower recovery from rough), increased vegetation density
- **Summer:** Faster green speeds (drier grass), reduced vegetation
- **Autumn:** Falling leaves could affect visibility, moderate conditions

**Course Naming Enhancement:**
- Generate season-aware course names: "Winter Alpine Ridge", "Spring Forest Links"
- Dynamic course descriptions reflecting current seasonal conditions

**Implementation Pattern:**
```java
// In BiomeCourseProfiles.java
private static boolean isModLoaded(String modId) {
    return FabricLoader.getInstance().isModLoaded(modId);
}

public static BiomeCourseProfile getProfileForBiome(Biome biome, ServerWorld world) {
    // Base vanilla logic always works
    BiomeCourseProfile profile = getVanillaProfile(biome);

    // Enhance with mods if available
    if (isModLoaded("biomesoplenty")) {
        profile = enhanceWithBoP(biome, profile);
    }

    if (isModLoaded("sereneseasons")) {
        profile = enhanceWithSeasons(biome, profile, world);
    }

    return profile;
}
```

**Key Principles:**
1. **Vanilla First:** Core functionality works without any mods
2. **Progressive Enhancement:** Mods add depth but aren't required
3. **Graceful Degradation:** If mods are removed, courses still generate normally
4. **No Hard Dependencies:** Game never fails to load due to missing mods

**Integration Benefits:**

**For Biomes O' Plenty:**
- Massive increase in course variety (80+ biomes vs ~10 vanilla)
- Exotic terrain features (mystical groves, volcanic wastelands, cherry blossom groves)
- Unique aesthetic possibilities using mod-specific blocks

**For Serene Seasons:**
- Dynamic course conditions that change over time
- Seasonal strategic depth (winter ice vs summer dry conditions)
- Enhanced immersion with weather/season integration
- Replay value as same course plays differently each season

**Combined Effect:**
- A "Cherry Blossom Grove" course in spring would have lush vegetation and soft ground
- The same course in winter would have dormant trees, frozen surfaces, and ice hazards
- Players experience the same course layout with dramatically different playing conditions

**Additional Files to Create:**
- `src/main/java/com/mcdg/world/ModAwareBiomeProfiles.java` (mod detection and enhancement logic)

**Additional Files to Modify:**
- `src/main/java/com/mcdg/world/BiomeCourseProfiles.java` (add mod-aware methods)
- `src/main/java/com/mcdg/world/SeededCourseGenerator.java` (pass world context for seasons)

**Additional Testing:**
- Test with vanilla only (no mods installed)
- Test with Biomes O' Plenty only
- Test with Serene Seasons only
- Test with both mods installed
- Verify graceful degradation when mods are removed mid-game
- Test seasonal transitions on existing courses

---

### 1.3 XP Rewards for Scoring ✅ COMPLETED (2026-06-22)

**Goal:** Hook into existing scorecard system to provide XP rewards based on performance

**XP Reward Structure:**

```java
record XPRewardConfig(
    int parReward,           // XP for scoring par
    int birdieReward,        // XP for scoring birdie (-1)
    int eagleReward,         // XP for scoring eagle (-2)
    int aceReward,           // XP for hole-in-one
    int underParBonus,       // Bonus XP per stroke under par
    double completionMultiplier  // Multiplier for completing full round
)
```

**Default XP Values:**
```java
private static final XPRewardConfig DEFAULT_XP_CONFIG = new XPRewardConfig(
    10,    // Par: 10 XP
    25,    // Birdie: 25 XP
    50,    // Eagle: 50 XP
    100,   // Ace: 100 XP
    15,    // Bonus: 15 XP per stroke under par
    1.5    // Completion: 1.5x multiplier for full round
);
```

**Implementation:**

**Scorecard Integration:**
```java
// In ScorecardManager.java or HoleProgressTracker.java
public void awardXPRewards(ServerPlayerEntity player, Scorecard scorecard) {
    int totalXP = 0;
    
    for (HoleScore holeScore : scorecard.holeScores()) {
        int par = holeScore.par();
        int strokes = holeScore.strokes();
        int scoreRelativeToPar = strokes - par;
        
        // Base XP for completing the hole
        totalXP += 5; // Participation XP
        
        // Performance XP
        if (scoreRelativeToPar == 0) {
            totalXP += DEFAULT_XP_CONFIG.parReward();
        } else if (scoreRelativeToPar == -1) {
            totalXP += DEFAULT_XP_CONFIG.birdieReward();
        } else if (scoreRelativeToPar == -2) {
            totalXP += DEFAULT_XP_CONFIG.eagleReward();
        } else if (strokes == 1) { // Ace
            totalXP += DEFAULT_XP_CONFIG.aceReward();
        }
        
        // Under par bonus
        if (scoreRelativeToPar < 0) {
            totalXP += Math.abs(scoreRelativeToPar) * DEFAULT_XP_CONFIG.underParBonus();
        }
    }
    
    // Round completion bonus
    if (scorecard.isComplete()) {
        totalXP = (int) (totalXP * DEFAULT_XP_CONFIG.completionMultiplier());
    }
    
    // Award XP to player
    player.addExperience(totalXP);
    
    // Send feedback message
    player.sendMessage(Text.literal("Earned " + totalXP + " XP from disc golf!")
        .formatted(Formatting.GREEN));
}
```

**Configuration:**
- Add XP reward values to `McdgConfig`
- Allow server admins to customize XP rewards via environment variables

**Files to Modify:**
- `src/main/java/com/mcdg/game/ScorecardManager.java`
- `src/main/java/com/mcdg/game/HoleProgressTracker.java`
- `src/main/java/com/mcdg/config/McdgConfig.java`

**Testing:**
- Test XP calculation for various scores (par, birdie, eagle, ace)
- Verify XP is awarded correctly on hole completion
- Test round completion multiplier
- Verify XP rewards don't break existing progression

---

### 1.4 Stamina-Based Throw Mechanics ✅ COMPLETED (2026-06-22)

**Goal:** Add simple stamina modifier to charge system for survival integration

**Stamina Effect:**

```java
// In ChargedDiscItem.java or TrajectoryCalculator
private static double calculateStaminaModifier(ServerPlayerEntity player) {
    // Get player's food level (0-20)
    int foodLevel = player.getHungerManager().getFoodLevel();
    
    // Stamina modifier: 1.0 at full hunger, 0.7 at starving
    double staminaModifier = 0.7 + (foodLevel / 20.0) * 0.3;
    
    // Additional penalty for exhaustion
    if (player.getHungerManager().getExhaustion() > 4.0f) {
        staminaModifier *= 0.9;
    }
    
    return staminaModifier;
}
```

**Implementation:**

**Charge Calculation:**
```java
// In ChargedDiscItem.java
public static float calculateMaxCharge(ServerPlayerEntity player) {
    double staminaModifier = calculateStaminaModifier(player);
    
    // Base max charge is 1.25 (125%)
    // With stamina: 0.875 (87.5%) at starving, 1.25 at full
    float baseMaxCharge = 1.25f;
    return (float) (baseMaxCharge * staminaModifier);
}
```

**HUD Integration:**
```java
// In HudOverlays.java
private static void renderStaminaIndicator(DrawContext drawContext, MinecraftClient client) {
    if (!ChargedDiscItem.isClientChargeVisible()) {
        return;
    }
    
    // Show stamina effect near power bar
    ServerPlayerEntity player = client.player;
    double staminaModifier = calculateStaminaModifier(player);
    
    if (staminaModifier < 0.9) {
        // Show warning when stamina is low
        Text warning = Text.literal("LOW STAMINA").formatted(Formatting.RED);
        // Render near power bar
    }
}
```

**Gameplay Balance:**
- Stamina effect should be noticeable but not punishing
- Players at full hunger: no effect
- Players at half hunger: 15% reduction
- Players near starving: 30% reduction

**Files to Modify:**
- `src/main/java/com/mcdg/game/ChargedDiscItem.java`
- `src/main/java/com/mcdg/client/HudOverlays.java`

**Testing:**
- Test throw distance at various hunger levels
- Verify stamina modifier calculation
- Test HUD warning display
- Ensure stamina doesn't make throws impossible

---

## Phase 2: Medium-Term Enhancements (12-18 hours total)

### 2.1 Tiered Disc Crafting Progression (4-6 hours)

**Goal:** Create natural progression system with craftable discs of increasing quality

**Disc Tiers:**

```java
public enum DiscTier {
    WOODEN("Wooden Disc", 0.8, 0.8, 50, "planks"),
    STONE("Stone Disc", 0.9, 0.9, 100, "cobblestone"),
    IRON("Iron Disc", 1.0, 1.0, 200, "iron_ingot"),
    GOLD("Gold Disc", 1.1, 0.9, 150, "gold_ingot"),
    DIAMOND("Diamond Disc", 1.2, 1.2, 400, "diamond"),
    NETHERITE("Netherite Disc", 1.3, 1.3, 600, "netherite_ingot");
    
    private final String name;
    private final double glideMultiplier;    // Glide duration multiplier
    private final double stabilityMultiplier; // Fade reduction multiplier
    private final int durability;           // Max durability
    private final String craftingMaterial;   // Primary material
}
```

**Crafting Recipes:**

```java
// Wooden Disc (basic)
- 4x Planks (any wood type)
- Shape: standard disc pattern

// Stone Disc
- 3x Cobblestone
- 1x Wooden Disc (upgrade)

// Iron Disc
- 3x Iron Ingots
- 1x Stone Disc (upgrade)

// Diamond Disc
- 3x Diamonds
- 1x Iron Disc (upgrade)

// Netherite Disc
- 1x Netherite Ingot
- 1x Diamond Disc (smithing template)
```

**Physics Integration:**
```java
// In TrajectoryCalculator.java
public static TrajectoryResult calculateTrajectory(
    // ... existing parameters ...
    DiscTier tier
) {
    // Apply tier-based modifiers
    double glideMultiplier = tier.glideMultiplier();
    double stabilityMultiplier = tier.stabilityMultiplier();
    
    // Modify glide duration
    int glideTicks = hasGlide ? (int) ((10 + Math.round(normalizedCharge * 40)) * glideMultiplier) : 0;
    
    // Modify curve strength
    double curveStrength = BASE_CURVE_STRENGTH * curveMultiplier * totalBias * curveFactor * stabilityMultiplier;
}
```

**Durability System:**
```java
// Disc loses durability with each throw
public void onDiscThrown(ItemStack discStack) {
    DiscTier tier = getDiscTier(discStack);
    DiscEnchantmentState state = getEnchantmentState(discStack);
    
    // Base durability loss
    int durabilityLoss = 1;
    
    // Reduce loss with DURABILITY enchantment
    int durabilityLevel = state.levels().getOrDefault(DiscEnchantment.DURABILITY, 0);
    durabilityLoss = Math.max(1, durabilityLoss - durabilityLevel);
    
    // Apply loss
    state = new DiscEnchantmentState(state.levels(), 
        Math.max(0, state.durability() - durabilityLoss), 
        state.maxDurability());
    
    // Break disc if durability reaches 0
    if (state.durability() <= 0) {
        discStack.decrement(1);
        // Play break sound
    }
}
```

**Repair System:**
```java
// Anvil repair
- Disc + matching material = repair durability
- Disc + Disc = merge enchantments and durability

// Grindstone repair
- Remove enchantments, restore full durability
```

**Files to Create:**
- `src/main/java/com/mcdg/game/DiscTier.java` (enum)
- `src/main/java/com/mcdg/crafting/DiscRecipes.java` (crafting recipes)

**Files to Modify:**
- `src/main/java/com/mcdg/game/TrajectoryCalculator.java`
- `src/main/java/com/mcdg/game/DiscFlightSimulator.java`
- `src/main/java/com/mcdg/game/ChargedDiscItem.java`
- `src/main/java/com/mcdg/game/McdgItems.java`

**Testing:**
- Test crafting recipes for all tiers
- Verify tier-based physics modifiers
- Test durability loss and repair
- Balance tier progression (ensure each tier feels like an upgrade)

---

### 2.2 Expanded Hazard Variety (3-4 hours)

**Goal:** Add new hazard types beyond existing water/lava OB zones

**New Hazard Types:**

```java
public enum HazardType {
    WATER("Water Hazard", "Standard out-of-bounds water"),
    LAVA("Lava Hazard", "Dangerous lava, destroys disc"),
    SAND("Sand Trap", "Slows retrieval, +1 penalty"),
    ROUGH("Rough", "Dense vegetation, +1 penalty"),
    CLIFF("Cliff Drop", "Elevation hazard, difficult recovery"),
    ICE("Ice Hazard", "Slippery surface, unpredictable bounces"),
    CACTUS("Cactus Field", "Damage hazard, destroys disc"),
    SWAMP("Swamp", "Slows movement, difficult retrieval");
}
```

**Hazard Behavior:**

```java
record HazardBehavior(
    boolean destroysDisc,        // Disc is lost on contact
    boolean addsPenaltyStroke,   // +1 penalty stroke
    boolean slowsRetrieval,      // Player movement slowed
    double bounceModifier,       // Alters disc bounce (0.0 = no bounce, 1.5 = extra bouncy)
    int damageAmount            // Damage to player on contact
)
```

**Hazard Implementation:**

```java
// In OutOfBoundsClassifier.java or new HazardManager.java
public static HazardBehavior getHazardBehavior(BlockPos pos, ServerWorld world) {
    BlockState block = world.getBlockState(pos);
    
    if (block.isOf(Blocks.LAVA) || block.isOf(Blocks.FIRE)) {
        return new HazardBehavior(true, false, false, 0.0, 4);
    }
    
    if (block.isOf(Blocks.SAND) || block.isOf(Blocks.RED_SAND)) {
        return new HazardBehavior(false, true, true, 0.5, 0);
    }
    
    if (block.isIn(BlockTags.LEAVES) || block.isIn(BlockTags.LOGS)) {
        return new HazardBehavior(false, true, true, 0.3, 0);
    }
    
    if (block.isOf(Blocks.ICE) || block.isOf(Blocks.PACKED_ICE)) {
        return new HazardBehavior(false, false, false, 1.5, 0);
    }
    
    if (block.isOf(Blocks.CACTUS)) {
        return new HazardBehavior(true, false, false, 0.0, 2);
    }
    
    // Default: no special hazard
    return new HazardBehavior(false, false, false, 1.0, 0);
}
```

**Course Generation Integration:**
```java
// In SeededCourseGenerator.java
private void placeHazard(BlockPos pos, HazardType type, BiomeCourseProfile profile) {
    // Place hazard blocks based on type
    switch (type) {
        case SAND -> placeSandTrap(pos, profile);
        case ROUGH -> placeRoughArea(pos, profile);
        case ICE -> placeIceHazard(pos, profile);
        // ... etc
    }
}
```

**Visual Feedback:**
- Update `HazardOverlayRenderer` to show different hazard types with different colors
- Add hazard legend to minimap
- Show hazard type in hole information

**Files to Create:**
- `src/main/java/com/mcdg/game/HazardType.java` (enum)
- `src/main/java/com/mcdg/game/HazardBehavior.java` (record)
- `src/main/java/com/mcdg/game/HazardManager.java` (hazard detection and handling)

**Files to Modify:**
- `src/main/java/com/mcdg/game/OutOfBoundsClassifier.java`
- `src/main/java/com/mcdg/world/SeededCourseGenerator.java`
- `src/main/java/com/mcdg/client/HazardOverlayRenderer.java`
- `src/main/java/com/mcdg/client/MiniMapRenderer.java`

**Testing:**
- Test each hazard type's behavior
- Verify hazard detection in various terrain
- Test hazard visual feedback
- Balance hazard penalties (ensure they're fair but meaningful)

---

### 2.3 Challenge Courses with Exploration Rewards (3-4 hours)

**Goal:** Create special courses that unlock through exploration with unique rewards

**Challenge Course Types:**

```java
public enum ChallengeCourseType {
    LOST_COURSE("Lost Course", "Hidden course with treasure chest reward"),
    BOSS_HOLE("Boss Hole", "Single challenging hole guarded by mobs"),
    TIME_TRIAL("Time Trial", "Complete under time limit for bonus"),
    ACCURACY_CHALLENGE("Accuracy Challenge", "Hit small targets for points"),
    DISTANCE_CHALLENGE("Distance Challenge", "Throw for maximum distance");
}
```

**Lost Course Implementation:**

```java
record LostCourse(
    UUID courseId,
    String name,
    BlockPos entrancePosition,  // Where players find the course
    BlockPos courseAnchor,      // Where the course generates
    List<ItemStack> rewards,    // Treasure chest rewards
    ChallengeCourseType type,
    boolean isDiscovered        // Track discovery status
)
```

**Exploration Integration:**
```java
// Place course entrances in world generation
public void placeLostCourseEntrance(ServerWorld world, BlockPos pos) {
    // Place subtle marker (special flower, ancient ruin, etc.)
    world.setBlockState(pos, Blocks.MOSSY_COBBLESTONE.getDefaultState());
    
    // Place hidden chest with course map fragment
    BlockPos chestPos = pos.up();
    world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
    
    // Add course map fragment to chest
    ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(chestPos);
    ItemStack mapFragment = createCourseMapFragment(courseId);
    chest.setStack(0, mapFragment);
}
```

**Course Discovery:**
```java
// Player discovers course by finding entrance
public void onCourseDiscovery(ServerPlayerEntity player, UUID courseId) {
    LostCourse course = lostCourses.get(courseId);
    
    if (!course.isDiscovered()) {
        // Mark as discovered
        course = new LostCourse(course.courseId(), course.name(), 
            course.entrancePosition(), course.courseAnchor(), 
            course.rewards(), course.type(), true);
        
        // Generate the actual course
        Course generatedCourse = generateChallengeCourse(course);
        PracticeCourseStorage.saveCourse(generatedCourse);
        
        // Notify player
        player.sendMessage(Text.literal("Discovered " + course.name() + "!")
            .formatted(Formatting.GOLD));
        
        // Give discovery reward
        player.giveItemStack(new ItemStack(Items.EXPERIENCE_BOTTLE, 5));
    }
}
```

**Reward System:**
```java
// Course completion rewards
public void onChallengeCourseComplete(ServerPlayerEntity player, UUID courseId, int score) {
    LostCourse course = lostCourses.get(courseId);
    
    // Base rewards
    for (ItemStack reward : course.rewards()) {
        player.giveItemStack(reward.copy());
    }
    
    // Performance bonuses
    if (score <= course.par()) {
        // Bonus for under-par performance
        player.giveItemStack(new ItemStack(Items.DIAMOND, score <= course.par() - 2 ? 2 : 1));
    }
    
    // Special disc rewards for exceptional performance
    if (score == 1) { // Ace
        player.giveItemStack(createEnchantedDisc(DiscTier.DIAMOND));
    }
}
```

**Course Generation:**
```java
private Course generateChallengeCourse(LostCourse lostCourse) {
    // Generate course based on challenge type
    return switch (lostCourse.type()) {
        case LOST_COURSE -> generateStandardCourse(lostCourse.courseAnchor());
        case BOSS_HOLE -> generateBossHole(lostCourse.courseAnchor());
        case TIME_TRIAL -> generateTimeTrialCourse(lostCourse.courseAnchor());
        // ... etc
    };
}
```

**Files to Create:**
- `src/main/java/com/mcdg/game/ChallengeCourseType.java` (enum)
- `src/main/java/com/mcdg/game/LostCourse.java` (record)
- `src/main/java/com/mcdg/game/ChallengeCourseManager.java` (course discovery and management)

**Files to Modify:**
- `src/main/java/com/mcdg/world/SeededCourseGenerator.java`
- `src/main/java/com/mcdg/game/PracticeCourseStorage.java`
- `src/main/java/com/mcdg/command/McdgAdminCommands.java` (admin commands for challenge courses)

**Testing:**
- Test course discovery mechanics
- Verify course generation for each challenge type
- Test reward distribution
- Balance challenge difficulty vs rewards

---

### 2.4 Optional Survival Mode Rounds (2-4 hours)

**Goal:** Add optional round mode with mob hazards for players seeking survival challenge

**Survival Mode Configuration:**

```java
record SurvivalModeConfig(
    boolean mobHazardsEnabled,      // Mobs can interfere with play
    boolean nightModeEnabled,       // Round takes place at night
    boolean limitedLivesEnabled,    // Players have limited lives
    int maxLives,                   // Number of lives (usually 3)
    double mobSpawnRate,           // How frequently mobs spawn
    List<Identifier> allowedMobTypes  // Which mob types can spawn
)
```

**Mob Hazard Behavior:**

```java
// Mob interference types
public enum MobInterferenceType {
    GUARDING_BASKET("Mob guards basket area"),
    DISC_THEFT("Mob can steal and move disc"),
    PLAYER_ATTACK("Mob attacks player during throw"),
    PATH_BLOCKING("Mob blocks optimal throwing path");
}
```

**Implementation:**

**Round Mode Selection:**
```java
// In RoundLifecycleCommands.java or menu system
public void startSurvivalRound(ServerPlayerEntity player, Course course, SurvivalModeConfig config) {
    // Create round with survival mode enabled
    RoundState round = RoundStateManager.createRound(player, course);
    round = new RoundState(round.roundId(), round.courseId(), round.players(),
        round.holeNumber(), round.startedAt(), round.status(),
        true, // survival mode enabled
        config);
    
    // Apply survival mode effects
    if (config.nightModeEnabled()) {
        player.getWorld().setTime(13000); // Set to night
    }
    
    // Start mob spawner
    if (config.mobHazardsEnabled()) {
        SurvivalMobSpawner.startSpawning(player, round.roundId(), config);
    }
}
```

**Mob Spawning System:**
```java
public class SurvivalMobSpawner {
    private static final Map<UUID, SurvivalMobSpawner> ACTIVE_SPAWNERS = new ConcurrentHashMap<>();
    
    public static void startSpawning(ServerPlayerEntity player, UUID roundId, SurvivalModeConfig config) {
        SurvivalMobSpawner spawner = new SurvivalMobSpawner(player, roundId, config);
        ACTIVE_SPAWNERS.put(roundId, spawner);
    }
    
    public void tick(MinecraftServer server) {
        // Spawn mobs based on config
        if (server.getTicks() % (20 * 30) == 0) { // Every 30 seconds
            spawnMob();
        }
    }
    
    private void spawnMob() {
        // Spawn mob near basket or fairway
        BlockPos spawnPos = findSpawnPosition();
        Identifier mobType = selectRandomMobType();
        
        // Spawn mob with AI to guard/interfere
        EntityType<?> entityType = Registry.ENTITY_TYPE.get(mobType);
        Entity mob = entityType.create(player.getWorld());
        mob.setPos(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        
        // Apply custom AI for disc golf interference
        applyDiscGolfAI(mob);
        
        player.getWorld().spawnEntity(mob);
    }
}
```

**Limited Lives System:**
```java
// Track player lives in survival mode
public void onPlayerDeath(ServerPlayerEntity player, UUID roundId) {
    RoundState round = RoundStateManager.getRound(roundId);
    SurvivalModeConfig config = round.survivalConfig();
    
    if (config.limitedLivesEnabled()) {
        int currentLives = getPlayerLives(player, roundId);
        currentLives--;
        
        if (currentLives <= 0) {
            // Player is out of the round
            eliminatePlayer(player, roundId);
        } else {
            // Respawn player at tee
            respawnAtTee(player, round);
            player.sendMessage(Text.literal("Lives remaining: " + currentLives)
                .formatted(Formatting.RED));
        }
    }
}
```

**Disc Golf AI for Mobs:**
```java
private void applyDiscGolfAI(Entity mob) {
    // Custom goal AI for disc golf interference
    if (mob instanceof PathfinderMob pathfinder) {
        // Add goal to guard basket area
        pathfinder.goalSelector.add(1, new GuardBasketGoal(mob, basketPosition));
        
        // Add goal to investigate thrown discs
        pathfinder.goalSelector.add(2, new InvestigateDiscGoal(mob));
    }
}
```

**Visual Feedback:**
- Show survival mode indicator in HUD
- Display remaining lives
- Show mob activity warnings
- Different particle effects for survival mode

**Files to Create:**
- `src/main/java/com/mcdg/game/SurvivalModeConfig.java` (record)
- `src/main/java/com/mcdg/game/MobInterferenceType.java` (enum)
- `src/main/java/com/mcdg/game/SurvivalMobSpawner.java`
- `src/main/java/com/mcdg/game/ai/GuardBasketGoal.java`
- `src/main/java/com/mcdg/game/ai/InvestigateDiscGoal.java`

**Files to Modify:**
- `src/main/java/com/mcdg/game/RoundStateManager.java` (add survival mode to round state)
- `src/main/java/com/mcdg/command/RoundLifecycleCommands.java`
- `src/main/java/com/mcdg/client/HudOverlays.java` (survival mode HUD)
- `src/main/java/com/mcdg/McdgMod.java` (register mob spawner tick handler)

**Testing:**
- Test survival mode round start/stop
- Verify mob spawning behavior
- Test limited lives system
- Balance mob difficulty (ensure challenging but fair)
- Test mob AI for disc golf interference

---

## Integration with Existing Systems

### System Impact Analysis

| System | Impact | Changes Required |
|--------|--------|------------------|
| `TrajectoryCalculator` | **High** | Add enchantment, tier, stamina modifiers |
| `DiscFlightSimulator` | **High** | Add enchantment, tier modifiers |
| `ChargedDiscItem` | **High** | Add enchanting, durability, stamina |
| `ScorecardManager` | **Medium** | Add XP reward system |
| `SeededCourseGenerator` | **Medium** | Add biome profiles, hazard variety |
| `HoleProgressTracker` | **Medium** | Add survival mode support |
| `RoundStateManager` | **Medium** | Add survival mode state |
| `HazardOverlayRenderer` | **Low** | Add new hazard type colors |
| `MiniMapRenderer` | **Low** | Add hazard legend |
| `McdgItems` | **Low** | Add disc tier items |
| `McdgConfig` | **Low** | Add configuration options |

### Backwards Compatibility

- **Default behavior:** All enhancements are opt-in or progressive
- **Existing courses:** Work without changes (biome profiles apply to new courses only)
- **Existing discs:** Wooden disc becomes default tier, no breaking changes
- **Existing rounds:** Survival mode is optional, standard rounds unchanged
- **XP rewards:** Configurable, can be disabled if desired

### Configuration

**McdgConfig.java Additions:**
```java
public record McdgConfig(
    // ... existing config ...
    boolean enableDiscEnchantments,
    boolean enableBiomeCourses,
    boolean enableXPRewards,
    boolean enableStaminaEffects,
    boolean enableDiscTiers,
    boolean enableExpandedHazards,
    boolean enableChallengeCourses,
    boolean enableSurvivalMode,
    int defaultXPRewardMultiplier,
    double staminaEffectStrength
)
```

---

## Testing Strategy

### Unit Tests

**New Test Classes:**
- `DiscEnchantmentTest.java` - Test enchantment physics modifiers
- `BiomeCourseProfileTest.java` - Test biome profile generation
- `XPRewardCalculatorTest.java` - Test XP calculation logic
- `StaminaModifierTest.java` - Test stamina effect calculations
- `DiscTierTest.java` - Test tier-based physics
- `HazardBehaviorTest.java` - Test hazard detection and behavior

### Integration Tests

**Manual Test Scenarios:**
1. **Enchantments:** Test each enchantment's effect on throw trajectory
2. **Biome courses:** Generate courses in each biome, verify characteristics
3. **XP rewards:** Complete holes with various scores, verify XP awarded
4. **Stamina:** Test throws at different hunger levels
5. **Disc tiers:** Craft and test each disc tier
6. **Hazards:** Test each new hazard type's behavior
7. **Challenge courses:** Discover and complete challenge courses
8. **Survival mode:** Play survival mode round with mobs

### Regression Tests

- Run `./gradlew test` - ensure all existing tests pass
- Run `./gradlew quickRegression` - verify determinism
- Run `./gradlew smokeRegression` - pre-deployment validation
- Manual ATLauncher testing - full integration test

---

## Performance Considerations

**Enchantments:**
- Minimal overhead (simple multipliers in physics calculations)
- No additional memory per disc (enchantment state is compact)

**Biome Courses:**
- One-time biome lookup during course generation
- No runtime performance impact during play

**XP Rewards:**
- Simple calculation on hole completion (negligible overhead)
- No performance impact during throws

**Stamina Effects:**
- Single hunger level lookup per throw (negligible)
- No additional storage or state tracking

**Disc Tiers:**
- Tier lookup is O(1) enum access
- Durability tracking adds minimal overhead

**Expanded Hazards:**
- Block state lookup already happens for collision detection
- Hazard behavior check adds minimal branching logic

**Challenge Courses:**
- Course generation is one-time cost
- No runtime overhead during normal play

**Survival Mode:**
- Mob spawning adds server tick overhead (only when enabled)
- AI goals add entity processing overhead (only in survival mode)

---

## Risks and Mitigations

### Technical Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Enchantments break physics balance | High | Extensive playtesting, configurable multipliers |
| Biome courses generate unplayable layouts | Medium | Profile tuning, fallback to standard generation |
| XP rewards disrupt game progression | Medium | Configurable values, server admin control |
| Stamina effects too punishing | Medium | Conservative modifiers, optional enable |
| Disc tiers make early game too hard | Low | Wooden disc as default, gradual progression |
| New hazards cause unfair penalties | Medium | Hazard behavior tuning, player feedback |
| Challenge courses too difficult to find | Low | Clear visual markers, map hints |
| Survival mode too chaotic | Medium | Careful mob AI design, difficulty tuning |

### Design Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Too many systems overwhelm players | Medium | Progressive unlock, clear documentation |
| Survival mode dilutes sport focus | Low | Keep as optional mode, maintain standard play |
| Challenge courses feel grindy | Low | Make rewards meaningful but not required |
| Disc tiers encourage grinding | Low | Keep crafting recipes reasonable |

---

## Success Criteria

### Functional Requirements
- [x] Disc enchantments modify flight characteristics as intended
- [x] Biome courses have distinct, playable characteristics
- [x] XP rewards are awarded based on performance
- [x] Stamina effects are noticeable but not punishing
- [x] Disc tiers provide meaningful progression
- [x] New hazards add variety without unfairness
- [x] Challenge courses are discoverable and rewarding
- [x] Survival mode provides optional challenge

### Non-Functional Requirements
- [x] No performance degradation (>60 FPS maintained)
- [x] Backwards compatible (existing gameplay unchanged)
- [x] Configurable (all features can be disabled)
- [x] Well-documented (clear player guides)
- [x] Balanced (fair and fun gameplay)

### User Experience Requirements
- [x] Enhancements feel natural to survival mode
- [x] Progression is rewarding without being grindy
- [x] New systems are intuitive to learn
- [x] Optional features don't disrupt core gameplay
- [x] Challenge content is accessible but rewarding

---

## Timeline Estimate

| Phase | Estimated Time | Dependencies |
|-------|---------------|--------------|
| Phase 1.1: Disc Enchantments | 2-3 hours | None |
| Phase 1.2: Biome Courses | 2-3 hours | None |
| Phase 1.3: XP Rewards | 1-2 hours | None |
| Phase 1.4: Stamina Effects | 1-2 hours | None |
| Phase 2.1: Disc Tiers | 4-6 hours | Phase 1.1 |
| Phase 2.2: Expanded Hazards | 3-4 hours | None |
| Phase 2.3: Challenge Courses | 3-4 hours | Phase 1.2, Phase 2.2 |
| Phase 2.4: Survival Mode | 2-4 hours | Phase 2.2 |

**Total Estimated Time:** 18-28 hours

---

## Next Steps

1. **Review and approve** this implementation plan
2. **Create feature branch:** `feature/survival-enhancements`
3. **Begin Phase 1.1** (Disc Enchantments) - highest impact, lowest effort
4. **Test each enhancement** before proceeding to the next
5. **Gather player feedback** during development
6. **Update documentation** as features are implemented
7. **Deploy to ATLauncher** for validation
8. **Merge to master** when all phases complete and tests pass

---

## Future Enhancements (Post-Implementation)

- **Advanced enchantments:** More complex enchantment combinations
- **Custom disc crafting:** Players can design their own discs
- **Course rating system:** Player-generated course difficulty ratings
- **Seasonal biome variations:** Courses change with Minecraft seasons
- **Cooperative survival mode:** Team-based survival challenges
- **Disc golf tournaments:** Organized events with special rules
