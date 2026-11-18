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
import com.pockyl.handson_vanilla.client.FurnaceKind;

/**
 * Vanilla gives the crafting table and the furnaces no block entity renderer, but other mods do: Visual Workbench draws what lies
 * in the table's grid over it, others draw a furnace's contents in front of it. Where this mod's scene shows the same things (the
 * table's cells while its menu is open here, a furnace's or smoker's insides whenever its scene is drawn) they would be drawn twice,
 * one copy floating off its place; such a renderer is stopped there, at the dispatcher, whichever mod registered it. Everywhere
 * else, and at a table nobody here works at, it draws as before.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$drawnByScene(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            CallbackInfo callback) {
        var state = blockEntity.getBlockState();
        if (state.is(Blocks.CRAFTING_TABLE)) {
            if (DiegesisClient.station(blockEntity.getBlockPos()).map(view -> view.menu() != null).orElse(false)) {
                callback.cancel();
            }
        } else if (FurnaceKind.remodelled(state) != null && DiegesisClient.isSceneDrawn(blockEntity.getBlockPos())) {
            callback.cancel();
        }
    }
}
