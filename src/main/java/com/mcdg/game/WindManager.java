package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages wind state per world, providing wind query interface for physics engines.
 * Supports manual wind control, natural wind generation, and tournament modes.
 */
public final class WindManager {
    
    // Per-world wind state storage
    private static final ConcurrentHashMap<Identifier, WindState> WORLD_WIND_STATES = new ConcurrentHashMap<>();
    
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
        McdgMod.LOGGER.info("Manual wind set | world={} speed={} direction={}°", 
                          worldId, speed, directionDegrees);
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
                
                // Log wind changes (occasional, not every update)
                if (currentTick % (windUpdateIntervalTicks * 5) == 0) {
                    McdgMod.LOGGER.debug("Natural wind updated | world={} speed={} direction={}°", 
                                       worldId, updated.speed(), updated.directionDegrees());
                }
            }
        }
    }
    
    /**
     * Generate natural wind based on world conditions.
     * Phase 2 will add biome, weather, and time modifiers.
     * For now, uses simple random generation.
     */
    private static WindState generateNaturalWind(ServerWorld world, WindState previousWind) {
        // Simple random wind for Phase 1
        // Phase 2 will add biome, weather, and time modifiers
        double speed = 0.1 + (world.random.nextDouble() * 0.3);
        float direction = world.random.nextFloat() * 360.0f;
        
        return new WindState(
            WindState.calculateVelocity(speed, direction),
            speed,
            direction,
            WindMode.NATURAL,
            false,
            world.getServer().getTicks(),
            null
        );
    }
    
    /**
     * Clear all wind states (called on server shutdown).
     */
    public static void reset() {
        int count = WORLD_WIND_STATES.size();
        WORLD_WIND_STATES.clear();
        McdgMod.LOGGER.info("WindManager reset | cleared {} world wind states", count);
    }
    
    /**
     * Check if wind system is enabled.
     */
    public static boolean isEnabled() {
        return windSystemEnabled;
    }
}
