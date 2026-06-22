package com.mcdg.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Soft-dependency bridge for Xaero's Minimap.
 *
 * All Xaero calls are made via reflection so this class compiles and runs
 * correctly whether or not xaerominimap is on the classpath.
 *
 * The result is cached and only re-evaluated when screen resolution or GUI
 * scale changes, so reflection overhead is negligible at steady state.
 *
 * Usage:
 *   int topOffset = XaeroMinimapCompat.getTopLeftReservedPixels(scale);
 * Returns 0 when Xaero is absent, the minimap session is not active, or the
 * minimap is not anchored to the top-left corner.
 */
public final class XaeroMinimapCompat {

    /** Extra margin (unscaled px) between Xaero's minimap bottom and our first panel. */
    private static final int XAERO_BELOW_MARGIN = 6;

    /** Xaero module identifier: "xaerominimap:minimap". */
    private static final String XAERO_MODULE_ID = "xaerominimap:minimap";

    private static final boolean XAERO_PRESENT =
            FabricLoader.getInstance().isModLoaded("xaerominimap");

    // Cache — invalidated when resolution or scale changes
    private static int lastScreenWidth = -1;
    private static int lastScreenHeight = -1;
    private static float lastScale = -1f;
    private static int cachedReservedPixels = 0;

    private XaeroMinimapCompat() {
    }

    /**
     * Returns the number of scaled pixels that should be reserved at the top of
     * the left-side HUD column to avoid overlapping Xaero's minimap.
     *
     * Result is cached per unique (screenWidth, screenHeight, scale) combination.
     *
     * Returns 0 if:
     * - Xaero is not installed
     * - No active minimap session exists
     * - The minimap is not anchored to the top-left corner
     *
     * @param scale The current HUD scale factor
     * @return Scaled pixels to reserve, or 0
     */
    public static int getTopLeftReservedPixels(float scale) {
        if (!XAERO_PRESENT) {
            return 0;
        }

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        int sw = mc != null ? mc.getWindow().getScaledWidth() : -1;
        int sh = mc != null ? mc.getWindow().getScaledHeight() : -1;

        if (sw != lastScreenWidth || sh != lastScreenHeight || scale != lastScale) {
            lastScreenWidth = sw;
            lastScreenHeight = sh;
            lastScale = scale;
            try {
                cachedReservedPixels = queryXaeroReservedPixels(scale);
            } catch (Exception e) {
                // Fail gracefully if Xaero's API changes between versions
                cachedReservedPixels = 0;
            }
        }
        return cachedReservedPixels;
    }

    private static int queryXaeroReservedPixels(float scale) throws Exception {
        // XaeroMinimapSession.getCurrentSession()
        Class<?> sessionClass = Class.forName("xaero.common.XaeroMinimapSession");
        Object session = sessionClass.getMethod("getCurrentSession").invoke(null);
        if (session == null) {
            return 0;
        }

        // session.getMinimapProcessor().getMinimapSize()
        Object processor = sessionClass.getMethod("getMinimapProcessor").invoke(session);
        int minimapSize = (int) processor.getClass().getMethod("getMinimapSize").invoke(processor);
        if (minimapSize <= 0) {
            return 0;
        }

        // Walk HudMod.INSTANCE.getHud().getModuleManager().getModules() to find
        // the minimap module and read its transform corner flags.
        Class<?> hudModClass = Class.forName("xaero.common.HudMod");
        Object hudModInstance = hudModClass.getField("INSTANCE").get(null);
        Object hud = hudModClass.getMethod("getHud").invoke(hudModInstance);
        Object moduleManager = hud.getClass().getMethod("getModuleManager").invoke(hud);
        Iterable<?> modules = (Iterable<?>) moduleManager.getClass()
                .getMethod("getModules").invoke(moduleManager);

        for (Object module : modules) {
            // Use toString() on the Identifier to get "namespace:path" — avoids
            // intermediary vs yarn method name issues at runtime.
            Object modId = module.getClass().getMethod("getId").invoke(module);
            if (!XAERO_MODULE_ID.equals(modId.toString())) {
                continue;
            }

            Object transform = module.getClass().getMethod("getUsedTransform").invoke(module);
            if (transform == null) {
                return 0;
            }
            boolean fromRight = (boolean) transform.getClass().getField("fromRight").get(transform);
            boolean fromBottom = (boolean) transform.getClass().getField("fromBottom").get(transform);

            // Only reserve space when anchored to the top-left corner
            if (fromRight || fromBottom) {
                return 0;
            }

            return Math.round((minimapSize + XAERO_BELOW_MARGIN) * scale);
        }

        return 0;
    }
}
