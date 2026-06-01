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
    private static final Path CLIENT_MINIMAP_FILE = Paths.get(
        "src", "client", "java", "com", "mcdg", "client", "McdgClientMod.java"
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

        private static void runMiniMapChecks() {
        if (!Files.exists(CLIENT_MINIMAP_FILE)) {
            throw new RuntimeException("Minimap regression file missing: " + CLIENT_MINIMAP_FILE);
        }

        String source;
        try {
            source = Files.readString(CLIENT_MINIMAP_FILE, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read minimap source for regression checks", ex);
        }

        assertContains(
            source,
            "private static final int PASSIVE_MINIMAP_SPAN_BLOCKS = 96;",
            "Minimap world span baseline changed unexpectedly."
        );
        assertContains(
            source,
            "private static final int MINIMAP_TEXTURE_SIZE = 128;",
            "Minimap texture resolution baseline changed unexpectedly."
        );
        assertContains(
            source,
            "private static final int MINIMAP_TEXTURE_SIZE =",
            "Minimap texture size constant is missing."
        );
        assertContains(
            source,
            "private static void refreshMiniMapRenderCache(MinecraftClient client, int mapSpan)",
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
            "image.setColor(px, py, argbToAbgr(0x00000000));",
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
            "if (horizontalSpeedSq > 0.0025d && client.player.isOnGround())",
            "Minimap baseline must use grounded movement heading to avoid jump jitter."
        );
        assertContains(
            source,
            "private static float shortestAngleDeltaDegrees(float from, float to)",
            "Minimap baseline must include angular stability helper."
        );
        assertContains(
            source,
            "drawMiniMapCardinalLabels(drawContext, client, mapCenterX, mapCenterY, miniMapSize, mapRotationDegrees, hudAlpha);",
            "Minimap regression: rotating cardinal labels call is missing."
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

    private static void assertNotContains(String text, String forbidden, String message) {
        if (text.contains(forbidden)) {
            throw new RuntimeException(message + " Forbidden snippet: " + forbidden);
        }
    }
}
