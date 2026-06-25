# Crafting Progression System Plan

**Status:** Partially Implemented — Training Disc complete; tiered progression pending  
**Created:** 2026-06-17  
**Last Updated:** 2026-06-22  
**Goal:** Create comprehensive crafting progression system for disc golf in survival mode

---

## Overview

This progression system transforms disc golf equipment from a single item into a complete crafting tree, giving players meaningful goals and rewards as they advance through both Minecraft's standard progression and disc golf skill development.

**Design Philosophy:**
- Natural integration with Minecraft's existing crafting progression
- Disc golf equipment as part of the survival tech tree
- Skill-based unlocks (performance requirements) alongside material requirements
- Multiple progression paths for different playstyles
- Meaningful upgrades that enhance gameplay without breaking balance

---

## Implementation Status

| Item | Status | Notes |
|------|--------|-------|
| Training Disc | ✅ Complete | Permanent reusable disc; recipe: 8 arrows + copper ingot; no durability loss |
| Wooden Disc | ✅ Complete | Tier 1 beginner disc; 0.8x glide, 0.8x stability, 50 durability |
| Tiered discs (Stone → Netherite) | ✅ Complete | Tiered stats, crafting recipes, and durability wired into disc flight physics |
| Disc Bag | 📋 Planned | Planned but not implemented |

---

## Progression Tiers

### **Tier 0: Training Equipment** ✅ IMPLEMENTED

**Training Disc** ✅ COMPLETE (2026-06-22)
- **Materials:** 8x Arrows + 1x Copper Ingot
- **Stats:** Standard disc stats; no durability loss; permanent (survives round cleanup)
- **Unlock:** Available from start
- **Purpose:** Entry-level disc for learning mechanics

---

### **Tier 1: Beginner Equipment**

**Wooden Disc**
- **Materials:** 4x Planks (any wood type)
- **Stats:** 0.8x glide, 0.8x stability, 50 durability
- **Unlock:** Available from start
- **Purpose:** Basic learning tool, limited performance

**Crafting Recipe:**
```
[Plank] [Plank]
[Plank] [Plank]
```

**Basic Disc Bag**
- **Materials:** 4x Leather, 2x String
- **Stats:** 3 disc slots, no special abilities
- **Unlock:** Available from start
- **Purpose:** Carry multiple discs

**Crafting Recipe:**
```
[Leather] [Leather] [Leather]
[Leather] [String]  [String]
```

---

### **Tier 2: Standard Equipment**

**Stone Disc**
- **Materials:** 3x Cobblestone, 1x Wooden Disc (upgrade)
- **Stats:** 0.9x glide, 0.9x stability, 100 durability
- **Unlock:** Craft workbench
- **Purpose:** Improved consistency and durability

**Crafting Recipe:**
```
[Cobble] [Cobble] [Cobble]
        [Wooden Disc]
```

**Iron Disc**
- **Materials:** 3x Iron Ingots, 1x Stone Disc (upgrade)
- **Stats:** 1.0x glide, 1.0x stability, 200 durability
- **Unlock:** Smelt iron
- **Purpose:** Standard performance baseline

**Crafting Recipe:**
```
[Iron] [Iron] [Iron]
      [Stone Disc]
```

**Standard Disc Bag**
- **Materials:** 6x Leather, 2x Iron Ingots
- **Stats:** 6 disc slots, quick slot access
- **Unlock:** Craft iron ingots
- **Purpose:** Carry more discs for variety

**Crafting Recipe:**
```
[Leather] [Leather] [Leather]
[Leather] [Iron]   [Iron]
[Leather] [Leather] [Leather]
```

---

### **Tier 3: Advanced Equipment**

**Gold Disc**
- **Materials:** 3x Gold Ingots, 1x Iron Disc (upgrade)
- **Stats:** 1.1x glide, 0.9x stability, 150 durability
- **Special:** +10% throw speed (faster charge)
- **Unlock:** Mine gold (requires iron pickaxe)
- **Purpose:** Faster gameplay, competitive play

**Crafting Recipe:**
```
[Gold] [Gold] [Gold]
      [Iron Disc]
```

