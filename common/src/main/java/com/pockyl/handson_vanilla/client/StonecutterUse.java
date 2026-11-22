package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientRecipeContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Whether the stonecutter cuts a stack into anything, from the cutting recipes the server sends every client; stacks it does not
 * cut stay off the rows.
 */
final class StonecutterUse {
    private StonecutterUse() {
    }

    /** Nothing is kept since Minecraft 1.21.2: the server's recipes are asked each time. */
    static void refresh() {
    }

    static boolean usable(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        return !stack.isEmpty() && level != null && level.recipeAccess() instanceof ClientRecipeContainer recipes
                && recipes.stonecutterRecipes().acceptsInput(stack);
    }
}
