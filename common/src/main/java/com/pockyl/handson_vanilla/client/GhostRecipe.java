package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.Iterator;

/**
 * The vanilla book's ghost recipe at the table: a recipe picked without its items comes back from the server as a ghost, and what it
 * needs lies see-through in the cells it goes into, the result see-through over the edge, until the player puts something into the
 * grid, lays another recipe or leaves. The vanilla screen draws it only when it is the screen open, so the packet is caught for the
 * station ({@code GhostRecipeMixin}).
 */
public final class GhostRecipe {
    private static final int CELLS = 9;
    /** The see-through of the ghosts: as the book's items the player does not have, a little stronger on the wood. */
    private static final int FADED = 0x70FFFFFF;
    private static final float CELL_SIZE = 2.2F;
    private static final float RESULT_SIZE = 3.2F;

    /** A ghost the server sent for a menu, not taken by a station yet. */
    private static int arrivedFor = -1;
    private static ResourceLocation arrived;

    private final Ingredient[] cells = new Ingredient[CELLS];
    private ItemStack result = ItemStack.EMPTY;
    private boolean shown;
    private StationView view;
    private float time;

    /** The server answered a recipe the player lacks items for, or the book found it so laying one by clicks; on the render thread. */
    public static void arrived(int containerId, ResourceLocation recipe) {
        if (Minecraft.getInstance().screen instanceof RecipeUpdateListener) {
            // The vanilla screen is open and showed it itself.
            return;
        }
        arrivedFor = containerId;
        arrived = recipe;
    }

    void frame(StationView view, float dt) {
        this.view = view;
        time += dt;
        MenuAccess menu = view.menu();
        if (arrived == null || menu == null || !view.isFocused()) {
            return;
        }
        AbstractContainerMenu handle = menu.handle();
        if (handle != null && handle.containerId == arrivedFor) {
            ResourceLocation id = arrived;
            arrived = null;
            view.level().getRecipeManager().byKey(id).ifPresent(recipe -> show(recipe, handle));
        }
    }

    /** Lays the ghost out as the vanilla book does, by the same placing as the server's, so it lies where the items would go. */
    private void show(Recipe<?> recipe, AbstractContainerMenu menu) {
        clear();
        int width = 3;
        int height = 3;
        int resultSlot = 0;
        if (menu instanceof RecipeBookMenu<?> book) {
            width = book.getGridWidth();
            height = book.getGridHeight();
            resultSlot = book.getResultSlotIndex();
        }
        int first = resultSlot == 0 ? 1 : 0;
        PlaceRecipe<Ingredient> placing = (Iterator<Ingredient> ingredients, int slot, int maxAmount, int y, int x) -> {
            Ingredient ingredient = ingredients.next();
            int cell = slot - first;
            if (!ingredient.isEmpty() && cell >= 0 && cell < CELLS) {
                cells[cell] = ingredient;
            }
        };
        placing.placeRecipe(width, height, resultSlot, recipe, recipe.getIngredients().iterator(), 0);
        result = recipe.getResultItem(view.level().registryAccess()).copy();
        shown = true;
    }

    void clear() {
        java.util.Arrays.fill(cells, null);
        result = ItemStack.EMPTY;
        shown = false;
    }

    /** Leaving the station: the ghost goes, and one still on its way is dropped. */
    void reset() {
        clear();
        arrived = null;
    }

    /** Draws the ghosts in the empty cells, at the {@code grid} node. */
    NodePainter grid() {
        return (poseStack, buffers, light) -> {
            if (!shown || view == null) {
                return;
            }
            Vector3fc middle = view.node("grid").map(node -> node.definition().transform().pos()).orElse(null);
            if (middle == null) {
                return;
            }
            for (int cell = 0; cell < CELLS; cell++) {
                if (cells[cell] == null || !view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, cell)).isEmpty()) {
                    continue;
                }
                Vector3f at = view.node("grid_" + cell).map(node -> new Vector3f(node.definition().transform().pos()).sub(middle))
                        .orElse(null);
                if (at == null) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                lying(choice(cells[cell]), cell, poseStack, buffers, light);
                poseStack.popPose();
            }
        };
    }

    /** Draws the ghost of the result floating where the result would, at the {@code result} node, while there is none. */
    NodePainter result() {
        return (poseStack, buffers, light) -> {
            if (!shown || result.isEmpty() || view == null || !view.state().getOutput(HandsOnVanillaClient.RESULT).isEmpty()) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, RESULT_SIZE * 0.55F + Mth.sin(time * 2.2F) * 0.35F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 40.0F));
            poseStack.scale(RESULT_SIZE, RESULT_SIZE, RESULT_SIZE);
            render(result, 0, poseStack, buffers, light);
            poseStack.popPose();
        };
    }

    /** One of what an ingredient takes, a new one each second, as the vanilla ghost goes through them. */
    private ItemStack choice(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[(int) (time % items.length)];
    }

    /** Lying in its cell as the table's own items lie (a block standing, a flat item flat). */
    private void lying(ItemStack stack, int cell, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) {
            return;
        }
        boolean block = Minecraft.getInstance().getItemRenderer().getModel(stack, view.level(), null, 0).isGui3d();
        float jitter = ((cell * 7919) % 25 - 12) * 0.7F;
        if (block) {
            poseStack.translate(0.0F, CELL_SIZE * 0.25F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + jitter));
        } else {
            poseStack.translate(0.0F, 0.07F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F + jitter));
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
        poseStack.scale(CELL_SIZE, CELL_SIZE, CELL_SIZE);
        render(stack, cell, poseStack, buffers, light);
    }

    private void render(ItemStack stack, int seed, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack,
                DiegesisClient.tinted(buffers, FADED), view.level(), seed);
    }
}
