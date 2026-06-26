package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.net.WindSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages wind state per world, providing wind query interface for physics engines.
 * Supports manual wind control, natural wind generation, and tournament modes.
 */
public final class WindManager {
    
    // Per-world wind state storage
    private static final ConcurrentHashMap<Identifier, WindState> WORLD_WIND_STATES = new ConcurrentHashMap<>();

    // Saved wind state before a round started, used to restore conditions after the round
    private static final ConcurrentHashMap<Identifier, WindState> PRE_ROUND_WIND_STATES = new ConcurrentHashMap<>();

    // Tracks worlds where an admin has manually set wind, disabling round lifecycle automation
    private static final ConcurrentHashMap<Identifier, Boolean> MANUAL_OVERRIDES = new ConcurrentHashMap<>();

    // Configuration (will be set from McdgConfig)
    private static boolean windSystemEnabled = true;
    private static double defaultWindSpeed = 0.2;
    private static int windUpdateIntervalTicks = 200;
    
    private WindManager() {
        // Utility class
    }
    
    /**
     * Initialize wind manager with configuration values.
     */
    public static void initialize(boolean enabled, double defaultSpeed, int updateInterval) {
        windSystemEnabled = enabled;
        defaultWindSpeed = defaultSpeed;
        windUpdateIntervalTicks = updateInterval;
        McdgMod.LOGGER.info("WindManager initialized | enabled={} defaultSpeed={} updateInterval={} ticks", 
                          enabled, defaultSpeed, updateInterval);
    }
    
    /**
     * Get current wind state for a world.
     * Returns calm wind if system is disabled or no wind is set.
     */
    public static WindState getWindState(ServerWorld world) {
        if (!windSystemEnabled) {
            return WindState.calm();
        }
        
        Identifier worldId = world.getRegistryKey().getValue();
        return WORLD_WIND_STATES.getOrDefault(worldId, WindState.calm());
    }
    
    /**
     * Set wind mode for a world.
     */
    public static void setWindMode(ServerWorld world, WindMode mode) {
        Identifier worldId = world.getRegistryKey().getValue();
        WindState current = WORLD_WIND_STATES.get(worldId);
        
        WindState newState;
        switch (mode) {
            case CALM:
                newState = WindState.calm();
                break;
            case NATURAL:
                // Generate initial natural wind
                newState = generateNaturalWind(world, current);
                break;
            case FIXED:
                // Keep current wind if already fixed, otherwise use default
                if (current != null && current.mode() == WindMode.FIXED) {
                    newState = current;
                } else {
                    newState = WindState.fixed(defaultWindSpeed, 0.0f);
                }
                break;
            case TOURNAMENT:
                // Tournament mode requires explicit tournament ID setup
                // Fall back to calm for now
                newState = WindState.calm();
                break;
            default:
                newState = WindState.calm();
        }
        
        WORLD_WIND_STATES.put(worldId, newState);
        setManualOverride(worldId, true);

        // Broadcast wind update to all players
        broadcastWindUpdate(world, newState);

        McdgMod.LOGGER.info("Wind mode set | world={} mode={} speed={} direction={}°", 
                          worldId, mode, newState.speed(), newState.directionDegrees());
    }

    /**
     * Set manual wind for a world (FIXED mode).
     */
    public static void setManualWind(ServerWorld world, double speed, float directionDegrees) {
        Identifier worldId = world.getRegistryKey().getValue();
        WindState newState = WindState.fixed(speed, directionDegrees);
        WORLD_WIND_STATES.put(worldId, newState);
        setManualOverride(worldId, true);

        // Broadcast wind update to all players
        broadcastWindUpdate(world, newState);

        McdgMod.LOGGER.info("Manual wind set | world={} speed={} direction={}°", 
                          worldId, speed, directionDegrees);
    }

    /**
     * Set a wind state directly without marking it as a manual override.
     * Used by round lifecycle automation so it does not disable itself.
     */
    public static void setWorldWindState(ServerWorld world, WindState wind) {
        Identifier worldId = world.getRegistryKey().getValue();
        WORLD_WIND_STATES.put(worldId, wind);
        broadcastWindUpdate(world, wind);
    }

