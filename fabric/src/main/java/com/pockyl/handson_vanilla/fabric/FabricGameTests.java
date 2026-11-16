package com.pockyl.handson_vanilla.fabric;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_vanilla.gametest.BookTests;

/**
 * The mod is client-side only: this checks that it loads on a Fabric server and stays out of the way there, and runs the book's
 * arithmetic ({@link BookTests}). They run when the game starts with {@code -Dfabric-api.gametest} ({@code :fabric:runGameTest}).
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
}
