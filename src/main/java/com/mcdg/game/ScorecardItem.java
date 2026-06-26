package com.mcdg.game;

import java.util.List;
import net.minecraft.client.item.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ScorecardItem extends Item {
    public ScorecardItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtCompound root = ScorecardManager.getScorecardRoot(stack);
        if (root == null) {
            tooltip.add(Text.translatable("tooltip.mcdg.scorecard.empty").formatted(Formatting.DARK_GRAY));
            return;
        }

        String courseName = root.getString(ScorecardManager.KEY_COURSE_NAME);
        if (!courseName.isBlank()) {
            tooltip.add(Text.literal(courseName).formatted(Formatting.AQUA));
        }

        String playerName = root.getString(ScorecardManager.KEY_PLAYER_NAME);
        if (!playerName.isBlank()) {
            tooltip.add(Text.translatable("tooltip.mcdg.scorecard.completed_by", playerName)
                    .formatted(Formatting.GOLD));
        }

        tooltip.add(Text.literal(String.format("%-2s %-6s %-3s %-5s", "H", "Dist", "Par", "Score")).formatted(Formatting.GRAY));

        NbtList holes = root.getList(ScorecardManager.KEY_HOLES, NbtElement.COMPOUND_TYPE);
        int totalScore = 0;
        int totalPar = 0;
        int completedHoles = 0;
        for (int i = 0; i < holes.size(); i++) {
            NbtCompound row = holes.getCompound(i);
            int index = row.getInt(ScorecardManager.KEY_HOLE_INDEX);
            int distanceFeet = row.getInt(ScorecardManager.KEY_DISTANCE_FEET);
            int par = row.getInt(ScorecardManager.KEY_PAR);
            int score = row.getInt(ScorecardManager.KEY_SCORE);
            boolean signature = row.getBoolean(ScorecardManager.KEY_SIGNATURE);

            String scoreText = score < 0 ? "-" : Integer.toString(score);
            String holeLabel = signature ? ("S" + index) : Integer.toString(index);
            tooltip.add(Text.literal(String.format("%-2s %-6s %-3d %-5s", holeLabel, distanceFeet + "ft", par, scoreText))
                    .formatted(signature ? Formatting.YELLOW : Formatting.WHITE));

            if (score >= 0) {
                totalScore += score;
                totalPar += par;
                completedHoles++;
            }
        }

        if (completedHoles > 0) {
            int delta = totalScore - totalPar;
            String deltaText;
            if (delta == 0) {
                deltaText = "E";
            } else if (delta > 0) {
                deltaText = "+" + delta;
            } else {
                deltaText = Integer.toString(delta);
            }

            tooltip.add(Text.literal(String.format("%-2s %-6s %-3s %-5s", "Tot", totalScore + "/" + totalPar, "", deltaText)).formatted(Formatting.GOLD));
        }

        tooltip.add(Text.literal("Completed: " + completedHoles + "/" + holes.size()).formatted(Formatting.DARK_GRAY));
    }
}
