package com.mcdg.game;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class RoundChunkLoader {
    private static final ChunkTicketType<Long> MCDG_ROUND =
            ChunkTicketType.create("mcdg_round", Comparator.comparingLong(Long::longValue));
    private static final ChunkTicketType<Long> MCDG_THROW_LANDING =
            ChunkTicketType.create("mcdg_throw_landing", Comparator.comparingLong(Long::longValue));
    private static final long TICKET_VALUE = 1L;
    private static final int CHUNK_RADIUS = 1;
    private static final int THROW_LANDING_RADIUS = 1;
    private static final Set<ChunkPos> loadedChunks = new HashSet<>();

    private RoundChunkLoader() {}

    public static void loadCourseChunks(ServerWorld world, PlacedCourseState placed) {
        unloadAll(world);

        for (BlockPos tee : placed.holeTees().values()) {
            ChunkPos cp = new ChunkPos(tee);
            world.getChunkManager().addTicket(MCDG_ROUND, cp, CHUNK_RADIUS, TICKET_VALUE);
            loadedChunks.add(cp);
        }
        for (BlockPos basket : placed.holeBaskets().values()) {
            ChunkPos cp = new ChunkPos(basket);
            world.getChunkManager().addTicket(MCDG_ROUND, cp, CHUNK_RADIUS, TICKET_VALUE);
            loadedChunks.add(cp);
        }
    }

    public static void unloadAll(ServerWorld world) {
        for (ChunkPos cp : loadedChunks) {
            world.getChunkManager().removeTicket(MCDG_ROUND, cp, CHUNK_RADIUS, TICKET_VALUE);
        }
        loadedChunks.clear();
    }

    /**
     * Adds a temporary chunk ticket for a calculated throw landing so the destination
     * chunks can load asynchronously while the disc is "in flight".
     */
    public static void addThrowLandingTicket(ServerWorld world, BlockPos landingFeet) {
        ChunkPos cp = new ChunkPos(landingFeet);
        world.getChunkManager().addTicket(MCDG_THROW_LANDING, cp, THROW_LANDING_RADIUS, TICKET_VALUE);
    }

    /**
     * Removes the temporary throw landing ticket after the player has been teleported.
     */
    public static void removeThrowLandingTicket(ServerWorld world, BlockPos landingFeet) {
        ChunkPos cp = new ChunkPos(landingFeet);
        world.getChunkManager().removeTicket(MCDG_THROW_LANDING, cp, THROW_LANDING_RADIUS, TICKET_VALUE);
    }

}