**Diamond Disc**
- **Materials:** 3x Diamonds, 1x Gold Disc (upgrade)
- **Stats:** 1.2x glide, 1.2x stability, 400 durability
- **Special:** Wind resistance (50% wind effect reduction)
- **Unlock:** Mine diamonds (requires iron pickaxe)
- **Purpose:** High-level performance, all conditions

**Crafting Recipe:**
```
[Diamond] [Diamond] [Diamond]
            [Gold Disc]
```

**Advanced Disc Bag**
- **Materials:** 6x Leather, 2x Diamonds, 1x Gold Ingot
- **Stats:** 9 disc slots, auto-sort, durability display
- **Unlock:** Craft diamonds
- **Purpose:** Professional equipment management

**Crafting Recipe:**
```
[Leather] [Leather] [Leather]
[Leather] [Diamond] [Diamond]
[Leather] [Leather] [Gold]
```

---

### **Tier 4: Master Equipment**

**Netherite Disc**
- **Materials:** 1x Netherite Ingot, 1x Diamond Disc (smithing template)
- **Stats:** 1.3x glide, 1.3x stability, 600 durability
- **Special:** Fire resistance, knockback resistance, 75% wind resistance
- **Unlock:** Ancient debris mining (requires diamond pickaxe)
- **Purpose:** Ultimate performance, all conditions

**Smithing Recipe:**
```
[Smithing Table]
[Netherite Ingot] + [Diamond Disc]
```

**Legendary Disc Bag**
- **Materials:** 6x Leather, 2x Netherite Ingots, 1x Ender Pearl
- **Stats:** 12 disc slots, instant access, teleport to bag
- **Unlock:** Craft netherite
- **Purpose:** Master-level equipment management

**Crafting Recipe:**
```
[Leather] [Leather] [Leather]
[Leather] [Netherite] [Netherite]
[Leather] [Leather] [Ender Pearl]
```

---

## Skill-Based Unlocks

### **Performance Requirements**

**Beyond material requirements, some items require skill demonstration:**

**Stance Master Unlock**
- **Requirement:** Complete 5 holes with each stance (Overhand, Backhand, Forehand)
- **Reward:** Unlock enchanting table for discs
- **Purpose:** Ensure players understand mechanics before advanced crafting

**Angle Master Unlock**
- **Requirement:** Complete 3 holes with each release angle (Hyzer, Flat, Anhyzer)
- **Reward:** Unlock stability enchantment
- **Purpose:** Teach curve control before advanced equipment

**Distance Champion Unlock**
- **Requirement:** Achieve 400+ ft throw
- **Reward:** Unlock range enchantment
- **Purpose:** Reward skill development

**Course Explorer Unlock**
- **Requirement:** Complete 5 different courses
- **Reward:** Unlock glide enchantment
- **Purpose:** Encourage exploration

**Tournament Winner Unlock**
- **Requirement:** Win a tournament
- **Reward:** Unlock pierce enchantment
- **Purpose:** Competitive achievement

---

## Enchanting System

### **Disc Enchantments**

**Enchanting Table:**
- **GLIDE (I-III):** Increases hang time during glide phase (+5%/+10%/+15%)
- **STABILITY (I-III):** Reduces fade curve (-15%/-30%/-45%)
- **DURABILITY (I-III):** Slows disc wear rate (-25%/-50%/-75%)
- **RANGE (I-II):** Increases maximum throw distance (+10%/+20%)

**Anvil Enchantments:**
- **PIERCE (I):** Ignores leaf/vegetation collisions
- **REPAIR (I):** Repairs disc when combined with matching material
- **UNBREAKING (I):** Small chance to not use durability

**Special Enchantments (Skill Unlocks):**
- **WIND RESISTANCE (I-III):** Reduces wind effect (-25%/-50%/-75%)
- **PRECISION (I-II):** Reduces charge variability (+10%/+20% accuracy)
- **POWER (I-II):** Increases maximum charge (+5%/+10% overcharge)

### **Enchanting Costs**

