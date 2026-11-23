package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationView;

/** What recipe viewers ask of a station: the item under the cursor, and the vanilla menu it works through. */
public final class StationHover {
    /** Room left between the station's right edge on the screen and a viewer's list, GUI pixels. */
    private static final int VIEWER_GAP = 6;
    private static MenuScreen menuScreen;
    /**
     * The station's screen the never-shown container screen was last asked for over: where a viewer going back to it goes. Held
     * while the station is worked at, as nothing else may hold it while a viewer's page is open.
     */
    private static Screen stationScreen;

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
     * A container screen over the station's open menu that is never shown, or null away from a station: later JEI builds ask the
     * screen under their recipe page for the menu their "+" fills, and a station's own screen is none.
     */
    public static AbstractContainerScreen<?> menuScreen() {
        AbstractContainerMenu menu = menu();
        if (menu == null || Minecraft.getInstance().player == null) {
            menuScreen = null;
            stationScreen = null;
            return null;
        }
        Screen shown = Minecraft.getInstance().screen;
        if (DiegesisClient.isStationScreen(shown)) {
            stationScreen = shown;
        }
        if (menuScreen == null || menuScreen.getMenu() != menu) {
            menuScreen = new MenuScreen(menu, Minecraft.getInstance().player.getInventory());
        }
        return menuScreen;
    }

    /**
     * What to show when {@code screen} is asked for: the container screen of {@link #menuScreen()} is never shown, a viewer going back
     * to it (EMI's recipe page takes it as the screen it was opened over) is back at the station, or out of it once that is left.
     */
    public static Screen shownInstead(Screen screen) {
        if (!(screen instanceof MenuScreen)) {
            return screen;
        }
        Screen station = DiegesisClient.isFocused() ? stationScreen : null;
        stationScreen = station;
        return station;
    }

    /** Whether {@code screen} is the screen of a station of this mod being worked at. */
    public static boolean isOurs(Screen screen) {
        return DiegesisClient.isStationScreen(screen) && DiegesisClient.focusedStation()
                .map(view -> view.type().id().getNamespace().equals(com.pockyl.handson_vanilla.HandsOnVanilla.MOD_ID)).orElse(false);
    }

    private static final class MenuScreen extends AbstractContainerScreen<AbstractContainerMenu> {
        MenuScreen(AbstractContainerMenu menu, Inventory inventory) {
            super(menu, inventory, Component.empty());
        }
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

    /** How wide the middle of the screen a viewer leaves to the station is, GUI pixels: up to its right edge and a little room. */
    public static int viewerMiddle(int screenWidth) {
        return Math.min(screenWidth, Math.round(rightEdge()) + VIEWER_GAP);
    }

    /** A viewer lays a recipe into the crafting table station: its items fly in as the book's do. */
    public static void recipeLaid() {
        CraftingTableBehavior.recipeLaid();
    }

    /** The player's recipe book's entry for a recipe a recipe viewer names, which the book's packet lays; empty when it has none. */
    public static java.util.Optional<net.minecraft.world.item.crafting.display.RecipeDisplayId> bookEntry(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key, net.minecraft.world.item.crafting.Recipe<?> recipe) {
        return ClientRecipes.knownEntry(key, recipe);
    }

    /** Lays a recipe into the station's menu as the vanilla recipe book does. */
    public static void placeRecipe(net.minecraft.world.item.crafting.display.RecipeDisplayId recipe, boolean all) {
        CraftingTableBehavior.recipeLaid();
        DiegesisClient.focusedStation().map(StationView::menu).ifPresent(menu -> menu.placeRecipe(recipe, all));
    }
}
