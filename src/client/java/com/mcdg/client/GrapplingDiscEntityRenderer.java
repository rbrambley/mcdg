package com.mcdg.client;

import com.mcdg.game.GrapplingDiscEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class GrapplingDiscEntityRenderer extends EntityRenderer<GrapplingDiscEntity> {
    public GrapplingDiscEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(GrapplingDiscEntity entity) {
        return null; // No texture needed for simple entity
    }

    @Override
    public void render(GrapplingDiscEntity entity, float yaw, float tickDelta, MatrixStack matrices, 
            net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light) {
        // Minimal rendering - just skip rendering to avoid crash
        // The entity particles are handled in the entity's tick() method
    }
}
