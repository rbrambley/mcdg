package com.mcdg.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.util.InputUtil;

/**
 * Renders ace and round-complete cinematic overlays and handles
 * their timed particle effects and skip-on-movement behavior.
 */
public final class CinematicOverlay {
    private static final long ACE_CINEMATIC_DURATION_MS = 3600L;
    private static final long ACE_CINEMATIC_PARTICLE_STEP_MS = 80L;
    private static final long ROUND_COMPLETE_CINEMATIC_DURATION_MS = 20000L;

    private static AceCinematicState aceState;
    private static long nextAceParticleAtMs;
    private static RoundCompleteCinematicState roundCompleteState;

    private CinematicOverlay() {
    }

    public static void activateAce(int holeIndex, int distanceFeet) {
        long now = System.currentTimeMillis();
        aceState = new AceCinematicState(holeIndex, distanceFeet, now, now + ACE_CINEMATIC_DURATION_MS);
        nextAceParticleAtMs = now;
    }

    public static void clearAce() {
        aceState = null;
    }

    public static void activateRoundComplete(
            int totalPar,
            int totalPlayers,
            String firstName,
            int firstScore,
            String secondName,
            int secondScore,
            String thirdName,
            int thirdScore,
            int localRank,
            int localScore
    ) {
        long now = System.currentTimeMillis();
        roundCompleteState = new RoundCompleteCinematicState(
                totalPar,
                totalPlayers,
                firstName,
                firstScore,
                secondName,
                secondScore,
                thirdName,
                thirdScore,
                localRank,
                localScore,
                now,
                now + ROUND_COMPLETE_CINEMATIC_DURATION_MS
        );
    }

    public static void clearRoundComplete() {
        roundCompleteState = null;
    }

    public static void tick(MinecraftClient client) {
        tickAceParticles(client);
        tickRoundCompleteSkip(client);
    }

    private static void tickAceParticles(MinecraftClient client) {
        if (aceState == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= aceState.endAtMs()) {
            aceState = null;
            return;
        }

        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (now < nextAceParticleAtMs) {
            return;
        }

        nextAceParticleAtMs = now + ACE_CINEMATIC_PARTICLE_STEP_MS;
        double centerX = client.player.getX();
        double centerY = client.player.getY() + 1.2;
        double centerZ = client.player.getZ();
        double phase = (now - aceState.startAtMs()) / 150.0d;

        for (int i = 0; i < 14; i++) {
            double angle = phase + ((Math.PI * 2.0d * i) / 14.0d);
            double radius = 0.9d + ((i % 3) * 0.18d);
            double px = centerX + (Math.cos(angle) * radius);
            double pz = centerZ + (Math.sin(angle) * radius);
            double vy = 0.02d + ((i % 4) * 0.01d);
            client.world.addParticle(ParticleTypes.END_ROD, px, centerY + ((i % 3) * 0.08d), pz, 0.0d, vy, 0.0d);
        }
    }

