package com.mcdg.world;

import com.mcdg.game.HazardType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.List;
import java.util.Map;

/**
 * Hazard placement profile for a biome theme.
 * Defines which hazards are appropriate for the biome, their density,
 * and the block states used to place them.
 */
public record BiomeHazardProfile(
        String name,
        List<HazardType> preferredHazards,
        double hazardDensity,           // 0.0 to 1.0, probability of hazard placement per fairway step
        int minDistanceFromTee,         // Minimum blocks from tee before hazards can appear
        int minDistanceFromBasket,      // Minimum blocks from basket before hazards can appear
        Map<HazardType, BlockState> hazardBlocks  // Block states to use for each hazard type
) {
    /**
     * Default profile with no biome-specific hazards.
     */
    public static final BiomeHazardProfile DEFAULT = new BiomeHazardProfile(
            "default",
            List.of(),
            0.0,
            0,
            0,
            Map.of()
    );

    /**
     * Desert profile: sand traps with sand blocks.
     */
    public static final BiomeHazardProfile DESERT = new BiomeHazardProfile(
            "desert",
            List.of(HazardType.SAND),
            0.15,
            20,
            15,
            Map.of(HazardType.SAND, Blocks.SAND.getDefaultState())
    );

    /**
     * Snowy profile: ice hazards with ice blocks.
     */
    public static final BiomeHazardProfile SNOWY = new BiomeHazardProfile(
            "snowy",
            List.of(HazardType.ICE),
            0.12,
            20,
            15,
            Map.of(HazardType.ICE, Blocks.ICE.getDefaultState())
    );

    /**
     * Swamp profile: swamp hazards with mud blocks.
     */
    public static final BiomeHazardProfile SWAMP = new BiomeHazardProfile(
            "swamp",
            List.of(HazardType.SWAMP),
            0.18,
            15,
            12,
            Map.of(HazardType.SWAMP, Blocks.MUD.getDefaultState())
    );

    /**
     * Forest profile: rough hazards with oak logs (persist, detected via BlockTags.LOGS).
     */
    public static final BiomeHazardProfile FOREST = new BiomeHazardProfile(
            "forest",
            List.of(HazardType.ROUGH),
            0.20,
            18,
            15,
            Map.of(
                    HazardType.ROUGH, Blocks.OAK_LOG.getDefaultState()
            )
    );

    /**
     * Jungle profile: dense rough with jungle logs.
     */
    public static final BiomeHazardProfile JUNGLE = new BiomeHazardProfile(
            "jungle",
            List.of(HazardType.ROUGH),
            0.25,
            18,
            15,
            Map.of(
                    HazardType.ROUGH, Blocks.JUNGLE_LOG.getDefaultState()
            )
    );

    /**
     * Mountain profile: no placed block hazards — mountains have natural cliffs
     * and elevation-based hazards detected by HazardManager.isSteepDrop().
     */
    public static final BiomeHazardProfile MOUNTAIN = new BiomeHazardProfile(
            "mountain",
            List.of(),
            0.0,
            0,
            0,
            Map.of()
    );

    /**
     * Badlands profile: sand traps with red sand.
     */
    public static final BiomeHazardProfile BADLANDS = new BiomeHazardProfile(
            "badlands",
            List.of(HazardType.SAND),
            0.18,
            20,
            15,
            Map.of(HazardType.SAND, Blocks.RED_SAND.getDefaultState())
    );

    /**
     * Beach profile: sand traps near water.
     */
    public static final BiomeHazardProfile BEACH = new BiomeHazardProfile(
            "beach",
            List.of(HazardType.SAND),
            0.10,
            15,
            12,
            Map.of(HazardType.SAND, Blocks.SAND.getDefaultState())
    );

    /**
     * Nether profile: lava hazards. Cactus removed (requires sand to survive).
     * Nether wood (crimson/warped) is fireproof, so lava is safe near structures.
     */
    public static final BiomeHazardProfile NETHER = new BiomeHazardProfile(
            "nether",
            List.of(HazardType.LAVA),
            0.08,
            25,
            20,
            Map.of(HazardType.LAVA, Blocks.LAVA.getDefaultState())
    );

    /**
     * Savanna profile: light rough with acacia logs.
     */
    public static final BiomeHazardProfile SAVANNA = new BiomeHazardProfile(
            "savanna",
            List.of(HazardType.ROUGH),
            0.12,
            18,
            15,
            Map.of(HazardType.ROUGH, Blocks.ACACIA_LOG.getDefaultState())
    );
}
