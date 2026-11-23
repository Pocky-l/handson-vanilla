package com.pockyl.handson_vanilla.neoforge.mixin;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.FastBenchResult;

/**
 * FastWorkbench tells the client a crafting table's result in a payload of its own, which it gives only to its own crafting screen,
 * and its result slot ignores the vanilla sync of that slot: at a station there is no such screen, so the result never showed. The
 * payload is read here as well, before FastWorkbench's handler, and given to the station's menu. Without FastWorkbench nothing
 * happens. FastWorkbench is on NeoForge only.
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.fastbench.net.RecipePayload$Provider", remap = false)
abstract class FastBenchResultMixin {
    @Inject(method = "handleClient(Ldev/shadowsoffire/fastbench/net/RecipePayload;"
            + "Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V", at = @At("HEAD"), remap = false, require = 0)
    private void handson_vanilla$toStation(@Coerce CustomPacketPayload message, IPayloadContext context, CallbackInfo ci) {
        FastBenchResult.arrived(message);
    }
}
