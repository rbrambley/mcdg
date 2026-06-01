package com.mcdg.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class RoundPresentationService {
    private static final int COUNTDOWN_SECONDS = 30;
    private static final int TICKS_PER_SECOND = 20;
    private static final int FINAL_STING_SECONDS = 3;
    private static final float WARMUP_MUSIC_VOLUME = 0.55f;
    private static final float FINAL_STING_VOLUME = 0.90f;
    private static final List<SoundEvent> WARMUP_TRACKS = List.of(
            SoundEvents.MUSIC_DISC_CHIRP,
            SoundEvents.MUSIC_DISC_BLOCKS,
            SoundEvents.MUSIC_DISC_STAL,
            SoundEvents.MUSIC_DISC_STRAD,
            SoundEvents.MUSIC_DISC_MALL,
            SoundEvents.MUSIC_DISC_WAIT
    );

    private final List<PendingCountdown> pendingCountdowns = new ArrayList<>();
    private final Random random = new Random();
    private final List<Integer> warmupShuffleBag = new ArrayList<>();
    private int lastWarmupTrackIndex = -1;

    public void startCountdown(
            MinecraftServer server,
            List<UUID> participantIds,
            String courseName,
            int holeCount,
            int totalPar,
            Runnable onRoundLive
    ) {
        if (participantIds.isEmpty()) {
            onRoundLive.run();
            return;
        }

        Set<UUID> idSet = new HashSet<>(participantIds);
        PendingCountdown pending = new PendingCountdown(
                idSet,
                courseName,
                COUNTDOWN_SECONDS * TICKS_PER_SECOND,
            onRoundLive,
            selectWarmupTrack()
        );
        pendingCountdowns.add(pending);

        playWarmupTrack(server, pending);

        String layoutLine = courseName + "  |  " + holeCount + " holes  |  Par " + totalPar;
        forEachParticipant(server, idSet, player -> {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 40, 10));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round starts in " + COUNTDOWN_SECONDS + "...").formatted(Formatting.WHITE)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(layoutLine).formatted(Formatting.GRAY)));
        });
    }

    public void tick(MinecraftServer server) {
        if (pendingCountdowns.isEmpty()) {
            return;
        }

        Iterator<PendingCountdown> iterator = pendingCountdowns.iterator();
        while (iterator.hasNext()) {
            PendingCountdown pending = iterator.next();
            if (pending.ticksRemaining > 0 && (pending.ticksRemaining % TICKS_PER_SECOND) == 0) {
                int seconds = pending.ticksRemaining / TICKS_PER_SECOND;
                if (seconds == FINAL_STING_SECONDS && !pending.finalStingPlayed) {
                    playNearParticipants(
                            server,
                            pending.participantIds,
                                SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                            SoundCategory.PLAYERS,
                            FINAL_STING_VOLUME,
                            1.05f
                    );
                    pending.finalStingPlayed = true;
                }
                forEachParticipant(server, pending.participantIds, player -> {
                    player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 25, 5));
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round starts in " + seconds + "...").formatted(Formatting.WHITE)));
                    player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(pending.courseName).formatted(Formatting.GRAY)));
                });
            }

            pending.ticksRemaining--;
            if (pending.ticksRemaining >= 0) {
                continue;
            }

            forEachParticipant(server, pending.participantIds, player -> {
                player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Round Live!").formatted(Formatting.GREEN, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(pending.courseName + "  |  Good luck.").formatted(Formatting.WHITE)));
            });
            pending.onRoundLive.run();
            iterator.remove();
        }
    }

    private static void forEachParticipant(MinecraftServer server, Set<UUID> participantIds, java.util.function.Consumer<ServerPlayerEntity> action) {
        for (UUID id : participantIds) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    private SoundEvent selectWarmupTrack() {
        if (WARMUP_TRACKS.isEmpty()) {
            return SoundEvents.MUSIC_DISC_STRAD;
        }

        if (warmupShuffleBag.isEmpty()) {
            refillWarmupShuffleBag();
        }

        int index = warmupShuffleBag.remove(warmupShuffleBag.size() - 1);
        lastWarmupTrackIndex = index;
        return WARMUP_TRACKS.get(index);
    }

    private void refillWarmupShuffleBag() {
        warmupShuffleBag.clear();
        for (int i = 0; i < WARMUP_TRACKS.size(); i++) {
            if (WARMUP_TRACKS.size() > 1 && i == lastWarmupTrackIndex) {
                continue;
            }
            warmupShuffleBag.add(i);
        }

        if (warmupShuffleBag.isEmpty()) {
            warmupShuffleBag.add(0);
        }
        Collections.shuffle(warmupShuffleBag, random);
    }

    private static void playWarmupTrack(MinecraftServer server, PendingCountdown pending) {
        playNearParticipants(
                server,
                pending.participantIds,
                pending.warmupTrack,
                SoundCategory.MUSIC,
                WARMUP_MUSIC_VOLUME,
                1.0f
        );
    }

    private static void playNearParticipants(
            MinecraftServer server,
            Set<UUID> participantIds,
            SoundEvent sound,
            SoundCategory category,
            float volume,
            float pitch
    ) {
        for (UUID id : participantIds) {
            ServerPlayerEntity anchor = server.getPlayerManager().getPlayer(id);
            if (anchor == null) {
                continue;
            }

            anchor.getWorld().playSound(
                    null,
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    sound,
                    category,
                    volume,
                    pitch
            );
            return;
        }
    }

    private static final class PendingCountdown {
        private final Set<UUID> participantIds;
        private final String courseName;
        private final SoundEvent warmupTrack;
        private boolean finalStingPlayed;
        private int ticksRemaining;
        private final Runnable onRoundLive;

        private PendingCountdown(
                Set<UUID> participantIds,
                String courseName,
                int ticksRemaining,
                Runnable onRoundLive,
                SoundEvent warmupTrack
        ) {
            this.participantIds = participantIds;
            this.courseName = courseName;
            this.ticksRemaining = ticksRemaining;
            this.onRoundLive = onRoundLive;
            this.warmupTrack = warmupTrack;
            this.finalStingPlayed = false;
        }
    }
}
