package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The anvil station's clicks on a server: the held piece shift-clicked onto the anvil, the name the station sends when a piece
 * is laid (the piece's own name, which renames nothing and keeps a custom name), a typed name, and the hammer's blow (a plain
 * click on the result slot) that pays the levels and puts the renamed piece onto the cursor.
 */
public final class AnvilTests {
    private static final int LEFT = 0;
    private static final int RESULT = 2;
    private static final int HOTBAR = 30;

    private AnvilTests() {
    }

    public static void anvilRenamesAndKeepsNames(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
        Player player = helper.makeMockPlayer();
        AnvilMenu menu = new AnvilMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.setHoverName(Component.literal("Old Friend"));
        inventory.setItem(0, named);
        menu.clicked(HOTBAR, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(LEFT).getItem().is(Items.IRON_SWORD), "the sword is on the anvil");
        // What the station sends when a piece is laid, as the vanilla screen: a custom name is sent as it is, so nothing changes.
        menu.setItemName("Old Friend");
        helper.assertTrue(menu.getSlot(RESULT).getItem().isEmpty() && menu.getCost() == 0, "laying a named sword costs nothing");
        // A typed name.
        menu.setItemName("New Friend");
        ItemStack renamed = menu.getSlot(RESULT).getItem();
        helper.assertTrue(renamed.is(Items.IRON_SWORD) && renamed.getHoverName().getString().equals("New Friend"),
                "the result bears the typed name");
        helper.assertTrue(menu.getCost() > 0, "renaming costs levels");
        helper.assertTrue(!menu.getSlot(RESULT).mayPickup(player), "without levels it cannot be taken");
        player.giveExperienceLevels(5);
        menu.clicked(RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getHoverName().getString().equals("New Friend"), "the renamed sword is on the cursor");
        helper.assertTrue(player.experienceLevel < 5, "the levels are paid");
        helper.assertTrue(menu.getSlot(LEFT).getItem().isEmpty(), "the anvil is empty");
        helper.succeed();
    }
}
