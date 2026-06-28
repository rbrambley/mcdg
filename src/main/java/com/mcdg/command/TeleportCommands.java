package com.mcdg.command;

import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.RoundStateManager;
import com.mcdg.world.ResortWaypointManager;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public final class TeleportCommands {
    private TeleportCommands() {
    }

    public static int executeGotoCourse(ServerCommandSource source, ActiveCourseManager courseManager) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            source.sendError(Text.literal("No placed course found. Run /mcdg startround first."));
            return 0;
        }

        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Hole 1 tee location is unavailable."));
            return 0;
        }

        try {
            var player = source.getPlayerOrThrow();
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

    public static int executeGotoLie(ServerCommandSource source, RoundStateManager roundStateManager) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            Optional<BlockPos> relocated = HoleProgressTracker.relocatePlayerToSafeLie(player, roundStateManager);
            if (relocated.isEmpty()) {
                player.sendMessage(Text.literal("No active lie found to teleport to."), true);
                return 0;
            }

            BlockPos lie = relocated.get();
            player.sendMessage(
                    Text.literal("Teleported to lie: " + lie.getX() + ", " + lie.getY() + ", " + lie.getZ())
                            .formatted(Formatting.GREEN),
                    true
            );
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }

    public static int executeResortTeleport(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            var resort = ResortWaypointManager.getResortWaypoint().orElse(null);
            if (resort == null) {
                source.sendError(Text.literal("No resort has been built yet."));
                return 0;
            }
            String playerDimension = player.getWorld().getRegistryKey().getValue().toString();
            if (!playerDimension.equals(resort.dimensionId())) {
                source.sendError(Text.literal("Resort is in a different dimension. Use a portal to return."));
                return 0;
            }
            ServerWorld world = player.getServerWorld();
            BlockPos target = new BlockPos(resort.x(), resort.y(), resort.z()).south(4);
            BlockPos safe = CommandUtils.resolveSafeFeetNear(world, target);
            player.teleport(world, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("Teleported to MCDG Resort!").formatted(Formatting.GREEN), false);
            return 1;
        } catch (Exception ex) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }
    }
}
