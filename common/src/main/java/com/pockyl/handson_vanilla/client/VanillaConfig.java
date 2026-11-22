package com.pockyl.handson_vanilla.client;

import net.minecraft.util.Mth;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The player's switches, read once at startup from {@code config/handson_vanilla.properties} (the same file on every
 * loader). Turned off, a block opens its vanilla screen as before.
 */
public final class VanillaConfig {
    private static final String FILE = HandsOnVanilla.MOD_ID + ".properties";
    private static final String HEADER = String.join("\n",
            "Hands-On Vanilla. Restart the game after a change.",
            "crafting_table: the crafting table is worked at with your hands. Off, it opens its vanilla screen.",
            "furnace: the furnace is worked at with your hands. Off, it opens its vanilla screen.",
            "smoker: the smoker is worked at with your hands. Off, it opens its vanilla screen.",
            "blast_furnace: the blast furnace is worked at with your hands. Off, it opens its vanilla screen.",
            "stonecutter: the stonecutter is worked at with your hands. Off, it opens its vanilla screen.",
            "smithing_table: the smithing table is worked at with your hands. Off, it opens its vanilla screen.",
            "grindstone: the grindstone is worked at with your hands. Off, it opens its vanilla screen.",
            "brewing_stand: the brewing stand is worked at with your hands. Off, it opens its vanilla screen.",
            "anvil: the anvil is worked at with your hands. Off, it opens its vanilla screen.",
            "enchanting_table: the enchanting table is worked at with your hands. Off, it opens its vanilla screen.",
            "lectern: a book on a lectern is read on the lectern. Off, it opens its vanilla screen.",
            "loom: the loom is worked at with your hands. Off, it opens its vanilla screen.",
            "cartography_table: the cartography table is worked at with your hands. Off, it opens its vanilla screen.",
            "recipe_book: the recipe book beside the crafting table. Off, there is none.",
            "book_recipes: which recipes the book shows. unlocked: those the player has unlocked, as the vanilla book. all: every",
            "  recipe (in singleplayer; a server tells the client only the unlocked ones). auto: all when Not Enough Recipe Book is",
            "  installed (it empties the vanilla book), else unlocked.",
            "sound_volume: the stations' own sounds, from 0 (silent) to 1.");

    private static boolean craftingTable = true;
    private static boolean recipeBook = true;
    private static String bookRecipes = "auto";
    /** Whether a mod that empties the vanilla book is installed; looked up once. */
    private static Boolean bookEmptier;
    private static boolean furnace = true;
    private static boolean smoker = true;
    private static boolean blastFurnace = true;
    private static boolean stonecutter = true;
    private static boolean smithingTable = true;
    private static boolean grindstone = true;
    private static boolean brewingStand = true;
    private static boolean anvil = true;
    private static boolean enchantingTable = true;
    private static boolean lectern = true;
    private static boolean loom = true;
    private static boolean cartographyTable = true;
    private static Path configDir = Path.of("config");
    private static float soundVolume = 1.0F;

    private VanillaConfig() {
    }

