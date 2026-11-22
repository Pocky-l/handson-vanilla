package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * What a furnace can do with a stack: cook it (what the server lists as its kind's input, as the vanilla menu asks) or burn it
 * (a fuel). Stacks that can do neither stay out of the rows over the hotbar.
 */
final class FurnaceUse {
    private FurnaceUse() {
    }

    /** Nothing is kept since Minecraft 1.21.2: the server's lists of inputs are asked each time. */
    static void refresh() {
    }

    static boolean usable(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        return !stack.isEmpty() && (cooks(type, stack) || burns(stack));
    }

    /** A fuel, as {@code AbstractFurnaceMenu.isFuel} tells one. */
    static boolean burns(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    /** Whether a furnace of this recipe type cooks the stack, as {@code AbstractFurnaceMenu.canSmelt} asks. */
    static boolean cooks(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        return !stack.isEmpty() && level != null && level.recipeAccess().propertySet(inputs(type)).test(stack);
    }

    private static ResourceKey<RecipePropertySet> inputs(RecipeType<? extends AbstractCookingRecipe> type) {
        if (type == RecipeType.SMOKING) {
            return RecipePropertySet.SMOKER_INPUT;
        }
        return type == RecipeType.BLASTING ? RecipePropertySet.BLAST_FURNACE_INPUT : RecipePropertySet.FURNACE_INPUT;
    }
}
