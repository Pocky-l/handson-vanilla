package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Drawing written for buffers ({@link SceneBuffers}) given to a block entity renderer's submit collector, where Minecraft 26.x
 * takes custom geometry one render type at a time and draws it later in the frame. The drawing is run once to learn the render
 * types it uses, then once more for each of them, when the game asks, writing only that type's vertices.
 */
final class SubmittedBuffers {
    private SubmittedBuffers() {
    }

    /** Submits what {@code draw} draws from {@code poseStack}'s pose; {@code draw} must draw the same each time it is run. */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, BiConsumer<PoseStack, SceneBuffers> draw) {
        Set<RenderType> types = new LinkedHashSet<>();
        PoseStack probe = new PoseStack();
        probe.last().set(poseStack.last());
        draw.accept(probe, type -> {
            types.add(type);
            return Nowhere.INSTANCE;
        });
        for (RenderType type : types) {
            collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
                PoseStack replay = new PoseStack();
                replay.last().set(pose);
                draw.accept(replay, wanted -> wanted == type ? consumer : Nowhere.INSTANCE);
            });
        }
    }

    /** Takes vertices and keeps none. */
    private enum Nowhere implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
