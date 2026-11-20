package com.pockyl.handson_vanilla.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.ContinuousCrafting;

/**
 * Inventory Profiles Next refills a crafting grid after a craft from its handler's tick, which runs only while a container screen is
 * open: at a station there is none. While a crafting table station is worked the handler is run for the station's menu instead
 * ({@link ContinuousCrafting#atStation}). Without Inventory Profiles Next nothing happens.
 */
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.ContinuousCraftingHandler", remap = false)
abstract class ContinuousCraftingMixin {
    @Inject(method = "onTickInGame()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$atStation(CallbackInfo ci) {
        if (ContinuousCrafting.atStation(this)) {
            ci.cancel();
        }
    }
}
