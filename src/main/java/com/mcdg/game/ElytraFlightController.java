package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.net.ElytraFlightSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side controller for elytra disc flight physics.
 * Moves players along calculated trajectories with optional player control.
 */
public final class ElytraFlightController {
    
    private static final Map<UUID, FlightState> ACTIVE_FLIGHTS = new ConcurrentHashMap<>();
    
    private ElytraFlightController() {}
    
    /**
     * Flight state tracking for a player.
     */
    public static class FlightState {
        private final UUID playerId;
        private final Vec3d[] pathPoints;
        private final int totalFlightTicks;
        private final boolean playerControlEnabled;
        private int currentTick;
        private Vec3d currentPosition;
        private Vec3d currentVelocity;
        
        public FlightState(
                UUID playerId,
                Vec3d[] pathPoints,
                int totalFlightTicks,
                boolean playerControlEnabled,
                Vec3d startPosition
        ) {
            this.playerId = playerId;
            this.pathPoints = pathPoints;
            this.totalFlightTicks = totalFlightTicks;
            this.playerControlEnabled = playerControlEnabled;
            this.currentTick = 0;
            this.currentPosition = startPosition;
            this.currentVelocity = Vec3d.ZERO;
        }
        
        public UUID playerId() { return playerId; }
        public Vec3d[] pathPoints() { return pathPoints; }
        public int totalFlightTicks() { return totalFlightTicks; }
        public boolean playerControlEnabled() { return playerControlEnabled; }
        public int currentTick() { return currentTick; }
        public Vec3d currentPosition() { return currentPosition; }
        public Vec3d currentVelocity() { return currentVelocity; }
        
        public void advanceTick() { currentTick++; }
        public void setPosition(Vec3d pos) { this.currentPosition = pos; }
        public void setVelocity(Vec3d vel) { this.currentVelocity = vel; }
    }
    
    /**
     * Start a new elytra flight for a player.
     */
    public static void startFlight(
            ServerPlayerEntity player,
            TrajectoryCalculator.TrajectoryResult trajectory,
            boolean playerControlEnabled
    ) {
        UUID playerId = player.getUuid();
        
        FlightState state = new FlightState(
                playerId,
                trajectory.pathPoints(),
                trajectory.flightTicks(),
                playerControlEnabled,
                player.getPos()
        );
        
        ACTIVE_FLIGHTS.put(playerId, state);
        
        McdgMod.LOGGER.info(
                "Elytra flight started | player={} duration={} ticks playerControl={}",
                player.getGameProfile().getName(),
                trajectory.flightTicks(),
                playerControlEnabled
        );
        
        // Set initial velocity toward first path point
        if (trajectory.pathPoints().length > 1) {
            Vec3d firstPoint = trajectory.pathPoints()[1];
            Vec3d direction = firstPoint.subtract(player.getPos()).normalize();
            double speed = 0.5; // Initial launch speed
            state.setVelocity(direction.multiply(speed));
        }
    }
    
    /**
     * Tick all active flights - called from server tick.
     */
    public static void tick(MinecraftServer server) {
        ACTIVE_FLIGHTS.entrySet().removeIf(entry -> {
            FlightState state = entry.getValue();
            UUID playerId = entry.getKey();
            
            // Find player entity
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            
            if (player == null) {
                McdgMod.LOGGER.warn("Player not found for elytra flight, ending flight: {}", playerId);
                return true; // Remove flight
            }
            
            // Check if flight is complete
            if (state.currentTick() >= state.totalFlightTicks()) {
                endFlight(player, state);
                return true; // Remove flight
            }
            
            // Move player along trajectory
            updateFlightPosition(player, state);

            // Broadcast flight state to nearby players
            ElytraFlightSync.Payload payload = new ElytraFlightSync.Payload(
                    playerId,
                    state.currentPosition(),
                    state.currentVelocity(),
                    state.currentTick(),
                    state.totalFlightTicks(),
                    state.playerControlEnabled()
            );
            ServerPlayNetworking.send(player, payload);
            for (ServerPlayerEntity nearbyPlayer : player.getServerWorld().getPlayers()) {
                if (nearbyPlayer != player && nearbyPlayer.squaredDistanceTo(state.currentPosition()) < 256 * 256) {
                    ServerPlayNetworking.send(nearbyPlayer, payload);
                }
            }

            state.advanceTick();
            return false; // Keep flight active
        });
    }
    
