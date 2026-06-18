package com.mcdg.client;

import com.mcdg.game.StrictPenaltyType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders temporary visual markers at OB crossing points.
 * Shows particle effects at the location where a disc went out of bounds,
 * color-coded by penalty type.
 */
public final class CrossingMarkerRenderer {
    private static final Map<BlockPos, MarkerData> ACTIVE_MARKERS = new ConcurrentHashMap<>();

    private static record MarkerData(
        StrictPenaltyType penaltyType,
        int remainingTicks
    ) {}

    private CrossingMarkerRenderer() {}

    public static void showMarker(BlockPos position, StrictPenaltyType penaltyType, int durationTicks) {
        ACTIVE_MARKERS.put(position, new MarkerData(penaltyType, durationTicks));
    }

    public static void tick(MinecraftClient client) {
        if (client.world == null) return;

        Iterator<Map.Entry<BlockPos, MarkerData>> iterator = ACTIVE_MARKERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, MarkerData> entry = iterator.next();
            MarkerData data = entry.getValue();
            BlockPos pos = entry.getKey();

            // Spawn particles
            spawnParticles(client, pos, data.penaltyType());

            // Decrement ticks
            MarkerData updated = new MarkerData(data.penaltyType(), data.remainingTicks() - 1);
            if (updated.remainingTicks() <= 0) {
                iterator.remove();
            } else {
                ACTIVE_MARKERS.put(pos, updated);
            }
        }
    }

    private static void spawnParticles(MinecraftClient client, BlockPos pos, StrictPenaltyType penaltyType) {
        // Color based on penalty type
        ParticleEffect particleType = switch (penaltyType) {
            case OB -> ParticleTypes.SMOKE;  // Gray/white for OB
            case HAZARD -> ParticleTypes.FLAME;  // Orange for hazard
            case NONE -> ParticleTypes.END_ROD;  // Shouldn't happen
        };

        if (client.world != null) {
            client.world.addParticle(
                particleType,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                0, 0.2, 0
            );
        }
    }

    public static void clearAll() {
        ACTIVE_MARKERS.clear();
    }
}
