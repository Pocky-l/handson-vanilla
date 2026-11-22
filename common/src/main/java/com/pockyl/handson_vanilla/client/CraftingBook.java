package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.handson_vanilla.RecipeLaying;
import com.pockyl.handson_vanilla.RecipeParts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crafting table's book, in place of the vanilla recipe book: every crafting recipe in the player's recipe book (or, in
 * singleplayer, every one at all, {@link VanillaConfig#allRecipesInBook()}), the recipes of one group in one place (right click: all
 * of them), what can be made now first. A shaped recipe shows its grid as it is laid; picking a recipe lays it into the table the
 * vanilla book's way (shift: as many as fit), so the server checks and moves the items as it would for the vanilla screen, and answers
 * a recipe the player lacks items for with its ghost in the grid. The "only what can be made now" switch and which recipes are new
 * are the vanilla book's own, shared with it.
 * <p>
 * Since Minecraft 1.21.2 a client knows recipes only as the recipe book's entries ({@link ClientRecipes}): a page is one entry, known
 * by its id.
 */
final class CraftingBook implements RecipeBook.Source {
    private static final int GRID = 3;
    /** The entries the pages were last made from, and those pages: the same entries give the same list, made once. */
    private List<RecipeDisplayEntry> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        if (Minecraft.getInstance().player == null) {
            return List.of();
        }
        List<RecipeDisplayEntry> entries = VanillaConfig.allRecipesInBook() ? ClientRecipes.every() : ClientRecipes.known();
        // Read four times a second: a big pack's thousands of pages are made again only when a recipe came, went or was reloaded.
        if (same(entries)) {
            return made;
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        List<RecipeBook.Page> pages = new ArrayList<>();
        Map<String, Integer> names = new HashMap<>();
        for (RecipeDisplayEntry entry : entries) {
            List<ItemStack> results = entry.resultItems(context);
            List<Ingredient> laid = laid(entry.display(), context);
            if (results.isEmpty() || results.getFirst().isEmpty() || laid.isEmpty()) {
                continue;
            }
            pages.add(new RecipeBook.Page(new Key(entry.id(), name(entry.id(), results.getFirst(), names)), results.getFirst().copy(),
                    laid, List.of(), null, false, kind(entry.category()),
                    entry.group().isPresent() ? Integer.toString(entry.group().getAsInt()) : ""));
        }
        madeFrom = entries;
        made = List.copyOf(pages);
        return made;
    }

    /**
     * A page's key: the recipe as the server numbers it for this session, and a name for it that stays across sessions, for the
     * recipes laid lately and the search (the recipe's id in singleplayer, as on 1.21.1; else what it makes).
     */
    record Key(RecipeDisplayId id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** The recipe's id when the game's own server knows it, else the id of what it makes, numbered from the second on. */
    private static String name(RecipeDisplayId id, ItemStack result, Map<String, Integer> names) {
        String name = ClientRecipes.recipe(id).map(holder -> holder.id().identifier().toString())
                .orElseGet(() -> BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        int same = names.merge(name, 1, Integer::sum);
        return same > 1 ? name + "#" + same : name;
    }

    /** Whether these are the very entries the pages were made from: the same objects, so a new recipe book is a change too. */
    private boolean same(List<RecipeDisplayEntry> entries) {
        if (entries.size() != madeFrom.size()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) != madeFrom.get(i)) {
                return false;
            }
        }
        return true;
    }

    /** The tab a recipe goes under, by its recipe book category. */
    private static String kind(RecipeBookCategory category) {
        if (category == RecipeBookCategories.CRAFTING_BUILDING_BLOCKS) {
            return "building";
        }
        if (category == RecipeBookCategories.CRAFTING_REDSTONE) {
            return "redstone";
        }
        if (category == RecipeBookCategories.CRAFTING_EQUIPMENT) {
            return "equipment";
        }
        return "misc";
    }

    /** What goes in, a shaped recipe's in rows of three as it lies in the grid, its gaps empty; nothing for another kind of display. */
    private static List<Ingredient> laid(RecipeDisplay display, ContextMap context) {
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            List<Ingredient> parts = new ArrayList<>();
            for (SlotDisplay part : shapeless.ingredients()) {
                parts.add(ingredient(part, context));
            }
            return parts;
        }
        if (!(display instanceof ShapedCraftingRecipeDisplay shaped)) {
            return List.of();
        }
        List<Ingredient> grid = new ArrayList<>();
        for (int row = 0; row < shaped.height(); row++) {
            for (int column = 0; column < GRID; column++) {
                grid.add(column < shaped.width() ? ingredient(shaped.ingredients().get(row * shaped.width() + column), context)
                        : RecipeParts.NOTHING);
            }
        }
        return grid;
    }

    /** What a place of the recipe takes, as an ingredient of the items it shows; a gap when it shows none. */
    private static Ingredient ingredient(SlotDisplay part, ContextMap context) {
        List<ItemStack> items = part.resolveForStacks(context);
        return items.isEmpty() ? RecipeParts.NOTHING : Ingredient.of(items.stream().map(ItemStack::getItem).distinct());
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
        if (menu != null && page.key() instanceof Key(RecipeDisplayId recipe, String ignored)) {
            // The page answers the finger at once; the items follow when the server has laid them.
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.35F * VanillaConfig.soundVolume()));
            CraftingTableBehavior.recipeLaid();
            lay(menu, recipe, Minecraft.getInstance().hasShiftDown());
        }
    }

    /**
     * Lays a recipe: one in the player's recipe book as the vanilla book does (its packet, the server's placing and ghost); one not in
     * it (the book shows every recipe in singleplayer, see {@link VanillaConfig#allRecipesInBook()}) with plain clicks, and its ghost
     * shown here when items are missing.
     */
    static void lay(MenuAccess menu, RecipeDisplayId id, boolean all) {
        LocalPlayer player = Minecraft.getInstance().player;
        Set<RecipeDisplayId> known = ClientRecipes.knownIds();
        if (player == null || known.contains(id) || !(menu.handle() instanceof AbstractCraftingMenu crafting)) {
            menu.placeRecipe(id, all);
            return;
        }
        ClientRecipes.recipe(id).map(holder -> holder.value() instanceof CraftingRecipe recipe ? recipe : null).ifPresentOrElse(recipe -> {
            if (RecipeLaying.lay(crafting, player.getInventory(), recipe, all, menu::click) == RecipeLaying.Outcome.MISSING) {
                ClientRecipes.every().stream().filter(entry -> entry.id().equals(id)).findFirst()
                        .ifPresent(entry -> GhostRecipe.arrived(crafting.containerId, entry.display()));
            }
        }, () -> menu.placeRecipe(id, all));
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
        return player != null && page.key() instanceof Key(RecipeDisplayId id, String ignored) && player.getRecipeBook().willHighlight(id);
    }

    @Override
    public void seen(RecipeBook.Page page) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && page.key() instanceof Key(RecipeDisplayId id, String ignored) && player.getRecipeBook().willHighlight(id)) {
            player.getRecipeBook().removeHighlight(id);
            player.connection.send(new ServerboundRecipeBookSeenRecipePacket(id));
        }
    }
}
