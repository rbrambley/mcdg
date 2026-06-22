package com.mcdg.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/**
 * Block palette for biome-themed course construction.
 * Each theme provides appropriate blocks for tees, baskets, hubs,
 * fairways, and decorative accents so courses feel native to their biome.
 */
public record BiomeTheme(
        String name,
        BlockState teePadBase,
        BlockState teePadCenter,
        BlockState basketGround,
        BlockState basketBase,
        BlockState basketPole,
        BlockState basketLantern,
        BlockState lanternPost,
        BlockState lanternLight,
        BlockState hubDeckRim,
        BlockState hubDeckCenter,
        BlockState hubWalkway,
        BlockState registrationDeskTop,
        BlockState registrationDeskLegs,
        BlockState registrationDeskTerminals,
        BlockState merchCanopyRoof,
        BlockState merchCanopyPosts,
        BlockState merchCanopyTables,
        BlockState fairwayPath,
        BlockState banner,
        BlockState bannerPole,
        BlockState signatureRing
) {
    /**
     * Vanilla / Plains default — dirt path, oak, smooth stone.
     */
    public static final BiomeTheme DEFAULT = new BiomeTheme(
            "default",
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.POLISHED_ANDESITE.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(),
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.SMOOTH_STONE_SLAB.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.WHITE_WOOL.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.DIRT_PATH.getDefaultState(),
            Blocks.WHITE_BANNER.getDefaultState(),
            Blocks.OAK_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );

    /**
     * Forest — coarse dirt, dark oak, mossy accents.
     */
    public static final BiomeTheme FOREST = new BiomeTheme(
            "forest",
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.COBBLESTONE.getDefaultState(),
            Blocks.DARK_OAK_PLANKS.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.SPRUCE_SLAB.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.GREEN_WOOL.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.GREEN_BANNER.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState()
    );

    /**
     * Desert — sandstone, smooth sandstone, jungle wood accents.
     */
    public static final BiomeTheme DESERT = new BiomeTheme(
            "desert",
            Blocks.SMOOTH_SANDSTONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.SAND.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.CUT_SANDSTONE.getDefaultState(),
            Blocks.SMOOTH_SANDSTONE.getDefaultState(),
            Blocks.SANDSTONE.getDefaultState(),
            Blocks.SANDSTONE_SLAB.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.ORANGE_WOOL.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.SANDSTONE.getDefaultState(),
            Blocks.ORANGE_BANNER.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );

    /**
     * Badlands — red sandstone, terracotta, acacia wood.
     */
    public static final BiomeTheme BADLANDS = new BiomeTheme(
            "badlands",
            Blocks.CUT_RED_SANDSTONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.RED_SAND.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.TERRACOTTA.getDefaultState(),
            Blocks.ACACIA_PLANKS.getDefaultState(),
            Blocks.RED_SANDSTONE.getDefaultState(),
            Blocks.RED_SANDSTONE_SLAB.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.RED_WOOL.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.RED_SANDSTONE.getDefaultState(),
            Blocks.RED_BANNER.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.ORANGE_CONCRETE.getDefaultState()
    );

    /**
     * Mountain — gravel, stone, andesite, spruce.
     */
    public static final BiomeTheme MOUNTAIN = new BiomeTheme(
            "mountain",
            Blocks.ANDESITE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.POLISHED_ANDESITE.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(),
            Blocks.GRAVEL.getDefaultState(),
            Blocks.POLISHED_ANDESITE_SLAB.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.LIGHT_GRAY_WOOL.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.GRAVEL.getDefaultState(),
            Blocks.LIGHT_GRAY_BANNER.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );

    /**
     * Snowy — packed ice, snow blocks, spruce.
     */
    public static final BiomeTheme SNOWY = new BiomeTheme(
            "snowy",
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.SNOW_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState(),
            Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(),
            Blocks.PACKED_ICE.getDefaultState(),
            Blocks.STONE_BRICK_SLAB.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.LIGHT_BLUE_WOOL.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.PACKED_ICE.getDefaultState(),
            Blocks.LIGHT_BLUE_BANNER.getDefaultState(),
            Blocks.SPRUCE_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );

    /**
     * Jungle — mossy stone, jungle wood, coarse dirt.
     */
    public static final BiomeTheme JUNGLE = new BiomeTheme(
            "jungle",
            Blocks.MOSSY_COBBLESTONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.JUNGLE_PLANKS.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.JUNGLE_SLAB.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.GREEN_WOOL.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.GREEN_BANNER.getDefaultState(),
            Blocks.JUNGLE_FENCE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState()
    );

    /**
     * Savanna — acacia wood, coarse dirt.
     */
    public static final BiomeTheme SAVANNA = new BiomeTheme(
            "savanna",
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.GRASS_BLOCK.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.GRANITE.getDefaultState(),
            Blocks.ACACIA_PLANKS.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.ACACIA_SLAB.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.YELLOW_WOOL.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.YELLOW_BANNER.getDefaultState(),
            Blocks.ACACIA_FENCE.getDefaultState(),
            Blocks.ORANGE_CONCRETE.getDefaultState()
    );

    /**
     * Nether — blackstone, nether brick, crimson/warped wood.
     */
    public static final BiomeTheme NETHER = new BiomeTheme(
            "nether",
            Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.CRIMSON_NYLIUM.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState(),
            Blocks.CRIMSON_FENCE.getDefaultState(),
            Blocks.SOUL_LANTERN.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.WARPED_PLANKS.getDefaultState(),
            Blocks.BLACKSTONE.getDefaultState(),
            Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState(),
            Blocks.CRIMSON_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.BLACK_WOOL.getDefaultState(),
            Blocks.CRIMSON_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.BLACKSTONE.getDefaultState(),
            Blocks.BLACK_BANNER.getDefaultState(),
            Blocks.CRIMSON_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );

    /**
     * Swamp — mud, dark oak, mossy stone.
     */
    public static final BiomeTheme SWAMP = new BiomeTheme(
            "swamp",
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.MUD.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.MUDDY_MANGROVE_ROOTS.getDefaultState(),
            Blocks.DARK_OAK_PLANKS.getDefaultState(),
            Blocks.MUD.getDefaultState(),
            Blocks.DARK_OAK_SLAB.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.GREEN_WOOL.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.MUD.getDefaultState(),
            Blocks.GREEN_BANNER.getDefaultState(),
            Blocks.DARK_OAK_FENCE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState()
    );

    /**
     * Beach / Ocean — sand, smooth stone, birch.
     */
    public static final BiomeTheme BEACH = new BiomeTheme(
            "beach",
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.SAND.getDefaultState(),
            Blocks.HOPPER.getDefaultState(),
            Blocks.IRON_BARS.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.BIRCH_FENCE.getDefaultState(),
            Blocks.LANTERN.getDefaultState(),
            Blocks.SMOOTH_STONE.getDefaultState(),
            Blocks.BIRCH_PLANKS.getDefaultState(),
            Blocks.SMOOTH_SANDSTONE.getDefaultState(),
            Blocks.BIRCH_SLAB.getDefaultState(),
            Blocks.BIRCH_FENCE.getDefaultState(),
            Blocks.DAYLIGHT_DETECTOR.getDefaultState(),
            Blocks.CYAN_WOOL.getDefaultState(),
            Blocks.BIRCH_FENCE.getDefaultState(),
            Blocks.BARREL.getDefaultState(),
            Blocks.SAND.getDefaultState(),
            Blocks.CYAN_BANNER.getDefaultState(),
            Blocks.BIRCH_FENCE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
    );
}
