package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The crafting recipes the client can know of. Since Minecraft 1.21.2 a server sends a client no recipes, only the entries of the
 * player's recipe book ({@link RecipeDisplayEntry}: how a recipe looks, what it needs, an id the server lays it by). In singleplayer
 * the game's own server is at hand: there every recipe can be read, for a book of every recipe
 * ({@link VanillaConfig#allRecipesInBook()}), and laid by clicks when it is not in the player's book.
 */
final class ClientRecipes {
    private ClientRecipes() {
    }

    /** Whether a recipe book category is one of the crafting table's. */
    static boolean isCrafting(RecipeBookCategory category) {
        return category == RecipeBookCategories.CRAFTING_BUILDING_BLOCKS || category == RecipeBookCategories.CRAFTING_REDSTONE
                || category == RecipeBookCategories.CRAFTING_EQUIPMENT || category == RecipeBookCategories.CRAFTING_MISC;
    }

    /** The crafting entries of the player's recipe book, as the vanilla book lists them. */
    static List<RecipeDisplayEntry> known() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        for (RecipeCollection collection : player.getRecipeBook().getCollection(SearchRecipeBookCategory.CRAFTING)) {
            entries.addAll(collection.getRecipes());
        }
        return entries;
    }

    /** The ids of {@link #known()}. */
    static Set<RecipeDisplayId> knownIds() {
        Set<RecipeDisplayId> ids = new HashSet<>();
        for (RecipeDisplayEntry entry : known()) {
            ids.add(entry.id());
        }
        return ids;
    }

    /**
     * Every crafting recipe's entries, in singleplayer (the server's own, with the ids the player's book has for them); on a server,
     * the player's book only, as nothing else is sent.
     */
    static List<RecipeDisplayEntry> every() {
        RecipeManager recipes = serverRecipes();
        if (recipes == null) {
            return known();
        }
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            if (holder.value().getType() == RecipeType.CRAFTING) {
                recipes.listDisplaysForRecipe(holder.id(), entry -> {
                    if (isCrafting(entry.category())) {
                        entries.add(entry);
                    }
                });
            }
        }
        return entries;
    }

    /** The recipe behind an entry, in singleplayer; empty on a server. */
    static Optional<RecipeHolder<?>> recipe(RecipeDisplayId id) {
        RecipeManager recipes = serverRecipes();
        RecipeManager.ServerDisplayInfo info = recipes == null ? null : recipes.getRecipeFromDisplay(id);
        return info == null ? Optional.empty() : Optional.of(info.parent());
    }

    /**
     * The entry of the player's book for a recipe a recipe viewer names: in singleplayer by the server's own list, on a server the
     * entry that shows the recipe as the recipe itself would ({@code Recipe.display()}).
     */
    static Optional<RecipeDisplayId> knownEntry(ResourceKey<Recipe<?>> key, Recipe<?> recipe) {
        Set<RecipeDisplayId> known = knownIds();
        RecipeManager recipes = serverRecipes();
        if (recipes != null) {
            List<RecipeDisplayId> found = new ArrayList<>();
            recipes.listDisplaysForRecipe(key, entry -> found.add(entry.id()));
            return found.stream().filter(known::contains).findFirst();
        }
        List<RecipeDisplay> looks = recipe.display();
        for (RecipeDisplayEntry entry : known()) {
            if (looks.contains(entry.display())) {
                return Optional.of(entry.id());
            }
        }
        return Optional.empty();
    }

    /** The crafting recipes that match a grid, in singleplayer; none on a server. */
    static List<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> craftingMatches(
            net.minecraft.world.item.crafting.CraftingInput input, net.minecraft.world.level.Level level) {
        RecipeManager recipes = serverRecipes();
        List<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> matches = new ArrayList<>();
        if (recipes == null) {
            return matches;
        }
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            if (holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe && recipe.matches(input, level)) {
                matches.add(new RecipeHolder<>(holder.id(), recipe));
            }
        }
        return matches;
    }

    /** The singleplayer server's recipes, or null on a server. */
    private static RecipeManager serverRecipes() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.getRecipeManager();
    }
}
