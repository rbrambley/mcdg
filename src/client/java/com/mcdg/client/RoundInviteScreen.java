package com.mcdg.client;

import com.mcdg.net.RoundInviteResponse;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Popup screen shown to a player when they are invited to a round.
 * They can accept or reject the invite before the timeout expires.
 */
public final class RoundInviteScreen extends Screen {

    private static final int BG_COLOR = 0xF0111820;
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 140;

    private final UUID initiatorId;
    private final String initiatorName;
    private final String courseName;
    private final int catalogIndex;
    private boolean responded = false;

    public RoundInviteScreen(UUID initiatorId, String initiatorName, String courseName, int catalogIndex) {
        super(Text.literal("Round Invite"));
        this.initiatorId = initiatorId;
        this.initiatorName = initiatorName;
        this.courseName = courseName;
        this.catalogIndex = catalogIndex;
    }

    @Override
    protected void init() {
        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;
        int btnY = panelY + PANEL_H - 36;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), b -> respond(true))
                .dimensions(panelX + 20, btnY, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Decline"), b -> respond(false))
                .dimensions(panelX + PANEL_W - 120, btnY, 100, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xB0000000, 0xB0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = (this.height - PANEL_H) / 2;

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, BG_COLOR);
        context.drawBorder(panelX, panelY, PANEL_W, PANEL_H, 0xFF3A5A7A);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Round Invite").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 12, panelY + 10, 0xFFFFFF);

        String fromText = initiatorName != null ? initiatorName : "Someone";
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(fromText + " invited you to play").formatted(Formatting.WHITE),
                panelX + 12, panelY + 36, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("\"" + courseName + "\"").formatted(Formatting.GOLD),
                panelX + 12, panelY + 52, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Accept to join the round.").formatted(Formatting.GRAY),
                panelX + 12, panelY + 76, 0xAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    private void respond(boolean accepted) {
        if (responded) {
            return;
        }
        responded = true;
        ClientPlayNetworking.send(new RoundInviteResponse.Payload(initiatorId, accepted));
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            respond(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
