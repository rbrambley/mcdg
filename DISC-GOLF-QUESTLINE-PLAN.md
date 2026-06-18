# Disc Golf Questline System Plan

**Status:** Planning Phase  
**Created:** 2026-06-17  
**Goal:** Create immersive questline that integrates disc golf into survival progression

---

## Overview

This questline transforms disc golf from a standalone activity into a core survival progression system. Players advance through quests by completing disc golf challenges, unlocking new courses, discs, and abilities while exploring the world.

**Design Philosophy:**
- Disc golf as a survival skill, not just a minigame
- Natural integration with existing Minecraft progression
- Exploration-driven quest discovery
- Meaningful rewards that enhance both disc golf and survival
- Multiple quest paths for different playstyles

---

## Quest System Architecture

### **Quest Data Model**

```java
record Quest(
    UUID questId,
    String name,
    String description,
    QuestType type,
    QuestDifficulty difficulty,
    List<QuestObjective> objectives,
    List<QuestReward> rewards,
    QuestPrerequisite prerequisite,
    QuestStatus status,
    BlockPos discoveryLocation,  // Where players find the quest
    boolean isRepeatable,
    int cooldownDays
)

record QuestObjective(
    String description,
    QuestObjectiveType type,
    Map<String, Object> parameters,  // Type-specific parameters
    boolean isOptional,
    int targetCount,
    int currentProgress
)

enum QuestObjectiveType {
    COMPLETE_COURSE,           // Complete a specific course
    COMPLETE_HOLE_UNDER_PAR,   // Complete hole under par
    THROW_DISTANCE,           // Achieve specific throw distance
    SCORE_TOTAL,              // Achieve total score target
    DISCOVER_COURSE,          // Find hidden course
    WIN_TOURNAMENT,           // Win tournament
    CRAFT_DISC,               // Craft specific disc tier
    BEAT_SCORE,               // Beat specific score
    COLLECT_ACES,             // Get hole-in-ones
    PLAY_STANCE,              // Use specific throw stance
    COMPLETE_IN_TIME,         // Complete within time limit
    SURVIVAL_MODE             // Complete survival mode round
}

enum QuestType {
    STORY,           // Main storyline quests
    EXPLORATION,     // Discovery-based quests
    SKILL,           // Skill development quests
    CHALLENGE,       // Optional difficult quests
    DAILY,           // Repeatable daily quests
    TOURNAMENT,      // Tournament-related quests
    COMMUNITY,       // Community event quests
}

enum QuestDifficulty {
    BEGINNER,        // Tutorial/introductory
    INTERMEDIATE,    // Standard gameplay
    ADVANCED,        // Experienced players
    EXPERT,          // Highly skilled players
    MASTER           // Ultimate challenges
}

enum QuestStatus {
    LOCKED,          // Not yet available
    AVAILABLE,       // Can be started
    IN_PROGRESS,     // Currently active
    COMPLETED,       // Successfully finished
    FAILED,          // Failed (time-limited quests)
    COOLDOWN         // Completed but on cooldown
}
```

### **Quest Manager**

```java
public final class QuestManager {
    private static final Map<UUID, QuestState> playerQuestStates = new ConcurrentHashMap<>();
    private static final Map<UUID, Quest> allQuests = new ConcurrentHashMap<>();
    
    public static void registerQuest(Quest quest) {
        allQuests.put(quest.questId(), quest);
    }
    
    public static void startQuest(UUID playerId, UUID questId) {
        Quest quest = allQuests.get(questId);
        if (canStartQuest(playerId, quest)) {
            playerQuestStates.put(playerId, new QuestState(questId, QuestStatus.IN_PROGRESS, new ArrayList<>()));
        }
    }
    
    public static void updateObjective(UUID playerId, UUID questId, QuestObjectiveType type, Map<String, Object> context) {
        QuestState state = playerQuestStates.get(playerId);
        if (state != null && state.status() == QuestStatus.IN_PROGRESS) {
            Quest quest = allQuests.get(state.questId());
            for (QuestObjective objective : quest.objectives()) {
                if (objective.type() == type && !objective.isOptional()) {
                    int progress = calculateProgress(objective, context);
                    if (progress >= objective.targetCount()) {
                        completeObjective(playerId, questId, objective);
                    }
                }
            }
            checkQuestCompletion(playerId, questId);
        }
    }
    
    public static List<Quest> getAvailableQuests(UUID playerId) {
        return allQuests.values().stream()
            .filter(quest -> canStartQuest(playerId, quest))
            .collect(Collectors.toList());
    }
}
```

---

