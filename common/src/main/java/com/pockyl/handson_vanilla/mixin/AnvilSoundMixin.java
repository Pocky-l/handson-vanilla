package com.pockyl.handson_vanilla.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.AnvilSound;

/** The anvil station's hammer keeps time with vanilla's anvil sound: when it starts and at what pitch, as this client plays it. */
@Mixin(ClientLevel.class)
abstract class AnvilSoundMixin {
    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"))
    private void handson_vanilla$heard(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch,
            boolean distanceDelay, CallbackInfo ci) {
        if (sound == SoundEvents.ANVIL_USE) {
            AnvilSound.heard(x, y, z, pitch);
        }
    }
}
