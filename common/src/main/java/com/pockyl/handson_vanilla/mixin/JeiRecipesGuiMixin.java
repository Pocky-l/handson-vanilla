package com.pockyl.handson_vanilla.mixin;

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
 * over the crafting table station, its recipe page gets the station's vanilla menu, so the button is there. Without JEI, or with a
 * JEI that has no such method, nothing happens.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
abstract class JeiRecipesGuiMixin {
    @Inject(method = "getParentContainer", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$stationMenu(CallbackInfoReturnable<Optional<AbstractContainerMenu>> cir) {
        AbstractContainerMenu menu = StationHover.menu();
        if (cir.getReturnValue().isEmpty() && menu != null) {
            cir.setReturnValue(Optional.of(menu));
        }
    }

    /** The same in later JEI builds (15.20 and on), where the method gives the menu or null. */
    @Inject(method = "getParentContainerMenu", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void handson_vanilla$stationMenuOrNull(CallbackInfoReturnable<AbstractContainerMenu> cir) {
        AbstractContainerMenu menu = StationHover.menu();
        if (cir.getReturnValue() == null && menu != null) {
            cir.setReturnValue(menu);
        }
    }
}
