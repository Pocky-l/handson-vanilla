package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.pockyl.diegesis.api.client.SceneBuffers;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Buffers for drawing what a block entity renderer drew straight into a buffer source before Minecraft 26.x (the books over the
 * enchanting tables and on the lecterns): the vertices are kept per render type, already in the renderer's space, and handed to the
 * game as custom geometry when the drawing is done. Only on the render thread, one drawing at a time.
 */
final class SubmittedBuffers implements SceneBuffers {
    private static final SubmittedBuffers INSTANCE = new SubmittedBuffers();

    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    private SubmittedBuffers() {
    }

    /** The buffers, emptied, for the next drawing. */
    static SubmittedBuffers start() {
        INSTANCE.recorders.values().forEach(recorder -> recorder.count = 0);
        return INSTANCE;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, key -> new Recorder());
    }

    /** Hands everything drawn to {@code collector}, each render type as one piece of custom geometry, in the order first drawn. */
    void submit(SubmitNodeCollector collector) {
        PoseStack none = new PoseStack();
        recorders.forEach((type, recorder) -> {
            if (recorder.count > 0) {
                int[] data = Arrays.copyOf(recorder.data, recorder.count * Recorder.STRIDE);
                int count = recorder.count;
                collector.submitCustomGeometry(none, type, (pose, buffer) -> Recorder.replay(data, count, buffer));
                recorder.count = 0;
            }
        });
    }

    /** The vertices of one render type: each attribute as it was set, replayed into the game's buffer. */
    private static final class Recorder implements VertexConsumer {
        private static final int COLOR = 1;
        private static final int UV = 1 << 1;
        private static final int UV1 = 1 << 2;
        private static final int UV2 = 1 << 3;
        private static final int NORMAL = 1 << 4;
        private static final int LINE_WIDTH = 1 << 5;
        /** Ints per vertex: flags, position (3), colour, uv (2), uv1, uv2, normal (3), line width. */
        static final int STRIDE = 13;
        private int[] data = new int[STRIDE * 256];
        private int count;
        private int at = -1;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if ((count + 1) * STRIDE > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            at = count * STRIDE;
            count++;
            Arrays.fill(data, at, at + STRIDE, 0);
            data[at + 1] = Float.floatToRawIntBits(x);
            data[at + 2] = Float.floatToRawIntBits(y);
            data[at + 3] = Float.floatToRawIntBits(z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(a << 24 | r << 16 | g << 8 | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            data[at] |= COLOR;
            data[at + 4] = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[at] |= UV;
            data[at + 5] = Float.floatToRawIntBits(u);
            data[at + 6] = Float.floatToRawIntBits(v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            data[at] |= UV1;
            data[at + 7] = u & 0xFFFF | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[at] |= UV2;
            data[at + 8] = u & 0xFFFF | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[at] |= NORMAL;
            data[at + 9] = Float.floatToRawIntBits(x);
            data[at + 10] = Float.floatToRawIntBits(y);
            data[at + 11] = Float.floatToRawIntBits(z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            data[at] |= LINE_WIDTH;
            data[at + 12] = Float.floatToRawIntBits(width);
            return this;
        }

        static void replay(int[] data, int count, VertexConsumer buffer) {
            for (int vertex = 0; vertex < count; vertex++) {
                int at = vertex * STRIDE;
                int flags = data[at];
                buffer.addVertex(Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]), Float.intBitsToFloat(data[at + 3]));
                if ((flags & COLOR) != 0) {
                    buffer.setColor(data[at + 4]);
                }
                if ((flags & UV) != 0) {
                    buffer.setUv(Float.intBitsToFloat(data[at + 5]), Float.intBitsToFloat(data[at + 6]));
                }
                if ((flags & UV1) != 0) {
                    buffer.setUv1((short) data[at + 7], data[at + 7] >> 16);
                }
                if ((flags & UV2) != 0) {
                    buffer.setUv2(data[at + 8] & 0xFFFF, data[at + 8] >>> 16);
                }
                if ((flags & NORMAL) != 0) {
                    buffer.setNormal(Float.intBitsToFloat(data[at + 9]), Float.intBitsToFloat(data[at + 10]),
                            Float.intBitsToFloat(data[at + 11]));
                }
                if ((flags & LINE_WIDTH) != 0) {
                    buffer.setLineWidth(Float.intBitsToFloat(data[at + 12]));
                }
            }
        }
    }
}