    /**
     * Save the current wind state for a world so it can be restored after a round.
     */
    public static void saveWorldWind(ServerWorld world) {
        Identifier worldId = world.getRegistryKey().getValue();
        PRE_ROUND_WIND_STATES.put(worldId, getWindState(world));
    }

    /**
     * Restore the wind state saved before the round and drop the saved snapshot.
     */
    public static void restoreWorldWind(ServerWorld world) {
        Identifier worldId = world.getRegistryKey().getValue();
        WindState saved = PRE_ROUND_WIND_STATES.remove(worldId);
        if (saved != null) {
            setWorldWindState(world, saved);
            McdgMod.LOGGER.info("Restored pre-round wind | world={}", worldId);
        }
    }

    /**
     * Mark whether a world has a manual wind override, disabling round automation.
     */
    public static void setManualOverride(Identifier worldId, boolean overridden) {
        MANUAL_OVERRIDES.put(worldId, overridden);
    }

    /**
     * Check if a world currently has a manual wind override active.
     */
    public static boolean isManualOverride(Identifier worldId) {
        return Boolean.TRUE.equals(MANUAL_OVERRIDES.get(worldId));
    }

    /**
     * Clear the manual override flag for a world.
     */
    public static void clearManualOverride(Identifier worldId) {
        MANUAL_OVERRIDES.remove(worldId);
    }

    /**
     * Set tournament wind for a specific tournament.
     */
    public static void setTournamentWind(UUID tournamentId, WindState wind) {
        // Tournament wind management will be implemented in Phase 4
        // For now, this is a placeholder
        McdgMod.LOGGER.info("Tournament wind set | tournamentId={} speed={} direction={}°", 
                          tournamentId, wind.speed(), wind.directionDegrees());
    }
    
    /**
     * Clear tournament wind.
     */
    public static void clearTournamentWind(UUID tournamentId) {
        // Tournament wind management will be implemented in Phase 4
        McdgMod.LOGGER.info("Tournament wind cleared | tournamentId={}", tournamentId);
    }
    
    /**
     * Main tick handler - updates wind state at configured intervals.
     * Registered on ServerTickEvents.END_SERVER_TICK in McdgMod.
     */
    public static void tick(MinecraftServer server) {
        if (!windSystemEnabled) {
            return;
        }
        
        long currentTick = server.getTicks();
        
        // Only update wind at configured intervals
        if (currentTick % windUpdateIntervalTicks != 0) {
            return;
        }
        
        // Update natural wind for all worlds
        for (ServerWorld world : server.getWorlds()) {
            Identifier worldId = world.getRegistryKey().getValue();
            WindState current = WORLD_WIND_STATES.get(worldId);
            
            if (current != null && current.mode() == WindMode.NATURAL) {
                WindState updated = generateNaturalWind(world, current);
                WORLD_WIND_STATES.put(worldId, updated);
                
                // Broadcast wind update to all players in this world
                broadcastWindUpdate(world, updated);
                
                // Log wind changes (occasional, not every update)
                if (currentTick % (windUpdateIntervalTicks * 5) == 0) {
                    McdgMod.LOGGER.debug("Natural wind updated | world={} speed={} direction={}°", 
                                       worldId, updated.speed(), updated.directionDegrees());
                }
            }
        }
    }
    
