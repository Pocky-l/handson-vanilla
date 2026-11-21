package com.pockyl.handson_vanilla;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.recipebook.PlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Iterator;

/**
 * Lays a recipe into a crafting grid with plain slot clicks, for recipes the vanilla book's packet cannot lay: the server takes that
 * packet only for recipes in the player's recipe book, and a mod that turns the book off leaves it empty. The items go where the
 * server's own placing would put them, as many as it would (one set more, or as many as fit), so the result is the vanilla book's;
 * any server takes the clicks. Outside {@code client}, so a game test can run it on a server's menu.
 */
public final class RecipeLaying {
    /** Clicks within one cell at most, a guard against a click that changes nothing. */
    private static final int MOVES_PER_CELL = 64;

    private RecipeLaying() {
    }

    /** A click on a menu slot, as the vanilla screen makes it. */
    @FunctionalInterface
    public interface Clicker {
        void click(int slot, int button, ClickType type);
    }

    /** What came of it. */
    public enum Outcome {
        /** The items lie in the grid. */
        LAID,
        /** The player lacks items for it: the grid is emptied, as the vanilla book does before it shows the recipe's ghost. */
        MISSING,
        /** Nothing was done: the grid holds all it can already, or something is in the way (an item on the cursor, a full inventory). */
        UNCHANGED
    }

    /**
     * Lays {@code recipe} into {@code menu}'s grid from {@code inventory}, the player's whose menu it is.
     *
     * @param all as many sets as fit (the vanilla book's shift click), else one set more than lies there now
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Outcome lay(RecipeBookMenu<?> menu, Inventory inventory, Recipe<?> recipe, boolean all, Clicker clicker) {
        if (!menu.getCarried().isEmpty()) {
            return Outcome.UNCHANGED;
        }
        StackedContents contents = new StackedContents();
        inventory.fillStackedContents(contents);
        menu.fillCraftSlotsStackedContents(contents);
        if (!contents.canCraft(recipe, null)) {
            return clearGrid(menu, inventory, clicker) ? Outcome.MISSING : Outcome.UNCHANGED;
        }
        boolean matches = ((RecipeBookMenu) menu).recipeMatches(recipe);
        int biggest = contents.getBiggestCraftableStack(recipe, null);
        if (matches) {
            for (int slot : gridSlots(menu)) {
                ItemStack there = menu.getSlot(slot).getItem();
                if (!there.isEmpty() && Math.min(biggest, there.getMaxStackSize()) < there.getCount() + 1) {
                    return Outcome.UNCHANGED;
                }
            }
        }
        int sets = sets(menu, all, biggest, matches);
        IntList items = new IntArrayList();
        if (!contents.canCraft(recipe, items, sets)) {
            return Outcome.UNCHANGED;
        }
        for (int item : items) {
            // An empty cell of a shaped recipe is item 0: it limits nothing.
            ItemStack stack = StackedContents.fromStackingIndex(item);
            if (!stack.isEmpty()) {
                sets = Math.min(sets, stack.getMaxStackSize());
            }
        }
        IntList picked = new IntArrayList();
        if (!contents.canCraft(recipe, picked, sets)) {
            return Outcome.UNCHANGED;
        }
        ItemStack[] wanted = new ItemStack[menu.slots.size()];
        int[] counts = new int[menu.slots.size()];
        int each = sets;
        PlaceRecipe<Integer> placing = (Iterator<Integer> ingredients, int slot, int maxAmount, int x, int y) -> {
            ItemStack item = StackedContents.fromStackingIndex(ingredients.next());
            if (!item.isEmpty() && slot >= 0 && slot < wanted.length) {
                wanted[slot] = item;
                counts[slot] = each;
            }
        };
        placing.placeRecipe(menu.getGridWidth(), menu.getGridHeight(), menu.getResultSlotIndex(), recipe, picked.iterator(), sets);
        // What lies in a cell and is wanted there stays; everything else goes back first, as it may be wanted in another cell.
        for (int slot : gridSlots(menu)) {
            ItemStack there = menu.getSlot(slot).getItem();
            if (!there.isEmpty() && !(wanted[slot] != null && same(there, wanted[slot]) && there.getCount() <= counts[slot])) {
                clicker.click(slot, 0, ClickType.QUICK_MOVE);
                if (!menu.getSlot(slot).getItem().isEmpty()) {
                    return Outcome.UNCHANGED;
                }
            }
        }
        for (int slot : gridSlots(menu)) {
            if (wanted[slot] != null) {
                fill(menu, inventory, slot, wanted[slot], counts[slot], clicker);
            }
        }
        return Outcome.LAID;
    }

    /** How many sets to lay: as the server's placing counts them. */
    private static int sets(RecipeBookMenu<?> menu, boolean all, int biggest, boolean matches) {
        if (all) {
            return biggest;
        }
        if (!matches) {
            return 1;
        }
        int fewest = 64;
        for (int slot : gridSlots(menu)) {
            ItemStack there = menu.getSlot(slot).getItem();
            if (!there.isEmpty()) {
                fewest = Math.min(fewest, there.getCount());
            }
        }
        return fewest < 64 ? fewest + 1 : fewest;
    }

