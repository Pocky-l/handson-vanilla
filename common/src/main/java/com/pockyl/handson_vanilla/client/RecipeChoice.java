package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * With Polymorph, one set of inputs can make several things (two mods' recipes for the same inputs); its vanilla screens have a
 * button that lists them. At a station the others lie small beside the result, each as what it makes: a click on one chooses it.
 * The choice goes to the server through Polymorph's own packets ({@link PolymorphLink}), as its buttons send it: the player's at
 * the crafting and smithing tables, where the server remakes the result; the furnace's own at the furnaces, which keep it.
 * Without Polymorph the server always makes its first match, so there is nothing to choose and nothing is shown.
 */
final class RecipeChoice {
    /** As many others as a scene has places for ({@code choice_N} nodes), beside the result. */
    static final int PLACES = 4;
    private static final float HOVER_GROWTH = 1.18F;
    /** Where the places of the station showing them lie, scene pixels, for what is laid out beside the station. */
    private static List<float[]> shownPoints = List.of();

    /** A recipe the station's inputs match, and what it makes of them. */
    record Option(ResourceLocation recipe, ItemStack output) {
    }

    private final boolean keptByTheBlock;
    private final float size;
    private final float tilt;
    /** Each place's recipe and what it makes; empty places hold nothing. */
    private final ResourceLocation[] recipes = new ResourceLocation[PLACES];
    private final ItemStack[] outputs = new ItemStack[PLACES];

    /**
     * @param keptByTheBlock whether the block keeps the choice (a furnace) rather than the player
     * @param size           how big the others stand, pixels
     * @param tilt           how far they lean back to the camera, degrees
     */
    RecipeChoice(boolean keptByTheBlock, float size, float tilt) {
        this.keptByTheBlock = keptByTheBlock;
        this.size = size;
        this.tilt = tilt;
        Arrays.fill(outputs, ItemStack.EMPTY);
    }

    void onSceneReady(StationView view) {
        for (int i = 0; i < PLACES; i++) {
            int place = i;
            view.node(node(i)).ifPresent(node -> {
                node.setVisible(false);
                node.setPainter((poseStack, buffers, light) -> paint(view, node, place, poseStack, buffers, light));
            });
        }
    }

    /** Whether Polymorph is there to choose with. */
    static boolean available() {
        return PolymorphLink.present();
    }

    static String node(int place) {
        return "choice_" + place;
    }

    /** Index of the place a node is, or -1. */
    static int place(String node) {
        for (int i = 0; i < PLACES; i++) {
            if (node(i).equals(node)) {
                return i;
            }
        }
        return -1;
    }

    /** Scene points around the places shown now, empty while none is. */
    static List<float[]> shownPoints() {
        return shownPoints;
    }

    /**
     * Shows the matches that make something else than {@code current} (what the server makes now), each once, as many as there are
     * places; nothing when there is no other.
     */
    void show(StationView view, List<Option> matches, ItemStack current) {
        ResourceLocation[] now = new ResourceLocation[PLACES];
        ItemStack[] made = new ItemStack[PLACES];
        Arrays.fill(made, ItemStack.EMPTY);
        List<ItemStack> seen = new ArrayList<>();
        if (!current.isEmpty()) {
            seen.add(current);
        }
        int place = 0;
        for (Option option : matches) {
            ItemStack makes = option.output();
            if (makes.isEmpty() || seen.stream().anyMatch(other -> ItemStack.isSameItemSameComponents(other, makes))) {
                continue;
            }
            seen.add(makes);
            if (place < PLACES) {
                now[place] = option.recipe();
                made[place] = makes;
                place++;
            }
        }
        if (Arrays.equals(now, recipes) && sameStacks(made, outputs)) {
            return;
        }
        System.arraycopy(now, 0, recipes, 0, PLACES);
        System.arraycopy(made, 0, outputs, 0, PLACES);
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i < PLACES; i++) {
            int at = i;
            view.node(node(i)).ifPresent(node -> {
                node.setVisible(recipes[at] != null);
                if (recipes[at] != null) {
                    node.setTooltip(outputs[at].getHoverName());
                    Vector3fc pos = node.definition().transform().pos();
                    points.add(new float[] {pos.x() - size, pos.y(), pos.z() - size});
                    points.add(new float[] {pos.x() + size, pos.y() + size, pos.z() + size});
                }
            });
        }
        shownPoints = List.copyOf(points);
    }

    /** A place was clicked: its recipe is chosen, as Polymorph's button chooses it. Gives the recipe chosen, or null. */
    ResourceLocation click(StationView view, int place) {
        if (place < 0 || recipes[place] == null) {
            return null;
        }
        ResourceLocation chosen = recipes[place];
        boolean sent = keptByTheBlock ? PolymorphLink.chooseBlockRecipe(chosen) : PolymorphLink.choosePlayerRecipe(chosen);
        if (!sent) {
            return null;
        }
        float volume = 0.3F * VanillaConfig.soundVolume();
        if (volume > 0.0F) {
            view.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, node(place), volume, 1.1F);
        }
        return chosen;
    }

    void clear(StationView view) {
        if (Arrays.stream(recipes).allMatch(Objects::isNull)) {
            return;
        }
        shownPoints = List.of();
        Arrays.fill(recipes, null);
        Arrays.fill(outputs, ItemStack.EMPTY);
        for (int i = 0; i < PLACES; i++) {
            view.node(node(i)).ifPresent(node -> node.setVisible(false));
        }
    }

    /** Standing small and still, turned to the camera as the result is; a little bigger under the cursor. */
    private void paint(StationView view, NodeView node, int place, PoseStack poseStack, MultiBufferSource buffers, int light) {
        ItemStack stack = outputs[place];
        if (stack.isEmpty()) {
            return;
        }
        float shown = size * (node.isHovered() ? HOVER_GROWTH : 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, shown * 0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.scale(shown, shown, shown);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                buffers, view.level(), place);
        poseStack.popPose();
    }

    /** Whether two rows of stacks hold the same, place by place. */
    static boolean sameStacks(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!ItemStack.matches(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }
}
