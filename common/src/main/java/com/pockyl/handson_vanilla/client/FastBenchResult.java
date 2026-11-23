package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** FastWorkbench's result message, given to a crafting menu that is open without a screen (a station's). */
public final class FastBenchResult {
    private static Method output;

    private FastBenchResult() {
    }

    /** What the message says, put into the station's result slot on the game thread. */
    public static void arrived(Object message) {
        ItemStack stack;
        try {
            if (output == null) {
                output = message.getClass().getMethod("output");
            }
            stack = (ItemStack) output.invoke(message);
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
            // Into the result container itself, as FastWorkbench's own handler does: its result slot is its own and does not take
            // a plain set. A client has no recipes since Minecraft 1.21.2, so the message carries the result alone.
            if (menu.getSlot(0).container instanceof ResultContainer container) {
                container.setItem(0, result);
            }
        });
    }
}
