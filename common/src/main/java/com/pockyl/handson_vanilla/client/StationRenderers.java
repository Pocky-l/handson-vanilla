package com.pockyl.handson_vanilla.client;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.diegesis.api.client.DiegesisClient;

/**
 * Vanilla gives the crafting table and the furnaces no block entity renderer, but other mods do: Visual Workbench draws what lies
 * in the table's grid over it, Easy Anvils the pieces on an anvil, others draw a furnace's contents in front of it. Where this
 * mod's scene shows the same things (the table's cells while its menu is open here, an anvil's pieces and a furnace's or smoker's
 * insides whenever its scene is drawn) they would be drawn twice, one copy floating off its place; such a renderer is stopped
 * there, at the dispatcher, whichever mod registered it. Everywhere else, and at a table nobody here works at, it draws as before.
 * The enchanting table's and the lectern's own books are stopped the same way while the scene draws them. Since Minecraft 26.x a
 * block entity is drawn from the look the dispatcher reads of it: none read, nothing drawn. The mixins of both loaders ask here.
 */
public final class StationRenderers {
    private StationRenderers() {
    }

    /** Whether the block entity's renderers are stopped: the station's scene draws what they would. */
    public static boolean drawnByScene(BlockEntity blockEntity) {
        BlockState state = blockEntity.getBlockState();
        // Visual Workbench's table is its own twin block of the crafting table, any mod's, not vanilla's.
        if (BlockTwins.craftingTable(state)) {
            return DiegesisClient.station(blockEntity.getBlockPos()).map(view -> view.menu() != null).orElse(false);
        }
        if (FurnaceKind.remodelled(state) != null) {
            return DiegesisClient.isSceneDrawn(blockEntity.getBlockPos());
        }
        if (state.getBlock() instanceof EnchantingTableBlock) {
            // The table's book is drawn by the scene while it comes to the player and goes back; its ticks go on, so it returns to
            // where the table's own book is then.
            return DiegesisClient.station(blockEntity.getBlockPos()).map(view -> view.isBlockHidden()).orElse(false);
        }
        if (state.is(BlockTags.ANVIL) || state.getBlock() instanceof AnvilBlock) {
            // Easy Anvils keeps the two pieces in the anvil and draws them lying on it: the scene's slots show them meanwhile.
            return DiegesisClient.isSceneDrawn(blockEntity.getBlockPos());
        }
        // The lectern's book is the scene's, in the same place and pose, with its text on it while it is read.
        return state.is(Blocks.LECTERN) && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos());
    }
}
