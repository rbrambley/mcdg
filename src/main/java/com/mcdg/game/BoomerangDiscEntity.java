package com.mcdg.game;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BoomerangDiscEntity extends ProjectileEntity {
    private ItemStack stack = ItemStack.EMPTY;
    private int maxRange = 20;
    private float baseDamage = 4.0f;
    private boolean returning = false;
    private double distanceTraveled = 0;
    private Vec3d lastPos = Vec3d.ZERO;

    public BoomerangDiscEntity(EntityType<? extends BoomerangDiscEntity> type, World world) {
        super(type, world);
    }

    public BoomerangDiscEntity(World world, LivingEntity owner, ItemStack stack) {
        super(McdgEntityTypes.BOOMERANG_DISC, world);
        this.setOwner(owner);
        this.refreshPositionAndAngles(owner.getX(), owner.getEyeY() - 0.1, owner.getZ(), owner.getYaw(), owner.getPitch());
        this.stack = stack.copy();
        if (stack.getItem() instanceof BoomerangDiscItem item) {
            this.maxRange = item.getTier().maxRange();
            this.baseDamage = item.getTier().baseDamage();
        }
        this.lastPos = this.getPos();
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d pos = this.getPos();
        if (this.getWorld().isClient()) {
            this.getWorld().addParticle(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 0, 0, 0);
            return;
        }

        if (returning) {
            Entity owner = this.getOwner();
            if (owner == null || !owner.isAlive()) {
                this.discard();
                return;
            }
            Vec3d ownerPos = new Vec3d(owner.getX(), owner.getEyeY(), owner.getZ());
            Vec3d toOwner = ownerPos.subtract(pos);
            double dist = toOwner.length();
            if (dist < 1.5) {
                // Returned to owner
                if (owner instanceof PlayerEntity player && !stack.isEmpty()) {
                    if (!player.getInventory().insertStack(stack)) {
                        player.dropItem(stack, false);
                    }
                }
                this.discard();
                return;
            }
            Vec3d vel = toOwner.normalize().multiply(1.8);
            this.setVelocity(vel);
        } else {
            distanceTraveled += pos.squaredDistanceTo(lastPos);
            lastPos = pos;
            if (distanceTraveled >= maxRange * maxRange) {
                returning = true;
            }

            HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
            if (hitResult.getType() == HitResult.Type.ENTITY) {
                onEntityHit((EntityHitResult) hitResult);
            }
        }

        this.setPos(pos.x + this.getVelocity().x, pos.y + this.getVelocity().y, pos.z + this.getVelocity().z);
    }

    protected void onEntityHit(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target == this.getOwner()) {
            return;
        }
        if (target instanceof LivingEntity living) {
            float damage = baseDamage;
            int sharpness = EnchantmentHelper.getLevel(Enchantments.SHARPNESS, stack);
            if (sharpness > 0) {
                damage += sharpness * 0.5f + 0.5f;
            }
            int fireAspect = EnchantmentHelper.getLevel(Enchantments.FIRE_ASPECT, stack);
            DamageSource source = this.getDamageSources().thrown(this, this.getOwner());
            if (living.damage(source, damage)) {
                if (fireAspect > 0) {
                    living.setOnFireFor(fireAspect * 4);
                }
                int knockback = EnchantmentHelper.getLevel(Enchantments.KNOCKBACK, stack);
                if (knockback > 0) {
                    living.takeKnockback(knockback * 0.4, -this.getVelocity().x, -this.getVelocity().z);
                }
            }
        }
        // Bounce off entity and start returning
        returning = true;
    }

    @Override
    protected void onBlockHit(net.minecraft.util.hit.BlockHitResult result) {
        if (!this.getWorld().isClient() && !returning) {
            returning = true;
        }
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