    /**
     * Update player position during flight.
     */
    private static void updateFlightPosition(ServerPlayerEntity player, FlightState state) {
        Vec3d[] pathPoints = state.pathPoints();
        int tick = state.currentTick();

        // Get target position from path
        if (tick < pathPoints.length) {
            Vec3d targetPos = pathPoints[tick];
            Vec3d oldPos = state.currentPosition();

            if (state.playerControlEnabled()) {
                // Blend trajectory with player input
                Vec3d trajectoryDirection = targetPos.subtract(state.currentPosition()).normalize();

                // Get player look direction
                float yaw = player.getYaw();
                float pitch = player.getPitch();
                double yawRad = Math.toRadians(yaw);
                double pitchRad = Math.toRadians(pitch);

                Vec3d playerDirection = new Vec3d(
                        -Math.sin(yawRad) * Math.cos(pitchRad),
                        -Math.sin(pitchRad),
                        Math.cos(yawRad) * Math.cos(pitchRad)
                ).normalize();

                // Blend: 70% trajectory, 30% player control
                Vec3d blendedDirection = trajectoryDirection.multiply(0.7)
                        .add(playerDirection.multiply(0.3))
                        .normalize();

                double speed = state.currentVelocity().length();
                Vec3d newVelocity = blendedDirection.multiply(speed);

                state.setVelocity(newVelocity);

                // Move player
                Vec3d newPos = state.currentPosition().add(newVelocity);
                player.teleport(newPos.x, newPos.y, newPos.z);
                state.setPosition(newPos);
            } else {
                // Pure trajectory following
                player.teleport(targetPos.x, targetPos.y, targetPos.z);
                state.setPosition(targetPos);

                // Accumulate fall distance for damage on landing
                if (targetPos.y < oldPos.y) {
                    player.fallDistance += (oldPos.y - targetPos.y);
                }

                // Calculate velocity for next tick
                if (tick + 1 < pathPoints.length) {
                    Vec3d nextPos = pathPoints[tick + 1];
                    Vec3d velocity = nextPos.subtract(targetPos);
                    state.setVelocity(velocity);
                }
            }
        }
    }
    
    /**
     * End a flight and clean up.
     */
    private static void endFlight(ServerPlayerEntity player, FlightState state) {
        McdgMod.LOGGER.info(
                "Elytra flight ended | player={} duration={} ticks",
                player.getGameProfile().getName(),
                state.currentTick()
        );

        // Apply slow fall on landing: cap downward velocity and reset fall distance
        Vec3d vel = player.getVelocity();
        if (vel.y < -0.05) {
            player.setVelocity(vel.x, -0.05, vel.z);
            player.velocityModified = true;
        }
        player.fallDistance = 0;
    }
    
    /**
     * Check if a player is currently in elytra flight.
     */
    public static boolean isInFlight(UUID playerId) {
        return ACTIVE_FLIGHTS.containsKey(playerId);
    }
    
    /**
     * Get the current flight state for a player.
     */
    public static FlightState getFlightState(UUID playerId) {
        return ACTIVE_FLIGHTS.get(playerId);
    }
    
    /**
     * Manually end a player's flight (e.g., on collision or disconnect).
     */
    public static void endFlight(UUID playerId) {
        FlightState state = ACTIVE_FLIGHTS.remove(playerId);
        if (state != null) {
            McdgMod.LOGGER.info("Elytra flight manually ended for player: {}", playerId);
        }
    }

}
