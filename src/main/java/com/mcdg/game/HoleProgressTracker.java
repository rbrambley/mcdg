package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.ui.HudStateFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class HoleProgressTracker {
    private static final int BASKET_RADIUS_BLOCKS = 3;
    private static final int BASKET_HEIGHT_TOLERANCE = 5;
    private static final int MAX_THROW_RESOLUTION_WAIT_TICKS = 80;
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final boolean ENABLE_STRICT_LANDING_PENALTIES = false;
    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();
    private static final Map<UUID, Integer> LAST_PROCESSED_THROW_TOTAL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_PENDING_TICKS = new HashMap<>();

    private HoleProgressTracker() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean hudScoringDebug
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!courseManager.isRoundActive()) {
                LAST_PROCESSED_THROW_TOTAL.clear();
                LAST_THROW_PENDING_TICKS.clear();
                return;
            }

            Course course = courseManager.getActiveCourse().orElse(null);
            PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
            if (course == null || placed == null) {
                return;
            }

            Map<UUID, PlayerRoundState> snapshot = roundStateManager.snapshotStates();
            for (Map.Entry<UUID, PlayerRoundState> entry : snapshot.entrySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || player.getWorld().getRegistryKey() != placed.worldKey()) {
                    continue;
                }

                // Keep random course-construction pickups out of player inventories during active play.
                RoundInventoryCleaner.purgeJunkItems(player);

                boolean suppressHud = player.isUsingItem() && player.getActiveItem().isOf(McdgItems.TRAINING_DISC);

                PlayerRoundState state = entry.getValue();
                Hole currentHole = course.holes().get(state.currentHole() - 1);
                BlockPos basket = placed.holeBaskets().get(state.currentHole());
                BlockPos tee = placed.holeTees().get(state.currentHole());
                if (basket == null) {
                    continue;
                }

                if (tee != null) {
                    state = resolveThrowLanding(
                            player,
                            state,
                            currentHole,
                            tee,
                            basket,
                            roundStateManager,
                            rulesetManager,
                            hudScoringDebug
                    );
                }

                String heading = headingTo(player.getBlockPos(), basket);
                int dist = manhattanDistance(player.getBlockPos(), basket);
                int lieDist = manhattanDistance(state.lie(), basket);
                String status = dist <= 12 ? "Basket Run" : "Ready to Throw";
                int completedPar = cumulativeParThroughHole(course, state.currentHole() - 1);
                int runningExpectedThrows = completedPar + state.holeStrokes();
                int holeParDelta = computeHolePaceDelta(
                    currentHole.par(),
                    currentHole.distanceFeet(),
                    lieDist,
                    state.holeStrokes()
                );
                int cumulativeParDelta = state.totalStrokes() - runningExpectedThrows;
                if (!suppressHud && hudScoringDebug && (server.getTicks() % 20) == 0) {
                    McdgMod.LOGGER.info(
                            "HUD score debug | player={} hole={} par={} holeDistFt={} lieDistBlocks={} holeStrokes={} totalStrokes={} expectedRunning={} holeDelta={} totalDelta={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            currentHole.par(),
                            currentHole.distanceFeet(),
                            lieDist,
                            state.holeStrokes(),
                            state.totalStrokes(),
                            runningExpectedThrows,
                            holeParDelta,
                            cumulativeParDelta
                    );
                }
                if (!suppressHud) {
                    player.sendMessage(
                            HUD_STATE_FORMATTER.formatStatus(
                                    course.name(),
                                    state.currentHole(),
                                    currentHole.par(),
                                    currentHole.distanceFeet(),
                                    Math.max(1, state.holeStrokes() + 1),
                                    state.lastThrowPenalty(),
                                    state.holeStrokes(),
                                    holeParDelta,
                                    state.totalStrokes(),
                                    runningExpectedThrows,
                                    cumulativeParDelta,
                                    status,
                                    heading,
                                    dist,
                                    currentHole.signatureType().displayName()
                                ),
                            true
                    );
                }

                if (!suppressHud && (server.getTicks() % 20) == 0) {
                    if (player.getWorld() instanceof ServerWorld serverWorld) {
                        spawnBreadcrumbLine(serverWorld, player, basket);
                    }
                }

                if (!isAtBasket(player.getBlockPos(), basket)) {
                    continue;
                }

                ScorecardManager.recordHoleScore(player, state.currentHole(), state.holeStrokes());

                if (state.currentHole() >= course.holes().size()) {
                    int totalPar = totalCoursePar(course);
                    BlockPos firstTee = placed.holeTees().get(1);
                    if (firstTee != null) {
                        player.teleport(firstTee.getX() + 0.5, firstTee.getY() + 1.0, firstTee.getZ() + 0.5);
                    }

                    removeRoundThrowItems(player);

                    roundStateManager.recordCompletedRound(player.getUuid(), state.totalStrokes());
                    roundStateManager.clearPlayer(player.getUuid());

                    if (firstTee != null) {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                        player.sendMessage(Text.literal("Returned to Hole 1 tee."), false);
                    } else {
                        player.sendMessage(HUD_STATE_FORMATTER.formatRoundComplete(state.totalStrokes(), totalPar), false);
                    }

                    broadcastRoundLeaderboard(server, placed.worldKey(), roundStateManager, totalPar);

                    if (roundStateManager.snapshotStates().isEmpty()) {
                        courseManager.setRoundActive(false);
                    }
                    continue;
                }

                int nextHole = state.currentHole() + 1;
                BlockPos nextTee = placed.holeTees().get(nextHole);
                if (nextTee == null) {
                    roundStateManager.clearPlayer(player.getUuid());
                    player.sendMessage(Text.literal("Round ended: next tee could not be resolved."), false);
                    continue;
                }

                int completedHolePar = currentHole.par();
                roundStateManager.advanceToNextHole(player.getUuid(), nextTee);
                player.teleport(nextTee.getX() + 0.5, nextTee.getY() + 1.0, nextTee.getZ() + 0.5);
                player.sendMessage(
                        HUD_STATE_FORMATTER.formatHoleAdvance(
                                state.currentHole(),
                                state.holeStrokes(),
                                completedHolePar,
                                state.totalStrokes(),
                                cumulativeParThroughHole(course, state.currentHole()),
                                nextHole
                        ),
                    true
                );
            }
        });
    }

    private static int cumulativeParThroughHole(Course course, int holeIndexInclusive) {
        int par = 0;
        int max = Math.min(holeIndexInclusive, course.holes().size());
        for (int i = 0; i < max; i++) {
            par += course.holes().get(i).par();
        }
        return par;
    }

    private static int totalCoursePar(Course course) {
        int par = 0;
        for (Hole hole : course.holes()) {
            par += hole.par();
        }
        return par;
    }

    private static int computeHolePaceDelta(int holePar, int holeDistanceFeet, int distanceToBasketBlocks, int holeStrokes) {
        int basketDistanceFeet = Math.max(0, Math.round(distanceToBasketBlocks * 3.28084f));
        int normalizedHoleDistance = Math.max(1, holeDistanceFeet);
        float progress = 1.0f - (basketDistanceFeet / (float) normalizedHoleDistance);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        int expectedThrowsAtProgress = Math.round(progress * holePar);
        return holeStrokes - expectedThrowsAtProgress;
    }

    private static boolean isAtBasket(BlockPos playerPos, BlockPos basketPos) {
        int dx = Math.abs(playerPos.getX() - basketPos.getX());
        int dz = Math.abs(playerPos.getZ() - basketPos.getZ());
        int dy = Math.abs(playerPos.getY() - basketPos.getY());
        return dx <= BASKET_RADIUS_BLOCKS && dz <= BASKET_RADIUS_BLOCKS && dy <= BASKET_HEIGHT_TOLERANCE;
    }

    private static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX()) + Math.abs(from.getY() - to.getY()) + Math.abs(from.getZ() - to.getZ());
    }

    private static String headingTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();

        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
            return "HERE";
        }

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) {
            angle += 360.0;
        }

        String[] dirs = { "E", "SE", "S", "SW", "W", "NW", "N", "NE" };
        int index = (int) Math.round(angle / 45.0) % dirs.length;
        return dirs[index];
    }

    private static void spawnBreadcrumbLine(ServerWorld world, ServerPlayerEntity player, BlockPos to) {
        BlockPos from = player.getBlockPos();
        double sx = player.getX();
        double sy = player.getY() + 6.5;
        double sz = player.getZ();
        double tx = to.getX() + 0.5;
        double ty = to.getY() + 6.5;
        double tz = to.getZ() + 0.5;

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(32, Math.max(8, (int) (distance / 3.0)));

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double px = sx + (dx * t);
            double py = sy + (dy * t);
            double pz = sz + (dz * t);
            world.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private static void removeRoundThrowItems(ServerPlayerEntity player) {
        RoundInventoryCleaner.purgeRoundItemsAndJunk(player);
    }

    private static PlayerRoundState resolveThrowLanding(
            ServerPlayerEntity player,
            PlayerRoundState state,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean hudScoringDebug
    ) {
        Integer processedTotalObj = LAST_PROCESSED_THROW_TOTAL.get(player.getUuid());
        int processedTotal;
        if (processedTotalObj == null) {
            // First tracker tick for this player in the active round.
            // If no throws yet, initialize and wait. If throws already happened,
            // process the latest throw instead of skipping it.
            if (state.totalStrokes() == 0) {
                LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), 0);
                return state;
            }
            processedTotal = state.totalStrokes() - 1;
        } else {
            processedTotal = processedTotalObj;
        }

        if (state.totalStrokes() <= processedTotal) {
            return state;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos throwLie = state.lie();
        BlockPos currentFeet = player.getBlockPos();
        boolean movedFromThrowLie = hasPlayerMovedFromThrowLie(currentFeet, throwLie);

        // Wait for the thrown pearl to finish flight before resolving landing lie.
        // Without this, early processing can lock lie at the throw origin (tee).
        if (!movedFromThrowLie && hasInFlightPearlForPlayer(world, player, throwLie)) {
            int pendingTicks = LAST_THROW_PENDING_TICKS.merge(player.getUuid(), 1, Integer::sum);
            if (pendingTicks <= MAX_THROW_RESOLUTION_WAIT_TICKS) {
                return state;
            }

            McdgMod.LOGGER.warn(
                    "Forcing throw landing resolution after {} ticks with in-flight pearl still present | player={} hole={} throwLie={} {} {}",
                    pendingTicks,
                    player.getGameProfile().getName(),
                    state.currentHole(),
                    throwLie.getX(),
                    throwLie.getY(),
                    throwLie.getZ()
            );
        }
        LAST_THROW_PENDING_TICKS.remove(player.getUuid());

        BlockPos landingFeet = findNearestStandableFeet(world, currentFeet);

        BlockPos resultingLie = landingFeet;
        BlockPos firstOutCrossing = null;
        StrictPenaltyType landingPenalty = StrictPenaltyType.NONE;
        if (ENABLE_STRICT_LANDING_PENALTIES && rulesetManager.isStrict()) {
            landingPenalty = classifyOutType(world, landingFeet, currentHole, tee, basket);
            if (landingPenalty != StrictPenaltyType.NONE) {
                CrossingResolution crossing = findLastSolidBeforeOutCrossing(world, throwLie, landingFeet, currentHole, tee, basket);
                resultingLie = crossing.safeLie();
                firstOutCrossing = crossing.firstOutCrossing();

                int penaltyStrokes = landingPenalty == StrictPenaltyType.OB
                        ? rulesetManager.strictObPenaltyStrokes()
                        : rulesetManager.strictHazardPenaltyStrokes();
                if (penaltyStrokes > 0) {
                    roundStateManager.applyPenaltyStrokes(player.getUuid(), penaltyStrokes);
                }

                player.teleport(
                        resultingLie.getX() + 0.5,
                        resultingLie.getY() + 1.0,
                        resultingLie.getZ() + 0.5
                );

                String label = landingPenalty == StrictPenaltyType.OB ? "OB" : "Hazard";
                player.sendMessage(
                        Text.literal(label + " landing in strict mode: +" + penaltyStrokes + " stroke. Returned to last in-bounds solid block."),
                        true
                );
                state = roundStateManager.markLastThrowPenalty(player.getUuid(), true).orElse(state);
            }

            if (hudScoringDebug) {
                player.sendMessage(Text.literal(
                        "Strict dbg | landing=" + landingPenalty.name()
                                + " | firstOut=" + formatPos(firstOutCrossing)
                                + " | safeLie=" + formatPos(resultingLie)
                ), false);
            }
        }

        if (landingPenalty == StrictPenaltyType.NONE) {
            state = roundStateManager.markLastThrowPenalty(player.getUuid(), false).orElse(state);
        }

        roundStateManager.updateLie(player.getUuid(), resultingLie);
        PlayerRoundState updated = roundStateManager.getState(player.getUuid()).orElse(state);
        LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), updated.totalStrokes());
        return updated;
    }

    private static boolean hasInFlightPearlForPlayer(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        Box search = new Box(origin).expand(192.0, 96.0, 192.0);
        return !world.getEntitiesByClass(
                EnderPearlEntity.class,
                search,
                pearl -> pearl.getOwner() == player && !pearl.isRemoved()
        ).isEmpty();
    }

    private static StrictPenaltyType classifyOutType(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket
    ) {
        if (world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA)) {
            return StrictPenaltyType.HAZARD;
        }

        double lateral = distanceFromPointToSegmentXZ(feet, tee, basket);
        if (lateral > strictCorridorHalfWidth(currentHole)) {
            return StrictPenaltyType.OB;
        }

        return StrictPenaltyType.NONE;
    }

    private static int strictCorridorHalfWidth(Hole hole) {
        int maxSegmentWidth = 0;
        for (var segment : hole.fairwaySegments()) {
            maxSegmentWidth = Math.max(maxSegmentWidth, segment.width());
        }
        int baseHalf = Math.max(3, maxSegmentWidth / 2);
        return Math.max(8, baseHalf + 6);
    }

    private static double distanceFromPointToSegmentXZ(BlockPos point, BlockPos start, BlockPos end) {
        double px = point.getX() + 0.5;
        double pz = point.getZ() + 0.5;
        double sx = start.getX() + 0.5;
        double sz = start.getZ() + 0.5;
        double ex = end.getX() + 0.5;
        double ez = end.getZ() + 0.5;

        double dx = ex - sx;
        double dz = ez - sz;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < 1.0e-6) {
            double mx = px - sx;
            double mz = pz - sz;
            return Math.sqrt(mx * mx + mz * mz);
        }

        double t = ((px - sx) * dx + (pz - sz) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double nx = sx + (t * dx);
        double nz = sz + (t * dz);
        double mx = px - nx;
        double mz = pz - nz;
        return Math.sqrt(mx * mx + mz * mz);
    }

    private static CrossingResolution findLastSolidBeforeOutCrossing(
            ServerWorld world,
            BlockPos throwLie,
            BlockPos landingFeet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket
    ) {
        BlockPos start = findNearestStandableFeet(world, throwLie);
        BlockPos end = findNearestStandableFeet(world, landingFeet);
        BlockPos lastInBoundsSolid = start;
        BlockPos firstOut = null;

        int distance = Math.max(1, manhattanDistance(start, end));
        int samples = Math.max(16, distance * 3);
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            int x = (int) Math.floor((start.getX() + 0.5) + ((end.getX() + 0.5 - (start.getX() + 0.5)) * t));
            int y = (int) Math.floor((start.getY() + 0.5) + ((end.getY() + 0.5 - (start.getY() + 0.5)) * t));
            int z = (int) Math.floor((start.getZ() + 0.5) + ((end.getZ() + 0.5 - (start.getZ() + 0.5)) * t));
            BlockPos probe = findNearestStandableFeet(world, new BlockPos(x, y, z));

            if (classifyOutType(world, probe, currentHole, tee, basket) != StrictPenaltyType.NONE) {
                firstOut = probe;
                return new CrossingResolution(lastInBoundsSolid, firstOut);
            }

            if (isStandableFeetBlock(world, probe)) {
                lastInBoundsSolid = probe;
            }
        }

        return new CrossingResolution(lastInBoundsSolid, firstOut);
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) {
            return "-";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos findNearestStandableFeet(ServerWorld world, BlockPos baseFeet) {
        BlockPos candidate = baseFeet;
        if (isStandableFeetBlock(world, candidate)) {
            return candidate;
        }

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos up = baseFeet.up(offset);
            if (isStandableFeetBlock(world, up)) {
                return up;
            }
            BlockPos down = baseFeet.down(offset);
            if (isStandableFeetBlock(world, down)) {
                return down;
            }
        }

        for (int radius = 1; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidateAtRadius = baseFeet.add(dx, 0, dz);
                    if (isStandableFeetBlock(world, candidateAtRadius)) {
                        return candidateAtRadius;
                    }

                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidateAtRadius.up(dy);
                        if (isStandableFeetBlock(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidateAtRadius.down(dy);
                        if (isStandableFeetBlock(world, candidateDown)) {
                            return candidateDown;
                        }
                    }
                }
            }
        }

        return baseFeet;
    }

    private static boolean isStandableFeetBlock(ServerWorld world, BlockPos feet) {
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

    private static boolean hasPlayerMovedFromThrowLie(BlockPos playerFeet, BlockPos throwLie) {
        int dx = Math.abs(playerFeet.getX() - throwLie.getX());
        int dz = Math.abs(playerFeet.getZ() - throwLie.getZ());
        if (dx > 0 || dz > 0) {
            return true;
        }

        int dy = Math.abs(playerFeet.getY() - throwLie.getY());
        return dy > 1;
    }

    private enum StrictPenaltyType {
        NONE,
        HAZARD,
        OB
    }

    private record CrossingResolution(BlockPos safeLie, BlockPos firstOutCrossing) {
    }

    private static void broadcastRoundLeaderboard(
            MinecraftServer server,
            RegistryKey<net.minecraft.world.World> worldKey,
            RoundStateManager roundStateManager,
            int totalPar
    ) {
        Map<UUID, Integer> completed = roundStateManager.snapshotCompletedRounds();
        if (completed.isEmpty()) {
            return;
        }

        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(completed.entrySet());
        ranked.sort(Comparator.comparingInt(Map.Entry::getValue));

        List<ServerPlayerEntity> viewers = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> p.getWorld().getRegistryKey() == worldKey)
                .toList();
        if (viewers.isEmpty()) {
            return;
        }

        Text header = HUD_STATE_FORMATTER.formatRoundSummaryHeader(totalPar, ranked.size());
        for (ServerPlayerEntity viewer : viewers) {
            viewer.sendMessage(header, false);
        }

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : ranked) {
            ServerPlayerEntity rankedPlayer = server.getPlayerManager().getPlayer(entry.getKey());
            String name = rankedPlayer != null ? rankedPlayer.getGameProfile().getName() : entry.getKey().toString().substring(0, 8);
            Text line = HUD_STATE_FORMATTER.formatRoundSummaryEntry(rank, name, entry.getValue(), totalPar);
            for (ServerPlayerEntity viewer : viewers) {
                viewer.sendMessage(line, false);
            }
            rank++;
        }
    }
}
