package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.pockyl.handson_vanilla.client.EmiSidebar;

/**
 * EMI hears the mouse and the keys over container screens only, and a station's screen is none: at a station of this mod they are
 * offered to EMI's sidebars and keys first, as EMI takes them over a container screen, and what EMI does not take goes on to the
 * station. Without EMI nothing changes.
 */
@Mixin(targets = "com.pockyl.diegesis.impl.client.focus.FocusScreen")
abstract class StationInputMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.mouseClicked((Screen) (Object) this, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.mouseReleased((Screen) (Object) this, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiDrag(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.mouseDragged((Screen) (Object) this, mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiScroll(double mouseX, double mouseY, double scrollX, double scrollY,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.mouseScrolled((Screen) (Object) this, mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.keyPressed((Screen) (Object) this, keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson_vanilla$emiChar(char character, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (EmiSidebar.charTyped((Screen) (Object) this, character, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
