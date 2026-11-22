package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

/** FastWorkbench's result message, given to a crafting menu that is open without a screen (a station's). */
public final class FastBenchResult {
    private static Field recipeId;
    private static Field output;

    private FastBenchResult() {
    }

    /** On the network thread: what the message says, put into the station's result slot on the game thread. */
    public static void arrived(Object message) {
        Identifier id;
        ItemStack stack;
        try {
            if (recipeId == null) {
                recipeId = message.getClass().getDeclaredField("recipeId");
                recipeId.setAccessible(true);
                output = message.getClass().getDeclaredField("output");
                output.setAccessible(true);
            }
            id = (Identifier) recipeId.get(message);
            stack = (ItemStack) output.get(message);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return;
        }
        ItemStack result = stack == null ? ItemStack.EMPTY : stack.copy();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            var player = minecraft.player;
            if (player == null || !(player.containerMenu instanceof CraftingMenu menu) || minecraft.screen instanceof AbstractContainerScreen<?>) {
                return;
            }
            // Into the result container itself, recipe first, as FastWorkbench's own handler does: its result slot is its own and
            // does not take a plain set. Its shift-craft reads the recipe from here to guess what moves before the server says so.
            if (menu.getSlot(0).container instanceof ResultContainer container && minecraft.level != null) {
                container.setRecipeUsed(id == null ? null : minecraft.level.getRecipeManager().byKey(id).orElse(null));
                container.setItem(0, result);
            }
        });
    }
}
