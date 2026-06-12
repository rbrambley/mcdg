package com.mcdg.client;

import com.mcdg.world.SeededCourseGenerator;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Random;

public class AutoCourseNameScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget nameField;

    public AutoCourseNameScreen(Screen parent) {
        super(Text.literal("Name Your Course"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelW = 280;
        int panelH = 110;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        String defaultName = buildDefaultName();

        nameField = new TextFieldWidget(this.textRenderer, panelX + 10, panelY + 34, panelW - 20, 20, Text.literal(""));
        nameField.setMaxLength(48);
        nameField.setText(defaultName);
        nameField.setPlaceholder(Text.literal("Course name...").formatted(Formatting.DARK_GRAY));
        nameField.setFocused(true);
        this.addSelectableChild(nameField);

        int btnY = panelY + panelH - 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Build"), btn -> build())
                .dimensions(panelX + 10, btnY, 80, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> cancel())
                .dimensions(panelX + panelW - 90, btnY, 80, 20)
                .build());
    }

    private String buildDefaultName() {
        String biome = resolvePlayerBiomeName();
        String randomName = SeededCourseGenerator.generateCourseName(new Random());
        if (biome.isBlank()) {
            return randomName;
        }
        return "[" + biome + "] " + randomName;
    }

    private String resolvePlayerBiomeName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            return "";
        }
        BlockPos pos = client.player.getBlockPos();
        RegistryEntry<Biome> biomeEntry = client.world.getBiome(pos);
        String id = biomeId(biomeEntry);
        return formatBiomeName(id);
    }

    private static String biomeId(RegistryEntry<Biome> biome) {
        RegistryKey<Biome> key = biome.getKey().orElse(null);
        if (key == null) {
            return "";
        }
        return key.getValue().getPath();
    }

    private static String formatBiomeName(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "";
        }
        String cleaned = biomeId.replace("minecraft:", "");
        String[] parts = cleaned.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.toString();
    }

    private void build() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            name = SeededCourseGenerator.generateCourseName(new Random());
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            // Escape any brackets or quotes that could break the greedy string parser
            String safeName = name.replace("\"", "'");
            player.networkHandler.sendChatCommand("mcdg autocourse " + safeName);
        }
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    private void cancel() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xB0000000, 0xB0000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int panelW = 280;
        int panelH = 110;
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101820);
        context.drawBorder(panelX, panelY, panelW, panelH, 0xFF334455);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Name Your Course").formatted(Formatting.AQUA, Formatting.BOLD),
                panelX + 10, panelY + 10, 0xFFFFFF);

        nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            build();
            return true;
        }
        if (keyCode == 256) {
            cancel();
            return true;
        }
        return nameField.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return nameField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
