package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

import com.pockyl.diegesis.api.client.SceneBuffers;

/**
 * Text drawn into a scene's buffers, as {@code Font.drawInBatch} drew it into a buffer source before Minecraft 26.x: since then the
 * game only submits text, so it goes through the buffers' submit collector, which writes it into them at once.
 */
final class SceneText {
    private SceneText() {
    }

    /** {@code Font.drawInBatch(text, x, y, color, shadow, matrix, buffers, mode, background, light)}. */
    static void drawInBatch(FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4fc matrix,
            SceneBuffers buffers, Font.DisplayMode mode, int background, int light) {
        PoseStack at = new PoseStack();
        at.last().pose().set(matrix);
        buffers.submits().submitText(at, x, y, text, shadow, mode, light, color, background, 0);
    }

    static void drawInBatch(Component text, float x, float y, int color, boolean shadow, Matrix4fc matrix, SceneBuffers buffers,
            Font.DisplayMode mode, int background, int light) {
        drawInBatch(text.getVisualOrderText(), x, y, color, shadow, matrix, buffers, mode, background, light);
    }

    static void drawInBatch(String text, float x, float y, int color, boolean shadow, Matrix4fc matrix, SceneBuffers buffers,
            Font.DisplayMode mode, int background, int light) {
        drawInBatch(Component.literal(text).getVisualOrderText(), x, y, color, shadow, matrix, buffers, mode, background, light);
    }
}
