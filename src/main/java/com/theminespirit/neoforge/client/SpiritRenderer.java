package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class SpiritRenderer extends HumanoidMobRenderer<SpiritEntity, PlayerModel<SpiritEntity>> {
    private static final float PLAYER_MODEL_HEIGHT = 1.8F;
    private static final float SHADOW_RADIUS = 0.4F;

    public SpiritRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), SHADOW_RADIUS);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritEntity spirit) {
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected void scale(SpiritEntity spirit, PoseStack poseStack, float partialTick) {
        float toHitbox = spirit.getBbHeight() / PLAYER_MODEL_HEIGHT;
        poseStack.scale(toHitbox, toHitbox, toHitbox);
    }
}
