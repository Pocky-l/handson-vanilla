package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;

import java.util.List;

/**
 * The loom station's clicks on a server: banner and dye shift-clicked onto the loom, a pattern picked by the menu's button, the
 * woven banner taken and laid back on the loom (the click on the result, then on the banner's place) ready for the next layer, a
 * second layer; then a stack of banners woven at once by the shift-click on the result.
 */
public final class LoomTests {
    private static final int BANNER = 0;
    private static final int DYE = 1;
    private static final int RESULT = 3;
    private static final int HOTBAR = 31;

    private LoomTests() {
    }

    public static void loomWeavesAndLaysBack(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.LOOM.defaultBlockState());
        Player player = helper.makeMockPlayer();
        LoomMenu menu = new LoomMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.WHITE_BANNER));
        inventory.setItem(1, new ItemStack(Items.RED_DYE, 2));
        menu.clicked(HOTBAR, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(BANNER).getItem().is(Items.WHITE_BANNER) && menu.getSlot(DYE).getItem().getCount() == 2,
                "banner and dye lie on the loom");
        int stripe = indexOf(menu.getSelectablePatterns(), BannerPatterns.STRIPE_BOTTOM);
        helper.assertTrue(stripe >= 0, "the loom offers the bottom stripe");
        helper.assertTrue(menu.clickMenuButton(player, stripe), "the pattern is picked");
        helper.assertTrue(BannerBlockEntity.getPatternCount(menu.getSlot(RESULT).getItem()) == 1, "the result bears one layer");
        menu.clicked(RESULT, 0, ClickType.PICKUP, player);
        menu.clicked(BANNER, 0, ClickType.PICKUP, player);
        helper.assertTrue(BannerBlockEntity.getPatternCount(menu.getSlot(BANNER).getItem()) == 1, "the woven banner lies on the loom");
        helper.assertTrue(menu.getCarried().isEmpty(), "nothing on the cursor");
        helper.assertTrue(menu.getSlot(DYE).getItem().getCount() == 1, "one dye is used");
        helper.assertTrue(menu.getSelectedBannerPatternIndex() == -1, "the selection is cleared");
        int top = indexOf(menu.getSelectablePatterns(), BannerPatterns.STRIPE_TOP);
        menu.clickMenuButton(player, top);
        helper.assertTrue(BannerBlockEntity.getPatternCount(menu.getSlot(RESULT).getItem()) == 2, "a second layer");

        Player other = helper.makeMockPlayer();
        LoomMenu stack = new LoomMenu(10, other.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        other.containerMenu = stack;
        other.getInventory().setItem(0, new ItemStack(Items.WHITE_BANNER, 3));
        other.getInventory().setItem(1, new ItemStack(Items.BLUE_DYE, 3));
        stack.clicked(HOTBAR, 0, ClickType.QUICK_MOVE, other);
        stack.clicked(HOTBAR + 1, 0, ClickType.QUICK_MOVE, other);
        stack.clickMenuButton(other, indexOf(stack.getSelectablePatterns(), BannerPatterns.STRIPE_BOTTOM));
        stack.clicked(RESULT, 0, ClickType.QUICK_MOVE, other);
        int woven = 0;
        for (int i = 0; i < other.getInventory().getContainerSize(); i++) {
            ItemStack there = other.getInventory().getItem(i);
            if (there.is(Items.WHITE_BANNER) && BannerBlockEntity.getPatternCount(there) == 1) {
                woven += there.getCount();
            }
        }
        helper.assertTrue(woven == 3, "three woven banners in the inventory, found " + woven);
        helper.succeed();
    }

    private static int indexOf(List<Holder<BannerPattern>> list, ResourceKey<BannerPattern> key) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).is(key)) {
                return i;
            }
        }
        return -1;
    }
}
