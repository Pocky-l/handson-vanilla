package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.util.List;
import java.util.function.Consumer;

/**
 * The mod is client-side only: this checks that it loads on a NeoForge server and stays out of the way there, and runs the book's
 * arithmetic and its grid gaps ({@link BookTests}), the book's laying by clicks ({@link CraftingLayTests}), the smoker's and blast
 * furnace's pack files and shift-clicks ({@link FurnaceTests}), the stonecutter's clicks ({@link StonecutterTests}), the smithing
 * table's ({@link SmithingTests}), the grindstone's ({@link GrindstoneTests}), the brewing stand's ({@link BrewingTests}), the
 * anvil's ({@link AnvilTests}), the enchanting table's ({@link EnchantingTests}), the lectern's ({@link LecternTests}),
 * the loom's ({@link LoomTests}) and the cartography table's ({@link CartographyTests}). Tests are registry entries since
 * Minecraft 1.21.5: each is a function and a test instance on the empty structure, named in snake case.
 */
public final class NeoForgeGameTests {
    private static final int TIMEOUT = 100;
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            HandsOnVanilla.MOD_ID);

    private record Test(String name, int timeout, Consumer<GameTestHelper> function) {
        Test(String name, Consumer<GameTestHelper> function) {
            this(name, TIMEOUT, function);
        }
    }

    private static final List<Test> TESTS = List.of(
            new Test("mod_loads", GameTestHelper::succeed),
            new Test("book_turns", BookTests::bookTurns),
            new Test("book_leafing", BookTests::bookLeafing),
            new Test("book_search", BookTests::bookSearch),
            new Test("book_gap_reads_empty", BookTests::bookGapReadsEmpty),
            new Test("crafting_lays_by_clicks", CraftingLayTests::craftingLaysByClicks),
            new Test("crafting_lay_missing", CraftingLayTests::craftingLayMissing),
            new Test("furnace_files_served", FurnaceTests::furnaceFilesServed),
            new Test("smoker_shift_clicks_route", FurnaceTests::smokerShiftClicksRoute),
            new Test("blast_furnace_shift_clicks_route", FurnaceTests::blastFurnaceShiftClicksRoute),
            new Test("stonecutter_picks_and_cuts", StonecutterTests::stonecutterPicksAndCuts),
            new Test("smithing_table_upgrades", SmithingTests::smithingTableUpgrades),
            new Test("grindstone_mends_and_cleans", GrindstoneTests::grindstoneMendsAndCleans),
            new Test("brewing_stand_brews", BrewingTests.TIMEOUT, BrewingTests::brewingStandBrews),
            new Test("anvil_renames_and_keeps_names", AnvilTests::anvilRenamesAndKeepsNames),
            new Test("enchanting_table_enchants", EnchantingTests::enchantingTableEnchants),
            new Test("lectern_turns_and_gives_book", LecternTests::lecternTurnsAndGivesBook),
            new Test("loom_weaves_and_lays_back", LoomTests::loomWeavesAndLaysBack),
            new Test("cartography_copies_locks_and_zooms", CartographyTests::cartographyCopiesLocksAndZooms));

    private NeoForgeGameTests() {
    }

    /** Registers the tests when the game test framework is on (the game test server and dev runs that enable it). */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Test test : TESTS) {
            FUNCTIONS.register(test.name(), test::function);
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(NeoForgeGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(HandsOnVanilla.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            Identifier id = HandsOnVanilla.id(test.name());
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, HandsOnVanilla.id("empty"),
                    test.timeout(), 0, true, Rotation.NONE, false, 1, 1, false, 0);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }
}
