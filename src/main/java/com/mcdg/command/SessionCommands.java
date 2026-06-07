package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlayerRoundState;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class SessionCommands {
    private SessionCommands() {
    }

    public enum ResumeSourceSelection {
        PREFER_MANUAL,
        MANUAL_ONLY,
        AUTO_ONLY
    }

    public static int executeSaveSession(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        if (!courseManager.isRoundActive()) {
            source.sendError(Text.literal("No active round. Start or resume a round first."));
            return 0;
        }

        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (course == null || placed == null) {
            source.sendError(Text.literal("No active course placement found. Cannot save player session."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Course world is unavailable."));
            return 0;
        }

        List<ServerPlayerEntity> players = resolveRoundParticipants(source, world, selectedPlayers, "savesession");
        if (players.isEmpty()) {
            source.sendError(Text.literal("No eligible players selected for this world."));
            return 0;
        }

        Set<UUID> activeParticipants = courseManager.getActiveParticipantIds();
        List<UUID> removedIds = new ArrayList<>();
        int saved = 0;
        int skipped = 0;
        for (ServerPlayerEntity player : players) {
            UUID playerId = player.getUuid();
            if (!activeParticipants.contains(playerId)) {
                skipped++;
                continue;
            }

            PlayerRoundState state = roundStateManager.getState(playerId).orElse(null);
            if (state == null) {
                skipped++;
                continue;
            }

            boolean persisted = playerRoundSessionStorage.savePlayer(
                    source.getServer(),
                    playerId,
                    course,
                    placed,
                    state,
                    null
            );
            if (!persisted) {
                skipped++;
                continue;
            }

            roundStateManager.clearPlayer(playerId);
            removedIds.add(playerId);
            removeRoundThrowItems(player);
            player.sendMessage(Text.literal(
                    "Session saved at hole " + state.currentHole() + " (strokes " + state.totalStrokes() + ")."
            ), true);
            saved++;
        }

        if (!removedIds.isEmpty()) {
            courseManager.removeActiveParticipantIds(removedIds);
            if (courseManager.getActiveParticipantIds().isEmpty()) {
                courseManager.setRoundActive(false);
            }
        }

        final int savedCount = saved;
        final int skippedCount = skipped;
        final boolean roundStillActive = courseManager.isRoundActive();
        source.sendFeedback(() -> Text.literal(
                "Save session complete. Saved=" + savedCount
                        + ", skipped=" + skippedCount
                        + ", roundActive=" + roundStillActive + "."
        ), true);

        return savedCount > 0 ? 1 : 0;
    }

    public static int executeResumeSession(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            ResumeSourceSelection sourceSelection,
            Collection<ServerPlayerEntity> selectedPlayers
    ) {
        Course course = courseManager.getActiveCourse().orElse(null);
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (course == null || placed == null) {
            var loaded = practiceCourseStorage.load(source.getServer()).orElse(null);
            if (loaded != null) {
                course = loaded.course();
                placed = loaded.placedCourseState();
                courseManager.setActiveCourse(course);
                courseManager.setActiveCourseCatalogIndex(null);
                courseManager.setPlacedCourseState(placed);
                courseManager.setPersistentPlacedCourse(true);
                courseManager.setLegacyPracticeSnapshot(loaded.legacyFormat());
            }
        }

        if (course == null || placed == null) {
            source.sendError(Text.literal("No saved course context found. Start a round or load a course first."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Course world is unavailable."));
            return 0;
        }

        List<ServerPlayerEntity> players = resolveRoundParticipants(source, world, selectedPlayers, "resumesession");
        if (players.isEmpty()) {
            source.sendError(Text.literal("No eligible players selected for this world."));
            return 0;
        }

        int resumed = 0;
        int skipped = 0;
        int manualUsed = 0;
        int autoUsed = 0;
        for (ServerPlayerEntity player : players) {
            UUID playerId = player.getUuid();
            var manual = playerRoundSessionStorage.loadPlayer(source.getServer(), playerId, null).orElse(null);
            PlayerRoundState autoState = roundStateManager.getState(playerId).orElse(null);

            PlayerRoundState chosenState = null;
            boolean usedManual = false;
            if (sourceSelection != ResumeSourceSelection.AUTO_ONLY && manual != null) {
                if (isManualSessionCompatible(manual, course, placed)) {
                    chosenState = manual.state();
                    usedManual = true;
                }
            }

            if (chosenState == null && sourceSelection != ResumeSourceSelection.MANUAL_ONLY && autoState != null) {
                chosenState = autoState;
                usedManual = false;
            }

            if (chosenState == null) {
                skipped++;
                continue;
            }

            BlockPos restoredFeet = resolveSafeFeetNearWithin(world, chosenState.lie(), 2);
            PlayerRoundState restoredState = chosenState.withLie(restoredFeet);

            roundStateManager.setState(playerId, restoredState);
            courseManager.addActiveParticipantId(playerId);
            courseManager.setRoundActive(true);

            RoundInventoryCleaner.restoreRoundInventory(player);
            ScorecardManager.ensureScorecardInInventory(player);
            player.teleport(restoredFeet.getX() + 0.5, restoredFeet.getY() + 1.0, restoredFeet.getZ() + 0.5);

            if (usedManual) {
                playerRoundSessionStorage.clearPlayer(source.getServer(), playerId, null);
                manualUsed++;
            } else {
                autoUsed++;
            }

            player.sendMessage(Text.literal(
                    "Session resumed at hole " + restoredState.currentHole()
                            + " (strokes " + restoredState.totalStrokes() + ")"
                            + (usedManual ? " from manual save." : " from auto state.")
            ), true);
            resumed++;
        }

        final int resumedCount = resumed;
        final int skippedCount = skipped;
        final int manualCount = manualUsed;
        final int autoCount = autoUsed;
        source.sendFeedback(() -> Text.literal(
                "Resume session complete. Resumed=" + resumedCount
                        + " (manual=" + manualCount + ", auto=" + autoCount + ")"
                        + ", skipped=" + skippedCount + "."
        ), true);
        return resumedCount > 0 ? 1 : 0;
    }

    private static boolean isManualSessionCompatible(
            PlayerRoundSessionStorage.LoadedPlayerRoundSession session,
            Course course,
            PlacedCourseState placed
    ) {
        if (session == null || course == null || placed == null) {
            return false;
        }
        if (!placed.worldKey().getValue().toString().equals(session.worldKey())) {
            return false;
        }
        if (course.seed() != session.courseSeed()) {
            return false;
        }
        return course.holes().size() == session.holeCount();
    }

    public static int executeRoundSessionStatus(
            ServerCommandSource source,
            RoundSessionStorage roundSessionStorage
    ) {
        Optional<RoundSessionStorage.LoadedRoundSession> snapshot = roundSessionStorage.load(source.getServer(), null);
        if (snapshot.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Round session snapshot: none."), false);
            return 1;
        }

        RoundSessionStorage.LoadedRoundSession loaded = snapshot.get();
        int participantCount = loaded.participantIds().size();
        int stateCount = loaded.playerStates().size();
        int completedCount = loaded.completedTotals().size();
        String worldKey = loaded.worldKey();
        long seed = loaded.courseSeed();
        int holes = loaded.holeCount();

        source.sendFeedback(() -> Text.literal(
                "Round session snapshot: active=" + loaded.roundActive()
                        + ", participants=" + participantCount
                        + ", states=" + stateCount
                        + ", completed=" + completedCount
                        + ", world=" + worldKey
                        + ", seed=" + seed
                        + ", holes=" + holes
        ), false);

        int listed = 0;
        for (UUID participantId : loaded.participantIds()) {
            if (listed >= 10) {
                break;
            }

            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(participantId);
            String label = onlinePlayer == null
                    ? participantId.toString().substring(0, 8)
                    : onlinePlayer.getName().getString();
            var state = loaded.playerStates().get(participantId);
            String stateLabel = state == null
                    ? "no-state"
                    : ("H" + state.currentHole() + " strokes=" + state.totalStrokes());
            source.sendFeedback(() -> Text.literal(" - " + label + " | " + stateLabel), false);
            listed++;
        }

        if (participantCount > listed) {
            int remaining = participantCount - listed;
            source.sendFeedback(() -> Text.literal(" - ... and " + remaining + " more participant(s)."), false);
        }
        return 1;
    }

    public static int executeRoundSessionClear(
            ServerCommandSource source,
            RoundSessionStorage roundSessionStorage,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage
    ) {
        // Clear in-memory round state first so the autosave tick doesn't immediately recreate the file.
        if (courseManager.isRoundActive() || courseManager.getActiveCourse().isPresent()) {
            courseManager.setActiveCourse(null);
            courseManager.clearPlacedCourseState();
            courseManager.setActiveCourseCatalogIndex(null);
            courseManager.setRoundActive(false);
            roundStateManager.clearAll();
            courseManager.setActiveParticipantIds(java.util.Set.of());
            practiceCourseStorage.clear(source.getServer());
        }
        com.mcdg.game.HoleProgressTracker.resetAllState(source.getServer());
        roundSessionStorage.clear(source.getServer(), null);
        source.sendFeedback(() -> Text.literal(
                "Round state and session file cleared. The world should return to normal."
        ), true);
        return 1;
    }

    private static List<ServerPlayerEntity> resolveRoundParticipants(
            ServerCommandSource source,
            ServerWorld world,
            Collection<ServerPlayerEntity> selectedPlayers,
            String commandName
    ) {
        LinkedHashSet<ServerPlayerEntity> participants = new LinkedHashSet<>();
        if (selectedPlayers != null && !selectedPlayers.isEmpty()) {
            participants.addAll(selectedPlayers);
        } else {
            ServerPlayerEntity sourcePlayer = source.getPlayer();
            if (sourcePlayer == null) {
                source.sendError(Text.literal(
                        "Console usage requires explicit players: /mcdg " + commandName + " <players>."
                ));
                return List.of();
            }
            participants.add(sourcePlayer);
        }

        List<ServerPlayerEntity> sameWorldParticipants = new ArrayList<>();
        int skippedDifferentWorld = 0;
        for (ServerPlayerEntity participant : participants) {
            if (participant.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                sameWorldParticipants.add(participant);
            } else {
                skippedDifferentWorld++;
            }
        }

        final int skippedCount = skippedDifferentWorld;
        if (skippedCount > 0) {
            source.sendFeedback(() -> Text.literal(
                    "Skipped " + skippedCount + " player(s) not in the current course world."
            ), false);
        }

        return sameWorldParticipants;
    }

    private static void removeRoundThrowItems(ServerPlayerEntity player) {
        RoundInventoryCleaner.purgeRoundItemsAndJunk(player);
    }

    private static BlockPos resolveSafeFeetNearWithin(ServerWorld world, BlockPos preferredFeet, int maxRadius) {
        int safeRadius = Math.max(0, maxRadius);
        if (isStandableFeet(world, preferredFeet)) {
            return preferredFeet;
        }

        for (int radius = 1; radius <= safeRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx * dx) + (dz * dz) > (safeRadius * safeRadius)) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos candidate = preferredFeet.add(dx, dy, dz);
                        if (isStandableFeet(world, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return preferredFeet;
    }

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        if (!world.getFluidState(feet).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }

        BlockPos head = feet.up();
        if (!world.getFluidState(head).isEmpty()) {
            return false;
        }
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }

        BlockPos ground = feet.down();
        if (!world.getFluidState(ground).isEmpty()) {
            return false;
        }

        return !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();
    }
}
