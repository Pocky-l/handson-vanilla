package com.pockyl.handson_vanilla.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_vanilla.HandsOnVanilla;

/**
 * The mod is client-side only: this checks that it loads on a server and stays out of the way there, and runs the book's
 * arithmetic ({@link BookTests}), the book's laying by clicks ({@link CraftingLayTests}) and the smoker's and blast furnace's pack files and
 * shift-clicks ({@link FurnaceTests}), the stonecutter's clicks ({@link StonecutterTests}),
 * the smithing table's ({@link SmithingTests}), the grindstone's ({@link GrindstoneTests}),
 * the brewing stand's ({@link BrewingTests}), the anvil's ({@link AnvilTests}), the enchanting table's
 * ({@link EnchantingTests}), the lectern's ({@link LecternTests}), the loom's ({@link LoomTests}), the cartography table's
 * and the cartography table's ({@link CartographyTests}).
 */
@GameTestHolder(HandsOnVanilla.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bookTurns(GameTestHelper helper) {
        BookTests.bookTurns(helper);
    }

    @GameTest(template = "empty")
    public static void bookLeafing(GameTestHelper helper) {
        BookTests.bookLeafing(helper);
    }

    @GameTest(template = "empty")
    public static void bookSearch(GameTestHelper helper) {
        BookTests.bookSearch(helper);
    }

    @GameTest(template = "empty")
    public static void craftingLaysByClicks(GameTestHelper helper) {
        CraftingLayTests.craftingLaysByClicks(helper);
    }

    @GameTest(template = "empty")
    public static void craftingLayMissing(GameTestHelper helper) {
        CraftingLayTests.craftingLayMissing(helper);
    }

    @GameTest(template = "empty")
    public static void furnaceFilesServed(GameTestHelper helper) {
        FurnaceTests.furnaceFilesServed(helper);
    }

    @GameTest(template = "empty")
    public static void everyChoosingStationHasItsPlaces(GameTestHelper helper) {
        ChoiceTests.everyChoosingStationHasItsPlaces(helper);
    }

    @GameTest(template = "empty")
    public static void smokerShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.smokerShiftClicksRoute(helper);
    }

    @GameTest(template = "empty")
    public static void blastFurnaceShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.blastFurnaceShiftClicksRoute(helper);
    }

    @GameTest(template = "empty")
    public static void stonecutterPicksAndCuts(GameTestHelper helper) {
        StonecutterTests.stonecutterPicksAndCuts(helper);
    }

    @GameTest(template = "empty")
    public static void smithingTableUpgrades(GameTestHelper helper) {
        SmithingTests.smithingTableUpgrades(helper);
    }

    @GameTest(template = "empty")
    public static void grindstoneMendsAndCleans(GameTestHelper helper) {
        GrindstoneTests.grindstoneMendsAndCleans(helper);
    }

    @GameTest(template = "empty", timeoutTicks = BrewingTests.TIMEOUT)
    public static void brewingStandBrews(GameTestHelper helper) {
        BrewingTests.brewingStandBrews(helper);
    }

    @GameTest(template = "empty")
    public static void anvilRenamesAndKeepsNames(GameTestHelper helper) {
        AnvilTests.anvilRenamesAndKeepsNames(helper);
    }

    @GameTest(template = "empty")
    public static void enchantingTableEnchants(GameTestHelper helper) {
        EnchantingTests.enchantingTableEnchants(helper);
    }

    @GameTest(template = "empty")
    public static void lecternTurnsAndGivesBook(GameTestHelper helper) {
        LecternTests.lecternTurnsAndGivesBook(helper);
    }

    @GameTest(template = "empty")
    public static void loomWeavesAndLaysBack(GameTestHelper helper) {
        LoomTests.loomWeavesAndLaysBack(helper);
    }

    @GameTest(template = "empty")
    public static void cartographyCopiesLocksAndZooms(GameTestHelper helper) {
        CartographyTests.cartographyCopiesLocksAndZooms(helper);
    }

}
