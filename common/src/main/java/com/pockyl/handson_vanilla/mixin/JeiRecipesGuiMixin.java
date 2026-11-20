package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_vanilla.client.StationHover;

import java.util.Optional;

/**
 * JEI looks for the menu its "+" fills on the screen it was opened from, and a station's screen is no container screen: opened
 * over a station, its recipe page gets the station's vanilla menu, so the button is there. Without JEI, or with a JEI that has no
 * such method, nothing happens.
 * <p>
 * The hooks sit at the head of the method, never at its return: some JEI builds are shrunk so that the method keeps the parent
 * screen in the local that held {@code this}, and a callback at the return would be called on that screen. The class then fails
 * verification and JEI does not start at all. While a station is focused the page was opened over it, so JEI would find nothing.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
abstract class JeiRecipesGuiMixin {
    @Inject(method = "getParentContainer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$stationMenu(CallbackInfoReturnable<Optional<AbstractContainerMenu>> cir) {
        AbstractContainerMenu menu = StationHover.menu();
        if (menu != null) {
            cir.setReturnValue(Optional.of(menu));
        }
    }

    /** The same in later JEI builds (15.20 and on), where the method gives the menu or null. */
    @Inject(method = "getParentContainerMenu", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$stationMenuOrNull(CallbackInfoReturnable<AbstractContainerMenu> cir) {
        AbstractContainerMenu menu = StationHover.menu();
        if (menu != null) {
            cir.setReturnValue(menu);
        }
    }

    /** Builds from 15.70 and 19.53 on: the "+" asks this for the container screen and fills that screen's menu. */
    @Inject(method = "getParentContainerScreen", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$stationMenuScreen(CallbackInfoReturnable<AbstractContainerScreen<?>> cir) {
        AbstractContainerScreen<?> screen = StationHover.menuScreen();
        if (screen != null) {
            cir.setReturnValue(screen);
        }
    }
}
