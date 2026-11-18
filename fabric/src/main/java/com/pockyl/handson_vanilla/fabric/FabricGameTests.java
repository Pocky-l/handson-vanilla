package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_vanilla.gametest.AnvilTests;
import com.pockyl.handson_vanilla.gametest.BookTests;
import com.pockyl.handson_vanilla.gametest.BrewingTests;
import com.pockyl.handson_vanilla.gametest.FurnaceTests;
import com.pockyl.handson_vanilla.gametest.GrindstoneTests;
import com.pockyl.handson_vanilla.gametest.SmithingTests;
import com.pockyl.handson_vanilla.gametest.StonecutterTests;

/**
 * The mod is client-side only: this checks that it loads on a Fabric server and stays out of the way there, and runs the book's
 * arithmetic ({@link BookTests}) and the smoker's and blast furnace's pack files and
 * shift-clicks ({@link FurnaceTests}), the stonecutter's clicks ({@link StonecutterTests}), the smithing table's
 * ({@link SmithingTests}), the grindstone's ({@link GrindstoneTests}), the brewing stand's ({@link BrewingTests}) and the
 * anvil's ({@link AnvilTests}). They run when the game starts with {@code -Dfabric-api.gametest} ({@code :fabric:runGameTest}).
 */
public final class FabricGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bookTurns(GameTestHelper helper) {
        BookTests.bookTurns(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bookLeafing(GameTestHelper helper) {
        BookTests.bookLeafing(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void bookSearch(GameTestHelper helper) {
        BookTests.bookSearch(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void furnaceFilesServed(GameTestHelper helper) {
        FurnaceTests.furnaceFilesServed(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void smokerShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.smokerShiftClicksRoute(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void blastFurnaceShiftClicksRoute(GameTestHelper helper) {
        FurnaceTests.blastFurnaceShiftClicksRoute(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void stonecutterPicksAndCuts(GameTestHelper helper) {
        StonecutterTests.stonecutterPicksAndCuts(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void smithingTableUpgrades(GameTestHelper helper) {
        SmithingTests.smithingTableUpgrades(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void grindstoneMendsAndCleans(GameTestHelper helper) {
        GrindstoneTests.grindstoneMendsAndCleans(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = BrewingTests.TIMEOUT)
    public void brewingStandBrews(GameTestHelper helper) {
        BrewingTests.brewingStandBrews(helper);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void anvilRenamesAndKeepsNames(GameTestHelper helper) {
        AnvilTests.anvilRenamesAndKeepsNames(helper);
    }
}
