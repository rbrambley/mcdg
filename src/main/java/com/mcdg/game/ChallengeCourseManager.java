package com.mcdg.game;

import com.mcdg.McdgMod;
import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.world.SeededCourseGenerator;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages challenge course discovery, generation, and rewards.
 */
public final class ChallengeCourseManager {
    private static final Map<UUID, LostCourse> LOST_COURSES = new ConcurrentHashMap<>();
    private static ChallengeCourseCatalog catalog;
    private static final SeededCourseGenerator COURSE_GENERATOR = new SeededCourseGenerator();
    private static final int BOSS_HOLE_DISTANCE_FEET = 600;
    private static final int TIME_TRIAL_HOLE_COUNT = 3;
    private static final int TIME_TRIAL_PAR_BONUS_SECONDS = 30;

    private ChallengeCourseManager() {}

    /**
     * Initializes the challenge course manager with a catalog.
     */
    public static void initialize(ChallengeCourseCatalog catalog) {
        ChallengeCourseManager.catalog = catalog;
    }

    /**
     * Gets the current catalog.
     */
    public static Optional<ChallengeCourseCatalog> getCatalog() {
        return Optional.ofNullable(catalog);
    }

    /**
     * Registers a new lost course in the world.
     */
    public static void registerLostCourse(LostCourse course) {
        LOST_COURSES.put(course.courseId(), course);
        McdgMod.LOGGER.info("Registered lost course: {} at entrance ({}, {}, {})",
            course.name(), course.entrancePosition().getX(), 
            course.entrancePosition().getY(), course.entrancePosition().getZ());
    }

    /**
     * Gets a lost course by ID.
     */
    public static Optional<LostCourse> getLostCourse(UUID courseId) {
        return Optional.ofNullable(LOST_COURSES.get(courseId));
    }

    /**
     * Gets all lost courses.
     */
    public static List<LostCourse> getAllLostCourses() {
        return new ArrayList<>(LOST_COURSES.values());
    }

    /**
     * Gets all undiscovered lost courses.
     */
    public static List<LostCourse> getUndiscoveredCourses() {
        return LOST_COURSES.values().stream()
            .filter(course -> !course.isDiscovered())
            .toList();
    }

    /**
     * Handles course discovery when a player finds an entrance.
     */
    public static void onCourseDiscovery(ServerPlayerEntity player, UUID courseId) {
        LostCourse course = LOST_COURSES.get(courseId);
        if (course == null) {
            McdgMod.LOGGER.warn("Player {} attempted to discover unknown course {}", 
                player.getName().getString(), courseId);
            return;
        }

        if (catalog == null) {
            McdgMod.LOGGER.error("Challenge course catalog not initialized");
            return;
        }

        // Check if already discovered in catalog
        if (catalog.getCourse(courseId).isPresent()) {
            player.sendMessage(Text.literal("This course has already been discovered.")
                .formatted(Formatting.GRAY));
            return;
        }

        // Generate the actual course
        Course generatedCourse = generateChallengeCourse(course);
        if (generatedCourse == null) {
            McdgMod.LOGGER.error("Failed to generate challenge course {}", course.name());
            return;
        }

        // Add to catalog
        catalog.addOrUpdateCourse(course, generatedCourse);

        // Mark as discovered in the original lost course tracking
        LostCourse discoveredCourse = course.markDiscovered();
        LOST_COURSES.put(courseId, discoveredCourse);

        // Notify player
        player.sendMessage(Text.literal("Discovered " + course.name() + "!")
            .formatted(Formatting.GOLD));
        player.sendMessage(Text.literal(course.type().getDescription())
            .formatted(Formatting.YELLOW));

        // Give discovery reward
        player.giveItemStack(new ItemStack(Items.EXPERIENCE_BOTTLE, 5));

        // Mark that this player has claimed discovery rewards
        catalog.markDiscoveryRewardClaimed(courseId, player.getUuid());

        McdgMod.LOGGER.info("Player {} discovered course {}", 
            player.getName().getString(), course.name());
    }

