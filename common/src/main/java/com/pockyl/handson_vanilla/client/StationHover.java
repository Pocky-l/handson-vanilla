package com.pockyl.handson_vanilla.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationView;

/** What recipe viewers ask of a station: the item under the cursor, and the vanilla menu it works through. */
public final class StationHover {
    private StationHover() {
    }

    /** The item under the cursor at a station: a dish in the open recipe book, else a slot's or the inventory's stack. */
    public static ItemStack stack() {
        if (!DiegesisClient.isFocused()) {
            return ItemStack.EMPTY;
        }
        ItemStack inBook = RecipeBook.hoveredDish();
        return !inBook.isEmpty() ? inBook : DiegesisClient.hoveredStack();
    }

    /** The open menu of the menu station the local player works at, or null. */
    public static AbstractContainerMenu menu() {
        if (!DiegesisClient.isFocused()) {
            return null;
        }
        MenuAccess menu = DiegesisClient.focusedStation().map(StationView::menu).orElse(null);
        return menu == null ? null : menu.handle();
    }

    /**
     * How far right the station reaches on the screen, GUI pixels: the block's corners, its result and the hotbar, as drawn last frame;
     * while the camera flies in or back, where they will be once it is there, so what is laid out beside the station takes its place
     * at the click. The middle of the screen plus a little while nothing is known yet.
     */
    public static float rightEdge() {
        float fallback = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2.0F + 100.0F;
        StationView view = DiegesisClient.focusedStation().orElse(null);
        if (view == null) {
            return fallback;
        }
        float right = Float.NEGATIVE_INFINITY;
        float[][] points = RecipeChoice.isShown()
                // The other recipes lie right of the result while there is a choice: the viewer's list keeps clear of them too.
                ? new float[][] {{0, 16, 0}, {16, 16, 0}, {0, 16, 16}, {16, 16, 16}, {0, 0, 0}, {16, 0, 0}, {0, 21, 8}, {-7.2F, 18.5F, 11}, {-7.2F, 16, 4}}
                : new float[][] {{0, 16, 0}, {16, 16, 0}, {0, 16, 16}, {16, 16, 16}, {0, 0, 0}, {16, 0, 0}, {0, 21, 8}};
        boolean flying = DiegesisClient.arrival() < 1.0F;
        for (float[] point : points) {
            var at = flying ? FinalShot.toScreen(view, point[0], point[1], point[2])
                    : DiegesisClient.toScreen(view.toWorld(point[0], point[1], point[2]));
            if (at.isPresent()) {
                right = Math.max(right, at.get().x);
            }
        }
        // The hotbar stays at the bottom in the middle (half of it 91 pixels wide): nothing may lie over its end.
        float hotbar = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2.0F + 92.0F;
        return right == Float.NEGATIVE_INFINITY ? fallback : Math.max(right, hotbar);
    }

    /** Lays a recipe into the station's menu as the vanilla recipe book does. */
    public static void placeRecipe(ResourceLocation recipe, boolean all) {
        CraftingTableBehavior.recipeLaid();
        DiegesisClient.focusedStation().map(StationView::menu).ifPresent(menu -> menu.placeRecipe(recipe, all));
    }
}
