package com.mcdg.game;

/**
 * Hazard categories for disc golf course hazards.
 * Grouped into 4 grid encoding categories for minimap display:
 * - NONE: No hazard
 * - SURFACE_HAZARD: Sand, ice, rough, swamp (affects disc bounce, slows retrieval)
 * - WATER: Water hazard (blue color for compatibility)
 * - DANGER_HAZARD: Lava, cactus (destroys disc, damages player)
 */
public enum HazardType {
    NONE("None", "No hazard", GridCategory.NONE),

    // Surface hazards (GridCategory.SURFACE_HAZARD)
    SAND("Sand Trap", "+1 penalty stroke, next throw power reduced, reduced bounce", GridCategory.SURFACE_HAZARD),
    ICE("Ice Hazard", "Next throw power reduced, extra bouncy", GridCategory.SURFACE_HAZARD),
    ROUGH("Rough", "+1 penalty stroke, next throw power reduced, reduced bounce", GridCategory.SURFACE_HAZARD),
    SWAMP("Swamp", "+1 penalty stroke, next throw power reduced, reduced bounce", GridCategory.SURFACE_HAZARD),

    // Water hazard (GridCategory.WATER - separate for blue color)
    WATER("Water Hazard", "+1 penalty stroke", GridCategory.WATER),

    // Danger hazards (GridCategory.DANGER_HAZARD)
    LAVA("Lava Hazard", "Destroys disc, damages player", GridCategory.DANGER_HAZARD),
    CACTUS("Cactus Field", "Destroys disc, damages player", GridCategory.DANGER_HAZARD),
    CLIFF("Cliff Drop", "+1 penalty stroke, next throw power reduced", GridCategory.DANGER_HAZARD);

    private final String displayName;
    private final String description;
    private final GridCategory gridCategory;

    HazardType(String displayName, String description, GridCategory gridCategory) {
        this.displayName = displayName;
        this.description = description;
        this.gridCategory = gridCategory;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public GridCategory gridCategory() {
        return gridCategory;
    }

    /**
     * Grid categories for minimap encoding (4 types max).
     * Maps to byte values: 0=NONE, 1=SURFACE_HAZARD, 2=WATER, 3=DANGER_HAZARD
     */
    public enum GridCategory {
        NONE(0),
        SURFACE_HAZARD(1),
        WATER(2),
        DANGER_HAZARD(3);

        private final int byteValue;

        GridCategory(int byteValue) {
            this.byteValue = byteValue;
        }

        public int byteValue() {
            return byteValue;
        }

        public static GridCategory fromByteValue(byte value) {
            return switch (value) {
                case 0 -> NONE;
                case 1 -> SURFACE_HAZARD;
                case 2 -> WATER;
                case 3 -> DANGER_HAZARD;
                default -> NONE;
            };
        }
    }
}
