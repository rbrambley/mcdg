package com.mcdg.game;

import com.mcdg.McdgMod;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Throwable disc that launches the player along a calculated flight trajectory.
 * Hold right-click to charge, release to fly. No player control during flight.
 * Can be upgraded to netherite tier at the Disc Workbench to enable player control.
 */
public class ElytraDiscItem extends Item {
    private static final int MAX_CHARGE_TICKS = 120;
    private static final float MAX_POWER_MULTIPLIER = 1.25f;
    private static final float MIN_VELOCITY = 0.7f;
    private static final float VELOCITY_SPAN = 1.6f;

    // Server-side power lock tracking (per player)
    private static final Map<UUID, Boolean> SERVER_POWER_LOCKED = new HashMap<>();
    private static final Map<UUID, Float> SERVER_LOCKED_CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> SERVER_LOCKED_TICKS = new HashMap<>();
    // Server-side stance tracking (per player)
    private static final Map<UUID, ThrowStance> SERVER_PLAYER_STANCE = new HashMap<>();
    private static final Map<UUID, ReleaseAngle> SERVER_PLAYER_ANGLE = new HashMap<>();

    private final boolean playerControl;

    public ElytraDiscItem(Settings settings) {
        this(settings, false);
    }

    public ElytraDiscItem(Settings settings, boolean playerControl) {
        super(settings);
        this.playerControl = playerControl;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            ChargedDiscItem.setClientChargeVisible(true);
            ChargedDiscItem.setClientChargePercent(0.0f);
            ChargedDiscItem.setPowerLocked(false);
            ChargedDiscItem.setLockedChargePercent(0.0f);
            ChargedDiscItem.setLockedTicks(0);
            ChargedDiscItem.setLastAudioThreshold(0);
        } else {
            // Reset server-side power lock state on new charge
            SERVER_POWER_LOCKED.remove(user.getUuid());
            SERVER_LOCKED_CHARGE.remove(user.getUuid());
            SERVER_LOCKED_TICKS.remove(user.getUuid());
        }

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        float charge = computeChargePercent(usedTicks);

        if (world.isClient()) {
            ChargedDiscItem.setClientChargeVisible(true);

            // Handle power lock - once locked, stop calculating from remainingUseTicks
            if (ChargedDiscItem.isPowerLocked()) {
                charge = computeChargePercent(ChargedDiscItem.getLockedTicks());
                ChargedDiscItem.setClientChargePercent(charge);
            } else {
                ChargedDiscItem.setClientChargePercent(charge);
            }

            // Handle audio thresholds
            int[] thresholds = {25, 50, 75, 100};
            int chargePercent = (int) (ChargedDiscItem.getClientChargePercent() * 100);
            for (int threshold : thresholds) {
                if (chargePercent >= threshold && ChargedDiscItem.getLastAudioThreshold() < threshold) {
                    ChargedDiscItem.setLastAudioThreshold(threshold);
                    float pitch = 0.8f + (threshold / 100.0f) * 0.4f;
                    if (user instanceof PlayerEntity player) {
                        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, pitch);
                    }
                    break;
                }
            }