## Questline Structure

### **Chapter 1: The Beginning Disc Golfer**

**Quest 1.1: First Steps**
- **Type:** STORY
- **Difficulty:** BEGINNER
- **Prerequisite:** None
- **Discovery:** Village elder (custom NPC or villager)

**Objectives:**
1. Craft a Wooden Disc (crafting table + planks)
2. Complete the beginner course (auto-generated near spawn)
3. Score par or better on any hole

**Rewards:**
- 50 XP
- Stone Disc recipe unlock
- Basic disc golf bag (inventory item)

**Dialogue:**
> "Welcome, traveler! I see you've found interest in the ancient art of disc golf. Let me teach you the basics..."

---

**Quest 1.2: Stance Master**
- **Type:** SKILL
- **Difficulty:** BEGINNER
- **Prerequisite:** Quest 1.1

**Objectives:**
1. Complete a hole using Overhand stance
2. Complete a hole using Backhand stance
3. Complete a hole using Forehand stance

**Rewards:**
- 75 XP
- Stance tutorial book
- Iron Disc recipe unlock

**Learning Focus:** Introduces throw stances and their uses

---

**Quest 1.3: The Perfect Release**
- **Type:** SKILL
- **Difficulty:** BEGINNER
- **Prerequisite:** Quest 1.2

**Objectives:**
1. Complete a hole with Hyzer release angle
2. Complete a hole with Flat release angle
3. Complete a hole with Anhyzer release angle

**Rewards:**
- 75 XP
- Angle mastery certificate
- Glide enchantment unlock

**Learning Focus:** Teaches release angles and curve control

---

### **Chapter 2: The Traveling Pro**

**Quest 2.1: Course Discovery**
- **Type:** EXPLORATION
- **Difficulty:** INTERMEDIATE
- **Prerequisite:** Complete Chapter 1

**Objectives:**
1. Discover 3 different biome-themed courses
2. Complete each discovered course
3. Collect unique biome tokens

**Rewards:**
- 150 XP
- Compass enhancement (shows nearby courses)
- Diamond Disc recipe unlock

**Discovery:** Courses hidden in different biomes with subtle markers

---

**Quest 2.2: Distance Champion**
- **Type:** CHALLENGE
- **Difficulty:** INTERMEDIATE
- **Prerequisite:** Quest 2.1

**Objectives:**
1. Achieve a 300+ ft throw
2. Achieve a 400+ ft throw
3. Achieve a 500+ ft throw

**Rewards:**
- 200 XP
- Range enchantment unlock
- Power throw ability (temporary speed boost)

**Location:** Special "long drive" course in plains biome

---

**Quest 2.3: The Tournament Circuit**
- **Type:** TOURNAMENT
- **Difficulty:** INTERMEDIATE
- **Prerequisite:** Quest 2.1

**Objectives:**
1. Participate in a local tournament (village green)
2. Finish in top 3
3. Score under par for the tournament

**Rewards:**
- 300 XP
- Tournament trophy (decorative item)
- Gold Disc recipe unlock
- Invitation to regional tournament

**Location:** Village with custom disc golf course

---

### **Chapter 3: The Master Player**

**Quest 3.1: The Lost Courses**
- **Type:** EXPLORATION
- **Difficulty:** ADVANCED
- **Prerequisite:** Complete Chapter 2

**Objectives:**
1. Discover the Ancient Forest course (hidden in dense forest)
2. Discover the Mountain Peak course (high elevation)
3. Discover the Desert Oasis course (desert temple)
4. Complete all three courses

**Rewards:**
- 400 XP
- Netherite Disc recipe unlock
- Ancient disc mold (special item)
- Course discovery map

**Discovery:** Requires exploration and puzzle-solving

---

**Quest 3.2: Ace Hunter**
- **Type:** CHALLENGE
- **Difficulty:** ADVANCED
- **Prerequisite:** Quest 3.1

**Objectives:**
1. Get a hole-in-one (ace)
2. Get 3 total aces across different courses
3. Get an ace with each throw stance

**Rewards:**
- 500 XP
- Ace title (prefix before name)
- Golden Disc (special cosmetic)
- Ace celebration enhancement

**Challenge:** Requires skill and luck

---

**Quest 3.3: Survival Champion**
- **Type:** CHALLENGE
- **Difficulty:** ADVANCED
- **Prerequisite:** Quest 3.1

**Objectives:**
1. Complete a survival mode round (mob hazards)
2. Complete with limited lives (3 lives)
3. Finish under par

**Rewards:**
- 600 XP
- Survival Champion title
- Special survival disc (mob-resistant)
- Access to boss holes

