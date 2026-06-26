package com.mcdg.world;

import com.mcdg.game.ChallengeCourseManager;
import com.mcdg.game.ChallengeCourseType;
import com.mcdg.game.LostCourse;
import com.mcdg.McdgMod;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Places lost course entrances in the world during world generation.
 */
public final class LostCoursePlacement {
    private static final int LOST_COURSE_COUNT = 5;
    private static final int MIN_DISTANCE_FROM_SPAWN = 500;
    private static final int MAX_DISTANCE_FROM_SPAWN = 2000;
    private static final int MIN_COURSE_SEPARATION = 300;

    private LostCoursePlacement() {}

    /**
     * Places lost course entrances in the world.
     */
    public static void placeLostCourseEntrances(ServerWorld world, BlockPos spawnPos) {
        Random random = Random.create(world.getSeed());
        List<BlockPos> placedPositions = new ArrayList<>();

        for (int i = 0; i < LOST_COURSE_COUNT; i++) {
            BlockPos entrancePos = findValidEntrancePosition(world, spawnPos, random, placedPositions);
            if (entrancePos == null) {
                McdgMod.LOGGER.warn("Could not find valid position for lost course entrance {}", i);
                continue;
            }

            LostCourse course = generateLostCourse(entrancePos, random, i);
            ChallengeCourseManager.registerLostCourse(course);
            ChallengeCourseManager.placeLostCourseEntrance(world, entrancePos, course);
            placedPositions.add(entrancePos);

            McdgMod.LOGGER.info("Placed lost course entrance #{}: {} at ({}, {}, {})",
                i, course.name(), entrancePos.getX(), entrancePos.getY(), entrancePos.getZ());
        }

        McdgMod.LOGGER.info("Placed {} lost course entrances in the world", placedPositions.size());
    }

    /**
     * Finds a valid position for a lost course entrance.
     */
    private static BlockPos findValidEntrancePosition(ServerWorld world, BlockPos spawnPos, 
                                                       Random random, List<BlockPos> placedPositions) {
        for (int attempt = 0; attempt < 50; attempt++) {
            // Generate random position within distance range
            int distance = MIN_DISTANCE_FROM_SPAWN + random.nextInt(MAX_DISTANCE_FROM_SPAWN - MIN_DISTANCE_FROM_SPAWN);
            double angle = random.nextDouble() * 2 * Math.PI;
            
            int x = spawnPos.getX() + (int) (Math.cos(angle) * distance);
            int z = spawnPos.getZ() + (int) (Math.sin(angle) * distance);
            
            // Find surface position
            BlockPos surfacePos = SurfaceResolver.resolveSurfacePos(world, x, z);
            if (surfacePos == null) {
                continue;
            }

            // Check minimum separation from other courses
            boolean tooClose = false;
            for (BlockPos existing : placedPositions) {
                double dist = Math.sqrt(
                    Math.pow(surfacePos.getX() - existing.getX(), 2) +
                    Math.pow(surfacePos.getZ() - existing.getZ(), 2)
                );
                if (dist < MIN_COURSE_SEPARATION) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }

            // Check if position is valid (not in water, not in lava, etc.)
            if (isValidEntrancePosition(world, surfacePos)) {
                return surfacePos;
            }
        }

        return null;
    }

