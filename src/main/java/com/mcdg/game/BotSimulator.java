package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates bot players for multiplayer testing.
 * Bots exist only as UUIDs in the state management layer, not as Minecraft entities.
 */
public final class BotSimulator {
    private static final Map<UUID, BotProfile> BOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BOT_THROW_TIMERS = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();
    
    // Bot skill levels affect throw accuracy and power
    public enum BotSkill {
        BEGINNER(0.6f, 0.7f, 200),   // Lower power, less accurate, slower throws
        INTERMEDIATE(0.8f, 0.85f, 150), // Moderate power, decent accuracy
        PRO(1.0f, 0.95f, 100);        // Full power, highly accurate, fast throws
        
        private final float powerMultiplier;
        private final float accuracy;
        private final int throwIntervalTicks;
        
        BotSkill(float powerMultiplier, float accuracy, int throwIntervalTicks) {
            this.powerMultiplier = powerMultiplier;
            this.accuracy = accuracy;
            this.throwIntervalTicks = throwIntervalTicks;
        }
        
        public float powerMultiplier() {
            return powerMultiplier;
        }
        
        public float accuracy() {
            return accuracy;
        }
        
        public int throwIntervalTicks() {
            return throwIntervalTicks;
        }
    }
    
    public record BotProfile(
        UUID uuid,
        String name,
        BotSkill skill
    ) {}
    
    private BotSimulator() {
        // Utility class
    }
    
    /**
     * Register a new bot player.
     */
    public static UUID addBot(String name, BotSkill skill) {
        UUID botUuid = UUID.randomUUID();
        BOTS.put(botUuid, new BotProfile(botUuid, name, skill));
        McdgMod.LOGGER.info("Bot registered: {} ({})", name, botUuid);
        return botUuid;
    }
    
    /**
     * Remove a bot player.
     */
    public static void removeBot(UUID botUuid) {
        BotProfile removed = BOTS.remove(botUuid);
        if (removed != null) {
            BOT_THROW_TIMERS.remove(botUuid);
            McdgMod.LOGGER.info("Bot removed: {} ({})", removed.name(), botUuid);
        }
    }
    
    /**
     * Get all registered bots.
     */
    public static Map<UUID, BotProfile> getBots() {
        return Map.copyOf(BOTS);
    }
    
    /**
     * Check if a UUID is a bot.
     */
    public static boolean isBot(UUID uuid) {
        return BOTS.containsKey(uuid);
    }
    
    /**
     * Get bot profile by UUID.
     */
    public static Optional<BotProfile> getBotProfile(UUID uuid) {
        return Optional.ofNullable(BOTS.get(uuid));
    }
    
    /**
     * Clear all bots.
     */
    public static void clearAllBots() {
        int count = BOTS.size();
        BOTS.clear();
        BOT_THROW_TIMERS.clear();
        McdgMod.LOGGER.info("Cleared {} bots", count);
    }
    
