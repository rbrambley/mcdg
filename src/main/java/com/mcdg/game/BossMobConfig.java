package com.mcdg.game;

import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Configuration for boss hole mob spawning behavior.
 */
public record BossMobConfig(
        List<Identifier> mobTypes,
        int maxMobs,
        int spawnIntervalTicks,
        boolean guardBasket,
        boolean patrolFairway
) {
    public BossMobConfig {
        if (mobTypes == null || mobTypes.isEmpty()) {
            throw new IllegalArgumentException("mobTypes must not be null or empty");
        }
        if (maxMobs < 1) {
            throw new IllegalArgumentException("maxMobs must be >= 1");
        }
        if (spawnIntervalTicks < 1) {
            throw new IllegalArgumentException("spawnIntervalTicks must be >= 1");
        }
    }

    /**
     * Default configuration for boss holes.
     */
    public static BossMobConfig defaultBossHoleConfig() {
        return new BossMobConfig(
                List.of(
                        Identifier.of("minecraft", "zombie"),
                        Identifier.of("minecraft", "skeleton"),
                        Identifier.of("minecraft", "spider")
                ),
                4, // max 4 mobs
                200, // spawn every 10 seconds (200 ticks)
                true, // guard basket
                true  // patrol fairway
        );
    }
}