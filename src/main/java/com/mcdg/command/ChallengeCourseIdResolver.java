package com.mcdg.command;

import com.mcdg.game.ChallengeCourseManager;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a challenge course identifier from either a UUID string or a course name.
 * Also provides tab-completion suggestions for command arguments.
 */
public final class ChallengeCourseIdResolver {

    private ChallengeCourseIdResolver() {
    }

    /**
     * Resolves a course ID from either a UUID string or a course name.
     * Returns the UUID if found, empty otherwise.
     */
    public static Optional<UUID> resolve(ServerCommandSource source, String courseIdOrName) {
        // Try parsing as UUID first
        try {
            UUID courseId = UUID.fromString(courseIdOrName);
            var catalog = ChallengeCourseManager.getCatalog();
            if (catalog.isPresent() && catalog.get().getCourse(courseId).isPresent()) {
                return Optional.of(courseId);
            }
            source.sendError(Text.literal("Challenge course not found with ID: " + courseIdOrName));
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            // Not a UUID, try to find by name
        }

        // Search by name (case-insensitive, partial match)
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            source.sendError(Text.literal("Challenge course catalog not available"));
            return Optional.empty();
        }

        String searchLower = courseIdOrName.toLowerCase();
        UUID foundId = null;
        int matchCount = 0;

        for (var entry : catalog.get().getAllCourses()) {
            if (entry.name().toLowerCase().contains(searchLower)) {
                foundId = entry.courseId();
                matchCount++;
            }
        }

        if (matchCount == 0) {
            source.sendError(Text.literal("Challenge course not found: " + courseIdOrName));
            return Optional.empty();
        } else if (matchCount > 1) {
            source.sendError(Text.literal("Multiple courses match '" + courseIdOrName + "'. Please use the full course name or UUID."));
            return Optional.empty();
        }

        return Optional.of(foundId);
    }

    /**
     * Provides tab-completion suggestions for challenge course names and IDs.
     */
    public static final SuggestionProvider<ServerCommandSource> SUGGESTIONS = (context, builder) -> {
        var catalog = ChallengeCourseManager.getCatalog();
        if (catalog.isEmpty()) {
            return builder.buildFuture();
        }

        for (var entry : catalog.get().getAllCourses()) {
            builder.suggest(entry.name());
            builder.suggest(entry.courseId().toString());
        }

        return builder.buildFuture();
    };
}
