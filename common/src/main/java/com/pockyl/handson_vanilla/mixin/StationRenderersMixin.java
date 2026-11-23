package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_vanilla.client.StationRenderers;

/**
 * Other mods' block entity renderers at a station whose scene draws the same things are stopped at the dispatcher
 * ({@link StationRenderers}). NeoForge's level reads block entities through a form of this method of its own: its mixin is in the
 * neoforge project.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$drawnByScene(BlockEntity blockEntity, float partialTicks,
            ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfoReturnable<BlockEntityRenderState> callback) {
        if (StationRenderers.drawnByScene(blockEntity)) {
            callback.setReturnValue(null);
        }
    }
}
