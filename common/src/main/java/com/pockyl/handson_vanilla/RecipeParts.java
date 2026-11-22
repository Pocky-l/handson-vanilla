package com.pockyl.handson_vanilla;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import com.mojang.datafixers.util.Either;

import java.util.List;
import java.util.Optional;

/**
 * What an ingredient said of itself before Minecraft 1.21.2, which the stations still ask. Since then an ingredient is never empty
 * (a shaped recipe's gap is no ingredient at all), and the client knows recipes only as the recipe book shows them: by slot displays.
 */
public final class RecipeParts {
    /**
     * An ingredient that takes nothing, where {@code Ingredient.EMPTY} stood: a gap. It is one of a tag of no items, so
     * {@link Ingredient#isEmpty()} is true for it, as it was for {@code Ingredient.EMPTY}.
     */
    public static final Ingredient NOTHING = emptyTag(TagKey.create(Registries.ITEM, HandsOnVanilla.id("nothing")));

    private RecipeParts() {
    }

    /** A stack of each item {@code ingredient} takes, as {@code Ingredient.getItems()} gave them. */
    public static ItemStack[] items(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }

    /** An ingredient (a gap: {@link #NOTHING}) as it was before 1.21.2, where {@code Optional.empty()} stands for a gap now. */
    public static Ingredient orGap(Optional<Ingredient> ingredient) {
        return ingredient.orElse(NOTHING);
    }

    /**
     * What a slot of a recipe the client knows only by its display takes: the items it shows, as one ingredient; a gap when it shows
     * none.
     */
    public static Ingredient fromDisplay(SlotDisplay display, ContextMap context) {
        List<Holder<Item>> items = display.resolveForStacks(context).stream().filter(stack -> !stack.isEmpty())
                .map(ItemStack::typeHolder).distinct().toList();
        return items.isEmpty() ? NOTHING : Ingredient.of(HolderSet.direct(items));
    }

    /** An ingredient of {@code tag} as a tag that names no items reads: it takes nothing. */
    private static Ingredient emptyTag(TagKey<Item> tag) {
        return Ingredient.of(new HolderSet.ListBacked<Item>() {
            @Override
            protected List<Holder<Item>> contents() {
                return List.of();
            }

            @Override
            public boolean isBound() {
                return true;
            }

            @Override
            public Either<TagKey<Item>, List<Holder<Item>>> unwrap() {
                return Either.left(tag);
            }

            @Override
            public Optional<TagKey<Item>> unwrapKey() {
                return Optional.of(tag);
            }

            @Override
            public boolean contains(Holder<Item> value) {
                return false;
            }
        });
    }
}
