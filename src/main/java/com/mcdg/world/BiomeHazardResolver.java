package com.mcdg.world;

/**
 * Resolves a {@link BiomeHazardProfile} from a {@link BiomeTheme}.
 * Maps biome themes to their appropriate hazard placement profiles.
 */
public final class BiomeHazardResolver {
    private BiomeHazardResolver() {}

    /**
     * Returns the hazard profile for the given biome theme.
     * Falls back to {@link BiomeHazardProfile#DEFAULT} when no specific profile matches.
     */
    public static BiomeHazardProfile resolve(BiomeTheme theme) {
        return switch (theme.name()) {
            case "desert" -> BiomeHazardProfile.DESERT;
            case "snowy" -> BiomeHazardProfile.SNOWY;
            case "swamp" -> BiomeHazardProfile.SWAMP;
            case "forest" -> BiomeHazardProfile.FOREST;
            case "jungle" -> BiomeHazardProfile.JUNGLE;
            case "mountain" -> BiomeHazardProfile.MOUNTAIN;
            case "badlands" -> BiomeHazardProfile.BADLANDS;
            case "beach" -> BiomeHazardProfile.BEACH;
            case "nether" -> BiomeHazardProfile.NETHER;
            case "savanna" -> BiomeHazardProfile.SAVANNA;
            default -> BiomeHazardProfile.DEFAULT;
        };
    }
}
