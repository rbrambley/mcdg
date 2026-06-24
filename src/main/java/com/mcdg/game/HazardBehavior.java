package com.mcdg.game;

/**
 * Hazard behavior properties that define how a hazard affects disc flight and player interaction.
 * Used by HazardManager to apply dynamic effects when a disc lands in a hazard.
 */
public record HazardBehavior(
    boolean destroysDisc,        // Disc is lost on contact (lava, cactus)
    boolean addsPenaltyStroke,   // +1 penalty stroke (most hazards)
    boolean slowsRetrieval,      // Player movement slowed during retrieval (sand, swamp, rough)
    double bounceModifier,       // Alters disc bounce (0.0 = no bounce, 1.0 = normal, 1.5+ = extra bouncy)
    int damageAmount,            // Damage to player on contact (lava, cactus)
    String penaltyReason         // Human-readable reason for penalty
) {
    /**
     * Default behavior for no hazard.
     */
    public static final HazardBehavior NONE = new HazardBehavior(
        false, false, false, 1.0, 0, "In Bounds"
    );

    /**
     * Water hazard behavior (OB penalty stroke).
     */
    public static final HazardBehavior WATER = new HazardBehavior(
        false, true, false, 0.0, 0, "Water"
    );

    /**
     * Lava hazard behavior (destroys disc, damages player).
     */
    public static final HazardBehavior LAVA = new HazardBehavior(
        true, false, false, 0.0, 4, "Lava"
    );

    /**
     * Sand trap behavior (slows retrieval, reduced bounce).
     */
    public static final HazardBehavior SAND = new HazardBehavior(
        false, true, true, 0.3, 0, "Sand Trap"
    );

    /**
     * Ice hazard behavior (extra bouncy, unpredictable).
     */
    public static final HazardBehavior ICE = new HazardBehavior(
        false, false, false, 1.5, 0, "Ice"
    );

    /**
     * Cactus field behavior (destroys disc, damages player).
     */
    public static final HazardBehavior CACTUS = new HazardBehavior(
        true, false, false, 0.0, 2, "Cactus"
    );

    /**
     * Rough behavior (slows retrieval, penalty stroke).
     */
    public static final HazardBehavior ROUGH = new HazardBehavior(
        false, true, true, 0.3, 0, "Rough"
    );

    /**
     * Swamp behavior (slows movement, penalty stroke).
     */
    public static final HazardBehavior SWAMP = new HazardBehavior(
        false, true, true, 0.2, 0, "Swamp"
    );

    /**
     * Cliff behavior (difficult recovery, penalty stroke).
     */
    public static final HazardBehavior CLIFF = new HazardBehavior(
        false, true, true, 0.1, 0, "Cliff"
    );
}
