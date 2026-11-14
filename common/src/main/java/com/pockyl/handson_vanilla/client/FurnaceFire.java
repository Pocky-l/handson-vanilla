package com.pockyl.handson_vanilla.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lit furnace's flames and smoke, inside its firebox (this mod's model has one) instead of on its front: over the coals, behind
 * the doors, a little smoke rising off them. The crackle is vanilla's.
 */
public final class FurnaceFire {
    /** The firebox inside, block pixels: how deep from the front, how far either side of the middle, how high over the floor. */
    private static final double DEPTH_FROM = 2.0;
    private static final double DEPTH_TO = 4.5;
    private static final double ACROSS = 3.0;
    private static final double LOW = 2.2;
    private static final double HIGH = 3.4;
    /** Share of ticks a puff of smoke rises with the flame. */
    private static final float SMOKE = 0.3F;

    private FurnaceFire() {
    }

    /** Whether this block has the firebox: the vanilla furnace while the furnace station, which brings the model, is on. */
    public static boolean inside(BlockState state) {
        return state.is(net.minecraft.world.level.block.Blocks.FURNACE) && VanillaConfig.furnace();
    }

    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(AbstractFurnaceBlock.LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
        double out = 0.5 - (DEPTH_FROM + random.nextDouble() * (DEPTH_TO - DEPTH_FROM)) / 16.0;
        double across = (random.nextDouble() * 2.0 - 1.0) * ACROSS / 16.0;
        double up = (LOW + random.nextDouble() * (HIGH - LOW)) / 16.0;
        double px = x + facing.getStepX() * out + (facing.getAxis() == Direction.Axis.Z ? across : 0.0);
        double pz = z + facing.getStepZ() * out + (facing.getAxis() == Direction.Axis.X ? across : 0.0);
        // The small flame of a candle: the firebox is four pixels high, a full one would stand out of it.
        level.addParticle(ParticleTypes.SMALL_FLAME, px, y + up, pz, 0.0, 0.0, 0.0);
        if (random.nextFloat() < SMOKE) {
            level.addParticle(ParticleTypes.SMOKE, px, y + up + 1.0 / 16.0, pz, 0.0, 0.005, 0.0);
        }
    }
}
