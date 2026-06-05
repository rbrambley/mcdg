package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.net.WaypointSync;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class WaypointCommands {
    private WaypointCommands() {
    }

    public static int executeWaypointClear(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("waypoint clear must be run by a player."));
            return 0;
        }
        WaypointSync.clear(player);
        source.sendFeedback(() -> Text.literal("All personal waypoints cleared from server. Use your minimap key to re-add them.").formatted(Formatting.GRAY), false);
        return 1;
    }

    public static int executeWaypointList(
            ServerCommandSource source,
            ActiveCourseManager courseManager
    ) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);

        if (placed != null && !source.getWorld().getRegistryKey().equals(placed.worldKey())) {
            placed = null;
        }

        List<WaypointTarget> targets = collectWaypointTargets(source, placed, placed != null && courseManager.isRoundActive());
        if (targets.isEmpty()) {
            source.sendError(Text.literal("No waypoints found. Add personal waypoints in-game first, or run /mcdg startround to see course waypoints."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Waypoint targets:"), false);
        for (int i = 0; i < targets.size(); i++) {
            WaypointTarget target = targets.get(i);
            BlockPos anchor = target.anchor();
            int displayIndex = i + 1;
            source.sendFeedback(
                    () -> Text.literal(displayIndex + ". " + target.name() + " -> (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ")"),
                    false
            );
        }
        source.sendFeedback(() -> Text.literal("Use /mcdg waypoint tp <target or number>. Examples: /mcdg waypoint tp 2, /mcdg waypoint tp central, /mcdg waypoint tp hole 3"), false);
        return completePlayerFacingLegacyCommand(source, "waypoints");
    }

    public static int executeWaypointTeleportPrompt(
            ServerCommandSource source,
            ActiveCourseManager courseManager
    ) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);

        if (placed != null && !source.getWorld().getRegistryKey().equals(placed.worldKey())) {
            placed = null;
        }

        List<WaypointTarget> targets = collectWaypointTargets(source, placed, placed != null && courseManager.isRoundActive());
        if (targets.isEmpty()) {
            source.sendError(Text.literal("No waypoints found. Add personal waypoints in-game first, or run /mcdg startround to see course waypoints."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Waypoint teleport prompt:"), false);
        for (int i = 0; i < targets.size(); i++) {
            WaypointTarget target = targets.get(i);
            BlockPos anchor = target.anchor();
            int displayIndex = i + 1;
            String command = "/mcdg waypoint tp " + displayIndex;
            source.sendFeedback(
                    () -> Text.literal(displayIndex + ". " + target.name() + " -> (" + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ() + ")")
                            .styled(style -> style
                                    .withColor(Formatting.AQUA)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + command)))
                            ),
                    false
            );
        }
        source.sendFeedback(() -> Text.literal("Pick one: /mcdg waypoint tp <number> (example: /mcdg waypoint tp 2)"), false);
        return completePlayerFacingLegacyCommand(source, "waypoints");
    }

    public static int executeWaypointTeleport(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            String targetInput
    ) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);

        if (placed != null && !source.getWorld().getRegistryKey().equals(placed.worldKey())) {
            placed = null;
        }

        List<WaypointTarget> targets = collectWaypointTargets(source, placed, placed != null && courseManager.isRoundActive());
        if (targets.isEmpty()) {
            source.sendError(Text.literal("No waypoints found. Add personal waypoints in-game first, or run /mcdg startround to see course waypoints."));
            return 0;
        }

        WaypointTarget selected = resolveWaypointTarget(targets, targetInput);
        if (selected == null) {
            source.sendError(Text.literal("Unknown waypoint target '" + targetInput + "'. Try /mcdg waypoint list."));
            return 0;
        }

        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            ServerWorld world = source.getWorld();
            BlockPos anchor = selected.anchor();
            BlockPos safe = resolveSafeFeetNear(world, anchor);
            player.teleport(safe.getX() + 0.5, safe.getY() + 1.0, safe.getZ() + 0.5);
            source.sendFeedback(() -> Text.literal("Teleported to " + selected.name() + "."), false);
            return completePlayerFacingLegacyCommand(source, "waypoints");
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int completePlayerFacingLegacyCommand(ServerCommandSource source, String submenu) {
        MenuCommands.sendBackToMenu(source);
        return 1;
    }

    private static List<WaypointTarget> collectWaypointTargets(ServerCommandSource source, PlacedCourseState placed, boolean includeHoleWaypoints) {
        List<WaypointTarget> targets = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (placed != null) {
            BlockPos holeOneTee = placed.holeTees().get(1);
            BlockPos holeOneBasket = placed.holeBaskets().get(1);
            if (holeOneTee != null) {
                addWaypointTarget(targets, seenNames, "Tournament Central", resolveTournamentCentralAnchor(holeOneTee, holeOneBasket));
            }
        }

        if (includeHoleWaypoints && placed != null) {
            int maxHole = 0;
            for (Integer holeIndex : placed.holeTees().keySet()) {
                if (holeIndex != null) {
                    maxHole = Math.max(maxHole, holeIndex);
                }
            }
            for (Integer holeIndex : placed.holeBaskets().keySet()) {
                if (holeIndex != null) {
                    maxHole = Math.max(maxHole, holeIndex);
                }
            }

            for (int hole = 1; hole <= maxHole; hole++) {
                BlockPos tee = placed.holeTees().get(hole);
                if (tee == null) {
                    continue;
                }
                BlockPos basket = placed.holeBaskets().get(hole);
                addWaypointTarget(targets, seenNames, "Hole " + hole, resolveHoleWaypointAnchor(tee, basket));
            }
        }

        if (source != null && source.getEntity() instanceof ServerPlayerEntity player) {
            String dimensionId = source.getWorld().getRegistryKey().getValue().toString();
            for (WaypointSync.WaypointEntry waypoint : WaypointSync.getWaypoints(player)) {
                if (!dimensionId.equals(waypoint.dimensionId())) {
                    continue;
                }
                String name = waypoint.name() == null ? "" : waypoint.name().trim();
                if (name.isBlank()) {
                    continue;
                }
                addWaypointTarget(targets, seenNames, name, resolveClientWaypointAnchor(source.getWorld(), waypoint.x(), waypoint.y(), waypoint.z()));
            }
        }

        return targets;
    }

    private static void addWaypointTarget(List<WaypointTarget> targets, Set<String> seenNames, String name, BlockPos anchor) {
        if (name == null || name.isBlank() || anchor == null) {
            return;
        }

        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (!seenNames.add(normalized)) {
            return;
        }

        targets.add(new WaypointTarget(name, anchor));
    }

    private static BlockPos resolveClientWaypointAnchor(ServerWorld world, int x, int y, int z) {
        world.getChunk(x >> 4, z >> 4);

        int resolvedY = y;
        int floor = world.getBottomY() + 2;
        if (resolvedY <= floor || resolvedY == WaypointSync.UNKNOWN_Y) {
            resolvedY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        if (resolvedY <= floor) {
            resolvedY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        }
        if (resolvedY <= floor) {
            resolvedY = world.getSeaLevel();
        }

        return resolveSafeFeetNear(world, new BlockPos(x, resolvedY, z));
    }

    private static WaypointTarget resolveWaypointTarget(List<WaypointTarget> targets, String targetInput) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        String normalized = targetInput == null ? "" : targetInput.trim().toLowerCase();
        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.equals("central")
                || normalized.equals("tournament")
                || normalized.equals("tournament central")
                || normalized.equals("course")
                || normalized.equals("tc")) {
            for (WaypointTarget target : targets) {
                if (target.name().equals("Tournament Central")) {
                    return target;
                }
            }
        }

        try {
            int numeric = Integer.parseInt(normalized);
            if (numeric >= 1 && numeric <= targets.size()) {
                return targets.get(numeric - 1);
            }

            String holeName = "Hole " + numeric;
            for (WaypointTarget target : targets) {
                if (target.name().equalsIgnoreCase(holeName)) {
                    return target;
                }
            }
        } catch (NumberFormatException ignored) {
        }

        if (normalized.startsWith("hole ")) {
            for (WaypointTarget target : targets) {
                if (target.name().toLowerCase().equals(normalized)) {
                    return target;
                }
            }
        }

        for (WaypointTarget target : targets) {
            if (target.name().equalsIgnoreCase(targetInput.trim())) {
                return target;
            }
        }
        return null;
    }

    private static BlockPos resolveTournamentCentralAnchor(BlockPos teeAnchor, BlockPos basketAnchor) {
        if (teeAnchor == null) {
            return BlockPos.ORIGIN;
        }
        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0] * 12, 0, back[1] * 12);
    }

    private static BlockPos resolveHoleWaypointAnchor(BlockPos teeAnchor, BlockPos basketAnchor) {
        if (teeAnchor == null) {
            return BlockPos.ORIGIN;
        }
        int[] back = resolveBackCardinal(teeAnchor, basketAnchor);
        return teeAnchor.add(back[0], 0, back[1]);
    }

    private static int[] resolveBackCardinal(BlockPos teeAnchor, BlockPos basketAnchor) {
        if (teeAnchor == null || basketAnchor == null) {
            return new int[] { 0, -1 };
        }

        int dx = basketAnchor.getX() - teeAnchor.getX();
        int dz = basketAnchor.getZ() - teeAnchor.getZ();
        if (dx == 0 && dz == 0) {
            return new int[] { 0, -1 };
        }

        if (Math.abs(dx) >= Math.abs(dz)) {
            return new int[] { -Integer.compare(dx, 0), 0 };
        }
        return new int[] { 0, -Integer.compare(dz, 0) };
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

    private record WaypointTarget(String name, BlockPos anchor) {
    }
}