    static void load(Path dir) {
        configDir = dir;
        Path path = dir.resolve(FILE);
        Properties values = new Properties();
        boolean unread = false;
        if (Files.exists(path)) {
            try {
                values.load(new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
            } catch (IOException | IllegalArgumentException exception) {
                HandsOnVanilla.LOGGER.warn("Could not read {}, using the defaults", path, exception);
                unread = true;
            }
        }
        craftingTable = Boolean.parseBoolean(values.getProperty("crafting_table", Boolean.toString(craftingTable)).trim());
        furnace = Boolean.parseBoolean(values.getProperty("furnace", Boolean.toString(furnace)).trim());
        smoker = Boolean.parseBoolean(values.getProperty("smoker", Boolean.toString(smoker)).trim());
        blastFurnace = Boolean.parseBoolean(values.getProperty("blast_furnace", Boolean.toString(blastFurnace)).trim());
        stonecutter = Boolean.parseBoolean(values.getProperty("stonecutter", Boolean.toString(stonecutter)).trim());
        smithingTable = Boolean.parseBoolean(values.getProperty("smithing_table", Boolean.toString(smithingTable)).trim());
        grindstone = Boolean.parseBoolean(values.getProperty("grindstone", Boolean.toString(grindstone)).trim());
        brewingStand = Boolean.parseBoolean(values.getProperty("brewing_stand", Boolean.toString(brewingStand)).trim());
        anvil = Boolean.parseBoolean(values.getProperty("anvil", Boolean.toString(anvil)).trim());
        enchantingTable = Boolean.parseBoolean(values.getProperty("enchanting_table", Boolean.toString(enchantingTable)).trim());
        lectern = Boolean.parseBoolean(values.getProperty("lectern", Boolean.toString(lectern)).trim());
        loom = Boolean.parseBoolean(values.getProperty("loom", Boolean.toString(loom)).trim());
        cartographyTable = Boolean.parseBoolean(values.getProperty("cartography_table", Boolean.toString(cartographyTable)).trim());
        recipeBook = Boolean.parseBoolean(values.getProperty("recipe_book", Boolean.toString(recipeBook)).trim());
        String recipes = values.getProperty("book_recipes", bookRecipes).trim().toLowerCase(java.util.Locale.ROOT);
        if (recipes.equals("auto") || recipes.equals("unlocked") || recipes.equals("all")) {
            bookRecipes = recipes;
        } else {
            HandsOnVanilla.LOGGER.warn("book_recipes in {} is none of auto, unlocked, all: using auto", path);
            bookRecipes = "auto";
        }
        try {
            soundVolume = Mth.clamp(Float.parseFloat(values.getProperty("sound_volume", Float.toString(soundVolume)).trim()), 0.0F, 1.0F);
        } catch (NumberFormatException exception) {
            HandsOnVanilla.LOGGER.warn("sound_volume in {} is no number, using 1", path);
            soundVolume = 1.0F;
        }
        if (unread) {
            return;
        }
        // Written back with every key, so a player sees what there is to change.
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String line : HEADER.split("\n")) {
                writer.write("# " + line + "\n");
            }
            writer.write("crafting_table=" + craftingTable + "\n");
            writer.write("furnace=" + furnace + "\n");
            writer.write("smoker=" + smoker + "\n");
            writer.write("blast_furnace=" + blastFurnace + "\n");
            writer.write("stonecutter=" + stonecutter + "\n");
            writer.write("smithing_table=" + smithingTable + "\n");
            writer.write("grindstone=" + grindstone + "\n");
            writer.write("brewing_stand=" + brewingStand + "\n");
            writer.write("anvil=" + anvil + "\n");
            writer.write("enchanting_table=" + enchantingTable + "\n");
            writer.write("lectern=" + lectern + "\n");
            writer.write("loom=" + loom + "\n");
            writer.write("cartography_table=" + cartographyTable + "\n");
            writer.write("recipe_book=" + recipeBook + "\n");
            writer.write("book_recipes=" + bookRecipes + "\n");
            writer.write("sound_volume=" + soundVolume + "\n");
        } catch (IOException exception) {
            HandsOnVanilla.LOGGER.warn("Could not write {}", path, exception);
        }
    }

    public static boolean craftingTable() {
        return craftingTable;
    }

    public static boolean furnace() {
        return furnace;
    }

    public static boolean smoker() {
        return smoker;
    }

    public static boolean blastFurnace() {
        return blastFurnace;
    }

    public static boolean stonecutter() {
        return stonecutter;
    }

    public static boolean smithingTable() {
        return smithingTable;
    }

    public static boolean grindstone() {
        return grindstone;
    }

    public static boolean brewingStand() {
        return brewingStand;
    }

    public static boolean anvil() {
        return anvil;
    }

    public static boolean enchantingTable() {
        return enchantingTable;
    }

    public static boolean lectern() {
        return lectern;
    }

    public static boolean loom() {
        return loom;
    }

    public static boolean cartographyTable() {
        return cartographyTable;
    }


    public static boolean recipeBook() {
        return recipeBook;
    }

    /**
     * Whether the book shows every recipe, not only those the player has unlocked: asked for, or {@code auto} with a mod that empties
     * the vanilla book (Not Enough Recipe Book, whose package moved between its versions).
     */
    public static boolean allRecipesInBook() {
        if (bookRecipes.equals("all")) {
            return true;
        }
        if (bookRecipes.equals("unlocked")) {
            return false;
        }
        if (bookEmptier == null) {
            bookEmptier = present("it.hurts.shatterbyte.nerb.common.NERB") || present("it.hurts.octostudios.nerb.common.NERB");
        }
        return bookEmptier;
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, VanillaConfig.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    /** The game's config folder, for files the client writes itself (the book's tabs). */
    static Path configDir() {
        return configDir;
    }

    public static float soundVolume() {
        return soundVolume;
    }
}
