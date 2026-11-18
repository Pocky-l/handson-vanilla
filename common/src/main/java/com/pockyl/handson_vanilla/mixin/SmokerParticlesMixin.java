package com.pockyl.handson_vanilla.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.FurnaceFire;

/**
 * The smoker's logs burn and its chamber smokes, which this mod's model shows: small flames among the logs and smoke seeping out of
 * the chamber, after vanilla's own chimney smoke and sound, which stay. Only for the vanilla smoker while its station is on.
 */
@Mixin(SmokerBlock.class)
abstract class SmokerParticlesMixin {
    @Inject(method = "animateTick", at = @At("TAIL"))
    private void handson_vanilla$fireInside(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        FurnaceFire.animateSmoker(state, level, pos, random);
    }
}
