package com.mcdg.client;

import com.mcdg.net.ElytraFlightSync;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side renderer for elytra disc flight visual effects.
 * Receives flight state from server and renders particle trails and effects.
 */
public final class ElytraFlightRenderer {
    
    private static class FlightData {
        Vec3d lastPosition;
        boolean playerControlEnabled;
    }
    
    private static final Map<UUID, FlightData> ACTIVE_FLIGHTS = new HashMap<>();
    
    private ElytraFlightRenderer() {}
    
    /**
     * Update flight state from server packet.
     */
    public static void updateFlight(ElytraFlightSync.Payload payload) {
        FlightData data = ACTIVE_FLIGHTS.computeIfAbsent(payload.playerId(), k -> new FlightData());
        data.lastPosition = payload.position();
        data.playerControlEnabled = payload.playerControlEnabled();
    }
    
    /**
     * End flight for a player.
     */
    public static void endFlight(UUID playerId) {
        ACTIVE_FLIGHTS.remove(playerId);
    }
    
    /**
     * Tick handler - called every client tick from McdgClientMod.
     */
    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return;
        }
        
        ParticleManager particleManager = client.particleManager;
        
        // Render flight effects for all active flights
        for (FlightData data : ACTIVE_FLIGHTS.values()) {
            Vec3d pos = data.lastPosition;
            
            // Skip if too far from player
            if (client.player.squaredDistanceTo(pos.x, pos.y, pos.z) > 256 * 256) {
                continue;
            }
            
            // Render trail particles
            renderFlightTrail(particleManager, pos, data.playerControlEnabled);
        }
    }
    
    /**
     * Render particle trail at flight position.
     */
    private static void renderFlightTrail(ParticleManager particleManager, Vec3d pos, boolean playerControlEnabled) {
        // Use different particle colors based on control status
        int color = playerControlEnabled ? 0xAA00FF : 0x00AAFF; // Purple for controlled, blue for auto
        
        // Render multiple particles for visual effect
        for (int i = 0; i < 3; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetY = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            
            Particle particle = particleManager.addParticle(
                    ParticleTypes.FLAME,
                    pos.x + offsetX,
                    pos.y + offsetY,
                    pos.z + offsetZ,
                    0.0, 0.0, 0.0
            );
            
            if (particle != null) {
                particle.setColor(
                        ((color >> 16) & 0xFF) / 255.0f,
                        ((color >> 8) & 0xFF) / 255.0f,
                        (color & 0xFF) / 255.0f
                );
                particle.setMaxAge(40); // 2 seconds
            }
        }
    }
    
    /**
     * Check if a player is currently in elytra flight.
     */
    public static boolean isInFlight(UUID playerId) {
        return ACTIVE_FLIGHTS.containsKey(playerId);
    }
}
