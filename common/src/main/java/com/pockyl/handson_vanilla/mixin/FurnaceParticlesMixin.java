package com.pockyl.handson_vanilla.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_vanilla.client.FurnaceFire;

/**
 * The furnace's flames and smoke come from inside its firebox, which this mod's model has, not from its front. Only for the vanilla
 * furnace while its station (and so the model) is on: another mod's furnace that extends this block (Quark's stone furnaces) keeps its
 * flames on its front, where its own model has the fire.
 */
@Mixin(FurnaceBlock.class)
abstract class FurnaceParticlesMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void handson_vanilla$fireInside(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (FurnaceFire.inside(state)) {
            FurnaceFire.animate(state, level, pos, random);
            ci.cancel();
        }
    }
}