    /**
     * Brings {@code cell} up to {@code count} of {@code item} from the inventory, from the stacks the server's placing takes (not
     * damaged, enchanted or named): a whole stack when it is not more than is wanted, else the cheaper of putting the wanted ones down
     * one by one and putting the unwanted ones back one by one.
     */
    private static void fill(RecipeBookMenu<?> menu, Inventory inventory, int cell, ItemStack item, int count, Clicker clicker) {
        for (int move = 0; move < MOVES_PER_CELL; move++) {
            ItemStack there = menu.getSlot(cell).getItem();
            int need = count - (there.isEmpty() ? 0 : there.getCount());
            if (need <= 0) {
                return;
            }
            int source = menuSlotOf(menu, inventory, inventory.findSlotMatchingUnusedItem(item));
            if (source < 0) {
                return;
            }
            int held = menu.getSlot(source).getItem().getCount();
            clicker.click(source, 0, ClickType.PICKUP);
            if (held <= need) {
                clicker.click(cell, 0, ClickType.PICKUP);
            } else if (need <= held - need) {
                for (int i = 0; i < need; i++) {
                    clicker.click(cell, 1, ClickType.PICKUP);
                }
            } else {
                for (int i = 0; i < held - need; i++) {
                    clicker.click(source, 1, ClickType.PICKUP);
                }
                clicker.click(cell, 0, ClickType.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) {
                clicker.click(source, 0, ClickType.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) {
                // The source slot took something else meanwhile: never leave items on the cursor.
                return;
            }
        }
    }

    /** Empties the grid into the inventory; false when something stays, the inventory being full. */
    private static boolean clearGrid(RecipeBookMenu<?> menu, Inventory inventory, Clicker clicker) {
        boolean clear = true;
        for (int slot : gridSlots(menu)) {
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                clicker.click(slot, 0, ClickType.QUICK_MOVE);
                clear &= menu.getSlot(slot).getItem().isEmpty();
            }
        }
        return clear;
    }

    /** The menu slots of the grid, without the result. */
    private static int[] gridSlots(RecipeBookMenu<?> menu) {
        int size = menu.getGridWidth() * menu.getGridHeight();
        int[] slots = new int[size];
        int result = menu.getResultSlotIndex();
        int next = 0;
        for (int slot = 0; next < size && slot < menu.slots.size(); slot++) {
            if (slot != result) {
                slots[next++] = slot;
            }
        }
        return slots;
    }

    /** The menu slot that shows the inventory's slot {@code index}, or -1. */
    private static int menuSlotOf(RecipeBookMenu<?> menu, Inventory inventory, int index) {
        if (index < 0) {
            return -1;
        }
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getContainerSlot() == index) {
                return slot.index;
            }
        }
        return -1;
    }

    private static boolean same(ItemStack there, ItemStack wanted) {
        return ItemStack.isSameItemSameTags(there, wanted);
    }
}
