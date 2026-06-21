package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.net.RoundInviteNotification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Manages round invite handshakes: a non-admin player selects other players
 * via the GUI, the server sends invites, and once all responses are received
 * (or the timeout expires) the round starts for everyone who accepted.
 */
public final class RoundInviteManager {
    private static final long INVITE_TIMEOUT_MS = 30_000L;
    private static final long WARMUP_COUNTDOWN_INTERVAL_MS = 5_000L;
    private static long lastWarmupCountdownMs = 0L;

    private final ActiveCourseManager courseManager;
    private final RoundStateManager roundStateManager;
    private final PracticeCourseStorage practiceCourseStorage;
    private final RoundPresentationService roundPresentationService;
    private final Map<UUID, PendingInvite> pendingByInitiator = new ConcurrentHashMap<>();

    public RoundInviteManager(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage,
            RoundPresentationService roundPresentationService
    ) {
        this.courseManager = courseManager;
        this.roundStateManager = roundStateManager;
        this.practiceCourseStorage = practiceCourseStorage;
        this.roundPresentationService = roundPresentationService;
    }

    public void handleInviteRequest(
            MinecraftServer server,
            ServerPlayerEntity initiator,
            List<UUID> targetPlayerIds,
            int catalogIndex
    ) {
        if (initiator == null || targetPlayerIds == null || targetPlayerIds.isEmpty()) {
            return;
        }
        if (courseManager.isRoundActive()) {
            initiator.sendMessage(Text.literal("A round is already active."), true);
            return;
        }

        Optional<PracticeCourseStorage.LoadedPracticeCourse> selected =
                practiceCourseStorage.loadReusableByIndex(server, catalogIndex);
        if (selected.isEmpty()) {
            initiator.sendMessage(Text.literal("Course not found."), true);
            return;
        }

        PracticeCourseStorage.LoadedPracticeCourse loaded = selected.get();
        Course course = loaded.course();
        PlacedCourseState placed = loaded.placedCourseState();
        ServerWorld world = server.getWorld(placed.worldKey());
        if (world == null) {
            initiator.sendMessage(Text.literal("Course world is unavailable."), true);
            return;
        }

        // Ensure initiator is included in the round
        Set<UUID> targets = new HashSet<>();
        targets.add(initiator.getUuid());
        for (UUID id : targetPlayerIds) {
            if (id != null && !id.equals(initiator.getUuid())) {
                targets.add(id);
            }
        }

        PendingInvite invite = new PendingInvite(
                initiator.getUuid(),
                catalogIndex,
                course.name(),
                targets,
                System.currentTimeMillis()
        );
        pendingByInitiator.put(initiator.getUuid(), invite);

        String initiatorName = initiator.getGameProfile().getName();
        for (UUID targetId : targets) {
            if (targetId.equals(initiator.getUuid())) {
                continue;
            }
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            if (target != null) {
                ServerPlayNetworking.send(target, new RoundInviteNotification.Payload(
                        initiator.getUuid(),
                        initiatorName,
                        course.name(),
                        catalogIndex
                ));
            }
        }

        initiator.sendMessage(Text.literal(
                "Invites sent for '" + course.name() + "'. Waiting for responses..."
        ), true);
    }

