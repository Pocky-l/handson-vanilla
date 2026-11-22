package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.pockyl.diegesis.api.client.SceneBuffers;
import com.pockyl.diegesis.impl.client.render.SceneDraw;

import java.util.function.Function;

/**
 * What the painters drew straight into a buffer source before Minecraft 26.x (items, text, sprites of a material), written into a
 * scene's {@link SceneBuffers} the way the game's own renderers write them now.
 */
final class Draw {
    private Draw() {
    }

    /** An item as a frame shows it ({@code renderStatic} before). */
    static void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack poseStack, SceneBuffers buffers,
            @Nullable Level level, int seed) {
        SceneDraw.item(stack, context, light, overlay, poseStack, buffers, level, seed);
    }

    /** Whether the item is drawn as a block (lit from the side in a GUI): what {@code BakedModel.isGui3d()} told before. */
    static boolean isBlock(ItemStack stack, @Nullable Level level) {
        return !stack.isEmpty() && SceneDraw.itemState(stack, ItemDisplayContext.GUI, level, 0).usesBlockLight();
    }

    /** A sprite of the block atlas. */
    static TextureAtlasSprite blockSprite(Identifier id) {
        return SceneDraw.blockSprite(id);
    }

    /** A sprite of any atlas, as the game names one now ({@code Material} before). */
    static TextureAtlasSprite sprite(SpriteId id) {
        return Minecraft.getInstance().getAtlasManager().get(id);
    }

    /** The buffer for drawing with {@code id}'s sprite: the UVs are mapped into it, as {@code Material.buffer} did. */
    static VertexConsumer buffer(SceneBuffers buffers, SpriteId id, Function<Identifier, RenderType> type) {
        return sprite(id).wrap(buffers.getBuffer(id.renderType(type)));
    }

    /** A line of text, as {@code Font.drawInBatch} drew it: a colour without alpha is opaque, as it was then. */
    static void text(SceneBuffers buffers, FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4fc matrix,
            Font.DisplayMode mode, int background, int light) {
        PoseStack at = new PoseStack();
        at.last().pose().set(matrix);
        buffers.submits().submitText(at, x, y, text, shadow, mode, light, opaque(color), background, 0);
    }

    static void text(SceneBuffers buffers, String text, float x, float y, int color, boolean shadow, Matrix4fc matrix,
            Font.DisplayMode mode, int background, int light) {
        text(buffers, Component.literal(text).getVisualOrderText(), x, y, color, shadow, matrix, mode, background, light);
    }

    static void text(SceneBuffers buffers, Component text, float x, float y, int color, boolean shadow, Matrix4fc matrix,
            Font.DisplayMode mode, int background, int light) {
        text(buffers, text.getVisualOrderText(), x, y, color, shadow, matrix, mode, background, light);
    }

    /** As the font read a colour before Minecraft 26.x: one with no alpha at all is opaque. */
    private static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