**Location:** Special survival course with mob hazards

---

### **Chapter 4: The Legend**

**Quest 4.1: The Grand Tournament**
- **Type:** TOURNAMENT
- **Difficulty:** EXPERT
- **Prerequisite:** Complete Chapter 3

**Objectives:**
1. Win the regional tournament
2. Win the national tournament
3. Win the world championship

**Rewards:**
- 1000 XP
- Legend title
- Championship belt (decorative)
- Custom course creation ability
- Disc golf legend status

**Location:** Multi-stage tournament across multiple courses

---

**Quest 4.2: Course Architect**
- **Type:** SKILL
- **Difficulty:** EXPERT
- **Prerequisite:** Quest 4.1

**Objectives:**
1. Design and build a 9-hole course
2. Have 5 other players complete your course
3. Average rating of 4+ stars from players

**Rewards:**
- 800 XP
- Course architect title
- Advanced course building tools
- Course publishing rights

**Integration:** Uses existing course placement system

---

**Quest 4.3: The Ultimate Challenge**
- **Type:** CHALLENGE
- **Difficulty:** MASTER
- **Prerequisite:** Quest 4.1

**Objectives:**
1. Complete the "Impossible Course" (extremely difficult)
2. Complete without any strokes over par
3. Complete within 30 minutes

**Rewards:**
- 2000 XP
- Master title
- Eternal Disc (legendary item)
- Status as server legend

**Location:** Custom-designed ultimate challenge course

---

## Daily Quest System

### **Daily Quest Types**

**Skill Practice:**
- "Practice Stance: Complete 5 holes with Backhand stance"
- "Angle Mastery: Use each release angle in one round"
- "Distance Training: Achieve 400+ ft throw"

**Course Variety:**
- "Biome Tour: Complete courses in 3 different biomes"
- "Course Explorer: Discover a new course"
- "Quick Round: Complete a 3-hole course under 15 minutes"

**Challenge Runs:**
- "Under Par: Complete a round under par"
- "No Bogeys: Complete a round without any bogeys"
- "Speed Golf: Complete 9 holes in under 20 minutes"

**Community:**
- "Social Play: Complete a round with another player"
- "Tournament Participant: Join any tournament"
- "Course Review: Rate a course you've played"

### **Daily Quest Rewards**

**Standard Rewards:**
- 25-50 XP per quest
- 1-3 emeralds
- Rare disc enchantment books
- Disc repair materials

**Bonus Rewards (weekly streak):**
- 7-day streak: Special cosmetic item
- 14-day streak: Rare disc mold
- 30-day streak: Legendary disc fragment

---

## Technical Implementation

### **Quest Storage**

```java
public final class QuestStorage {
    private static final String QUESTS_DIR = "data/mcdg/quests/";
    
    public static void savePlayerQuests(UUID playerId) {
        QuestState state = QuestManager.getPlayerState(playerId);
        String json = serializeQuestState(state);
        Path path = Paths.get(QUESTS_DIR + playerId + ".json");
        Files.writeString(path, json);
    }
    
    public static QuestState loadPlayerQuests(UUID playerId) {
        Path path = Paths.get(QUESTS_DIR + playerId + ".json");
        if (Files.exists(path)) {
            String json = Files.readString(path);
            return deserializeQuestState(json);
        }
        return createInitialState(playerId);
    }
}
```

### **Quest UI Integration**

**Quest Journal Screen:**
```java
public class QuestJournalScreen extends Screen {
    private final List<Quest> availableQuests;
    private final List<Quest> activeQuests;
    private final List<Quest> completedQuests;
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render quest list with tabs for Available/Active/Completed
        // Show quest details, objectives, rewards
        // Track progress bars for objectives
    }
}
```

**Quest Tracker HUD:**
```java
public static void renderQuestTracker(DrawContext context) {
    QuestState activeQuest = getActiveQuest();
    if (activeQuest != null) {
        // Show current quest objectives
        // Show progress bars
        // Show quest timer for time-limited quests
    }
}
```

### **Quest Discovery System**

**World Generation:**
```java
public class QuestGiverPlacement {
    public static void placeQuestGiver(ServerWorld world, BlockPos pos, Quest quest) {
        // Place custom NPC or special villager
        // Add quest marker block
        // Store quest association in block entity
    }
}
```

**Visual Markers:**
- Special flowers or blocks marking quest locations
- Particle effects indicating nearby quests
- Map markers for discovered quest locations
- Waypoint integration for quest objectives

### **Quest Event Hooks**

**Integration with Existing Systems:**

