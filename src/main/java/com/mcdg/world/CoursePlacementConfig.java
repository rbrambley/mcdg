package com.mcdg.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * Configuration constants for course placement logic.
 * Groups related constants for better organization and maintainability.
 */
public final class CoursePlacementConfig {
    private CoursePlacementConfig() {
    }

    /**
     * Search radius constants for finding suitable placement locations.
     */
    public static final class SearchRadii {
        private SearchRadii() {
        }

        /** Radius to search for fairway placement around a target point */
        public static final int FAIRWAY = 3;
        
        /** Radius to search for hole placement (tee to basket) */
        public static final int HOLE = 40;
        
        /** Radius to search for course anchor point */
        public static final int ANCHOR = 120;
        
        /** Radius to search for alternate fairway anchor */
        public static final int ALT_FAIRWAY_ANCHOR = 91;
        
        /** Radius to search for emergency alternate fairway anchor */
        public static final int ALT_FAIRWAY_EMERGENCY_ANCHOR = 91;
        
        /** Radius to search for camp site markers */
        public static final int CAMP_SITE_MARKER = 192;
    }

    /**
     * Fairway clearing and carving constants.
     */
    public static final class Fairway {
        private Fairway() {
        }

        /** Bottom padding for fairway clearing (blocks below fairway) */
        public static final int CLEAR_BOTTOM_PADDING = 10;
        
        /** Top padding for fairway clearing (blocks above fairway) */
        public static final int CLEAR_TOP_PADDING = 4;
        
        /** Extra radius for log sweeping around fairway */
        public static final int LOG_SWEEP_EXTRA_RADIUS = 1;
        
        /** Maximum tree cluster blocks to clear */
        public static final int TREE_CLUSTER_CLEAR_LIMIT = 1400;
    }

    /**
     * Water landing and carry constants.
     */
    public static final class WaterLanding {
        private WaterLanding() {
        }

        /** Interval between water landing patches */
        public static final int PATCH_INTERVAL = 35;
        
        /** Radius of water landing patches */
        public static final int PATCH_RADIUS = 6;
        
        /** Maximum carry distance for water landing patches */
        public static final int PATCH_MAX_CARRY = 24;
        
        /** Scan radius for enforcing water landing */
        public static final int ENFORCE_SCAN_RADIUS = 6;
        
        /** Maximum gap allowed when enforcing water landing */
        public static final int ENFORCE_MAX_GAP = 20;
        
        /** Radius around basket for water-adjacent checks */
        public static final int ADJACENT_BASKET_GREEN_RADIUS = 12;
        
        /** Scan radius for water-adjacent checks */
        public static final int ADJACENT_SCAN_RADIUS = 9;
        
        /** Minimum water columns required for water-adjacent detection */
        public static final int ADJACENT_MIN_COLUMNS = 10;
        
        /** Maximum water carry distance (about 300 ft) */
        public static final int MAX_CARRY_BLOCKS = 500;

        /** Minimum safe fairway width at alternate anchors (7 blocks wide) */
        public static final int SAFE_FAIRWAY_HALF_WIDTH = 3;

        /** Minimum safe fairway length towards hole at alternate anchors */
        public static final int SAFE_FAIRWAY_MIN_LENGTH = 20;
    }

    /**
     * Finish green and approach constants.
     */
    public static final class FinishGreen {
        private FinishGreen() {
        }

        /** Minimum safe columns around finish green */
        public static final int MIN_SAFE_COLUMNS = 36;
        
        /** Maximum radius of finish green */
        public static final int MAX_RADIUS = 24;
        
        /** Scan distance for finish approach */
        public static final int APPROACH_SCAN_DISTANCE = 32;
        
        /** Sample interval for finish approach */
        public static final int APPROACH_SAMPLE_INTERVAL = 8;
        
        /** Base radius for finish approach */
        public static final int APPROACH_BASE_RADIUS = 4;
        
        /** Maximum extra radius for finish approach */
        public static final int APPROACH_MAX_EXTRA_RADIUS = 3;
        
        /** Distance at which to widen finish approach */
        public static final int APPROACH_WIDEN_DISTANCE = 28;
        
        /** Half-width for finish hazard scanning */
        public static final int HAZARD_SCAN_HALF_WIDTH = 7;
    }

    /**
     * Island and ring constants for signature holes.
     */
    public static final class Islands {
        private Islands() {
        }

        /** Radius of tee island */
        public static final int TEE_RADIUS = 2;
        
        /** Radius of basket island */
        public static final int BASKET_RADIUS = 7;
        
        /** Radius of signature ring */
        public static final int SIGNATURE_RING_RADIUS = 4;
    }

    /**
     * Tee placement and clearing constants.
     */
    public static final class Tee {
        private Tee() {
        }

        /** Clear distance from tee for launch */
        public static final int LAUNCH_CLEAR_DISTANCE = 22;
        
        /** Half-width for tee launch clearing */
        public static final int LAUNCH_CLEAR_HALF_WIDTH = 3;
        
        /** Radius for tee relocation */
        public static final int RELOCATION_RADIUS = 28;
        
        /** Y tolerance for tee exit */
        public static final int EXIT_Y_TOLERANCE = 1;
        
        /** Minimum nearby exits required for tee */
        public static final int MIN_NEARBY_EXITS = 5;
        
        /** Wall scan radius for tee */
        public static final int WALL_SCAN_RADIUS = 6;
        
        /** Maximum enclosure score for tee */
        public static final int MAX_ENCLOSURE_SCORE = 9;
        
        /** Enclosure depth failure threshold for tee prefilter */
        public static final int PREFILTER_ENCLOSURE_DEPTH_FAIL = 10;
        
        /** Pit depth threshold for tee */
        public static final int PIT_DEPTH_THRESHOLD = 4;
        
