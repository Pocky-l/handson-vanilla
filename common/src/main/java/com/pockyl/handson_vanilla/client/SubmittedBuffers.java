package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buffers for drawing in the world outside a scene (the books over every enchanting table and on every lectern): since Minecraft
 * 1.21.9 a block entity's look is submitted while the frame is gathered and drawn after it, so what is written here is kept per
 * render type and handed over as pieces of custom geometry ({@link #submitTo}). The vertices stand where the pose put them already.
 */
final class SubmittedBuffers implements SceneBuffers {
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, key -> new Recorder());
    }

    /** Hands every type's vertices to {@code collector}, in the order the types were first drawn. */
    void submitTo(SubmitNodeCollector collector) {
        recorders.forEach((type, recorder) -> {
            if (!recorder.vertices.isEmpty()) {
                List<Vertex> vertices = List.copyOf(recorder.vertices);
                collector.submitCustomGeometry(new PoseStack(), type, (pose, buffer) -> vertices.forEach(vertex -> vertex.replay(buffer)));
            }
        });
        recorders.clear();
    }

    /** One vertex: each attribute as it was set, replayed in the same order. */
    private static final class Vertex {
        private final float x;
        private final float y;
        private final float z;
        private Integer color;
        private float[] uv;
        private int[] uv1;
        private int[] uv2;
        private float[] normal;
        private Float lineWidth;

        Vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void replay(VertexConsumer buffer) {
            buffer.addVertex(x, y, z);
            if (color != null) {
                buffer.setColor(color);
            }
            if (uv != null) {
                buffer.setUv(uv[0], uv[1]);
            }
            if (uv1 != null) {
                buffer.setUv1(uv1[0], uv1[1]);
            }
            if (uv2 != null) {
                buffer.setUv2(uv2[0], uv2[1]);
            }
            if (normal != null) {
                buffer.setNormal(normal[0], normal[1], normal[2]);
            }
            if (lineWidth != null) {
                buffer.setLineWidth(lineWidth);
            }
        }
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private Vertex last;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            last = new Vertex(x, y, z);
            vertices.add(last);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return setColor(alpha << 24 | red << 16 | green << 8 | blue);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            last.color = argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            last.uv = new float[] {u, v};
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            last.uv1 = new int[] {u, v};
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            last.uv2 = new int[] {u, v};
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            last.normal = new float[] {x, y, z};
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            last.lineWidth = width;
            return this;
        }
    }
}
