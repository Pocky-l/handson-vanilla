package com.pockyl.handson_vanilla.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import com.pockyl.handson_vanilla.BookMath;

/**
 * The recipe book's arithmetic, checked on a server: the mod is client-side only, and what a server does with its clicks is
 * tested in Diegesis (MenuGameTests). Each loader registers these in its own way (Forge {@code ModGameTests}, Fabric
 * {@code FabricGameTests}).
 */
public final class BookTests {
    private BookTests() {
    }

    /** The book's arrows and wheel: kept within the list, round the ends only when asked, nothing to turn with one page. */
    public static void bookTurns(GameTestHelper helper) {
        helper.assertTrue(BookMath.turned(0, -1, 5, false) == 0, "back from the first page stays");
        helper.assertTrue(BookMath.turned(4, 1, 5, false) == 4, "on from the last page stays");
        helper.assertTrue(BookMath.turned(4, 1, 5, true) == 0, "a click on from the last page goes to the first");
        helper.assertTrue(BookMath.turned(0, -1, 5, true) == 4, "a click back from the first page goes to the last");
        helper.assertTrue(BookMath.turned(2, 1, 5, false) == 3, "a turn on");
        helper.assertTrue(BookMath.turned(0, 1, 1, true) == 0, "one page has nothing to turn");
        helper.succeed();
    }

    /** A held arrow: one page at once, a wait, then quicker and quicker. */
    public static void bookLeafing(GameTestHelper helper) {
        helper.assertTrue(BookMath.turnsDue(0.0F) == 1, "one page at once");
        helper.assertTrue(BookMath.turnsDue(0.3F) == 1, "then a wait");
        helper.assertTrue(BookMath.turnsDue(0.36F) == 2, "then it goes on");
        int atOneAndAHalf = BookMath.turnsDue(1.5F);
        helper.assertTrue(atOneAndAHalf >= 9 && atOneAndAHalf <= 12, "about ten pages in a second and a half: " + atOneAndAHalf);
        helper.assertTrue(BookMath.turnsDue(2.5F) - atOneAndAHalf >= 15, "faster after that");
        helper.assertTrue(BookMath.repeatDue(0.36F, 1) && !BookMath.repeatDue(0.36F, 2), "one turn per page due");
        helper.succeed();
    }

    /** The book's search: every word in the name or the id, case aside. */
    public static void bookSearch(GameTestHelper helper) {
        helper.assertTrue(BookMath.matches("oak st", "Oak Stairs", "minecraft:oak_stairs"), "words of the name");
        helper.assertTrue(BookMath.matches("create", "Shaft", "create:shaft"), "the mod of the id");
        helper.assertTrue(BookMath.matches("", "Stick", "minecraft:stick"), "nothing typed finds all");
        helper.assertTrue(!BookMath.matches("birch", "Oak Stairs", "minecraft:oak_stairs"), "another wood is not found");
        helper.assertTrue(BookMath.matches("STICK", "Sticky Piston", "minecraft:sticky_piston"), "case aside");
        helper.succeed();
    }
}