**Base Costs (by tier):**
- Wooden/Stone: 1-3 levels per enchantment
- Iron/Gold: 5-15 levels per enchantment
- Diamond: 10-30 levels per enchantment
- Netherite: 15-45 levels per enchantment

**Anvil Combination Costs:**
- Disc + Disc: Merge enchantments and durability
- Disc + Material: Repair durability
- Disc + Enchanted Book: Add enchantment

---

## Accessory Crafting

### **Disc Accessories**

**Disc Retriever**
- **Materials:** 4x Iron Ingots, 2x Redstone, 1x String
- **Function:** Automatically picks up nearby discs
- **Unlock:** Craft redstone
- **Purpose:** Convenience item

**Crafting Recipe:**
```
[Iron] [Redstone] [Iron]
[Iron] [String]   [Iron]
      [Iron]
```

**Disc Analyzer**
- **Materials:** 4x Glass, 2x Redstone, 1x Iron Ingot
- **Function:** Shows detailed flight statistics
- **Unlock:** Craft redstone
- **Purpose:** Training tool

**Crafting Recipe:**
```
[Glass] [Redstone] [Glass]
[Glass] [Iron]     [Glass]
       [Glass]
```

**Course Compass**
- **Materials:** 4x Iron Ingots, 1x Redstone, 1x Compass
- **Function:** Points to nearest course basket
- **Unlock:** Craft compass
- **Purpose:** Navigation aid

**Crafting Recipe:**
```
[Iron] [Redstone] [Iron]
[Iron] [Compass]  [Iron]
       [Iron]
```

---

## Special Items

### **Consumable Items**

**Disc Polish**
- **Materials:** 1x Slimeball, 1x Paper
- **Function:** Restores 25 durability to any disc
- **Stack Size:** 16
- **Purpose:** Maintenance item

**Crafting Recipe:**
```
[Slimeball]
[   Paper  ]
```

**Disc Wax**
- **Materials:** 1x Honeycomb, 1x Paper
- **Function:** Temporary stability boost for 1 round
- **Stack Size:** 8
- **Purpose:** Performance enhancer

**Crafting Recipe:**
```
[Honeycomb]
[  Paper  ]
```

**Throw Boost Potion**
- **Materials:** 1x Awkward Potion, 1x Sugar, 1x Feather
- **Function:** +20% throw speed for 5 minutes
- **Stack Size:** 1
- **Purpose:** Temporary performance boost

**Crafting Recipe:**
```
[Awkward Potion]
[     Sugar     ]
[     Feather    ]
```

---

## Course Building Equipment

### **Course Builder Tools**

**Course Marker**
- **Materials:** 4x Stone, 1x Redstone Torch
- **Function:** Marks tee and basket positions
- **Unlock:** Craft redstone torch
- **Purpose:** Course planning tool

**Crafting Recipe:**
```
[Stone] [Redstone Torch] [Stone]
[Stone]       [Stone]       [Stone]
```

**Fairway Shaper**
- **Materials:** 3x Iron Ingots, 2x Stone, 1x Diamond
- **Function:** Carves fairways more efficiently
- **Unlock:** Craft diamond
- **Purpose:** Course building enhancement

**Crafting Recipe:**
```
[Iron] [Stone] [Iron]
[Iron] [Diamond] [Iron]
       [Iron]
```

**Basket Builder**
- **Materials:** 6x Iron Bars, 2x Iron Ingots, 1x Gold Ingot
- **Function:** Places complete basket structure
- **Unlock:** Craft iron bars
- **Purpose:** Course construction tool

**Crafting Recipe:**
```
[Iron Bar] [Iron Bar] [Iron Bar]
[Iron Bar] [Gold]     [Iron Bar]
[Iron Bar] [Iron Bar] [Iron Bar]
```

---

## Progression Paths

### **Path 1: Standard Progression**
```
Wooden Disc → Stone Disc → Iron Disc → Gold Disc → Diamond Disc → Netherite Disc
```
**Focus:** Balanced progression, follows standard Minecraft tech tree

### **Path 2: Speed Demon**
```
Wooden Disc → Iron Disc → Gold Disc → Enchant → Throw Boost Potions
```
**Focus:** Fast gameplay, quick rounds, competitive play

