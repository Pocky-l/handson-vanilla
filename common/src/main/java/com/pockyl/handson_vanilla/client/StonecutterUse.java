package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Whether the stonecutter cuts a stack into anything, from the stonecutter recipes the server tells every client of; stacks it does
 * not cut stay off the rows.
 */
final class StonecutterUse {
    private StonecutterUse() {
    }

    /** Nothing is kept between stonecutters any more: the recipes are the level's own. Kept for the callers' order of things. */
    static void refresh() {
    }

    static boolean usable(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        return !stack.isEmpty() && level != null && level.recipeAccess().stonecutterRecipes().acceptsInput(stack);
    }
}
