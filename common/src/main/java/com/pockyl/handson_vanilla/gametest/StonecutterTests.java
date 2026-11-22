package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The stonecutter station's clicks on a server: the shift-click that puts the held stone onto the table, the menu button that a
 * click on a piece sends, and the shift-click on the picked piece that cuts all the stone gives.
 */
public final class StonecutterTests {
    /** The stonecutter menu's slots: the stone, the result; then the inventory, the hotbar last. */
    private static final int INPUT = 0;
    private static final int RESULT = 1;
    private static final int HOTBAR = 29;

    private StonecutterTests() {
    }

    public static void stonecutterPicksAndCuts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONECUTTER.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        StonecutterMenu menu = new StonecutterMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.STONE, 4));
        inventory.setItem(1, new ItemStack(Items.DIRT, 2));
        // The held stone goes onto the table; dirt is cut into nothing, so it stays out.
        menu.clicked(HOTBAR, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(INPUT).getItem().is(Items.STONE) && menu.getSlot(INPUT).getItem().getCount() == 4,
                "the stone is on the table");
        helper.assertTrue(count(inventory, Items.DIRT) == 2, "the dirt is not");
        // A click on the slab piece: the menu's button of that recipe, as the vanilla screen's list sends it.
        int slab = -1;
        var recipes = menu.getVisibleRecipes().entries();
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(helper.getLevel());
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).recipe().optionDisplay().resolveForFirstStack(context).is(Items.STONE_SLAB)) {
                slab = i;
            }
        }
        helper.assertTrue(slab >= 0, "stone can be cut into slabs");
        helper.assertTrue(menu.clickMenuButton(player, slab), "the slab is picked");
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.STONE_SLAB) && menu.getSlot(RESULT).getItem().getCount() == 2,
                "two slabs are the result");
        // Shift on the picked piece cuts all the stone gives.
        menu.clicked(RESULT, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(count(inventory, Items.STONE_SLAB) == 8, "eight slabs from four stone: " + count(inventory, Items.STONE_SLAB));
        helper.assertTrue(menu.getSlot(INPUT).getItem().isEmpty(), "no stone left on the table");
        helper.succeed();
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                count += inventory.getItem(i).getCount();
            }
        }
        return count;
    }
}
