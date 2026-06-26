package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.game.ThrowStance;
import com.mcdg.game.ReleaseAngle;
import com.mcdg.net.ThrowTrailStartSync;

import com.mcdg.rules.TournamentRulesetManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChargedDiscItem extends Item {
    private static final int MAX_CHARGE_TICKS = 120;
    private static final float MAX_POWER_MULTIPLIER = 1.25f;
    private static final float MIN_VELOCITY = 0.7f;
    private static final float VELOCITY_SPAN = 1.6f;

    // Client-side only fields (for visual feedback)
    private static boolean clientChargeVisible;
    private static float clientChargePercent;
    private static boolean powerLocked;
    private static float lockedChargePercent;
    private static int lockedTicks;  // Track ticks at lock time
    private static int lastAudioThreshold;

    // Server-side power lock tracking (per player)
    private static final Map<UUID, Boolean> SERVER_POWER_LOCKED = new HashMap<>();
    private static final Map<UUID, Float> SERVER_LOCKED_CHARGE = new HashMap<>();
    private static final Map<UUID, Integer> SERVER_LOCKED_TICKS = new HashMap<>();
    // Server-side stance tracking (per player)
    private static final Map<UUID, ThrowStance> SERVER_PLAYER_STANCE = new HashMap<>();
    private static final Map<UUID, ReleaseAngle> SERVER_PLAYER_ANGLE = new HashMap<>();

    private final ActiveCourseManager courseManager;
    private final RoundStateManager roundStateManager;
    private final TournamentRulesetManager rulesetManager;
    private final boolean strictFlowDebug;

    public ChargedDiscItem(
            Settings settings,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean strictFlowDebug
    ) {
        super(settings);
        this.courseManager = courseManager;
        this.roundStateManager = roundStateManager;
        this.rulesetManager = rulesetManager;
        this.strictFlowDebug = strictFlowDebug;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        
        // Check if player has an enchanted book in off-hand and defer to it
        ItemStack offHand = user.getStackInHand(Hand.OFF_HAND);
        if (offHand.isOf(McdgItems.DISC_ENCHANTED_BOOK)) {
            // Let the book handle the interaction, but return pass to avoid consuming the disc
            offHand.getItem().use(world, user, Hand.OFF_HAND);
            return TypedActionResult.pass(stack);
        }
        
        if (!world.isClient() && !courseManager.isRoundActive()) {
            if (user instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("No active round. Use /mcdg startround."), true);
            }
            return TypedActionResult.fail(stack);
        }

        if (world.isClient()) {
            clientChargeVisible = true;
            clientChargePercent = 0.0f;
            powerLocked = false;
            lockedChargePercent = 0.0f;
            lockedTicks = 0;
            lastAudioThreshold = 0;
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
        if (!world.isClient() && !courseManager.isRoundActive()) {
            return;
        }

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        float charge = computeChargePercent(usedTicks);

        if (world.isClient()) {
            clientChargeVisible = true;

            // Handle power lock - once locked, stop calculating from remainingUseTicks
            if (powerLocked) {
                // Use the locked tick count, don't continue accumulating
                charge = computeChargePercent(lockedTicks);
                clientChargePercent = charge;
            } else {
                clientChargePercent = charge;
            }

            // Handle audio thresholds - check all thresholds that may have been crossed
            int[] thresholds = {25, 50, 75, 100};
            int chargePercent = (int) (clientChargePercent * 100);
            for (int threshold : thresholds) {
                if (chargePercent >= threshold && lastAudioThreshold < threshold) {
                    lastAudioThreshold = threshold;
                    float pitch = 0.8f + (threshold / 100.0f) * 0.4f;
                    // Play sound through the player entity for client-side audio
                    if (user instanceof PlayerEntity player) {
                        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, pitch);
                    }
                    break; // Only play one sound per tick
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
            clientChargeVisible = false;
            clientChargePercent = 0.0f;
            powerLocked = false;
            lockedChargePercent = 0.0f;
            lockedTicks = 0;
            lastAudioThreshold = 0;
            return;
        }

        if (!(user instanceof ServerPlayerEntity serverPlayer) || !courseManager.isRoundActive()) {
            return;
        }

        // Always enforce throw-from-lie across rulesets; strict mode only affects landing penalties.
        var state = roundStateManager.getState(serverPlayer.getUuid()).orElse(null);
        if (state != null) {
            HoleProgressTracker.ThrowTurnGate turnGate = HoleProgressTracker.evaluateThrowGate(serverPlayer, courseManager, roundStateManager);
            if (!turnGate.isAllowed()) {
                serverPlayer.sendMessage(Text.literal(turnGate.message()).formatted(Formatting.YELLOW), true);
                return;
            }

            if (ThrowResolver.isThrowResolutionPending(serverPlayer.getUuid(), state.totalStrokes())) {
                String snapshot = ThrowResolver.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                McdgMod.LOGGER.info(
                    "Throw gate pending resolution | player={} total={} hole={} lie={} playerPos={} mode={} snapshot={}",
                    serverPlayer.getGameProfile().getName(),
                    state.totalStrokes(),
                    state.currentHole(),
                    formatPos(state.lie()),
                    formatPos(serverPlayer.getBlockPos()),
                    rulesetManager.getActiveRuleset().name(),
                    snapshot
                );
                serverPlayer.sendMessage(
                    Text.literal("Wait for the previous throw to finish resolving before throwing again.")
                        .formatted(Formatting.YELLOW),
                    true
                );
                return;
            }

            if (strictFlowDebug) {
                String snapshot = ThrowResolver.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                McdgMod.LOGGER.info(
                    "Throw gate | player={} total={} hole={} lie={} playerPos={} allowed={} mode={} snapshot={}",
                        serverPlayer.getGameProfile().getName(),
                        state.totalStrokes(),
                        state.currentHole(),
                        formatPos(state.lie()),
                        formatPos(serverPlayer.getBlockPos()),
                        rulesetManager.allowedLieToleranceBlocks(),
                        rulesetManager.getActiveRuleset().name(),
                        snapshot
                );
            }

            int distanceFromLie = horizontalDistance(serverPlayer.getBlockPos(), state.lie());
            int allowedDistance = rulesetManager.allowedLieToleranceBlocks();
            if (distanceFromLie > allowedDistance) {
                String snapshot = ThrowResolver.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                McdgMod.LOGGER.warn(
                        "Throw gate blocked | player={} hole={} total={} holeStrokes={} distanceFromLie={} allowed={} stateLie={} playerPos={} mode={} snapshot={}",
                        serverPlayer.getGameProfile().getName(),
                        state.currentHole(),
                        state.totalStrokes(),
                        state.holeStrokes(),
                        distanceFromLie,
                        allowedDistance,
                        formatPos(state.lie()),
                        formatPos(serverPlayer.getBlockPos()),
                        rulesetManager.getActiveRuleset().name(),
                        snapshot
                );
                serverPlayer.sendMessage(
                        Text.literal(
                                "Move back to your lie before throwing! (" + distanceFromLie + " blocks away, max " + allowedDistance + ")"
                        ).formatted(Formatting.RED),
                        true
                );
                return;
            }
        }

        // Check if power is locked on the server
        UUID playerUuid = serverPlayer.getUuid();
        Boolean serverLocked = SERVER_POWER_LOCKED.get(playerUuid);
        Integer lockedTicks = SERVER_LOCKED_TICKS.get(playerUuid);

        float charge;
        int usedTicks;
        if (serverLocked != null && serverLocked && lockedTicks != null) {
            // Currently locked - use the locked tick count (charge stops accumulating)
            usedTicks = lockedTicks;
            charge = computeChargePercent(usedTicks);
            McdgMod.LOGGER.info("Using LOCKED charge for throw: player={} lockedTicks={} charge={}", playerUuid, lockedTicks, String.format("%.3f", charge));
        } else {
            // Not locked - use real-time calculated charge
            usedTicks = getMaxUseTime(stack) - remainingUseTicks;
            charge = computeChargePercent(usedTicks);
            String source = (lockedTicks != null) ? "(was locked at " + lockedTicks + " ticks but unlocked)" : "(never locked)";
            McdgMod.LOGGER.info("Using REAL-TIME charge for throw: player={} usedTicks={} charge={}% {}", playerUuid, usedTicks, String.format("%.1f", charge*100), source);
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

        // Apply tier-based disc stats (Phase 3.1)
        DiscStats stats = getDiscStats(stack);
        // Apply accessory and skill effects to the effective disc stats
        stats = PlayerThrowStats.applyPlayerEffects(stats, serverPlayer);
        velocity *= (float) stats.throwSpeedMultiplier();
        velocity *= (1.0f + PlayerThrowStats.getPowerMultiplierBonus(serverPlayer));

        // Calculate throw trajectory (predicted flight path, no pearl entity)
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

        // Get current wind for trajectory calculation and apply tier wind resistance
        Vec3d windVelocity = WindManager.getWindState(serverPlayer.getServerWorld()).velocity();
        Vec3d effectiveWind = stats.applyWindResistance(windVelocity);

        // Calculate complete trajectory (terrain-aware collision)
        TrajectoryCalculator.TrajectoryResult trajectory = TrajectoryCalculator.calculateTrajectory(
                serverPlayer.getServerWorld(),
                startPos,
                initialVelocity,
                yaw,
                charge,
                stance,
                angle,
                enchantments,
                effectiveWind,
                stats,
                PlayerThrowStats.getAnglePenaltyReduction(serverPlayer)
        );

        McdgMod.LOGGER.info(
                "Trajectory calculated | player={} distance={}ft drift={}ft {} flightTicks={} stance={} angle={}",
                playerUuid,
                String.format("%.1f", trajectory.totalDistanceFt()),
                String.format("%.1f", Math.abs(trajectory.lateralDriftFt())),
                trajectory.lateralDriftFt() > 0 ? "RIGHT" : "LEFT",
                trajectory.flightTicks(),
                stance,
                angle
        );

        // Send trail start packet immediately for real-time progressive trail rendering
        ThrowTrailStartSync.Payload startPayload = new ThrowTrailStartSync.Payload(
                serverPlayer.getUuid(),
                trajectory.pathPoints(),
                trajectory.flightTicks(),
                stance,
                angle
        );

        McdgMod.LOGGER.info(
                "Sending trail start packet | thrower={} pathPoints={} flightTicks={} stance={} angle={}",
                playerUuid,
                trajectory.pathPoints().length,
                trajectory.flightTicks(),
                stance,
                angle
        );

        // Send to all active participants immediately (includes thrower if enrolled)
        boolean throwerSent = false;
        for (UUID participantId : courseManager.getActiveParticipantIds()) {
            ServerPlayerEntity participant = serverPlayer.getServer().getPlayerManager().getPlayer(participantId);
            if (participant != null) {
                ServerPlayNetworking.send(participant, startPayload);
            }
            if (playerUuid.equals(participantId)) {
                throwerSent = true;
            }
        }
        if (!throwerSent) {
            ServerPlayNetworking.send(serverPlayer, startPayload);
        }

        // Initialize throw tracking with calculated landing position
        ThrowResolver.registerCalculatedThrow(
                serverPlayer.getUuid(),
                world.getTime(),
                trajectory.landingPosition(),
                trajectory.flightTicks(),
                trajectory.pathPoints(),
                trajectory.totalDistanceFt(),
                trajectory.lateralDriftFt(),
                stance,
                angle
        );

        // Pre-load landing chunk so the eventual teleport doesn't freeze
        BlockPos landingFeet = new BlockPos(
                (int) Math.round(trajectory.landingPosition().x),
                (int) Math.round(trajectory.landingPosition().y),
                (int) Math.round(trajectory.landingPosition().z)
        );
        RoundChunkLoader.addThrowLandingTicket((net.minecraft.server.world.ServerWorld) world, landingFeet);

        // Clear server-side power lock state after throw
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

        BlockPos recordedThrowLie = state == null ? serverPlayer.getBlockPos() : state.lie();
        roundStateManager.recordThrow(serverPlayer.getUuid(), recordedThrowLie);
        StaminaXpService.consumeThrowStamina(serverPlayer, charge);
        DiscTier thrownTier = stack.getItem() instanceof TieredDiscItem tieredDisc ? tieredDisc.tier() : null;
        PlayerSkillManager.recordThrow(serverPlayer, thrownTier);
        if (strictFlowDebug) {
            McdgMod.LOGGER.info(
                "Strict throw release | player={} charge={} velocity={} pos={} strict={}",
                serverPlayer.getGameProfile().getName(),
                String.format("%.3f", charge),
                String.format("%.3f", velocity),
                formatPos(serverPlayer.getBlockPos()),
                rulesetManager.isStrict()
            );
        }
        Hand swingHand = serverPlayer.getMainHandStack().isOf(stack.getItem()) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        serverPlayer.swingHand(swingHand, true);
        // Tiered discs lose durability on each throw; training disc has no durability.
        // Disc towel accessory reduces durability loss chance.
        if (stack.isDamageable()) {
            float preserveChance = AccessoryManager.getDurabilityPreserveMultiplier(serverPlayer);
            if (serverPlayer.getRandom().nextFloat() >= preserveChance) {
                EquipmentSlot slot = swingHand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.damage(1, serverPlayer, slot);
            }
        }
        serverPlayer.sendMessage(buildReleaseText(charge), true);
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        // Phase 6: Use different UseAction per stance for visual feedback
        // For now, return BOW as default since client-side access is not available in main source set
        // Future enhancement: Could add server-side stance tracking or move this to client source set
        return UseAction.BOW;
    }

    /**
     * Returns the flight stats for the given disc stack.
     * Subclasses (tiered discs) override this to provide tier-specific stats.
     * The stack parameter is reserved for future NBT-based per-disc customization.
     */
    protected DiscStats getDiscStats(ItemStack stack) {
        return DiscStats.DEFAULT;
    }

    private static float computeChargePercent(int usedTicks) {
        float charge = usedTicks / (float) MAX_CHARGE_TICKS;
        return Math.max(0.0f, Math.min(MAX_POWER_MULTIPLIER, charge));
    }

    // Client-side accessors
    public static boolean isClientChargeVisible() {
        return clientChargeVisible;
    }

    public static float getClientChargePercent() {
        return clientChargePercent;
    }

    public static boolean isPowerLocked() {
        return powerLocked;
    }

    public static float getLockedChargePercent() {
        return lockedChargePercent;
    }

    public static int getLockedTicks() {
        return lockedTicks;
    }

    // Client-side setters used by throwable disc items to share charge state
    public static void setClientChargeVisible(boolean visible) {
        clientChargeVisible = visible;
    }

    public static void setClientChargePercent(float percent) {
        clientChargePercent = percent;
    }

    public static void setLockedChargePercent(float percent) {
        lockedChargePercent = percent;
    }

    public static void setLockedTicks(int ticks) {
        lockedTicks = ticks;
    }

    public static void setLastAudioThreshold(int threshold) {
        lastAudioThreshold = threshold;
    }

    public static int getLastAudioThreshold() {
        return lastAudioThreshold;
    }

    public static void resetPowerLock() {
        powerLocked = false;
        lockedChargePercent = 0.0f;
        lockedTicks = 0;
    }

    public static void setPowerLocked(boolean locked) {
        // Final lock - no unlock allowed
        if (!locked) {
            return;  // Ignore unlock commands
        }
        // Only lock if not already locked
        if (powerLocked) {
            return;  // Already locked, ignore re-lock attempts
        }
        powerLocked = true;
        lockedChargePercent = clientChargePercent;
        // Calculate and store the tick count at lock time
        lockedTicks = Math.round(lockedChargePercent * MAX_CHARGE_TICKS);
    }

    // Server-side power lock state management - FINAL LOCK, no unlock
    public static void setServerPowerLocked(UUID playerUuid, boolean locked, float chargePercent) {
        // Only allow locking once per throw - ignore unlock commands
        if (!locked) {
            // Unlock is not allowed - power lock is final
            return;
        }
        // Only lock if not already locked
        Boolean alreadyLocked = SERVER_POWER_LOCKED.get(playerUuid);
        if (alreadyLocked != null && alreadyLocked) {
            // Already locked, ignore re-lock attempts
            return;
        }
        SERVER_POWER_LOCKED.put(playerUuid, true);
        SERVER_LOCKED_CHARGE.put(playerUuid, chargePercent);
        // Calculate and store the tick count at which we locked
        int lockedTicks = Math.round(chargePercent * MAX_CHARGE_TICKS);
        SERVER_LOCKED_TICKS.put(playerUuid, lockedTicks);
        McdgMod.LOGGER.info("Power LOCKED for player {} at charge={} ticks={} (FINAL)", playerUuid, String.format("%.3f", chargePercent), lockedTicks);
    }

    // Server-side stance state management
    public static void setServerStance(UUID playerUuid, ThrowStance stance, ReleaseAngle angle) {
        SERVER_PLAYER_STANCE.put(playerUuid, stance);
        SERVER_PLAYER_ANGLE.put(playerUuid, angle);
        McdgMod.LOGGER.debug("Stance set for player {}: stance={} angle={}", playerUuid, stance, angle);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, net.minecraft.client.item.TooltipType type) {
        // Subclasses (TieredDiscItem) add their own layout; enchantments are added via addEnchantmentTooltip.
    }

    /**
     * Adds the active disc enchantments to the tooltip.
     */
    protected void addEnchantmentTooltip(ItemStack stack, List<Text> tooltip) {
        Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(stack);
        for (Map.Entry<DiscEnchantment, Integer> entry : enchantments.entrySet()) {
            DiscEnchantment enchant = entry.getKey();
            int level = entry.getValue();
            String roman = romanLevel(level);
            tooltip.add(Text.literal(enchant.displayName() + " " + roman).formatted(enchant.color()));
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

    private static Text buildReleaseText(float charge) {
        int percent = Math.round(charge * 100);
        return Text.literal("Disc thrown at " + percent + "% power").formatted(Formatting.YELLOW);
    }

    private static int horizontalDistance(net.minecraft.util.math.BlockPos from, net.minecraft.util.math.BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());
        return Math.max(dx, dz);
    }

    private static String formatPos(net.minecraft.util.math.BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
