package com.mcdg.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mcdg.McdgMod;
import com.mcdg.data.BasketPoint;
import com.mcdg.data.Course;
import com.mcdg.data.FairwaySegment;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import com.mcdg.data.TeePoint;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CoursePlacementValidator;
import com.mcdg.world.HoleLayoutValidator;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.Block;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BuildCourseSessionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "mcdg_buildcourse_session.json";
    private static final int MIN_HOLES = 3;
    private static final int MAX_HOLES = 27;
    private static final long CLAIM_TIMEOUT_MS = 24L * 60L * 60L * 1000L;
    private static final long CONFIRM_TIMEOUT_MS = 15_000L;
    private static final int MIN_DISTANCE_FEET = 180;
    private static final int MAX_DISTANCE_FEET = 780;
    private static final int MAX_DISTANCE_DRIFT_FEET = 180;
    private static final int PAR3_MAX_FEET = 400;
    private static final int PAR4_MAX_FEET = 700;
    private static final int MIN_FAIRWAY_WIDTH = 4;
    private static final int MAX_FAIRWAY_WIDTH = 10;

    private final CoursePlacementService placementService;
    private final CoursePlacementValidator placementValidator;
    private final PracticeCourseStorage practiceCourseStorage;
    private final HoleLayoutValidator layoutValidator = new HoleLayoutValidator();

    private BuildSession session;

    public BuildCourseSessionManager(
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            PracticeCourseStorage practiceCourseStorage
    ) {
        this.placementService = placementService;
        this.placementValidator = placementValidator;
        this.practiceCourseStorage = practiceCourseStorage;
    }

    public void load(MinecraftServer server) {
        Path path = resolvePath(server);
        if (!Files.exists(path)) {
            session = null;
            return;
        }

        try {
            BuildSessionSnapshot snapshot = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), BuildSessionSnapshot.class);
            if (snapshot == null) {
                session = null;
                return;
            }
            session = snapshot.toSession();
        } catch (IOException | JsonParseException ex) {
            McdgMod.LOGGER.error("Failed to load buildcourse session snapshot", ex);
            session = null;
        }
    }

    public void save(MinecraftServer server) {
        Path path = resolvePath(server);
        try {
            if (session == null) {
                Files.deleteIfExists(path);
                return;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(BuildSessionSnapshot.from(session)), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to save buildcourse session snapshot", ex);
        }
    }

    public void tick(MinecraftServer server) {
        if (session == null || session.paused) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerId);
        if (owner == null) {
            return;
        }

        session.ownerLastSeenAtMs = System.currentTimeMillis();
        session.updatedAtMs = System.currentTimeMillis();

        if (session.previewTickGate++ % 5 != 0) {
            return;
        }

        if (session.holeCount <= 0) {
            return;
        }

        int holeIndex = currentTargetHoleIndex();
        if (!isActiveHoleIndex(holeIndex)) {
            return;
        }

        ServerWorld world = owner.getServerWorld();
        PreviewSpec spec = computePreview(owner, holeIndex);

        spawnMarker(world, owner, spec.teeAnchor, 0.4, 0.95, 0.2);
        spawnMarker(world, owner, spec.basketAnchor, 0.95, 0.2, 0.2);
        spawnPath(world, owner, spec.teeAnchor, spec.basketAnchor, 0.2, 0.75, 0.95);
    }

    public LiteralArgumentBuilder<ServerCommandSource> registerNode() {
        return literal("buildcourse")
                .executes(this::executeBuildCourseRoot)
                .then(literal("status").executes(this::executeStatus))
                .then(literal("holes")
                        .then(argument("count", IntegerArgumentType.integer(MIN_HOLES, MAX_HOLES))
                                .executes(ctx -> executeSetHoles(ctx, IntegerArgumentType.getInteger(ctx, "count"))))
                        .then(literal("presets")
                                .executes(this::executeHolePresetMenu))
                        .then(literal("custom")
                                .executes(this::executeCustomHolePrompt)))
                .then(literal("build").executes(this::executeBuildHole))
                .then(literal("undo").executes(this::executeUndoLast))
                .then(literal("pause").executes(this::executePause))
                .then(literal("resume").executes(this::executeResume))
                .then(literal("cancel").executes(this::executeCancelSession))
                .then(literal("finalize").executes(this::executeFinalizePrompt))
                .then(literal("save-name")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeSaveName(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(literal("save-auto-rename").executes(this::executeSaveAutoRename))
                .then(literal("discard").executes(this::executeDiscard))
                .then(literal("rebuild-request")
                        .then(argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeRebuildRequest(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
                .then(literal("rebuild-run")
                        .then(argument("token", LongArgumentType.longArg(1L))
                                .executes(ctx -> executeRebuildRun(ctx, LongArgumentType.getLong(ctx, "token")))))
                .then(literal("rebuild-cancel").executes(this::executeRebuildCancel))
                .then(literal("claim").executes(this::executeClaimRequest))
                .then(literal("claim-run")
                        .then(argument("token", LongArgumentType.longArg(1L))
                                .executes(ctx -> executeClaimRun(ctx, LongArgumentType.getLong(ctx, "token")))))
                .then(literal("claim-cancel").executes(this::executeClaimCancel));
    }

    private int executeBuildCourseRoot(CommandContext<ServerCommandSource> context) {
        return executeBuildCourseRoot(context.getSource());
    }

    public int executeBuildCourseRoot(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception ex) {
            source.sendError(Text.literal("buildcourse must be run by a player."));
            return 0;
        }

        long now = System.currentTimeMillis();
        if (session == null) {
            session = BuildSession.newSession(player);
            save(source.getServer());
            sendHolePresetMenu(source);
            return 1;
        }

        if (session.ownerId.equals(player.getUuid())) {
            session.ownerLastSeenAtMs = now;
            sendStatus(source, true, true);
            return 1;
        }

        boolean claimReady = now - session.ownerLastSeenAtMs >= CLAIM_TIMEOUT_MS;
        sendStatus(source, false, claimReady);
        return 0;
    }

    public int executeHolePresetMenu(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        sendHolePresetMenu(source);
        return 1;
    }

    private int executeHolePresetMenu(CommandContext<ServerCommandSource> context) {
        return executeHolePresetMenu(context.getSource());
    }

    public int executeCustomHolePrompt(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Custom holes (" + MIN_HOLES + "-" + MAX_HOLES + "):"), false);
        source.sendFeedback(() -> button("ENTER COUNT", "/mcdg buildcourse holes ", Formatting.YELLOW, false), false);
        source.sendFeedback(() -> button("BACK", "/mcdg buildcourse holes presets", Formatting.GRAY, true), false);
        return 1;
    }

    private int executeCustomHolePrompt(CommandContext<ServerCommandSource> context) {
        return executeCustomHolePrompt(context.getSource());
    }

    private int executeSetHoles(CommandContext<ServerCommandSource> context, int holeCount) {
        return executeSetHoles(context.getSource(), holeCount);
    }

    public int executeSetHoles(ServerCommandSource source, int holeCount) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (holeCount < MIN_HOLES || holeCount > MAX_HOLES) {
            source.sendError(Text.literal("Hole count must be between " + MIN_HOLES + " and " + MAX_HOLES + "."));
            return 0;
        }
        session.holeCount = holeCount;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Builder configured for " + holeCount + " holes."), false);
        sendBuildStepPrompt(source);
        return 1;
    }

    private int executeStatus(CommandContext<ServerCommandSource> context) {
        return executeStatus(context.getSource());
    }

    public int executeStatus(ServerCommandSource source) {
        if (session == null) {
            source.sendFeedback(() -> Text.literal("No active buildcourse session."), false);
            return 1;
        }

        ServerPlayerEntity player = source.getPlayer();
        boolean owner = player != null && session.ownerId.equals(player.getUuid());
        boolean claimReady = System.currentTimeMillis() - session.ownerLastSeenAtMs >= CLAIM_TIMEOUT_MS;
        sendStatus(source, owner, claimReady);
        return 1;
    }

    private int executeBuildHole(CommandContext<ServerCommandSource> context) {
        return executeBuildHole(context.getSource());
    }

    public int executeBuildHole(ServerCommandSource source) {
        if (!ensureOwner(source, true)) {
            return 0;
        }
        if (session.holeCount <= 0) {
            source.sendError(Text.literal("Select hole count first."));
            sendHolePresetMenu(source);
            return 0;
        }

        int targetIndex = currentTargetHoleIndex();
        if (!isActiveHoleIndex(targetIndex)) {
            source.sendError(Text.literal("All holes already built. Use /mcdg buildcourse finalize."));
            return 0;
        }

        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception ex) {
            source.sendError(Text.literal("buildcourse build must be run by a player."));
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        PreviewSpec preview = computePreview(player, targetIndex);
        BlockPos desiredCenter = midpoint(preview.teeAnchor, preview.basketAnchor);
        int localTeeX = preview.teeAnchor.getX() - desiredCenter.getX();
        int localTeeZ = preview.teeAnchor.getZ() - desiredCenter.getZ();
        int localBasketX = preview.basketAnchor.getX() - desiredCenter.getX();
        int localBasketZ = preview.basketAnchor.getZ() - desiredCenter.getZ();

        List<Hole> existingForOverlap = new ArrayList<>();
        for (BuiltHole built : session.builtHoles) {
            if (built.index != targetIndex) {
                existingForOverlap.add(built.hole);
            }
        }

        Hole candidate = new Hole(
                targetIndex,
                preview.par,
                preview.distanceFeet,
                new TeePoint(localTeeX, 64, localTeeZ),
                new BasketPoint(localBasketX, 64, localBasketZ, preview.basketHeight),
                List.of(new FairwaySegment(
                        localTeeX,
                        localTeeZ,
                        localBasketX,
                        localBasketZ,
                        preview.fairwayWidth
                )),
                SignatureHoleType.NONE
        );

        if (!layoutValidator.isNonOverlapping(candidate, existingForOverlap)) {
            source.sendError(Text.literal("Hole overlaps existing layout. Move and try again."));
            sendBuildStepPrompt(source);
            return 0;
        }

        Course tempCourse = new Course(preview.seed, "builder-hole-" + targetIndex, List.of(candidate));
        PlacedCourseState placed = null;
        try {
            placed = placementService.placeCourseAtFixedOrigin(world, desiredCenter, tempCourse, ignored -> {});
            BlockPos actualTee = placed.holeTees().get(targetIndex);
            BlockPos actualBasket = placed.holeBaskets().get(targetIndex);
            if (actualTee == null || actualBasket == null) {
                rollbackPlaced(world, placed);
                source.sendError(Text.literal("Hole build did not produce a tee and basket. Move and try again."));
                sendBuildStepPrompt(source);
                return 0;
            }

            int actualDistanceFeet = layoutValidator.distanceFeetFromBlocks(
                    actualTee.getX(),
                    actualTee.getZ(),
                    actualBasket.getX(),
                    actualBasket.getZ()
            );
                if (actualDistanceFeet > (preview.distanceFeet + MAX_DISTANCE_DRIFT_FEET)) {
                rollbackPlaced(world, placed);
                source.sendError(Text.literal(
                    "Hole stretched too far from preview (preview=" + preview.distanceFeet
                        + "ft, actual=" + actualDistanceFeet
                        + "ft). Move to a clearer area and try again."
                ));
                sendBuildStepPrompt(source);
                return 0;
                }
            int effectivePar = placed.effectiveHolePars().getOrDefault(targetIndex, computePar(actualDistanceFeet));
            Hole actualHole = new Hole(
                    targetIndex,
                    effectivePar,
                    actualDistanceFeet,
                    new TeePoint(actualTee.getX(), actualTee.getY(), actualTee.getZ()),
                    new BasketPoint(actualBasket.getX(), actualBasket.getY(), actualBasket.getZ(), preview.basketHeight),
                    List.of(new FairwaySegment(
                            actualTee.getX(),
                            actualTee.getZ(),
                            actualBasket.getX(),
                            actualBasket.getZ(),
                            preview.fairwayWidth
                    )),
                    SignatureHoleType.NONE
            );

            var validation = placementValidator.validatePlacedCourse(world, new Course(preview.seed, tempCourse.name(), List.of(actualHole)), placed, "buildcourse-hole-" + targetIndex);
            if (!validation.passed()) {
                rollbackPlaced(world, placed);
                source.sendError(Text.literal("Hole validation failed with " + validation.issueCount() + " issue(s)."));
                for (var issue : validation.issues()) {
                    source.sendError(Text.literal(" - " + issue.message()));
                }
                sendBuildStepPrompt(source);
                return 0;
            }
            BuiltHole builtHole = BuiltHole.from(actualHole, placed, world.getRegistryKey().getValue().toString(), preview.seed, actualTee);
            upsertBuiltHole(builtHole);
            session.dimensionsUsed.add(world.getRegistryKey().getValue().toString());
            session.updatedAtMs = System.currentTimeMillis();
        } catch (Exception ex) {
            if (placed != null) {
                rollbackPlaced(world, placed);
            }
            source.sendError(Text.literal("Hole build failed: " + ex.getMessage()));
            sendBuildStepPrompt(source);
            return 0;
        }

        if (session.rebuildHoleIndex > 0) {
            source.sendFeedback(() -> Text.literal("Rebuilt hole " + targetIndex + "."), false);
            session.rebuildHoleIndex = -1;
        } else {
            session.nextHoleIndex = Math.max(session.nextHoleIndex, targetIndex + 1);
            source.sendFeedback(() -> Text.literal("Built hole " + targetIndex + "."), false);
        }

        save(source.getServer());

        if (session.nextHoleIndex > session.holeCount && session.rebuildHoleIndex <= 0) {
            source.sendFeedback(() -> Text.literal("All holes built. Open final summary."), false);
            source.sendFeedback(() -> button("FINAL SUMMARY", "/mcdg buildcourse finalize", Formatting.GOLD, true), false);
        } else {
            sendBuildStepPrompt(source);
        }

        return 1;
    }

    private int executeUndoLast(CommandContext<ServerCommandSource> context) {
        return executeUndoLast(context.getSource());
    }

    public int executeUndoLast(ServerCommandSource source) {
        if (!ensureOwner(source, true)) {
            return 0;
        }
        if (session.builtHoles.isEmpty()) {
            source.sendError(Text.literal("No built holes to undo."));
            return 0;
        }

        BuiltHole last = session.builtHoles.stream().max(Comparator.comparingInt(h -> h.index)).orElse(null);
        if (last == null) {
            source.sendError(Text.literal("No built holes to undo."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(resolveWorldKey(last.worldId));
        if (world != null) {
            rollbackPlaced(world, last.placedState());
        }

        session.builtHoles.removeIf(h -> h.index == last.index);
        session.nextHoleIndex = last.index;
        session.rebuildHoleIndex = -1;
        session.updatedAtMs = System.currentTimeMillis();

        teleportBehindOriginalTee(source, last);
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Undid hole " + last.index + "."), false);
        sendBuildStepPrompt(source);
        return 1;
    }

    private int executePause(CommandContext<ServerCommandSource> context) {
        return executePause(context.getSource());
    }

    public int executePause(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        session.paused = true;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Buildcourse paused."), false);
        source.sendFeedback(() -> button("RESUME", "/mcdg buildcourse resume", Formatting.GREEN, true), false);
        source.sendFeedback(() -> button("STATUS", "/mcdg buildcourse status", Formatting.AQUA, true), false);
        source.sendFeedback(() -> button("CANCEL", "/mcdg buildcourse cancel", Formatting.RED, true), false);
        return 1;
    }

    private int executeResume(CommandContext<ServerCommandSource> context) {
        return executeResume(context.getSource());
    }

    public int executeResume(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        session.paused = false;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Buildcourse resumed."), false);
        sendBuildStepPrompt(source);
        return 1;
    }

    private int executeCancelSession(CommandContext<ServerCommandSource> context) {
        return executeCancelSession(context.getSource());
    }

    public int executeCancelSession(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        rollbackAllBuiltHoles(source.getServer());
        session = null;
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Buildcourse session canceled and discarded."), false);
        return 1;
    }

    private int executeFinalizePrompt(CommandContext<ServerCommandSource> context) {
        return executeFinalizePrompt(context.getSource());
    }

    public int executeFinalizePrompt(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (!isComplete()) {
            source.sendError(Text.literal("Cannot finalize before all holes are built."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Buildcourse final summary").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (BuiltHole hole : sortedBuiltHoles()) {
            source.sendFeedback(() -> Text.literal(
                    "Hole " + hole.index + " | par " + hole.hole.par() + " | " + hole.hole.distanceFeet() + "ft"
                            + " | tee(" + hole.hole.tee().x() + ", " + hole.hole.tee().z() + ")"
                            + " -> basket(" + hole.hole.basket().x() + ", " + hole.hole.basket().z() + ")"
            ), false);
            source.sendFeedback(() -> button("REBUILD HOLE " + hole.index, "/mcdg buildcourse rebuild-request " + hole.index, Formatting.RED, true), false);
        }

        source.sendFeedback(() -> Text.literal("Dimensions used: " + String.join(", ", session.dimensionsUsed)), false);
        source.sendFeedback(() -> button("SAVE (ENTER NAME)", "/mcdg buildcourse save-name ", Formatting.GREEN, false), false);
        source.sendFeedback(() -> button("DISCARD", "/mcdg buildcourse discard", Formatting.RED, true), false);
        return 1;
    }

    private int executeSaveName(CommandContext<ServerCommandSource> context, String name) {
        return executeSaveName(context.getSource(), name);
    }

    public int executeSaveName(ServerCommandSource source, String name) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (!isComplete()) {
            source.sendError(Text.literal("Cannot save before all holes are built."));
            return 0;
        }

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            source.sendError(Text.literal("Course name is required."));
            return 0;
        }

        Optional<String> duplicate = findDuplicateName(source.getServer(), trimmed);
        if (duplicate.isPresent()) {
            source.sendError(Text.literal("Duplicate course name: " + duplicate.get()));
            source.sendFeedback(() -> button("AUTO-RENAME", "/mcdg buildcourse save-auto-rename", Formatting.YELLOW, true), false);
            source.sendFeedback(() -> button("ENTER NEW NAME", "/mcdg buildcourse save-name ", Formatting.GRAY, false), false);
            return 0;
        }

        return saveCourse(source, trimmed);
    }

    private int executeSaveAutoRename(CommandContext<ServerCommandSource> context) {
        return executeSaveAutoRename(context.getSource());
    }

    public int executeSaveAutoRename(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (!isComplete()) {
            source.sendError(Text.literal("Cannot save before all holes are built."));
            return 0;
        }

        String base = "Builder Course " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(session.createdAtMs));
        String candidate = base;
        int suffix = 2;
        while (findDuplicateName(source.getServer(), candidate).isPresent()) {
            candidate = base + " (" + suffix + ")";
            suffix++;
        }
        return saveCourse(source, candidate);
    }

    private int executeDiscard(CommandContext<ServerCommandSource> context) {
        return executeDiscard(context.getSource());
    }

    public int executeDiscard(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        rollbackAllBuiltHoles(source.getServer());
        session = null;
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Buildcourse discarded."), false);
        return 1;
    }

    private int executeRebuildRequest(CommandContext<ServerCommandSource> context, int index) {
        return executeRebuildRequest(context.getSource(), index);
    }

    public int executeRebuildRequest(ServerCommandSource source, int index) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (!isComplete()) {
            source.sendError(Text.literal("Rebuild from summary is only available after completion."));
            return 0;
        }
        BuiltHole target = findBuiltHole(index);
        if (target == null) {
            source.sendError(Text.literal("Unknown hole index: " + index));
            return 0;
        }

        long token = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        session.rebuildToken = token;
        session.rebuildTokenExpiresAtMs = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        session.rebuildTokenHoleIndex = index;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());

        source.sendFeedback(() -> Text.literal("Confirm rebuild hole " + index + " (15s)").formatted(Formatting.RED), false);
        source.sendFeedback(() -> button("CONFIRM", "/mcdg buildcourse rebuild-run " + token, Formatting.DARK_RED, true), false);
        source.sendFeedback(() -> button("CANCEL", "/mcdg buildcourse rebuild-cancel", Formatting.GRAY, true), false);
        return 1;
    }

    private int executeRebuildRun(CommandContext<ServerCommandSource> context, long token) {
        return executeRebuildRun(context.getSource(), token);
    }

    public int executeRebuildRun(ServerCommandSource source, long token) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        if (session.rebuildToken != token || System.currentTimeMillis() > session.rebuildTokenExpiresAtMs) {
            source.sendError(Text.literal("Rebuild confirmation expired or invalid."));
            return 0;
        }

        BuiltHole target = findBuiltHole(session.rebuildTokenHoleIndex);
        if (target == null) {
            source.sendError(Text.literal("Selected hole no longer exists."));
            clearRebuildToken();
            save(source.getServer());
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(resolveWorldKey(target.worldId));
        if (world != null) {
            rollbackPlaced(world, target.placedState());
        }
        session.builtHoles.removeIf(h -> h.index == target.index);
        session.rebuildHoleIndex = target.index;
        clearRebuildToken();
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());

        teleportBehindOriginalTee(source, target);
        source.sendFeedback(() -> Text.literal("Hole " + target.index + " is ready to rebuild. Use /mcdg buildcourse build."), false);
        return 1;
    }

    private int executeRebuildCancel(CommandContext<ServerCommandSource> context) {
        return executeRebuildCancel(context.getSource());
    }

    public int executeRebuildCancel(ServerCommandSource source) {
        if (!ensureOwner(source, false)) {
            return 0;
        }
        clearRebuildToken();
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Rebuild confirmation canceled."), false);
        return 1;
    }

    private int executeClaimRequest(CommandContext<ServerCommandSource> context) {
        return executeClaimRequest(context.getSource());
    }

    public int executeClaimRequest(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("claim must be run by a player."));
            return 0;
        }
        if (session == null) {
            source.sendError(Text.literal("No active buildcourse session to claim."));
            return 0;
        }
        if (session.ownerId.equals(player.getUuid())) {
            source.sendError(Text.literal("You already own this buildcourse session."));
            return 0;
        }

        long offlineMs = System.currentTimeMillis() - session.ownerLastSeenAtMs;
        if (offlineMs < CLAIM_TIMEOUT_MS) {
            long remainingSeconds = (CLAIM_TIMEOUT_MS - offlineMs) / 1000L;
            source.sendError(Text.literal("Claim unavailable for " + remainingSeconds + "s."));
            return 0;
        }

        long token = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        session.claimToken = token;
        session.claimTokenExpiresAtMs = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());

        source.sendFeedback(() -> Text.literal("Confirm claim session (15s)").formatted(Formatting.RED), false);
        source.sendFeedback(() -> button("CONFIRM CLAIM", "/mcdg buildcourse claim-run " + token, Formatting.DARK_RED, true), false);
        source.sendFeedback(() -> button("CANCEL", "/mcdg buildcourse claim-cancel", Formatting.GRAY, true), false);
        return 1;
    }

    private int executeClaimRun(CommandContext<ServerCommandSource> context, long token) {
        return executeClaimRun(context.getSource(), token);
    }

    public int executeClaimRun(ServerCommandSource source, long token) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("claim must be run by a player."));
            return 0;
        }
        if (session == null) {
            source.sendError(Text.literal("No active buildcourse session."));
            return 0;
        }
        if (session.claimToken != token || System.currentTimeMillis() > session.claimTokenExpiresAtMs) {
            source.sendError(Text.literal("Claim confirmation expired or invalid."));
            return 0;
        }

        session.ownerId = player.getUuid();
        session.ownerName = player.getName().getString();
        session.ownerLastSeenAtMs = System.currentTimeMillis();
        session.claimToken = -1L;
        session.claimTokenExpiresAtMs = -1L;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());

        BuiltHole target = findBuiltHole(fallbackTargetHoleIndex());
        if (target != null) {
            teleportBehindOriginalTee(source, target);
        }

        source.sendFeedback(() -> Text.literal("Claimed buildcourse session."), false);
        sendStatus(source, true, false);
        return 1;
    }

    private int executeClaimCancel(CommandContext<ServerCommandSource> context) {
        return executeClaimCancel(context.getSource());
    }

    public int executeClaimCancel(ServerCommandSource source) {
        if (session == null) {
            return 1;
        }
        session.claimToken = -1L;
        session.claimTokenExpiresAtMs = -1L;
        session.updatedAtMs = System.currentTimeMillis();
        save(source.getServer());
        source.sendFeedback(() -> Text.literal("Claim confirmation canceled."), false);
        return 1;
    }

    private boolean ensureOwner(ServerCommandSource source, boolean allowNonDestructivePaused) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("buildcourse must be run by a player."));
            return false;
        }
        if (session == null) {
            source.sendError(Text.literal("No active buildcourse session."));
            return false;
        }
        if (!session.ownerId.equals(player.getUuid())) {
            long offlineMs = System.currentTimeMillis() - session.ownerLastSeenAtMs;
            boolean claimReady = offlineMs >= CLAIM_TIMEOUT_MS;
            sendStatus(source, false, claimReady);
            return false;
        }

        session.ownerLastSeenAtMs = System.currentTimeMillis();
        if (session.paused && !allowNonDestructivePaused) {
            source.sendError(Text.literal("Session is paused."));
            source.sendFeedback(() -> button("RESUME", "/mcdg buildcourse resume", Formatting.GREEN, true), false);
            return false;
        }
        return true;
    }

    private boolean isComplete() {
        return session != null && session.holeCount > 0 && session.builtHoles.size() == session.holeCount && session.rebuildHoleIndex <= 0;
    }

    private int saveCourse(ServerCommandSource source, String name) {
        if (session == null) {
            source.sendError(Text.literal("No active buildcourse session."));
            return 0;
        }

        Set<String> dimensions = new HashSet<>();
        for (BuiltHole hole : session.builtHoles) {
            dimensions.add(hole.worldId);
        }
        if (dimensions.size() != 1) {
            source.sendError(Text.literal("Phase 1 save requires one dimension. Rebuild holes to a single dimension before saving."));
            return 0;
        }

        String worldId = dimensions.iterator().next();
        ServerWorld world = source.getServer().getWorld(resolveWorldKey(worldId));
        if (world == null) {
            source.sendError(Text.literal("Target world is unavailable for save: " + worldId));
            return 0;
        }

        List<Hole> holes = new ArrayList<>();
        Map<BlockPos, net.minecraft.block.BlockState> mergedOriginals = new HashMap<>();
        Map<Integer, BlockPos> tees = new HashMap<>();
        Map<Integer, BlockPos> baskets = new HashMap<>();
        Map<Integer, BlockPos> alternates = new HashMap<>();
        Map<Integer, Integer> effectivePars = new HashMap<>();

        for (BuiltHole built : sortedBuiltHoles()) {
            holes.add(built.hole);
            PlacedCourseState placed = built.placedState();
            mergedOriginals.putAll(placed.originalBlocks());
            tees.putAll(placed.holeTees());
            baskets.putAll(placed.holeBaskets());
            alternates.putAll(placed.holeAlternateAnchors());
            effectivePars.putAll(placed.effectiveHolePars());
        }

        long seed = ThreadLocalRandom.current().nextLong();
        Course course = new Course(seed, name, holes);
        PlacedCourseState mergedPlaced = new PlacedCourseState(world.getRegistryKey(), mergedOriginals, tees, baskets, alternates, effectivePars);
        int catalogIndex = practiceCourseStorage.saveReusable(source.getServer(), course, mergedPlaced, "builder/" + session.ownerName, false);

        source.sendFeedback(() -> Text.literal("Saved buildcourse as #" + catalogIndex + " '" + name + "' (inactive)."), false);
        source.sendFeedback(() -> Text.literal("Builder metadata: owner=" + session.ownerName
                + ", createdAt=" + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(session.createdAtMs))
                + ", dimensions=" + String.join(",", session.dimensionsUsed)
        ), false);

        session = null;
        save(source.getServer());
        return 1;
    }

    private void sendBuildStepPrompt(ServerCommandSource source) {
        if (session == null) {
            return;
        }
        int target = currentTargetHoleIndex();
        if (!isActiveHoleIndex(target)) {
            source.sendFeedback(() -> button("FINAL SUMMARY", "/mcdg buildcourse finalize", Formatting.GOLD, true), false);
            return;
        }

        source.sendFeedback(() -> Text.literal("Build hole " + target + " of " + session.holeCount
                + ": stand facing shot direction (tee starts 2 blocks ahead)."), false);
    source.sendFeedback(() -> Text.literal("A live preview should appear in front of you. If not, turn to face the shot and use STATUS or BUILD HOLE " + target + "."), false);
        source.sendFeedback(() -> button("BUILD HOLE " + target, "/mcdg buildcourse build", Formatting.GREEN, true), false);
        source.sendFeedback(() -> button("UNDO LAST", "/mcdg buildcourse undo", Formatting.YELLOW, true), false);
        source.sendFeedback(() -> button("PAUSE", "/mcdg buildcourse pause", Formatting.GRAY, true), false);
        source.sendFeedback(() -> button("STATUS", "/mcdg buildcourse status", Formatting.AQUA, true), false);
    }

    private void sendHolePresetMenu(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Buildcourse hole count (" + MIN_HOLES + "-" + MAX_HOLES + ")"), false);
        source.sendFeedback(() -> button("9", "/mcdg buildcourse holes 9", Formatting.GREEN, true), false);
        source.sendFeedback(() -> button("12", "/mcdg buildcourse holes 12", Formatting.GREEN, true), false);
        source.sendFeedback(() -> button("18", "/mcdg buildcourse holes 18", Formatting.GREEN, true), false);
        source.sendFeedback(() -> button("15", "/mcdg buildcourse holes 15", Formatting.AQUA, true), false);
        source.sendFeedback(() -> button("21", "/mcdg buildcourse holes 21", Formatting.AQUA, true), false);
        source.sendFeedback(() -> button("24", "/mcdg buildcourse holes 24", Formatting.AQUA, true), false);
        source.sendFeedback(() -> button("27", "/mcdg buildcourse holes 27", Formatting.AQUA, true), false);
        source.sendFeedback(() -> button("CUSTOM", "/mcdg buildcourse holes custom", Formatting.YELLOW, true), false);
    }

    private void sendStatus(ServerCommandSource source, boolean ownerView, boolean claimReady) {
        if (session == null) {
            source.sendFeedback(() -> Text.literal("No active buildcourse session."), false);
            return;
        }

        source.sendFeedback(() -> Text.literal("Buildcourse session").formatted(Formatting.GOLD, Formatting.BOLD), false);
        source.sendFeedback(() -> Text.literal("Owner: " + session.ownerName), false);
        source.sendFeedback(() -> Text.literal("Paused: " + session.paused), false);
        source.sendFeedback(() -> Text.literal("Progress: " + session.builtHoles.size() + "/" + Math.max(session.holeCount, 0)
                + " | next=" + session.nextHoleIndex + (session.rebuildHoleIndex > 0 ? " | rebuild=" + session.rebuildHoleIndex : "")), false);
        source.sendFeedback(() -> Text.literal("Created: " + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(session.createdAtMs))), false);
        source.sendFeedback(() -> Text.literal("Updated: " + DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(session.updatedAtMs))), false);
        source.sendFeedback(() -> Text.literal("Dimensions: " + String.join(", ", session.dimensionsUsed)), false);

        if (ownerView) {
            if (!isComplete()) {
                source.sendFeedback(() -> button("BUILD", "/mcdg buildcourse build", Formatting.GREEN, true), false);
            }
            if (isComplete()) {
                source.sendFeedback(() -> button("FINALIZE", "/mcdg buildcourse finalize", Formatting.GOLD, true), false);
            }
            source.sendFeedback(() -> button("PAUSE", "/mcdg buildcourse pause", Formatting.GRAY, true), false);
            source.sendFeedback(() -> button("CANCEL", "/mcdg buildcourse cancel", Formatting.RED, true), false);
        } else if (claimReady) {
            source.sendFeedback(() -> button("CLAIM SESSION", "/mcdg buildcourse claim", Formatting.RED, true), false);
        }
    }

    private Optional<String> findDuplicateName(MinecraftServer server, String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (PracticeCourseStorage.ReusableCourseEntry entry : practiceCourseStorage.listReusable(server)) {
            if (entry.name() != null && entry.name().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(entry.name());
            }
        }
        return Optional.empty();
    }

    private PreviewSpec computePreview(ServerPlayerEntity player, int holeIndex) {
        // Use the raw yaw (not snapped to 4 cardinals) to get an 8-directional unit vector.
        float yawRad = (float) Math.toRadians(player.getYaw());
        int dx = (int) Math.round(-Math.sin(yawRad));
        int dz = (int) Math.round(Math.cos(yawRad));
        if (dx == 0 && dz == 0) { dz = 1; } // guard against floating-point edge case
        BlockPos feet = player.getBlockPos();
        BlockPos teeAnchor = feet.add(dx * 2, 0, dz * 2);

        long seed = (((long) teeAnchor.getX()) << 32) ^ (teeAnchor.getZ() * 341873128712L) ^ (holeIndex * 73428767L);
        java.util.Random random = new java.util.Random(seed);

        int distanceFeet = MIN_DISTANCE_FEET + random.nextInt((MAX_DISTANCE_FEET - MIN_DISTANCE_FEET) + 1);
        int distanceBlocks = Math.max(1, Math.round(distanceFeet / 3.0f));
        int fairwayWidth = MIN_FAIRWAY_WIDTH + random.nextInt((MAX_FAIRWAY_WIDTH - MIN_FAIRWAY_WIDTH) + 1);
        int basketHeight = 1 + random.nextInt(2);

        BlockPos basketAnchor = teeAnchor.add(dx * distanceBlocks, 0, dz * distanceBlocks);
        int par = computePar(distanceFeet);

        return new PreviewSpec(seed, holeIndex, teeAnchor, basketAnchor, feet, distanceFeet, par, fairwayWidth, basketHeight);
    }

    private static int computePar(int distanceFeet) {
        if (distanceFeet <= PAR3_MAX_FEET) {
            return 3;
        }
        if (distanceFeet <= PAR4_MAX_FEET) {
            return 4;
        }
        return 5;
    }

    private void spawnMarker(ServerWorld world, ServerPlayerEntity player, BlockPos pos, double r, double g, double b) {
        world.spawnParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                6, 0.35, 0.35, 0.35, 0.0);
    }

    private void spawnPath(ServerWorld world, ServerPlayerEntity player, BlockPos from, BlockPos to, double r, double g, double b) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        int steps = Math.max(6, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 6.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = from.getX() + 0.5 + (dx * t);
            double y = from.getY() + 1.0 + (dy * t);
            double z = from.getZ() + 0.5 + (dz * t);
            world.spawnParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, 0, 0, 0, 0.0);
        }
    }

    private void upsertBuiltHole(BuiltHole builtHole) {
        session.builtHoles.removeIf(h -> h.index == builtHole.index);
        session.builtHoles.add(builtHole);
        session.builtHoles.sort(Comparator.comparingInt(h -> h.index));
    }

    private BuiltHole findBuiltHole(int index) {
        for (BuiltHole hole : session.builtHoles) {
            if (hole.index == index) {
                return hole;
            }
        }
        return null;
    }

    private List<BuiltHole> sortedBuiltHoles() {
        List<BuiltHole> copy = new ArrayList<>(session.builtHoles);
        copy.sort(Comparator.comparingInt(h -> h.index));
        return copy;
    }

    private void clearRebuildToken() {
        session.rebuildToken = -1L;
        session.rebuildTokenExpiresAtMs = -1L;
        session.rebuildTokenHoleIndex = -1;
    }

    private void rollbackPlaced(ServerWorld world, PlacedCourseState placed) {
        for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : placed.originalBlocks().entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue(), Block.NOTIFY_ALL);
        }
    }

    private int currentTargetHoleIndex() {
        return session.rebuildHoleIndex > 0 ? session.rebuildHoleIndex : session.nextHoleIndex;
    }

    private int fallbackTargetHoleIndex() {
        return isActiveHoleIndex(currentTargetHoleIndex()) ? currentTargetHoleIndex() : firstHoleIndex();
    }

    private boolean isActiveHoleIndex(int holeIndex) {
        return session != null && holeIndex >= firstHoleIndex() && holeIndex <= session.holeCount;
    }

    private int firstHoleIndex() {
        return 1;
    }

    private void rollbackAllBuiltHoles(MinecraftServer server) {
        if (session == null) {
            return;
        }
        for (BuiltHole hole : session.builtHoles) {
            ServerWorld world = server.getWorld(resolveWorldKey(hole.worldId));
            if (world != null) {
                rollbackPlaced(world, hole.placedState());
            }
        }
    }

    private void teleportBehindOriginalTee(ServerCommandSource source, BuiltHole hole) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return;
        }

        RegistryKey<World> key = resolveWorldKey(hole.worldId);
        ServerWorld world = source.getServer().getWorld(key);
        if (world == null) {
            return;
        }

        int dx = hole.hole.basket().x() - hole.hole.tee().x();
        int dz = hole.hole.basket().z() - hole.hole.tee().z();
        BlockPos desired;
        if (Math.abs(dx) >= Math.abs(dz)) {
            desired = hole.originalTeeAnchor.add(-Integer.compare(dx, 0) * 2, 0, 0);
        } else {
            desired = hole.originalTeeAnchor.add(0, 0, -Integer.compare(dz, 0) * 2);
        }
        BlockPos safe = resolveSafeFeetNear(world, desired);
        player.teleport(world, safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5, player.getYaw(), player.getPitch());
    }

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos((a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
    }

    private static BlockPos resolveSafeFeetNear(ServerWorld world, BlockPos anchor) {
        world.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        BlockPos candidate = anchor.withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ()));
        if (isStandableFeet(world, candidate)) {
            return candidate;
        }

        int[] deltas = {1, -1, 2, -2, 3, -3, 4, -4};
        for (int dx : deltas) {
            for (int dz : deltas) {
                int x = anchor.getX() + dx;
                int z = anchor.getZ() + dz;
                world.getChunk(x >> 4, z >> 4);
                BlockPos probe = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (isStandableFeet(world, probe)) {
                    return probe;
                }
            }
        }

        return candidate;
    }

    private static boolean isStandableFeet(ServerWorld world, BlockPos feet) {
        BlockPos below = feet.down();
        if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
            return false;
        }
        BlockPos head = feet.up();
        if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
            return false;
        }
        return !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
    }

    private Path resolvePath(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(FILE_NAME);
    }

    private Text button(String label, String command, Formatting color, boolean runNow) {
        ClickEvent.Action action = runNow ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        return Text.literal("[" + label + "]").styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal((runNow ? "Run: " : "Fill chat: ") + command)))
        );
    }

    private static final class PreviewSpec {
        private final long seed;
        private final int holeIndex;
        private final BlockPos teeAnchor;
        private final BlockPos basketAnchor;
        private final BlockPos originalTeeAnchor;
        private final int distanceFeet;
        private final int par;
        private final int fairwayWidth;
        private final int basketHeight;

        private PreviewSpec(long seed, int holeIndex, BlockPos teeAnchor, BlockPos basketAnchor, BlockPos originalTeeAnchor,
                            int distanceFeet, int par, int fairwayWidth, int basketHeight) {
            this.seed = seed;
            this.holeIndex = holeIndex;
            this.teeAnchor = teeAnchor;
            this.basketAnchor = basketAnchor;
            this.originalTeeAnchor = originalTeeAnchor;
            this.distanceFeet = distanceFeet;
            this.par = par;
            this.fairwayWidth = fairwayWidth;
            this.basketHeight = basketHeight;
        }
    }

    private static final class BuildSession {
        private UUID ownerId;
        private String ownerName;
        private long createdAtMs;
        private long updatedAtMs;
        private long ownerLastSeenAtMs;
        private boolean paused;
        private int holeCount;
        private int nextHoleIndex;
        private int rebuildHoleIndex;
        private long rebuildToken;
        private long rebuildTokenExpiresAtMs;
        private int rebuildTokenHoleIndex;
        private long claimToken;
        private long claimTokenExpiresAtMs;
        private int previewTickGate;
        private final List<BuiltHole> builtHoles;
        private final Set<String> dimensionsUsed;

        private BuildSession() {
            this.builtHoles = new ArrayList<>();
            this.dimensionsUsed = new HashSet<>();
            this.nextHoleIndex = 1;
            this.rebuildHoleIndex = -1;
            this.rebuildToken = -1L;
            this.rebuildTokenExpiresAtMs = -1L;
            this.rebuildTokenHoleIndex = -1;
            this.claimToken = -1L;
            this.claimTokenExpiresAtMs = -1L;
        }

        private static BuildSession newSession(ServerPlayerEntity owner) {
            BuildSession session = new BuildSession();
            long now = System.currentTimeMillis();
            session.ownerId = owner.getUuid();
            session.ownerName = owner.getName().getString();
            session.createdAtMs = now;
            session.updatedAtMs = now;
            session.ownerLastSeenAtMs = now;
            return session;
        }
    }

    private record BuiltHole(
            int index,
            Hole hole,
            PlacedCourseStateSnapshot placed,
            String worldId,
            long holeSeed,
            BlockPos originalTeeAnchor
    ) {
        private static BuiltHole from(Hole hole, PlacedCourseState placed, String worldId, long holeSeed, BlockPos originalTeeAnchor) {
            return new BuiltHole(
                    hole.index(),
                    hole,
                    PlacedCourseStateSnapshot.from(placed),
                    worldId,
                    holeSeed,
                    originalTeeAnchor.toImmutable()
            );
        }

        private PlacedCourseState placedState() {
            return placed.toPlacedCourseState(worldId);
        }
    }

    private static final class BuildSessionSnapshot {
        private String ownerId;
        private String ownerName;
        private long createdAtMs;
        private long updatedAtMs;
        private long ownerLastSeenAtMs;
        private boolean paused;
        private int holeCount;
        private int nextHoleIndex;
        private int rebuildHoleIndex;
        private long rebuildToken;
        private long rebuildTokenExpiresAtMs;
        private int rebuildTokenHoleIndex;
        private long claimToken;
        private long claimTokenExpiresAtMs;
        private List<BuiltHoleSnapshot> builtHoles;
        private Set<String> dimensionsUsed;

        private static BuildSessionSnapshot from(BuildSession session) {
            BuildSessionSnapshot snapshot = new BuildSessionSnapshot();
            snapshot.ownerId = session.ownerId.toString();
            snapshot.ownerName = session.ownerName;
            snapshot.createdAtMs = session.createdAtMs;
            snapshot.updatedAtMs = session.updatedAtMs;
            snapshot.ownerLastSeenAtMs = session.ownerLastSeenAtMs;
            snapshot.paused = session.paused;
            snapshot.holeCount = session.holeCount;
            snapshot.nextHoleIndex = session.nextHoleIndex;
            snapshot.rebuildHoleIndex = session.rebuildHoleIndex;
            snapshot.rebuildToken = session.rebuildToken;
            snapshot.rebuildTokenExpiresAtMs = session.rebuildTokenExpiresAtMs;
            snapshot.rebuildTokenHoleIndex = session.rebuildTokenHoleIndex;
            snapshot.claimToken = session.claimToken;
            snapshot.claimTokenExpiresAtMs = session.claimTokenExpiresAtMs;
            snapshot.builtHoles = new ArrayList<>();
            for (BuiltHole hole : session.builtHoles) {
                snapshot.builtHoles.add(BuiltHoleSnapshot.from(hole));
            }
            snapshot.dimensionsUsed = new HashSet<>(session.dimensionsUsed);
            return snapshot;
        }

        private BuildSession toSession() {
            BuildSession restored = new BuildSession();
            restored.ownerId = UUID.fromString(ownerId);
            restored.ownerName = ownerName;
            restored.createdAtMs = createdAtMs;
            restored.updatedAtMs = updatedAtMs;
            restored.ownerLastSeenAtMs = ownerLastSeenAtMs;
            restored.paused = paused;
            restored.holeCount = holeCount;
            restored.nextHoleIndex = nextHoleIndex;
            restored.rebuildHoleIndex = rebuildHoleIndex;
            restored.rebuildToken = rebuildToken;
            restored.rebuildTokenExpiresAtMs = rebuildTokenExpiresAtMs;
            restored.rebuildTokenHoleIndex = rebuildTokenHoleIndex;
            restored.claimToken = claimToken;
            restored.claimTokenExpiresAtMs = claimTokenExpiresAtMs;
            if (builtHoles != null) {
                for (BuiltHoleSnapshot hole : builtHoles) {
                    restored.builtHoles.add(hole.toBuiltHole());
                }
            }
            if (dimensionsUsed != null) {
                restored.dimensionsUsed.addAll(dimensionsUsed);
            }
            return restored;
        }
    }

    private static final class BuiltHoleSnapshot {
        private int index;
        private Hole hole;
        private PlacedCourseStateSnapshot placed;
        private String worldId;
        private long holeSeed;
        private BlockPosSnapshot originalTeeAnchor;

        private static BuiltHoleSnapshot from(BuiltHole hole) {
            BuiltHoleSnapshot snapshot = new BuiltHoleSnapshot();
            snapshot.index = hole.index;
            snapshot.hole = hole.hole;
            snapshot.placed = hole.placed;
            snapshot.worldId = hole.worldId;
            snapshot.holeSeed = hole.holeSeed;
            snapshot.originalTeeAnchor = BlockPosSnapshot.of(hole.originalTeeAnchor);
            return snapshot;
        }

        private BuiltHole toBuiltHole() {
            return new BuiltHole(index, hole, placed, worldId, holeSeed, originalTeeAnchor.toBlockPos());
        }
    }

    private static final class PlacedCourseStateSnapshot {
        private String worldId;
        private List<BlockStateSnapshot> originals;
        private Map<Integer, BlockPosSnapshot> tees;
        private Map<Integer, BlockPosSnapshot> baskets;
        private Map<Integer, BlockPosSnapshot> alternates;
        private Map<Integer, Integer> effectivePars;

        private static PlacedCourseStateSnapshot from(PlacedCourseState placed) {
            PlacedCourseStateSnapshot snapshot = new PlacedCourseStateSnapshot();
            snapshot.worldId = placed.worldKey().getValue().toString();
            snapshot.originals = new ArrayList<>();
            for (Map.Entry<BlockPos, net.minecraft.block.BlockState> entry : placed.originalBlocks().entrySet()) {
                snapshot.originals.add(new BlockStateSnapshot(BlockPosSnapshot.of(entry.getKey()), net.minecraft.block.Block.getRawIdFromState(entry.getValue())));
            }
            snapshot.tees = toPosSnapshotMap(placed.holeTees());
            snapshot.baskets = toPosSnapshotMap(placed.holeBaskets());
            snapshot.alternates = toPosSnapshotMap(placed.holeAlternateAnchors());
            snapshot.effectivePars = new HashMap<>(placed.effectiveHolePars());
            return snapshot;
        }

        private static Map<Integer, BlockPosSnapshot> toPosSnapshotMap(Map<Integer, BlockPos> source) {
            Map<Integer, BlockPosSnapshot> map = new HashMap<>();
            for (Map.Entry<Integer, BlockPos> entry : source.entrySet()) {
                map.put(entry.getKey(), BlockPosSnapshot.of(entry.getValue()));
            }
            return map;
        }

        private PlacedCourseState toPlacedCourseState(String fallbackWorldId) {
            String resolvedWorld = worldId == null || worldId.isBlank() ? fallbackWorldId : worldId;
            Map<BlockPos, net.minecraft.block.BlockState> originalsMap = new HashMap<>();
            if (originals != null) {
                for (BlockStateSnapshot snapshot : originals) {
                    originalsMap.put(snapshot.pos.toBlockPos(), Block.getStateFromRawId(snapshot.stateId));
                }
            }

            Map<Integer, BlockPos> teesMap = toPosMap(tees);
            Map<Integer, BlockPos> basketsMap = toPosMap(baskets);
            Map<Integer, BlockPos> alternatesMap = toPosMap(alternates);
            Map<Integer, Integer> effectiveParsMap = effectivePars == null ? Map.of() : new HashMap<>(effectivePars);

            RegistryKey<World> key = resolveWorldKey(resolvedWorld);
            return new PlacedCourseState(key, originalsMap, teesMap, basketsMap, alternatesMap, effectiveParsMap);
        }

        private static Map<Integer, BlockPos> toPosMap(Map<Integer, BlockPosSnapshot> source) {
            Map<Integer, BlockPos> map = new HashMap<>();
            if (source == null) {
                return map;
            }
            for (Map.Entry<Integer, BlockPosSnapshot> entry : source.entrySet()) {
                map.put(entry.getKey(), entry.getValue().toBlockPos());
            }
            return map;
        }
    }

    private record BlockStateSnapshot(BlockPosSnapshot pos, int stateId) {
    }

    private static final class BlockPosSnapshot {
        private int x;
        private int y;
        private int z;

        private static BlockPosSnapshot of(BlockPos pos) {
            BlockPosSnapshot snapshot = new BlockPosSnapshot();
            snapshot.x = pos.getX();
            snapshot.y = pos.getY();
            snapshot.z = pos.getZ();
            return snapshot;
        }

        private BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    private static RegistryKey<World> resolveWorldKey(String worldId) {
        String normalized = worldId == null || worldId.isBlank() ? "minecraft:overworld" : worldId.trim();
        int colon = normalized.indexOf(':');
        if (colon < 0) {
            return RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of("minecraft", normalized));
        }
        return RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, Identifier.of(normalized.substring(0, colon), normalized.substring(colon + 1)));
    }
}
