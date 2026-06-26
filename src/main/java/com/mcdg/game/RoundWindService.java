package com.mcdg.game;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automates wind setup and teardown around round lifecycle.
 * Wind is configured once at round start and remains constant for the entire round.
 * A manual admin wind command disables automation for that world until the round ends
 * or an admin explicitly re-enables it with /mcdg wind auto.
 */
public final class RoundWindService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundWindService.class);

    private RoundWindService() {
        // Utility class
    }

    /**
     * Initialize the service with the configured round wind policy.
     */
    public static void initialize(RoundWindMode mode) {
        RoundWindPolicy.initialize(mode);
    }

    /**
     * Get the currently configured round wind mode.
     */
    public static RoundWindMode getRoundWindMode() {
        return RoundWindPolicy.getRoundWindMode();
    }

    /**
     * Called when a round starts for a given world.
     * Saves the current wind and applies the configured round wind unless an admin has
     * manually overridden wind.
     */
    public static void onRoundStart(ServerWorld world, long courseSeed) {
        if (!WindManager.isEnabled()) {
            return;
        }

        Identifier worldId = world.getRegistryKey().getValue();
        WindManager.saveWorldWind(world);

        if (WindManager.isManualOverride(worldId)) {
            LOGGER.info("Round wind automation skipped | world={} reason=manual_override", worldId);
            return;
        }

        WindState roundWind = generateRoundWind(world, courseSeed, RoundWindPolicy.getRoundWindMode());
        WindManager.setWorldWindState(world, roundWind);
        LOGGER.info("Round wind applied | world={} mode={} speed={} direction={}°",
                worldId, RoundWindPolicy.getRoundWindMode(), roundWind.speed(), roundWind.directionDegrees());
    }

    /**
     * Called when a round ends for a given world.
     * Restores the pre-round wind and clears the manual override flag so the next round
     * starts fresh with automation.
     */
    public static void onRoundEnd(ServerWorld world) {
        if (!WindManager.isEnabled()) {
            return;
        }

        Identifier worldId = world.getRegistryKey().getValue();
        WindManager.restoreWorldWind(world);
        WindManager.clearManualOverride(worldId);
        LOGGER.info("Round wind restored | world={}", worldId);
    }

    /**
     * Re-enable automation for a world, optionally applying round wind immediately if requested.
     * Used by /mcdg wind auto.
     */
    public static void reEnableAutomation(ServerWorld world, long courseSeed) {
        Identifier worldId = world.getRegistryKey().getValue();
        WindManager.clearManualOverride(worldId);
        WindState roundWind = generateRoundWind(world, courseSeed, RoundWindPolicy.getRoundWindMode());
        WindManager.setWorldWindState(world, roundWind);
        LOGGER.info("Round wind automation re-enabled | world={} mode={} speed={} direction={}°",
                worldId, RoundWindPolicy.getRoundWindMode(), roundWind.speed(), roundWind.directionDegrees());
    }

    /**
     * Generate a round-consistent wind state based on the configured policy.
     * The returned state uses FIXED mode so the server tick handler does not change it mid-round.
     */
    static WindState generateRoundWind(ServerWorld world, long courseSeed, RoundWindMode mode) {
        return switch (mode) {
            case CALM -> WindState.calm();
            case NATURAL -> {
                WindState natural = WindManager.generateNaturalWind(world, null);
                yield new WindState(
                        natural.velocity(),
                        natural.speed(),
                        natural.directionDegrees(),
                        WindMode.FIXED,
                        natural.isGusting(),
                        natural.lastUpdated(),
                        natural.tournamentId()
                );
            }
            case FIXED_RANDOM -> {
                double speed = 0.1 + world.random.nextDouble() * 0.4;
                float direction = world.random.nextFloat() * 360.0f;
                yield WindState.fixed(speed, direction);
            }
        };
    }
}
