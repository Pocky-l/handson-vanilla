package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether the smithing table takes a stack, by the lists the server tells every client (the vanilla screen asks the same): a
 * template, a gear a template upgrades or trims, or a material it takes. Stacks it takes nothing of stay off the rows.
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
            RecipeAccess recipes = level.recipeAccess();
            return recipes.propertySet(RecipePropertySet.SMITHING_TEMPLATE).test(one)
                    || recipes.propertySet(RecipePropertySet.SMITHING_BASE).test(one)
                    || recipes.propertySet(RecipePropertySet.SMITHING_ADDITION).test(one);
        });
    }
}
