package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.LieMarkerService;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.AutoCourseService;
import com.mcdg.game.RoundStateManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

public final class PlacementAutoTestService {
    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String AUTOTEST_SHUTDOWN_ENV = "MCDG_AUTOTEST_SHUTDOWN";
    private static final String AUTOTEST_SHADOW_SURFACE_ENV = "MCDG_AUTOTEST_SHADOW_SURFACE_RULE";

    private final CourseGenerator generator;
    private final AutoCourseService autoCourseService;
    private final CoursePlacementService placementService;
    private final CoursePlacementValidator placementValidator;
    private final ActiveCourseManager courseManager;
    private final RoundStateManager roundStateManager;

    private AutoTestSession activeSession;
    private Boolean shadowSurfaceRuleOverride;

    public PlacementAutoTestService(
            CourseGenerator generator,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        this(generator, placementService, placementValidator, courseManager, roundStateManager, null);
    }

    public PlacementAutoTestService(
            CourseGenerator generator,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            AutoCourseService autoCourseService
    ) {
        this.generator = generator;
        this.autoCourseService = autoCourseService;
        this.placementService = placementService;
        this.placementValidator = placementValidator;
        this.courseManager = courseManager;
        this.roundStateManager = roundStateManager;
    }

    public int start(ServerCommandSource source, int runs, int holes) {
        return start(source, runs, holes, null);
    }

