package com.mcdg.game;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

/**
 * Defines special block palettes for challenge courses to distinguish them visually.
 * Each challenge type has unique blocks, colors, and banner patterns.
 */
public class ChallengeBlockPalette {
    
    /**
     * Gets the special block palette for a challenge type.
     */
    public static BlockPalette forType(ChallengeCourseType type) {
        return switch (type) {
            case LOST_COURSE -> lostCoursePalette();
            case BOSS_HOLE -> bossHolePalette();
            case TIME_TRIAL -> timeTrialPalette();
            case ACCURACY_CHALLENGE -> accuracyChallengePalette();
            case DISTANCE_CHALLENGE -> distanceChallengePalette();
        };
    }

    /**
     * Lost Course palette - mystical purple theme.
     */
    private static BlockPalette lostCoursePalette() {
        return new BlockPalette(
            Blocks.SEA_LANTERN,                    // tee marker
            Blocks.OAK_SIGN,                       // hole sign (use regular sign)
            Blocks.END_ROD,                        // fairway marker
            DyeColor.PURPLE,                       // banner color
            "Lost Course",                        // banner pattern name
            Formatting.DARK_PURPLE                 // text color
        );
    }

    /**
     * Boss Hole palette - dangerous red/black theme.
     */
    private static BlockPalette bossHolePalette() {
        return new BlockPalette(
            Blocks.SEA_LANTERN,                    // tee marker
            Blocks.OAK_SIGN,                       // hole sign
            Blocks.END_ROD,                        // fairway marker
            DyeColor.RED,                          // banner color
            "Boss Hole",                          // banner pattern name
            Formatting.DARK_RED                    // text color
        );
    }

    /**
     * Time Trial palette - speed yellow/orange theme.
     */
    private static BlockPalette timeTrialPalette() {
        return new BlockPalette(
            Blocks.SEA_LANTERN,                    // tee marker
            Blocks.OAK_SIGN,                       // hole sign
            Blocks.END_ROD,                        // fairway marker
            DyeColor.ORANGE,                       // banner color
            "Time Trial",                         // banner pattern name
            Formatting.GOLD                       // text color
        );
    }

    /**
     * Accuracy Challenge palette - precision blue/cyan theme.
     */
    private static BlockPalette accuracyChallengePalette() {
        return new BlockPalette(
            Blocks.SEA_LANTERN,                    // tee marker
            Blocks.OAK_SIGN,                       // hole sign
            Blocks.END_ROD,                        // fairway marker
            DyeColor.CYAN,                         // banner color
            "Accuracy",                           // banner pattern name
            Formatting.AQUA                        // text color
        );
    }

    /**
     * Distance Challenge palette - power green/lime theme.
     */
    private static BlockPalette distanceChallengePalette() {
        return new BlockPalette(
            Blocks.SEA_LANTERN,                    // tee marker
            Blocks.OAK_SIGN,                       // hole sign
            Blocks.END_ROD,                        // fairway marker
            DyeColor.LIME,                         // banner color
            "Distance",                           // banner pattern name
            Formatting.GREEN                       // text color
        );
    }

    /**
     * Creates a special banner for a challenge course.
     */
    public static ItemStack createSpecialBanner(ChallengeCourseType type) {
        BlockPalette palette = forType(type);
        
        // Use the colored banner item directly
        ItemStack banner = switch (palette.bannerColor()) {
            case PURPLE -> new ItemStack(Items.PURPLE_BANNER);
            case RED -> new ItemStack(Items.RED_BANNER);
            case ORANGE -> new ItemStack(Items.ORANGE_BANNER);
            case CYAN -> new ItemStack(Items.CYAN_BANNER);
            case LIME -> new ItemStack(Items.LIME_BANNER);
            default -> new ItemStack(Items.WHITE_BANNER);
        };
        
        // Set custom name
        banner.set(DataComponentTypes.CUSTOM_NAME, 
            Text.literal(palette.bannerPatternName()).formatted(palette.textColor()));
        
        return banner;
    }

    /**
     * Block palette record for challenge courses.
     */
    public record BlockPalette(
        Block teeMarker,         // Block used for tee box markers
        Block holeSign,          // Block used for hole information signs
        Block fairwayMarker,     // Block used for fairway path markers
        DyeColor bannerColor,    // Color for special banners
        String bannerPatternName, // Name for banner pattern
        Formatting textColor     // Text color for signs
    ) {}
}