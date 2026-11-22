package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashSet;
import java.util.Set;

/**
 * Whether a stack can do anything in a crafting grid: an ingredient of a crafting recipe the client knows (those in the player's
 * recipe book since Minecraft 1.21.2; every one in singleplayer), or something the
 * special recipes take (a tool to repair, leather to dye (the items a cauldron washes the dye off), a map or a book to copy, a firework, a banner, a shield, a shulker
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
        for (RecipeDisplayEntry entry : ClientRecipes.every()) {
            for (Ingredient ingredient : entry.craftingRequirements().orElse(java.util.List.of())) {
                ingredient.items().forEach(item -> INGREDIENTS.add(item.value()));
            }
        }
    }

    static boolean usable(ItemStack stack) {
        if (INGREDIENTS.isEmpty()) {
            refresh();
        }
        Item item = stack.getItem();
        return INGREDIENTS.contains(item) || SPECIAL.contains(item) || stack.isDamageableItem() || stack.is(ItemTags.CAULDRON_CAN_REMOVE_DYE)
                || item instanceof BannerItem || item instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
}
