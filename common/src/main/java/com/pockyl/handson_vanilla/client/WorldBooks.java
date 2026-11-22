package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's book ({@link EnchantingBookModel}, through {@link OpenBook}) where vanilla draws its own: hovering over every enchanting
 * table and lying on every lectern with a book, in vanilla's place, pose and animation, so the book in the world is the one read at
 * the station: one book, wherever it is seen. Its loose sheets stand where vanilla's flipping pages are: the table's are covered in
 * runes ({@link RunePages}, the pages the station opens on), the lectern's are blank.
 */
public final class WorldBooks {
    /**
     * The book's size against vanilla's, over the table and on the lectern, about its spine: at vanilla's size, a pixel wider, it
     * looks too big for its block. The stations read it at this size too.
     */
    public static final float SCALE = 0.8F;

    /** One book for every lectern, one for every table: they keep no state of their own (no turn, no riffle is ever started). */
    private static final OpenBook BOOK = new OpenBook(1, 1, (canvas, page, resting) -> {
    });
    private static final OpenBook TABLE_BOOK = new OpenBook(RunePages.UNITS_WIDE, RunePages.UNITS_HIGH, (canvas, page, resting) -> {
    });
    /** How strong the table book's runes are near, and from how far (blocks) they fade out to none: far off they only flicker. */
    static final float RUNES_ALPHA = 0.7F;
    private static final double RUNES_NEAR = 10.0;
    private static final double RUNES_FAR = 16.0;
    private static float runes;

    static {
        TABLE_BOOK.setFiller((canvas, page) -> RunePages.paint(canvas, page, runes, RunePages.now()));
        TABLE_BOOK.setPaper(RunePages::paper);
    }

    private WorldBooks() {
    }

    /**
     * As {@code EnchantTableRenderer} submits its book, in block units, from the look it read of the table (its time, turn, flip and
     * opening, already for this frame).
     */
    public static void submitEnchanting(EnchantTableRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        SubmittedBuffers buffers = SubmittedBuffers.start();
        drawEnchanting(state, poseStack, buffers, state.lightCoords);
        buffers.submit(collector);
    }

    /** As {@code LecternRenderer} submits the book of a lectern that has one, in block units. */
    public static void submitLectern(LecternRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        SubmittedBuffers buffers = SubmittedBuffers.start();
        drawLectern(state.yRot, poseStack, buffers, state.lightCoords);
        buffers.submit(collector);
    }

    private static void drawEnchanting(EnchantTableRenderState state, PoseStack poseStack, SceneBuffers buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.75F, 0.5F);
        float time = state.time;
        poseStack.translate(0.0F, 0.1F + Mth.sin(time * 0.1F) * 0.01F, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(-state.yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));
        float flip = state.flip;
        float open = (Mth.sin(time * 0.02F) * 0.1F + 1.25F) * state.open;
        double far = Minecraft.getInstance().gameRenderer.mainCamera().position()
                .distanceTo(Vec3.atCenterOf(state.blockPos));
        runes = RUNES_ALPHA * (float) Mth.clamp((RUNES_FAR - far) / (RUNES_FAR - RUNES_NEAR), 0.0, 1.0);
        // Every page vanilla's flip turns is a page turned: the book shows the spread it has come to, going on or back.
        TABLE_BOOK.jumpTo(OpenBook.flipTurns(flip));
        float[] sheets = OpenBook.flipAngles(flip, open);
        draw(TABLE_BOOK, poseStack, buffers, light, open, sheets[0], sheets[1]);
        poseStack.popPose();
    }

    /** As {@code LecternRenderer} draws the book of a lectern that has one, in block units; {@code yRot}: the lectern's turn. */
    private static void drawLectern(float yRot, PoseStack poseStack, SceneBuffers buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0625F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(67.5F));
        poseStack.translate(0.0F, -0.125F, 0.0F);
        // Vanilla's lectern pose: setupAnim(0, 0.1, 0.9, 1.2).
        float open = 1.25F * 1.2F;
        draw(BOOK, poseStack, buffers, light, open, open - open * 2.0F * 0.1F, open - open * 2.0F * 0.9F);
        poseStack.popPose();
    }

    private static void draw(OpenBook book, PoseStack poseStack, SceneBuffers buffers, int light, float open, float right,
            float left) {
        // The book is laid out in pixels; a block entity renderer works in blocks.
        poseStack.scale(SCALE / 16.0F, SCALE / 16.0F, SCALE / 16.0F);
        book.draw(poseStack, buffers, light, open, right, left, 0.0F, 1.0F);
    }
}
