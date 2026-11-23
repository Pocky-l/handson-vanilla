package com.pockyl.handson_vanilla.neoforge.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_vanilla.client.StationRenderers;

/**
 * NeoForge's form of the common {@code StationRenderersMixin}: NeoForge adds a {@code tryExtractRenderState} that also takes the
 * cull frustum, and the level reads block entities through that one; vanilla's only hands over to it.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersNeoForgeMixin {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$drawnByScene(BlockEntity blockEntity, float partialTicks,
            ModelFeatureRenderer.CrumblingOverlay breakProgress, Frustum frustum,
            CallbackInfoReturnable<BlockEntityRenderState> callback) {
        if (StationRenderers.drawnByScene(blockEntity)) {
            callback.setReturnValue(null);
        }
    }
}
