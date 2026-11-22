package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The grindstone station's clicks on a server: the shift-clicks that set two worn swords before the wheel (one to each place),
 * the grind, a plain click on the result slot that puts the mended sword onto the cursor, and an enchanted sword ground clean.
 */
public final class GrindstoneTests {
    /** The grindstone menu's slots: two inputs, the result; then the inventory, the hotbar last. */
    private static final int INPUT = 0;
    private static final int ADDITIONAL = 1;
    private static final int RESULT = 2;
    private static final int HOTBAR = 30;

    private GrindstoneTests() {
    }

    public static void grindstoneMendsAndCleans(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.GRINDSTONE.defaultBlockState());
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        GrindstoneMenu menu = new GrindstoneMenu(9, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(200);
        inventory.setItem(0, worn.copy());
        inventory.setItem(1, worn.copy());
        menu.clicked(HOTBAR, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(HOTBAR + 1, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(menu.getSlot(INPUT).getItem().is(Items.IRON_SWORD) && menu.getSlot(ADDITIONAL).getItem().is(Items.IRON_SWORD),
                "both swords are before the wheel");
        ItemStack mended = menu.getSlot(RESULT).getItem();
        helper.assertTrue(mended.is(Items.IRON_SWORD) && mended.getDamageValue() < 200, "the result is one mended sword");
        menu.clicked(RESULT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.IRON_SWORD) && menu.getCarried().getDamageValue() < 200, "the mended sword is on the cursor");
        helper.assertTrue(menu.getSlot(INPUT).getItem().isEmpty() && menu.getSlot(ADDITIONAL).getItem().isEmpty(), "both swords are used");
        menu.setCarried(ItemStack.EMPTY);
        // An enchanted sword alone is ground clean.
        ItemStack sharp = new ItemStack(Items.DIAMOND_SWORD);
        sharp.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 3);
        inventory.setItem(2, sharp);
        menu.clicked(HOTBAR + 2, 0, ClickType.QUICK_MOVE, player);
        ItemStack clean = menu.getSlot(RESULT).getItem();
        helper.assertTrue(clean.is(Items.DIAMOND_SWORD) && !clean.isEnchanted(), "the result has no enchantment");
        menu.clicked(RESULT, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(inventory.countItem(Items.DIAMOND_SWORD) == 1 && menu.getSlot(INPUT).getItem().isEmpty()
                && menu.getSlot(ADDITIONAL).getItem().isEmpty(), "shift on the result puts the clean sword into the inventory");
        helper.succeed();
    }
}
