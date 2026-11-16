package com.pockyl.handson_vanilla.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.pockyl.handson_vanilla.HandsOnVanilla;

/**
 * The mod is client-side only: this checks that it loads on a server and stays out of the way there, and runs the book's
 * arithmetic ({@link BookTests}).
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
}
