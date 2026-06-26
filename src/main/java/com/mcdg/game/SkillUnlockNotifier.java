package com.mcdg.game;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Sends unlock notifications to players. Kept separate from {@link PlayerSkillManager}
 * so that the skill data logic can be loaded without pulling in Minecraft text classes.
 */
final class SkillUnlockNotifier {
    private SkillUnlockNotifier() {}

    static void notifyUnlock(ServerPlayerEntity player, SkillUnlock skill) {
        player.sendMessage(
            Text.translatable("tooltip.mcdg.skill_unlocked", skill.displayName()).formatted(skill.color(), Formatting.BOLD),
            false
        );
    }
}
