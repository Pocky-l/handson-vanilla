package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;

/**
 * Whether the smithing table takes a stack, from what the server tells every client of its recipes (since Minecraft 1.21.2 the sets
 * of templates, gear and materials they take, as the vanilla smithing menu reads them). Stacks it takes nothing of stay off the rows.
 */
final class SmithingUse {
    private SmithingUse() {
    }

    /** Nothing is kept between tables any more: the sets are the level's own. Kept for the callers' order of things. */
    static void refresh() {
    }

    static boolean usable(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        if (stack.isEmpty() || level == null) {
            return false;
        }
        RecipeAccess recipes = level.recipeAccess();
        return recipes.propertySet(RecipePropertySet.SMITHING_TEMPLATE).test(stack)
                || recipes.propertySet(RecipePropertySet.SMITHING_BASE).test(stack)
                || recipes.propertySet(RecipePropertySet.SMITHING_ADDITION).test(stack);
    }
}
