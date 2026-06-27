package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.game.PlayerSkillManager;
import com.mcdg.game.SkillUnlock;
import com.mcdg.net.SkillsScreenSync;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Commands for viewing and debugging player skill unlocks.
 */
public final class SkillCommands {
    private SkillCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("mcdg")
                .then(literal("skills")
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        ServerPlayerEntity player = source.getPlayer();
                        if (player == null) {
                            source.sendError(Text.literal("Only players can use this command."));
                            return 0;
                        }

                        source.sendFeedback(() -> Text.translatable("chat.mcdg.skill_list_header").formatted(Formatting.GOLD), false);
                        Map<SkillUnlock, Boolean> status = PlayerSkillManager.getSkillStatus(player);
                        for (SkillUnlock skill : SkillUnlock.values()) {
                            boolean unlocked = status.getOrDefault(skill, false);
                            if (unlocked) {
                                source.sendFeedback(() -> Text.translatable(
                                        "chat.mcdg.skill_unlocked_status",
                                        skill.displayName(),
                                        Text.translatable("chat.mcdg.skill_status_unlocked").formatted(Formatting.GREEN)
                                    ).formatted(skill.color()), false);
                            } else {
                                source.sendFeedback(() -> Text.translatable(
                                        "chat.mcdg.skill_locked_status",
                                        skill.displayName(),
                                        Text.translatable("chat.mcdg.skill_status_locked").formatted(Formatting.RED),
                                        skill.description()
                                    ).formatted(Formatting.GRAY), false);
                            }
                        }
                        return 1;
                    })
                    .then(literal("gui")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = source.getPlayer();
                            if (player == null) {
                                source.sendError(Text.literal("Only players can use this command."));
                                return 0;
                            }

                            SkillsScreenSync.Payload payload = PlayerSkillManager.createSkillsScreenPayload(player);
                            ServerPlayNetworking.send(player, payload);
                            return 1;
                        })
                    )
                )
            );
        });
    }
}