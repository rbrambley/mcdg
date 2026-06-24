package com.mcdg.game;

import com.mcdg.data.Hole;
import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Computes and caches hazard grids for hole maps.
 * Precomputes hazard information during course placement to avoid
 * client-side terrain sampling during rendering.
 */
public final class HoleHazardGridService {

    private static final int GRID_PADDING = 4;
    private static final int MAX_GRID_SIZE = 260;

    private static final Map<String, Map<Integer, CachedHazardGrid>> COURSE_CACHE = new HashMap<>();

    public record CachedHazardGrid(
            int minX,
            int minZ,
            int width,
            int height,
            byte[] gridData
    ) {}

    private HoleHazardGridService() {}

    public static void reset() {
        COURSE_CACHE.clear();
    }

    public static void onPlayerDisconnect(UUID playerId) {
        // No player-specific state to clear
    }

    /**
     * Computes the hazard grid for a hole during course placement.
     * Samples terrain to detect slope hazards and dense rough.
     */
    public static CachedHazardGrid computeGrid(
            ServerWorld world,
            Hole hole,
            BlockPos tee,
            BlockPos basket,
            TournamentRulesetManager rulesetManager
    ) {
        int minX = Math.min(tee.getX(), basket.getX()) - GRID_PADDING;
        int minZ = Math.min(tee.getZ(), basket.getZ()) - GRID_PADDING;
        int maxX = Math.max(tee.getX(), basket.getX()) + GRID_PADDING;
        int maxZ = Math.max(tee.getZ(), basket.getZ()) + GRID_PADDING;

        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;

        if (width > MAX_GRID_SIZE || height > MAX_GRID_SIZE) {
            // Hole too large, return empty grid
            return new CachedHazardGrid(minX, minZ, width, height, new byte[width * height]);
        }

        byte[] grid = new byte[width * height];
        BlockPos.Mutable feet = new BlockPos.Mutable();

        boolean slopeEnabled = rulesetManager.strictEnableSlopeHazard();
        int slopeThreshold = rulesetManager.strictSlopeHazardDeltaY();
        boolean roughEnabled = rulesetManager.strictEnableRoughHazard();
        int roughThreshold = rulesetManager.strictRoughHazardLeafLogThreshold();

        for (int z = 0; z < height; z++) {
            int worldZ = minZ + z;
            for (int x = 0; x < width; x++) {
                int worldX = minX + x;
                int feetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ) - 1;
                feet.set(worldX, feetY, worldZ);

                byte hazardType = 0;

                // Optimized hazard detection - use targeted checks first, then HazardManager for expanded hazards
                // Check fluid penalty zones (water/lava) - render on map
                if (OutOfBoundsClassifier.isFluidPenaltyZone(world, feet)) {
                    if (world.getFluidState(feet).isIn(FluidTags.WATER) || world.getFluidState(feet.down()).isIn(FluidTags.WATER)) {
                        hazardType = 2; // Water (WATER category for blue color)
                    } else {
                        hazardType = 3; // Lava (danger hazard category)
                    }
                } else if (slopeEnabled && OutOfBoundsClassifier.isSteepSlopeHazard(world, feet, slopeThreshold)) {
                    hazardType = 1; // Slope (surface hazard category)
                } else if (roughEnabled && OutOfBoundsClassifier.isDenseRoughHazard(world, feet, roughThreshold)) {
                    hazardType = 1; // Rough (surface hazard category)
                } else {
                    // Only use HazardManager for non-fluid, non-slope, non-rough hazards
                    // This avoids expensive isSteepDrop checks for most cells
                    HazardType hazard = HazardManager.getHazardType(world, feet);
                    if (hazard != HazardType.NONE && hazard != HazardType.WATER && hazard != HazardType.LAVA) {
                        hazardType = HazardManager.getGridCategoryByte(hazard);
                    }
                }

                grid[z * width + x] = hazardType;
            }
        }

        return new CachedHazardGrid(minX, minZ, width, height, grid);
    }

    /**
     * Caches the hazard grid for a specific hole in a course.
     */
    public static void cacheGrid(String courseKey, int holeIndex, CachedHazardGrid grid) {
        COURSE_CACHE.computeIfAbsent(courseKey, k -> new HashMap<>()).put(holeIndex, grid);
    }

    /**
     * Retrieves the cached hazard grid for a hole, or null if not cached.
     */
    public static CachedHazardGrid getCachedGrid(String courseKey, int holeIndex) {
        Map<Integer, CachedHazardGrid> courseGrids = COURSE_CACHE.get(courseKey);
        if (courseGrids == null) {
            return null;
        }
        return courseGrids.get(holeIndex);
    }

    /**
     * Checks if a hazard grid exists for the given course and hole.
     */
    public static boolean hasGrid(String courseKey, int holeIndex) {
        Map<Integer, CachedHazardGrid> courseGrids = COURSE_CACHE.get(courseKey);
        if (courseGrids == null) {
            return false;
        }
        return courseGrids.containsKey(holeIndex);
    }

    /**
     * Generates a course key from course name and seed.
     */
    public static String courseKey(String courseName, long seed) {
        return courseName + "|" + seed;
    }
}
