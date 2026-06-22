package com.mcdg.game;

import java.util.List;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A consumable book that applies a specific disc enchantment when used on a
 * {@link ChargedDiscItem} (Training Disc).
 */
public final class DiscEnchantedBook extends Item {
    private static final String NBT_ENCHANT_KEY = "McdgBookEnchantment";
    private static final String NBT_LEVEL_KEY = "McdgBookLevel";

    public DiscEnchantedBook(Settings settings) {
        super(settings);
    }

    /**
     * Creates a new enchanted book stack holding the given enchantment and level.
     */
    public static ItemStack create(DiscEnchantment enchantment, int level) {
        ItemStack stack = new ItemStack(McdgItems.DISC_ENCHANTED_BOOK);
        int clampedLevel = Math.max(1, Math.min(level, enchantment.maxLevel()));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(NBT_ENCHANT_KEY, enchantment.key());
            nbt.putInt(NBT_LEVEL_KEY, clampedLevel);
        });
        return stack;
    }

    /**
     * Returns the enchantment stored in this book, or null if none / invalid.
     */
    public static DiscEnchantment getEnchantment(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        NbtCompound nbt = readCustomNbt(stack);
        if (nbt == null || !nbt.contains(NBT_ENCHANT_KEY)) {
            return null;
        }
        String key = nbt.getString(NBT_ENCHANT_KEY);
        for (DiscEnchantment e : DiscEnchantment.values()) {
            if (e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    public static int getLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        NbtCompound nbt = readCustomNbt(stack);
        if (nbt == null) {
            return 0;
        }
        return nbt.getInt(NBT_LEVEL_KEY);
    }

    private static NbtCompound readCustomNbt(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        return customData.copyNbt();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack bookStack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.pass(bookStack);
        }

        DiscEnchantment enchant = getEnchantment(bookStack);
        int level = getLevel(bookStack);
        if (enchant == null || level <= 0) {
            return TypedActionResult.pass(bookStack);
        }

        ItemStack mainHand = user.getMainHandStack();
        if (!mainHand.isOf(McdgItems.TRAINING_DISC)) {
            if (user instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Hold a Training Disc in your main hand to apply this enchantment.").formatted(net.minecraft.util.Formatting.RED), true);
            }
            return TypedActionResult.fail(bookStack);
        }

        int currentLevel = DiscEnchantmentHelper.getLevel(mainHand, enchant);
        if (currentLevel >= level) {
            if (user instanceof ServerPlayerEntity sp) {
                sp.sendMessage(Text.literal("Your disc already has " + enchant.displayName() + " " + roman(level) + " or higher.").formatted(net.minecraft.util.Formatting.YELLOW), true);
            }
            return TypedActionResult.fail(bookStack);
        }

        DiscEnchantmentHelper.setLevel(mainHand, enchant, level);
        bookStack.decrement(1);

        if (user instanceof ServerPlayerEntity sp) {
            sp.sendMessage(Text.literal("Applied " + enchant.displayName() + " " + roman(level) + " to your disc!").formatted(net.minecraft.util.Formatting.GREEN), true);
        }

        return TypedActionResult.success(bookStack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, net.minecraft.client.item.TooltipType type) {
        DiscEnchantment enchant = getEnchantment(stack);
        int level = getLevel(stack);
        if (enchant != null && level > 0) {
            tooltip.add(Text.literal(enchant.displayName() + " " + roman(level)).formatted(enchant.color()));
            tooltip.add(Text.literal("Use while holding a Training Disc").formatted(net.minecraft.util.Formatting.GRAY));
        } else {
            tooltip.add(Text.literal("Empty book").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        }
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
