package com.pockyl.handson_vanilla.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.FastBenchResult;

import java.util.function.Supplier;

/**
 * FastWorkbench tells the client a crafting table's result in a message of its own, which it gives only to its own crafting screen,
 * and keeps the vanilla sync of that slot quiet: at a station there is no such screen, so the result never showed. The message is
 * read here as well, before FastWorkbench's handler, and given to the station's menu. Without FastWorkbench nothing happens.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastbench.net.RecipeMessage$Provider", remap = false)
abstract class FastBenchResultMixin {
    @Inject(method = "handle(Ljava/lang/Object;Ljava/util/function/Supplier;)V", at = @At("HEAD"), remap = false, require = 0)
    private void handson_vanilla$toStation(Object message, Supplier<?> context, CallbackInfo ci) {
        FastBenchResult.arrived(message);
    }
}
