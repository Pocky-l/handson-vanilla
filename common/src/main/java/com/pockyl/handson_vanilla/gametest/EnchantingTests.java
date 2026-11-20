package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The enchanting table station's clicks on a server: the held sword and the lapis shift-clicked onto the table (what the station
 * sends on opening), a second item that finds the table taken, and a click on an offer (the menu's button) that is refused without
 * levels and enchants with them, taking one level's worth of lapis and changing the seed the offers come from.
 */
public final class EnchantingTests {
    private static final int ITEM = 0;
    private static final int LAPIS = 1;
    private static final int HOTBAR = 29;

    private EnchantingTests() {
    }

    public static void enchantingTableEnchants(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
        Player player = helper.makeMockPlayer();
        EnchantmentMenu menu = new EnchantmentMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.IRON_SWORD));
        inventory.setItem(1, new ItemStack(Items.LAPIS_LAZULI, 3));
        inventory.setItem(2, new ItemStack(Items.IRON_SWORD));
        menu.clicked(HOTBAR, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(ITEM).getItem().is(Items.IRON_SWORD), "the sword is on the table");
        helper.assertTrue(menu.getSlot(LAPIS).getItem().is(Items.LAPIS_LAZULI) && menu.getGoldCount() == 3, "the lapis is on the table");
        menu.clicked(HOTBAR + 2, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(inventory.getItem(2).is(Items.IRON_SWORD), "a second sword stays in the inventory");
        helper.assertTrue(menu.costs[0] > 0, "the table offers an enchantment");
        int seed = menu.getEnchantmentSeed();
        helper.assertTrue(!player.getAbilities().instabuild && player.experienceLevel == 0, "the player has no levels");
        helper.assertTrue(!menu.clickMenuButton(player, 0), "without levels the offer is refused");
        player.giveExperienceLevels(30);
        helper.assertTrue(menu.clickMenuButton(player, 0), "with levels the offer is taken");
        helper.assertTrue(menu.getSlot(ITEM).getItem().isEnchanted(), "the sword is enchanted");
        helper.assertTrue(menu.getGoldCount() == 2, "one lapis is used");
        helper.assertTrue(player.experienceLevel == 29, "one level is paid");
        helper.assertTrue(menu.getEnchantmentSeed() != seed, "the offers come from a new seed");
        helper.succeed();
    }
}
