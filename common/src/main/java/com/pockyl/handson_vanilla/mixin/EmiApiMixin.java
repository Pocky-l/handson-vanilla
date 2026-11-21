package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;

import com.pockyl.handson_vanilla.client.EmiSidebar;
import com.pockyl.handson_vanilla.client.StationHover;

import java.util.List;
import java.util.Map;

/**
 * EMI works with the container screen that is open, and a station's screen is none: at a station of this mod it gets the never-shown
 * container screen over the station's vanilla menu, so its recipe pages fill the station (the "+") and its sidebar knows what can be
 * made, and its recipe pages open over the station. A page opened from there keeps that screen as the one to go back to: showing it
 * is going back to the station ({@code ScreenShownMixin}). Without EMI nothing happens.
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.api.EmiApi", remap = false)
abstract class EmiApiMixin {
    @Inject(method = "getHandledScreen", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void handson_vanilla$stationMenuScreen(CallbackInfoReturnable<AbstractContainerScreen<?>> cir) {
        if (StationHover.isOurs(Minecraft.getInstance().screen)) {
            AbstractContainerScreen<?> screen = StationHover.menuScreen();
            if (screen != null) {
                cir.setReturnValue(screen);
            }
        }
    }

    /** EMI opens recipe pages over container screens only: at a station of this mod they open over the station ({@code EmiSidebar}). */
    @Inject(method = "setPages", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void handson_vanilla$pagesAtStation(Map<EmiRecipeCategory, List<EmiRecipe>> pages, EmiIngredient stack,
            CallbackInfo ci) {
        if (EmiSidebar.openPages(pages, stack)) {
            ci.cancel();
        }
    }
}
