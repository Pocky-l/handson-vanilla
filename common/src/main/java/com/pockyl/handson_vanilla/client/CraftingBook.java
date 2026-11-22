package com.pockyl.handson_vanilla.client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
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
import com.pockyl.handson_vanilla.RecipeParts;

import java.util.ArrayList;
import java.util.List;

/**
 * The crafting table's book, in place of the vanilla recipe book: every crafting recipe the player has unlocked, the recipes of one
 * group in one place (right click: all of them), what can be made now first. A shaped recipe shows its grid as it is laid;
 * picking a recipe lays it into the table the vanilla book's way (shift: as many as fit), so the server checks and moves the
 * items as it would for the vanilla screen, and answers a recipe the player lacks items for with its ghost in the grid. The
 * "only what can be made now" switch and which recipes are new are the vanilla book's own, shared with it.
 *
 * <p>Since Minecraft 1.21.2 the client knows recipes only as the server shows them in the player's recipe book (displays, by
 * {@link RecipeDisplayId}), so the book shows the unlocked recipes only, whatever {@link VanillaConfig#allRecipesInBook()} says.
 */
final class CraftingBook implements RecipeBook.Source {
    private static final int GRID = 3;
    /** The recipes the pages were last made from, and those pages: the same recipes give the same list, made once. */
    private List<RecipeDisplayEntry> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<RecipeDisplayEntry> unlocked = known(player);
        // Read four times a second: a big pack's thousands of pages are made again only when a recipe came, went or was reloaded.
        if (sameRecipes(unlocked)) {
            return made;
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (RecipeDisplayEntry entry : unlocked) {
            ItemStack result = entry.display().result().resolveForFirstStack(context);
            List<Ingredient> laid = laid(entry, context);
            if (result.isEmpty() || laid.stream().allMatch(Ingredient::isEmpty)) {
                continue;
            }
            Identifier category = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
            String group = entry.group().isPresent() ? Integer.toString(entry.group().getAsInt()) : "";
            pages.add(new RecipeBook.Page(new Key(entry.id(), name(result, laid)), result.copy(), laid, List.of(), null, false,
                    kind(category), group));
        }
        madeFrom = unlocked;
        made = List.copyOf(pages);
        return made;
    }

    /**
     * A page's key: the recipe as the server numbers it for this session, and a name for it that stays across sessions (what the book
     * remembers of the recipes laid last).
     */
    record Key(RecipeDisplayId id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** What makes a recipe known across sessions: what it makes and the first item of each place. */
    private static String name(ItemStack result, List<Ingredient> laid) {
        StringBuilder name = new StringBuilder(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
        for (Ingredient ingredient : laid) {
            name.append('|').append(ingredient.items().findFirst().map(item -> item.getRegisteredName()).orElse(""));
        }
        return Integer.toHexString(name.toString().hashCode());
    }

    /** The crafting recipes in the player's recipe book, as the server showed them. */
    private static List<RecipeDisplayEntry> known(LocalPlayer player) {
        List<RecipeDisplayEntry> known = new ArrayList<>();
        for (RecipeCollection collection : player.getRecipeBook().getCollection(SearchRecipeBookCategory.CRAFTING)) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                RecipeDisplay display = entry.display();
                if (display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay) {
                    known.add(entry);
                }
            }
        }
        return known;
    }

    /** The tab a recipe book category belongs to, by the names the 1.21.1 categories had ({@code building}, {@code redstone}...). */
    private static String kind(Identifier category) {
        if (category == null) {
            return "misc";
        }
        return switch (category.getPath()) {
            case "crafting_building_blocks" -> "building";
            case "crafting_redstone" -> "redstone";
            case "crafting_equipment" -> "equipment";
            default -> "misc";
        };
    }

    /** Whether these are the very recipes the pages were made from: the same objects, so a new recipe book is a change too. */
    private boolean sameRecipes(List<RecipeDisplayEntry> unlocked) {
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

    /**
     * What goes in, a shaped recipe's in rows of three as it lies in the grid, its gaps empty ({@link RecipeParts#NOTHING}). The
     * recipe's own ingredients when the server sent them (in the order of the places that take something), else what each place
     * shows.
     */
    private static List<Ingredient> laid(RecipeDisplayEntry entry, ContextMap context) {
        List<Ingredient> requirements = entry.craftingRequirements().orElse(null);
        if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
            if (requirements != null) {
                return List.copyOf(requirements);
            }
            return shapeless.ingredients().stream().map(slot -> RecipeParts.fromDisplay(slot, context)).toList();
        }
        ShapedCraftingRecipeDisplay shaped = (ShapedCraftingRecipeDisplay) entry.display();
        List<Ingredient> cells = new ArrayList<>();
        int next = 0;
        for (SlotDisplay slot : shaped.ingredients()) {
            if (slot instanceof SlotDisplay.Empty) {
                cells.add(RecipeParts.NOTHING);
            } else if (requirements != null && next < requirements.size()) {
                cells.add(requirements.get(next++));
            } else {
                cells.add(RecipeParts.fromDisplay(slot, context));
            }
        }
        List<Ingredient> grid = new ArrayList<>();
        for (int row = 0; row < shaped.height(); row++) {
            for (int column = 0; column < GRID; column++) {
                grid.add(column < shaped.width() ? cells.get(row * shaped.width() + column) : RecipeParts.NOTHING);
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
        if (menu != null && page.key() instanceof Key(RecipeDisplayId recipe, String ignored)) {
            // The page answers the finger at once; the items follow when the server has laid them.
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.4F, 0.35F * VanillaConfig.soundVolume()));
            CraftingTableBehavior.recipeLaid();
            // Every recipe the book shows is in the player's recipe book: the vanilla book's packet, the server's placing and ghost.
            menu.placeRecipe(recipe, Minecraft.getInstance().hasShiftDown());
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
        return player != null && page.key() instanceof Key(RecipeDisplayId recipe, String ignored)
                && player.getRecipeBook().willHighlight(recipe);
    }

    @Override
    public void seen(RecipeBook.Page page) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && page.key() instanceof Key(RecipeDisplayId recipe, String ignored)) {
            player.getRecipeBook().removeHighlight(recipe);
            player.connection.send(new ServerboundRecipeBookSeenRecipePacket(recipe));
        }
    }
}
