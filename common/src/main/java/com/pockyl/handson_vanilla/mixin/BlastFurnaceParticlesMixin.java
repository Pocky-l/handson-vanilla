package com.pockyl.handson_vanilla.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.FurnaceFire;

/**
 * The blast furnace's fire burns in its hearth, which this mod's model has, and its smoke curls out of the hatch's slits instead of
 * rising off its whole front. Only for the vanilla blast furnace while its station is on: another mod's block that extends it keeps
 * vanilla's smoke.
 */
@Mixin(BlastFurnaceBlock.class)
abstract class BlastFurnaceParticlesMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$fireInside(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (FurnaceFire.insideBlast(state)) {
            FurnaceFire.animateBlast(state, level, pos, random);
            ci.cancel();
        }
    }
}
