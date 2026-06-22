package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.game.DiscEnchantment;
import com.mcdg.game.DiscEnchantedBook;
import com.mcdg.game.DiscEnchantmentHelper;
import com.mcdg.game.McdgItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Admin commands for applying and removing disc enchantments.
 * Usage: /mcdg enchant <enchantment> <level>
 *        /mcdg enchant clear
 *        /mcdg enchant givebook <enchantment> <level>
 *        /mcdg enchant givedisc
 */
public final class DiscEnchantmentCommands {
    private DiscEnchantmentCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return literal("enchant")
                .requires(CommandPermission::canUseAdminCommands)
                .then(literal("clear")
                        .executes(context -> executeClear(context)))
                .then(literal("givebook")
                        .then(argument("enchantment", StringArgumentType.word())
                                .suggests(ENCHANTMENT_SUGGESTIONS)
                                .then(argument("level", IntegerArgumentType.integer(1, 3))
                                        .executes(context -> executeGiveBook(
                                                context,
                                                StringArgumentType.getString(context, "enchantment"),
                                                IntegerArgumentType.getInteger(context, "level")
                                        )))))
                .then(literal("givedisc")
                        .executes(context -> executeGiveDisc(context)))
                .then(argument("enchantment", StringArgumentType.word())
                        .suggests(ENCHANTMENT_SUGGESTIONS)
                        .then(argument("level", IntegerArgumentType.integer(1, 3))
                                .executes(context -> executeEnchant(
                                        context,
                                        StringArgumentType.getString(context, "enchantment"),
                                        IntegerArgumentType.getInteger(context, "level")
                                ))));
    }

    private static final SuggestionProvider<ServerCommandSource> ENCHANTMENT_SUGGESTIONS = (context, builder) -> {
        for (DiscEnchantment e : DiscEnchantment.values()) {
            builder.suggest(e.key());
        }
        return builder.buildFuture();
    };

    private static int executeEnchant(CommandContext<ServerCommandSource> context, String enchantmentKey, int level) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(McdgItems.TRAINING_DISC)) {
            source.sendError(Text.literal("You must hold a Training Disc in your main hand."));
            return 0;
        }

        DiscEnchantment enchant = Arrays.stream(DiscEnchantment.values())
                .filter(e -> e.key().equalsIgnoreCase(enchantmentKey))
                .findFirst()
                .orElse(null);

        if (enchant == null) {
            source.sendError(Text.literal("Unknown enchantment: " + enchantmentKey
                    + ". Valid: glide, fade_control, distance"));
            return 0;
        }

        int finalLevel = Math.min(level, enchant.maxLevel());
        DiscEnchantmentHelper.setLevel(stack, enchant, finalLevel);

        source.sendFeedback(() -> Text.literal("Applied " + enchant.displayName() + " " + roman(finalLevel)
                + " to your disc.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(McdgItems.TRAINING_DISC)) {
            source.sendError(Text.literal("You must hold a Training Disc in your main hand."));
            return 0;
        }

        DiscEnchantmentHelper.clear(stack);
        source.sendFeedback(() -> Text.literal("Cleared all disc enchantments.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGiveBook(CommandContext<ServerCommandSource> context, String enchantmentKey, int level) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        DiscEnchantment enchant = Arrays.stream(DiscEnchantment.values())
                .filter(e -> e.key().equalsIgnoreCase(enchantmentKey))
                .findFirst()
                .orElse(null);

        if (enchant == null) {
            source.sendError(Text.literal("Unknown enchantment: " + enchantmentKey
                    + ". Valid: glide, fade_control, distance"));
            return 0;
        }

        ItemStack book = DiscEnchantedBook.create(enchant, level);
        player.getInventory().insertStack(book);

        source.sendFeedback(() -> Text.literal("Gave " + enchant.displayName() + " " + roman(level)
                + " enchanted book.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGiveDisc(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        ItemStack disc = new ItemStack(McdgItems.TRAINING_DISC);
        player.getInventory().insertStack(disc);

        source.sendFeedback(() -> Text.literal("Gave Training Disc.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }
}
