package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.HoleProgressTracker;
import com.mcdg.game.PlacedCourseState;
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

    private final CourseGenerator generator;
    private final CoursePlacementService placementService;
    private final CoursePlacementValidator placementValidator;
    private final ActiveCourseManager courseManager;
    private final RoundStateManager roundStateManager;

    private AutoTestSession activeSession;

    public PlacementAutoTestService(
            CourseGenerator generator,
            CoursePlacementService placementService,
            CoursePlacementValidator placementValidator,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager
    ) {
        this.generator = generator;
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
        activeSession = new AutoTestSession(source, world, runs, holes, biomeAnchors, progressBar, resolvedBaseSeed);
        HoleProgressTracker.beginAutotestLieMarkerTrail();

        String startMessage = "Starting autotest: runs=" + runs
                + ", holes=" + holes
                + ", biomeAnchors=" + biomeAnchors.size()
                + ", baseSeed=" + resolvedBaseSeed
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
        PlacedCourseState placed = null;
        try {
            course = generator.generate(seed, session.holes);
            placed = placementService.placeCourse(session.world, runOrigin, course, progress -> {
            });

            CoursePlacementValidator.ValidationReport report = placementValidator.validatePlacedCourse(
                    session.world,
                    course,
                    placed,
                    "batch-run-" + runNumber
            );

            session.totalIssues += report.issueCount();
            session.warningLandingGaps += report.metrics().getOrDefault("warning_landing_gaps", 0);
            session.maxLandingGap = Math.max(session.maxLandingGap, report.metrics().getOrDefault("max_landing_gap", 0));
            session.biomeRunCounts.merge(report.biome(), 1, Integer::sum);

            if (report.passed()) {
                session.passRuns++;
            } else {
                session.failRuns++;
                if (!report.issues().isEmpty() && session.sampleFailures.size() < 12) {
                    CoursePlacementValidator.ValidationIssue first = report.issues().get(0);
                    session.sampleFailures.add(
                            "run=" + runNumber
                                    + " seed=" + seed
                                    + " hole=" + first.holeIndex()
                                    + " code=" + first.code()
                                    + " biome=" + report.biome()
                    );
                }
            }
        } catch (RuntimeException ex) {
            session.failRuns++;
            session.totalIssues++;
            if (session.sampleFailures.size() < 12) {
                session.sampleFailures.add("run=" + runNumber + " seed=" + seed + " error=" + ex.getMessage());
            }
        } finally {
            if (placed != null) {
                placementService.resetPlacedCourse(session.world, placed);
            }
            session.completedRuns++;
            float pct = Math.min(1.0f, session.completedRuns / (float) Math.max(1, session.runs));
            session.progressBar.setPercent(pct);
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

        Path reportPath = writeReportFile(session, status, summary);
        if (reportPath != null) {
            String reportMessage = "Autotest report: " + reportPath;
            session.source.sendFeedback(() -> Text.literal(reportMessage), false);
        }

        for (String failure : session.sampleFailures) {
            session.source.sendFeedback(() -> Text.literal(" - " + failure), false);
        }

        roundStateManager.clearAll();
        HoleProgressTracker.endAutotestLieMarkerTrail(session.source.getServer());
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

    private static List<BlockPos> collectBiomeAnchors(
            ServerWorld world,
            BlockPos center,
            int searchRadius,
            int step,
            int maxBiomes
    ) {
        Map<String, BlockPos> anchorsByBiome = new LinkedHashMap<>();
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

                anchorsByBiome.putIfAbsent(biome, sample.toImmutable());
                if (anchorsByBiome.size() >= maxBiomes) {
                    return List.copyOf(anchorsByBiome.values());
                }
            }
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

    private static final class AutoTestSession {
        private final ServerCommandSource source;
        private final ServerWorld world;
        private final int runs;
        private final int holes;
        private final List<BlockPos> biomeAnchors;
        private final ServerBossBar progressBar;
        private final long baseSeed;

        private int completedRuns;
        private int passRuns;
        private int failRuns;
        private int totalIssues;
        private int warningLandingGaps;
        private int maxLandingGap;
        private boolean cancelRequested;

        private final Map<String, Integer> biomeRunCounts = new LinkedHashMap<>();
        private final List<String> sampleFailures = new ArrayList<>();

        private AutoTestSession(
                ServerCommandSource source,
                ServerWorld world,
                int runs,
                int holes,
                List<BlockPos> biomeAnchors,
                ServerBossBar progressBar,
                long baseSeed
        ) {
            this.source = source;
            this.world = world;
            this.runs = runs;
            this.holes = holes;
            this.biomeAnchors = biomeAnchors;
            this.progressBar = progressBar;
            this.baseSeed = baseSeed;
        }
    }
}
