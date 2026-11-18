package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a furnace can do with a stack, from the client's own recipes of its kind (smelting, smoking) and the fuel list: cook it (and
 * into what) or burn it. Stacks that can do neither stay out of the rows over the hotbar.
 */
final class FurnaceUse {
    private static final Map<RecipeType<?>, Map<Item, ItemStack>> RESULTS = new HashMap<>();
    private static Level readFor;

    private FurnaceUse() {
    }

    /** Forgets what was read, once each time a furnace opens, as a server may send other recipes. */
    static void refresh() {
        RESULTS.clear();
        readFor = Minecraft.getInstance().level;
    }

    static boolean usable(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        return !stack.isEmpty() && (cooks(type, stack) || burns(stack));
    }

    static boolean burns(ItemStack stack) {
        return AbstractFurnaceBlockEntity.isFuel(stack);
    }

    static boolean cooks(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        return !result(type, stack).isEmpty();
    }

    /** What cooking one of {@code stack} gives in a furnace of this recipe type; empty when it does not cook there. */
    static ItemStack result(RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        Level level = Minecraft.getInstance().level;
        if (stack.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        if (level != readFor) {
            refresh();
        }
        return RESULTS.computeIfAbsent(type, any -> new HashMap<>()).computeIfAbsent(stack.getItem(), item -> {
            Optional<? extends AbstractCookingRecipe> recipe = level.getRecipeManager()
                    .getRecipeFor(type, new SingleRecipeInput(new ItemStack(item)), level).map(RecipeHolder::value);
            return recipe.map(found -> found.getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
        });
    }
}
