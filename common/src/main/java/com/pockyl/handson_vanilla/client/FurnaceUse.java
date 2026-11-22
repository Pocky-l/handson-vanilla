package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * What a furnace can do with a stack: cook it, by what the server tells every client its kind of furnace takes (the same list the
 * vanilla furnace screen asks), or burn it, by the level's fuel list. Stacks that can do neither stay out of the rows over the hotbar.
 * Since Minecraft 1.21.2 a client knows no recipes of its own, only these lists.
 */
final class FurnaceUse {
    private FurnaceUse() {
    }

    /** Nothing is kept: the lists are the level's own and follow the server. */
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
        return level != null && !stack.isEmpty() && level.recipeAccess().propertySet(inputs(type)).test(stack);
    }

    /** The list of what a furnace of this recipe type takes, as its vanilla menu names it. */
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
