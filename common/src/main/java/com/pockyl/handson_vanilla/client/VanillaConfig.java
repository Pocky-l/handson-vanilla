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
            "recipe_book: the recipe book beside the crafting table. Off, there is none.",
            "sound_volume: the stations' own sounds, from 0 (silent) to 1.");

    private static boolean craftingTable = true;
    private static boolean recipeBook = true;
    private static boolean furnace = true;
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
        recipeBook = Boolean.parseBoolean(values.getProperty("recipe_book", Boolean.toString(recipeBook)).trim());
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
            writer.write("recipe_book=" + recipeBook + "\n");
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

    public static boolean recipeBook() {
        return recipeBook;
    }

    /** The game's config folder, for files the client writes itself (the book's tabs). */
    static Path configDir() {
        return configDir;
    }

    public static float soundVolume() {
        return soundVolume;
    }
}