    private static void tickRoundCompleteSkip(MinecraftClient client) {
        if (roundCompleteState == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= roundCompleteState.endAtMs()) {
            roundCompleteState = null;
            return;
        }

        if (client == null || client.player == null || client.currentScreen != null) {
            roundCompleteState = null;
            return;
        }

        long handle = client.getWindow().getHandle();
        if (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_W)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_A)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_S)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_D)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_SPACE)) {
            roundCompleteState = null;
        }
    }

    public static void render(DrawContext drawContext) {
        renderAce(drawContext);
        renderRoundComplete(drawContext);
    }

    private static void renderAce(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null || aceState == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= aceState.endAtMs()) {
            aceState = null;
            return;
        }

        float alpha = computeFadeAlpha(now, aceState.startAtMs(), aceState.endAtMs(), 0.16f, 0.22f);
        if (alpha <= 0.0f) {
            return;
        }

        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        int cardW = 238;
        int cardH = 72;
        int x = (width - cardW) / 2;
        int y = Math.max(18, (height / 2) - 120);

        drawContext.fill(x, y, x + cardW, y + cardH, withAlpha(0xC0141820, alpha));
        drawContext.fill(x, y, x + cardW, y + 14, withAlpha(0xE3987A19, alpha));
        drawContext.fill(x, y, x + cardW, y + 1, withAlpha(0xFFE5BD4A, alpha));
        drawContext.fill(x, y + cardH - 1, x + cardW, y + cardH, withAlpha(0xFFE5BD4A, alpha));
        drawContext.fill(x, y, x + 1, y + cardH, withAlpha(0xFFE5BD4A, alpha));
        drawContext.fill(x + cardW - 1, y, x + cardW, y + cardH, withAlpha(0xFFE5BD4A, alpha));

        String title = "ACE!";
        String sub1 = "Hole-in-One";
        String sub2 = "Hole " + aceState.holeIndex() + "  Dist " + aceState.distanceFeet() + " ft";
        int titleX = x + ((cardW - client.textRenderer.getWidth(title)) / 2);
        int sub1X = x + ((cardW - client.textRenderer.getWidth(sub1)) / 2);
        int sub2X = x + ((cardW - client.textRenderer.getWidth(sub2)) / 2);

        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(title).formatted(Formatting.GOLD, Formatting.BOLD), titleX, y + 18, withAlpha(0xFFF6D15A, alpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(sub1).formatted(Formatting.YELLOW), sub1X, y + 35, withAlpha(0xFFF3E5B3, alpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(sub2).formatted(Formatting.WHITE), sub2X, y + 49, withAlpha(0xFFF7F8FB, alpha));
    }

    private static void renderRoundComplete(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || client.textRenderer == null || roundCompleteState == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= roundCompleteState.endAtMs()) {
            roundCompleteState = null;
            return;
        }

        float alpha = computeFadeAlpha(now, roundCompleteState.startAtMs(), roundCompleteState.endAtMs(), 0.14f, 0.18f);
        if (alpha <= 0.0f) {
            return;
        }

        int width = drawContext.getScaledWindowWidth();
        int height = drawContext.getScaledWindowHeight();
        int cardW = 312;
        int cardH = 162;
        int x = (width - cardW) / 2;
        int y = Math.max(18, (height / 2) - 134);

        drawContext.fill(x, y, x + cardW, y + cardH, withAlpha(0xCC121720, alpha));
        drawContext.fill(x, y, x + cardW, y + 16, withAlpha(0xE3947A24, alpha));
        drawContext.fill(x, y, x + cardW, y + 1, withAlpha(0xFFE0C468, alpha));
        drawContext.fill(x, y + cardH - 1, x + cardW, y + cardH, withAlpha(0xFFE0C468, alpha));
        drawContext.fill(x, y, x + 1, y + cardH, withAlpha(0xFFE0C468, alpha));
        drawContext.fill(x + cardW - 1, y, x + cardW, y + cardH, withAlpha(0xFFE0C468, alpha));

        String title = "Round Complete";
        String subtitle = roundCompleteState.totalPlayers() + " Players  |  Par " + roundCompleteState.totalPar();
        int titleX = x + ((cardW - client.textRenderer.getWidth(title)) / 2);
        int subtitleX = x + ((cardW - client.textRenderer.getWidth(subtitle)) / 2);
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(title).formatted(Formatting.GOLD, Formatting.BOLD), titleX, y + 22, withAlpha(0xFFF5D57A, alpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(subtitle).formatted(Formatting.YELLOW), subtitleX, y + 37, withAlpha(0xFFEFE4BF, alpha));

        drawPodiumLine(drawContext, client, x + 20, y + 62, 1, roundCompleteState.firstName(), roundCompleteState.firstScore(), roundCompleteState.totalPar(), alpha);
        drawPodiumLine(drawContext, client, x + 20, y + 78, 2, roundCompleteState.secondName(), roundCompleteState.secondScore(), roundCompleteState.totalPar(), alpha);
        drawPodiumLine(drawContext, client, x + 20, y + 94, 3, roundCompleteState.thirdName(), roundCompleteState.thirdScore(), roundCompleteState.totalPar(), alpha);

        String local;
        if (roundCompleteState.localRank() > 0) {
            int delta = roundCompleteState.localScore() - roundCompleteState.totalPar();
            String deltaText = delta == 0 ? "E" : (delta > 0 ? "+" + delta : Integer.toString(delta));
            local = "You: #" + roundCompleteState.localRank() + "  Score " + roundCompleteState.localScore() + " (" + deltaText + ")";
        } else {
            local = "You: spectator";
        }
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(local).formatted(Formatting.WHITE), x + 20, y + 122, withAlpha(0xFFF5F7FB, alpha));
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal("Press movement key or jump to skip").formatted(Formatting.GRAY), x + 20, y + 138, withAlpha(0xFFABB5C2, alpha));
    }

    private static void drawPodiumLine(
            DrawContext drawContext,
            MinecraftClient client,
            int x,
            int y,
            int rank,
            String name,
            int score,
            int par,
            float alpha
    ) {
        String safeName = (name == null || name.isBlank()) ? "-" : name;
        int delta = score - par;
        String deltaText = delta == 0 ? "E" : (delta > 0 ? "+" + delta : Integer.toString(delta));
        String line = "#" + rank + "  " + safeName + "  " + score + " (" + deltaText + ")";
        drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line), x, y, withAlpha(0xFFE5ECF7, alpha));
    }

    private static float computeFadeAlpha(long now, long startAtMs, long endAtMs, float fadeInRatio, float fadeOutRatio) {
        float duration = Math.max(1.0f, (float) (endAtMs - startAtMs));
        float progress = Math.max(0.0f, Math.min(1.0f, (now - startAtMs) / duration));
        float fadeIn = Math.min(1.0f, progress / fadeInRatio);
        float fadeOut = Math.min(1.0f, (1.0f - progress) / fadeOutRatio);
        return Math.max(0.0f, Math.min(fadeIn, fadeOut));
    }

    private static int withAlpha(int argb, float alphaFactor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int appliedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alphaFactor)));
        return (argb & 0x00FFFFFF) | (appliedAlpha << 24);
    }

    private record AceCinematicState(
            int holeIndex,
            int distanceFeet,
            long startAtMs,
            long endAtMs
    ) {
    }

    private record RoundCompleteCinematicState(
            int totalPar,
            int totalPlayers,
            String firstName,
            int firstScore,
            String secondName,
            int secondScore,
            String thirdName,
            int thirdScore,
            int localRank,
            int localScore,
            long startAtMs,
            long endAtMs
    ) {
    }
}
