package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Handles player discovery of challenge courses through world interaction.
 */
public final class ChallengeCourseDiscoveryHandler {
    private ChallengeCourseDiscoveryHandler() {}

    /**
     * Called when a player interacts with a block that might be a course entrance.
     */
    public static void onBlockInteract(PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // Check if this is a chest (potential course entrance)
        if (state.isOf(Blocks.CHEST)) {
            handleChestInteraction(serverPlayer, world, pos);
        }
    }

    /**
     * Handles chest interaction to discover courses.
     */
    private static void handleChestInteraction(ServerPlayerEntity player, ServerWorld world, BlockPos chestPos) {
        if (!(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)) {
            return;
        }

        // Find the corresponding lost course
        LostCourse course = findCourseByEntrance(chestPos.down());
        if (course == null) {
            return;
        }

        // Check if player has already claimed discovery rewards
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isPresent() && catalog.get().hasClaimedDiscoveryRewards(course.courseId(), player.getUuid())) {
            player.sendMessage(Text.literal("You've already claimed the discovery rewards for this course.")
                .formatted(Formatting.GRAY));
            return;
        }

        // Check if chest contains a map fragment
        for (int i = 0; i < chest.size(); i++) {
            ItemStack stack = chest.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }

            // Check if this is a map fragment (has custom name containing "Map Fragment")
            Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (name != null && name.getString().contains("Map Fragment")) {
                ChallengeCourseManager.onCourseDiscovery(player, course.courseId());
                chest.setStack(i, ItemStack.EMPTY);
                player.sendMessage(Text.literal("The map fragment reveals the location of " + course.name() + "!")
                    .formatted(Formatting.GREEN));
                return;
            }
        }
    }

    /**
     * Finds a lost course by its entrance position.
     */
    private static LostCourse findCourseByEntrance(BlockPos entrancePos) {
        for (LostCourse course : ChallengeCourseManager.getAllLostCourses()) {
            if (course.entrancePosition().equals(entrancePos)) {
                return course;
            }
        }
        return null;
    }

    /**
     * Called when a player breaks a block that might be a course entrance marker.
     */
    public static void onBlockBreak(PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        // Check if this is mossy cobblestone (potential entrance marker)
        if (state.isOf(Blocks.MOSSY_COBBLESTONE)) {
            handleMarkerBreak(serverPlayer, world, pos);
        }
    }

    /**
     * Handles breaking of entrance marker to discover courses.
     */
    private static void handleMarkerBreak(ServerPlayerEntity player, ServerWorld world, BlockPos markerPos) {
        LostCourse course = findCourseByEntrance(markerPos);
        if (course == null) {
            return;
        }

        // Check if player has already claimed discovery rewards
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isPresent() && catalog.get().hasClaimedDiscoveryRewards(course.courseId(), player.getUuid())) {
            player.sendMessage(Text.literal("You've already claimed the discovery rewards for this course.")
                .formatted(Formatting.GRAY));
            return;
        }

        ChallengeCourseManager.onCourseDiscovery(player, course.courseId());
        player.sendMessage(Text.literal("The ancient marker points to " + course.name() + "!")
            .formatted(Formatting.GREEN));
    }

    /**
     * Manually discovers a course by ID (for admin/debug purposes).
     */
    public static void discoverCourseById(ServerPlayerEntity player, UUID courseId) {
        ChallengeCourseManager.onCourseDiscovery(player, courseId);
    }
}