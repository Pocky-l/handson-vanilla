package com.pockyl.handson_vanilla;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import com.mojang.datafixers.util.Either;

import java.util.List;
import java.util.Optional;

/**
 * What an ingredient said of itself before Minecraft 1.21.2, which the stations still ask. Since then an ingredient is never empty
 * (a shaped recipe's gap is no ingredient at all).
 */
public final class RecipeParts {
    /**
     * An ingredient that takes nothing, where {@code Ingredient.EMPTY} stood: a gap. It is one of a tag of no items, so
     * {@link Ingredient#isEmpty()} is true for it, as it was for {@code Ingredient.EMPTY}; a named tag left unbound would throw when
     * its items are read.
     */
    public static final Ingredient NOTHING = emptyTag(TagKey.create(Registries.ITEM, HandsOnVanilla.id("nothing")));

    private RecipeParts() {
    }

    /** A stack of each item {@code ingredient} takes, as {@code Ingredient.getItems()} gave them; none for {@link #NOTHING}. */
    public static ItemStack[] items(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
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
