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
    static final String KEY_SCORECARD = "McdgScorecard";
    static final String KEY_COURSE_NAME = "courseName";
    static final String KEY_HOLES = "holes";
    static final String KEY_HOLE_INDEX = "index";
    static final String KEY_DISTANCE_FEET = "distanceFeet";
    static final String KEY_PAR = "par";
    static final String KEY_SCORE = "score";
    private static final ConcurrentMap<UUID, NbtCompound> SCORECARD_BY_PLAYER = new ConcurrentHashMap<>();

    private ScorecardManager() {
    }

    public static void initializeScorecard(ServerPlayerEntity player, Course course) {
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
            row.putInt(KEY_DISTANCE_FEET, hole.distanceFeet());
            row.putInt(KEY_PAR, hole.par());
            row.putInt(KEY_SCORE, -1);
            holes.add(row);
        }
        root.put(KEY_HOLES, holes);
        SCORECARD_BY_PLAYER.put(player.getUuid(), root.copy());

        setScorecardRoot(stack, root);
        if (!hadExisting) {
            player.giveItemStack(stack);
        }
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

    static NbtCompound getScorecardRoot(ItemStack stack) {
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