        /** Maximum direct carry gap from tee */
        public static final int MAX_DIRECT_CARRY_GAP = 91;
    }

    /**
     * Basket placement and enclosure recovery constants.
     */
    public static final class Basket {
        private Basket() {
        }

        /** Radius for basket relocation */
        public static final int RELOCATION_RADIUS = 24;
        
        /** Scan radius for basket enclosure */
        public static final int ENCLOSURE_SCAN_RADIUS = 6;
        
        /** Center depth failure threshold for basket enclosure */
        public static final int ENCLOSURE_CENTER_DEPTH_FAIL = 18;
        
        /** Center depth check threshold for basket enclosure */
        public static final int ENCLOSURE_CENTER_DEPTH_CHECK = 12;
        
        /** Minimum depth for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_MIN_DEPTH = 13;
        
        /** Maximum depth for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_MAX_DEPTH = 35;
        
        /** Width for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_WIDTH = 8;
        
        /** Headroom for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_HEADROOM = 4;
        
        /** Lateral step for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_LATERAL_STEP = 8;
        
        /** Maximum lava reroute attempts for basket enclosure recovery */
        public static final int ENCLOSURE_RECOVERY_MAX_LAVA_REROUTE_ATTEMPTS = 3;
        
        /** Wall depth threshold for basket enclosure */
        public static final int ENCLOSURE_WALL_DEPTH_THRESHOLD = 8;
        
        /** High wall ratio threshold for basket enclosure */
        public static final double ENCLOSURE_HIGH_WALL_RATIO = 0.82;
        
        /** Check height for basket dry column */
        public static final int DRY_COLUMN_CHECK_HEIGHT = 8;
        
        /** Enforce distance for basket approach */
        public static final int APPROACH_ENFORCE_DISTANCE = 12;
        
        /** Minimum width for basket approach */
        public static final int APPROACH_MIN_WIDTH = 8;
    }

    /**
     * Surface search and player-relative placement constants.
     */
    public static final class Surface {
        private Surface() {
        }

        /** Depth limit for surface search */
        public static final int SEARCH_DEPTH_LIMIT = 24;
        
        /** Minimum Y offset for player-relative tee */
        public static final int PLAYER_RELATIVE_TEE_MIN_Y_OFFSET = 20;
        
        /** Minimum Y offset for player-relative basket target */
        public static final int PLAYER_RELATIVE_BASKET_TARGET_MIN_Y_OFFSET = 20;
        
        /** Absolute minimum Y offset for player-relative basket */
        public static final int PLAYER_RELATIVE_BASKET_ABSOLUTE_MIN_Y_OFFSET = 28;
        
        /** Radius for player-relative Y repositioning */
        public static final int PLAYER_RELATIVE_Y_REPOSITION_RADIUS = 96;
    }

    /**
     * Alternate fairway routing constants.
     */
    public static final class AltFairway {
        private AltFairway() {
        }

        /** Target route gap for alternate fairway */
        public static final int TARGET_ROUTE_GAP = 87;
        
        /** Maximum gap for first leg of alternate fairway */
        public static final int FIRST_LEG_MAX_GAP = 76;
        
        /** Fallback maximum gap for first leg of alternate fairway */
        public static final int FIRST_LEG_MAX_GAP_FALLBACK = 91;
        
        /** Minimum advance for alternate fairway */
        public static final int MIN_ADVANCE = 8;
        
        /** Maximum first leg length for alternate fairway */
        public static final int MAX_FIRST_LEG = 91;
        
        /** Emergency maximum first leg length for alternate fairway */
        public static final int EMERGENCY_MAX_FIRST_LEG = 91;
    }

    /**
     * Course anchor selection constants.
     */
    public static final class CourseAnchor {
        private CourseAnchor() {
        }

        /** Maximum retries for course anchor selection */
        public static final int MAX_RETRIES = 4;
        
        /** Hard reject water ratio for course anchor */
        public static final double HARD_REJECT_WATER_RATIO = 0.22;
        
        /** Maximum water sample ratio for course anchor */
        public static final double MAX_WATER_SAMPLE_RATIO = 0.30;
        
        /** Score weight for water ratio in course anchor selection */
        public static final int WATER_RATIO_SCORE_WEIGHT = 22000;
        
        /** Penalty for water rejection in course anchor selection */
        public static final int WATER_REJECT_PENALTY = 180000;
    }

    /**
     * Route policy constants.
     */
    public static final class RoutePolicy {
        private RoutePolicy() {
        }

        /** Maximum retries for route policy enforcement */
        public static final int MAX_RETRIES = 5;
        
        /** Maximum water carry for par 5 routes */
        public static final int PAR5_MAX_WATER_CARRY = 500;
        
        /** Maximum water carry for par 3/4 routes */
        public static final int PAR34_MAX_WATER_CARRY = 500;
    }

    /**
     * Camp site constants.
     */
    public static final class CampSite {
        private CampSite() {
        }

        /** Radius for camp site */
        public static final int RADIUS = 28;
        
        /** Scan step for camp site */
        public static final int SCAN_STEP = 4;
        
        /** Maximum Y delta for camp site */
        public static final int MAX_Y_DELTA = 6;
        
        /** Minimum safe percentage for camp site */
        public static final int MIN_SAFE_PERCENT = 65;
        
        /** Block state for camp site marker */
        public static final BlockState MARKER_BLOCK = Blocks.LODESTONE.getDefaultState();
    }

    /**
     * Environment variable names.
     */
    public static final class EnvVars {
        private EnvVars() {
        }

        /** Environment variable to enable alternate route diagnostics */
        public static final String ALT_ROUTE_DIAG = "MCDG_ALT_ROUTE_DIAG";
    }
}
