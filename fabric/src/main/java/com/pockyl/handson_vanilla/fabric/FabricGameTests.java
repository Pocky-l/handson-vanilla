package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.gametest.AnvilTests;
import com.pockyl.handson_vanilla.gametest.BookTests;
import com.pockyl.handson_vanilla.gametest.BrewingTests;
import com.pockyl.handson_vanilla.gametest.CartographyTests;
import com.pockyl.handson_vanilla.gametest.CraftingLayTests;
import com.pockyl.handson_vanilla.gametest.EnchantingTests;
import com.pockyl.handson_vanilla.gametest.FurnaceTests;
import com.pockyl.handson_vanilla.gametest.GrindstoneTests;
import com.pockyl.handson_vanilla.gametest.LecternTests;
import com.pockyl.handson_vanilla.gametest.LoomTests;
import com.pockyl.handson_vanilla.gametest.SmithingTests;
import com.pockyl.handson_vanilla.gametest.StonecutterTests;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The mod is client-side only: this checks that it loads on a Fabric server and stays out of the way there, and runs the book's
 * arithmetic and its grid gaps ({@link BookTests}), the book's laying by clicks ({@link CraftingLayTests}), the smoker's and blast
 * furnace's pack files and shift-clicks ({@link FurnaceTests}), the stonecutter's clicks ({@link StonecutterTests}), the smithing
 * table's ({@link SmithingTests}), the grindstone's ({@link GrindstoneTests}), the brewing stand's ({@link BrewingTests}), the
 * anvil's ({@link AnvilTests}), the enchanting table's ({@link EnchantingTests}), the lectern's ({@link LecternTests}),
 * the loom's ({@link LoomTests}) and the cartography table's ({@link CartographyTests}).
 * <p>
 * They run when the game starts with {@code -Dfabric-api.gametest} ({@code :fabric:runGameTest}), and only then are they there: the
 * functions are registered here and the tests themselves come from the built-in data pack {@code resourcepacks/gametests} (one file
 * each, its time limit there), which exists for that run alone. Fabric's own {@code @GameTest} cannot be read outside development
 * on Minecraft 1.21.11: its default rotation names an enum constant by its development name.
 */
public final class FabricGameTests implements ModInitializer {
    /** Fabric API's switch for the game test server. */
    private static final String ENABLED = "fabric-api.gametest";

    @Override
    public void onInitialize() {
        if (System.getProperty(ENABLED) == null) {
            return;
        }
        tests().forEach((name, test) -> Registry.register(BuiltInRegistries.TEST_FUNCTION, HandsOnVanilla.id(name), test));
        FabricLoader.getInstance().getModContainer(HandsOnVanilla.MOD_ID).ifPresent(mod ->
                ResourceLoader.registerBuiltinPack(HandsOnVanilla.id("gametests"), mod, PackActivationType.ALWAYS_ENABLED));
    }

    /** Each test by its name, the name of its file in the data pack. */
    private static Map<String, Consumer<GameTestHelper>> tests() {
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("mod_loads", GameTestHelper::succeed);
        tests.put("book_turns", BookTests::bookTurns);
        tests.put("book_leafing", BookTests::bookLeafing);
        tests.put("book_search", BookTests::bookSearch);
        tests.put("book_gap_reads_empty", BookTests::bookGapReadsEmpty);
        tests.put("crafting_lays_by_clicks", CraftingLayTests::craftingLaysByClicks);
        tests.put("crafting_lay_missing", CraftingLayTests::craftingLayMissing);
        tests.put("furnace_files_served", FurnaceTests::furnaceFilesServed);
        tests.put("smoker_shift_clicks_route", FurnaceTests::smokerShiftClicksRoute);
        tests.put("blast_furnace_shift_clicks_route", FurnaceTests::blastFurnaceShiftClicksRoute);
        tests.put("stonecutter_picks_and_cuts", StonecutterTests::stonecutterPicksAndCuts);
        tests.put("smithing_table_upgrades", SmithingTests::smithingTableUpgrades);
        tests.put("grindstone_mends_and_cleans", GrindstoneTests::grindstoneMendsAndCleans);
        tests.put("brewing_stand_brews", BrewingTests::brewingStandBrews);
        tests.put("anvil_renames_and_keeps_names", AnvilTests::anvilRenamesAndKeepsNames);
        tests.put("enchanting_table_enchants", EnchantingTests::enchantingTableEnchants);
        tests.put("lectern_turns_and_gives_book", LecternTests::lecternTurnsAndGivesBook);
        tests.put("loom_weaves_and_lays_back", LoomTests::loomWeavesAndLaysBack);
        tests.put("cartography_copies_locks_and_zooms", CartographyTests::cartographyCopiesLocksAndZooms);
        return tests;
    }
}
