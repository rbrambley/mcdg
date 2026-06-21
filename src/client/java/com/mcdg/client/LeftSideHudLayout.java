package com.mcdg.client;

/**
 * Manages vertical positioning for left-side HUD elements to prevent overlaps.
 * Coordinates spacing between HoleMapOverlay and RunningScoreboardOverlay.
 */
public final class LeftSideHudLayout {
    private static final int TOP_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 8;
    private static final int HUD_SPACING = 8;
    
    private final int screenHeight;
    private final float scale;
    private int allocatedHeight;
    private int nextY;
    private int reservedTopSpace;
    
    public LeftSideHudLayout(int screenHeight, float scale) {
        this.screenHeight = screenHeight;
        this.scale = scale;
        this.allocatedHeight = 0;
        this.nextY = Math.round(TOP_MARGIN * scale);
        this.reservedTopSpace = 0;
    }
    
    /**
     * Reserves space at the top of the screen (e.g., for Xaero's minimap).
     * This space will be skipped when allocating HUD positions.
     * 
     * @param reservedPixels The number of pixels to reserve at the top
     */
    public void reserveTopSpace(int reservedPixels) {
        this.reservedTopSpace = reservedPixels;
        this.nextY = Math.max(this.nextY, Math.round(TOP_MARGIN * scale) + reservedPixels);
    }
    
    /**
     * Allocates vertical space for a HUD element and returns its Y position.
     * 
     * @param requestedHeight The height needed for the HUD element
     * @return The Y position where the HUD should be rendered
     */
    public int allocateSpace(int requestedHeight) {
        int y = nextY;
        nextY += requestedHeight + Math.round(HUD_SPACING * scale);
        allocatedHeight += requestedHeight + Math.round(HUD_SPACING * scale);
        return y;
    }
    
    /**
     * Gets the remaining available vertical space.
     * 
     * @return Remaining pixels available for HUD elements
     */
    public int getRemainingSpace() {
        return screenHeight - nextY - Math.round(BOTTOM_MARGIN * scale);
    }
    
    /**
     * Gets the total allocated height so far.
     * 
     * @return Total height allocated to HUD elements
     */
    public int getAllocatedHeight() {
        return allocatedHeight;
    }
    
    /**
     * Gets the current scale factor.
     * 
     * @return The scale factor being used
     */
    public float getScale() {
        return scale;
    }
    
    /**
     * Gets the screen height.
     * 
     * @return The total screen height in pixels
     */
    public int getScreenHeight() {
        return screenHeight;
    }
    
    /**
     * Resets the layout to initial state.
     */
    public void reset() {
        allocatedHeight = 0;
        nextY = Math.round(TOP_MARGIN * scale);
    }
}