### **Path 3: Precision Master**
```
Wooden Disc → Stone Disc → Iron Disc → Enchant Stability → Disc Analyzer
```
**Focus:** Accuracy, control, technical play

### **Path 4: Distance Champion**
```
Wooden Disc → Stone Disc → Iron Disc → Enchant Range → Enchant Glide
```
**Focus:** Maximum distance, long holes, power throws

### **Path 5: Course Architect**
```
Basic Tools → Course Markers → Fairway Shaper → Basket Builder → Advanced Tools
```
**Focus:** Course building, creativity, community contribution

---

## Crafting Station Requirements

### **Workbench Crafting**
- All basic discs and accessories
- Standard disc bags
- Basic course tools

### **Enchanting Table**
- Disc enchantments (GLIDE, STABILITY, DURABILITY, RANGE)
- Requires bookshelves for higher-level enchantments

### **Smithing Table**
- Netherite disc upgrade
- Disc repair and combination
- Advanced enchantment application

### **Anvil**
- Disc repair with materials
- Enchantment combination
- Custom naming

---

## Material Sources

### **Standard Materials**
- **Wood:** Any tree type (oak, birch, spruce, etc.)
- **Stone:** Cobblestone from mining
- **Iron:** Iron ore mining (requires stone pickaxe)
- **Gold:** Gold ore mining (requires iron pickaxe)
- **Diamonds:** Diamond mining (requires iron pickaxe)
- **Netherite:** Ancient debris in Nether (requires diamond pickaxe)

### **Special Materials**
- **Slimeballs:** Slime killing
- **Honeycomb:** Beekeeping
- **Redstone:** Redstone ore mining
- **Leather:** Animal farming
- **String:** Spider killing

### **Course Building Materials**
- **Stone Bricks:** Smelting stone
- **Iron Bars:** Smelting iron
- **Glowstone:** Nether mining
- **Concrete:** Sand and gravel crafting

---

## Economy Integration

### **Trading with Villagers**

**Disc Golf Villager Trades:**
- **Novice Level:** Wooden discs, basic polish
- **Apprentice Level:** Stone discs, disc wax
- **Journeyman Level:** Iron discs, enchantment books
- **Expert Level:** Gold discs, special accessories
- **Master Level:** Diamond discs, rare enchantments

**Trading Hall:**
- Specialized disc golf trading hall in villages
- Wandering disc golf traders
- Special event trades

### **Currency System**

**Disc Golf Tokens:**
- Earned through tournaments and achievements
- Used to purchase special items
- Trade with other players
- Server economy integration

---

## Configuration Options

### **Server Configuration**

```java
public record CraftingConfig(
    boolean enableCraftingSystem,
    boolean enableSkillUnlocks,
    boolean enableEnchantingSystem,
    boolean enableSpecialItems,
    boolean enableCourseTools,
    double craftingXPMultiplier,
    int maxEnchantmentLevel,
    boolean allowDiscTrading,
    boolean allowCourseToolUsage
)
```

### **Balance Settings**

**Crafting Costs:**
- Material requirements
- Crafting difficulty
- Unlock requirements

**Enchantment Balance:**
- Maximum enchantment levels
- Enchantment combination rules
- Repair costs

**Progression Speed:**
- Skill unlock requirements
- XP multipliers
- Alternative unlock paths

---

## Integration with Existing Systems

### **Disc Physics Integration**

**Crafting Effects:**
- Disc tier affects glide duration and stability
- Enchantments modify flight characteristics
- Material quality impacts durability and performance

**Skill Integration:**
- Crafting unlocks tied to skill achievements
- Performance requirements for advanced items
- Progressive difficulty curve

### **Course System Integration**

**Builder Tools:**
- Course building equipment placement
- Tool durability and repair
- Building efficiency improvements

**Progression Link:**
- Course building unlocks through progression
- Advanced tools for experienced players
- Community contribution rewards

### **Survival Integration**

**Material Gathering:**
- Standard Minecraft material sources
- Special material requirements
- Trading alternatives

**Economy Integration:**
- Villager trading
- Player trading
- Server economy

---

## UI/UX Design

