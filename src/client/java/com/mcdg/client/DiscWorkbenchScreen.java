package com.mcdg.client;

import com.mcdg.game.DiscEnchantment;
import com.mcdg.game.DiscEnchantmentHelper;
import com.mcdg.game.DiscEnchantedBook;
import com.mcdg.game.DiscWorkbenchScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class DiscWorkbenchScreen extends HandledScreen<DiscWorkbenchScreenHandler> {

    private static final Identifier SLOT_BACKGROUND = Identifier.of("minecraft", "container/slot");

    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int CRAFT_BG = 0xFFB9B9B9;
    private static final int OUTSET_LIGHT = 0xFFFFFFFF;
    private static final int OUTSET_DARK = 0xFF373737;
    private static final int INSET_LIGHT = 0xFFFFFFFF;
    private static final int INSET_DARK = 0xFF555555;
    private static final int LABEL_TEXT = 0xFFFFFF;
    private static final int ARROW_COLOR = 0xFF404040;
    private static final int WARNING_COLOR = 0xFFFF5555;

    private ButtonWidget applyButton;
    private Text previewName = Text.empty();
    private Text previewEffect = Text.empty();
    private Text warningText = Text.empty();

    public DiscWorkbenchScreen(DiscWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = (this.backgroundWidth - this.textRenderer.getWidth(this.title)) / 2;
        this.titleY = 6;
        this.playerInventoryTitleY = 76;

        int buttonX = this.x + 135;
        int buttonY = this.y + 34;
        applyButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mcdg.disc_workbench.apply"), btn -> {
            this.client.interactionManager.clickButton(this.handler.syncId, 0);
        }).dimensions(buttonX, buttonY, 34, 14).build());
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;

        // Main panel with beveled border
        context.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL_BG);
        drawOutsetBorder(context, x, y, this.backgroundWidth, this.backgroundHeight);

        // Crafting area inset
        int craftX = x + 7;
        int craftY = y + 16;
        int craftW = this.backgroundWidth - 14;
        int craftH = 54;
        context.fill(craftX, craftY, craftX + craftW, craftY + craftH, CRAFT_BG);
        drawInsetBorder(context, craftX, craftY, craftW, craftH);

        // Slot backgrounds
        for (Slot slot : this.handler.slots) {
            context.drawGuiTexture(SLOT_BACKGROUND, x + slot.x, y + slot.y, 18, 18);
        }

        // Slot labels
        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.disc"), x + 44 + 9, y + 56);
        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.book"), x + 80 + 9, y + 56);
        drawSlotLabel(context, Text.translatable("gui.mcdg.disc_workbench.result"), x + 116 + 9, y + 56);

        // Flow symbols (centered between slot frames)
        int symbolY = y + 37;
        drawCenteredText(context, Text.literal("+"), x + 71, symbolY, ARROW_COLOR);
        drawCenteredText(context, Text.literal("→"), x + 107, symbolY, ARROW_COLOR);

        // Preview line (above the slots)
        if (!previewName.getString().isEmpty() || !previewEffect.getString().isEmpty()) {
            int previewY = y + 20;
            int combinedWidth = this.textRenderer.getWidth(previewName) + this.textRenderer.getWidth(previewEffect) + 4;
            int startX = x + (this.backgroundWidth - combinedWidth) / 2;
            context.drawTextWithShadow(this.textRenderer, previewName, startX, previewY, LABEL_TEXT);
            context.drawTextWithShadow(this.textRenderer, previewEffect, startX + this.textRenderer.getWidth(previewName) + 4, previewY, LABEL_TEXT);
        }

        // Warning line (above the slots, below the preview)
        if (!warningText.getString().isEmpty()) {
            int warningY = y + 29;
            int warningWidth = this.textRenderer.getWidth(warningText);
            context.drawTextWithShadow(this.textRenderer, warningText, x + (this.backgroundWidth - warningWidth) / 2, warningY, WARNING_COLOR);
        }
    }

    private void drawOutsetBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width - 1, y + 1, OUTSET_LIGHT);
        context.fill(x, y, x + 1, y + height - 1, OUTSET_LIGHT);
        context.fill(x + 1, y + height - 1, x + width, y + height, OUTSET_DARK);
        context.fill(x + width - 1, y + 1, x + width, y + height, OUTSET_DARK);
    }

    private void drawInsetBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width - 1, y + 1, INSET_DARK);
        context.fill(x, y, x + 1, y + height - 1, INSET_DARK);
        context.fill(x + 1, y + height - 1, x + width, y + height, INSET_LIGHT);
        context.fill(x + width - 1, y + 1, x + width, y + height, INSET_LIGHT);
    }

    private void drawSlotLabel(DrawContext context, Text label, int centerX, int y) {
        int width = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label, centerX - width / 2, y, 0x404040, false);
    }

    private void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        int width = this.textRenderer.getWidth(text);
        context.drawTextWithShadow(this.textRenderer, text, centerX - width / 2, y, color);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updatePreview();
        if (applyButton != null) {
            applyButton.active = this.handler.canApply();
            updateButtonTooltip();
        }
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void updatePreview() {
        ItemStack disc = this.handler.getInventory().getStack(0);
        ItemStack book = this.handler.getInventory().getStack(1);
        DiscEnchantment enchant = DiscEnchantedBook.getEnchantment(book);
        int level = DiscEnchantedBook.getLevel(book);

        if (!disc.isEmpty() && !book.isEmpty() && enchant != null && level > 0) {
            int currentLevel = DiscEnchantmentHelper.getLevel(disc, enchant);
            int percent = Math.round(enchant.perLevelMultiplier() * level * 100.0f);
            String roman = DiscEnchantedBook.roman(level);
            previewName = Text.literal(enchant.displayName() + " " + roman).formatted(enchant.color());
            previewEffect = Text.translatable(effectKey(enchant), roman, percent);
            if (currentLevel >= level) {
                warningText = Text.translatable("gui.mcdg.disc_workbench.warning.already_has", enchant.displayName()).formatted(Formatting.RED);
            } else {
                warningText = Text.empty();
            }
        } else {
            previewName = Text.empty();
            previewEffect = Text.empty();
            warningText = Text.empty();
        }
    }

    private static String effectKey(DiscEnchantment enchant) {
        return switch (enchant) {
            case GLIDE -> "tooltip.mcdg.enchanted_book.effect.glide";
            case FADE_CONTROL -> "tooltip.mcdg.enchanted_book.effect.fade_control";
            case DISTANCE -> "tooltip.mcdg.enchanted_book.effect.distance";
        };
    }

    private void updateButtonTooltip() {
        if (this.handler.canApply()) {
            applyButton.setTooltip(Tooltip.of(Text.translatable("gui.mcdg.disc_workbench.tooltip.ready")));
            return;
        }
        ItemStack disc = this.handler.getInventory().getStack(0);
        ItemStack book = this.handler.getInventory().getStack(1);
        Text tooltip;
        if (disc.isEmpty() || book.isEmpty()) {
            tooltip = Text.translatable("gui.mcdg.disc_workbench.tooltip.missing_items");
        } else if (this.handler.getInventory().getStack(2).isEmpty()) {
            tooltip = Text.translatable("gui.mcdg.disc_workbench.tooltip.no_upgrade");
        } else {
            tooltip = Text.translatable("gui.mcdg.disc_workbench.tooltip.result_not_empty");
        }
        applyButton.setTooltip(Tooltip.of(tooltip));
    }
}
