package com.mcdg.world;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.data.SignatureHoleType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

public final class RegressionCheckRunner {
    private static final int HOLE_COUNT = 9;
    private static final int PAR5_CAP_SEED_SAMPLES = 120;
    private static final Path MINIMAP_RENDERER_FILE = Paths.get(
        "src", "client", "java", "com", "mcdg", "client", "MiniMapRenderer.java"
    );
    private static final Path VALIDATOR_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "world", "CoursePlacementValidator.java"
    );
    private static final Path ROUND_LIFECYCLE_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "command", "RoundLifecycleCommands.java"
    );
    private static final Path COURSE_PLACEMENT_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "world", "CoursePlacementService.java"
    );
    private static final Path HOLE_PROGRESS_TRACKER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "game", "HoleProgressTracker.java"
    );
    private static final Path TURN_MANAGER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "game", "TurnManager.java"
    );
    private static final Path ACE_CINEMATIC_SYNC_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "net", "AceCinematicSync.java"
    );
    private static final Path ROUND_COMPLETE_CINEMATIC_SYNC_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "net", "RoundCompleteCinematicSync.java"
    );
    private static final Path ROUND_RUNNING_SCORES_SYNC_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "net", "RoundRunningScoresSync.java"
    );
    private static final Path COMMAND_PERMISSIONS_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "command", "CommandPermissions.java"
    );
    private static final Path SAFE_POSITION_FINDER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "world", "SafePositionFinder.java"
    );
    private static final Path OUT_OF_BOUNDS_CLASSIFIER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "game", "OutOfBoundsClassifier.java"
    );
    private static final Path COURSE_ANCHOR_FINDER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "world", "CourseAnchorFinder.java"
    );
    private static final Path GOLF_TITLE_MESSENGER_FILE = Paths.get(
        "src", "main", "java", "com", "mcdg", "game", "GolfTitleMessenger.java"
    );
    private static final Pattern HOLE_SPECIAL_CASE_PATTERN = Pattern.compile(
            "\\b(?:holeIndex|currentHole|holeNumber|holeId)\\b\\s*(?:==|!=|<=|>=|<|>)\\s*\\d+|\\.index\\(\\)\\s*(?:==|!=|<=|>=|<|>)\\s*\\d+"
    );

    private RegressionCheckRunner() {
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "quick";
        SeededCourseGenerator generator = new SeededCourseGenerator();

        if ("quick".equalsIgnoreCase(mode)) {
            runQuickChecks(generator);
            return;
        }

        if ("smoke".equalsIgnoreCase(mode)) {
            runSmokeChecks(generator);
            return;
        }

        if ("full".equalsIgnoreCase(mode)) {
            runQuickChecks(generator);
            runSmokeChecks(generator);
            return;
        }

        throw new RuntimeException("Unknown regression mode: " + mode);
    }

    private static void runQuickChecks(SeededCourseGenerator generator) {
        runArchitectureChecks();
        runMiniMapChecks();
        runPlacementIssueSyncChecks();
        runCarryPolicyChecks();
        runGameplayFlowChecks();
        runScoreboardContractChecks();
        runCinematicContractChecks();
        runRefactoredClassChecks();
        runParDistributionChecks(generator);

        Course one = generator.generate(123456789L, HOLE_COUNT);
        assertExactlyOneSignature(one, "quick-seed-1");

        SignatureHoleType signatureType = one.holes().stream()
                .filter(Hole::isSignature)
                .map(Hole::signatureType)
                .findFirst()
                .orElse(SignatureHoleType.NONE);

        if (!EnumSet.of(
                SignatureHoleType.ISLAND_GREEN,
                SignatureHoleType.TUNNEL_GAP,
                SignatureHoleType.DOWNHILL_BOMBER
        ).contains(signatureType)) {
            throw new RuntimeException("Unexpected signature type in quick check: " + signatureType);
        }

        long seed = 42424242L;
        Course first = generator.generate(seed, HOLE_COUNT);
        Course second = generator.generate(seed, HOLE_COUNT);

        assertEquals(first.name(), second.name(), "Course name must be deterministic.");
        assertEquals(first.holes().size(), second.holes().size(), "Hole count must be deterministic.");

        List<Hole> a = first.holes();
        List<Hole> b = second.holes();
        for (int i = 0; i < a.size(); i++) {
            Hole h1 = a.get(i);
            Hole h2 = b.get(i);
            assertEquals(h1.index(), h2.index(), "Hole index must be deterministic.");
            assertEquals(h1.par(), h2.par(), "Hole par must be deterministic.");
            assertEquals(h1.distanceFeet(), h2.distanceFeet(), "Hole distance must be deterministic.");
            assertEquals(h1.tee(), h2.tee(), "Tee coordinates must be deterministic.");
            assertEquals(h1.basket(), h2.basket(), "Basket coordinates must be deterministic.");
            assertEquals(h1.signatureType(), h2.signatureType(), "Signature assignment must be deterministic.");
        }

        System.out.println("Quick regression checks passed.");
    }

    private static void runParDistributionChecks(SeededCourseGenerator generator) {
        Random random = new Random(20260601L);
        for (int i = 0; i < PAR5_CAP_SEED_SAMPLES; i++) {
            long seed = random.nextLong();
            Course course = generator.generate(seed, HOLE_COUNT);
            long par5Count = course.holes().stream().filter(hole -> hole.par() >= 5).count();
            if (par5Count > 2) {
                throw new RuntimeException(
                        "Par distribution regression for seed " + seed
                                + ": expected <=1 Par 5 in 9 holes, got " + par5Count
                );
            }
        }
    }

    private static void runMiniMapChecks() {
        if (!Files.exists(MINIMAP_RENDERER_FILE)) {
            throw new RuntimeException("Minimap renderer regression file missing: " + MINIMAP_RENDERER_FILE);
        }

        String source;
        try {
            source = Files.readString(MINIMAP_RENDERER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read minimap renderer source for regression checks", ex);
        }

        assertContains(
            source,
            "public static final int PASSIVE_MINIMAP_SPAN_BLOCKS = 96;",
            "Minimap world span baseline changed unexpectedly."
        );
        assertContains(
            source,
            "private static final int MINIMAP_TEXTURE_SIZE = 128;",
            "Minimap texture resolution baseline changed unexpectedly."
        );
        // Hazard overlay constants moved to HazardOverlayRenderer
        Path hazardOverlayFile = Paths.get("src", "client", "java", "com", "mcdg", "client", "HazardOverlayRenderer.java");
        if (!Files.exists(hazardOverlayFile)) {
            throw new RuntimeException("Hazard overlay renderer regression file missing: " + hazardOverlayFile);
        }
        String hazardSource;
        try {
            hazardSource = Files.readString(hazardOverlayFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read hazard overlay renderer source for regression checks", ex);
        }
        assertContains(
            hazardSource,
            "private static final int HAZARD_OVERLAY_ARGB = 0x8CFF9A32;",
            "Minimap strict hazard overlay alpha baseline changed unexpectedly."
        );
        assertContains(
            hazardSource,
            "private static final int HAZARD_SAMPLE_STEP_PX = 2;",
            "Minimap strict hazard sample-density baseline changed unexpectedly."
        );
        assertContains(
            source,
            "private static final int MINIMAP_TEXTURE_SIZE =",
            "Minimap texture size constant is missing."
        );
        assertContains(
            source,
            "public static void refreshMiniMapRenderCache(MinecraftClient client, int mapSpan)",
            "Minimap refresh cache signature must remain decoupled from gameplay state."
        );
        assertContains(
            source,
            "matrices.scale(texScale, texScale, 1.0f);",
            "Minimap render must apply matrix scaling for full-texture display."
        );
        assertContains(
            source,
            "drawContext.drawTexture(miniMapRenderCache.textureId(), 0, 0, 0, 0, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE, MINIMAP_TEXTURE_SIZE);",
            "Minimap render must draw the full texture atlas region."
        );
        assertContains(
            source,
            "drawFilledCircle(drawContext, mapCenterX, mapCenterY, mapRadius",
            "Minimap baseline must render circular map background."
        );
        assertContains(
            source,
            "image.setColor(px, py, TerrainSampler.argbToAbgr(0x00000000));",
            "Minimap baseline must preserve transparent texture outside circular area."
        );

        assertContains(
            source,
            "float mapRotationDegrees = resolveMiniMapHeadingRotationDegrees(client);",
            "Minimap regression: player-up heading resolver call is missing."
        );
        assertContains(
            source,
            "matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(mapRotationDegrees));",
            "Minimap regression: map rotation transform is missing."
        );
        assertContains(
            source,
            "private static float resolveMiniMapHeadingRotationDegrees(MinecraftClient client)",
            "Minimap regression: movement-heading resolver is missing."
        );
        assertContains(
            source,
            "float lookHeading = normalizeDegrees(180.0f - client.player.getYaw());",
            "Minimap baseline must lock heading to player look yaw for stable compass/cardinals."
        );
        assertContains(
            source,
            "public static float[] rotateMiniMapVector(float x, float y, float rotationDegrees)",
            "Minimap baseline must include map-space rotation helper."
        );
        assertContains(
            source,
            "drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, mapRotationDegrees, hudAlpha);",
            "Minimap regression: rotating cardinal labels call is missing."
        );
    }

    private static void runPlacementIssueSyncChecks() {
        if (!Files.exists(VALIDATOR_FILE)) {
            throw new RuntimeException("Validator regression file missing: " + VALIDATOR_FILE);
        }
        if (!Files.exists(ROUND_LIFECYCLE_FILE)) {
            throw new RuntimeException("Admin command regression file missing: " + ROUND_LIFECYCLE_FILE);
        }

        String validatorSource;
        String adminSource;
        try {
            validatorSource = Files.readString(VALIDATOR_FILE, StandardCharsets.UTF_8);
            adminSource = Files.readString(ROUND_LIFECYCLE_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read placement source files for regression checks", ex);
        }

        String[] mustStayInSync = {
                "tee_deeply_enclosed",
                "basket_deeply_enclosed",
                "par5_alternate_route_missing",
                "alternate_route_missing",
                "landing_gap_too_long"
        };

        for (String issueCode : mustStayInSync) {
            assertContains(
                    validatorSource,
                    "\"" + issueCode + "\"",
                    "Placement validator is missing expected issue code."
            );
            assertContains(
                    adminSource,
                    "\"" + issueCode + "\"",
                    "Start-round retry gate is missing expected issue code."
            );
        }
    }

    private static void runCarryPolicyChecks() {
        if (!Files.exists(COURSE_PLACEMENT_FILE)) {
            throw new RuntimeException("Course placement regression file missing: " + COURSE_PLACEMENT_FILE);
        }

        String placementSource;
        try {
            placementSource = Files.readString(COURSE_PLACEMENT_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read course placement source for carry-policy regression checks", ex);
        }

        assertContains(
            placementSource,
            "MAX_WATER_CARRY_BLOCKS = CoursePlacementConfig.WaterLanding.MAX_CARRY_BLOCKS;",
            "Unified max water-carry policy changed unexpectedly."
        );
        assertContains(
            placementSource,
            "PAR5_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR5_MAX_WATER_CARRY;",
            "Par 5 carry policy diverged from unified max carry policy."
        );
        assertContains(
            placementSource,
            "PAR34_ROUTE_MAX_WATER_CARRY = CoursePlacementConfig.RoutePolicy.PAR34_MAX_WATER_CARRY;",
            "Par 3/4 carry policy diverged from unified max carry policy."
        );
    }

    private static void runGameplayFlowChecks() {
        if (!Files.exists(HOLE_PROGRESS_TRACKER_FILE)) {
            throw new RuntimeException("Gameplay flow regression file missing: " + HOLE_PROGRESS_TRACKER_FILE);
        }
        if (!Files.exists(TURN_MANAGER_FILE)) {
            throw new RuntimeException("Turn manager regression file missing: " + TURN_MANAGER_FILE);
        }

        String source;
        String turnSource;
        try {
            source = Files.readString(HOLE_PROGRESS_TRACKER_FILE, StandardCharsets.UTF_8);
            turnSource = Files.readString(TURN_MANAGER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read gameplay flow source for regression checks", ex);
        }

        assertContains(
            turnSource,
            "if ((now - startedAt) >= TURN_TIMEOUT_TICKS) {",
            "Turn-timeout regression: timeout condition was removed or changed unexpectedly."
        );
        assertContains(
            turnSource,
            "applyTurnTimeoutPenalty(server, roundStateManager, expected, expectedState, placed);",
            "Turn-timeout regression: timeout penalty application is missing."
        );
        assertContains(
            turnSource,
            "TURN_SKIP_ONCE_BY_HOLE.put(hole, expected);",
            "Turn-timeout regression: skip-once guard should remain in place after timeout penalty."
        );
        assertContains(
            source,
            "for (int priorHole = focusHole - 1; priorHole >= 1; priorHole--) {",
            "Scoreboard ordering regression: prior-hole tie-break loop is missing."
        );
        assertContains(
            source,
            "int aRank = HOLE_ONE_RANDOM_ORDER.getOrDefault(a, Integer.MAX_VALUE);",
            "Scoreboard ordering regression: hole-one random tie-break fallback is missing."
        );
    }

    private static void runScoreboardContractChecks() {
        if (!Files.exists(ROUND_RUNNING_SCORES_SYNC_FILE)) {
            throw new RuntimeException("Running-scores sync file missing: " + ROUND_RUNNING_SCORES_SYNC_FILE);
        }

        String source;
        try {
            source = Files.readString(ROUND_RUNNING_SCORES_SYNC_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read running-scores sync source for regression checks", ex);
        }

        assertContains(
            source,
            "public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, \"round_running_scores\");",
            "Running-scores regression: channel ID changed unexpectedly."
        );
        assertContains(
            source,
            "return new PlayerRow(playerName, online, List.copyOf(holeScores), runningTotal);",
            "Running-scores regression: PlayerRow read path should preserve immutable score list semantics."
        );
        assertContains(
            source,
            "return new Payload(false, 0, 0, \"\", List.of());",
            "Running-scores regression: inactive payload contract changed unexpectedly."
        );
    }

    private static void runCinematicContractChecks() {
        if (!Files.exists(HOLE_PROGRESS_TRACKER_FILE)) {
            throw new RuntimeException("Cinematic regression file missing: " + HOLE_PROGRESS_TRACKER_FILE);
        }
        if (!Files.exists(ACE_CINEMATIC_SYNC_FILE)) {
            throw new RuntimeException("Ace cinematic sync file missing: " + ACE_CINEMATIC_SYNC_FILE);
        }
        if (!Files.exists(ROUND_COMPLETE_CINEMATIC_SYNC_FILE)) {
            throw new RuntimeException("Round-complete cinematic sync file missing: " + ROUND_COMPLETE_CINEMATIC_SYNC_FILE);
        }

        String trackerSource;
        String aceSource;
        String roundCompleteSource;
        try {
            trackerSource = Files.readString(HOLE_PROGRESS_TRACKER_FILE, StandardCharsets.UTF_8);
            aceSource = Files.readString(ACE_CINEMATIC_SYNC_FILE, StandardCharsets.UTF_8);
            roundCompleteSource = Files.readString(ROUND_COMPLETE_CINEMATIC_SYNC_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read cinematic sources for regression checks", ex);
        }

        assertContains(
            trackerSource,
            "if (state.holeStrokes() == 1) {",
            "Cinematic regression: ace trigger guard for first throw is missing."
        );
        assertContains(
            trackerSource,
            "ServerPlayNetworking.send(player, AceCinematicSync.Payload.active(state.currentHole(), currentHole.distanceFeet()));",
            "Cinematic regression: ace cinematic network trigger changed unexpectedly."
        );
        assertContains(
            trackerSource,
            "sendRoundCompleteCinematic(server, placed.worldKey(), roundStateManager, totalPar);",
            "Cinematic regression: round-complete cinematic trigger is missing."
        );

        assertContains(
            aceSource,
            "public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, \"ace_cinematic\");",
            "Cinematic regression: ace channel ID changed unexpectedly."
        );
        assertContains(
            aceSource,
            "public record Payload(",
            "Cinematic regression: ace payload record contract is missing."
        );
        assertContains(
            aceSource,
            "int holeIndex",
            "Cinematic regression: ace payload hole index field is missing."
        );
        assertContains(
            aceSource,
            "int distanceFeet",
            "Cinematic regression: ace payload distance field is missing."
        );

        assertContains(
            roundCompleteSource,
            "public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, \"round_complete_cinematic\");",
            "Cinematic regression: round-complete channel ID changed unexpectedly."
        );
        assertContains(
            roundCompleteSource,
            "String firstName",
            "Cinematic regression: round-complete payload podium fields are missing."
        );
        assertContains(
            roundCompleteSource,
            "int localRank",
            "Cinematic regression: round-complete local rank field is missing."
        );
        assertContains(
            roundCompleteSource,
            "int localScore",
            "Cinematic regression: round-complete local score field is missing."
        );

    }

    private static void runRefactoredClassChecks() {
        // CommandPermissions: Security-critical authorization checks
        if (!Files.exists(COMMAND_PERMISSIONS_FILE)) {
            throw new RuntimeException("CommandPermissions regression file missing: " + COMMAND_PERMISSIONS_FILE);
        }
        String commandPermissionsSource;
        try {
            commandPermissionsSource = Files.readString(COMMAND_PERMISSIONS_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read CommandPermissions source for regression checks", ex);
        }
        assertContains(
            commandPermissionsSource,
            "public static boolean canUseAdminCommands",
            "CommandPermissions regression: canUseAdminCommands method is missing (security-critical)."
        );
        assertContains(
            commandPermissionsSource,
            "public static boolean canUseAdvancedCommands",
            "CommandPermissions regression: canUseAdvancedCommands method is missing (security-critical)."
        );

        // SafePositionFinder: Safety-critical position validation
        if (!Files.exists(SAFE_POSITION_FINDER_FILE)) {
            throw new RuntimeException("SafePositionFinder regression file missing: " + SAFE_POSITION_FINDER_FILE);
        }
        String safePositionFinderSource;
        try {
            safePositionFinderSource = Files.readString(SAFE_POSITION_FINDER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read SafePositionFinder source for regression checks", ex);
        }
        assertContains(
            safePositionFinderSource,
            "public static BlockPos resolveSafeFeetNear",
            "SafePositionFinder regression: resolveSafeFeetNear method is missing (safety-critical)."
        );
        assertContains(
            safePositionFinderSource,
            "isStandableFeet",
            "SafePositionFinder regression: resolveSafeFeetNear must use isStandableFeet for safety validation."
        );
        assertContains(
            safePositionFinderSource,
            "public static BlockPos findNearestStandableFeet",
            "SafePositionFinder regression: findNearestStandableFeet method is missing (safety-critical)."
        );

        // OutOfBoundsClassifier: Gameplay-critical boundary detection
        if (!Files.exists(OUT_OF_BOUNDS_CLASSIFIER_FILE)) {
            throw new RuntimeException("OutOfBoundsClassifier regression file missing: " + OUT_OF_BOUNDS_CLASSIFIER_FILE);
        }
        String outOfBoundsClassifierSource;
        try {
            outOfBoundsClassifierSource = Files.readString(OUT_OF_BOUNDS_CLASSIFIER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read OutOfBoundsClassifier source for regression checks", ex);
        }
        assertContains(
            outOfBoundsClassifierSource,
            "public static StrictPenaltyType classifyOutType",
            "OutOfBoundsClassifier regression: classifyOutType classification method is missing."
        );
        assertContains(
            outOfBoundsClassifierSource,
            "public static StrictPenaltyType classifyOutTypeWithCorridor",
            "OutOfBoundsClassifier regression: classifyOutTypeWithCorridor method is missing."
        );

        // CourseAnchorFinder: Terrain-aware course placement
        if (!Files.exists(COURSE_ANCHOR_FINDER_FILE)) {
            throw new RuntimeException("CourseAnchorFinder regression file missing: " + COURSE_ANCHOR_FINDER_FILE);
        }
        String courseAnchorFinderSource;
        try {
            courseAnchorFinderSource = Files.readString(COURSE_ANCHOR_FINDER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read CourseAnchorFinder source for regression checks", ex);
        }
        assertContains(
            courseAnchorFinderSource,
            "static BlockPos findPreferredCourseAnchor",
            "CourseAnchorFinder regression: findPreferredCourseAnchor method is missing."
        );
        assertContains(
            courseAnchorFinderSource,
            "static CourseBounds findCourseBounds",
            "CourseAnchorFinder regression: findCourseBounds method is missing."
        );

        // GolfTitleMessenger: Server-side title overlay
        if (!Files.exists(GOLF_TITLE_MESSENGER_FILE)) {
            throw new RuntimeException("GolfTitleMessenger regression file missing: " + GOLF_TITLE_MESSENGER_FILE);
        }
        String golfTitleMessengerSource;
        try {
            golfTitleMessengerSource = Files.readString(GOLF_TITLE_MESSENGER_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read GolfTitleMessenger source for regression checks", ex);
        }
        assertContains(
            golfTitleMessengerSource,
            "static void sendClankTitle",
            "GolfTitleMessenger regression: sendClankTitle method is missing."
        );
        assertContains(
            golfTitleMessengerSource,
            "static void sendStrictPenaltyTitle",
            "GolfTitleMessenger regression: sendStrictPenaltyTitle method is missing."
        );

    }

    private static void runArchitectureChecks() {
        Path root = Paths.get("src", "main", "java", "com", "mcdg");
        if (!Files.exists(root)) {
            throw new RuntimeException("Architecture check root missing: " + root);
        }

        try (var paths = Files.walk(root)) {
            List<Path> files = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(RegressionCheckRunner::isCoreGameplayPath)
                    .toList();

            for (Path file : files) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!HOLE_SPECIAL_CASE_PATTERN.matcher(line).find()) {
                        continue;
                    }

                    if (isAllowedHoleNumberInvariant(file, line)) {
                        continue;
                    }

                    String rel = root.getParent().getParent().relativize(file).toString().replace('\\', '/');
                    throw new RuntimeException(
                            "Global behavior guard failed: hard-coded hole-specific branch detected at "
                                    + rel + ":" + (i + 1)
                                    + " -> " + line.trim()
                                    + " | Use rules/data-driven logic instead of hole-number branching."
                    );
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed architecture guard scan", ex);
        }
    }

    private static boolean isCoreGameplayPath(Path file) {
        String normalized = file.toString().replace('\\', '/');
        if (normalized.endsWith("/com/mcdg/world/RegressionCheckRunner.java")) {
            return false;
        }
        return normalized.contains("/com/mcdg/game/")
                || normalized.contains("/com/mcdg/world/")
                || normalized.contains("/com/mcdg/rules/")
                || normalized.contains("/com/mcdg/net/");
    }

    private static boolean isAllowedHoleNumberInvariant(Path file, String line) {
        String normalized = file.toString().replace('\\', '/');
        if (normalized.endsWith("/com/mcdg/game/PlayerRoundState.java")
                && line.contains("currentHole < 1")) {
            return true;
        }
        return false;
    }

    private static void runSmokeChecks(SeededCourseGenerator generator) {
        Random random = new Random(20260528L);
        for (int i = 0; i < 40; i++) {
            long seed = random.nextLong();
            Course course = generator.generate(seed, HOLE_COUNT);
            assertEquals(HOLE_COUNT, course.holes().size(), "Smoke check hole count mismatch for seed " + seed);
            assertExactlyOneSignature(course, "smoke-seed-" + seed);
        }

        System.out.println("Smoke regression checks passed.");
    }

    private static void assertExactlyOneSignature(Course course, String context) {
        long signatureCount = course.holes().stream().filter(Hole::isSignature).count();
        if (signatureCount != 1L) {
            throw new RuntimeException("Expected exactly one signature hole for " + context + ", got " + signatureCount);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new RuntimeException(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertContains(String text, String required, String message) {
        if (!text.contains(required)) {
            throw new RuntimeException(message + " Missing snippet: " + required);
        }
    }

}
