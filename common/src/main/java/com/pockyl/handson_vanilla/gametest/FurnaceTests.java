package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import com.pockyl.handson_vanilla.client.FurnaceKind;

/**
 * The smoker and blast furnace stations on a server: the built-in pack serves each furnace's files by name, and the one shift-click
 * that puts what the hand held on opening into the furnace routes as the station expects (what cooks there to the top, fuel to the
 * fire, what cooks only elsewhere into neither).
 */
public final class FurnaceTests {
    /** A furnace menu's slots: what cooks, the fuel, the result; then the inventory, the hotbar last. */
    private static final int INPUT = 0;
    private static final int FUEL = 1;
    private static final int HOTBAR = 30;

    private FurnaceTests() {
    }

    /** Each furnace's block state and models are served by name; nothing else is. */
    public static void furnaceFilesServed(GameTestHelper helper) {
        helper.assertTrue(FurnaceKind.served("blockstates/smoker.json"), "the smoker's block state");
        helper.assertTrue(FurnaceKind.served("models/block/smoker_on.json"), "the lit smoker's model");
        helper.assertTrue(FurnaceKind.served("models/block/furnace.json"), "the furnace's model");
        helper.assertTrue(FurnaceKind.served("blockstates/blast_furnace.json"), "the blast furnace's block state");
        helper.assertTrue(FurnaceKind.served("models/block/blast_furnace_on.json"), "the lit blast furnace's model");
        helper.assertFalse(FurnaceKind.served("models/block/smoker_side.json"), "nothing else named after the smoker");
        helper.assertFalse(FurnaceKind.served("models/block/blast_furnace_front.json"), "nor after the blast furnace");
        helper.assertTrue(FurnaceKind.SMOKER.block() == Blocks.SMOKER, "the smoker station is on the smoker");
        helper.assertTrue(FurnaceKind.BLAST_FURNACE.block() == Blocks.BLAST_FURNACE, "the blast furnace station on the blast furnace");
        helper.succeed();
    }

    /** At the smoker: food to the input, coal to the fuel, raw iron stays out of both. */
    public static void smokerShiftClicksRoute(GameTestHelper helper) {
        routes(helper, Blocks.SMOKER, Items.BEEF, Items.RAW_IRON);
    }

    /** At the blast furnace: raw iron to the input, coal to the fuel, beef stays out of both. */
    public static void blastFurnaceShiftClicksRoute(GameTestHelper helper) {
        routes(helper, Blocks.BLAST_FURNACE, Items.RAW_IRON, Items.BEEF);
    }

    /** Shift-clicks of three hotbar stacks: one that cooks there, coal, and one that cooks only in another furnace. */
    private static void routes(GameTestHelper helper, Block block, Item cooks, Item elsewhere) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        AbstractContainerMenu menu = furnace.createMenu(9, player.getInventory(), player);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(cooks, 5));
        inventory.setItem(1, new ItemStack(Items.COAL, 3));
        inventory.setItem(2, new ItemStack(elsewhere, 4));
        menu.clicked(HOTBAR, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 2, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(INPUT).getItem().is(cooks) && menu.getSlot(INPUT).getItem().getCount() == 5,
                "what cooks here is in the input");
        helper.assertTrue(menu.getSlot(FUEL).getItem().is(Items.COAL) && menu.getSlot(FUEL).getItem().getCount() == 3,
                "the coal is in the fire");
        int left = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(elsewhere)) {
                left += inventory.getItem(i).getCount();
            }
        }
        helper.assertTrue(left == 4, "what cooks only elsewhere stays in the inventory");
        helper.succeed();
    }
}
