package com.pockyl.handson_vanilla.client;

import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import com.pockyl.handson_vanilla.RecipeParts;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipes as a client knows them since Minecraft 1.21.2: no recipe objects, only what the server shows of them (a recipe display:
 * its slots, each a list of what may go there, and its result), for the recipes in the player's book. The book's pages and the
 * ghost in the grid are made from these.
 */
final class RecipeDisplays {
    /** A gap in a shaped recipe's grid, where {@code Ingredient.EMPTY} stood: {@link RecipeParts#NOTHING}. */
    static final Ingredient GAP = RecipeParts.NOTHING;

    private static final int GRID = 3;

    private RecipeDisplays() {
    }

    static ContextMap context(Level level) {
        return SlotDisplayContext.fromLevel(level);
    }

    /** A stack of each item {@code ingredient} takes, as {@code Ingredient.getItems()} gave them; none for {@link #GAP}. */
    static ItemStack[] items(Ingredient ingredient) {
        return RecipeParts.items(ingredient);
    }

    /** Whether the crafting table makes it: a shaped or shapeless craft. */
    static boolean isCrafting(RecipeDisplay display) {
        return display instanceof ShapedCraftingRecipeDisplay || display instanceof ShapelessCraftingRecipeDisplay;
    }

    /** What goes in, a shaped recipe's in rows of three as it lies in the grid, its gaps {@link #GAP}. */
    static List<Ingredient> laid(RecipeDisplay display, ContextMap context) {
        List<Ingredient> laid = new ArrayList<>();
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            for (int row = 0; row < shaped.height(); row++) {
                for (int column = 0; column < GRID; column++) {
                    laid.add(column < shaped.width() ? ingredient(shaped.ingredients().get(row * shaped.width() + column), context) : GAP);
                }
            }
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            for (SlotDisplay slot : shapeless.ingredients()) {
                laid.add(ingredient(slot, context));
            }
        }
        return laid;
    }

    /** What may go into a slot, as an ingredient of those items; {@link #GAP} for an empty one. */
    static Ingredient ingredient(SlotDisplay slot, ContextMap context) {
        List<Item> items = slot.resolveForStacks(context).stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem).distinct()
                .toList();
        return items.isEmpty() ? GAP : Ingredient.of(items.stream());
    }

    /** What the recipe makes, one stack of it; empty when it says nothing. */
    static ItemStack result(RecipeDisplay display, ContextMap context) {
        return display.result().resolveForFirstStack(context);
    }
}
