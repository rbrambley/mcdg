package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Handles stamina (hunger/exhaustion) consumption on throws and XP rewards on round completion.
 *
 * <p>Stamina is implemented via Minecraft's exhaustion system so it integrates naturally
 * with survival gameplay.  XP is granted as orbs on round completion based on score vs par.
 */
public final class StaminaXpService {
    // Exhaustion point cost per throw at 100% charge. Scales linearly with charge percent.
    private static final float BASE_THROW_EXHAUSTION = 3.0f;
    private static final float MIN_EXHAUSTION_MULTIPLIER = 0.5f;
    private static final float MAX_EXHAUSTION_MULTIPLIER = 1.5f;

    // XP reward constants
    private static final int XP_PER_STROKE_UNDER_PAR = 50;
    private static final int XP_PAR_BONUS = 25;
    private static final int XP_PER_STROKE_OVER_PAR_BASE = 10;
    private static final int XP_ACE_BONUS = 100;
    private static final int XP_STRICT_BONUS = 50;

    private StaminaXpService() {}

    /**
     * Deducts stamina (adds exhaustion) for a throw based on its charge level.
     *
     * @param player the player throwing
     * @param chargePercent 0.0f to 1.25f (125% max overcharge)
     */
    public static void consumeThrowStamina(ServerPlayerEntity player, float chargePercent) {
        if (player == null) {
            return;
        }

        float normalizedCharge = Math.clamp(chargePercent, 0.0f, 1.25f);
        float multiplier = MIN_EXHAUSTION_MULTIPLIER
                + (normalizedCharge / 1.25f) * (MAX_EXHAUSTION_MULTIPLIER - MIN_EXHAUSTION_MULTIPLIER);
        float skillMultiplier = PlayerThrowStats.getExhaustionMultiplier(player);
        float exhaustion = BASE_THROW_EXHAUSTION * multiplier * skillMultiplier;

        player.getHungerManager().addExhaustion(exhaustion);

        McdgMod.LOGGER.info(
                "Stamina consumed | player={} charge={}% exhaustion={}",
                player.getGameProfile().getName(),
                String.format("%.0f", normalizedCharge * 100),
                String.format("%.2f", exhaustion)
        );
    }

    /**
     * Awards XP on round completion based on performance.
     *
     * @param player      the player completing the round
     * @param totalStrokes total strokes taken
     * @param totalPar    course par
     * @param aceCount    number of aces
     * @param strict      whether strict ruleset was used
     */
    public static void awardRoundXp(
            ServerPlayerEntity player,
            int totalStrokes,
            int totalPar,
            int aceCount,
            boolean strict
    ) {
        if (player == null) {
            return;
        }

        int delta = totalStrokes - totalPar;
        int xp = 0;

        if (delta < 0) {
            // Under par: generous XP per stroke under
            xp = (-delta) * XP_PER_STROKE_UNDER_PAR;
        } else if (delta == 0) {
            xp = XP_PAR_BONUS;
        } else {
            // Over par: small consolation XP
            xp = XP_PER_STROKE_OVER_PAR_BASE;
        }

        xp += aceCount * XP_ACE_BONUS;

        if (strict) {
            xp += XP_STRICT_BONUS;
        }

        if (xp > 0) {
            player.addExperience(xp);

            player.sendMessage(
                    Text.literal("+" + xp + " XP").formatted(Formatting.GREEN),
                    true
            );

            McdgMod.LOGGER.info(
                    "XP awarded | player={} strokes={} par={} delta={} aces={} strict={} xp={}",
                    player.getGameProfile().getName(),
                    totalStrokes, totalPar, delta, aceCount, strict, xp
            );
        }
    }
}