```java
// In HoleProgressTracker.java
public void onHoleComplete(UUID playerId, HoleScore score) {
    QuestManager.updateObjective(playerId, 
        QuestObjectiveType.COMPLETE_HOLE_UNDER_PAR,
        Map.of("score", score.strokes(), "par", score.par()));
}

// In ChargedDiscItem.java
public void onThrowComplete(UUID playerId, ThrowResult result) {
    QuestManager.updateObjective(playerId,
        QuestObjectiveType.THROW_DISTANCE,
        Map.of("distance", result.distanceFt()));
}

// In CoursePlacementService.java
public void onCourseGenerated(UUID playerId, Course course) {
    QuestManager.updateObjective(playerId,
        QuestObjectiveType.DISCOVER_COURSE,
        Map.of("courseId", course.courseId()));
}
```

---

## Quest Giver System

### **NPC Quest Givers**

```java
public class DiscGolfNPC {
    private final String name;
    private final String dialogue;
    private final List<Quest> availableQuests;
    private final BlockPos position;
    
    public void interact(ServerPlayerEntity player) {
        // Show dialogue
        player.sendMessage(Text.literal(dialogue));
        
        // Offer available quests
        for (Quest quest : availableQuests) {
            if (QuestManager.canStartQuest(player.getUuid(), quest)) {
                offerQuest(player, quest);
            }
        }
    }
}
```

### **Villager Integration**

**Custom Villager Profession:**
```java
public class DiscGolfVillager {
    public static final VillagerProfession DISC_GOLF_PRO = VillagerProfession.register(
        new Identifier("mcdg:disc_golf"),
        pointOfInterestType -> true,
        ImmutableSet.of()
    );
}
```

**Villager Trades:**
- Disc items
- Enchantment books
- Course maps
- Quest items

---

## Quest Progression Flow

### **Linear Progression (Main Story)**
```
Chapter 1 (Beginner) → Chapter 2 (Intermediate) → Chapter 3 (Advanced) → Chapter 4 (Expert)
```

### **Branching Progression (Skill Paths)**
```
Main Story → Choose Path:
├── Power Path (distance-focused quests)
├── Precision Path (accuracy-focused quests)
├── Technical Path (stance/angle mastery)
└── Explorer Path (course discovery quests)
```

### **Optional Content**
- Daily quests (always available)
- Challenge quests (optional, high difficulty)
- Tournament quests (scheduled events)
- Community quests (special events)

---

## Reward System

### **Reward Types**

**Progression Rewards:**
- Disc tier unlocks
- Enchantment unlocks
- Recipe unlocks
- Ability unlocks

**Cosmetic Rewards:**
- Titles (prefix before name)
- Custom disc skins
- Particle effects
- Cosmetic items

**Practical Rewards:**
- XP boosts
- Emeralds
- Rare materials
- Special items

**Unlock Rewards:**
- Course access
- Game modes (survival mode, boss holes)
- Building tools
- Customization options

### **Reward Tiers**

**Common Rewards:**
- 25-100 XP
- 1-5 emeralds
- Basic enchantments
- Common materials

**Uncommon Rewards:**
- 100-300 XP
- 5-15 emeralds
- Rare enchantments
- Uncommon materials

**Rare Rewards:**
- 300-600 XP
- 15-30 emeralds
- Epic enchantments
- Rare materials
- Cosmetic items

**Legendary Rewards:**
- 600-2000 XP
- 30+ emeralds
- Unique items
- Special abilities
- Titles

---

## UI/UX Design

### **Quest Journal**

**Tabs:**
- Available (quests that can be started)
- Active (currently in progress)
- Completed (finished quests)
- Daily (repeatable daily quests)

**Quest Details:**
- Quest name and description
- Objectives with progress bars
- Rewards preview
- Prerequisites
- Time remaining (for timed quests)

**Quest Tracker:**
- HUD overlay showing active quest
- Objective progress
- Distance to quest objectives
- Time remaining

### **Discovery Experience**

**Visual Cues:**
- Special blocks/markers for quest locations
- Particle effects for nearby quests
- Map markers for discovered quests
- Compass direction to active quest

**Dialogue System:**
- NPC dialogue for quest introduction
- Context-sensitive dialogue based on progress
- Quest acceptance/decline options
- Progress updates from quest givers

---

## Integration with Existing Systems

### **Course System Integration**

**Quest-Generated Courses:**
- Special courses for specific quests
- Hidden courses for exploration quests
- Challenge courses for difficult quests
- Tournament courses for events

