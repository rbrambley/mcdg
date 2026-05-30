package com.mcdg.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HudStateFormatter {
    public Text formatStatus(
            String courseName,
            int hole,
            int par,
            int distanceFeet,
            int throwNumber,
            boolean lastThrowPenalty,
            int holeScore,
            int holeParDelta,
            int totalScore,
            int cumulativePar,
            int cumulativeParDelta,
            String playerStatus,
            String basketHeading,
            int basketDistanceBlocks,
            String signatureLabel
        ) {
        MutableText text = Text.empty();
        text.append(Text.literal("H").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(hole)).formatted(Formatting.GOLD));
        text.append(Text.literal(" P").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(par)).formatted(Formatting.GREEN));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));

        text.append(Text.literal(distanceFeet + "f/" + basketDistanceBlocks + "m").formatted(Formatting.AQUA));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));
        text.append(Text.literal(basketHeading).formatted(Formatting.LIGHT_PURPLE));
        text.append(Text.literal("\n").formatted(Formatting.DARK_GRAY));

        text.append(Text.literal("Th:").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(throwNumber)).formatted(Formatting.WHITE));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));
        text.append(formatThrowFlag(lastThrowPenalty));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));

        text.append(Text.literal("Ho:").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(holeScore)).formatted(Formatting.WHITE));
        text.append(Text.literal("(").formatted(Formatting.GRAY));
        text.append(formatParDelta(holeParDelta));
        text.append(Text.literal(")").formatted(Formatting.GRAY));
        text.append(Text.literal(" ").formatted(Formatting.DARK_GRAY));

        text.append(Text.literal("Rd:").formatted(Formatting.GRAY));
        text.append(Text.literal(Integer.toString(totalScore)).formatted(Formatting.WHITE));
        text.append(Text.literal("/").formatted(Formatting.DARK_GRAY));
        text.append(Text.literal(Integer.toString(cumulativePar)).formatted(Formatting.GREEN));
        text.append(Text.literal("(").formatted(Formatting.GRAY));
        text.append(formatParDelta(cumulativeParDelta));
        text.append(Text.literal(")").formatted(Formatting.GRAY));
        if (!signatureLabel.isEmpty()) {
            text.append(Text.literal("\n").formatted(Formatting.DARK_GRAY));
            text.append(Text.literal("SIGNATURE \u2605 " + signatureLabel).formatted(Formatting.GOLD));
        }
        return text;
    }

    private Text formatThrowFlag(boolean lastThrowPenalty) {
        if (lastThrowPenalty) {
            return Text.literal("⚑").formatted(Formatting.RED);
        }
        return Text.literal("⚑").formatted(Formatting.GREEN);
    }

    public Text formatHoleAdvance(
            int completedHole,
            int completedHoleScore,
            int completedHolePar,
            int totalScore,
            int cumulativePar,
            int nextHole
    ) {
        int holeDelta = completedHoleScore - completedHolePar;
        String resultName = golfResultName(completedHoleScore, holeDelta);
        Formatting resultColor = holeDelta <= 0 ? Formatting.GREEN : Formatting.RED;
        return Text.literal(resultName).formatted(resultColor, Formatting.BOLD);
    }

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

    private Text formatGolfResultName(int holeScore, int holeDelta) {
        if (holeScore == 1) {
            return Text.literal("Ace").formatted(Formatting.AQUA);
        }
        if (holeDelta == -3) {
            return Text.literal("Albatross").formatted(Formatting.AQUA);
        }
        if (holeDelta <= -4) {
            return Text.literal("Three or Better").formatted(Formatting.AQUA);
        }
        if (holeDelta == -2) {
            return Text.literal("Eagle").formatted(Formatting.AQUA);
        }
        if (holeDelta == -1) {
            return Text.literal("Birdie").formatted(Formatting.GREEN);
        }
        if (holeDelta == 0) {
            return Text.literal("Par").formatted(Formatting.WHITE);
        }
        if (holeDelta == 1) {
            return Text.literal("Bogey").formatted(Formatting.GOLD);
        }
        if (holeDelta == 2) {
            return Text.literal("Double Bogey").formatted(Formatting.RED);
        }
        if (holeDelta == 3) {
            return Text.literal("Triple Bogey").formatted(Formatting.RED);
        }
        return Text.literal("+" + holeDelta + " Bogey").formatted(Formatting.DARK_RED);
    }

    private String golfResultName(int holeScore, int holeDelta) {
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
