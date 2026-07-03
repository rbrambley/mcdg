package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class MenuScreenSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "menu_screen");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private MenuScreenSync() {
    }

    public record CourseEntry(int index, String name, int holeCount, String sourceTag) {
        static CourseEntry read(RegistryByteBuf buf) {
            return new CourseEntry(buf.readVarInt(), buf.readString(), buf.readVarInt(), buf.readString());
        }

        void write(RegistryByteBuf buf) {
            buf.writeVarInt(index);
            buf.writeString(name != null ? name : "");
            buf.writeVarInt(holeCount);
            buf.writeString(sourceTag != null ? sourceTag : "");
        }
    }

    public record ChallengeCourseEntry(String courseId, String name, String type, boolean placed, int bestScore, int completions) {
        static ChallengeCourseEntry read(RegistryByteBuf buf) {
            return new ChallengeCourseEntry(
                    buf.readString(),
                    buf.readString(),
                    buf.readString(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(courseId != null ? courseId : "");
            buf.writeString(name != null ? name : "");
            buf.writeString(type != null ? type : "");
            buf.writeBoolean(placed);
            buf.writeVarInt(bestScore);
            buf.writeVarInt(completions);
        }
    }

    public record Payload(
            boolean roundActive,
            boolean courseLoaded,
            String courseName,
            int activeCatalogIndex,
            int activeHoleCount,
            boolean hasSavedSession,
            String savedCourseName,
            int savedHole,
            int savedStrokes,
            boolean isAdmin,
            String rulesetName,
            String presetName,
            List<CourseEntry> courses,
            boolean caveMode,
            List<ChallengeCourseEntry> challengeCourses
    ) implements CustomPayload {

        public static Payload read(RegistryByteBuf buf) {
            boolean roundActive = buf.readBoolean();
            boolean courseLoaded = buf.readBoolean();
            String courseName = buf.readString();
            int activeCatalogIndex = buf.readVarInt();
            int activeHoleCount = buf.readVarInt();
            boolean hasSavedSession = buf.readBoolean();
            String savedCourseName = buf.readString();
            int savedHole = buf.readVarInt();
            int savedStrokes = buf.readVarInt();
            boolean isAdmin = buf.readBoolean();
            String rulesetName = buf.readString();
            String presetName = buf.readString();
            int courseCount = buf.readVarInt();
            List<CourseEntry> courses = new ArrayList<>();
            for (int i = 0; i < courseCount; i++) {
                courses.add(CourseEntry.read(buf));
            }
            boolean caveMode = buf.readBoolean();
            int challengeCount = buf.readVarInt();
            List<ChallengeCourseEntry> challengeCourses = new ArrayList<>();
            for (int i = 0; i < challengeCount; i++) {
                challengeCourses.add(ChallengeCourseEntry.read(buf));
            }
            return new Payload(roundActive, courseLoaded, courseName, activeCatalogIndex,
                    activeHoleCount, hasSavedSession, savedCourseName, savedHole, savedStrokes,
                    isAdmin, rulesetName, presetName, List.copyOf(courses), caveMode, List.copyOf(challengeCourses));
        }

        public void write(RegistryByteBuf buf) {
            buf.writeBoolean(roundActive);
            buf.writeBoolean(courseLoaded);
            buf.writeString(courseName != null ? courseName : "");
            buf.writeVarInt(activeCatalogIndex);
            buf.writeVarInt(activeHoleCount);
            buf.writeBoolean(hasSavedSession);
            buf.writeString(savedCourseName != null ? savedCourseName : "");
            buf.writeVarInt(savedHole);
            buf.writeVarInt(savedStrokes);
            buf.writeBoolean(isAdmin);
            buf.writeString(rulesetName != null ? rulesetName : "");
            buf.writeString(presetName != null ? presetName : "");
            buf.writeVarInt(courses.size());
            for (CourseEntry entry : courses) {
                entry.write(buf);
            }
            buf.writeBoolean(caveMode);
            buf.writeVarInt(challengeCourses.size());
            for (ChallengeCourseEntry entry : challengeCourses) {
                entry.write(buf);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
