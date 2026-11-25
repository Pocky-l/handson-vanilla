package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
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
 * box, a lingering potion for arrows, an empty map or a book and quill to copy onto, a firework star's shape and fade, a decorated
 * pot's sherds). Stacks that can do nothing stay out of the rows over the hotbar.
 */
final class CraftingUse {
    private static final Set<Item> SPECIAL = Set.of(Items.FILLED_MAP, Items.WRITTEN_BOOK, Items.FIREWORK_STAR, Items.FIREWORK_ROCKET,
            Items.LINGERING_POTION, Items.SHIELD, Items.SUSPICIOUS_STEW, Items.MAP, Items.WRITABLE_BOOK, Items.FIRE_CHARGE,
            Items.SKELETON_SKULL, Items.WITHER_SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.PLAYER_HEAD, Items.CREEPER_HEAD, Items.DRAGON_HEAD,
            Items.PIGLIN_HEAD);

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
        return INGREDIENTS.contains(item) || SPECIAL.contains(item) || stack.is(ItemTags.DECORATED_POT_INGREDIENTS)
                || stack.isDamageableItem() || item instanceof DyeableLeatherItem
                || item instanceof BannerItem || item instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
}
