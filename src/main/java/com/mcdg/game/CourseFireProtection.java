package com.mcdg.game;

import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public final class CourseFireProtection {
    private static final ConcurrentHashMap<RegistryKey<World>, Boolean> ORIGINAL_DO_FIRE_TICK = new ConcurrentHashMap<>();

    private CourseFireProtection() {}

    public static void registerDamageHandler(ActiveCourseManager courseManager) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            if (!courseManager.getActiveParticipantIds().contains(player.getUuid())) {
                return true;
            }
            if (!isFireDamage(source)) {
                return true;
            }
            return false;
        });
    }

    public static void apply(ServerWorld world) {
        ORIGINAL_DO_FIRE_TICK.computeIfAbsent(world.getRegistryKey(), key -> {
            boolean original = world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK);
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false, world.getServer());
            return original;
        });
    }

    public static void remove(ServerWorld world) {
        Boolean original = ORIGINAL_DO_FIRE_TICK.remove(world.getRegistryKey());
        if (original != null) {
            world.getGameRules().get(GameRules.DO_FIRE_TICK).set(original, world.getServer());
        }
    }

    public static boolean isFireDamage(DamageSource source) {
        return source.isOf(DamageTypes.IN_FIRE)
                || source.isOf(DamageTypes.ON_FIRE)
                || source.isOf(DamageTypes.HOT_FLOOR)
                || source.isOf(DamageTypes.FIREBALL)
                || source.isOf(DamageTypes.FIREWORKS);
    }
}
