package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.world.SafePositionFinder;
import com.mcdg.data.Course;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class ThrowAutoTestService {
    private static final String THROW_AUTOTEST_COUNT_ENV = "MCDG_THROW_AUTOTEST_COUNT";
    private static final String THROW_AUTOTEST_PLAYER_ENV = "MCDG_THROW_AUTOTEST_PLAYER";
    private static final String THROW_AUTOTEST_SHUTDOWN_ENV = "MCDG_THROW_AUTOTEST_SHUTDOWN";
    private static final int MAX_CONSECUTIVE_UNCHANGED_LIE_THROWS = 3;
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final ActiveCourseManager courseManager;
    private final RoundStateManager roundStateManager;
    private AutoThrowSession activeSession;
    private Integer autoThrowCount;
    private String autoPlayerName;
    private boolean autoShutdown;
    private boolean autoConfigRead;
    private boolean autoSessionPending;

    public ThrowAutoTestService(ActiveCourseManager courseManager, RoundStateManager roundStateManager) {
        this.courseManager = courseManager;
        this.roundStateManager = roundStateManager;
    }

    public int start(ServerCommandSource source, int throwsToRun) {
        if (activeSession != null) {
            source.sendError(Text.literal("Throw autotest already running. Use /mcdg cancelthrowtest first."));
            return 0;
        }

        if (!courseManager.isRoundActive()) {
            source.sendError(Text.literal("No active round. Start or resume a round first."));
            return 0;
        }

        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception ex) {
            source.sendError(Text.literal("Run this command as a player."));
            return 0;
        }

        PlayerRoundState state = roundStateManager.getState(player.getUuid()).orElse(null);
        if (state == null) {
            source.sendError(Text.literal("No round state found for this player."));
            return 0;
        }

        activeSession = new AutoThrowSession(
            source,
            player.getUuid(),
            Math.max(1, throwsToRun),
            state.totalStrokes(),
            state.lie(),
            createBossBar("Throw autotest: starting...", source.getServer(), player)
        );
        LieMarkerService.beginAutotestLieMarkerTrail();
        updateBossBar(activeSession, 0, false, state.currentHole(), state.lie(), null);
        source.sendFeedback(() -> Text.literal("Throw autotest started for " + throwsToRun + " throws."), true);
        return 1;
    }

    public int cancel(ServerCommandSource source) {
        if (activeSession == null) {
            source.sendError(Text.literal("No throw autotest is running."));
            return 0;
        }

        finishSession("Throw autotest canceled.", true);
        return 1;
    }

    public void tick(MinecraftServer server) {
        if (!autoConfigRead) {
            loadAutoConfig();
        }

        if (activeSession == null && autoSessionPending && autoThrowCount != null && autoThrowCount > 0) {
            maybeStartAutoSession(server);
        }

        if (activeSession == null) {
            return;
        }
        if (!server.isOnThread()) {
            return;
        }

        AutoThrowSession session = activeSession;
        if (!courseManager.isRoundActive()) {
            finishSession("Throw autotest ended: round is no longer active.", false);
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerId);
        if (player == null) {
            finishSession("Throw autotest ended: player went offline.", false);
            return;
        }

        keepPlayerAlive(player);
        clearNearbyHostiles((ServerWorld) player.getWorld(), player);

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        Course course = courseManager.getActiveCourse().orElse(null);
        if (placed == null || course == null) {
            finishSession("Throw autotest ended: active course state unavailable.", false);
            return;
        }

        ServerWorld world = server.getWorld(placed.worldKey());
        if (world == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
            finishSession("Throw autotest ended: player is not in active course world.", false);
            return;
        }

        PlayerRoundState state = roundStateManager.getState(session.playerId).orElse(null);
        if (state == null) {
            finishSession("Throw autotest ended: player round state cleared.", false);
            return;
        }

        boolean resolutionPending = ThrowResolver.isThrowResolutionPending(session.playerId, state.totalStrokes());
        if (state.totalStrokes() > session.lastObservedTotalStrokes) {
            session.lastObservedTotalStrokes = state.totalStrokes();
        }

        if (session.waitingForLieResolution) {
            if (!resolutionPending) {
                boolean meaningful = hasMeaningfulLieResolution(
                        session.lastThrowLie,
                        state.lie(),
                        session.lastThrowHole,
                        state.currentHole()
                );
                session.resolvedThrows++;
                if (!meaningful) {
                    session.resolvedUnchangedLieThrows++;
                    session.consecutiveUnchangedLieThrows++;
                    int horizontalDelta = horizontalDistance(session.lastThrowLie, state.lie());
                    String failure =
                            "failed throw=" + session.throwsLaunched
                            + " outcome=NON_MEANINGFUL_LIE from=" + formatPos(session.lastThrowLie)
                            + " to=" + formatPos(state.lie())
                            + " horizontalDelta=" + horizontalDelta
                                    + " hole=" + state.currentHole();
                    session.logLines.add(failure);
                    if (session.consecutiveUnchangedLieThrows >= MAX_CONSECUTIVE_UNCHANGED_LIE_THROWS) {
                    finishSession(
                        "Throw autotest failed: lie did not move meaningfully after throw "
                            + session.throwsLaunched
                            + " (hole " + state.currentHole() + ")",
                        false
                    );
                    return;
                    }
                    session.logLines.add(
                        "warning: tolerated unchanged-lie resolution at throw "
                            + session.throwsLaunched
                            + " (streak=" + session.consecutiveUnchangedLieThrows + ")"
                    );
                } else {
                    session.consecutiveUnchangedLieThrows = 0;
                }
                session.logLines.add(
                        "resolved throw=" + session.throwsLaunched
                                + " outcome=LIE_UPDATED from=" + formatPos(session.lastThrowLie)
                                + " to=" + formatPos(state.lie())
                                + " hole=" + state.currentHole()
                );
                session.waitingForLieResolution = false;
                session.waitTicksAfterThrow = 0;
            } else {
                session.waitTicksAfterThrow++;
            }

            if (session.waitTicksAfterThrow > 80) {
                String diagnostic = buildStuckLieDiagnostic(world, player, session, state);
                session.suspectUnchangedLieEvents++;
                McdgMod.LOGGER.warn(
                        "Throw autotest suspect event | player={} throw={} unresolved lie at {} {} {} (start {} {} {}) | {}",
                        player.getGameProfile().getName(),
                        session.throwsLaunched,
                        state.lie().getX(),
                        state.lie().getY(),
                        state.lie().getZ(),
                        session.lastThrowLie.getX(),
                        session.lastThrowLie.getY(),
                        session.lastThrowLie.getZ(),
                        diagnostic
                );
                session.waitingForLieResolution = false;
                session.waitTicksAfterThrow = 0;
                session.logLines.add("warning: lie did not resolve within 80 ticks after throw " + session.throwsLaunched);
                session.logLines.add("diagnostic: " + diagnostic);
                finishSession(
                        "Throw autotest failed: unresolved lie after throw " + session.throwsLaunched,
                        false
                );
                return;
            } else {
                updateBossBar(session, session.throwsLaunched, true, state.currentHole(), state.lie(), null);
                return;
            }
        }

        if (session.throwsLaunched >= session.targetThrows && !hasInFlightPearlForPlayer(world, player)) {
            updateBossBar(session, session.targetThrows, false, state.currentHole(), state.lie(), null);
            boolean hasAnomalies = session.suspectUnchangedLieEvents > 0;
            if (hasAnomalies) {
                finishSession(
                        "Throw autotest failed: unresolved/unchanged lie anomalies detected.",
                        false
                );
            } else {
                finishSession("Throw autotest complete.", true);
            }
            return;
        }

        if (session.throwsLaunched >= session.targetThrows || hasInFlightPearlForPlayer(world, player)) {
            updateBossBar(session, session.throwsLaunched, hasInFlightPearlForPlayer(world, player), state.currentHole(), state.lie(), null);
            return;
        }

        BlockPos basket = placed.holeBaskets().get(state.currentHole());
        if (basket == null) {
            finishSession("Throw autotest ended: basket position unavailable.", false);
            return;
        }

        BlockPos lie = state.lie();
        BlockPos safeFeet = resolveClearLaunchFeetNear(world, lie, basket);
        player.teleport(safeFeet.getX() + 0.5, safeFeet.getY(), safeFeet.getZ() + 0.5);

        Vec3d from = new Vec3d(player.getX(), player.getEyeY(), player.getZ());
        Vec3d to = new Vec3d(basket.getX() + 0.5, basket.getY() + 1.2, basket.getZ() + 0.5);
        Vec3d direction = to.subtract(from);
        if (direction.lengthSquared() < 1.0e-4) {
            direction = new Vec3d(0.0, 0.2, 0.0);
        }

        double horizontal = Math.sqrt((direction.x * direction.x) + (direction.z * direction.z));
        double speed = horizontal > 45.0 ? 2.0 : 1.5;
        Vec3d normalized = direction.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-normalized.x, normalized.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(direction.y, horizontal));

        if (session.consecutiveUnchangedLieThrows > 0) {
            int streak = Math.min(3, session.consecutiveUnchangedLieThrows);
            float yawOffset = (session.throwsLaunched % 2 == 0 ? 1.0f : -1.0f) * (6.0f * streak);
            float loftAdjustment = 4.0f * streak;
            yaw += yawOffset;
            pitch = clampPitch(pitch - loftAdjustment);
            speed = Math.min(2.0, speed + (0.1 * streak));
        }

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        player.setVelocity(Vec3d.ZERO);

        EnderPearlEntity pearl = new EnderPearlEntity(world, player);
        pearl.setItem(new ItemStack(Items.ENDER_PEARL));
        pearl.setVelocity(player, pitch, yaw, 0.0f, (float) speed, 0.0f);
        world.spawnEntity(pearl);
        ThrowResolver.registerThrowRelease(player.getUuid(), pearl.getUuid(), world.getTime());

        roundStateManager.recordThrow(session.playerId, lie);
        session.lastThrowLie = lie;
        session.lastThrowHole = state.currentHole();
        session.lastLaunchPlayerFeet = player.getBlockPos();
        session.lastLaunchSafeFeet = safeFeet;
        session.lastLaunchBasket = basket.toImmutable();
        session.lastLaunchFrom = from;
        session.lastLaunchTo = to;
        session.waitingForLieResolution = true;
        session.waitTicksAfterThrow = 0;
        session.logLines.add(
            "launch throw=" + session.throwsLaunched
                + " hole=" + state.currentHole()
                + " lie=" + formatPos(lie)
                + " basket=" + formatPos(basket)
                + " yaw=" + String.format("%.2f", yaw)
                + " pitch=" + String.format("%.2f", pitch)
                + " speed=" + String.format("%.2f", speed)
                + " unchangedStreak=" + session.consecutiveUnchangedLieThrows
        );
        session.throwsLaunched++;
        updateBossBar(session, session.throwsLaunched, true, state.currentHole(), lie, basket);

        McdgMod.LOGGER.info(
                "Throw autotest launch | player={} launched={}/{} hole={} lie={} {} {} basket={} {} {} speed={}",
                player.getGameProfile().getName(),
                session.throwsLaunched,
                session.targetThrows,
                state.currentHole(),
                lie.getX(),
                lie.getY(),
                lie.getZ(),
                basket.getX(),
                basket.getY(),
                basket.getZ(),
                String.format("%.2f", speed)
        );
    }

    private void finishSession(String message, boolean expectedCompletion) {
        AutoThrowSession session = activeSession;
        if (session != null) {
            int launched = session.throwsLaunched;
            int suspected = session.suspectUnchangedLieEvents;
            session.source.sendFeedback(() -> Text.literal(message + " Launched=" + launched + "/" + session.targetThrows
                    + ", suspectUnchangedLieEvents=" + suspected), true);

            writeReport(session, message, expectedCompletion);
            removeBossBar(session);

            if (!expectedCompletion) {
                McdgMod.LOGGER.warn(
                        "Throw autotest ended early | launched={}/{} suspectUnchangedLieEvents={}",
                        launched,
                        session.targetThrows,
                        suspected
                );
            }

            if (autoShutdown) {
                session.source.getServer().stop(false);
            }

            LieMarkerService.endAutotestLieMarkerTrail(session.source.getServer());
        }
        activeSession = null;
    }

    private static ServerBossBar createBossBar(String title, MinecraftServer server, ServerPlayerEntity focusPlayer) {
        ServerBossBar bossBar = new ServerBossBar(Text.literal(title), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey().equals(focusPlayer.getWorld().getRegistryKey())) {
                bossBar.addPlayer(player);
            }
        }
        bossBar.setPercent(0.0f);
        return bossBar;
    }

    private static void updateBossBar(
            AutoThrowSession session,
            int launched,
            boolean waiting,
            int hole,
            BlockPos lie,
            BlockPos basket
    ) {
        if (session.progressBar == null) {
            return;
        }

        float progress = Math.min(1.0f, launched / (float) Math.max(1, session.targetThrows));
        session.progressBar.setPercent(progress);

        String state = waiting ? "waiting for landing" : "ready";
        String basketText = basket == null ? "basket: -" : "basket: " + formatPos(basket);
        session.progressBar.setName(Text.literal(
                "Throw autotest " + launched + "/" + session.targetThrows
                        + " | hole " + hole
                        + " | lie " + formatPos(lie)
                        + " | " + state
                        + " | " + basketText
        ));
    }

    private static void removeBossBar(AutoThrowSession session) {
        if (session.progressBar == null) {
            return;
        }

        for (ServerPlayerEntity player : session.source.getServer().getPlayerManager().getPlayerList()) {
            session.progressBar.removePlayer(player);
        }
    }

    private void loadAutoConfig() {
        autoConfigRead = true;
        String countRaw = System.getenv(THROW_AUTOTEST_COUNT_ENV);
        if (countRaw == null || countRaw.isBlank()) {
            return;
        }

        try {
            int parsed = Integer.parseInt(countRaw.trim());
            autoThrowCount = Math.max(1, Math.min(400, parsed));
            autoSessionPending = true;
        } catch (NumberFormatException ex) {
            McdgMod.LOGGER.warn("Ignoring invalid {} value '{}'", THROW_AUTOTEST_COUNT_ENV, countRaw);
            return;
        }

        String playerRaw = System.getenv(THROW_AUTOTEST_PLAYER_ENV);
        if (playerRaw != null && !playerRaw.isBlank()) {
            autoPlayerName = playerRaw.trim();
        }

        String shutdownRaw = System.getenv(THROW_AUTOTEST_SHUTDOWN_ENV);
        autoShutdown = shutdownRaw != null && (
                shutdownRaw.equalsIgnoreCase("1")
                        || shutdownRaw.equalsIgnoreCase("true")
                        || shutdownRaw.equalsIgnoreCase("yes")
                        || shutdownRaw.equalsIgnoreCase("on")
        );

        McdgMod.LOGGER.info(
                "Throw autotest auto mode enabled: count={}, player={}, shutdown={}",
                autoThrowCount,
                autoPlayerName == null ? "<first-online>" : autoPlayerName,
                autoShutdown
        );
    }

    private void maybeStartAutoSession(MinecraftServer server) {
        if (!courseManager.isRoundActive()) {
            return;
        }

        ServerPlayerEntity player = resolveAutoPlayer(server);
        if (player == null) {
            return;
        }

        PlayerRoundState state = roundStateManager.getState(player.getUuid()).orElse(null);
        if (state == null) {
            return;
        }

        activeSession = new AutoThrowSession(
                server.getCommandSource(),
                player.getUuid(),
                autoThrowCount,
                state.totalStrokes(),
            state.lie(),
            createBossBar("Throw autotest: waiting for auto start...", server, player)
        );
        LieMarkerService.beginAutotestLieMarkerTrail();
        updateBossBar(activeSession, 0, false, state.currentHole(), state.lie(), null);

        server.getCommandSource().sendFeedback(
                () -> Text.literal("Auto throw autotest started for player " + player.getGameProfile().getName()
                        + " with " + autoThrowCount + " throws."),
                true
        );

        McdgMod.LOGGER.info(
                "Auto throw autotest session started: player={} throws={}",
                player.getGameProfile().getName(),
                autoThrowCount
        );
        autoSessionPending = false;
    }

    private ServerPlayerEntity resolveAutoPlayer(MinecraftServer server) {
        if (server.getPlayerManager().getPlayerList().isEmpty()) {
            return null;
        }

        if (autoPlayerName == null) {
            return server.getPlayerManager().getPlayerList().get(0);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(autoPlayerName)) {
                return player;
            }
        }

        return null;
    }

    private void writeReport(AutoThrowSession session, String statusMessage, boolean expectedCompletion) {
        try {
            Path runDir = session.source.getServer().getRunDirectory().toPath();
            Path logsDir = runDir.resolve("logs");
            Files.createDirectories(logsDir);

            String stamp = LocalDateTime.now().format(REPORT_TIME);
            Path timestamped = logsDir.resolve("mcdg-throw-autotest-" + stamp + ".txt");
            Path latest = logsDir.resolve("mcdg-throw-autotest-latest.txt");

            List<String> lines = new ArrayList<>();
            lines.add("MCDG Throw Autotest Report");
            lines.add("Status: " + statusMessage);
            lines.add("Expected completion: " + expectedCompletion);
            lines.add("Target throws: " + session.targetThrows);
            lines.add("Launched throws: " + session.throwsLaunched);
            lines.add("Resolved throws: " + session.resolvedThrows);
            lines.add("Resolved unchanged-lie throws: " + session.resolvedUnchangedLieThrows);
            lines.add("Suspect unchanged-lie events: " + session.suspectUnchangedLieEvents);
            lines.add("Events:");
            if (session.logLines.isEmpty()) {
                lines.add(" - none");
            } else {
                for (String line : session.logLines) {
                    lines.add(" - " + line);
                }
            }

            Files.write(timestamped, lines, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.write(latest, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            McdgMod.LOGGER.error("Failed to write throw autotest report", ex);
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean hasMeaningfulLieResolution(
            BlockPos throwLie,
            BlockPos currentLie,
            int throwHole,
            int currentHole
    ) {
        if (currentHole != throwHole) {
            return true;
        }

        return horizontalDistance(throwLie, currentLie) > 0;
    }

    private static int horizontalDistance(BlockPos from, BlockPos to) {
        int dx = Math.abs(from.getX() - to.getX());
        int dz = Math.abs(from.getZ() - to.getZ());
        return Math.max(dx, dz);
    }

    private static float clampPitch(float pitch) {
        return Math.max(-85.0f, Math.min(85.0f, pitch));
    }

    private static String buildStuckLieDiagnostic(
            ServerWorld world,
            ServerPlayerEntity player,
            AutoThrowSession session,
            PlayerRoundState state
    ) {
        BlockPos playerFeet = player.getBlockPos();
        BlockPos nearestPlayerFeet = SafePositionFinder.resolveSafeFeetNear(world, playerFeet);
        StringBuilder builder = new StringBuilder();
        builder.append("playerFeet=").append(formatPos(playerFeet));
        builder.append(" nearestStandable=").append(formatPos(nearestPlayerFeet));
        builder.append(" stateLie=").append(formatPos(state.lie()));
        if (session.lastLaunchPlayerFeet != null) {
            builder.append(" launchFeet=").append(formatPos(session.lastLaunchPlayerFeet));
        }
        if (session.lastLaunchSafeFeet != null) {
            builder.append(" safeFeet=").append(formatPos(session.lastLaunchSafeFeet));
        }
        if (session.lastLaunchBasket != null) {
            builder.append(" basket=").append(formatPos(session.lastLaunchBasket));
        }
        if (session.lastLaunchFrom != null && session.lastLaunchTo != null) {
            builder.append(" launchPath=").append(traceImmediateBlockage(world, session.lastLaunchFrom, session.lastLaunchTo));
        }
        return builder.toString();
    }

    private static BlockPos resolveClearLaunchFeetNear(ServerWorld world, BlockPos preferredFeet, BlockPos basket) {
        BlockPos fallback = SafePositionFinder.resolveSafeFeetNear(world, preferredFeet);
        if (hasClearImmediateLaunchPath(world, fallback, basket)) {
            return fallback;
        }

        BlockPos bestCandidate = fallback;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int radius = 0; radius <= 6; radius++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                            continue;
                        }

                        BlockPos candidate = preferredFeet.add(dx, dy, dz);
                        if (!SafePositionFinder.isStandableFeet(world, candidate) || !hasClearImmediateLaunchPath(world, candidate, basket)) {
                            continue;
                        }

                        double distanceSquared = candidate.getSquaredDistance(preferredFeet);
                        if (distanceSquared < bestDistanceSquared) {
                            bestCandidate = candidate;
                            bestDistanceSquared = distanceSquared;
                        }
                    }
                }
            }
        }

        return bestCandidate;
    }

    private static boolean hasClearImmediateLaunchPath(ServerWorld world, BlockPos launchFeet, BlockPos basket) {
        Vec3d from = new Vec3d(launchFeet.getX() + 0.5, launchFeet.getY() + 1.62, launchFeet.getZ() + 0.5);
        Vec3d to = new Vec3d(basket.getX() + 0.5, basket.getY() + 1.2, basket.getZ() + 0.5);
        return firstBlockingPos(world, from, to) == null;
    }

    private static String traceImmediateBlockage(ServerWorld world, Vec3d from, Vec3d to) {
        BlockPos blocked = firstBlockingPos(world, from, to);
        return blocked == null ? "clear" : "blocked@" + formatPos(blocked);
    }

    private static BlockPos firstBlockingPos(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0e-4) {
            return null;
        }

        Vec3d step = delta.normalize().multiply(0.4);
        Vec3d cursor = from;
        int samples = Math.min(8, Math.max(1, (int) Math.ceil(length / 0.4)));
        for (int i = 1; i <= samples; i++) {
            cursor = cursor.add(step);
            BlockPos probe = BlockPos.ofFloored(cursor);
            if (!world.getBlockState(probe).getCollisionShape(world, probe).isEmpty()) {
                return probe;
            }
        }

        return null;
    }

    private static void keepPlayerAlive(ServerPlayerEntity player) {
        if (player.isDead()) {
            player.setHealth(player.getMaxHealth());
        }

        player.setHealth(player.getMaxHealth());
        player.setInvulnerable(true);
        player.setFireTicks(0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 80, 2, false, false, true));
    }

    private static void clearNearbyHostiles(ServerWorld world, ServerPlayerEntity player) {
        Box search = new Box(player.getBlockPos()).expand(20.0, 8.0, 20.0);
        for (HostileEntity hostile : world.getEntitiesByClass(HostileEntity.class, search, entity -> entity.isAlive())) {
            hostile.discard();
        }
    }

    private static boolean hasInFlightPearlForPlayer(ServerWorld world, ServerPlayerEntity player) {
        Box search = new Box(player.getBlockPos()).expand(192.0, 96.0, 192.0);
        return !world.getEntitiesByClass(
                EnderPearlEntity.class,
                search,
                pearl -> pearl.getOwner() == player && !pearl.isRemoved()
        ).isEmpty();
    }


    private static final class AutoThrowSession {
        private final ServerCommandSource source;
        private final UUID playerId;
        private final int targetThrows;
        private final ServerBossBar progressBar;
        private int throwsLaunched;
        private int resolvedThrows;
        private int resolvedUnchangedLieThrows;
        private int consecutiveUnchangedLieThrows;
        private int lastObservedTotalStrokes;
        private int suspectUnchangedLieEvents;
        private BlockPos lastThrowLie;
        private int lastThrowHole;
        private BlockPos lastLaunchPlayerFeet;
        private BlockPos lastLaunchSafeFeet;
        private BlockPos lastLaunchBasket;
        private Vec3d lastLaunchFrom;
        private Vec3d lastLaunchTo;
        private final List<String> logLines = new ArrayList<>();
        private boolean waitingForLieResolution;
        private int waitTicksAfterThrow;

        private AutoThrowSession(
                ServerCommandSource source,
                UUID playerId,
                int targetThrows,
                int lastObservedTotalStrokes,
                BlockPos initialLie,
                ServerBossBar progressBar
        ) {
            this.source = source;
            this.playerId = playerId;
            this.targetThrows = targetThrows;
            this.lastObservedTotalStrokes = lastObservedTotalStrokes;
            this.lastThrowLie = initialLie;
            this.lastThrowHole = 1;
            this.progressBar = progressBar;
        }
    }
}
