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
    private static final long TICKET_VALUE = 1L;
    private static final int CHUNK_RADIUS = 1;
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
}
