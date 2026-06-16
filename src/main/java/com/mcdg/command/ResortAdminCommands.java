package com.mcdg.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.CourseFireProtection;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlayerRoundSessionStorage;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundInventoryCleaner;
import com.mcdg.game.RoundPresentationService;
import com.mcdg.game.RoundSessionStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.game.ScorecardManager;
import com.mcdg.game.ThrowAutoTestService;
import com.mcdg.McdgMod;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.BuildCourseSessionManager;
import com.mcdg.net.MenuScreenSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.world.PlacementAutoTestService;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.CourseGenerator;
import com.mcdg.world.ResortBuilder;
import com.mcdg.world.ResortWaypointManager;
import com.mcdg.world.ResortCoursePlacement;
import com.mcdg.world.WorldSpawnHandler;
import com.mcdg.world.ResortData;
import com.mcdg.net.WaypointRemovedSync;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class ResortAdminCommands {
    private static final Map<UUID, PendingResortAction> PENDING_RESORT_ACTIONS = new ConcurrentHashMap<>();
    private record PendingResortAction(BlockPos center, boolean overwrite) {}

    private ResortAdminCommands() {
    }

    public static int executeBuildResort(
            ServerCommandSource source,
            CourseGenerator generator,
            AutoCourseService autoCourseService,
            PracticeCourseStorage practiceCourseStorage,
            Integer x,
            Integer z
    ) {
        ServerWorld world = source.getWorld();
        BlockPos center;
        if (x != null && z != null) {
            center = new BlockPos(x, 64, z);
        } else {
            center = BlockPos.ofFloored(source.getPosition());
        }

        ResortData existing = WorldSpawnHandler.loadResortData(source.getServer());
        if (existing != null) {
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                PENDING_RESORT_ACTIONS.put(player.getUuid(), new PendingResortAction(center, false));
            }
            source.sendFeedback(() -> Text.literal(
                    "A resort already exists at (" + existing.centerX + ", " + existing.centerZ + ")."
            ).formatted(Formatting.YELLOW), false);

            Text overwriteBtn = Text.literal("[OVERWRITE]").styled(style -> style
                    .withColor(Formatting.RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg buildresort overwrite"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Clear existing resort and rebuild"))));
            Text cancelBtn = Text.literal("[CANCEL]").styled(style -> style
                    .withColor(Formatting.GRAY)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcdg buildresort cancel"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Cancel operation"))));

            source.sendFeedback(() -> Text.literal("Choose an action: ").append(overwriteBtn).append(" ").append(cancelBtn), false);
            return 1;
        }

        BlockPos lobbyPos = doBuildResort(source, generator, autoCourseService, practiceCourseStorage, center);
        world.setSpawnPos(lobbyPos, 0.0f);
        return 1;
    }

    public static int executeBuildResortOverwrite(
            ServerCommandSource source,
            CourseGenerator generator,
            AutoCourseService autoCourseService,
            PracticeCourseStorage practiceCourseStorage
    ) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        PendingResortAction pending = PENDING_RESORT_ACTIONS.remove(player.getUuid());
        if (pending == null) {
            source.sendError(Text.literal("No pending resort action. Run /mcdg buildresort first."));
            return 0;
        }

        ResortData existing = WorldSpawnHandler.loadResortData(source.getServer());
        if (existing == null) {
            source.sendError(Text.literal("No existing resort found to overwrite."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        BlockPos oldCenter = existing.centerPos();
        resetResortAt(world, oldCenter);

        BlockPos lobbyPos = doBuildResort(source, generator, autoCourseService, practiceCourseStorage, oldCenter);
        world.setSpawnPos(lobbyPos, 0.0f);
        source.sendFeedback(() -> Text.literal("World spawn updated to new resort lobby.").formatted(Formatting.GREEN), true);
        return 1;
    }

    public static int executeBuildResortCancel(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            PENDING_RESORT_ACTIONS.remove(player.getUuid());
        }
        source.sendFeedback(() -> Text.literal("Resort build cancelled.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static BlockPos doBuildResort(
            ServerCommandSource source,
            CourseGenerator generator,
            AutoCourseService autoCourseService,
            PracticeCourseStorage practiceCourseStorage,
            BlockPos center
    ) {
        ServerWorld world = source.getWorld();
        java.util.Map<BlockPos, BlockState> originalBlocks = new java.util.HashMap<>();
        java.util.Set<BlockPos> protectedPositions = new java.util.HashSet<>();

        ResortBuilder.placeResort(world, center, originalBlocks, protectedPositions);

        int courseCount = 3;
        java.util.Random random = new java.util.Random(world.getSeed());
        int builtCourses = 0;

        java.util.List<ResortCoursePlacement.Candidate> candidates = ResortCoursePlacement.selectCourseAnchors(world, center, random);
        if (candidates.size() < courseCount) {
            source.sendFeedback(() -> Text.literal(
                    "Warning: only " + candidates.size() + " suitable course locations found (wanted " + courseCount + ")."
            ).formatted(Formatting.YELLOW), true);
        }

        for (int c = 0; c < Math.min(courseCount, candidates.size()); c++) {
            ResortCoursePlacement.Candidate candidate = candidates.get(c);
            int currentCourseNum = c + 1;
            source.sendFeedback(() -> Text.literal("Building resort surround course " + currentCourseNum + "/" + courseCount + "...").formatted(Formatting.YELLOW), true);

            BlockPos hubOrigin = candidate.pos();
            double angle = candidate.angle();
            int distance = (int) Math.round(Math.sqrt(
                    Math.pow(hubOrigin.getX() - center.getX(), 2) +
                    Math.pow(hubOrigin.getZ() - center.getZ(), 2)
            ));

            long seed = random.nextLong();
            float facingYaw = (float) Math.toDegrees(angle);
            Course course = autoCourseService.generateOutwardConeCourse(seed, center, facingYaw, distance, 80);

            try {
                AutoCourseService.AutoCourseScenarioResult result = autoCourseService.placeCourseIncrementally(world, hubOrigin, course, true, msg -> {
                    source.sendFeedback(() -> Text.literal(msg).formatted(Formatting.YELLOW), true);
                });
                int catalogIndex = practiceCourseStorage.saveReusable(source.getServer(), result.course(), result.placedState(), "resort-surround", false);
                builtCourses++;
                int completedCourseNum = builtCourses;
                source.sendFeedback(() -> Text.literal("Surround course " + completedCourseNum + " complete.").formatted(Formatting.GREEN), true);
                McdgMod.LOGGER.info("Resort surround course {} placed at ({}, {}), saved as catalog #{}" , builtCourses, hubOrigin.getX(), hubOrigin.getZ(), catalogIndex);
            } catch (Exception ex) {
                int failedCourseNum = c + 1;
                source.sendFeedback(() -> Text.literal("Surround course " + failedCourseNum + " failed: " + ex.getMessage()).formatted(Formatting.RED), true);
                McdgMod.LOGGER.warn("Resort surround course {} failed at ({}, {}): {}", c + 1, hubOrigin.getX(), hubOrigin.getZ(), ex.getMessage());
            }
        }

        BlockPos lobbyPos = center.east(23);
        int finalBuiltCourses = builtCourses;
        source.sendFeedback(() -> Text.literal(
                "Resort built at X=" + center.getX() + " Z=" + center.getZ() +
                ". Lobby at X=" + lobbyPos.getX() + " Z=" + lobbyPos.getZ() +
                ". " + finalBuiltCourses + " surround course(s) generated." +
                " Use the courtyard paths to explore."
        ), true);
        return lobbyPos;
    }

    private static void resetResortAt(ServerWorld world, BlockPos resortCenter) {
        int clearRadius = 45;
        int baseY = resortCenter.getY() - 1;
        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                for (int y = baseY; y <= baseY + 15; y++) {
                    BlockPos clearPos = new BlockPos(resortCenter.getX() + dx, y, resortCenter.getZ() + dz);
                    world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
                BlockPos surfacePos = new BlockPos(resortCenter.getX() + dx, baseY, resortCenter.getZ() + dz);
                world.setBlockState(surfacePos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        int itemRemovalRadius = clearRadius;
        var entities = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(
                        resortCenter.getX() - itemRemovalRadius, baseY, resortCenter.getZ() - itemRemovalRadius,
                        resortCenter.getX() + itemRemovalRadius, baseY + 20, resortCenter.getZ() + itemRemovalRadius
                ), entity -> true);
        for (var item : entities) {
            item.discard();
        }
        ResortWaypointManager.clearResortWaypoint();
        for (var player : world.getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new WaypointRemovedSync.Payload("MCDG Resort"));
        }
    }

    public static int executeResetResort(ServerCommandSource source) {
        ServerWorld world = source.getWorld();
        BlockPos resortCenter = BlockPos.ofFloored(source.getPosition());
        // Clear area larger than the 80x80 compound so player can stand anywhere nearby
        int clearRadius = 45; // COMPOUND_SIZE/2 + 5
        int baseY = resortCenter.getY() - 1; // player stands 1 block above surface

        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                // Clear from surface up to max building height
                for (int y = baseY; y <= baseY + 15; y++) {
                    BlockPos clearPos = new BlockPos(resortCenter.getX() + dx, y, resortCenter.getZ() + dz);
                    world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
                // Restore grass at surface level
                BlockPos surfacePos = new BlockPos(resortCenter.getX() + dx, baseY, resortCenter.getZ() + dz);
                world.setBlockState(surfacePos, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        // Remove all dropped items in the area
        int itemRemovalRadius = clearRadius;
        var entities = world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(
                        resortCenter.getX() - itemRemovalRadius, baseY, resortCenter.getZ() - itemRemovalRadius,
                        resortCenter.getX() + itemRemovalRadius, baseY + 20, resortCenter.getZ() + itemRemovalRadius
                ), entity -> true);
        for (var item : entities) {
            item.discard();
        }

        // Clear resort waypoint and notify all online players
        ResortWaypointManager.clearResortWaypoint();
        for (var player : world.getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new WaypointRemovedSync.Payload("MCDG Resort"));
        }

        int itemsRemoved = entities.size();
        String itemMsg = itemsRemoved > 0 ? " (" + itemsRemoved + " dropped items cleared)" : "";
        source.sendFeedback(() -> Text.literal(
                "Resort cleared at X=" + resortCenter.getX() + " Z=" + resortCenter.getZ() +
                ". Area is ready for rebuilding." + itemMsg
        ), true);
        return 1;
    }

    public static int executeRemoveSurroundCourses(
            ServerCommandSource source,
            CoursePlacementService placementService,
            PracticeCourseStorage practiceCourseStorage
    ) {
        List<PracticeCourseStorage.ReusableCourseEntry> allCourses = practiceCourseStorage.listReusable(source.getServer());
        List<PracticeCourseStorage.ReusableCourseEntry> surroundCourses = new ArrayList<>();
        for (PracticeCourseStorage.ReusableCourseEntry entry : allCourses) {
            if ("resort-surround".equals(entry.sourceTag())) {
                surroundCourses.add(entry);
            }
        }

        if (surroundCourses.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No resort surround courses found in catalog."), false);
            return 1;
        }

        int resetCount = 0;
        int failCount = 0;
        Set<Integer> indicesToRemove = new HashSet<>();

        for (PracticeCourseStorage.ReusableCourseEntry entry : surroundCourses) {
            int idx = entry.index();
            // Full load required: resort removal needs originalBlocks to restore the world.
            var loaded = practiceCourseStorage.loadReusableByIndexFull(source.getServer(), idx);
            if (loaded.isEmpty()) {
                failCount++;
                indicesToRemove.add(idx);
                continue;
            }
            var courseData = loaded.get();
            ServerWorld courseWorld = source.getServer().getWorld(courseData.placedCourseState().worldKey());
            if (courseWorld != null) {
                placementService.resetPlacedCourse(courseWorld, courseData.placedCourseState());
            }
            CommandUtils.broadcastCourseWaypointRemoval(source.getServer(), courseData.course().name());
            indicesToRemove.add(idx);
            resetCount++;
        }

        if (!indicesToRemove.isEmpty()) {
            practiceCourseStorage.pruneReusableByIndices(source.getServer(), indicesToRemove);
        }

        int finalResetCount = resetCount;
        int finalFailCount = failCount;
        source.sendFeedback(() -> Text.literal(
            "Removed " + finalResetCount + " resort surround course(s)" +
            (finalFailCount > 0 ? " (" + finalFailCount + " failed to reset)" : "") + "."
        ), true);
        return 1;
    }

    public static boolean isCourseOverlappingResort(net.minecraft.server.MinecraftServer server, PlacedCourseState placed) {
        ResortData resort = WorldSpawnHandler.loadResortData(server);
        if (resort == null) {
            return false;
        }
        BlockPos resortCenter = resort.centerPos();
        int protectRadius = 50; // covers 80x80 compound + buffer
        for (BlockPos tee : placed.holeTees().values()) {
            int dx = Math.abs(tee.getX() - resortCenter.getX());
            int dz = Math.abs(tee.getZ() - resortCenter.getZ());
            if (dx <= protectRadius && dz <= protectRadius) {
                return true;
            }
        }
        for (BlockPos basket : placed.holeBaskets().values()) {
            int dx = Math.abs(basket.getX() - resortCenter.getX());
            int dz = Math.abs(basket.getZ() - resortCenter.getZ());
            if (dx <= protectRadius && dz <= protectRadius) {
                return true;
            }
        }
        for (BlockPos alternate : placed.holeAlternateAnchors().values()) {
            int dx = Math.abs(alternate.getX() - resortCenter.getX());
            int dz = Math.abs(alternate.getZ() - resortCenter.getZ());
            if (dx <= protectRadius && dz <= protectRadius) {
                return true;
            }
        }
        return false;
    }
}
