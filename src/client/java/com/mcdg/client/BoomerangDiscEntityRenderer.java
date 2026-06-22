package com.mcdg.client;

import com.mcdg.game.BoomerangDiscEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class BoomerangDiscEntityRenderer extends EntityRenderer<BoomerangDiscEntity> {
    public BoomerangDiscEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTexture(BoomerangDiscEntity entity) {
        return null; // No texture needed for simple entity
    }

    @Override
    public void render(BoomerangDiscEntity entity, float yaw, float tickDelta, MatrixStack matrices, 
            net.minecraft.client.render.VertexConsumerProvider vertexConsumers, int light) {
        // Minimal rendering - just skip rendering to avoid crash
        // The entity particles are handled in the entity's tick() method
    }
}
