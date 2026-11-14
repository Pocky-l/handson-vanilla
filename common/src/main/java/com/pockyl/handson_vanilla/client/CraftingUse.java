package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether a stack can do anything in a crafting grid: an ingredient of a crafting recipe the client knows, or something the
 * special recipes take (a tool to repair, leather to dye, a map or a book to copy, a firework, a banner, a shield, a shulker
 * box, a lingering potion for arrows). Stacks that can do nothing stay out of the rows over the hotbar.
 */
final class CraftingUse {
    private static final Set<Item> SPECIAL = Set.of(Items.FILLED_MAP, Items.WRITTEN_BOOK, Items.FIREWORK_STAR, Items.FIREWORK_ROCKET,
            Items.LINGERING_POTION, Items.SHIELD, Items.SUSPICIOUS_STEW);

    private static final Set<Item> INGREDIENTS = new HashSet<>();

    private CraftingUse() {
    }

    /** Reads the client's crafting recipes again; once each time a table opens, as a server may send others. */
    static void refresh() {
        INGREDIENTS.clear();
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Recipe<?> recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack option : ingredient.getItems()) {
                    INGREDIENTS.add(option.getItem());
                }
            }
        }
    }

    static boolean usable(ItemStack stack) {
        if (INGREDIENTS.isEmpty()) {
            refresh();
        }
        Item item = stack.getItem();
        return INGREDIENTS.contains(item) || SPECIAL.contains(item) || stack.isDamageableItem() || item instanceof DyeableLeatherItem
                || item instanceof BannerItem || item instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
}
