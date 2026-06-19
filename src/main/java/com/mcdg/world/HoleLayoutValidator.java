package com.mcdg.world;

import com.mcdg.data.Hole;
import com.mcdg.data.TeePoint;
import java.util.List;

public final class HoleLayoutValidator {
    private static final int MIN_HOLE_SPACING_BLOCKS = 48;

    public boolean isDistanceValid(int distanceFeet, int minFeet, int maxFeet) {
        return distanceFeet >= minFeet && distanceFeet <= maxFeet;
    }

    public boolean isNonOverlapping(Hole candidate, List<Hole> placedHoles) {
        TeePoint candidateTee = candidate.tee();

        for (Hole existing : placedHoles) {
            if (distance2d(candidateTee.x(), candidateTee.z(), existing.tee().x(), existing.tee().z()) < MIN_HOLE_SPACING_BLOCKS) {
                return false;
            }

            if (distance2d(candidate.basket().x(), candidate.basket().z(), existing.basket().x(), existing.basket().z()) < MIN_HOLE_SPACING_BLOCKS) {
                return false;
            }
        }

        return true;
    }

    public int distanceFeetFromBlocks(int ax, int az, int bx, int bz) {
        return Math.round(distance2d(ax, az, bx, bz) * 3.28084f);
    }

    private static int distance2d(int ax, int az, int bx, int bz) {
        return (int) Math.round(Math.hypot(bx - ax, bz - az));
    }
}
