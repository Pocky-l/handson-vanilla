package com.pockyl.handson_vanilla.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.blockentity.state.LecternRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.VanillaConfig;
import com.pockyl.handson_vanilla.client.WorldBooks;

/** The book on every lectern is the mod's, the one read at the station, while the station is on. */
@Mixin(LecternRenderer.class)
abstract class LecternBookMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/LecternRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$ownBook(LecternRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo callback) {
        if (VanillaConfig.lectern()) {
            WorldBooks.submitLectern(state, poseStack, collector);
            callback.cancel();
        }
    }
}