    /**
     * Generates a challenge course based on its type.
     */
    public static Course generateChallengeCourse(LostCourse lostCourse) {
        Random random = new Random(lostCourse.courseId().getLeastSignificantBits());
        
        return switch (lostCourse.type()) {
            case LOST_COURSE -> generateStandardCourse(lostCourse.courseAnchor(), random);
            case BOSS_HOLE -> generateBossHole(lostCourse.courseAnchor(), random);
            case TIME_TRIAL -> generateTimeTrialCourse(lostCourse.courseAnchor(), random);
            case ACCURACY_CHALLENGE -> generateAccuracyChallenge(lostCourse.courseAnchor(), random);
            case DISTANCE_CHALLENGE -> generateDistanceChallenge(lostCourse.courseAnchor(), random);
        };
    }

    /**
     * Generates a standard 9-hole course for lost courses.
     */
    private static Course generateStandardCourse(BlockPos anchor, Random random) {
        long seed = random.nextLong();
        return COURSE_GENERATOR.generate(seed, 9);
    }

    /**
     * Generates a single challenging boss hole.
     */
    private static Course generateBossHole(BlockPos anchor, Random random) {
        long seed = random.nextLong();
        Course singleHoleCourse = COURSE_GENERATOR.generate(seed, 1);
        
        // Adjust the hole to be more challenging
        Hole original = singleHoleCourse.holes().get(0);
        Hole bossHole = new Hole(
            original.index(),
            Math.max(original.par(), 4), // Ensure at least Par 4
            Math.max(original.distanceFeet(), BOSS_HOLE_DISTANCE_FEET),
            original.tee(),
            original.basket(),
            original.fairwaySegments(),
            original.signatureType()
        );
        
        return new Course(seed, "Boss Hole Challenge", List.of(bossHole));
    }

    /**
     * Generates a short 3-hole time trial course.
     */
    private static Course generateTimeTrialCourse(BlockPos anchor, Random random) {
        long seed = random.nextLong();
        return COURSE_GENERATOR.generate(seed, TIME_TRIAL_HOLE_COUNT);
    }

    /**
     * Generates an accuracy challenge course with short holes.
     */
    private static Course generateAccuracyChallenge(BlockPos anchor, Random random) {
        long seed = random.nextLong();
        Course course = COURSE_GENERATOR.generate(seed, 5);
        
        // Modify holes to be shorter for accuracy focus
        List<Hole> accuracyHoles = new ArrayList<>();
        for (Hole hole : course.holes()) {
            Hole accuracyHole = new Hole(
                hole.index(),
                3, // All Par 3
                Math.min(hole.distanceFeet(), 300), // Max 300 ft
                hole.tee(),
                hole.basket(),
                hole.fairwaySegments(),
                hole.signatureType()
            );
            accuracyHoles.add(accuracyHole);
        }
        
        return new Course(seed, "Accuracy Challenge", accuracyHoles);
    }

    /**
     * Generates a distance challenge with one very long hole.
     */
    private static Course generateDistanceChallenge(BlockPos anchor, Random random) {
        long seed = random.nextLong();
        Course singleHoleCourse = COURSE_GENERATOR.generate(seed, 1);
        
        // Make the hole very long
        Hole original = singleHoleCourse.holes().get(0);
        Hole distanceHole = new Hole(
            original.index(),
            5, // Par 5
            Math.max(original.distanceFeet(), 1000), // At least 1000 ft
            original.tee(),
            original.basket(),
            original.fairwaySegments(),
            original.signatureType()
        );
        
        return new Course(seed, "Distance Challenge", List.of(distanceHole));
    }

