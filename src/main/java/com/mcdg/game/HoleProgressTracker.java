package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.net.HoleMiniMapSync;
import com.mcdg.rules.TournamentRulesetManager;
import com.mcdg.ui.HudStateFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

public final class HoleProgressTracker {
    private static final int BASKET_RADIUS_BLOCKS = 1;
    private static final int BASKET_HEIGHT_TOLERANCE = 2;
    private static final int MAX_THROW_RESOLUTION_WAIT_TICKS = 320;
    private static final int THROW_RELEASE_GRACE_TICKS = 8;
    // Temporary safety rollback: keep core throw/lie flow stable while strict landing penalties are reworked.
    private static final boolean ENABLE_STRICT_LANDING_PENALTIES = true;
    private static final HudStateFormatter HUD_STATE_FORMATTER = new HudStateFormatter();
    private static final Map<UUID, Integer> LAST_PROCESSED_THROW_TOTAL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_THROW_PENDING_TICKS = new HashMap<>();
    private static final Map<UUID, UUID> LAST_THROW_PEARL_UUID = new HashMap<>();
    private static final Map<UUID, Long> LAST_THROW_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, Boolean> STRICT_PENALTY_THROW_BYPASS = new HashMap<>();
    private static final Map<UUID, String> LAST_RESOLUTION_REASON = new HashMap<>();
    private static final Map<UUID, LieMarkerState> LAST_LIE_MARKER_STATE = new HashMap<>();
    private static final Map<UUID, MiniMapTerrainSnapshot> LAST_MINIMAP_TERRAIN = new HashMap<>();

    private HoleProgressTracker() {
    }

