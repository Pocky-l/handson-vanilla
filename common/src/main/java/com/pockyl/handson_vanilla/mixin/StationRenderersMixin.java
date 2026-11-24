package com.pockyl.handson_vanilla.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_vanilla.client.CraftingTables;
import com.pockyl.handson_vanilla.client.FurnaceKind;

/**
 * Vanilla gives the crafting table and the furnaces no block entity renderer, but other mods do: Visual Workbench draws what lies
 * in the table's grid over it, Easy Anvils the pieces on an anvil, others draw a furnace's contents in front of it. Where this
 * mod's scene shows the same things (the table's cells while its menu is open here, an anvil's pieces and a furnace's or smoker's
 * insides whenever its scene is drawn) they would be drawn twice, one copy floating off its place; such a renderer is stopped
 * there, at the dispatcher, whichever mod registered it. Everywhere else, and at a table nobody here works at, it draws as before.
 * The enchanting table's and the lectern's own books are stopped the same way while the scene draws them.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        var state = blockEntity.getBlockState();
        // Any crafting table Visual Workbench serves, other mods' too.
        if (CraftingTables.craftingTable(state)) {
            if (DiegesisClient.station(blockEntity.getBlockPos()).map(view -> view.menu() != null).orElse(false)) {
                callback.cancel();
            }
        } else if (FurnaceKind.remodelled(state) != null && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos())) {
            callback.cancel();
        } else if (state.is(Blocks.ENCHANTING_TABLE)
                && DiegesisClient.station(blockEntity.getBlockPos()).map(view -> view.isBlockHidden()).orElse(false)) {
            // The table's book is drawn by the scene while it comes to the player and goes back; its ticks go on, so it returns to
            // where the table's own book is then.
            callback.cancel();
        } else if (state.is(net.minecraft.tags.BlockTags.ANVIL) && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos())) {
            // Easy Anvils keeps the two pieces in the anvil and draws them lying on it: the scene's slots show them meanwhile.
            callback.cancel();
        } else if (state.is(Blocks.LECTERN) && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos())) {
            // The lectern's book is the scene's, in the same place and pose, with its text on it while it is read.
            callback.cancel();
        }
    }
}
