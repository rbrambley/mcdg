package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.world.CoursePlacementValidator;
import java.util.List;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public final class PlacementValidationCommand {
    private PlacementValidationCommand() {}

    static int executeValidatePlacement(
        ServerCommandSource source,
        ActiveCourseManager courseManager,
        CoursePlacementValidator placementValidator
    ) {
        Course course = courseManager.getActiveCourse().orElse(null);
        if (course == null) {
            source.sendError(Text.literal("No active course. Run /mcdg createcourse <seed> and /mcdg startround first."));
            return 0;
        }

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            source.sendError(Text.literal("No placed course found. Run /mcdg startround first."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Placed course world is unavailable for validation."));
            return 0;
        }

        CoursePlacementValidator.ValidationReport report = placementValidator.validatePlacedCourse(world, course, placed, "active-course");
        int invalidHoles = report.metrics().getOrDefault("invalid_holes", 0);
        int warningLandingGaps = report.metrics().getOrDefault("warning_landing_gaps", 0);
        int maxLandingGap = report.metrics().getOrDefault("max_landing_gap", 0);
        int landingGapWarningThreshold = report.metrics().getOrDefault("landing_gap_warning_threshold", 95);
        int landingGapFailThreshold = report.metrics().getOrDefault("landing_gap_fail_threshold", 110);
        source.sendFeedback(() -> Text.literal(
                "Validation " + (report.passed() ? "PASSED" : "FAILED")
                        + " | holes=" + report.metrics().getOrDefault("total_holes", 0)
                        + ", invalid=" + invalidHoles
                        + ", issues=" + report.issueCount()
                        + ", warningLandingGaps=" + warningLandingGaps
                        + ", maxLandingGap=" + maxLandingGap
                        + " (warn>" + landingGapWarningThreshold + ", fail>" + landingGapFailThreshold + ")"
                        + ", biome=" + report.biome()
        ), true);

        int maxIssueLines = 8;
        List<CoursePlacementValidator.ValidationIssue> issues = report.issues();
        for (int i = 0; i < issues.size() && i < maxIssueLines; i++) {
            CoursePlacementValidator.ValidationIssue issue = issues.get(i);
            String posText = issue.position() == null
                    ? ""
                    : (" @ " + issue.position().getX() + " " + issue.position().getY() + " " + issue.position().getZ());
            source.sendFeedback(() -> Text.literal(
                    " - H" + issue.holeIndex() + " [" + issue.code() + "] " + issue.message() + posText
            ), false);
        }

        if (issues.size() > maxIssueLines) {
            int remaining = issues.size() - maxIssueLines;
            source.sendFeedback(() -> Text.literal(" - ... and " + remaining + " more issues."), false);
        }

        return report.passed() ? 1 : 0;
    }
}
