package com.theminespirit.neoforge.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = ModIdentity.MOD_ID, value = Dist.CLIENT)
public final class ConstructionHighlightRenderer {
    // mc: RenderType.lines() несёт LEQUAL-тест глубины и скрыт за блоками; состояние собрано заново с NO_DEPTH_TEST
    private static final RenderType HIGHLIGHT_LINES = RenderType.create(
            "theminespirit_construction_highlight",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean drawLogged;

    private ConstructionHighlightRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!RenderLevelStageEvent.Stage.AFTER_ENTITIES.equals(event.getStage())) {
            return;
        }
        ConstructionHighlightManager manager = ConstructionHighlightManager.instance();
        Minecraft minecraft = Minecraft.getInstance();
        if (!manager.isEnabled() || minecraft.level == null) {
            drawLogged = false;
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(HIGHLIGHT_LINES);
        if (!drawLogged) {
            drawLogged = true;
            LOGGER.info(
                    "Подсветка строительства: рисую снести {}, поставить {}",
                    manager.wrongBlocks().size(),
                    manager.missingBlocks().size());
        }
        drawBoxes(poseStack, lines, minecraft.level, manager.wrongBlocks(), 1.0F, 0.0F, 0.0F);
        drawBoxes(poseStack, lines, minecraft.level, manager.missingBlocks(), 1.0F, 0.6F, 0.0F);
        buffers.endBatch(HIGHLIGHT_LINES);
        poseStack.popPose();
    }

    private static void drawBoxes(
            PoseStack poseStack,
            VertexConsumer lines,
            Level level,
            Set<BlockPos> blocks,
            float red,
            float green,
            float blue) {
        for (BlockPos pos : blocks) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            LevelRenderer.renderLineBox(
                    poseStack,
                    lines,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0,
                    pos.getY() + 1.0,
                    pos.getZ() + 1.0,
                    red,
                    green,
                    blue,
                    1.0F);
        }
    }
}
