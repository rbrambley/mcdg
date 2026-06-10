package com.mcdg.game;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Spawns a tamed pet companion after an ace, once the cinematic has finished.
 * Delay is ~4 seconds (80 ticks) to clear the 3.6s ace cinematic.
 */
public final class AceCompanionService {
    private static final int SPAWN_DELAY_TICKS = 80;
    private static final Random RANDOM = new Random();

    private static final List<PendingCompanion> PENDING = new ArrayList<>();

    private AceCompanionService() {
    }

    public static void scheduleForPlayer(UUID playerId, long currentTick) {
        PENDING.add(new PendingCompanion(playerId, currentTick + SPAWN_DELAY_TICKS));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.getOverworld().getTime();
        PENDING.removeIf(pending -> {
            if (now < pending.spawnAtTick()) {
                return false;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.playerId());
            if (player == null) {
                return true;
            }
            spawnCompanion(player);
            return true;
        });
    }

    public static void reset() {
        PENDING.clear();
    }

    private static void spawnCompanion(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos feet = player.getBlockPos();
        BlockPos spawnPos = findSpawnNear(world, feet);

        int choice = RANDOM.nextInt(3);
        LivingEntity pet;
        String petName;

        switch (choice) {
            case 0 -> {
                WolfEntity wolf = new WolfEntity(EntityType.WOLF, world);
                wolf.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                wolf.setOwnerUuid(player.getUuid());
                wolf.setTamed(true, false);
                wolf.setSitting(false);
                world.spawnEntity(wolf);
                pet = wolf;
                petName = "A wolf joins your pack!";
            }
            case 1 -> {
                CatEntity cat = new CatEntity(EntityType.CAT, world);
                cat.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                cat.setOwnerUuid(player.getUuid());
                cat.setTamed(true, false);
                cat.setSitting(false);
                world.spawnEntity(cat);
                pet = cat;
                petName = "A cat chooses you!";
            }
            default -> {
                ParrotEntity parrot = new ParrotEntity(EntityType.PARROT, world);
                parrot.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                parrot.setOwnerUuid(player.getUuid());
                parrot.setTamed(true, false);
                int variantIndex = RANDOM.nextInt(ParrotEntity.Variant.values().length);
                parrot.setVariant(ParrotEntity.Variant.values()[variantIndex]);
                world.spawnEntity(parrot);
                pet = parrot;
                petName = "A parrot lands on your shoulder!";
            }
        }

        McdgMod.LOGGER.info(
                "Ace companion spawned | player={} pet={} pos=({},{},{})",
                player.getGameProfile().getName(),
                pet.getType().getUntranslatedName(),
                spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()
        );

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(8, 50, 15));
        player.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("Ace Companion!").formatted(Formatting.GOLD, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal(petName).formatted(Formatting.YELLOW)));
    }

    private static BlockPos findSpawnNear(ServerWorld world, BlockPos origin) {
        int[] offsets = {1, -1, 2, -2};
        for (int dz : offsets) {
            for (int dx : offsets) {
                BlockPos candidate = origin.add(dx, 0, dz);
                if (isValidSpawn(world, candidate)) {
                    return candidate;
                }
                BlockPos up = candidate.up();
                if (isValidSpawn(world, up)) {
                    return up;
                }
            }
        }
        return origin;
    }

    private static boolean isValidSpawn(ServerWorld world, BlockPos feet) {
        BlockPos head = feet.up();
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }
        BlockPos ground = feet.down();
        return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
    }

    private record PendingCompanion(UUID playerId, long spawnAtTick) {
    }
}
