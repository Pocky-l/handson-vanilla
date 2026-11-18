package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * The brewing stand station's clicks on a server: the shift-clicks that set water bottles on the pads, the nether wart on the rod
 * and the blaze powder by its foot (each to its own place, as the station's rows and the held stack send them), and the brew that
 * follows, which the station only watches.
 */
public final class BrewingTests {
    /** The brewing stand menu's slots: three bottles, the ingredient, the fuel; then the inventory, the hotbar last. */
    private static final int INGREDIENT = 3;
    private static final int FUEL = 4;
    private static final int HOTBAR = 32;
    /** A brew's ticks, and some to spare. */
    public static final int TIMEOUT = 500;

    private BrewingTests() {
    }

    public static void brewingStandBrews(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.BREWING_STAND.defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) {
            helper.fail("no brewing stand");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        AbstractContainerMenu menu = stand.createMenu(9, player.getInventory(), player);
        player.containerMenu = menu;
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.BLAZE_POWDER));
        inventory.setItem(1, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        inventory.setItem(2, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        inventory.setItem(3, new ItemStack(Items.NETHER_WART));
        for (int i = 0; i < 4; i++) {
            menu.clicked(HOTBAR + i, 0, ClickType.QUICK_MOVE, player);
        }
        helper.assertTrue(menu.getSlot(FUEL).getItem().is(Items.BLAZE_POWDER), "the blaze powder lies by the foot");
        helper.assertTrue(menu.getSlot(INGREDIENT).getItem().is(Items.NETHER_WART), "the nether wart is on the rod");
        int bottles = 0;
        for (int slot = 0; slot < 3; slot++) {
            bottles += menu.getSlot(slot).getItem().is(Items.POTION) ? 1 : 0;
        }
        helper.assertTrue(bottles == 2, "both water bottles are on pads: " + bottles);
        helper.succeedWhen(() -> {
            int awkward = 0;
            for (int slot = 0; slot < 3; slot++) {
                awkward += stand.getItem(slot).getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD) ? 1 : 0;
            }
            helper.assertTrue(awkward == 2, "the water turned awkward");
        });
    }
}
