package com.mcdg.game;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ScorecardManager {
    public static final String KEY_SCORECARD = "McdgScorecard";
    public static final String KEY_COURSE_NAME = "courseName";
    public static final String KEY_PLAYER_NAME = "playerName";
    public static final String KEY_HOLES = "holes";
    public static final String KEY_HOLE_INDEX = "index";
    public static final String KEY_DISTANCE_FEET = "distanceFeet";
    public static final String KEY_PAR = "par";
    public static final String KEY_SCORE = "score";
    public static final String KEY_SIGNATURE = "signature";
    private static final ConcurrentMap<UUID, NbtCompound> SCORECARD_BY_PLAYER = new ConcurrentHashMap<>();

    private ScorecardManager() {
    }

    public static void initializeScorecard(ServerPlayerEntity player, Course course) {
        initializeScorecard(player, course, null);
    }

    public static void initializeScorecard(ServerPlayerEntity player, Course course, PlacedCourseState placedCourseState) {
        ItemStack stack = findScorecard(player);
        boolean hadExisting = !stack.isEmpty();
        if (!hadExisting) {
            stack = new ItemStack(McdgItems.SCORECARD, 1);
        }

        NbtCompound root = new NbtCompound();
        root.putString(KEY_COURSE_NAME, course.name());

        NbtList holes = new NbtList();
        for (Hole hole : course.holes()) {
            NbtCompound row = new NbtCompound();
            row.putInt(KEY_HOLE_INDEX, hole.index());
            row.putInt(KEY_DISTANCE_FEET, resolveScorecardDistanceFeet(hole, placedCourseState));
            row.putInt(KEY_PAR, hole.par());
            row.putInt(KEY_SCORE, -1);
            row.putBoolean(KEY_SIGNATURE, hole.isSignature());
            holes.add(row);
        }
        root.put(KEY_HOLES, holes);
        SCORECARD_BY_PLAYER.put(player.getUuid(), root.copy());

        setScorecardRoot(stack, root);
        if (!hadExisting) {
            player.giveItemStack(stack);
        }
    }

    private static int resolveScorecardDistanceFeet(Hole hole, PlacedCourseState placedCourseState) {
        if (placedCourseState == null) {
            return hole.distanceFeet();
        }

        var tee = placedCourseState.holeTees().get(hole.index());
        var basket = placedCourseState.holeBaskets().get(hole.index());
        if (tee == null || basket == null) {
            return hole.distanceFeet();
        }

        double dx = (basket.getX() + 0.5) - (tee.getX() + 0.5);
        double dy = (basket.getY() + 0.5) - (tee.getY() + 0.5);
        double dz = (basket.getZ() + 0.5) - (tee.getZ() + 0.5);
        int meters = Math.max(0, (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        return Math.max(0, Math.round(meters * 3.28084f));
    }

    public static void recordHoleScore(ServerPlayerEntity player, int holeIndex, int score) {
        ItemStack stack = findScorecard(player);
        if (stack.isEmpty()) {
            return;
        }

        NbtCompound root = getScorecardRoot(stack);
        if (root == null) {
            return;
        }

        NbtList holes = root.getList(KEY_HOLES, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < holes.size(); i++) {
            NbtCompound row = holes.getCompound(i);
            if (row.getInt(KEY_HOLE_INDEX) != holeIndex) {
                continue;
            }
            row.putInt(KEY_SCORE, score);
            break;
        }

        root.put(KEY_HOLES, holes);
        SCORECARD_BY_PLAYER.put(player.getUuid(), root.copy());
        setScorecardRoot(stack, root);
    }

    /**
     * Stores the player name on the scorecard when the round is completed,
     * turning the scorecard into a souvenir.
     */
    public static void recordCompletionPlayer(ServerPlayerEntity player) {
        ItemStack stack = findScorecard(player);
        if (stack.isEmpty()) {
            return;
        }

        NbtCompound root = getScorecardRoot(stack);
        if (root == null) {
            return;
        }

        root.putString(KEY_PLAYER_NAME, player.getGameProfile().getName());
        SCORECARD_BY_PLAYER.put(player.getUuid(), root.copy());
        setScorecardRoot(stack, root);
    }

    public static void ensureScorecardInInventory(ServerPlayerEntity player) {
        NbtCompound root = SCORECARD_BY_PLAYER.get(player.getUuid());
        if (root == null) {
            return;
        }

        ItemStack stack = findScorecard(player);
        if (stack.isEmpty()) {
            stack = new ItemStack(McdgItems.SCORECARD, 1);
            setScorecardRoot(stack, root.copy());
            player.giveItemStack(stack);
            return;
        }

        setScorecardRoot(stack, root.copy());
    }

    public static NbtCompound getScorecardRoot(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        NbtCompound customNbt = customData.copyNbt();
        if (!customNbt.contains(KEY_SCORECARD)) {
            return null;
        }
        return customNbt.getCompound(KEY_SCORECARD);
    }

    private static void setScorecardRoot(ItemStack stack, NbtCompound root) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.put(KEY_SCORECARD, root.copy()));
    }

    private static ItemStack findScorecard(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(McdgItems.SCORECARD)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
