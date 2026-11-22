package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The cartography table station's clicks on a server: a map and an empty map shift-clicked onto the table make two maps, taken by
 * the click on the result with both pieces used; a map and a glass pane make the map locked, and a locked map with a glass pane
 * makes nothing; a map and paper make it one scale out.
 */
public final class CartographyTests {
    private static final int MAP = 0;
    private static final int EXTRA = 1;
    private static final int RESULT = 2;
    private static final int HOTBAR = 30;

    private CartographyTests() {
    }

    public static void cartographyCopiesLocksAndZooms(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        CartographyTableMenu menu = new CartographyTableMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        ItemStack map = MapItem.create(helper.getLevel(), pos.getX(), pos.getZ(), (byte) 0, true, false);

        // Copy: shift-clicked from the hotbar, each to its place.
        player.getInventory().setItem(0, map.copy());
        player.getInventory().setItem(1, new ItemStack(Items.MAP));
        menu.clicked(HOTBAR, 0, ContainerInput.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(MAP).getItem().is(Items.FILLED_MAP) && menu.getSlot(EXTRA).getItem().is(Items.MAP),
                "map and empty map lie on the table");
        helper.assertTrue(menu.getSlot(RESULT).getItem().getCount() == 2, "the copy makes two maps");
        menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 2, "both maps on the cursor");
        helper.assertTrue(!menu.getSlot(MAP).hasItem() && !menu.getSlot(EXTRA).hasItem(), "both pieces used");
        menu.setCarried(ItemStack.EMPTY);

        // Lock: the taken map's picture is locked; a locked map with a glass pane makes nothing.
        menu.getSlot(MAP).set(map.copy());
        menu.getSlot(EXTRA).set(new ItemStack(Items.GLASS_PANE));
        helper.assertTrue(menu.getSlot(RESULT).hasItem(), "a map and a glass pane make the locked map");
        menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
        ItemStack locked = menu.getCarried();
        MapItemSavedData lockedData = MapItem.getSavedData(locked, helper.getLevel());
        helper.assertTrue(lockedData != null && lockedData.locked, "the taken map is locked");
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(MAP).set(locked.copy());
        menu.getSlot(EXTRA).set(new ItemStack(Items.GLASS_PANE));
        helper.assertTrue(!menu.getSlot(RESULT).hasItem(), "a locked map with a glass pane makes nothing");
        menu.getSlot(MAP).set(ItemStack.EMPTY);
        menu.getSlot(EXTRA).set(ItemStack.EMPTY);

        // Zoom: a map and paper make it one scale out.
        menu.getSlot(MAP).set(map.copy());
        menu.getSlot(EXTRA).set(new ItemStack(Items.PAPER));
        helper.assertTrue(menu.getSlot(RESULT).hasItem(), "a map and paper make the zoomed map");
        menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
        MapItemSavedData zoomed = MapItem.getSavedData(menu.getCarried(), helper.getLevel());
        helper.assertTrue(zoomed != null && zoomed.scale == 1, "the taken map is one scale out");
        helper.succeed();
    }
}
