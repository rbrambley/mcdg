package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.ChallengeCourseCatalog;
import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.LostCourse;
import com.mcdg.game.LostCourseStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundChunkLoader;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.RoundWindService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.game.PlayerRoundSessionStorage;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class CourseCleanupCommand {
    private CourseCleanupCommand() {
    }

    static int executeCleanupCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage
    ) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            source.sendError(Text.literal("No placed course state to reset."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Original world for placed course is not available."));
            return 0;
        }

        evacuatePlayersBeforeCleanup(source, world, placed);
        RoundChunkLoader.unloadAll(world);
        Optional<UUID> challengeCourseId = courseManager.getActiveChallengeCourseId();
        placementService.resetPlacedCourse(world, placed);
        CommandUtils.removeJunkDropsNearCourse(world, placed);
        CommandUtils.removeTemporaryRoundItemsFromCourseWorldPlayers(source, courseManager);
        RoundWindService.onRoundEnd(world);
        courseManager.clearPlacedCourseState();
        courseManager.setRoundActive(false);
        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        practiceCourseStorage.clear(source.getServer());

        if (challengeCourseId.isPresent()) {
            UUID courseId = challengeCourseId.get();
            LostCourseStorage.clearPlacedState(source.getServer(), courseId);
            ChallengeCourseManager.getCatalog().ifPresent(catalog -> {
                catalog.getCourse(courseId).ifPresent(entry -> {
                    catalog.entries().put(courseId, entry.withPlaced(false));
                    catalog.save(source.getServer());
                });
            });
        }

        source.sendFeedback(() -> Text.literal("Course cleanup complete. Original blocks restored."), true);
        return 1;
    }

    private static void evacuatePlayersBeforeCleanup(ServerCommandSource source, ServerWorld world, PlacedCourseState placed) {
        ServerPlayerEntity sourcePlayer = source.getPlayer();
        BlockPos sourceAnchorSafeFeet = sourcePlayer != null && sourcePlayer.getWorld().getRegistryKey().equals(world.getRegistryKey())
                ? CommandUtils.resolveSafeFeetNear(world, sourcePlayer.getBlockPos())
                : CommandUtils.resolveSafeFeetNear(world, world.getSpawnPos());
        if (isWithinPlacedCourseBuffer(placed, sourceAnchorSafeFeet, 28)) {
            sourceAnchorSafeFeet = findNearestSafeOutsideCourse(world, placed, sourceAnchorSafeFeet, 28);
        }

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (!player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                continue;
            }

            BlockPos targetFeet = CommandUtils.resolveSafeFeetNear(world, player.getBlockPos());
            String relocationReason = "nearby";

            if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                targetFeet = findNearestSafeOutsideCourse(world, placed, targetFeet, 28);
                relocationReason = "nearby-safe";
            }
            if (isWithinPlacedCourseBuffer(placed, targetFeet, 28)) {
                targetFeet = sourceAnchorSafeFeet;
                relocationReason = "admin";
            }

            player.teleport(targetFeet.getX() + 0.5, targetFeet.getY() + 1.0, targetFeet.getZ() + 0.5);
            if ("nearby".equals(relocationReason) || "nearby-safe".equals(relocationReason)) {
                player.sendMessage(Text.literal("Course cleanup in progress. Relocated to a nearby safe location."), true);
            } else {
                player.sendMessage(Text.literal("Course cleanup in progress. Relocated to an admin safe zone."), true);
            }
        }
    }

    private static BlockPos findNearestSafeOutsideCourse(ServerWorld world, PlacedCourseState placed, BlockPos originFeet, int bufferBlocks) {
        BlockPos safeOrigin = CommandUtils.resolveSafeFeetNear(world, originFeet);
        if (!isWithinPlacedCourseBuffer(placed, safeOrigin, bufferBlocks)) {
            return safeOrigin;
        }

        for (int radius = 12; radius <= 144; radius += 12) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = CommandUtils.resolveSafeFeetNear(world, safeOrigin.add(dx, 0, dz));
                    if (!isWithinPlacedCourseBuffer(placed, candidate, bufferBlocks)) {
                        return candidate;
                    }
                }
            }
        }

        return safeOrigin;
    }

    private static boolean isWithinPlacedCourseBuffer(PlacedCourseState placed, BlockPos pos, int bufferBlocks) {
        if (placed == null || pos == null || placed.holeTees().isEmpty()) {
            return false;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos tee : placed.holeTees().values()) {
            minX = Math.min(minX, tee.getX());
            maxX = Math.max(maxX, tee.getX());
            minZ = Math.min(minZ, tee.getZ());
            maxZ = Math.max(maxZ, tee.getZ());
        }
        for (BlockPos basket : placed.holeBaskets().values()) {
            minX = Math.min(minX, basket.getX());
            maxX = Math.max(maxX, basket.getX());
            minZ = Math.min(minZ, basket.getZ());
            maxZ = Math.max(maxZ, basket.getZ());
        }
        for (BlockPos alternate : placed.holeAlternateAnchors().values()) {
            minX = Math.min(minX, alternate.getX());
            maxX = Math.max(maxX, alternate.getX());
            minZ = Math.min(minZ, alternate.getZ());
            maxZ = Math.max(maxZ, alternate.getZ());
        }

        int expandedMinX = minX - bufferBlocks;
        int expandedMaxX = maxX + bufferBlocks;
        int expandedMinZ = minZ - bufferBlocks;
        int expandedMaxZ = maxZ + bufferBlocks;
        return pos.getX() >= expandedMinX
                && pos.getX() <= expandedMaxX
                && pos.getZ() >= expandedMinZ
                && pos.getZ() <= expandedMaxZ;
    }

    static int executeGotoCourseByIndex(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            int oneBasedIndex
    ) {
        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndex(source.getServer(), oneBasedIndex);
        if (loaded.isEmpty()) {
            source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
            return 0;
        }

        PlacedCourseState placed = loaded.get().placedCourseState();
        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Hole 1 tee location is unavailable for course #" + oneBasedIndex + "."));
            return 0;
        }

        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            BlockPos safeTee = world == null ? firstTee : CommandUtils.resolveSafeFeetNear(world, firstTee);
            player.teleport(safeTee.getX() + 0.5, safeTee.getY() + 1.0, safeTee.getZ() + 0.5);
            source.sendFeedback(() -> Text.literal("Teleported to Hole 1 tee."), false);
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }

    static int executeCleanupCourseByIndex(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            int oneBasedIndex
    ) {
        // Full load required: cleanup needs originalBlocks to restore the world.
        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndexFull(source.getServer(), oneBasedIndex);
        if (loaded.isEmpty()) {
            source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
            return 0;
        }

        PlacedCourseState placed = loaded.get().placedCourseState();
        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("World for course #" + oneBasedIndex + " is not available."));
            return 0;
        }

        evacuatePlayersBeforeCleanup(source, world, placed);
        RoundChunkLoader.unloadAll(world);
        placementService.resetPlacedCourse(world, placed);
        CommandUtils.removeJunkDropsNearCourse(world, placed);
        CommandUtils.removeTemporaryRoundItemsFromCourseWorldPlayers(source, courseManager);
        RoundWindService.onRoundEnd(world);
        courseManager.setRoundActive(false);
        CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);

        source.sendFeedback(() -> Text.literal("Cleaned up course #" + oneBasedIndex + "."), true);
        return 1;
    }

    static int executeRemoveCourseBoth(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage,
            PlayerRoundSessionStorage playerRoundSessionStorage,
            CoursePlacementService placementService,
            int oneBasedIndex
    ) {
        // First, remove from world (cleanup)
        Optional<PracticeCourseStorage.LoadedPracticeCourse> loaded = practiceCourseStorage.loadReusableByIndexFull(source.getServer(), oneBasedIndex);
        if (loaded.isEmpty()) {
            source.sendError(Text.literal("Course #" + oneBasedIndex + " not found."));
            return 0;
        }

        PlacedCourseState placed = loaded.get().placedCourseState();
        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world != null) {
            evacuatePlayersBeforeCleanup(source, world, placed);
            RoundChunkLoader.unloadAll(world);
            placementService.resetPlacedCourse(world, placed);
            CommandUtils.removeJunkDropsNearCourse(world, placed);
        }

        // Then, remove from catalog
        int removed = practiceCourseStorage.pruneReusableByIndices(source.getServer(), Set.of(oneBasedIndex));
        if (removed <= 0) {
            source.sendError(Text.literal("Failed to remove course #" + oneBasedIndex + " from catalog."));
            return 0;
        }

        // Clear round state if this was the active course
        Integer activeCatalogIndex = courseManager.getActiveCourseCatalogIndex().orElse(null);
        boolean wasActiveMatch = activeCatalogIndex != null && activeCatalogIndex == oneBasedIndex;
        if (wasActiveMatch || courseManager.isRoundActive()) {
            CommandUtils.removeTemporaryRoundItemsFromCourseWorldPlayers(source, courseManager);
            if (world != null) {
                RoundWindService.onRoundEnd(world);
            }
            courseManager.setActiveCourse(null);
            courseManager.clearPlacedCourseState();
            courseManager.setActiveCourseCatalogIndex(null);
            courseManager.setRoundActive(false);
            CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        }
        source.sendFeedback(() -> Text.literal("Removed course #" + oneBasedIndex + " from both catalog and world."), true);
        return 1;
    }

    static int executeCleanupChallenge(
            ServerCommandSource source,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            String courseIdString
    ) {
        Optional<UUID> courseId = parseChallengeCourseId(source, courseIdString);
        if (courseId.isEmpty()) {
            return 0;
        }
        if (!cleanupChallengeCourse(source, placementService, roundStateManager, courseManager, courseId.get())) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Challenge course world blocks cleaned up."), true);
        return 1;
    }

    static int executeRemoveChallenge(
            ServerCommandSource source,
            String courseIdString
    ) {
        Optional<UUID> courseId = parseChallengeCourseId(source, courseIdString);
        if (courseId.isEmpty()) {
            return 0;
        }
        if (!removeChallengeCourse(source, courseId.get())) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Challenge course removed from catalog."), true);
        return 1;
    }

    static int executeRemoveChallengeBoth(
            ServerCommandSource source,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            String courseIdString
    ) {
        Optional<UUID> courseId = parseChallengeCourseId(source, courseIdString);
        if (courseId.isEmpty()) {
            return 0;
        }
        UUID id = courseId.get();
        if (!cleanupChallengeCourse(source, placementService, roundStateManager, courseManager, id)) {
            return 0;
        }
        if (!removeChallengeCourse(source, id)) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Challenge course removed from catalog and world."), true);
        return 1;
    }

    private static Optional<UUID> parseChallengeCourseId(ServerCommandSource source, String courseIdString) {
        // Try parsing as UUID first
        try {
            UUID courseId = UUID.fromString(courseIdString);
            var catalog = ChallengeCourseManager.getCatalog();
            if (catalog.isPresent() && catalog.get().getCourse(courseId).isPresent()) {
                return Optional.of(courseId);
            }
            source.sendError(Text.literal("Challenge course not found with ID: " + courseIdString));
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // Not a UUID, try to find by name
        }

        // Search by name (case-insensitive, partial match)
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendError(Text.literal("Challenge course catalog not available"));
            return Optional.empty();
        }

        String searchLower = courseIdString.toLowerCase();
        UUID foundId = null;
        int matchCount = 0;

        for (var entry : catalog.get().getAllCourses()) {
            if (entry.name().toLowerCase().contains(searchLower)) {
                foundId = entry.courseId();
                matchCount++;
            }
        }

        if (matchCount == 0) {
            source.sendError(Text.literal("Challenge course not found: " + courseIdString));
            return Optional.empty();
        } else if (matchCount > 1) {
            source.sendError(Text.literal("Multiple courses match '" + courseIdString + "'. Please use the full course name or UUID."));
            return Optional.empty();
        }

        return Optional.of(foundId);
    }

    private static Optional<ChallengeCourseCatalog> requireChallengeCatalog(ServerCommandSource source) {
        Optional<ChallengeCourseCatalog> catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendError(Text.literal("Challenge course catalog not available."));
        }
        return catalog;
    }

    private static boolean cleanupChallengeCourse(
            ServerCommandSource source,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            ActiveCourseManager courseManager,
            UUID courseId
    ) {
        Optional<ChallengeCourseCatalog> catalogOpt = requireChallengeCatalog(source);
        if (catalogOpt.isEmpty()) {
            return false;
        }
        ChallengeCourseCatalog catalog = catalogOpt.get();
        Optional<ChallengeCourseCatalog.CatalogEntry> entryOpt = catalog.getCourse(courseId);
        if (entryOpt.isEmpty()) {
            source.sendError(Text.literal("Challenge course not found: " + courseId));
            return false;
        }

        Optional<PlacedCourseState> storedPlaced = LostCourseStorage.loadPlacedState(source.getServer(), courseId);
        if (storedPlaced.isPresent()) {
            PlacedCourseState placed = storedPlaced.get();
            ServerWorld world = source.getServer().getWorld(placed.worldKey());
            if (world != null) {
                evacuatePlayersBeforeCleanup(source, world, placed);
                RoundChunkLoader.unloadAll(world);
                placementService.resetPlacedCourse(world, placed);
                CommandUtils.removeJunkDropsNearCourse(world, placed);
            }
        }

        Optional<UUID> activeChallengeId = courseManager.getActiveChallengeCourseId();
        if (activeChallengeId.isPresent() && activeChallengeId.get().equals(courseId)) {
            if (storedPlaced.isPresent()) {
                ServerWorld activeWorld = source.getServer().getWorld(storedPlaced.get().worldKey());
                if (activeWorld != null) {
                    CommandUtils.removeTemporaryRoundItemsFromCourseWorldPlayers(source, courseManager);
                    RoundWindService.onRoundEnd(activeWorld);
                }
            }
            courseManager.clearPlacedCourseState();
            courseManager.setActiveCourse(null);
            courseManager.setActiveChallengeCourseId(null);
            courseManager.setRoundActive(false);
            CommandUtils.clearRoundStateForTrackedParticipants(courseManager, roundStateManager);
        }

        LostCourseStorage.clearPlacedState(source.getServer(), courseId);
        catalog.entries().put(courseId, entryOpt.get().withPlaced(false));
        catalog.save(source.getServer());
        return true;
    }

    private static boolean removeChallengeCourse(ServerCommandSource source, UUID courseId) {
        Optional<ChallengeCourseCatalog> catalogOpt = requireChallengeCatalog(source);
        if (catalogOpt.isEmpty()) {
            return false;
        }
        ChallengeCourseCatalog catalog = catalogOpt.get();
        if (catalog.getCourse(courseId).isEmpty()) {
            source.sendError(Text.literal("Challenge course not found: " + courseId));
            return false;
        }
        catalog.removeCourse(courseId);
        catalog.save(source.getServer());

        ChallengeCourseManager.getLostCourse(courseId).ifPresent(lostCourse -> {
            ChallengeCourseManager.updateLostCourse(lostCourse.markUndiscovered());
            LostCourseStorage.save(source.getServer(), ChallengeCourseManager.getAllLostCourses());
            ChallengeCourseManager.respawnMapFragment(source.getServer(), courseId);
        });
        return true;
    }
}
