package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
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
    private final StonecutterBehavior station;
    /** The recipes the pages were last made from, and those pages: the same recipes give the same list, made once. */
    private List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> madeFrom = List.of();
    private List<RecipeBook.Page> made = List.of();

    StonecutterBook(StonecutterBehavior station) {
        this.station = station;
    }

    /**
     * A cut, as the server sends every client its stonecutter recipes since Minecraft 1.21.2: the stone it takes and what it shows it
     * makes, no id. Two cuts are the same when those are; the name the book remembers it by stays across sessions.
     */
    record Cut(SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry) {
        /** What the cut makes, as the stonecutter shows its button. */
        ItemStack makes(Level level) {
            return entry.recipe().optionDisplay().resolveForFirstStack(SlotDisplayContext.fromLevel(level));
        }

        @Override
        public String toString() {
            Level level = Minecraft.getInstance().level;
            String into = level == null ? "" : BuiltInRegistries.ITEM.getKey(makes(level).getItem()).toString();
            return entry.input().items().findFirst().map(Holder::getRegisteredName).orElse("") + ">" + into;
        }
    }

    @Override
    public List<RecipeBook.Page> pages(StationView view) {
        Level level = view.level();
        // Since Minecraft 1.21.2 every client is sent the stonecutter's recipes as entries (what each takes, what it shows): a page each.
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes = level.recipeAccess().stonecutterRecipes().entries();
        if (same(recipes)) {
            return made;
        }
        List<RecipeBook.Page> pages = new ArrayList<>();
        for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : recipes) {
            Cut cut = new Cut(entry);
            ItemStack result = cut.makes(level);
            if (result.isEmpty() || entry.input().isEmpty()) {
                continue;
            }
            pages.add(new RecipeBook.Page(cut, result.copy(), List.of(entry.input()), List.of(), null, false, null, null));
        }
        madeFrom = recipes;
        made = List.copyOf(pages);
        return made;
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
            // One piece a pick, so a stack is never cut by mistake; Shift cuts all the stone on the slab.
            station.cut(view, recipe, Minecraft.getInstance().hasShiftDown());
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