            return;
        }

        if (!(user instanceof ServerPlayerEntity)) {
            return;
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()) {
            ChargedDiscItem.setClientChargeVisible(false);
            ChargedDiscItem.setClientChargePercent(0.0f);
            ChargedDiscItem.setLockedChargePercent(0.0f);
            ChargedDiscItem.setLockedTicks(0);
            ChargedDiscItem.setLastAudioThreshold(0);
            ChargedDiscItem.resetPowerLock();
            return;
        }

        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        // Check if power is locked on the server
        UUID playerUuid = serverPlayer.getUuid();
        Boolean serverLocked = SERVER_POWER_LOCKED.get(playerUuid);
        Integer lockedTicks = SERVER_LOCKED_TICKS.get(playerUuid);

        float charge;
        int usedTicks;
        if (serverLocked != null && serverLocked && lockedTicks != null) {
            usedTicks = lockedTicks;
            charge = computeChargePercent(usedTicks);
            McdgMod.LOGGER.info("Using LOCKED charge for elytra launch: player={} lockedTicks={} charge={}", playerUuid, lockedTicks, String.format("%.3f", charge));
        } else {
            usedTicks = getMaxUseTime(stack) - remainingUseTicks;
            charge = computeChargePercent(usedTicks);
            McdgMod.LOGGER.info("Using REAL-TIME charge for elytra launch: player={} usedTicks={} charge={}", playerUuid, usedTicks, String.format("%.1f", charge*100));
        }

        if (charge <= 0.0f) {
            return;
        }

        float velocity = MIN_VELOCITY + charge * VELOCITY_SPAN;

        // Read disc enchantments from the held stack
        Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(stack);
        int distanceLevel = enchantments.getOrDefault(DiscEnchantment.DISTANCE, 0);
        if (distanceLevel > 0) {
            velocity *= (1.0f + distanceLevel * DiscEnchantment.DISTANCE.perLevelMultiplier());
        }

        // Get server-side stance (defaults to OVERHAND/FLAT if not set)
        ThrowStance stance = SERVER_PLAYER_STANCE.getOrDefault(playerUuid, ThrowStance.OVERHAND);
        ReleaseAngle angle = SERVER_PLAYER_ANGLE.getOrDefault(playerUuid, ReleaseAngle.FLAT);

        // Calculate initial velocity vector
        float pitch = serverPlayer.getPitch();
        float yaw = serverPlayer.getYaw();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double velX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
        double velY = -Math.sin(pitchRad) * velocity;
        double velZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
        Vec3d initialVelocity = new Vec3d(velX, velY, velZ);

        Vec3d startPos = new Vec3d(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ());

        // Get current wind for trajectory calculation
        Vec3d windVelocity = WindManager.getWindState(serverPlayer.getServerWorld()).velocity();

        // Calculate complete trajectory
        TrajectoryCalculator.TrajectoryResult trajectory = TrajectoryCalculator.calculateTrajectory(
                serverPlayer.getServerWorld(),
                startPos,
                initialVelocity,
                yaw,
                charge,
                stance,
                angle,
                enchantments,
                windVelocity
        );

        McdgMod.LOGGER.info(
                "Elytra trajectory calculated | player={} distance={}ft drift={}ft {} flightTicks={} upgraded={}",
                playerUuid,
                String.format("%.1f", trajectory.totalDistanceFt()),
                String.format("%.1f", Math.abs(trajectory.lateralDriftFt())),
                trajectory.lateralDriftFt() > 0 ? "RIGHT" : "LEFT",
                trajectory.flightTicks(),
                playerControl
        );

        // Initialize elytra flight
        ElytraFlightController.startFlight(
                serverPlayer,
                trajectory,
                playerControl
        );

        // Clear server-side power lock state after launch
        SERVER_POWER_LOCKED.remove(playerUuid);
        SERVER_LOCKED_CHARGE.remove(playerUuid);
        SERVER_LOCKED_TICKS.remove(playerUuid);

        world.playSound(
                null,
                serverPlayer.getX(),
                serverPlayer.getY(),
                serverPlayer.getZ(),
                SoundEvents.ENTITY_ENDER_PEARL_THROW,
                SoundCategory.PLAYERS,
                0.5f,
                0.8f + world.getRandom().nextFloat() * 0.4f
        );

        serverPlayer.swingHand(Hand.MAIN_HAND, true);
        serverPlayer.sendMessage(buildLaunchText(charge, playerControl), true);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    private static float computeChargePercent(int usedTicks) {
        float charge = usedTicks / (float) MAX_CHARGE_TICKS;
        return Math.max(0.0f, Math.min(MAX_POWER_MULTIPLIER, charge));
    }


    // Server-side power lock state management
    public static void setServerPowerLocked(UUID playerUuid, boolean locked, float chargePercent) {
        if (!locked) {
            return;
        }
        Boolean alreadyLocked = SERVER_POWER_LOCKED.get(playerUuid);
        if (alreadyLocked != null && alreadyLocked) {
            return;
        }
        SERVER_POWER_LOCKED.put(playerUuid, true);
        SERVER_LOCKED_CHARGE.put(playerUuid, chargePercent);
        int lockedTicks = Math.round(chargePercent * MAX_CHARGE_TICKS);
        SERVER_LOCKED_TICKS.put(playerUuid, lockedTicks);
        McdgMod.LOGGER.info("Elytra power LOCKED for player {} at charge={} ticks={}", playerUuid, String.format("%.3f", chargePercent), lockedTicks);
    }

    // Server-side stance state management
    public static void setServerStance(UUID playerUuid, ThrowStance stance, ReleaseAngle angle) {
        SERVER_PLAYER_STANCE.put(playerUuid, stance);
        SERVER_PLAYER_ANGLE.put(playerUuid, angle);
        McdgMod.LOGGER.debug("Elytra stance set for player {}: stance={} angle={}", playerUuid, stance, angle);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, net.minecraft.client.item.TooltipType type) {
        // Show MCDG disc enchantments
        Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(stack);
        for (Map.Entry<DiscEnchantment, Integer> entry : enchantments.entrySet()) {
            DiscEnchantment enchant = entry.getKey();
            int level = entry.getValue();
            String roman = romanLevel(level);
            tooltip.add(Text.literal(enchant.displayName() + " " + roman).formatted(enchant.color()));
        }

        // Show netherite upgrade status
        if (playerControl) {
            tooltip.add(Text.literal("Netherite Upgraded").formatted(Formatting.DARK_PURPLE));
        }
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(level);
        };
    }

    private static Text buildLaunchText(float charge, boolean isNetheriteUpgraded) {
        int percent = Math.round(charge * 100);
        if (isNetheriteUpgraded) {
            return Text.literal("Elytra launch at " + percent + "% power (Player Control Enabled)").formatted(Formatting.AQUA);
        }
        return Text.literal("Elytra launch at " + percent + "% power").formatted(Formatting.YELLOW);
    }
}