    /**
     * Tick handler for bot throw simulation.
     * Called from HoleProgressTracker during server tick.
     */
    public static void tick(
        MinecraftServer server,
        ActiveCourseManager courseManager,
        RoundStateManager roundStateManager
    ) {
        if (!courseManager.isRoundActive()) {
            BOT_THROW_TIMERS.clear();
            return;
        }
        
        Optional<Course> courseOpt = courseManager.getActiveCourse();
        Optional<PlacedCourseState> placedOpt = courseManager.getPlacedCourseState();
        
        if (courseOpt.isEmpty() || placedOpt.isEmpty()) {
            return;
        }
        
        Course course = courseOpt.get();
        PlacedCourseState placed = placedOpt.get();
        ServerWorld world = server.getWorld(placed.worldKey());
        
        if (world == null) {
            return;
        }
        
        // Process each bot
        for (Map.Entry<UUID, BotProfile> entry : BOTS.entrySet()) {
            UUID botUuid = entry.getKey();
            BotProfile bot = entry.getValue();
            
            try {
                // Check if bot is an active participant
                if (!courseManager.getActiveParticipantIds().contains(botUuid)) {
                    continue;
                }
                
                // Get bot's current state
                Optional<PlayerRoundState> stateOpt = roundStateManager.getState(botUuid);
                if (stateOpt.isEmpty()) {
                    continue;
                }
                
                PlayerRoundState state = stateOpt.get();
                
                // Safety check for hole index
                if (state.currentHole() < 1 || state.currentHole() > course.holes().size()) {
                    McdgMod.LOGGER.warn("Bot {} has invalid hole index: {}", bot.name(), state.currentHole());
                    continue;
                }
                
                Hole currentHole = course.holes().get(state.currentHole() - 1);
                BlockPos basket = placed.holeBaskets().get(state.currentHole());
                BlockPos tee = placed.holeTees().get(state.currentHole());
                
                if (basket == null || tee == null) {
                    McdgMod.LOGGER.warn("Bot {} missing basket or tee for hole {}", bot.name(), state.currentHole());
                    continue;
                }
                
                // Check if bot already at basket (hole complete)
                double distanceToBasket = DistanceUtils.distanceMeters(state.lie(), basket);
                if (distanceToBasket < 3.0) { // Increased from 2.0 to 3.0 meters
                    // Record hole score for scoreboard display
                    int holeScore = state.holeStrokes();
                    HoleProgressTracker.recordHoleScoreForBot(botUuid, state.currentHole(), holeScore);
                    
                    // Bot completed the hole, advance to next
                    BlockPos nextTee = placed.holeTees().get(state.currentHole() + 1);
                    if (nextTee != null) {
                        roundStateManager.advanceToNextHole(botUuid, nextTee);
                        McdgMod.LOGGER.info("Bot {} completed hole {} in {} strokes (distance: {}m), advancing to hole {}", 
                            bot.name(), state.currentHole(), holeScore, Math.round(distanceToBasket), state.currentHole() + 1);
                    } else {
                        // No next hole, bot finished the round
                        McdgMod.LOGGER.info("Bot {} completed final hole {} in {} strokes", bot.name(), state.currentHole(), holeScore);
                        roundStateManager.recordCompletedRound(botUuid, state.totalStrokes());
                        roundStateManager.clearPlayer(botUuid);
                    }
                    continue;
                }
                
                // Safety: prevent infinite throwing on same hole
                if (state.holeStrokes() > 15) {
                    McdgMod.LOGGER.warn("Bot {} exceeded max strokes on hole {}, forcing completion", bot.name(), state.currentHole());
                    // Force advance to next hole
                    BlockPos nextTee = placed.holeTees().get(state.currentHole() + 1);
                    if (nextTee != null) {
                        roundStateManager.advanceToNextHole(botUuid, nextTee);
                    }
                    continue;
                }
                
                // Increment throw timer
                int timer = BOT_THROW_TIMERS.getOrDefault(botUuid, 0);
                timer++;
                BOT_THROW_TIMERS.put(botUuid, timer);
                
                // Check if bot should throw
                if (timer >= bot.skill().throwIntervalTicks()) {
                    BOT_THROW_TIMERS.put(botUuid, 0);
                    simulateThrow(server, world, bot, state, currentHole, tee, basket, roundStateManager, distanceToBasket);
                }
            } catch (Exception e) {
                McdgMod.LOGGER.error("Error processing bot {}: {}", bot.name(), e.getMessage(), e);
                // Continue to next bot instead of crashing the server
            }
        }
    }
    
    /**
     * Simulate a bot throw using trajectory calculation.
     * TEMPORARILY DISABLED - using simple fallback to prevent server crashes
     */
    private static void simulateThrow(
        MinecraftServer server,
        ServerWorld world,
        BotProfile bot,
        PlayerRoundState state,
        Hole currentHole,
        BlockPos tee,
        BlockPos basket,
        RoundStateManager roundStateManager,
        double distanceToBasket
    ) {
        try {
            BlockPos currentLie = state.lie();
            
            // TEMPORARY: Use simple fallback instead of trajectory calculation
            // The trajectory calculation may be causing server freezes
            BlockPos fallbackLie = calculateFallbackLie(currentLie, basket, bot.skill());
            roundStateManager.recordThrow(bot.uuid(), fallbackLie);
            
            McdgMod.LOGGER.info(
                "Bot {} threw (simple): distance={}m, skill={}",
                bot.name(),
                Math.round(distanceToBasket),
                bot.skill()
            );
            
        } catch (Exception e) {
            McdgMod.LOGGER.error("Bot {} throw simulation failed: {}", bot.name(), e.getMessage(), e);
        }
    }
    
