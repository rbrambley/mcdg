package com.mcdg.world;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;

/**
 * Resolves a {@link BiomeTheme} from a {@link RegistryEntry<Biome>}.
 * Uses BiomeTags and ID string matching for robust categorization.
 */
public final class BiomeThemeResolver {
    private BiomeThemeResolver() {}

    /**
     * Returns the most appropriate {@link BiomeTheme} for the given biome entry.
     * Falls back to {@link BiomeTheme#DEFAULT} when no specific theme matches.
     */
    public static BiomeTheme resolve(RegistryEntry<Biome> biome) {
        String id = PlacementUtils.biomeId(biome);

        if (biome.isIn(BiomeTags.IS_NETHER)) {
            return BiomeTheme.NETHER;
        }

        if (PlacementUtils.isBiome(id,
                "desert", "beach", "snowy_beach", "stony_shore")) {
            return BiomeTheme.DESERT;
        }

        if (PlacementUtils.isBiome(id,
                "badlands", "eroded_badlands", "wooded_badlands")) {
            return BiomeTheme.BADLANDS;
        }

        if (biome.isIn(BiomeTags.IS_JUNGLE)) {
            return BiomeTheme.JUNGLE;
        }

        if (biome.isIn(BiomeTags.IS_SAVANNA)) {
            return BiomeTheme.SAVANNA;
        }

        if (PlacementUtils.isBiome(id,
                "swamp", "mangrove_swamp")) {
            return BiomeTheme.SWAMP;
        }

        if (biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)) {
            return BiomeTheme.MOUNTAIN;
        }

        if (PlacementUtils.isBiome(id,
                "snowy_plains", "snowy_taiga", "snowy_slopes",
                "snowy_beach", "grove", "frozen_peaks", "jagged_peaks")) {
            return BiomeTheme.SNOWY;
        }

        if (biome.isIn(BiomeTags.IS_FOREST)) {
            return BiomeTheme.FOREST;
        }

        if (PlacementUtils.isBiome(id,
                "beach", "stony_shore", "mushroom_fields")) {
            return BiomeTheme.BEACH;
        }

        return BiomeTheme.DEFAULT;
    }
}
