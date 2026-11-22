package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pockyl.handson_vanilla.client.StationHover;

/**
 * The container screen recipe viewers are given at a station is never shown: a viewer going back to it (EMI's recipe page closing)
 * goes back to the station's screen instead.
 */
@Mixin(Gui.class)
abstract class ScreenShownMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen handson_vanilla$stationInstead(Screen screen) {
        return StationHover.shownInstead(screen);
    }
}
