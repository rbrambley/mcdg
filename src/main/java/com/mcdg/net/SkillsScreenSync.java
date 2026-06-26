package com.mcdg.net;

import com.mcdg.McdgMod;
import com.mcdg.game.SkillUnlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class SkillsScreenSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "skills_screen");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private SkillsScreenSync() {
    }

    public record SkillEntry(
        String key,
        String displayName,
        String description,
        String benefitDescription,
        String colorName,
        int requiredCount,
        int currentProgress,
        boolean unlocked
    ) {
        static SkillEntry read(RegistryByteBuf buf) {
            String key = buf.readString();
            String displayName = buf.readString();
            String description = buf.readString();
            String benefitDescription = buf.readString();
            String colorName = buf.readString();
            int requiredCount = buf.readVarInt();
            int currentProgress = buf.readVarInt();
            boolean unlocked = buf.readBoolean();
            return new SkillEntry(key, displayName, description, benefitDescription, colorName, requiredCount, currentProgress, unlocked);
        }

        void write(RegistryByteBuf buf) {
            buf.writeString(key != null ? key : "");
            buf.writeString(displayName != null ? displayName : "");
            buf.writeString(description != null ? description : "");
            buf.writeString(benefitDescription != null ? benefitDescription : "");
            buf.writeString(colorName != null ? colorName : "");
            buf.writeVarInt(requiredCount);
            buf.writeVarInt(currentProgress);
            buf.writeBoolean(unlocked);
        }
    }

    public record Payload(Map<String, SkillEntry> skills) implements CustomPayload {

        public static Payload read(RegistryByteBuf buf) {
            int skillCount = buf.readVarInt();
            Map<String, SkillEntry> skills = new HashMap<>();
            for (int i = 0; i < skillCount; i++) {
                SkillEntry entry = SkillEntry.read(buf);
                skills.put(entry.key(), entry);
            }
            return new Payload(Map.copyOf(skills));
        }

        public void write(RegistryByteBuf buf) {
            buf.writeVarInt(skills.size());
            for (SkillEntry entry : skills.values()) {
                entry.write(buf);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}