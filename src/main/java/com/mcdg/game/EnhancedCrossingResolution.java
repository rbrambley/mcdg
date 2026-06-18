package com.mcdg.game;

import net.minecraft.util.math.BlockPos;

/**
 * Enhanced crossing resolution with detailed trajectory-based OB detection data.
 * Includes crossing location, distance, lateral drift, and penalty type information.
 */
public record EnhancedCrossingResolution(
    BlockPos safeLie,              // Last in-bounds position
    BlockPos firstOutCrossing,    // First OB position along trajectory
    double crossingDistanceFt,     // Distance from throw to crossing
    double lateralDriftAtCrossing, // Lateral drift at crossing point
    StrictPenaltyType penaltyType  // Specific penalty (OB, HAZARD, or NONE)
) {}
