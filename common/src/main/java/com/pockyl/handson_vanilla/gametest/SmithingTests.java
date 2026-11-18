package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The smithing table station's clicks on a server: the shift-clicks that lay the held template, gear and material on the plate
 * (each to its own place, as the station's rows send them), and the hammer's blow, a plain click on the result slot that puts the
 * upgraded gear onto the cursor.
 */
public final class SmithingTests {
    /** The smithing menu's slots: template, base, addition, result; then the inventory, the hotbar last. */
    private static final int TEMPLATE = 0;
    private static final int BASE = 1;
    private static final int ADDITION = 2;
    private static final int RESULT = 3;
    private static final int HOTBAR = 31;

    private SmithingTests() {
    }

    public static void smithingTableUpgrades(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.SMITHING_TABLE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        SmithingMenu menu = new SmithingMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        // In an order the plate does not have: material, gear, template, and dirt the table takes nothing of.
        inventory.setItem(0, new ItemStack(Items.NETHERITE_INGOT, 2));
        inventory.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(2, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
        inventory.setItem(3, new ItemStack(Items.DIRT));
        for (int i = 0; i < 4; i++) {
            menu.clicked(HOTBAR + i, 0, ClickType.QUICK_MOVE, player);
        }
        helper.assertTrue(menu.getSlot(TEMPLATE).getItem().is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), "the template lies in its place");
        helper.assertTrue(menu.getSlot(BASE).getItem().is(Items.DIAMOND_SWORD), "the sword lies in its place");
        helper.assertTrue(menu.getSlot(ADDITION).getItem().is(Items.NETHERITE_INGOT), "the ingot lies in its place");
        helper.assertTrue(inventory.countItem(Items.DIRT) == 1, "the dirt stays in the inventory (moved up out of the hotbar, as vanilla does)");
        helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.NETHERITE_SWORD), "a netherite sword is the result");
        // The blow: a plain click on the result, which goes onto the cursor and uses one of each.
        menu.clicked(RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.NETHERITE_SWORD), "the netherite sword is on the cursor");
        helper.assertTrue(menu.getSlot(BASE).getItem().isEmpty(), "the diamond sword is used");
        helper.assertTrue(menu.getSlot(TEMPLATE).getItem().getCount() == 1 && menu.getSlot(ADDITION).getItem().getCount() == 1,
                "one template and one ingot are used");
        helper.assertTrue(menu.getSlot(RESULT).getItem().isEmpty(), "nothing more to make");
        helper.succeed();
    }
}
