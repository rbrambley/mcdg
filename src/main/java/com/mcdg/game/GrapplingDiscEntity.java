package com.mcdg.game;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Projectile entity for the Grappling Disc. Pulls its owner toward the block it hits.
 */
public class GrapplingDiscEntity extends ProjectileEntity {
    private ItemStack stack = ItemStack.EMPTY;

    public GrapplingDiscEntity(EntityType<? extends GrapplingDiscEntity> type, World world) {
        super(type, world);
    }

    public GrapplingDiscEntity(World world, LivingEntity owner) {
        super(McdgEntityTypes.GRAPPLING_DISC, world);
        this.setOwner(owner);
        this.refreshPositionAndAngles(owner.getX(), owner.getEyeY() - 0.1, owner.getZ(), owner.getYaw(), owner.getPitch());
    }

    public void setItem(ItemStack stack) {
        this.stack = stack.copy();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            Vec3d pos = this.getPos();
            this.getWorld().addParticle(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 0, 0, 0);
            return;
        }

        HitResult hitResult = ProjectileUtil.getCollision(this, this::canHit);
        if (hitResult.getType() != HitResult.Type.MISS) {
            onCollision(hitResult);
        }

        this.setPos(this.getX() + this.getVelocity().x, this.getY() + this.getVelocity().y, this.getZ() + this.getVelocity().z);
        this.setVelocity(this.getVelocity().multiply(0.99));
    }

    @Override
    protected void onBlockHit(BlockHitResult result) {
        if (!this.getWorld().isClient()) {
            Vec3d hitPos = result.getPos();
            Entity owner = this.getOwner();
            if (owner instanceof PlayerEntity player) {
                double dx = hitPos.x - player.getX();
                double dy = hitPos.y - player.getY();
                double dz = hitPos.z - player.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0.1) {
                    double speed = Math.min(2.0, dist * 0.15);
                    player.setVelocity(dx / dist * speed, dy / dist * speed + 0.7, dz / dist * speed);
                    player.velocityModified = true;
                }
            }
        }
        this.discard();
    }

    @Override
    protected void onEntityHit(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target != this.getOwner()) {
            target.damage(this.getDamageSources().thrown(this, this.getOwner()), 2.0f);
        }
        this.discard();
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
