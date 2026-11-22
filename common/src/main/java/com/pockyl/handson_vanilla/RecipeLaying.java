package com.pockyl.handson_vanilla;

import net.minecraft.core.Holder;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        void click(int slot, int button, ContainerInput type);
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
    public static Outcome lay(AbstractCraftingMenu menu, Inventory inventory, RecipeHolder<CraftingRecipe> holder, boolean all,
            Clicker clicker) {
        CraftingRecipe recipe = holder.value();
        if (!menu.getCarried().isEmpty()) {
            return Outcome.UNCHANGED;
        }
        StackedItemContents contents = new StackedItemContents();
        inventory.fillStackedContents(contents);
        menu.fillCraftSlotsStackedContents(contents);
        if (!contents.canCraft(recipe, null)) {
            return clearGrid(menu, clicker) ? Outcome.MISSING : Outcome.UNCHANGED;
        }
        boolean matches = recipe.matches(input(menu), inventory.player.level());
        int biggest = contents.getBiggestCraftableStack(recipe, null);
        if (matches) {
            for (Slot slot : menu.getInputGridSlots()) {
                ItemStack there = slot.getItem();
                if (!there.isEmpty() && Math.min(biggest, there.getMaxStackSize()) < there.getCount() + 1) {
                    return Outcome.UNCHANGED;
                }
            }
        }
        int sets = sets(menu, all, biggest, matches);
        List<Holder<Item>> picks = new ArrayList<>();
        if (!contents.canCraft(recipe, sets, picks::add)) {
            return Outcome.UNCHANGED;
        }
        int fitting = sets;
        for (Holder<Item> item : picks) {
            fitting = Math.min(fitting, item.value().getDefaultMaxStackSize());
        }
        if (fitting != sets) {
            sets = fitting;
            picks.clear();
            if (!contents.canCraft(recipe, sets, picks::add)) {
                return Outcome.UNCHANGED;
            }
        }
        List<Slot> grid = menu.getInputGridSlots();
        List<Holder<Item>> wanted = new ArrayList<>(Collections.nCopies(grid.size(), null));
        int each = sets;
        PlaceRecipeHelper.placeRecipe(menu.getGridWidth(), menu.getGridHeight(), recipe, recipe.placementInfo().slotsToIngredientIndex(),
                (Integer picked, int cell, int x, int y) -> {
                    if (picked != -1 && cell >= 0 && cell < wanted.size()) {
                        wanted.set(cell, picks.get(picked));
                    }
                });
        // What lies in a cell and is wanted there stays; everything else goes back first, as it may be wanted in another cell.
        for (int cell = 0; cell < grid.size(); cell++) {
            Slot slot = grid.get(cell);
            ItemStack there = slot.getItem();
            Holder<Item> item = wanted.get(cell);
            if (!there.isEmpty() && !(item != null && there.is(item) && there.getCount() <= each)) {
                clicker.click(slot.index, 0, ContainerInput.QUICK_MOVE);
                if (!slot.getItem().isEmpty()) {
                    return Outcome.UNCHANGED;
                }
            }
        }
        for (int cell = 0; cell < grid.size(); cell++) {
            if (wanted.get(cell) != null) {
                fill(menu, inventory, grid.get(cell), wanted.get(cell), each, clicker);
            }
        }
        return Outcome.LAID;
    }

    /** What lies in the grid, as the recipe sees it. */
    private static CraftingInput input(AbstractCraftingMenu menu) {
        List<ItemStack> items = new ArrayList<>();
        for (Slot slot : menu.getInputGridSlots()) {
            items.add(slot.getItem().copy());
        }
        return CraftingInput.of(menu.getGridWidth(), menu.getGridHeight(), items);
    }

    /** How many sets to lay: as the server's placing counts them. */
    private static int sets(AbstractCraftingMenu menu, boolean all, int biggest, boolean matches) {
        if (all) {
            return biggest;
        }
        if (!matches) {
            return 1;
        }
        int fewest = Integer.MAX_VALUE;
        for (Slot slot : menu.getInputGridSlots()) {
            ItemStack there = slot.getItem();
            if (!there.isEmpty()) {
                fewest = Math.min(fewest, there.getCount());
            }
        }
        return fewest == Integer.MAX_VALUE ? fewest : fewest + 1;
    }

    /**
     * Brings {@code cell} up to {@code count} of {@code item} from the inventory, from the stacks the server's placing takes (not
     * damaged, enchanted or named, and like what lies there already): a whole stack when it is not more than is wanted, else the
     * cheaper of putting the wanted ones down one by one and putting the unwanted ones back one by one.
     */
    private static void fill(AbstractCraftingMenu menu, Inventory inventory, Slot cell, Holder<Item> item, int count, Clicker clicker) {
        for (int move = 0; move < MOVES_PER_CELL; move++) {
            ItemStack there = cell.getItem();
            int need = count - (there.isEmpty() ? 0 : there.getCount());
            if (need <= 0) {
                return;
            }
            int source = menuSlotOf(menu, inventory, inventory.findSlotMatchingCraftingIngredient(item, there));
            if (source < 0) {
                return;
            }
            int held = menu.getSlot(source).getItem().getCount();
            clicker.click(source, 0, ContainerInput.PICKUP);
            if (held <= need) {
                clicker.click(cell.index, 0, ContainerInput.PICKUP);
            } else if (need <= held - need) {
                for (int i = 0; i < need; i++) {
                    clicker.click(cell.index, 1, ContainerInput.PICKUP);
                }
            } else {
                for (int i = 0; i < held - need; i++) {
                    clicker.click(source, 1, ContainerInput.PICKUP);
                }
                clicker.click(cell.index, 0, ContainerInput.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) {
                clicker.click(source, 0, ContainerInput.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) {
                // The source slot took something else meanwhile: never leave items on the cursor.
                return;
            }
        }
    }

    /** Empties the grid into the inventory; false when something stays, the inventory being full. */
    private static boolean clearGrid(AbstractCraftingMenu menu, Clicker clicker) {
        boolean clear = true;
        for (Slot slot : menu.getInputGridSlots()) {
            if (!slot.getItem().isEmpty()) {
                clicker.click(slot.index, 0, ContainerInput.QUICK_MOVE);
                clear &= slot.getItem().isEmpty();
            }
        }
        return clear;
    }

    /** The menu slot that shows the inventory's slot {@code index}, or -1. */
    private static int menuSlotOf(AbstractCraftingMenu menu, Inventory inventory, int index) {
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
}
