package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * What a furnace can do with a stack, from what the server tells every client of its kind of recipes (since Minecraft 1.21.2 the
 * set of what its recipes take, as the vanilla furnace menu reads it) and the fuel list: cook it or burn it. Stacks that can do
 * neither stay out of the rows over the hotbar.
 */
final class FurnaceUse {
    private FurnaceUse() {
    }

    /** Nothing is kept between furnaces any more: the sets are the level's own. Kept for the callers' order of things. */
    static void refresh() {
    }

    static boolean usable(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        return !stack.isEmpty() && (cooks(type, stack) || burns(stack));
    }

    static boolean burns(ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        return level != null && level.fuelValues().isFuel(stack);
    }

    static boolean cooks(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        return !stack.isEmpty() && level != null && level.recipeAccess().propertySet(inputs(type)).test(stack);
    }

    /** The set of what the furnace of this recipe type takes, as its menu names it. */
    private static ResourceKey<RecipePropertySet> inputs(RecipeType<? extends AbstractCookingRecipe> type) {
        if (type == RecipeType.SMOKING) {
            return RecipePropertySet.SMOKER_INPUT;
        }
        if (type == RecipeType.BLASTING) {
            return RecipePropertySet.BLAST_FURNACE_INPUT;
        }
        return RecipePropertySet.FURNACE_INPUT;
    }
}