    /**
     * Calculate throw parameters based on bot skill and hole layout.
     */
    private static ThrowParameters calculateThrowParameters(
        BotProfile bot,
        BlockPos from,
        BlockPos to,
        double distanceMeters
    ) {
        // Calculate yaw to face basket
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        
        // Calculate pitch (slight upward angle for distance)
        float pitch = -10.0f; // Slight upward angle
        
        // Determine charge based on distance and skill
        double distanceFeet = distanceMeters * 3.28084;
        float charge = calculateCharge(distanceFeet, bot.skill());
        
        // Select stance based on distance and skill
        ThrowStance stance = selectStance(distanceFeet, bot.skill());
        
        // Select angle based on skill and random variation
        ReleaseAngle angle = selectAngle(bot.skill());
        
        return new ThrowParameters(yaw, pitch, charge, stance, angle);
    }
    
    /**
     * Calculate throw charge based on distance and skill.
     */
    private static float calculateCharge(double distanceFeet, BotSkill skill) {
        // Base charge needed for distance
        float baseCharge = (float) Math.min(1.0, distanceFeet / 600.0);
        
        // Apply skill multiplier and add variation
        float skillCharge = baseCharge * skill.powerMultiplier();
        float variation = (RANDOM.nextFloat() - 0.5f) * 0.2f * (1.0f - skill.accuracy());
        
        return MathHelper.clamp(skillCharge + variation, 0.3f, 1.25f);
    }
    
    /**
     * Select throw stance based on distance and skill.
     */
    private static ThrowStance selectStance(double distanceFeet, BotSkill skill) {
        // Beginners use overhand for simplicity
        if (skill == BotSkill.BEGINNER && distanceFeet < 200) {
            return ThrowStance.OVERHAND;
        }
        
        // Intermediate and pro use backhand/forehand based on distance
        if (distanceFeet > 300) {
            return RANDOM.nextBoolean() ? ThrowStance.BACKHAND : ThrowStance.FOREHAND;
        }
        
        // Short to medium throws: mix of stances
        ThrowStance[] stances = ThrowStance.values();
        return stances[RANDOM.nextInt(stances.length)];
    }
    
    /**
     * Select release angle based on skill.
     */
    private static ReleaseAngle selectAngle(BotSkill skill) {
        // Higher skill = more likely to use flat (accurate)
        if (skill == BotSkill.PRO && RANDOM.nextFloat() < 0.7f) {
            return ReleaseAngle.FLAT;
        }
        
        // Random selection with skill bias
        ReleaseAngle[] angles = ReleaseAngle.values();
        return angles[RANDOM.nextInt(angles.length)];
    }
    
    /**
     * Calculate fallback lie if trajectory calculation fails.
     * More aggressive to ensure bots eventually reach the basket.
     */
    private static BlockPos calculateFallbackLie(BlockPos from, BlockPos to, BotSkill skill) {
        double distance = DistanceUtils.distanceMeters(from, to);
        double horizontalDistance = Math.sqrt(
            Math.pow(to.getX() - from.getX(), 2) +
            Math.pow(to.getZ() - from.getZ(), 2)
        );

        // If very close (or directly under/over basket), go directly to basket
        if (distance <= 5.0 || horizontalDistance <= 2.0) {
            return to;
        }

        // Move 50-80% of the way toward basket based on skill (more aggressive)
        double progress = 0.5 + (RANDOM.nextFloat() * 0.3 * skill.powerMultiplier());

        int newX = from.getX() + (int) Math.round((to.getX() - from.getX()) * progress);
        int newZ = from.getZ() + (int) Math.round((to.getZ() - from.getZ()) * progress);
        // Interpolate Y as well to avoid getting stuck vertically under basket pedestals
        int newY = from.getY() + (int) Math.round((to.getY() - from.getY()) * progress);

        return new BlockPos(newX, newY, newZ);
    }
    
    /**
     * Reset bot state (called when round ends).
     */
    public static void reset() {
        BOT_THROW_TIMERS.clear();
    }
    
    /**
     * Throw parameters record.
     */
    private record ThrowParameters(
        float yaw,
        float pitch,
        float charge,
        ThrowStance stance,
        ReleaseAngle angle
    ) {}
}
