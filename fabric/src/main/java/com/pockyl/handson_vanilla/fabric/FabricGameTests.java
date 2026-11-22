package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

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

/**
 * The mod is client-side only: this checks that it loads on a Fabric server and stays out of the way there, and runs the book's
 * arithmetic ({@link BookTests}), the book's laying by clicks ({@link CraftingLayTests}) and the smoker's and blast furnace's pack files and
 * shift-clicks ({@link FurnaceTests}), the stonecutter's clicks ({@link StonecutterTests}), the smithing table's
 * ({@link SmithingTests}), the grindstone's ({@link GrindstoneTests}), the brewing stand's ({@link BrewingTests}), the
 * anvil's ({@link AnvilTests}), the enchanting table's ({@link EnchantingTests}), the lectern's ({@link LecternTests}),
 * the loom's ({@link LoomTests}) and the cartography table's ({@link CartographyTests}).
 * They run when the game starts with {@code -Dfabric-api.gametest} ({@code :fabric:runGameTest}).
 */
public final class FabricGameTests {
    @GameTest
    public void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest
    public void bookTurns(GameTestHelper helper) {
        BookTests.bookTurns(helper);
    }

    @GameTest
    public void bookLeafing(GameTestHelper helper) {
        BookTests.bookLeafing(helper);
    }

    @GameTest
    public void bookSearch(GameTestHelper helper) {
        BookTests.bookSearch(helper);
    }

    @GameTest
    public void craftingLaysByClicks(GameTestHelper helper) {
        CraftingLayTests.craftingLaysByClicks(helper);
    }

    @GameTest
    public void craftingLayMissing(GameTestHelper helper) {
        CraftingLayTests.craftingLayMissing(helper);
    }

    @GameTest
    public void furnaceFilesServed(GameTestHelper helper) {
        FurnaceTests.furnaceFilesServed(helper);
    }

    @GameTest
    public void smokerShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.smokerShiftClicksRoute(helper);
    }

    @GameTest
    public void blastFurnaceShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.blastFurnaceShiftClicksRoute(helper);
    }

    @GameTest
    public void stonecutterPicksAndCuts(GameTestHelper helper) {
        StonecutterTests.stonecutterPicksAndCuts(helper);
    }

    @GameTest
    public void smithingTableUpgrades(GameTestHelper helper) {
        SmithingTests.smithingTableUpgrades(helper);
    }

    @GameTest
    public void grindstoneMendsAndCleans(GameTestHelper helper) {
        GrindstoneTests.grindstoneMendsAndCleans(helper);
    }

    @GameTest(maxTicks = BrewingTests.TIMEOUT)
    public void brewingStandBrews(GameTestHelper helper) {
        BrewingTests.brewingStandBrews(helper);
    }

    @GameTest
    public void anvilRenamesAndKeepsNames(GameTestHelper helper) {
        AnvilTests.anvilRenamesAndKeepsNames(helper);
    }

    @GameTest
    public void enchantingTableEnchants(GameTestHelper helper) {
        EnchantingTests.enchantingTableEnchants(helper);
    }

    @GameTest
    public void lecternTurnsAndGivesBook(GameTestHelper helper) {
        LecternTests.lecternTurnsAndGivesBook(helper);
    }

    @GameTest
    public void loomWeavesAndLaysBack(GameTestHelper helper) {
        LoomTests.loomWeavesAndLaysBack(helper);
    }

    @GameTest
    public void cartographyCopiesLocksAndZooms(GameTestHelper helper) {
        CartographyTests.cartographyCopiesLocksAndZooms(helper);
    }

}