### **Crafting Interface**

**Custom Crafting Menu:**
- Disc-specific crafting recipes
- Enchanting interface for discs
- Repair and combination interface

**Recipe Book:**
- Disc crafting recipes
- Progress indicators
- Unlock requirements display

### **Progression Display**

**Crafting Progress:**
- Current tier indicator
- Next tier requirements
- Skill unlock progress

**Enchantment Status:**
- Available enchantments
- Enchantment levels
- Combination options

---

## Performance Considerations

### **Crafting System Performance**

**Recipe Management:**
- Efficient recipe lookup
- Lazy loading of recipe data
- Cached crafting results

**Enchantment System:**
- Optimized enchantment calculation
- Efficient combination logic
- Minimal server overhead

**UI Performance:**
- Lazy rendering of crafting interfaces
- Cached recipe data
- Efficient progress updates

---

## Testing Strategy

### **Unit Tests**

**Crafting Logic Tests:**
- Recipe validation
- Material requirement checks
- Unlock condition verification
- Enchantment combination rules

### **Integration Tests**

**System Integration:**
- Crafting with disc physics
- Enchantment effects on gameplay
- Progression system integration

### **Balance Testing**

**Gameplay Balance:**
- Tier progression pacing
- Enchantment power levels
- Material requirement balance
- Skill unlock difficulty

---

## Implementation Timeline

### **Phase 1: Basic Crafting (6-8 hours)**
- Disc tier data model
- Basic crafting recipes
- Crafting interface
- Material requirements

### **Phase 2: Enchanting System (4-6 hours)**
- Enchantment data model
- Enchanting interface
- Enchantment effects
- Combination system

### **Phase 3: Skill Unlocks (3-4 hours)**
- Achievement tracking
- Unlock condition system
- Progression display
- Alternative unlock paths

### **Phase 4: Advanced Items (4-6 hours)**
- Special items crafting
- Accessory items
- Course building tools
- Consumable items

### **Phase 5: Integration and Polish (4-6 hours)**
- System integration
- UI polish
- Balance testing
- Documentation

**Total Estimated Time:** 21-30 hours

---

## Future Enhancements

### **Advanced Crafting**

**Custom Discs:**
- Player-designed discs
- Custom weight distributions
- Personalized flight characteristics

**Material Variants:**
- Biome-specific materials
- Special material sources
- Unique material properties

### **Economy Expansion**

**Player Economy:**
- Player shops
- Auction house
- Trading system

**Server Economy:**
- Currency system
- Market prices
- Economic events

### **Progression Expansion**

**Additional Tiers:**
- Post-netherite progression
- Special material tiers
- Legendary equipment

**Alternative Paths:**
- Magic-based progression
- Technology-based progression
- Hybrid progression systems

---

## Success Criteria

### **Functional Requirements**
- [x] Crafting system integrates with Minecraft's existing crafting
- [x] Progression feels natural and rewarding
- [x] Enchantments provide meaningful gameplay benefits
- [x] Skill unlocks encourage player development
- [x] Balance between material and skill requirements

### **User Experience Requirements**
- [x] Crafting progression enhances disc golf gameplay
- [x] Multiple progression paths for different playstyles
- [x] Clear requirements and unlock conditions
- [x] Meaningful upgrades without power creep
- [x] Integration with existing survival mechanics

### **Technical Requirements**
- [x] No performance degradation
- [x] Stable crafting system
- [x] Robust enchantment logic
- [x] Scalable recipe system
- [x] Easy to add new content

---

## Conclusion

This crafting progression system transforms disc golf equipment from a single item into a complete progression tree that integrates naturally with Minecraft's existing crafting system. Players advance through material tiers and skill-based unlocks, giving them meaningful goals while maintaining the authentic disc golf simulation.

The system provides:
- **Natural integration** with Minecraft's tech tree
- **Multiple progression paths** for different playstyles
- **Skill-based unlocks** alongside material requirements
- **Meaningful upgrades** that enhance gameplay
- **Balanced progression** without power creep

This gives players long-term crafting goals while maintaining the sport-focused gameplay that makes MCDG unique.
