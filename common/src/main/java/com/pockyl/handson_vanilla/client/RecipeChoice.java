package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * With Polymorph, one grid can make several things (two mods' recipes for the same shape); its vanilla screen has a button that
 * lists them. At the table the others lie small beside the result, each as what it makes: a click on one makes it the result.
 * The choice goes to the server through Polymorph's own selection packet, which picks the recipe and remakes the result, as its
 * button does. Without Polymorph the server always makes its first match, so there is nothing to choose and nothing is shown.
 */
final class RecipeChoice {
    /** As many others as the scene has places for, beside the result. */
    static final int PLACES = 4;
    private static final float SIZE = 1.9F;
    private static final float HOVER_GROWTH = 1.18F;

    private static Method selection;
    private static boolean looked;
    /** Whether any table shows a choice now. */
    private static boolean anyShown;

    /** Each place's recipe and what it makes; empty places hold nothing. */
    private final ResourceLocation[] recipes = new ResourceLocation[PLACES];
    private final ItemStack[] outputs = new ItemStack[PLACES];
    /** The grid as read last: the matches are read again only when it changes. */
    private ItemStack[] grid = new ItemStack[0];
    private ItemStack result = ItemStack.EMPTY;

    RecipeChoice() {
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

    static boolean isShown() {
        return anyShown;
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

    /** Once a tick at the table: what else the grid makes. */
    void tick(StationView view) {
        if (selection() == null || view.menu() == null || !view.isFocused()) {
            clear(view);
            return;
        }
        ItemStack[] now = new ItemStack[9];
        for (int i = 0; i < now.length; i++) {
            now[i] = view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, i));
        }
        ItemStack shown = view.state().getOutput(HandsOnVanillaClient.RESULT);
        if (same(now, grid) && ItemStack.matches(shown, result)) {
            return;
        }
        grid = Arrays.stream(now).map(ItemStack::copy).toArray(ItemStack[]::new);
        result = shown.copy();
        read(view, now, shown);
    }

    private void read(StationView view, ItemStack[] now, ItemStack shown) {
        Arrays.fill(recipes, null);
        Arrays.fill(outputs, ItemStack.EMPTY);
        var level = view.level();
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < now.length; i++) {
            items.set(i, now[i].copy());
        }
        TransientCraftingContainer container = new TransientCraftingContainer(view.menu().handle(), 3, 3, items);
        List<CraftingRecipe> matches = level.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, container, level);
        int place = 0;
        List<ItemStack> seen = new ArrayList<>();
        if (!shown.isEmpty()) {
            seen.add(shown);
        }
        for (CraftingRecipe recipe : matches) {
            ItemStack makes = recipe.assemble(container, level.registryAccess());
            if (makes.isEmpty() || seen.stream().anyMatch(other -> ItemStack.isSameItemSameTags(other, makes))) {
                continue;
            }
            seen.add(makes);
            if (place < PLACES) {
                recipes[place] = recipe.getId();
                outputs[place] = makes;
                place++;
            }
        }
        anyShown = recipes[0] != null;
        for (int i = 0; i < PLACES; i++) {
            int at = i;
            view.node(node(i)).ifPresent(node -> {
                node.setVisible(recipes[at] != null);
                if (recipes[at] != null) {
                    node.setTooltip(outputs[at].getHoverName());
                }
            });
        }
    }

    /** A place was clicked: its recipe becomes the result. */
    void click(StationView view, int place) {
        Method send = selection();
        if (send == null || place < 0 || recipes[place] == null) {
            return;
        }
        try {
            Object api = Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi").getMethod("common").invoke(null);
            Object distributor = api.getClass().getMethod("getPacketDistributor").invoke(api);
            send.invoke(distributor, recipes[place]);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return;
        }
        float volume = 0.3F * VanillaConfig.soundVolume();
        if (volume > 0.0F) {
            view.playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, node(place), volume, 1.1F);
        }
        // Read again once the server's new result arrives.
        result = ItemStack.EMPTY;
    }

    void clear(StationView view) {
        if (grid.length == 0 && Arrays.stream(recipes).allMatch(java.util.Objects::isNull)) {
            return;
        }
        grid = new ItemStack[0];
        result = ItemStack.EMPTY;
        anyShown = false;
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
        float size = SIZE * (node.isHovered() ? HOVER_GROWTH : 1.0F);
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-35.0F));
        poseStack.scale(size, size, size);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                buffers, view.level(), place);
        poseStack.popPose();
    }

    private static boolean same(ItemStack[] a, ItemStack[] b) {
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

    /** Polymorph's {@code sendPlayerRecipeSelectionC2S}, or null without Polymorph. */
    private static Method selection() {
        if (!looked) {
            looked = true;
            try {
                Class<?> distributor = Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphPacketDistributor");
                selection = distributor.getMethod("sendPlayerRecipeSelectionC2S", ResourceLocation.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                selection = null;
            }
        }
        return selection;
    }
}
