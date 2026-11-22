package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;

import com.pockyl.handson_vanilla.RecipeLaying;

/**
 * The book's laying of a recipe the vanilla book's packet cannot lay (not in the player's book, Not Enough Recipe Book's empty
 * book): plain clicks on a server's crafting menu, as the vanilla book's placing would lay it. The menu has no block behind it, so
 * it makes no result: the grid is checked against the recipe.
 */
public final class CraftingLayTests {
    /** The crafting menu's first grid slot, after the result. */
    private static final int FIRST_CELL = 1;
    private static final int CELLS = 9;

    private CraftingLayTests() {
    }

    /** One set, one more, as many as fit (each way of moving items: whole stacks, one by one, the rest put back), then no more. */
    public static void craftingLaysByClicks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingMenu menu = menu(player);
        Inventory inventory = player.getInventory();
        RecipeHolder<CraftingRecipe> torch = recipe(helper, "torch");
        inventory.setItem(0, new ItemStack(Items.COAL, 40));
        inventory.setItem(1, new ItemStack(Items.STICK, 64));
        // Something else lies in the grid already: it goes back to the inventory.
        menu.getSlot(FIRST_CELL + 4).set(new ItemStack(Items.OAK_PLANKS, 3));
        RecipeLaying.Clicker clicker = (slot, button, type) -> menu.clicked(slot, button, type, player);

        helper.assertTrue(RecipeLaying.lay(menu, inventory, torch, false, clicker) == RecipeLaying.Outcome.LAID, "one set laid");
        helper.assertTrue(matches(menu, torch, helper), "the grid makes torches");
        helper.assertTrue(inGrid(menu, Items.COAL) == 1 && inGrid(menu, Items.STICK) == 1, "one coal, one stick");
        helper.assertTrue(inGrid(menu, Items.OAK_PLANKS) == 0 && count(inventory, Items.OAK_PLANKS) == 3, "the planks went back");

        helper.assertTrue(RecipeLaying.lay(menu, inventory, torch, false, clicker) == RecipeLaying.Outcome.LAID, "one more laid");
        helper.assertTrue(inGrid(menu, Items.COAL) == 2 && inGrid(menu, Items.STICK) == 2, "two of each");

        helper.assertTrue(RecipeLaying.lay(menu, inventory, torch, true, clicker) == RecipeLaying.Outcome.LAID, "all laid");
        helper.assertTrue(inGrid(menu, Items.COAL) == 40 && inGrid(menu, Items.STICK) == 40,
                "as many as the coal gives: " + inGrid(menu, Items.COAL) + " coal, " + inGrid(menu, Items.STICK) + " sticks");
        helper.assertTrue(count(inventory, Items.COAL) == 0 && count(inventory, Items.STICK) == 24, "the rest of the sticks stayed");
        helper.assertTrue(menu.getCarried().isEmpty(), "nothing left on the cursor");
        helper.assertTrue(matches(menu, torch, helper), "the grid still makes torches");

        helper.assertTrue(RecipeLaying.lay(menu, inventory, torch, true, clicker) == RecipeLaying.Outcome.UNCHANGED, "no more fit");
        helper.succeed();
    }

    /** Planks taken from two stacks for a chest; too few for another, the grid empties for the ghost; never over a held stack. */
    public static void craftingLayMissing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingMenu menu = menu(player);
        Inventory inventory = player.getInventory();
        RecipeHolder<CraftingRecipe> chest = recipe(helper, "chest");
        RecipeLaying.Clicker clicker = (slot, button, type) -> menu.clicked(slot, button, type, player);
        inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
        inventory.setItem(5, new ItemStack(Items.OAK_PLANKS, 8));

        helper.assertTrue(RecipeLaying.lay(menu, inventory, chest, true, clicker) == RecipeLaying.Outcome.LAID, "chests laid");
        helper.assertTrue(matches(menu, chest, helper) && inGrid(menu, Items.OAK_PLANKS) == 16, "two sets: " + inGrid(menu,
                Items.OAK_PLANKS));
        helper.assertTrue(count(inventory, Items.OAK_PLANKS) == 4, "four planks left");

        for (int cell = 0; cell < CELLS; cell++) {
            menu.getSlot(FIRST_CELL + cell).set(ItemStack.EMPTY);
        }
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
        menu.getSlot(FIRST_CELL).set(new ItemStack(Items.STICK));
        menu.setCarried(new ItemStack(Items.DIRT));
        helper.assertTrue(RecipeLaying.lay(menu, inventory, chest, false, clicker) == RecipeLaying.Outcome.UNCHANGED,
                "nothing done with a stack held");
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(RecipeLaying.lay(menu, inventory, chest, false, clicker) == RecipeLaying.Outcome.MISSING, "too few planks");
        helper.assertTrue(inGrid(menu, Items.STICK) == 0 && count(inventory, Items.STICK) == 1, "the grid emptied");
        helper.assertTrue(count(inventory, Items.OAK_PLANKS) == 5, "the planks stay in the inventory");
        helper.succeed();
    }

    private static CraftingMenu menu(Player player) {
        // No block behind it: a menu at a block would make its result for a server player only.
        CraftingMenu menu = new CraftingMenu(7, player.getInventory(), ContainerLevelAccess.NULL);
        player.containerMenu = menu;
        return menu;
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, String name) {
        RecipeHolder<?> recipe = helper.getLevel().recipeAccess()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(name))).orElse(null);
        if (recipe == null || !(recipe.value() instanceof CraftingRecipe)) {
            throw new IllegalStateException("no crafting recipe minecraft:" + name);
        }
        return (RecipeHolder<CraftingRecipe>) recipe;
    }

    /** Whether the grid makes what the recipe makes, as the server's placing asks it. */
    private static boolean matches(CraftingMenu menu, RecipeHolder<CraftingRecipe> recipe, GameTestHelper helper) {
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (int cell = 0; cell < CELLS; cell++) {
            items.add(menu.getSlot(FIRST_CELL + cell).getItem());
        }
        return recipe.value().matches(CraftingInput.of(3, 3, items), helper.getLevel());
    }

    private static int inGrid(CraftingMenu menu, Item item) {
        int count = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            ItemStack stack = menu.getSlot(FIRST_CELL + cell).getItem();
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                count += inventory.getItem(i).getCount();
            }
        }
        return count;
    }
}
