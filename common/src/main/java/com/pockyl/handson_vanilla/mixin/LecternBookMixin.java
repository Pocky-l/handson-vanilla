package com.pockyl.handson_vanilla.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.VanillaConfig;
import com.pockyl.handson_vanilla.client.WorldBooks;

/** The book on every lectern is the mod's, the one read at the station, while the station is on. */
@Mixin(LecternRenderer.class)
abstract class LecternBookMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/LecternBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$ownBook(LecternBlockEntity entity, float partial, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay, CallbackInfo callback) {
        if (!VanillaConfig.lectern()) {
            return;
        }
        var state = entity.getBlockState();
        if (state.hasProperty(LecternBlock.HAS_BOOK) && state.getValue(LecternBlock.HAS_BOOK)) {
            WorldBooks.drawLectern(state, poseStack, buffers, light);
        }
        callback.cancel();
    }
}