    public int start(ServerCommandSource source, int runs, int holes, Long baseSeedOverride) {
        if (courseManager.isRoundActive()) {
            source.sendError(Text.literal("End the active round before running placement automation."));
            return 0;
        }
        if (activeSession != null) {
            source.sendError(Text.literal("Autotest is already running. Use /mcdg cancelautotest first."));
            return 0;
        }

        ServerWorld world = source.getWorld();
        BlockPos origin = BlockPos.ofFloored(source.getPosition());
        // Keep automation near spawn/player area to avoid watchdog crashes from far-chunk generation.
        List<BlockPos> biomeAnchors = collectBiomeAnchors(world, origin, 512, 96, 4);
        if (biomeAnchors.isEmpty()) {
            biomeAnchors = List.of(origin);
        }

        ServerBossBar progressBar = new ServerBossBar(
                Text.literal("Autotest placement: 0/" + runs + " runs"),
                BossBar.Color.BLUE,
                BossBar.Style.PROGRESS
        );
        progressBar.setPercent(0.0f);

        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey().equals(world.getRegistryKey())) {
                progressBar.addPlayer(player);
            }
        }

        long resolvedBaseSeed = baseSeedOverride == null ? System.currentTimeMillis() : baseSeedOverride;
        boolean shadowSurfaceRuleMode = isShadowSurfaceRuleEnabledEffective();
        activeSession = new AutoTestSession(source, world, runs, holes, biomeAnchors, progressBar, resolvedBaseSeed, shadowSurfaceRuleMode);
        LieMarkerService.beginAutotestLieMarkerTrail();

        String startMessage = "Starting autotest: runs=" + runs
                + ", holes=" + holes
                + ", biomeAnchors=" + biomeAnchors.size()
                + ", baseSeed=" + resolvedBaseSeed
            + (shadowSurfaceRuleMode ? ", shadowSurfaceRule=true" : "")
                + ". Use /mcdg cancelautotest to stop.";
        source.sendFeedback(() -> Text.literal(startMessage), true);
        return 1;
    }

    public int cancel(ServerCommandSource source) {
        if (activeSession == null) {
            source.sendError(Text.literal("No autotest is currently running."));
            return 0;
        }

        activeSession.cancelRequested = true;
        source.sendFeedback(() -> Text.literal("Autotest cancellation requested. Finishing current run..."), true);
        return 1;
    }

    public boolean isShadowSurfaceRuleOverrideSet() {
        return shadowSurfaceRuleOverride != null;
    }

    public boolean isShadowSurfaceRuleEnabledNow() {
        return isShadowSurfaceRuleEnabledEffective();
    }

    public void setShadowSurfaceRuleOverride(boolean enabled) {
        shadowSurfaceRuleOverride = enabled;
    }

    public void clearShadowSurfaceRuleOverride() {
        shadowSurfaceRuleOverride = null;
    }

    public void tick(MinecraftServer server) {
        if (activeSession == null) {
            return;
        }
        if (!server.isOnThread()) {
            return;
        }

        AutoTestSession session = activeSession;
        if (session.cancelRequested) {
            finishSession(session, true);
            return;
        }

        if (session.completedRuns >= session.runs) {
            finishSession(session, false);
            return;
        }

        runSingleIteration(session);

        if (session.cancelRequested) {
            finishSession(session, true);
            return;
        }

        if (session.completedRuns >= session.runs) {
            finishSession(session, false);
        }
    }

    private void runSingleIteration(AutoTestSession session) {
        int runNumber = session.completedRuns + 1;
        long seed = session.baseSeed + (session.completedRuns * 7919L);
        BlockPos runOrigin = session.biomeAnchors.get(session.completedRuns % session.biomeAnchors.size());

        updateBossBar(session, "Autotest placement: run " + runNumber + "/" + session.runs
                + " (pass=" + session.passRuns + ", fail=" + session.failRuns + ")");

        Course course;
        try {
            course = generator.generate(seed, session.holes);
        } catch (RuntimeException ex) {
            ScenarioOutcome failed = ScenarioOutcome.failure(runNumber, seed, ex.getMessage());
            applyScenarioOutcome(session, failed, false);
            if (session.shadowSurfaceRuleMode) {
                applyScenarioOutcome(session, failed, true);
            }
            session.completedRuns++;
            float pctFailed = Math.min(1.0f, session.completedRuns / (float) Math.max(1, session.runs));
            session.progressBar.setPercent(pctFailed);
            return;
        }

        ScenarioOutcome baseline = executeScenario(session, runNumber, seed, runOrigin, course, false);
        applyScenarioOutcome(session, baseline, false);

        if (session.shadowSurfaceRuleMode) {
            ScenarioOutcome shadow = executeScenario(session, runNumber, seed, runOrigin, course, true);
            applyScenarioOutcome(session, shadow, true);
        }

        // Also run an AutoCourseService scenario if available, to validate the autocourse build path.
        if (autoCourseService != null) {
            ScenarioOutcome autoCourseOutcome = executeAutoCourseScenario(session, runNumber, seed, runOrigin);
            applyScenarioOutcome(session, autoCourseOutcome, false);
        }

        session.completedRuns++;
        float pct = Math.min(1.0f, session.completedRuns / (float) Math.max(1, session.runs));
        session.progressBar.setPercent(pct);
    }

    private ScenarioOutcome executeAutoCourseScenario(
            AutoTestSession session,
            int runNumber,
            long seed,
            BlockPos runOrigin
    ) {
        AutoCourseService.AutoCourseScenarioResult result = null;
        try {
            result = autoCourseService.runSynchronousScenario(session.world, runOrigin, seed, "autotest-" + runNumber);
            CoursePlacementValidator.ValidationReport report = placementValidator.validatePlacedCourse(
                    session.world,
                    result.course(),
                    result.placedState(),
                    "autocourse-run-" + runNumber
            );
            return ScenarioOutcome.success(runNumber, seed, report);
        } catch (RuntimeException ex) {
            return ScenarioOutcome.failure(runNumber, seed, "[autocourse] " + ex.getMessage());
        } finally {
            if (result != null) {
                placementService.resetPlacedCourse(session.world, result.placedState());
            }
        }
    }

    private ScenarioOutcome executeScenario(
            AutoTestSession session,
            int runNumber,
            long seed,
            BlockPos runOrigin,
            Course course,
            boolean shadowSurfaceRule
    ) {
        PlacedCourseState placed = null;
        try {
            if (shadowSurfaceRule) {
                placed = placementService.placeCourseWithHeightmapSurfaceRule(session.world, runOrigin, course, progress -> {
                });
            } else {
                placed = placementService.placeCourse(session.world, runOrigin, course, progress -> {
                });
            }

            CoursePlacementValidator.ValidationReport report = placementValidator.validatePlacedCourse(
                    session.world,
                    course,
                    placed,
                    (shadowSurfaceRule ? "shadow-surface-run-" : "batch-run-") + runNumber
            );
            return ScenarioOutcome.success(runNumber, seed, report);
        } catch (RuntimeException ex) {
            return ScenarioOutcome.failure(runNumber, seed, ex.getMessage());
        } finally {
            if (placed != null) {
                placementService.resetPlacedCourse(session.world, placed);
            }
        }
    }

    private void applyScenarioOutcome(AutoTestSession session, ScenarioOutcome outcome, boolean shadow) {
        if (outcome.errorMessage != null) {
            if (shadow) {
                session.shadowFailRuns++;
                session.shadowTotalIssues++;
                if (session.shadowSampleFailures.size() < 12) {
                    session.shadowSampleFailures.add(
                            "run=" + outcome.runNumber + " seed=" + outcome.seed + " error=" + outcome.errorMessage
                    );
                }
            } else {
                session.failRuns++;
                session.totalIssues++;
                if (session.sampleFailures.size() < 12) {
                    session.sampleFailures.add(
                            "run=" + outcome.runNumber + " seed=" + outcome.seed + " error=" + outcome.errorMessage
                    );
                }
            }
            return;
        }

        CoursePlacementValidator.ValidationReport report = outcome.report;
        if (shadow) {
            session.shadowTotalIssues += report.issueCount();
            session.shadowWarningLandingGaps += report.metrics().getOrDefault("warning_landing_gaps", 0);
            session.shadowMaxLandingGap = Math.max(session.shadowMaxLandingGap, report.metrics().getOrDefault("max_landing_gap", 0));
            session.shadowBiomeRunCounts.merge(report.biome(), 1, Integer::sum);
            for (CoursePlacementValidator.ValidationIssue issue : report.issues()) {
                session.shadowIssueCodeCounts.merge(issue.code(), 1, Integer::sum);
            }

            if (report.passed()) {
                session.shadowPassRuns++;
            } else {
                session.shadowFailRuns++;
                if (!report.issues().isEmpty() && session.shadowSampleFailures.size() < 12) {
                    CoursePlacementValidator.ValidationIssue first = report.issues().get(0);
                    session.shadowSampleFailures.add(
                            "run=" + outcome.runNumber
                                    + " seed=" + outcome.seed
                                    + " hole=" + first.holeIndex()
                                    + " code=" + first.code()
                                    + " biome=" + report.biome()
                    );
                }
            }
            return;
        }

        session.totalIssues += report.issueCount();
        session.warningLandingGaps += report.metrics().getOrDefault("warning_landing_gaps", 0);
        session.maxLandingGap = Math.max(session.maxLandingGap, report.metrics().getOrDefault("max_landing_gap", 0));
        session.biomeRunCounts.merge(report.biome(), 1, Integer::sum);
        for (CoursePlacementValidator.ValidationIssue issue : report.issues()) {
            session.issueCodeCounts.merge(issue.code(), 1, Integer::sum);
        }

        if (report.passed()) {
            session.passRuns++;
        } else {
            session.failRuns++;
            if (!report.issues().isEmpty() && session.sampleFailures.size() < 12) {
                CoursePlacementValidator.ValidationIssue first = report.issues().get(0);
                session.sampleFailures.add(
                        "run=" + outcome.runNumber
                                + " seed=" + outcome.seed
                                + " hole=" + first.holeIndex()
                                + " code=" + first.code()
                                + " biome=" + report.biome()
                );
            }
        }
    }

    private void finishSession(AutoTestSession session, boolean canceled) {
        for (ServerPlayerEntity player : session.source.getServer().getPlayerManager().getPlayerList()) {
            session.progressBar.removePlayer(player);
        }

        StringBuilder biomeSummary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : session.biomeRunCounts.entrySet()) {
            if (!biomeSummary.isEmpty()) {
                biomeSummary.append(", ");
            }
            biomeSummary.append(entry.getKey()).append("=").append(entry.getValue());
        }

        String status = canceled ? "Autotest canceled" : "Autotest complete";
        String summary = status + ": pass=" + session.passRuns
                + ", fail=" + session.failRuns
                + ", issues=" + session.totalIssues
            + ", warningLandingGaps=" + session.warningLandingGaps
                + ", maxLandingGap=" + session.maxLandingGap
                + ", processedRuns=" + session.completedRuns + "/" + session.runs
                + ", biomes=[" + biomeSummary + "]";
        session.source.sendFeedback(() -> Text.literal(summary), true);

        if (session.shadowSurfaceRuleMode) {
            String shadowBiomeSummary = biomeSummary(session.shadowBiomeRunCounts);
            String shadowSummary = "Shadow(surfaceRule): pass=" + session.shadowPassRuns
                + ", fail=" + session.shadowFailRuns
                + ", issues=" + session.shadowTotalIssues
                + ", warningLandingGaps=" + session.shadowWarningLandingGaps
                + ", maxLandingGap=" + session.shadowMaxLandingGap
                + ", processedRuns=" + session.completedRuns + "/" + session.runs
                + ", biomes=[" + shadowBiomeSummary + "]";
            session.source.sendFeedback(() -> Text.literal(shadowSummary), false);
        }

        Path reportPath = writeReportFile(session, status, summary);
        if (reportPath != null) {
            String reportMessage = "Autotest report: " + reportPath;
            session.source.sendFeedback(() -> Text.literal(reportMessage), false);
        }

        for (String failure : session.sampleFailures) {
            session.source.sendFeedback(() -> Text.literal(" - " + failure), false);
        }

        if (session.shadowSurfaceRuleMode) {
            for (String failure : session.shadowSampleFailures) {
                session.source.sendFeedback(() -> Text.literal(" - shadow " + failure), false);
            }
        }

        roundStateManager.clearAll();
        LieMarkerService.endAutotestLieMarkerTrail(session.source.getServer());
        if ("true".equalsIgnoreCase(System.getenv(AUTOTEST_SHUTDOWN_ENV))) {
            session.source.getServer().stop(false);
        }
        activeSession = null;
    }

    private Path writeReportFile(AutoTestSession session, String status, String summary) {
        try {
            Path runDir = session.source.getServer().getRunDirectory().toPath();
            Path logsDir = runDir.resolve("logs");
            Files.createDirectories(logsDir);

            String timestamp = LocalDateTime.now().format(REPORT_TIME);
            Path timestamped = logsDir.resolve("mcdg-autotest-" + timestamp + ".txt");
            Path latest = logsDir.resolve("mcdg-autotest-latest.txt");

            List<String> lines = new ArrayList<>();
            lines.add("MCDG Placement Autotest Report");
            lines.add("Status: " + status);
            lines.add("Summary: " + summary);
            lines.add("Runs: " + session.runs);
            lines.add("Holes per run: " + session.holes);
            lines.add("Processed runs: " + session.completedRuns);
            lines.add("Pass runs: " + session.passRuns);
            lines.add("Fail runs: " + session.failRuns);
            lines.add("Total issues: " + session.totalIssues);
            lines.add("Warning landing gaps: " + session.warningLandingGaps);
            lines.add("Max landing gap: " + session.maxLandingGap);
            lines.add("Biome counts:");
            if (session.biomeRunCounts.isEmpty()) {
                lines.add(" - none");
            } else {
                for (Map.Entry<String, Integer> entry : session.biomeRunCounts.entrySet()) {
                    lines.add(" - " + entry.getKey() + "=" + entry.getValue());
                }
            }

            lines.add("Sample failures:");
            if (session.sampleFailures.isEmpty()) {
                lines.add(" - none");
            } else {
                for (String failure : session.sampleFailures) {
                    lines.add(" - " + failure);
                }
            }

            lines.add("Issue code counts:");
            if (session.issueCodeCounts.isEmpty()) {
                lines.add(" - none");
            } else {
                for (Map.Entry<String, Integer> entry : session.issueCodeCounts.entrySet()) {
                    lines.add(" - " + entry.getKey() + "=" + entry.getValue());
                }
            }

            if (session.shadowSurfaceRuleMode) {
                lines.add("Shadow mode: surface-rule A/B enabled");
                lines.add("Shadow pass runs: " + session.shadowPassRuns);
                lines.add("Shadow fail runs: " + session.shadowFailRuns);
                lines.add("Shadow total issues: " + session.shadowTotalIssues);
                lines.add("Shadow warning landing gaps: " + session.shadowWarningLandingGaps);
                lines.add("Shadow max landing gap: " + session.shadowMaxLandingGap);
                lines.add("Shadow biome counts:");
                if (session.shadowBiomeRunCounts.isEmpty()) {
                    lines.add(" - none");
                } else {
                    for (Map.Entry<String, Integer> entry : session.shadowBiomeRunCounts.entrySet()) {
                        lines.add(" - " + entry.getKey() + "=" + entry.getValue());
                    }
                }

                lines.add("Shadow sample failures:");
                if (session.shadowSampleFailures.isEmpty()) {
                    lines.add(" - none");
                } else {
                    for (String failure : session.shadowSampleFailures) {
                        lines.add(" - " + failure);
                    }
                }

                lines.add("Shadow issue code counts:");
                if (session.shadowIssueCodeCounts.isEmpty()) {
                    lines.add(" - none");
                } else {
                    for (Map.Entry<String, Integer> entry : session.shadowIssueCodeCounts.entrySet()) {
                        lines.add(" - " + entry.getKey() + "=" + entry.getValue());
                    }
                }
            }

            Files.write(timestamped, lines, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.write(latest, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return timestamped;
        } catch (IOException ex) {
            String warning = "Failed to write autotest report file: " + ex.getMessage();
            session.source.sendError(Text.literal(warning));
            return null;
        }
    }

    private static void updateBossBar(AutoTestSession session, String title) {
        session.progressBar.setName(Text.literal(title));
    }

    private boolean isShadowSurfaceRuleEnabledEffective() {
        if (shadowSurfaceRuleOverride != null) {
            return shadowSurfaceRuleOverride;
        }
        return isShadowSurfaceRuleEnabledFromEnv();
    }

    private static boolean isShadowSurfaceRuleEnabledFromEnv() {
        String value = System.getenv(AUTOTEST_SHADOW_SURFACE_ENV);
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim();
        return normalized.equalsIgnoreCase("1")
                || normalized.equalsIgnoreCase("true")
                || normalized.equalsIgnoreCase("yes")
                || normalized.equalsIgnoreCase("on");
    }

    private static String biomeSummary(Map<String, Integer> biomeRunCounts) {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : biomeRunCounts.entrySet()) {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return summary.toString();
    }

    private static List<BlockPos> collectBiomeAnchors(
            ServerWorld world,
            BlockPos center,
            int searchRadius,
            int step,
            int maxBiomes
    ) {
        Map<String, BlockPos> anchorsByBiome = new LinkedHashMap<>();
        Map<String, BlockPos> fallbackNonExcludedAnchorsByBiome = new LinkedHashMap<>();
        int stride = Math.max(32, step);

        for (int dx = -searchRadius; dx <= searchRadius; dx += stride) {
            for (int dz = -searchRadius; dz <= searchRadius; dz += stride) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos sample = new BlockPos(x, y, z);
                String biome = biomeId(world.getBiome(sample));
                if (isUndergroundBiome(biome)) {
                    continue;
                }

                if (!isExcludedSurfaceAutotestBiome(biome)) {
                    fallbackNonExcludedAnchorsByBiome.putIfAbsent(biome, sample.toImmutable());
                }
                if (isExcludedSurfaceAutotestBiome(biome)) {
                    continue;
                }
                if (isPoorAutotestAnchor(world, center, sample)) {
                    continue;
                }

                anchorsByBiome.putIfAbsent(biome, sample.toImmutable());
                if (anchorsByBiome.size() >= maxBiomes) {
                    return List.copyOf(anchorsByBiome.values());
                }
            }
        }

        if (anchorsByBiome.isEmpty()) {
            if (!fallbackNonExcludedAnchorsByBiome.isEmpty()) {
                return List.copyOf(fallbackNonExcludedAnchorsByBiome.values().stream().limit(maxBiomes).toList());
            }
            return List.of(center.toImmutable());
        }

        return List.copyOf(anchorsByBiome.values());
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "unknown";
        }
        return key.getValue().getPath();
    }

    private static boolean isUndergroundBiome(String biomeId) {
        return biomeId.contains("cave") || biomeId.contains("deep_dark");
    }

    private static boolean isExcludedSurfaceAutotestBiome(String biomeId) {
        return biomeId.contains("ocean")
                || biomeId.contains("river")
                || biomeId.contains("beach")
                || biomeId.contains("shore")
                || biomeId.contains("stony_shore")
                || biomeId.contains("snowy_beach")
                || biomeId.contains("mushroom_fields")
                || biomeId.contains("mangrove_swamp")
                || biomeId.contains("frozen_ocean");
    }

    private static boolean isPoorAutotestAnchor(ServerWorld world, BlockPos center, BlockPos sample) {
        int verticalDelta = Math.abs(sample.getY() - center.getY());
        if (verticalDelta > 24) {
            return true;
        }

        int waterColumns = 0;
        int radius = 8;
        int step = 2;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int py = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sample.getX() + dx, sample.getZ() + dz) - 1;
                BlockPos probe = new BlockPos(sample.getX() + dx, py, sample.getZ() + dz);
                if (!world.getFluidState(probe).isEmpty() || !world.getFluidState(probe.up()).isEmpty()) {
                    waterColumns++;
                }
            }
        }

        return waterColumns >= 6;
    }

    private static final class ScenarioOutcome {
        private final int runNumber;
        private final long seed;
        private final CoursePlacementValidator.ValidationReport report;
        private final String errorMessage;

        private ScenarioOutcome(int runNumber, long seed, CoursePlacementValidator.ValidationReport report, String errorMessage) {
            this.runNumber = runNumber;
            this.seed = seed;
            this.report = report;
            this.errorMessage = errorMessage;
        }

        private static ScenarioOutcome success(int runNumber, long seed, CoursePlacementValidator.ValidationReport report) {
            return new ScenarioOutcome(runNumber, seed, report, null);
        }

        private static ScenarioOutcome failure(int runNumber, long seed, String errorMessage) {
            return new ScenarioOutcome(runNumber, seed, null, errorMessage);
        }
    }

    private static final class AutoTestSession {
        private final ServerCommandSource source;
        private final ServerWorld world;
        private final int runs;
        private final int holes;
        private final List<BlockPos> biomeAnchors;
        private final ServerBossBar progressBar;
        private final long baseSeed;
        private final boolean shadowSurfaceRuleMode;

        private int completedRuns;
        private int passRuns;
        private int failRuns;
        private int totalIssues;
        private int warningLandingGaps;
        private int maxLandingGap;
        private boolean cancelRequested;
        private int shadowPassRuns;
        private int shadowFailRuns;
        private int shadowTotalIssues;
        private int shadowWarningLandingGaps;
        private int shadowMaxLandingGap;

        private final Map<String, Integer> biomeRunCounts = new LinkedHashMap<>();
        private final List<String> sampleFailures = new ArrayList<>();
        private final Map<String, Integer> issueCodeCounts = new LinkedHashMap<>();
        private final Map<String, Integer> shadowBiomeRunCounts = new LinkedHashMap<>();
        private final List<String> shadowSampleFailures = new ArrayList<>();
        private final Map<String, Integer> shadowIssueCodeCounts = new LinkedHashMap<>();

        private AutoTestSession(
                ServerCommandSource source,
                ServerWorld world,
                int runs,
                int holes,
                List<BlockPos> biomeAnchors,
                ServerBossBar progressBar,
                long baseSeed,
                boolean shadowSurfaceRuleMode
        ) {
            this.source = source;
            this.world = world;
            this.runs = runs;
            this.holes = holes;
            this.biomeAnchors = biomeAnchors;
            this.progressBar = progressBar;
            this.baseSeed = baseSeed;
            this.shadowSurfaceRuleMode = shadowSurfaceRuleMode;
        }
    }
}