    public static void register(
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            TournamentRulesetManager rulesetManager,
            boolean hudScoringDebug,
            boolean strictFlowDebug
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!courseManager.isRoundActive()) {
                LAST_PROCESSED_THROW_TOTAL.clear();
                LAST_THROW_PENDING_TICKS.clear();
                LAST_THROW_PEARL_UUID.clear();
                LAST_THROW_RELEASE_TICK.clear();
                STRICT_PENALTY_THROW_BYPASS.clear();
                LAST_RESOLUTION_REASON.clear();
                LAST_MINIMAP_TERRAIN.clear();
                clearAllLieMarkers(server);
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
                BlockPos alternateAnchor = placed.holeAlternateAnchors().get(state.currentHole());
                if (basket == null) {
                    continue;
                }

                updateLieMarker(player, state.lie());

                if (tee != null) {
                    state = resolveThrowLanding(
                            player,
                            state,
                            currentHole,
                            tee,
                            basket,
                            roundStateManager,
                            rulesetManager,
                            hudScoringDebug,
                            strictFlowDebug
                    );
                }

                String heading = headingTo(player.getBlockPos(), basket);
                int dist = manhattanDistance(player.getBlockPos(), basket);
                int lieDistMeters = distanceMeters(state.lie(), basket);
                int lieDistFeet = distanceFeet(state.lie(), basket);
                String status = dist <= 12 ? "Basket Run" : "Ready to Throw";
                int completedPar = cumulativeParThroughHole(course, state.currentHole() - 1);
                int runningExpectedThrows = completedPar + state.holeStrokes();
                int holeParDelta = computeHolePaceDelta(
                    currentHole.par(),
                    currentHole.distanceFeet(),
                    lieDistMeters,
                    state.holeStrokes()
                );
                int cumulativeParDelta = state.totalStrokes() - runningExpectedThrows;

                MiniMapTerrainSnapshot previousTerrain = LAST_MINIMAP_TERRAIN.get(player.getUuid());
                boolean refreshTerrain = shouldRefreshMiniMapTerrain(
                        previousTerrain,
                        server.getTicks(),
                        state.currentHole(),
                        state.lie(),
                        rulesetManager
                );
                MiniMapPayloadBuildResult miniMapPayload = buildMiniMapPayload(
                        player.getServerWorld(),
                        currentHole,
                        rulesetManager,
                        state.currentHole(),
                        currentHole.par(),
                        Math.max(1, state.holeStrokes() + 1),
                        state.totalStrokes(),
                        cumulativeParDelta,
                        tee == null ? state.lie() : tee,
                        basket,
                        state.lie(),
                        rulesetManager.isStrict(),
                        alternateAnchor,
                        previousTerrain,
                        refreshTerrain,
                        server.getTicks()
                );
                if (miniMapPayload.refreshedTerrain() != null) {
                    LAST_MINIMAP_TERRAIN.put(player.getUuid(), miniMapPayload.refreshedTerrain());
                }

                ServerPlayNetworking.send(
                    player,
                    miniMapPayload.payload()
                );
                if (!suppressHud && hudScoringDebug && (server.getTicks() % 20) == 0) {
                    McdgMod.LOGGER.info(
                            "HUD score debug | player={} hole={} par={} holeDistFt={} lieDistMeters={} lieDistFeet={} holeStrokes={} totalStrokes={} expectedRunning={} holeDelta={} totalDelta={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            currentHole.par(),
                            currentHole.distanceFeet(),
                            lieDistMeters,
                            lieDistFeet,
                            state.holeStrokes(),
                            state.totalStrokes(),
                            runningExpectedThrows,
                            holeParDelta,
                            cumulativeParDelta
                    );
                }
                // Status action-bar removed in favor of right-side HUD overlays on the client.

                if (!suppressHud && (server.getTicks() % 20) == 0) {
                    if (player.getWorld() instanceof ServerWorld serverWorld) {
                        spawnBreadcrumbLine(serverWorld, player, basket);
                    }
                }

                // Score completion only from the resolved lie, so walking into the basket does not count.
                if (!isAtBasket(state.lie(), basket)) {
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
                BlockPos safeNextTee = resolveSafeFeetNear(player.getServerWorld(), nextTee);
                roundStateManager.advanceToNextHole(player.getUuid(), safeNextTee);
                player.teleport(safeNextTee.getX() + 0.5, safeNextTee.getY() + 1.0, safeNextTee.getZ() + 0.5);
                updateLieMarker(player, safeNextTee);
                sendHoleFinishTitle(player, state.holeStrokes(), completedHolePar);
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

    private static int distanceMeters(BlockPos from, BlockPos to) {
        double dx = (to.getX() + 0.5) - (from.getX() + 0.5);
        double dy = (to.getY() + 0.5) - (from.getY() + 0.5);
        double dz = (to.getZ() + 0.5) - (from.getZ() + 0.5);
        return Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
    }

    private static int distanceFeet(BlockPos from, BlockPos to) {
        return Math.max(0, Math.round(distanceMeters(from, to) * 3.28084f));
    }

    private static MiniMapPayloadBuildResult buildMiniMapPayload(
            ServerWorld world,
            Hole currentHole,
            TournamentRulesetManager rulesetManager,
            int holeIndex,
            int par,
            int throwNumber,
            int totalStrokes,
            int cumulativeParDelta,
            BlockPos tee,
            BlockPos basket,
            BlockPos lie,
            boolean strictMode,
            BlockPos alternateAnchor,
            MiniMapTerrainSnapshot cachedTerrain,
            boolean refreshTerrain,
            int serverTick
    ) {
        int originX;
        int originZ;
        int span;
        byte[] terrainCells;
        MiniMapTerrainSnapshot refreshedTerrain = null;

        boolean canReuseTerrain = !refreshTerrain
                && cachedTerrain != null
                && cachedTerrain.holeIndex() == holeIndex
                && cachedTerrain.terrainCells().length == (HoleMiniMapSync.TERRAIN_GRID_SIZE * HoleMiniMapSync.TERRAIN_GRID_SIZE);

        if (canReuseTerrain) {
            originX = cachedTerrain.mapOriginX();
            originZ = cachedTerrain.mapOriginZ();
            span = cachedTerrain.mapSpan();
            terrainCells = new byte[0];
        } else {
            int minX = Math.min(Math.min(tee.getX(), basket.getX()), lie.getX());
            int maxX = Math.max(Math.max(tee.getX(), basket.getX()), lie.getX());
            int minZ = Math.min(Math.min(tee.getZ(), basket.getZ()), lie.getZ());
            int maxZ = Math.max(Math.max(tee.getZ(), basket.getZ()), lie.getZ());
            if (alternateAnchor != null) {
                minX = Math.min(minX, alternateAnchor.getX());
                maxX = Math.max(maxX, alternateAnchor.getX());
                minZ = Math.min(minZ, alternateAnchor.getZ());
                maxZ = Math.max(maxZ, alternateAnchor.getZ());
            }

            int baseSpan = Math.max(Math.max(1, maxX - minX), Math.max(1, maxZ - minZ)) + 10;
            int maxLieDelta = maxLieDelta(lie, tee, basket, alternateAnchor);
            // Ensure a player-centered map still reaches far enough forward to include basket/route targets.
            span = Math.max(120, Math.max(baseSpan, (maxLieDelta * 2) + 24));
            int halfSpan = span / 2;
            originX = lie.getX() - halfSpan;
            originZ = lie.getZ() - halfSpan;
            terrainCells = sampleMiniMapTerrain(world, currentHole, tee, basket, rulesetManager, originX, originZ, span);
            refreshedTerrain = new MiniMapTerrainSnapshot(
                    holeIndex,
                    originX,
                    originZ,
                    span,
                    lie.getX(),
                    lie.getZ(),
                    serverTick,
                    terrainCells
            );
        }

        return new MiniMapPayloadBuildResult(HoleMiniMapSync.Payload.active(
                holeIndex,
                tee.getX(),
                tee.getZ(),
                basket.getX(),
                basket.getZ(),
                lie.getX(),
                lie.getZ(),
            par,
            throwNumber,
            totalStrokes,
            cumulativeParDelta,
                rulesetManager.getMiniMapQualityPreset().ordinal(),
                strictMode,
                alternateAnchor != null,
                alternateAnchor == null ? 0 : alternateAnchor.getX(),
                alternateAnchor == null ? 0 : alternateAnchor.getZ(),
                originX,
                originZ,
                span,
                terrainCells
        ), refreshedTerrain);
    }

    private static boolean shouldRefreshMiniMapTerrain(
            MiniMapTerrainSnapshot previousTerrain,
            int serverTick,
            int currentHole,
            BlockPos lie,
            TournamentRulesetManager rulesetManager
    ) {
        if (previousTerrain == null) {
            return true;
        }
        if (previousTerrain.holeIndex() != currentHole) {
            return true;
        }

        int maxAxisDrift = Math.max(
                Math.abs(lie.getX() - previousTerrain.lieAnchorX()),
                Math.abs(lie.getZ() - previousTerrain.lieAnchorZ())
        );
        if (maxAxisDrift >= rulesetManager.miniMapTerrainRefreshMoveThresholdBlocks()) {
            return true;
        }

        int ticksSinceRefresh = Math.max(0, serverTick - previousTerrain.serverTick());
        return ticksSinceRefresh >= rulesetManager.miniMapTerrainRefreshIntervalTicks();
    }

    private static int maxLieDelta(BlockPos lie, BlockPos tee, BlockPos basket, BlockPos alternateAnchor) {
        int maxDelta = Math.max(
                Math.max(Math.abs(tee.getX() - lie.getX()), Math.abs(tee.getZ() - lie.getZ())),
                Math.max(Math.abs(basket.getX() - lie.getX()), Math.abs(basket.getZ() - lie.getZ()))
        );
        if (alternateAnchor != null) {
            maxDelta = Math.max(
                    maxDelta,
                    Math.max(Math.abs(alternateAnchor.getX() - lie.getX()), Math.abs(alternateAnchor.getZ() - lie.getZ()))
            );
        }
        return maxDelta;
    }

    private static byte[] sampleMiniMapTerrain(
            ServerWorld world,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            TournamentRulesetManager rulesetManager,
            int originX,
            int originZ,
            int span
    ) {
        int grid = HoleMiniMapSync.TERRAIN_GRID_SIZE;
        byte[] cells = new byte[grid * grid];
        int[] heights = new int[grid * grid];
        byte[] terrainClasses = new byte[grid * grid];
        int denominator = Math.max(1, grid - 1);
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        for (int gz = 0; gz < grid; gz++) {
            for (int gx = 0; gx < grid; gx++) {
                int sampleX = originX + Math.round((gx / (float) denominator) * span);
                int sampleZ = originZ + Math.round((gz / (float) denominator) * span);
                int index = (gz * grid) + gx;
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, sampleX, sampleZ) - 1;
                heights[index] = surfaceY;
                terrainClasses[index] = classifyMiniMapTerrainClass(world, sampleX, sampleZ, surfaceY);

                if (surfaceY >= world.getBottomY()) {
                    minHeight = Math.min(minHeight, surfaceY);
                    maxHeight = Math.max(maxHeight, surfaceY);
                }
            }
        }

        if (minHeight == Integer.MAX_VALUE || maxHeight == Integer.MIN_VALUE) {
            minHeight = world.getBottomY();
            maxHeight = world.getBottomY();
        }

        int heightRange = Math.max(1, maxHeight - minHeight);

        for (int gz = 0; gz < grid; gz++) {
            for (int gx = 0; gx < grid; gx++) {
                int index = (gz * grid) + gx;
                int sampleX = originX + Math.round((gx / (float) denominator) * span);
                int sampleZ = originZ + Math.round((gz / (float) denominator) * span);
                int surfaceY = heights[index];
                int elevationBand = quantizeElevationBand(surfaceY, minHeight, heightRange, world.getBottomY());

                BlockPos feet = new BlockPos(sampleX, Math.max(world.getBottomY() + 1, surfaceY + 1), sampleZ);
                StrictPenaltyType outType = classifyOutType(world, feet, currentHole, tee, basket, rulesetManager);
                int riskCode = switch (outType) {
                    case HAZARD -> 1;
                    case OB -> 2;
                    default -> 0;
                };

                cells[index] = packMiniMapCell(terrainClasses[index], riskCode, elevationBand);
            }
        }

        return cells;
    }

    private static byte classifyMiniMapTerrainClass(ServerWorld world, int x, int z, int surfaceY) {
        if (surfaceY < world.getBottomY()) {
            return 0;
        }

        BlockPos surface = new BlockPos(x, surfaceY, z);
        BlockState state = world.getBlockState(surface);
        if (world.getFluidState(surface).isIn(FluidTags.LAVA)) {
            return 10;
        }
        if (world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return 1;
        }
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)
                || state.isOf(Blocks.FROSTED_ICE)) {
            return 7;
        }
        if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.POWDER_SNOW)) {
            return 6;
        }
        if (state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.MOSS_BLOCK)
                || state.isOf(Blocks.FERN)
                || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.SHORT_GRASS)) {
            return 3;
        }
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MUD) || state.isOf(Blocks.MYCELIUM)
                || state.isOf(Blocks.SOUL_SOIL)) {
            return 8;
        }
        if (state.isOf(Blocks.DIRT_PATH) || state.isOf(Blocks.FARMLAND)
                || state.isOf(Blocks.CLAY) || state.isOf(Blocks.GRAVEL)) {
            return 9;
        }
        if (state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND)) {
            return 2;
        }
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) {
            return 4;
        }
        if (state.isOf(Blocks.STONE) || state.isOf(Blocks.ANDESITE) || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.CALCITE)) {
            return 5;
        }
        return 8;
    }

    private static int quantizeElevationBand(int surfaceY, int minHeight, int heightRange, int worldBottomY) {
        if (surfaceY < worldBottomY) {
            return 0;
        }
        int normalized = ((surfaceY - minHeight) * 4) / (heightRange + 1);
        return Math.max(0, Math.min(3, normalized));
    }

    private static byte packMiniMapCell(int terrainClass, int riskCode, int elevationBand) {
        int terrain = terrainClass & 0x0F;
        int risk = (riskCode & 0x03) << 4;
        int elevation = (elevationBand & 0x03) << 6;
        return (byte) (terrain | risk | elevation);
    }

    private static void clearAllLieMarkers(MinecraftServer server) {
        for (LieMarkerState markerState : LAST_LIE_MARKER_STATE.values()) {
            ServerWorld world = server.getWorld(markerState.worldKey());
            if (world == null) {
                continue;
            }
            if (world.getBlockState(markerState.markerPos()).isOf(Blocks.LIME_WOOL)) {
                world.setBlockState(markerState.markerPos(), markerState.previousGroundState(), 3);
            }
        }
        LAST_LIE_MARKER_STATE.clear();
    }

    private static void updateLieMarker(ServerPlayerEntity player, BlockPos lieFeet) {
        ServerWorld world = player.getServerWorld();
        BlockPos markerPos = lieFeet.down();
        UUID playerId = player.getUuid();

        LieMarkerState previous = LAST_LIE_MARKER_STATE.get(playerId);
        if (previous != null) {
            if (previous.worldKey().equals(world.getRegistryKey()) && previous.markerPos().equals(markerPos)) {
                return;
            }

            ServerWorld prevWorld = player.getServer().getWorld(previous.worldKey());
            if (prevWorld != null && prevWorld.getBlockState(previous.markerPos()).isOf(Blocks.LIME_WOOL)) {
                prevWorld.setBlockState(previous.markerPos(), previous.previousGroundState(), 3);
            }
        }

        BlockState original = world.getBlockState(markerPos);
        world.setBlockState(markerPos, Blocks.LIME_WOOL.getDefaultState(), 3);
        LAST_LIE_MARKER_STATE.put(
                playerId,
                new LieMarkerState(world.getRegistryKey(), markerPos.toImmutable(), original)
        );
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

    private static void sendStrictPenaltyTitle(ServerPlayerEntity player, StrictPenaltyType landingPenalty, int penaltyStrokes) {
        String titleText = landingPenalty == StrictPenaltyType.OB ? "OB +" + penaltyStrokes : "Hazard +" + penaltyStrokes;
        String subtitleText = landingPenalty == StrictPenaltyType.OB ? "Returned to lie" : "Penalty applied";

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(net.minecraft.util.Formatting.WHITE)));
    }

    private static void sendHoleFinishTitle(ServerPlayerEntity player, int holeScore, int holePar) {
        int holeDelta = holeScore - holePar;
        String resultName = golfResultName(holeScore, holeDelta);
        String deltaText = holeDelta == 0 ? "E" : (holeDelta > 0 ? "+" + holeDelta : Integer.toString(holeDelta));
        net.minecraft.util.Formatting resultColor = holeDelta <= 0
                ? net.minecraft.util.Formatting.GREEN
                : net.minecraft.util.Formatting.RED;

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 30, 10));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(resultName).formatted(resultColor, net.minecraft.util.Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(deltaText).formatted(resultColor, net.minecraft.util.Formatting.BOLD)));
    }

    private static String golfResultName(int holeScore, int holeDelta) {
        if (holeScore == 1) {
            return "Ace";
        }
        if (holeDelta == -3) {
            return "Albatross";
        }
        if (holeDelta <= -4) {
            return "Three or Better";
        }
        if (holeDelta == -2) {
            return "Eagle";
        }
        if (holeDelta == -1) {
            return "Birdie";
        }
        if (holeDelta == 0) {
            return "Par";
        }
        if (holeDelta == 1) {
            return "Bogey";
        }
        if (holeDelta == 2) {
            return "Double Bogey";
        }
        if (holeDelta == 3) {
            return "Triple Bogey";
        }
        return "+" + holeDelta + " Bogey";
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
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    BlockPos candidate = preferredFeet.add(dx, 0, dz);
                    if (isStandableFeet(world, candidate)) {
                        return candidate;
                    }
                    for (int dy = 1; dy <= 3; dy++) {
                        BlockPos candidateUp = candidate.up(dy);
                        if (isStandableFeet(world, candidateUp)) {
                            return candidateUp;
                        }
                        BlockPos candidateDown = candidate.down(dy);
                        if (isStandableFeet(world, candidateDown)) {
                            return candidateDown;
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
                boolean hudScoringDebug,
                boolean strictFlowDebug
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
            LAST_RESOLUTION_REASON.put(player.getUuid(), "NO_NEW_THROW");
            return state;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos throwLie = state.lie();
        BlockPos currentFeet = player.getBlockPos();
        boolean movedFromThrowLie = hasPlayerMovedFromThrowLie(currentFeet, throwLie);

        // Wait for the exact throw pearl (plus a short release grace window) before resolving lie.
        // This avoids stale, older pearls from keeping resolution pinned at the tee.
        boolean trackedPearlInFlight = !movedFromThrowLie && hasTrackedPearlInFlight(world, player, throwLie);
        boolean withinReleaseGrace = !movedFromThrowLie && isWithinThrowReleaseGrace(world, player.getUuid());
        if (trackedPearlInFlight || withinReleaseGrace) {
            int pendingTicks = LAST_THROW_PENDING_TICKS.merge(player.getUuid(), 1, Integer::sum);
            if (pendingTicks <= MAX_THROW_RESOLUTION_WAIT_TICKS) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing wait | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} inFlightPearl={} releaseGrace={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            pendingTicks,
                            trackedPearlInFlight,
                            withinReleaseGrace
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), trackedPearlInFlight ? "WAITING_TRACKED_PEARL" : "WAITING_RELEASE_GRACE");
                return state;
            }

            // If the exact throw pearl is still in flight after timeout, keep waiting instead of
            // force-resolving to the throw lie. This avoids false strict blocks on very long throws.
            if (trackedPearlInFlight) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.warn(
                            "Strict landing long-flight wait extension | player={} hole={} total={} throwLie={} currentFeet={} pendingTicks={} trackedPearlStillInFlight=true",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            pendingTicks
                    );
                }
                LAST_RESOLUTION_REASON.put(player.getUuid(), "WAITING_TRACKED_PEARL_LONG_FLIGHT");
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
            landingPenalty = classifyOutType(world, landingFeet, currentHole, tee, basket, rulesetManager);
            if (landingPenalty != StrictPenaltyType.NONE) {
                if (strictFlowDebug) {
                    McdgMod.LOGGER.info(
                            "Strict landing classified | player={} hole={} total={} throwLie={} currentFeet={} landingFeet={} penalty={}",
                            player.getGameProfile().getName(),
                            state.currentHole(),
                            state.totalStrokes(),
                            formatPos(throwLie),
                            formatPos(currentFeet),
                            formatPos(landingFeet),
                            landingPenalty.name()
                    );
                }
                CrossingResolution crossing = findLastSolidBeforeOutCrossing(world, throwLie, landingFeet, currentHole, tee, basket, rulesetManager);
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
                sendStrictPenaltyTitle(player, landingPenalty, penaltyStrokes);
                state = roundStateManager.markLastThrowPenalty(player.getUuid(), true).orElse(state);
                STRICT_PENALTY_THROW_BYPASS.put(player.getUuid(), Boolean.TRUE);
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
        updateLieMarker(player, resultingLie);
        PlayerRoundState updated = roundStateManager.getState(player.getUuid()).orElse(state);
        if (strictFlowDebug) {
            McdgMod.LOGGER.info(
                    "Strict landing resolved | player={} hole={} totalBefore={} totalAfter={} throwLie={} resultingLie={} penalty={} lastPenalty={} bypassArmed={}",
                    player.getGameProfile().getName(),
                    updated.currentHole(),
                    state.totalStrokes(),
                    updated.totalStrokes(),
                    formatPos(throwLie),
                    formatPos(resultingLie),
                    landingPenalty.name(),
                    updated.lastThrowPenalty(),
                    STRICT_PENALTY_THROW_BYPASS.containsKey(player.getUuid())
            );
        }
        LAST_PROCESSED_THROW_TOTAL.put(player.getUuid(), updated.totalStrokes());
        LAST_THROW_PEARL_UUID.remove(player.getUuid());
        LAST_THROW_RELEASE_TICK.remove(player.getUuid());
        LAST_RESOLUTION_REASON.put(player.getUuid(), "RESOLVED");
        return updated;
    }

    static void registerThrowRelease(UUID playerId, UUID pearlId, long worldTime) {
        LAST_THROW_PEARL_UUID.put(playerId, pearlId);
        LAST_THROW_RELEASE_TICK.put(playerId, worldTime);
        LAST_THROW_PENDING_TICKS.remove(playerId);
    }

    static boolean consumeStrictPenaltyThrowBypass(UUID playerId) {
        return STRICT_PENALTY_THROW_BYPASS.remove(playerId) != null;
    }

    static boolean isThrowResolutionPending(UUID playerId, int totalStrokes) {
        if (totalStrokes <= 0) {
            return false;
        }

        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        if (processedTotal == null) {
            return true;
        }

        return processedTotal < totalStrokes;
    }

    static String strictThrowGateDebugSnapshot(UUID playerId, int totalStrokes) {
        Integer processedTotal = LAST_PROCESSED_THROW_TOTAL.get(playerId);
        Integer pendingTicks = LAST_THROW_PENDING_TICKS.get(playerId);
        boolean bypassArmed = STRICT_PENALTY_THROW_BYPASS.containsKey(playerId);
        String reason = LAST_RESOLUTION_REASON.getOrDefault(playerId, "UNKNOWN");
        boolean pending = totalStrokes > 0 && (processedTotal == null || processedTotal < totalStrokes);
        return "pending=" + pending
                + " totalStrokes=" + totalStrokes
                + " processedTotal=" + (processedTotal == null ? "-" : processedTotal)
                + " pendingTicks=" + (pendingTicks == null ? "-" : pendingTicks)
                + " bypassArmed=" + bypassArmed
                + " lastReason=" + reason;
    }

    private static boolean hasTrackedPearlInFlight(ServerWorld world, ServerPlayerEntity player, BlockPos origin) {
        UUID trackedPearlId = LAST_THROW_PEARL_UUID.get(player.getUuid());
        if (trackedPearlId == null) {
            return false;
        }

        Box search = new Box(origin).expand(384.0, 192.0, 384.0);
        return !world.getEntitiesByClass(
                EnderPearlEntity.class,
                search,
                pearl -> trackedPearlId.equals(pearl.getUuid()) && !pearl.isRemoved()
        ).isEmpty();
    }

    private static boolean isWithinThrowReleaseGrace(ServerWorld world, UUID playerId) {
        Long releaseTick = LAST_THROW_RELEASE_TICK.get(playerId);
        if (releaseTick == null) {
            return false;
        }
        return (world.getTime() - releaseTick) <= THROW_RELEASE_GRACE_TICKS;
    }

    private static StrictPenaltyType classifyOutType(
            ServerWorld world,
            BlockPos feet,
            Hole currentHole,
            BlockPos tee,
            BlockPos basket,
            TournamentRulesetManager rulesetManager
    ) {
        if (world.getFluidState(feet).isIn(FluidTags.WATER)
                || world.getFluidState(feet).isIn(FluidTags.LAVA)
                || world.getFluidState(feet.down()).isIn(FluidTags.WATER)
                || world.getFluidState(feet.down()).isIn(FluidTags.LAVA)) {
            return StrictPenaltyType.HAZARD;
        }

        if (rulesetManager.strictEnableSlopeHazard() && isSteepSlopeHazard(world, feet, rulesetManager.strictSlopeHazardDeltaY())) {
            return StrictPenaltyType.HAZARD;
        }

        if (rulesetManager.strictEnableRoughHazard() && isDenseRoughHazard(world, feet, rulesetManager.strictRoughHazardLeafLogThreshold())) {
            return StrictPenaltyType.HAZARD;
        }

        double lateral = distanceFromPointToSegmentXZ(feet, tee, basket);
        if (lateral > strictCorridorHalfWidth(currentHole, world, tee, basket, rulesetManager)) {
            return StrictPenaltyType.OB;
        }

        return StrictPenaltyType.NONE;
    }

    private static int strictCorridorHalfWidth(Hole hole, ServerWorld world, BlockPos tee, BlockPos basket, TournamentRulesetManager rulesetManager) {
        int maxSegmentWidth = 0;
        for (var segment : hole.fairwaySegments()) {
            maxSegmentWidth = Math.max(maxSegmentWidth, segment.width());
        }
        int baseHalf = Math.max(3, maxSegmentWidth / 2);
        int baseline = Math.max(rulesetManager.strictCorridorMinimumHalfWidthBlocks(), baseHalf + rulesetManager.strictCorridorBasePaddingBlocks());

        int directCarryGap = computeLongestWaterCarryGap(world, tee, basket);
        if (directCarryGap > rulesetManager.strictAltRouteCarryTriggerBlocks()) {
            return Math.max(baseline, rulesetManager.strictAltRouteHalfWidthBlocks());
        }

        return baseline;
    }

    private static int computeLongestWaterCarryGap(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int dominant = Math.max(Math.abs(dx), Math.abs(dz));
        if (dominant == 0) {
            return 0;
        }

        int stepX = Integer.signum(dx);
        int stepZ = Integer.signum(dz);

        int longest = 0;
        int current = 0;

        int x = start.getX();
        int z = start.getZ();
        int errX = 0;
        int errZ = 0;

        for (int i = 0; i <= dominant; i++) {
            if (isWaterCarryColumn(world, x, z)) {
                current++;
                if (current > longest) {
                    longest = current;
                }
            } else if (current > 0) {
                current = 0;
            }

            errX += Math.abs(dx);
            if (errX >= dominant && stepX != 0) {
                x += stepX;
                errX -= dominant;
            }
            errZ += Math.abs(dz);
            if (errZ >= dominant && stepZ != 0) {
                z += stepZ;
                errZ -= dominant;
            }
        }

        return longest;
    }

    private static boolean isWaterCarryColumn(ServerWorld world, int x, int z) {
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY < world.getBottomY()) {
            return false;
        }

        BlockPos surface = new BlockPos(x, surfaceY, z);
        if (!world.getFluidState(surface).isIn(FluidTags.WATER)) {
            return false;
        }

        return !hasAnySafeLandingNearby(world, surface);
    }

    private static boolean hasAnySafeLandingNearby(ServerWorld world, BlockPos center) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if ((dx * dx) + (dz * dz) > 36) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos feet = new BlockPos(x, y + 1, z);
                if (isStandableFeetBlock(world, feet)) {
                    return true;
                }
            }
        }

        return false;
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
            BlockPos basket,
            TournamentRulesetManager rulesetManager
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

            if (classifyOutType(world, probe, currentHole, tee, basket, rulesetManager) != StrictPenaltyType.NONE) {
                firstOut = probe;
                return new CrossingResolution(lastInBoundsSolid, firstOut);
            }

            if (isStandableFeetBlock(world, probe)) {
                lastInBoundsSolid = probe;
            }
        }

        return new CrossingResolution(lastInBoundsSolid, firstOut);
    }

    private static boolean isSteepSlopeHazard(ServerWorld world, BlockPos feet, int slopeDeltaThreshold) {
        int centerY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) - 1;
        int[] offsets = { -2, 0, 2 };
        for (int dx : offsets) {
            for (int dz : offsets) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int sampleY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX() + dx, feet.getZ() + dz) - 1;
                if (Math.abs(sampleY - centerY) >= slopeDeltaThreshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isDenseRoughHazard(ServerWorld world, BlockPos feet, int threshold) {
        int roughHits = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = feet.getX() + dx;
                int z = feet.getZ() + dz;
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos surface = new BlockPos(x, topY, z);
                BlockState surfaceState = world.getBlockState(surface);
                BlockState headState = world.getBlockState(surface.up());
                if (isRoughMaterial(surfaceState) || isRoughMaterial(headState)) {
                    roughHits++;
                }
            }
        }
        return roughHits >= threshold;
    }

    private static boolean isRoughMaterial(BlockState state) {
        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.LEAVES)
                || state.isOf(Blocks.VINE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.CACTUS);
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

    private record MiniMapTerrainSnapshot(
            int holeIndex,
            int mapOriginX,
            int mapOriginZ,
            int mapSpan,
            int lieAnchorX,
            int lieAnchorZ,
            int serverTick,
            byte[] terrainCells
    ) {
        private MiniMapTerrainSnapshot {
            terrainCells = terrainCells == null ? new byte[0] : terrainCells.clone();
        }
    }

    private record MiniMapPayloadBuildResult(HoleMiniMapSync.Payload payload, MiniMapTerrainSnapshot refreshedTerrain) {
    }

        private record LieMarkerState(
            RegistryKey<net.minecraft.world.World> worldKey,
            BlockPos markerPos,
            BlockState previousGroundState
        ) {
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
