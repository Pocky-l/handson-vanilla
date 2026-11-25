package com.pockyl.handson_vanilla.client;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodeView;

/**
 * What a strike makes, hovering over the work, bobbing and swaying to the camera; it grows in when it first appears, so the
 * thing to click announces itself, and under the cursor it lights up, swells a little and shows its tooltip. And the piece just
 * struck, rising off the work for a moment where it was made.
 */
final class HoveringResult {
    private static final float BOB = 0.5F;
    private static final float BOB_RATE = 2.2F;
    private static final float SWAY = 18.0F;
    private static final float SWAY_RATE = 1.3F;
    /** Seconds the result takes to grow in, and how much larger it is under the cursor. */
    private static final float GROW = 0.12F;
    private static final float HOVER_SWELL = 0.1F;
    /** The struck piece: seconds it shows, how high it rises (pixels), how much larger it starts. */
    static final float FORGED = 0.3F;
    private static final float FORGED_RISE = 3.0F;
    private static final float FORGED_SWELL = 0.15F;

    private final float size;
    private ItemStack seen = ItemStack.EMPTY;
    private float appearedAt;
    private float hover;

    HoveringResult(float size) {
        this.size = size;
    }

    /**
     * Every frame: the node's tooltip is the result's, so the player reads what a strike gives before striking; the swell follows
     * the cursor. {@code shown}: whether the result is drawn now (not while it is being struck).
     */
    void frame(NodeView node, ItemStack made, boolean shown, float dt) {
        node.setTooltipItem(shown ? made : ItemStack.EMPTY);
        float wanted = shown && !made.isEmpty() && node.isHovered() ? 1.0F : 0.0F;
        hover = Mth.lerp(Math.min(1.0F, dt * 14.0F), hover, wanted);
    }

    /** Draws the hovering result at the node, {@code clock} being the station's seconds. */
    void paint(Level level, ItemStack made, float clock, PoseStack poseStack, SceneBuffers buffers, int light) {
        if (!ItemStack.matches(made, seen)) {
            if (seen.isEmpty() || made.isEmpty() || !ItemStack.isSameItem(made, seen)) {
                appearedAt = clock;
            }
            seen = made.copy();
        }
        if (made.isEmpty()) {
            return;
        }
        float scale = size * outCubic((clock - appearedAt) / GROW) * (1.0F + HOVER_SWELL * hover);
        boolean block = ItemLook.isGui3d(made, ItemDisplayContext.FIXED, level);
        poseStack.pushPose();
        poseStack.translate(0.0F, scale * (block ? 0.25F : 0.5F) + BOB * (1.0F + (float) Math.sin(clock * BOB_RATE)), 0.0F);
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F + SWAY * (float) Math.sin(clock * SWAY_RATE)));
        poseStack.scale(scale, scale, scale);
        SceneDraw.item(made, ItemDisplayContext.FIXED, hover > 0.5F ? LightCoordsUtil.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY, poseStack, DiegesisClient.items(buffers), level, 0);
        poseStack.popPose();
    }

    /** The piece just struck, {@code age} seconds after the blow: it rises off the work and settles from a little larger. */
    static void paintForged(Level level, ItemStack forged, float age, float itemSize, PoseStack poseStack, SceneBuffers buffers,
            int light) {
        if (forged.isEmpty() || age < 0.0F || age >= FORGED) {
            return;
        }
        float t = outCubic(age / FORGED);
        float scale = itemSize * (1.0F + FORGED_SWELL * (1.0F - t));
        boolean block = ItemLook.isGui3d(forged, ItemDisplayContext.FIXED, level);
        poseStack.pushPose();
        poseStack.translate(0.0F, FORGED_RISE * t + scale * (block ? 0.25F : 0.5F), 0.0F);
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, scale, scale);
        SceneDraw.item(forged, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                DiegesisClient.items(buffers), level, 0);
        poseStack.popPose();
    }

    /** A piece lying flat where it was put, face up and its top away from the camera, as the scene lays a flat item in a slot. */
    static void paintLying(Level level, ItemStack stack, float itemSize, PoseStack poseStack, SceneBuffers buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.07F, 0.0F);
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        poseStack.rotate(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(itemSize, itemSize, itemSize);
        SceneDraw.item(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                DiegesisClient.items(buffers), level, 0);
        poseStack.popPose();
    }

    static float outCubic(float t) {
        float c = 1.0F - Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - c * c * c;
    }
}
