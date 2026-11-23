package com.pockyl.handson_vanilla.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.PolymorphLink;

/**
 * Polymorph tells the players at a furnace's screen which recipe the furnace keeps, in a list its own screen reads: at a station
 * there is no such screen, so the list is read here as well, before Polymorph's handler. Without Polymorph nothing happens.
 */
@Pseudo
@Mixin(targets = "com.illusivesoulworks.polymorph.common.network.server.ClientPacketHandler", remap = false)
abstract class PolymorphListMixin {
    @Inject(method = "handle(Lcom/illusivesoulworks/polymorph/common/network/server/SPacketRecipesList;)V", at = @At("HEAD"),
            remap = false, require = 0)
    private static void handson_vanilla$listArrived(@Coerce Object packet, CallbackInfo ci) {
        PolymorphLink.listArrived(packet);
    }
}
