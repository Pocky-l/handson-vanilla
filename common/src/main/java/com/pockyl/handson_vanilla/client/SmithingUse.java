package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;

/**
 * Whether the smithing table takes a stack: a template, a gear a template upgrades or trims, or a material it takes, from the
 * lists the server sends of what its slots take (as the vanilla menu asks). Stacks it takes nothing of stay off the rows.
 */
final class SmithingUse {
    private SmithingUse() {
    }

    /** Nothing is kept since Minecraft 1.21.2: the server's lists are asked each time. */
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
