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
    private static KeyBinding toggleHoleMapKey;
    private static KeyBinding lockPowerKey;
    private static KeyBinding cycleThrowStanceKey;
    private static KeyBinding angleLeftKey;
    private static KeyBinding angleRightKey;

    private ClientKeybinds() {
    }

    public static void register() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.mcdg"
        ));
        toggleHoleMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.toggle_hole_map",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
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
        // Phase 3: Left/Right arrow keys for release angle adjustment
        angleLeftKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.angle_left",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT,
                "category.mcdg"
        ));
        angleRightKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcdg.angle_right",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT,
                "category.mcdg"
        ));
    }

    public static net.minecraft.text.Text getOpenMenuKeyText() {
        return openMenuKey.getBoundKeyLocalizedText();
    }

    public static net.minecraft.text.Text getHoleMapKeyText() {
        return toggleHoleMapKey.getBoundKeyLocalizedText();
    }

    public static net.minecraft.text.Text getCycleStanceKeyText() {
        return cycleThrowStanceKey.getBoundKeyLocalizedText();
    }

    public static net.minecraft.text.Text getAngleLeftKeyText() {
        return angleLeftKey.getBoundKeyLocalizedText();
    }

    public static net.minecraft.text.Text getAngleRightKeyText() {
        return angleRightKey.getBoundKeyLocalizedText();
    }

    public static void forEachOpenMenuPress(Runnable action) {
        while (openMenuKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachHoleMapTogglePress(Runnable action) {
        while (toggleHoleMapKey.wasPressed()) {
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

    // Phase 3: Angle adjustment key polling
    public static void forEachAngleLeftPress(Runnable action) {
        while (angleLeftKey.wasPressed()) {
            action.run();
        }
    }

    public static void forEachAngleRightPress(Runnable action) {
        while (angleRightKey.wasPressed()) {
            action.run();
        }
    }
}
