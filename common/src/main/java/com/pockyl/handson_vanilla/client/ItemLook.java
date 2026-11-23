package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** What kind of look an item has, as Minecraft 1.21.1's {@code BakedModel} told it. */
final class ItemLook {
    private ItemLook() {
    }

    /**
     * Whether the item is a thing rather than a flat icon, as {@code BakedModel.isGui3d()} said before Minecraft 1.21.4: a model lit
     * as a block, or a special model (a banner, a shield), a built-in entity model then. Lighting alone calls a banner flat.
     * {@code level} may be null.
     */
    static boolean isGui3d(ItemStack stack, ItemDisplayContext context, Level level) {
        if (stack.isEmpty()) {
            return false;
        }
        // A fresh state each time: a tracking state keeps adding to its identity over updates.
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, 0);
        if (state.usesBlockLight()) {
            return true;
        }
        for (Object element : (List<?>) state.getModelIdentity()) {
            if (element instanceof SpecialModelWrapper<?>) {
                return true;
            }
        }
        return false;
    }
}
