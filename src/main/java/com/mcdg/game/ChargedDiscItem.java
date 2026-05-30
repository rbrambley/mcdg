package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.rules.TournamentRulesetManager;
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
import net.minecraft.world.World;

public final class ChargedDiscItem extends Item {
    private static final int MAX_CHARGE_TICKS = 20;
    private static final float MIN_VELOCITY = 0.7f;
    private static final float VELOCITY_SPAN = 1.6f;
    private static boolean clientChargeVisible;
    private static float clientChargePercent;

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
        if (!courseManager.isRoundActive()) {
            if (user instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.sendMessage(Text.literal("No active round. Use /mcdg startround."), true);
            }
            return TypedActionResult.fail(stack);
        }

        if (world.isClient()) {
            clientChargeVisible = true;
            clientChargePercent = 0.0f;
        }

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!courseManager.isRoundActive()) {
            if (world.isClient()) {
                clientChargeVisible = false;
                clientChargePercent = 0.0f;
            }
            return;
        }

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        float charge = computeChargePercent(usedTicks);

        if (world.isClient()) {
            clientChargeVisible = true;
            clientChargePercent = charge;
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
            return;
        }

        if (!(user instanceof ServerPlayerEntity serverPlayer) || !courseManager.isRoundActive()) {
            return;
        }

        // Lie-distance enforcement is a strict-mode rule only.
        // In casual mode there is no enforcement — and even in strict mode we must only
        // check AFTER the tracker has had a chance to update the lie from the previous
        // pearl landing, otherwise the player is always blocked on throw #2+.
        // NOTE: totalStrokes > 0 guard was intentionally removed — the first throw of the
        // entire round (hole 1, throw 1) must also be gated because the initial lie is
        // set to the tee position at round start, so the distance check is valid.
        if (rulesetManager.isStrict()) {
            var state = roundStateManager.getState(serverPlayer.getUuid()).orElse(null);
            if (state != null && !state.lastThrowPenalty()) {
            if (HoleProgressTracker.isThrowResolutionPending(serverPlayer.getUuid(), state.totalStrokes())) {
                String snapshot = HoleProgressTracker.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                McdgMod.LOGGER.info(
                    "Strict throw gate pending resolution | player={} total={} hole={} lie={} playerPos={} snapshot={}",
                    serverPlayer.getGameProfile().getName(),
                    state.totalStrokes(),
                    state.currentHole(),
                    formatPos(state.lie()),
                    formatPos(serverPlayer.getBlockPos()),
                    snapshot
                );
                serverPlayer.sendMessage(
                    Text.literal("Wait for the previous throw to finish resolving before throwing again.")
                        .formatted(Formatting.YELLOW),
                    true
                );
                return;
            }

                boolean bypassConsumed = HoleProgressTracker.consumeStrictPenaltyThrowBypass(serverPlayer.getUuid());
                if (strictFlowDebug) {
                    String snapshot = HoleProgressTracker.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                    McdgMod.LOGGER.info(
                        "Strict throw gate | player={} total={} hole={} lie={} playerPos={} bypass={} allowed={} strict={} snapshot={}",
                            serverPlayer.getGameProfile().getName(),
                            state.totalStrokes(),
                            state.currentHole(),
                            formatPos(state.lie()),
                            formatPos(serverPlayer.getBlockPos()),
                            bypassConsumed,
                            rulesetManager.allowedLieToleranceBlocks(),
                        rulesetManager.isStrict(),
                        snapshot
                    );
                }
                if (!bypassConsumed) {
                    int distanceFromLie = horizontalDistance(serverPlayer.getBlockPos(), state.lie());
                    int allowedDistance = rulesetManager.allowedLieToleranceBlocks();
                    if (distanceFromLie > allowedDistance) {
                        String snapshot = HoleProgressTracker.strictThrowGateDebugSnapshot(serverPlayer.getUuid(), state.totalStrokes());
                        McdgMod.LOGGER.warn(
                                "Strict throw gate blocked | player={} hole={} total={} holeStrokes={} distanceFromLie={} allowed={} stateLie={} playerPos={} lastPenalty={} bypassConsumed={} snapshot={}",
                                serverPlayer.getGameProfile().getName(),
                                state.currentHole(),
                                state.totalStrokes(),
                                state.holeStrokes(),
                                distanceFromLie,
                                allowedDistance,
                                formatPos(state.lie()),
                                formatPos(serverPlayer.getBlockPos()),
                                state.lastThrowPenalty(),
                                bypassConsumed,
                                snapshot
                        );
                        serverPlayer.sendMessage(
                                Text.literal(
                                        "Move back to your lie before throwing. "
                                        + "Distance=" + distanceFromLie
                                        + " blocks, allowed=" + allowedDistance
                                        + " (strict)."
                                ).formatted(Formatting.RED),
                                true
                        );
                        return;
                    }
                }
            }
        }

        int usedTicks = getMaxUseTime(stack) - remainingUseTicks;
        float charge = computeChargePercent(usedTicks);
        float velocity = MIN_VELOCITY + (VELOCITY_SPAN * charge);

        EnderPearlEntity pearl = new EnderPearlEntity(world, serverPlayer);
        pearl.setItem(new ItemStack(Items.ENDER_PEARL));
        pearl.setVelocity(serverPlayer, serverPlayer.getPitch(), serverPlayer.getYaw(), 0.0f, velocity, 1.0f);
        world.spawnEntity(pearl);
        HoleProgressTracker.registerThrowRelease(serverPlayer.getUuid(), pearl.getUuid(), world.getTime());

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

        roundStateManager.recordThrow(serverPlayer.getUuid(), serverPlayer.getBlockPos());
        if (strictFlowDebug) {
            McdgMod.LOGGER.info(
                "Strict throw release | player={} usedTicks={} charge={} velocity={} pos={} strict={}",
                serverPlayer.getGameProfile().getName(),
                usedTicks,
                String.format("%.3f", charge),
                String.format("%.3f", velocity),
                formatPos(serverPlayer.getBlockPos()),
                rulesetManager.isStrict()
            );
        }
        Hand swingHand = serverPlayer.getMainHandStack().isOf(stack.getItem()) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        serverPlayer.swingHand(swingHand, true);
        serverPlayer.sendMessage(buildReleaseText(charge), true);
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
        return Math.max(0.0f, Math.min(1.0f, charge));
    }

    public static boolean isClientChargeVisible() {
        return clientChargeVisible;
    }

    public static float getClientChargePercent() {
        return clientChargePercent;
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
