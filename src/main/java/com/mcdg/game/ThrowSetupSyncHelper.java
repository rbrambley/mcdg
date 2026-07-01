package com.mcdg.game;

import com.mcdg.net.ThrowSetupSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and syncs effective throw multipliers to the client for the Setup HUD.
 * Only sends the packet when the combined multipliers actually change.
 */
public final class ThrowSetupSyncHelper {
    private ThrowSetupSyncHelper() {
    }

    private static final Map<UUID, float[]> LAST_SENT_MULTIPLIERS = new ConcurrentHashMap<>();

    /**
     * Computes the effective throw multipliers for the player's current held disc
     * and sends a ThrowSetupSync packet if they changed since the last sync.
     */
    public static void syncSetupMultipliers(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!McdgItems.isDisc(stack)) {
            stack = player.getOffHandStack();
            if (!McdgItems.isDisc(stack)) {
                return;
            }
        }

        Map<DiscEnchantment, Integer> enchantments = DiscEnchantmentHelper.getAll(stack);
        int distanceLevel = enchantments.getOrDefault(DiscEnchantment.DISTANCE, 0);
        int glideLevel = enchantments.getOrDefault(DiscEnchantment.GLIDE, 0);
        int fadeLevel = enchantments.getOrDefault(DiscEnchantment.FADE_CONTROL, 0);

        DiscStats baseStats = ((ChargedDiscItem) stack.getItem()).getDiscStats(stack);
        DiscStats stats = PlayerThrowStats.applyPlayerEffects(baseStats, player);

        float distanceMultiplier = (float) stats.throwSpeedMultiplier()
                * (1.0f + distanceLevel * DiscEnchantment.DISTANCE.perLevelMultiplier());

        float glideMultiplier = (float) stats.glideMultiplier()
                * (1.0f + glideLevel * DiscEnchantment.GLIDE.perLevelMultiplier());

        // Stability multiplier reduces fade; fade control enchant also reduces fade
        float stabilityMultiplier = Math.min(2.0f,
                (float) stats.stabilityMultiplier()
                * (1.0f + fadeLevel * DiscEnchantment.FADE_CONTROL.perLevelMultiplier()));

        float powerMultiplier = 1.0f + PlayerThrowStats.getPowerMultiplierBonus(player);

        // Hazard penalty reduces the effective max power cap for the next throw
        float hazardPenalty = com.mcdg.McdgMod.getRoundStateManager().getNextThrowPowerMultiplier(player.getUuid());
        powerMultiplier *= hazardPenalty;

        float[] current = new float[] { powerMultiplier, distanceMultiplier, glideMultiplier, stabilityMultiplier };
        float[] last = LAST_SENT_MULTIPLIERS.get(player.getUuid());
        if (last != null && last.length == 4
                && last[0] == current[0] && last[1] == current[1]
                && last[2] == current[2] && last[3] == current[3]) {
            return;
        }

        LAST_SENT_MULTIPLIERS.put(player.getUuid(), current);
        ThrowSetupSync.Payload payload = new ThrowSetupSync.Payload(
                current[0],
                current[1],
                current[2],
                current[3]
        );
        ServerPlayNetworking.send(player, payload);
    }

    public static void clearPlayerState(UUID playerUuid) {
        LAST_SENT_MULTIPLIERS.remove(playerUuid);
    }
}
