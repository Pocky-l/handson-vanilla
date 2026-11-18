package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/** Whether the stonecutter cuts a stack into anything, from the client's own recipes; stacks it does not cut stay off the rows. */
final class StonecutterUse {
    private static final Map<Item, Boolean> CUTS = new HashMap<>();
    private static Level readFor;

    private StonecutterUse() {
    }

    /** Forgets what was read, once each time a stonecutter opens, as a server may send other recipes. */
    static void refresh() {
        CUTS.clear();
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
        return CUTS.computeIfAbsent(stack.getItem(), item -> level.getRecipeManager()
                .getRecipeFor(RecipeType.STONECUTTING, new SimpleContainer(new ItemStack(item)), level).isPresent());
    }
}