    public void handleInviteResponse(
            MinecraftServer server,
            ServerPlayerEntity responder,
            UUID initiatorId,
            boolean accepted
    ) {
        if (responder == null || initiatorId == null) {
            return;
        }
        PendingInvite invite = pendingByInitiator.get(initiatorId);
        if (invite == null) {
            responder.sendMessage(Text.literal("That invite has expired or been cancelled."), true);
            return;
        }

        UUID responderId = responder.getUuid();
        if (!invite.pendingTargets.contains(responderId)) {
            return;
        }

        invite.pendingTargets.remove(responderId);
        if (accepted) {
            invite.acceptedTargets.add(responderId);
        } else {
            invite.rejectedTargets.add(responderId);
            responder.sendMessage(Text.literal("You declined the round invite."), true);
            ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(initiatorId);
            if (initiator != null) {
                initiator.sendMessage(Text.literal(
                        responder.getGameProfile().getName() + " declined the invite."
                ), true);
            }
        }

        if (invite.pendingTargets.isEmpty()) {
            finalizeInvite(server, invite);
        }
    }

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        
        // Handle invite timeouts
        List<UUID> toFinalize = new ArrayList<>();
        for (Map.Entry<UUID, PendingInvite> entry : pendingByInitiator.entrySet()) {
            if (now - entry.getValue().createdAtMs >= INVITE_TIMEOUT_MS) {
                toFinalize.add(entry.getKey());
            }
        }
        for (UUID key : toFinalize) {
            PendingInvite invite = pendingByInitiator.get(key);
            if (invite != null) {
                for (UUID id : new HashSet<>(invite.pendingTargets)) {
                    invite.pendingTargets.remove(id);
                    invite.rejectedTargets.add(id);
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player != null) {
                        player.sendMessage(Text.literal("The round invite timed out."), true);
                    }
                }
                finalizeInvite(server, invite);
            }
        }

        // Handle warmup expiration and countdown
        if (courseManager.isWarmupActive()) {
            long remainingMs = courseManager.getWarmupRemainingMs();
            
            // Send countdown messages at intervals
            if (now - lastWarmupCountdownMs >= WARMUP_COUNTDOWN_INTERVAL_MS) {
                lastWarmupCountdownMs = now;
                int remainingSeconds = (int) Math.ceil(remainingMs / 1000.0);
                
                // Only send countdown if more than 3 seconds remaining
                if (remainingSeconds > 3) {
                    for (UUID participantId : courseManager.getActiveParticipantIds()) {
                        ServerPlayerEntity player = server.getPlayerManager().getPlayer(participantId);
                        if (player != null) {
                            player.sendMessage(Text.literal("Round starting in " + remainingSeconds + " seconds..."), true);
                        }
                    }
                }
            }
            
            if (remainingMs <= 0) {
                transitionFromWarmupToActiveRound(server);
            }
        } else {
            lastWarmupCountdownMs = 0L;
        }
    }

    private void transitionFromWarmupToActiveRound(MinecraftServer server) {
        courseManager.setWarmupActive(false);
        courseManager.setRoundActive(true);

        // Notify all participants that the round has started
        for (UUID participantId : courseManager.getActiveParticipantIds()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(participantId);
            if (player != null) {
                player.sendMessage(Text.literal("Round started! Good luck!"), true);
            }
        }
    }

    private void finalizeInvite(MinecraftServer server, PendingInvite invite) {
        pendingByInitiator.remove(invite.initiatorId);

        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(invite.initiatorId);
        if (initiator == null) {
            // Initiator left; abort
            if (!invite.acceptedTargets.isEmpty()) {
                for (UUID id : invite.acceptedTargets) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p != null) {
                        p.sendMessage(Text.literal("Round invite cancelled: initiator left."), true);
                    }
                }
            }
            return;
        }

        if (invite.acceptedTargets.isEmpty()) {
            initiator.sendMessage(Text.literal("No one accepted the round invite. Round not started."), true);
            return;
        }

        Optional<PracticeCourseStorage.LoadedPracticeCourse> selected =
                practiceCourseStorage.loadReusableByIndex(server, invite.catalogIndex);
        if (selected.isEmpty()) {
            initiator.sendMessage(Text.literal("Course no longer available. Round not started."), true);
            return;
        }

        PracticeCourseStorage.LoadedPracticeCourse loaded = selected.get();
        Course course = loaded.course();
        PlacedCourseState placed = loaded.placedCourseState();
        ServerWorld world = server.getWorld(placed.worldKey());
        if (world == null) {
            initiator.sendMessage(Text.literal("Course world is unavailable. Round not started."), true);
            return;
        }

        // Build participant list from accepted players who are still online
        List<ServerPlayerEntity> participants = new ArrayList<>();
        for (UUID id : invite.acceptedTargets) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && p.getWorld().getRegistryKey().equals(placed.worldKey())) {
                participants.add(p);
            }
        }

        if (participants.isEmpty()) {
            initiator.sendMessage(Text.literal("No accepted players are online. Round not started."), true);
            return;
        }

        courseManager.setLegacyPracticeSnapshot(loaded.legacyFormat());
        startRoundForParticipants(server, course, placed, world, participants);
        initiator.sendMessage(Text.literal(
                "Round started with " + participants.size() + " player(s)."
        ), true);
    }

    private void startRoundForParticipants(
            MinecraftServer server,
            Course course,
            PlacedCourseState placed,
            ServerWorld world,
            List<ServerPlayerEntity> participants
    ) {
        clearRoundStateForTrackedParticipants(courseManager, roundStateManager);

        // Pre-load course chunks so teleports don't trigger synchronous chunk generation
        RoundChunkLoader.loadCourseChunks(world, placed);

        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            McdgMod.LOGGER.warn("RoundInviteManager: hole 1 tee missing for course '{}'", course.name());
            return;
        }

        List<UUID> participantIds = new ArrayList<>();
        for (ServerPlayerEntity player : participants) {
            BlockPos safeTee = resolveSafeFeetNear(world, firstTee);
            roundStateManager.startRoundForPlayer(player.getUuid(), safeTee);
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            ensureSingleRoundThrowItem(player);
            ScorecardManager.initializeScorecard(player, course, placed);
            participantIds.add(player.getUuid());
            player.sendMessage(Text.literal("Warmup period started. Round will begin in 30 seconds."), true);
        }

        courseManager.setActiveParticipantIds(participantIds);
        courseManager.setActiveCourse(course);
        courseManager.setPlacedCourseState(placed);
        courseManager.setPersistentPlacedCourse(true);
        courseManager.setWarmupActive(true);

        // Send running scoreboard to all participants
        for (ServerPlayerEntity player : participants) {
            HoleProgressTracker.sendRunningScoreboardToPlayer(player, courseManager, roundStateManager);
        }
    }

    // --- Helper methods copied from McdgAdminCommands to keep this self-contained ---

    private static void clearRoundStateForTrackedParticipants(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        for (UUID id : courseManager.getActiveParticipantIds()) {
            roundStateManager.clearPlayer(id);
        }
        courseManager.clearActiveParticipantIds();
    }

    private static void ensureSingleRoundThrowItem(ServerPlayerEntity player) {
        RoundInventoryCleaner.purgeRoundItemsAndJunk(player);
        player.giveItemStack(new net.minecraft.item.ItemStack(McdgItems.TRAINING_DISC, 1));
    }

    private static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos preferredFeet) {
        if (isStandableFeet(world, preferredFeet)) {
            return preferredFeet;
        }
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = preferredFeet.up(dy);
            if (isStandableFeet(world, up)) {
                return up;
            }
            BlockPos down = preferredFeet.down(dy);
            if (isStandableFeet(world, down)) {
                return down;
            }
        }
        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = preferredFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 4; dy++) {
                        BlockPos up = candidate.up(dy);
                        if (isStandableFeet(world, up)) {
                            return up;
                        }
                        BlockPos down = candidate.down(dy);
                        if (isStandableFeet(world, down)) {
                            return down;
                        }
                    }
                }
            }
        }
        return preferredFeet;
    }

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        var feetState = world.getBlockState(feet);
        var headState = world.getBlockState(feet.up());
        if (!feetState.getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        if (!headState.getCollisionShape(world, feet.up()).isEmpty()) {
            return false;
        }
        BlockPos below = feet.down();
        var belowState = world.getBlockState(below);
        if (belowState.isAir()) {
            return false;
        }
        var belowShape = belowState.getCollisionShape(world, below);
        return !belowShape.isEmpty();
    }

    private static final class PendingInvite {
        final UUID initiatorId;
        final int catalogIndex;
        final String courseName;
        final Set<UUID> pendingTargets;
        final Set<UUID> acceptedTargets = new HashSet<>();
        final Set<UUID> rejectedTargets = new HashSet<>();
        final long createdAtMs;

        PendingInvite(UUID initiatorId, int catalogIndex, String courseName, Set<UUID> pendingTargets, long createdAtMs) {
            this.initiatorId = initiatorId;
            this.catalogIndex = catalogIndex;
            this.courseName = courseName;
            this.pendingTargets = new HashSet<>(pendingTargets);
            this.createdAtMs = createdAtMs;
        }
    }
}
