package com.mcdg.net;

import com.mcdg.McdgMod;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class SkillsStatusSync {
    public static final Identifier CHANNEL = Identifier.of(McdgMod.MOD_ID, "skills_status");
    public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(CHANNEL);
    public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.of(Payload::write, Payload::read);

    private SkillsStatusSync() {
    }

    public record Payload(Set<String> unlockedSkills) implements CustomPayload {

        public static Payload read(RegistryByteBuf buf) {
            int skillCount = buf.readVarInt();
            Set<String> skills = new HashSet<>();
            for (int i = 0; i < skillCount; i++) {
                skills.add(buf.readString());
            }
            return new Payload(Set.copyOf(skills));
        }

        public void write(RegistryByteBuf buf) {
            buf.writeVarInt(unlockedSkills.size());
            for (String skill : unlockedSkills) {
                buf.writeString(skill);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}