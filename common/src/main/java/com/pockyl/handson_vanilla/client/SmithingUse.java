package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether the smithing table takes a stack, from the client's own recipes: a template, a gear a template upgrades or trims, or
 * a material it takes. Stacks it takes nothing of stay off the rows.
 */
final class SmithingUse {
    private static final Map<Item, Boolean> TAKES = new HashMap<>();
    private static Level readFor;

    private SmithingUse() {
    }

    /** Forgets what was read, once each time a smithing table opens, as a server may send other recipes. */
    static void refresh() {
        TAKES.clear();
        readFor = Minecraft.getInstance().level;
    }

    static boolean usable(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        if (stack.isEmpty() || level == null) {
            return false;
        }
        if (level != readFor) {
            refresh();
        }
        return TAKES.computeIfAbsent(stack.getItem(), item -> {
            ItemStack one = new ItemStack(item);
            List<RecipeHolder<SmithingRecipe>> recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING);
            for (RecipeHolder<SmithingRecipe> holder : recipes) {
                SmithingRecipe recipe = holder.value();
                if (recipe.isTemplateIngredient(one) || recipe.isBaseIngredient(one) || recipe.isAdditionIngredient(one)) {
                    return true;
                }
            }
            return false;
        });
    }
}