**Course Catalog Integration:**
- Quest-discovered courses added to catalog
- Quest rewards include course access
- Quest progress tied to course completion

### **Scoring System Integration**

**Quest Objectives:**
- Score-based objectives (under par, total score)
- Stance-based objectives (use specific stances)
- Performance objectives (aces, birdies)

**Leaderboard Integration:**
- Quest completion on leaderboards
- Special quest leaderboards
- Tournament quest rankings

### **Survival Integration**

**Survival Mode Quests:**
- Survival mode round objectives
- Mob hazard challenges
- Limited survival quests

**Crafting Integration:**
- Disc crafting quests
- Material gathering quests
- Equipment upgrade quests

---

## Configuration and Customization

### **Server Configuration**

```java
public record QuestConfig(
    boolean enableQuestSystem,
    boolean enableDailyQuests,
    boolean enableTournamentQuests,
    int dailyQuestCount,
    int dailyQuestResetHour,
    double questXPMultiplier,
    boolean enableQuestMarkers,
    boolean enableQuestTracking
)
```

### **Quest Difficulty Scaling**

**Server-Wide Settings:**
- Adjust quest difficulty based on player count
- Scale rewards based on difficulty
- Customize quest availability

**Player-Specific Settings:**
- Difficulty preferences
- Quest type preferences
- Notification settings

---

## Performance Considerations

### **Optimization Strategies**

**Quest State Management:**
- Lazy loading of quest data
- Efficient state serialization
- Periodic cleanup of completed quests

**Event System:**
- Efficient event hooks for quest updates
- Batch processing of quest progress
- Minimal overhead during gameplay

**UI Performance:**
- Cached quest data
- Lazy rendering of quest lists
- Efficient progress bar updates

---

## Testing Strategy

### **Unit Tests**

**Quest Logic Tests:**
- Quest completion conditions
- Objective progress calculation
- Prerequisite validation
- Reward distribution

### **Integration Tests**

**System Integration:**
- Quest system with course completion
- Quest system with scoring
- Quest system with survival mode

### **Playtesting**

**User Experience:**
- Quest flow and progression
- Reward balance
- Difficulty scaling
- Discovery experience

---

## Implementation Timeline

### **Phase 1: Core Quest System (8-12 hours)**
- Quest data model and storage
- Quest manager and state management
- Basic quest UI (journal, tracker)
- Quest discovery system

### **Phase 2: Quest Content (12-16 hours)**
- Main storyline quests (Chapters 1-4)
- Daily quest system
- Quest giver NPCs
- Reward system

### **Phase 3: Integration (6-8 hours)**
- Integration with existing systems
- Event hooks for quest progress
- UI polish and optimization
- Configuration system

### **Phase 4: Testing and Balancing (4-6 hours)**
- Quest flow testing
- Reward balancing
- Performance optimization
- Bug fixes and polish

**Total Estimated Time:** 30-42 hours

---

## Future Enhancements

### **Advanced Features**

**Multiplayer Quests:**
- Cooperative quests (complete with friends)
- Competitive quests (race to complete)
- Team quests (group objectives)

**Seasonal Content:**
- Seasonal quest lines
- Holiday events
- Limited-time quests

**Player-Created Quests:**
- Quest editor for players
- Community quest sharing
- Rating system for player quests

**Achievement System:**
- Achievements tied to quests
- Achievement points
- Achievement rewards

---

## Success Criteria

### **Functional Requirements**
- [x] Quest system integrates seamlessly with existing MCDG systems
- [x] Quest progression feels natural and rewarding
- [x] Rewards are meaningful and balanced
- [x] Discovery system is intuitive
- [x] UI is clear and responsive

### **User Experience Requirements**
- [x] Quests enhance rather than disrupt disc golf gameplay
- [x] Progression feels earned and satisfying
- [x] Multiple playstyles supported
- [x] Replayability through daily quests
- [x] Clear goals and objectives

### **Technical Requirements**
- [x] No performance degradation
- [x] Stable quest state management
- [x] Robust error handling
- [x] Scalable to many quests
- [x] Easy to add new content

---

## Conclusion

This questline system transforms MCDG from a standalone disc golf mod into an integrated survival progression system. Players advance through chapters by developing disc golf skills, exploring the world, and completing challenges, unlocking new abilities and content along the way.

The system is designed to:
- Enhance rather than replace core disc golf gameplay
- Provide meaningful progression and rewards
- Support different playstyles and skill levels
- Integrate naturally with existing MCDG systems
- Offer replayability through daily and optional quests

The questline gives players long-term goals while maintaining the authentic disc golf simulation that makes MCDG unique.
