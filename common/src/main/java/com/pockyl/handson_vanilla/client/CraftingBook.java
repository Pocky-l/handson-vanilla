package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_vanilla.RecipeLaying;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The crafting table's book, in place of the vanilla recipe book: every crafting recipe the player has unlocked (or every one at
 * all, {@link VanillaConfig#allRecipesInBook()}), the recipes of one group in one place (right click: all of them), what can be
 * made now first. A shaped recipe shows its grid as it is laid;
 * picking a recipe lays it into the table the vanilla book's way (shift: as many as fit), so the server checks and moves the
 * items as it would for the vanilla screen, and answers a recipe the player lacks items for with its ghost in the grid. The
 * "only what can be made now" switch and which recipes are new are the vanilla book's own, shared with it.
 */
final class CraftingBook implements RecipeBook.Source {
    private static final int GRID = 3;
    /** The recipes the pages were last made from, and those pages: the same recipes give the same list, made once. */
    private List<CraftingRecipe> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        boolean every = VanillaConfig.allRecipesInBook();
        List<CraftingRecipe> unlocked = new ArrayList<>();
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!recipe.isSpecial() && !recipe.getIngredients().isEmpty() && (every || player.getRecipeBook().contains(recipe))) {
                unlocked.add(recipe);
            }
        }
        // Read four times a second: a big pack's thousands of pages are made again only when a recipe came, went or was reloaded.
        if (sameRecipes(unlocked)) {
            return made;
        }
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (CraftingRecipe recipe : unlocked) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            pages.add(new RecipeBook.Page(recipe.getId(), result.copy(), laid(recipe), List.of(), null, false,
                    recipe.category().name().toLowerCase(Locale.ROOT), recipe.getGroup()));
        }
        madeFrom = unlocked;
        made = List.copyOf(pages);
        return made;
    }

    /** Whether these are the very recipes the pages were made from: the same objects, so a reload is a change too. */
    private boolean sameRecipes(List<CraftingRecipe> unlocked) {
        if (unlocked.size() != madeFrom.size()) {
            return false;
        }
        for (int i = 0; i < unlocked.size(); i++) {
            if (unlocked.get(i) != madeFrom.get(i)) {
                return false;
            }
        }
        return true;
    }

    /** What goes in, a shaped recipe's in rows of three as it lies in the grid, its gaps empty. */
    private static List<Ingredient> laid(CraftingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (!(recipe instanceof ShapedRecipe shaped)) {
            return List.copyOf(ingredients);
        }
        List<Ingredient> grid = new ArrayList<>();
        for (int row = 0; row < shaped.getHeight(); row++) {
            for (int column = 0; column < GRID; column++) {
                grid.add(column < shaped.getWidth() ? ingredients.get(row * shaped.getWidth() + column) : Ingredient.EMPTY);
            }
        }
        return grid;
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        List<ItemStack> there = new ArrayList<>();
        for (int cell = 0; cell < GRID * GRID; cell++) {
            there.add(view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, cell)));
        }
        return there;
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        MenuAccess menu = view.menu();
        if (menu != null && page.key() instanceof ResourceLocation recipe) {
            // The page answers the finger at once; the items follow when the server has laid them.
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.35F * VanillaConfig.soundVolume()));
            CraftingTableBehavior.recipeLaid();
            lay(menu, recipe, Screen.hasShiftDown());
        }
    }

    /**
     * Lays a recipe: one in the player's recipe book as the vanilla book does (its packet, the server's placing and ghost); one not in
     * it (the book shows every recipe, see {@link VanillaConfig#allRecipesInBook()}) with plain clicks, which any server takes, and its
     * ghost shown here when items are missing.
     */
    static void lay(MenuAccess menu, ResourceLocation id, boolean all) {
        LocalPlayer player = Minecraft.getInstance().player;
        Recipe<?> recipe = player == null ? null : player.level().getRecipeManager().byKey(id).orElse(null);
        if (recipe == null || player.getRecipeBook().contains(recipe)
                || !(menu.handle() instanceof net.minecraft.world.inventory.RecipeBookMenu<?> book)) {
            menu.placeRecipe(id, all);
            return;
        }
        if (RecipeLaying.lay(book, player.getInventory(), recipe, all, menu::click) == RecipeLaying.Outcome.MISSING) {
            GhostRecipe.arrived(book.containerId, id);
        }
    }

    /** The vanilla book's tabs (all, then building, redstone, equipment, the rest), with the recipes made lately after all. */
    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        return List.of(tab(null, Items.COMPASS, "all"), tab(RecipeBook.RECENT, Items.CLOCK, "recent"),
                tab("building", Items.BRICKS, "building"), tab("redstone", Items.REDSTONE, "redstone"),
                tab("equipment", Items.IRON_AXE, "equipment"), tab("misc", Items.LAVA_BUCKET, "misc"));
    }

    private static RecipeBook.Tab tab(String kind, net.minecraft.world.item.Item icon, String name) {
        return new RecipeBook.Tab(kind, new ItemStack(icon), Component.translatable("handson_vanilla.book.tab." + name));
    }

    @Override
    public String memory() {
        return "crafting_table";
    }

    @Override
    public boolean hasFilter() {
        return true;
    }

    @Override
    public boolean filtering() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getRecipeBook().isFiltering(RecipeBookType.CRAFTING);
    }

    /** As the vanilla book's switch: kept in the player's recipe book and told to the server, which keeps it with the player. */
    @Override
    public void setFiltering(boolean value) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ClientRecipeBook book = player.getRecipeBook();
        book.setFiltering(RecipeBookType.CRAFTING, value);
        player.connection.send(new ServerboundRecipeBookChangeSettingsPacket(RecipeBookType.CRAFTING, book.isOpen(RecipeBookType.CRAFTING),
                value));
    }

    @Override
    public boolean isNew(RecipeBook.Page page) {
        LocalPlayer player = Minecraft.getInstance().player;
        Recipe<?> recipe = recipe(page);
        return player != null && recipe != null && player.getRecipeBook().willHighlight(recipe);
    }

    @Override
    public void seen(RecipeBook.Page page) {
        LocalPlayer player = Minecraft.getInstance().player;
        Recipe<?> recipe = recipe(page);
        if (player != null && recipe != null) {
            player.getRecipeBook().removeHighlight(recipe);
            player.connection.send(new ServerboundRecipeBookSeenRecipePacket(recipe));
        }
    }

    private static Recipe<?> recipe(RecipeBook.Page page) {
        var level = Minecraft.getInstance().level;
        return level == null || !(page.key() instanceof ResourceLocation id) ? null : level.getRecipeManager().byKey(id).orElse(null);
    }
}
