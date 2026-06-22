package com.mcdg.game;

import com.mcdg.McdgMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class McdgEntityTypes {
    public static EntityType<GrapplingDiscEntity> GRAPPLING_DISC;
    public static EntityType<BoomerangDiscEntity> BOOMERANG_DISC;

    private McdgEntityTypes() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        GRAPPLING_DISC = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(McdgMod.MOD_ID, "grappling_disc"),
                EntityType.Builder.<GrapplingDiscEntity>create(GrapplingDiscEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(64)
                        .trackingTickInterval(2)
                        .build()
        );

        BOOMERANG_DISC = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(McdgMod.MOD_ID, "boomerang_disc"),
                EntityType.Builder.<BoomerangDiscEntity>create(BoomerangDiscEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(64)
                        .trackingTickInterval(2)
                        .build()
        );
    }
}