    /**
     * Broadcast wind state update to all players in a world.
     */
    private static void broadcastWindUpdate(ServerWorld world, WindState wind) {
        WindSync.Payload payload = new WindSync.Payload(
            wind.velocity(),
            wind.speed(),
            wind.directionDegrees(),
            wind.mode(),
            wind.isGusting()
        );
        
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
    
    /**
     * Generate natural wind based on world conditions.
     * Phase 2: Includes biome, weather, and time modifiers with smoothed direction changes.
     * Package-private so RoundWindService can generate round-scoped natural wind.
     */
    static WindState generateNaturalWind(ServerWorld world, WindState previousWind) {
        long currentTick = world.getServer().getTicks();
        
        // Get biome modifier at spawn position (representative location)
        BlockPos spawnPos = world.getSpawnPos();
        RegistryEntry<Biome> biomeEntry = world.getBiome(spawnPos);
        double biomeModifier = getBiomeWindModifier(biomeEntry);
        
        // Weather modifier
        double weatherModifier = 1.0;
        if (world.isRaining()) {
            weatherModifier = 1.5;
        }
        if (world.isThundering()) {
            weatherModifier = 2.5;
        }
        
        // Time modifier (day = calmer, night = windier)
        double timeModifier = world.isDay() ? 0.8 : 1.2;
        
        // Base speed with random variation
        double baseSpeed = 0.1 + (world.random.nextDouble() * 0.3);
        double speed = Math.min(1.0, baseSpeed * biomeModifier * weatherModifier * timeModifier);
        
        // Direction with smoothing (avoid sudden 180° flips)
        float direction;
        if (previousWind == null || previousWind.mode() != WindMode.NATURAL) {
            // Generate new direction for first natural wind
            direction = world.random.nextFloat() * 360.0f;
        } else {
            // Smooth transition from previous direction
            float targetDirection = world.random.nextFloat() * 360.0f;
            direction = smoothDirectionTransition(previousWind.directionDegrees(), targetDirection, 0.1f);
        }
        
        // Gusting behavior (20% chance of gust event)
        boolean isGusting = world.random.nextFloat() < 0.2;
        if (isGusting) {
            speed *= 1.3; // 30% speed increase during gusts
        }
        
        return new WindState(
            WindState.calculateVelocity(speed, direction),
            speed,
            direction,
            WindMode.NATURAL,
            isGusting,
            currentTick,
            null
        );
    }
    
    /**
     * Get biome-based wind modifier.
     * Open areas = windier, forests = calmer, mountains = very windy.
     */
    private static double getBiomeWindModifier(RegistryEntry<Biome> biomeEntry) {
        // Use biome tags to determine wind modifier
        if (biomeEntry.isIn(BiomeTags.IS_OCEAN)) {
            return 1.3; // Windier
        } else if (biomeEntry.isIn(BiomeTags.IS_FOREST) || 
                   biomeEntry.isIn(BiomeTags.IS_JUNGLE)) {
            return 0.7; // Calmer
        } else if (biomeEntry.isIn(BiomeTags.IS_MOUNTAIN) || 
                   biomeEntry.isIn(BiomeTags.IS_HILL)) {
            return 1.5; // Very windy
        }
        
        // Use string matching for biomes without tags
        String biomeId = biomeEntry.getKey().map(key -> key.getValue().toString()).orElse("");
        if (biomeId.contains("plains") || biomeId.contains("savanna")) {
            return 1.3; // Windier
        } else if (biomeId.contains("desert")) {
            return 1.2; // Moderately windy
        } else if (biomeId.contains("swamp")) {
            return 0.8; // Slightly calmer
        }
        
        return 1.0; // Neutral
    }
    
    /**
     * Smooth direction transition to avoid sudden wind direction changes.
     * Interpolates between previous and target direction by the given factor.
     */
    private static float smoothDirectionTransition(float previousDirection, float targetDirection, float factor) {
        // Handle wrap-around (e.g., transitioning from 350° to 10° should be +20°, not -340°)
        float diff = targetDirection - previousDirection;
        
        // Normalize difference to [-180, 180]
        while (diff > 180.0f) {
            diff -= 360.0f;
        }
        while (diff < -180.0f) {
            diff += 360.0f;
        }
        
        // Apply smoothing factor
        float newDirection = previousDirection + (diff * factor);
        
        // Normalize to [0, 360)
        while (newDirection < 0.0f) {
            newDirection += 360.0f;
        }
        while (newDirection >= 360.0f) {
            newDirection -= 360.0f;
        }
        
        return newDirection;
    }
    
    /**
     * Clear all wind states (called on server shutdown).
     */
    public static void reset() {
        int count = WORLD_WIND_STATES.size();
        WORLD_WIND_STATES.clear();
        PRE_ROUND_WIND_STATES.clear();
        MANUAL_OVERRIDES.clear();
        McdgMod.LOGGER.info("WindManager reset | cleared {} world wind states", count);
    }
    
    /**
     * Check if wind system is enabled.
     */
    public static boolean isEnabled() {
        return windSystemEnabled;
    }
}
