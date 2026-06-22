package com.mcdg.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A throwable disc that acts as a boomerang. Damages mobs and returns to the thrower.
 * Tier determines max range. Supports weapon enchantments via vanilla mechanics.
 */
public final class BoomerangDiscItem extends Item {
    public enum Tier {
        COPPER(20, 4.0f),
        IRON(30, 5.0f),
        DIAMOND(40, 6.0f),
        NETHERITE(50, 7.0f);

        private final int maxRangeBlocks;
        private final float baseDamage;

        Tier(int maxRangeBlocks, float baseDamage) {
            this.maxRangeBlocks = maxRangeBlocks;
            this.baseDamage = baseDamage;
        }

        public int maxRange() { return maxRangeBlocks; }
        public float baseDamage() { return baseDamage; }
    }

    public static final int MAX_DAMAGE = 250;
    private final Tier tier;

    public BoomerangDiscItem(Tier tier) {
        super(new Item.Settings().maxDamage(MAX_DAMAGE));
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS,
                0.5f, 0.4f / (world.getRandom().nextFloat() * 0.4f + 0.8f));

        if (!world.isClient()) {
            BoomerangDiscEntity entity = new BoomerangDiscEntity(world, user, stack);
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
