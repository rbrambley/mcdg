package com.mcdg.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HudStateFormatter {
    public Text formatRoundComplete(int totalScore, int totalPar) {
        int finalDelta = totalScore - totalPar;

        MutableText text = Text.empty();
        text.append(Text.literal("Round complete | Final Throws ").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(totalScore)).formatted(Formatting.WHITE));
        text.append(Text.literal("/").formatted(Formatting.DARK_GRAY));
        text.append(Text.literal(Integer.toString(totalPar)).formatted(Formatting.GREEN));
        text.append(Text.literal(" ").formatted(Formatting.GRAY));
        text.append(formatParDelta(finalDelta));
        text.append(Text.literal(" | Result ").formatted(Formatting.GRAY));
        text.append(formatRoundResultName(finalDelta));
        return text;
    }

    public Text formatRoundSummaryHeader(int totalPar, int completedPlayers) {
        MutableText text = Text.empty();
        text.append(Text.literal("Round Leaderboard").formatted(Formatting.GOLD));
        text.append(Text.literal(" | Par ").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(totalPar)).formatted(Formatting.GREEN));
        text.append(Text.literal(" | Finished ").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(completedPlayers)).formatted(Formatting.WHITE));
        return text;
    }

    public Text formatRoundSummaryEntry(int rank, String playerName, int totalScore, int totalPar) {
        int delta = totalScore - totalPar;
        MutableText text = Text.empty();
        text.append(Text.literal("#" + rank + " ").formatted(Formatting.GRAY));
        text.append(Text.literal(playerName).formatted(Formatting.AQUA));
        text.append(Text.literal(" ").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(totalScore)).formatted(Formatting.WHITE));
        text.append(Text.literal("/").formatted(Formatting.DARK_GRAY));
        text.append(Text.literal(Integer.toString(totalPar)).formatted(Formatting.GREEN));
        text.append(Text.literal(" ").formatted(Formatting.GRAY));
        text.append(formatParDelta(delta));
        return text;
    }

    private Text formatParDelta(int delta) {
        if (delta == 0) {
            return Text.literal("E").formatted(Formatting.WHITE);
        }
        if (delta < 0) {
            return Text.literal(Integer.toString(delta)).formatted(Formatting.GREEN);
        }
        return Text.literal("+" + delta).formatted(Formatting.RED);
    }

    private Text formatRoundResultName(int finalDelta) {
        if (finalDelta < 0) {
            return Text.literal("Under Par").formatted(Formatting.GREEN);
        }
        if (finalDelta == 0) {
            return Text.literal("Even Par").formatted(Formatting.WHITE);
        }
        return Text.literal("Over Par").formatted(Formatting.RED);
    }
}
