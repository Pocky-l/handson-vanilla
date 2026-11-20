package com.pockyl.handson_vanilla.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * The lectern station's buttons on a server, as it sends them: a page set by number (a spread turned, a page clicked, a link),
 * vanilla's previous and next, a number past the book kept within it, the comparator following the page, and Take Book putting
 * the book into the inventory, after which the menu is no longer valid (the server closes it).
 */
public final class LecternTests {
    private static final int PAGES = 5;

    private LecternTests() {
    }

    public static void lecternTurnsAndGivesBook(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.LECTERN.defaultBlockState());
        ItemStack written = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = written.getOrCreateTag();
        tag.putString("title", "Rules");
        tag.putString("author", "Pocky");
        ListTag pages = new ListTag();
        for (int i = 0; i < PAGES; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("Page " + (i + 1)))));
        }
        tag.put("pages", pages);
        Player player = helper.makeMockPlayer();
        helper.assertTrue(LecternBlock.tryPlaceBook(player, helper.getLevel(), pos, helper.getLevel().getBlockState(pos), written),
                "the book is laid on the lectern");
        LecternBlockEntity lectern = (LecternBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(lectern != null && helper.getLevel().getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "the lectern holds it");
        LecternMenu menu = (LecternMenu) lectern.createMenu(9, player.getInventory(), player);
        helper.assertTrue(menu != null && menu.slots.size() == 1, "the menu has the book's slot and no inventory");
        menu.clickMenuButton(player, 102);
        helper.assertTrue(lectern.getPage() == 2 && menu.getPage() == 2, "button 102 opens page 2");
        menu.clickMenuButton(player, 2);
        helper.assertTrue(lectern.getPage() == 3, "next gives page 3");
        menu.clickMenuButton(player, 1);
        helper.assertTrue(lectern.getPage() == 2, "previous gives page 2");
        menu.clickMenuButton(player, 109);
        helper.assertTrue(lectern.getPage() == PAGES - 1, "a page past the book is the last one");
        helper.assertTrue(lectern.getRedstoneSignal() == 15, "the comparator gives full strength on the last page");
        menu.clickMenuButton(player, 100);
        helper.assertTrue(lectern.getPage() == 0 && lectern.getRedstoneSignal() == 1, "and the least on the first");
        helper.assertTrue(menu.clickMenuButton(player, 3), "Take Book");
        helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 1, "the book is in the inventory");
        helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "the lectern is empty");
        helper.assertTrue(!menu.stillValid(player), "the menu is no longer valid");
        helper.succeed();
    }
}
