package com.mcdg.command;

import com.mcdg.data.Course;
import com.mcdg.data.Hole;
import com.mcdg.game.ActiveCourseManager;
import com.mcdg.game.CourseFireProtection;
import com.mcdg.game.PlacedCourseState;
import com.mcdg.game.PracticeCourseStorage;
import com.mcdg.game.RoundStateManager;
import com.mcdg.world.CoursePlacementService;
import com.mcdg.world.CourseGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class CourseCommands {
    private CourseCommands() {
    }

    public static int executeCreateCourse(
            ServerCommandSource source,
            CourseGenerator generator,
            ActiveCourseManager courseManager,
            long seed
    ) {
        int holeCount = 9;

        try {
            float facingYaw = source.getPlayer() != null ? source.getPlayer().getYaw() : 0.0f;
            Course generated = generator.generate(seed, holeCount, facingYaw);
            Course course = ensureSingleSignatureHole(generated);
            courseManager.setActiveCourse(course);
            courseManager.setActiveCourseCatalogIndex(null);

            Hole signatureHole = course.holes().stream().filter(Hole::isSignature).findFirst().orElse(null);
            String signatureSuffix = signatureHole == null
                    ? ""
                    : " Signature: H" + signatureHole.index() + " (" + signatureHole.signatureType().displayName() + ").";

            source.sendFeedback(() -> Text.literal(
                    "Created active course '" + course.name() + "' with " + course.holes().size() + " holes (seed=" + seed + "). Use /mcdg startround or /mcdg practicecourse to place it near you on the surface."
                            + signatureSuffix
            ), false);
            return 1;
        } catch (RuntimeException ex) {
            source.sendError(Text.literal("Course generation failed: " + ex.getMessage()));
            return 0;
        }
    }

    public static int executeListCourses(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage
    ) {
        MinecraftServer server = source.getServer();
        List<PracticeCourseStorage.ReusableCourseEntry> catalog = practiceCourseStorage.listReusable(server);
        if (catalog.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No saved courses in the reusable catalog."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Reusable course catalog (" + catalog.size() + " entries):"), false);
        for (int i = 0; i < catalog.size(); i++) {
            PracticeCourseStorage.ReusableCourseEntry entry = catalog.get(i);
            String line = String.format("[%d] %s (seed=%d, holes=%d, %s)",
                    i + 1,
                    entry.name(),
                    entry.seed(),
                    entry.holeCount(),
                    entry.sourceTag()
            );
            source.sendFeedback(() -> Text.literal(line), false);
        }
        source.sendFeedback(() -> Text.literal("Use /mcdg playcourse <index> to play a saved course."), false);
        return 1;
    }

    public static int executeUseCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage,
            int index
    ) {
        MinecraftServer server = source.getServer();
        List<PracticeCourseStorage.ReusableCourseEntry> catalog = practiceCourseStorage.listReusable(server);
        if (index < 1 || index > catalog.size()) {
            source.sendError(Text.literal("Invalid course index. Use /mcdg listcourses to see available courses."));
            return 0;
        }

        Optional<PracticeCourseStorage.LoadedPracticeCourse> loadedOpt = practiceCourseStorage.loadReusableByIndex(server, index);
        if (loadedOpt.isEmpty()) {
            source.sendError(Text.literal("Failed to load course from catalog index: " + index));
            return 0;
        }

        PracticeCourseStorage.LoadedPracticeCourse loaded = loadedOpt.get();
        Course course = loaded.course();
        courseManager.setActiveCourse(course);
        courseManager.setActiveCourseCatalogIndex(index - 1);
        courseManager.setRoundActive(false);
        roundStateManager.clearAll();

        source.sendFeedback(() -> Text.literal(
                "Loaded course '" + course.name() + "' (seed=" + course.seed() + "). Use /mcdg startround to begin a round."
        ), false);
        return 1;
    }

    public static int executeRemoveCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            PracticeCourseStorage practiceCourseStorage,
            int index
    ) {
        MinecraftServer server = source.getServer();
        List<PracticeCourseStorage.ReusableCourseEntry> catalog = practiceCourseStorage.listReusable(server);
        if (index < 1 || index > catalog.size()) {
            source.sendError(Text.literal("Invalid course index. Use /mcdg listcourses to see available courses."));
            return 0;
        }

        PracticeCourseStorage.ReusableCourseEntry entry = catalog.get(index - 1);
        java.util.Set<Integer> indicesToRemove = new java.util.HashSet<>();
        indicesToRemove.add(index);
        int removed = practiceCourseStorage.pruneReusableByIndices(server, indicesToRemove);
        if (removed <= 0) {
            source.sendError(Text.literal("Failed to remove course entry."));
            return 0;
        }

        // If the removed course was the active course, clear it
        Integer activeIndex = courseManager.getActiveCourseCatalogIndex().orElse(null);
        if (activeIndex != null && activeIndex == index - 1) {
            courseManager.setActiveCourse(null);
            courseManager.setActiveCourseCatalogIndex(null);
        }

        source.sendFeedback(() -> Text.literal("Removed course: " + entry.name()), false);
        return 1;
    }

    public static int executePruneCourses(
            ServerCommandSource source,
            PracticeCourseStorage practiceCourseStorage,
            int keep
    ) {
        MinecraftServer server = source.getServer();
        List<PracticeCourseStorage.ReusableCourseEntry> catalog = practiceCourseStorage.listReusable(server);
        if (catalog.size() <= keep) {
            source.sendFeedback(() -> Text.literal("Catalog already has " + catalog.size() + " entries (keep=" + keep + "). No pruning needed."), false);
            return 1;
        }

        int removed = practiceCourseStorage.pruneReusable(server, keep);
        source.sendFeedback(() -> Text.literal("Pruned " + removed + " old course entries. Kept " + keep + " most recent."), false);
        return 1;
    }

    public static int executeCleanupCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager,
            CoursePlacementService placementService,
            RoundStateManager roundStateManager,
            PracticeCourseStorage practiceCourseStorage
    ) {
        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            source.sendError(Text.literal("No active placed course to clean up."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Placed course world is not loaded."));
            return 0;
        }

        placementService.resetPlacedCourse(world, placed);
        CourseFireProtection.remove(world);
        courseManager.clearPlacedCourseState();
        courseManager.setRoundActive(false);
        roundStateManager.clearAll();
        practiceCourseStorage.clear(source.getServer());

        source.sendFeedback(() -> Text.literal("Cleaned up active course and cleared round state."), false);
        return 1;
    }

    public static int executeGotoCourse(
            ServerCommandSource source,
            ActiveCourseManager courseManager
    ) {
        if (source.getEntity() == null || !(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        PlacedCourseState placed = courseManager.getPlacedCourseState().orElse(null);
        if (placed == null) {
            source.sendError(Text.literal("No active placed course. Create and place a course first."));
            return 0;
        }

        BlockPos firstTee = placed.holeTees().get(1);
        if (firstTee == null) {
            source.sendError(Text.literal("Course has no hole 1 tee."));
            return 0;
        }

        ServerWorld world = source.getServer().getWorld(placed.worldKey());
        if (world == null) {
            source.sendError(Text.literal("Placed course world is not loaded."));
            return 0;
        }

        player.teleport(world, firstTee.getX() + 0.5, firstTee.getY() + 1.0, firstTee.getZ() + 0.5, player.getYaw(), player.getPitch());
        source.sendFeedback(() -> Text.literal("Teleported to hole 1 tee."), false);
        return 1;
    }

    private static Course ensureSingleSignatureHole(Course course) {
        List<Hole> holes = course.holes();
        Hole signatureHole = holes.stream().filter(Hole::isSignature).findFirst().orElse(null);

        if (signatureHole == null) {
            // No signature hole, add one at a random position
            int signatureIndex = ThreadLocalRandom.current().nextInt(1, holes.size() + 1);
            Hole targetHole = holes.get(signatureIndex - 1);
            Hole newSignature = new Hole(
                    targetHole.index(),
                    targetHole.par(),
                    targetHole.distanceFeet(),
                    targetHole.tee(),
                    targetHole.basket(),
                    targetHole.fairwaySegments(),
                    ThreadLocalRandom.current().nextBoolean()
                            ? com.mcdg.data.SignatureHoleType.ISLAND_GREEN
                            : com.mcdg.data.SignatureHoleType.DOWNHILL_BOMBER
            );
            List<Hole> newHoles = new java.util.ArrayList<>(holes);
            newHoles.set(signatureIndex - 1, newSignature);
            return new Course(course.seed(), course.name(), newHoles);
        }

        // Keep only the first signature hole, convert others to NONE
        boolean foundSignature = false;
        List<Hole> newHoles = new java.util.ArrayList<>();
        for (Hole hole : holes) {
            if (hole.isSignature() && !foundSignature) {
                newHoles.add(hole);
                foundSignature = true;
            } else if (hole.isSignature()) {
                newHoles.add(new Hole(
                        hole.index(),
                        hole.par(),
                        hole.distanceFeet(),
                        hole.tee(),
                        hole.basket(),
                        hole.fairwaySegments(),
                        com.mcdg.data.SignatureHoleType.NONE
                ));
            } else {
                newHoles.add(hole);
            }
        }

        return new Course(course.seed(), course.name(), newHoles);
    }
}
