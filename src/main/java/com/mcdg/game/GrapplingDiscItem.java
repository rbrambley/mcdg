package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A throwable disc that acts as a grappling hook. On hit, pulls the owner toward the impact point.
 * Supports only Mending and Unbreaking enchantments (via vanilla durability mechanics).
 */
public final class GrapplingDiscItem extends Item {
    public static final int MAX_DAMAGE = 64;

    public GrapplingDiscItem(Settings settings) {
        super(settings.maxDamage(MAX_DAMAGE));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.NEUTRAL,
                0.5f, 0.4f / (world.getRandom().nextFloat() * 0.4f + 0.8f));

        if (!world.isClient()) {
            GrapplingDiscEntity entity = new GrapplingDiscEntity(world, user);
            entity.setItem(stack);
            entity.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.5f, 1.0f);
            world.spawnEntity(entity);
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.getAbilities().creativeMode) {
            stack.damage(1, user, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }

        return TypedActionResult.success(stack, world.isClient());
    }
}