    /**
     * Checks if a position is valid for a course entrance.
     */
    private static boolean isValidEntrancePosition(ServerWorld world, BlockPos pos) {
        // Check if position is not in water or lava
        if (world.isWater(pos) || world.isWater(pos.up())) {
            return false;
        }

        // Check if position is not in lava
        if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.LAVA) ||
            world.getBlockState(pos.up()).isOf(net.minecraft.block.Blocks.LAVA)) {
            return false;
        }

        // Check if position is on solid ground
        if (!world.getBlockState(pos).isSolidBlock(world, pos)) {
            return false;
        }

        return true;
    }

    /**
     * Generates a lost course with random properties.
     */
    private static LostCourse generateLostCourse(BlockPos entrancePos, Random random, int index) {
        UUID courseId = UUID.randomUUID();
        ChallengeCourseType type = randomCourseType(random);
        String name = generateCourseName(type, index, random);
        
        // Course anchor is offset from entrance
        BlockPos courseAnchor = entrancePos.add(
            random.nextInt(100) - 50,
            0,
            random.nextInt(100) - 50
        );
        
        // Generate rewards based on course type
        List<ItemStack> rewards = generateRewards(type, random);

        return new LostCourse(courseId, name, entrancePos, courseAnchor, rewards, type, false);
    }

    /**
     * Selects a random course type with weighted probabilities.
     */
    private static ChallengeCourseType randomCourseType(Random random) {
        // Weight probabilities: Lost Course (40%), Boss Hole (20%), Time Trial (15%), 
        // Accuracy Challenge (15%), Distance Challenge (10%)
        double roll = random.nextDouble();
        
        if (roll < 0.40) return ChallengeCourseType.LOST_COURSE;
        if (roll < 0.60) return ChallengeCourseType.BOSS_HOLE;
        if (roll < 0.75) return ChallengeCourseType.TIME_TRIAL;
        if (roll < 0.90) return ChallengeCourseType.ACCURACY_CHALLENGE;
        return ChallengeCourseType.DISTANCE_CHALLENGE;
    }

    /**
     * Generates a name for the course.
     */
    private static String generateCourseName(ChallengeCourseType type, int index, Random random) {
        String[] lostNames = {
            "Forgotten Grove", "Ancient Ruins", "Hidden Valley", "Mystic Clearing",
            "Secret Sanctuary", "Lost Paradise", "Whispering Woods", "Shadow Hollow"
        };
        
        String[] bossNames = {
            "Guardian's Challenge", "The Beast's Lair", "Titan's Test", "Overlord's Arena"
        };
        
        String[] timeTrialNames = {
            "Speed Run", "Time Attack", "Velocity Challenge", "Sprint Course"
        };
        
        String[] accuracyNames = {
            "Precision Range", "Target Practice", "Sharpshooter's Haven", "Bullseye Valley"
        };
        
        String[] distanceNames = {
            "Long Drive", "Mega Distance", "Epic Throw", "Endurance Challenge"
        };

        String[] names = switch (type) {
            case LOST_COURSE -> lostNames;
            case BOSS_HOLE -> bossNames;
            case TIME_TRIAL -> timeTrialNames;
            case ACCURACY_CHALLENGE -> accuracyNames;
            case DISTANCE_CHALLENGE -> distanceNames;
        };

        String baseName = names[random.nextInt(names.length)];
        return baseName + " #" + (index + 1);
    }

    /**
     * Generates rewards based on course type.
     */
    private static List<ItemStack> generateRewards(ChallengeCourseType type, Random random) {
        List<ItemStack> rewards = new ArrayList<>();
        
        // Base reward: experience bottles
        rewards.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + random.nextInt(5)));
        
        // Type-specific rewards
        switch (type) {
            case LOST_COURSE -> {
                // Treasure chest rewards
                rewards.add(new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4)));
                if (random.nextFloat() < 0.3f) {
                    rewards.add(new ItemStack(Items.DIAMOND, 1));
                }
            }
            case BOSS_HOLE -> {
                // Combat rewards
                rewards.add(new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(5)));
                rewards.add(new ItemStack(Items.GOLDEN_APPLE, 1));
            }
            case TIME_TRIAL -> {
                // Speed rewards
                rewards.add(new ItemStack(Items.SUGAR, 5 + random.nextInt(10)));
                rewards.add(new ItemStack(Items.CLOCK, 1));
            }
            case ACCURACY_CHALLENGE -> {
                // Precision rewards
                rewards.add(new ItemStack(Items.ARROW, 16 + random.nextInt(16)));
                rewards.add(new ItemStack(Items.BOW, 1));
            }
            case DISTANCE_CHALLENGE -> {
                // Power rewards
                rewards.add(new ItemStack(Items.IRON_BLOCK, 1));
                rewards.add(new ItemStack(Items.ANVIL, 1));
            }
        }
        
        return rewards;
    }
}