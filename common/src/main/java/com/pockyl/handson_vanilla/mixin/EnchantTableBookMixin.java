package com.pockyl.handson_vanilla.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.VanillaConfig;
import com.pockyl.handson_vanilla.client.WorldBooks;

/** The book over every enchanting table is the mod's, the one read at the station, while the station is on. */
@Mixin(EnchantTableRenderer.class)
abstract class EnchantTableBookMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$ownBook(EnchantingTableBlockEntity entity, float partial, PoseStack poseStack, MultiBufferSource buffers,
            int light, int overlay, CallbackInfo callback) {
        if (VanillaConfig.enchantingTable()) {
            WorldBooks.drawEnchanting(entity, partial, poseStack, buffers, light);
            callback.cancel();
        }
    }
}
