package com.mcdg.client;

/**
 * Manages vertical positioning for left-side HUD elements to prevent overlaps.
 * Coordinates spacing between HoleMapOverlay and RunningScoreboardOverlay.
 *
 * All inputs (margins, spacing, heights) are in UNSCALED pixels.
 * The layout applies the scale factor internally so callers never need to
 * pre-multiply by scale before passing values in.
 */
public final class LeftSideHudLayout {
    private static final int TOP_MARGIN = 8;      // unscaled px
    private static final int BOTTOM_MARGIN = 20;  // unscaled px
    private static final int HUD_SPACING = 8;     // unscaled px between panels

    private final int screenHeight;
    private final float scale;
    private int nextY;
    private int bottomReserve; // scaled px reserved for later panels

    public LeftSideHudLayout(int screenHeight, float scale) {
        this.screenHeight = screenHeight;
        this.scale = scale;
        this.nextY = Math.round(TOP_MARGIN * scale);
        this.bottomReserve = 0;
    }

    /**
     * Reserves scaled pixels at the bottom of the remaining space.
     * Panels allocated after this call see reduced remaining height,
     * while panels allocated before it are unaffected.
     * Intended to be called before the first allocation so that early
     * panels (e.g. hole map) leave room for later panels (e.g. scoreboard).
     *
     * @param scaledPx Scaled pixels to reserve
     */
    public void reserveBottom(int scaledPx) {
        bottomReserve += Math.max(0, scaledPx);
    }

    /**
     * Creates a layout whose top cursor is already pushed past Xaero's minimap
     * when it occupies the top-left corner.
     *
     * @param screenHeight Scaled screen height in pixels
     * @param scale        Current HUD scale factor
     * @return A layout whose nextY starts below Xaero (or at the normal top margin
     *         when Xaero is absent or not in the top-left corner)
     */
    public static LeftSideHudLayout withXaeroOffset(int screenHeight, float scale) {
        LeftSideHudLayout layout = new LeftSideHudLayout(screenHeight, scale);
        int xaeroReserved = XaeroMinimapCompat.getTopLeftReservedPixels(scale);
        if (xaeroReserved > 0) {
            // Push the cursor below Xaero's minimap footprint
            layout.nextY = Math.max(layout.nextY, xaeroReserved);
        }
        return layout;
    }

    /**
     * Returns the fixed left-edge X for all left-side HUD panels.
     * Uses the same 8px margin as the right-side HUDs.
     */
    public int getLeftX() {
        return Math.round(8 * scale);
    }

    /**
     * Returns the current scale factor.
     */
    public float getScale() {
        return scale;
    }

    /**
     * Returns the screen height in scaled pixels.
     */
    public int getScreenHeight() {
        return screenHeight;
    }

    /**
     * Allocates vertical space for a HUD element and returns its Y position
     * in scaled pixels.
     *
     * @param unscaledHeight The unscaled height of the HUD element
     * @return The scaled Y position where the HUD should be rendered
     */
    public int allocate(int unscaledHeight) {
        int y = nextY;
        nextY += Math.round(unscaledHeight * scale) + Math.round(HUD_SPACING * scale);
        return y;
    }

    /**
     * Allocates vertical space using an already-scaled height value.
     * Use this when the caller has already computed the panel height in scaled pixels.
     *
     * @param scaledHeight The scaled height of the HUD element (pixels)
     * @return The scaled Y position where the HUD should be rendered
     */
    public int allocateScaled(int scaledHeight) {
        int y = nextY;
        nextY += scaledHeight + Math.round(HUD_SPACING * scale);
        return y;
    }

    /**
     * Returns the remaining vertical space available below the current cursor,
     * accounting for the bottom margin and any reserved bottom space.
     * May be negative if panels have overflowed.
     */
    public int getRemainingHeight() {
        return screenHeight - nextY - Math.round(BOTTOM_MARGIN * scale) - bottomReserve;
    }

    /**
     * Returns the remaining vertical space ignoring the bottom reserve.
     * Used by panels that are themselves the target of a reservation (e.g. the
     * scoreboard, which reserved its own space so it should see the full slot).
     */
    public int getRemainingHeightUnreserved() {
        return screenHeight - nextY - Math.round(BOTTOM_MARGIN * scale);
    }
}