    /**
     * Handles challenge course completion and rewards.
     */
    public static void onChallengeCourseComplete(ServerPlayerEntity player, UUID courseId, int score, int totalPar) {
        if (catalog == null) {
            McdgMod.LOGGER.error("Challenge course catalog not initialized");
            return;
        }

        var catalogEntry = catalog.getCourse(courseId);
        if (catalogEntry.isEmpty()) {
            McdgMod.LOGGER.warn("Player {} completed unknown course {}", 
                player.getName().getString(), courseId);
            return;
        }

        LostCourse course = LOST_COURSES.get(courseId);
        if (course == null) {
            McdgMod.LOGGER.warn("Lost course data not found for {}", courseId);
            return;
        }

        // Check if player has already completed this course
        if (catalogEntry.get().playerCompletions().containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("You have already completed this course. No additional rewards.")
                .formatted(Formatting.GRAY));
            // Still record the score if it's better
            catalog.recordCourseCompletion(courseId, player.getUuid(), score);
            return;
        }

        // Base rewards
        for (ItemStack reward : course.rewards()) {
            player.giveItemStack(reward.copy());
        }

        // Performance bonuses
        if (score <= totalPar) {
            int underPar = totalPar - score;
            int diamondCount = underPar >= 3 ? 3 : underPar >= 1 ? 1 : 0;
            if (diamondCount > 0) {
                player.giveItemStack(new ItemStack(Items.DIAMOND, diamondCount));
                player.sendMessage(Text.literal("Under-par bonus: +" + diamondCount + " Diamond(s)")
                    .formatted(Formatting.AQUA));
            }
        }

        // Special disc rewards for exceptional performance (ace on single-hole courses)
        boolean isSingleHoleCourse = course.type() == ChallengeCourseType.BOSS_HOLE ||
                                    course.type() == ChallengeCourseType.DISTANCE_CHALLENGE;
        if (score == 1 && isSingleHoleCourse) {
            player.giveItemStack(createEnchantedDisc());
            player.sendMessage(Text.literal("ACE! You received an enchanted disc!")
                .formatted(Formatting.GOLD));
        }

        // Record the completion
        catalog.recordCourseCompletion(courseId, player.getUuid(), score);

        // Show best score info
        catalog.getBestScore(courseId).ifPresent(bestScore -> {
            if (score == bestScore) {
                player.sendMessage(Text.literal("New best score on this course!")
                    .formatted(Formatting.GREEN));
            } else {
                player.sendMessage(Text.literal("Best score on this course: " + bestScore)
                    .formatted(Formatting.YELLOW));
            }
        });

        McdgMod.LOGGER.info("Player {} completed challenge course {} with score {} (par: {})",
            player.getName().getString(), course.name(), score, totalPar);
    }

    /**
     * Creates an enchanted disc as a special reward.
     */
    private static ItemStack createEnchantedDisc() {
        // This would create a tiered disc with enchantments
        // For now, return a diamond as placeholder
        return new ItemStack(Items.DIAMOND);
    }

    /**
     * Places a lost course entrance in the world.
     */
    public static void placeLostCourseEntrance(ServerWorld world, BlockPos pos, LostCourse course) {
        // Place subtle marker (mossy cobblestone)
        world.setBlockState(pos, Blocks.MOSSY_COBBLESTONE.getDefaultState());

        // Place hidden chest with course map fragment
        BlockPos chestPos = pos.up();
        world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());

        // Add course map fragment to chest
        if (world.getBlockEntity(chestPos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
            ItemStack mapFragment = createCourseMapFragment(course);
            chest.setStack(0, mapFragment);
        }

        McdgMod.LOGGER.info("Placed lost course entrance for {} at ({}, {}, {})",
            course.name(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Creates a course map fragment item.
     */
    private static ItemStack createCourseMapFragment(LostCourse course) {
        // For now, use a paper as placeholder
        ItemStack fragment = new ItemStack(Items.PAPER);
        fragment.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Map Fragment: " + course.name())
            .formatted(Formatting.GOLD));
        return fragment;
    }

    /**
     * Clears all lost courses (for testing/admin).
     */
    public static void clearAllLostCourses() {
        LOST_COURSES.clear();
        McdgMod.LOGGER.info("Cleared all lost courses");
    }
}