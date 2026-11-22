package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.GhostRecipe;

/**
 * The server's ghost recipe (a recipe picked without its items) reaches the station's table too: vanilla gives it only to an
 * open vanilla screen. At the tail the packet is on the render thread already.
 */
@Mixin(ClientPacketListener.class)
abstract class GhostRecipeMixin {
    @Inject(method = "handlePlaceRecipe", at = @At("TAIL"))
    private void handson_vanilla$ghostAtStation(ClientboundPlaceGhostRecipePacket packet, CallbackInfo ci) {
        GhostRecipe.arrived(packet.containerId(), packet.recipeDisplay());
    }
}
