package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import com.pockyl.diegesis.api.client.StationView;

import java.util.ArrayList;
import java.util.List;

/**
 * The stonecutter's book: everything the stonecutter cuts, a page each, its stone on the page; what can be cut from the stone on
 * the slab or in the inventory comes first, as the book orders what can be made now. Picking a page cuts all the stone on the slab
 * into it at the saw (Shift: one), first laying the right stone on the slab from the inventory when another or none lies there. Every stonecutter
 * recipe is listed, as the vanilla stonecutter offers them all without unlocking.
 */
final class StonecutterBook implements RecipeBook.Source {
    /**
     * A cut, as a page knows it: the client's entry of the stonecutter recipes (since Minecraft 1.21.2 a client knows them only as
     * the server lists them for the stonecutter: what goes in and what comes out), what it makes, and a name that stays from one game
     * to the next for the pieces cut lately and the search (the stone and the piece).
     */
    record Cut(SelectableRecipe.SingleInputEntry<StonecutterRecipe> recipe, ItemStack makes, String name) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Cut cut && cut.recipe.equals(recipe);
        }

        @Override
        public int hashCode() {
            return recipe.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final StonecutterBehavior station;
    /** The recipes the pages were last made from, and those pages: the same recipes give the same list, made once. */
    private List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    StonecutterBook(StonecutterBehavior station) {
        this.station = station;
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes = level.recipeAccess().stonecutterRecipes().entries();
        if (same(recipes)) {
            return made;
        }
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : recipes) {
            Cut cut = cut(entry, level);
            if (cut.makes().isEmpty() || entry.input().isEmpty()) {
                continue;
            }
            pages.add(new RecipeBook.Page(cut, cut.makes().copy(), List.of(entry.input()), List.of(), null, false, null, null));
        }
        madeFrom = recipes;
        made = List.copyOf(pages);
        return made;
    }

    /** A cut of the stonecutter recipes the client knows. */
    static Cut cut(SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry, Level level) {
        ItemStack makes = entry.recipe().optionDisplay().resolveForFirstStack(RecipeDisplays.context(level));
        String stone = entry.input().items().findFirst().map(item -> BuiltInRegistries.ITEM.getKey(item.value()).toString()).orElse("");
        return new Cut(entry, makes, stone + ">" + BuiltInRegistries.ITEM.getKey(makes.getItem()));
    }

    /** Whether these are the very recipes the pages were made from: the same objects, so a reload is a change too. */
    private boolean same(List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes) {
        if (recipes.size() != madeFrom.size()) {
            return false;
        }
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i) != madeFrom.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ItemStack> inStation(StationView view) {
        return List.of(station.input(view));
    }

    @Override
    public void picked(StationView view, RecipeBook.Page page) {
        if (page.key() instanceof Cut recipe) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.4F,
                    0.35F * VanillaConfig.soundVolume()));
            station.cut(view, recipe, !Minecraft.getInstance().hasShiftDown());
        }
    }

    /** The book opens at what the stonecutter is set to cut. */
    @Override
    public Object current(StationView view) {
        return station.picked(view);
    }

    /** All, and the pieces cut lately. */
    @Override
    public List<RecipeBook.Tab> tabs(StationView view) {
        return List.of(tab(null, Items.COMPASS, "all"), tab(RecipeBook.RECENT, Items.CLOCK, "recent"));
    }

    private static RecipeBook.Tab tab(String kind, net.minecraft.world.item.Item icon, String name) {
        return new RecipeBook.Tab(kind, new ItemStack(icon), Component.translatable("handson_vanilla.book.tab." + name));
    }

    @Override
    public String memory() {
        return "stonecutter";
    }

    /**
     * Only what can be cut from the stone on the slab or in the inventory, as the vanilla stonecutter lists only the cuts of the
     * stone it holds: hundreds of pages otherwise. On until the player turns it off; kept on the client.
     */
    @Override
    public boolean hasFilter() {
        return true;
    }

    @Override
    public boolean filtering() {
        return !"false".equals(BookTabs.get(memory() + ".filter"));
    }

    @Override
    public void setFiltering(boolean value) {
        BookTabs.put(memory() + ".filter", value ? null : "false");
    }

    @Override
    public ItemStack filterIcon() {
        return new ItemStack(Items.STONECUTTER);
    }

    /** Pieces are picked only here: a player new to the stonecutter must see the book open. */
    @Override
    public boolean openByDefault() {
        return true;
    }

    @Override
    public boolean required() {
        return true;
    }
}
