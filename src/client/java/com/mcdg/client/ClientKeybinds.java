package com.mcdg.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Registers and polls MCDG client keybindings.
 * Keeps GLFW/KeyBinding boilerplate out of {@link McdgClientMod}.
 */
public final class ClientKeybinds {
    private static KeyBinding openMenuKey;
    private static KeyBinding increaseMiniMapSizeKey;
    private static KeyBinding decreaseMiniMapSizeKey;
    private static KeyBinding addWaypointKey;
    private static KeyBinding removeNearestWaypointKey;
    private static KeyBinding toggleWaypointLabelsKey;
    private static KeyBinding lockPowerKey;
    private static KeyBinding cycleThrowStanceKey;

    private ClientKeybinds() {
    }

    public static void register() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.mcdg"
        ));
        increaseMiniMapSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.minimap_size_up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_EQUAL,
                "category.mcdg"
        ));
        decreaseMiniMapSizeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.minimap_size_down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                "category.mcdg"
        ));
        addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.add_waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.mcdg"
        ));
        removeNearestWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.remove_nearest_waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.mcdg"
        ));
        toggleWaypointLabelsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.toggle_waypoint_labels",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category.mcdg"
        ));
        lockPowerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.lock_power",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F,
                "category.mcdg"
        ));
        cycleThrowStanceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.cycle_stance",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.mcdg"
        ));
    }

    public static net.minecraft.text.Text getOpenMenuKeyText() {
        return openMenuKey.getBoundKeyLocalizedText();
    }

    public static void forEachOpenMenuPress(Runnable action) {
        while (openMenuKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachMinimapSizeUpPress(Runnable action) {
        while (increaseMiniMapSizeKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachMinimapSizeDownPress(Runnable action) {
        while (decreaseMiniMapSizeKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachAddWaypointPress(Runnable action) {
        while (addWaypointKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachRemoveNearestWaypointPress(Runnable action) {
        while (removeNearestWaypointKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachToggleWaypointLabelsPress(Runnable action) {
        while (toggleWaypointLabelsKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachLockPowerPress(Runnable action) {
        while (lockPowerKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachStanceCyclePress(Runnable action) {
        while (cycleThrowStanceKey.wasPressed()) {
            action.run();
        }
    }
}
