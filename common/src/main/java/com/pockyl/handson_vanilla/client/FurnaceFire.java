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
 * A lit furnace's flames and smoke where this mod's model has its fire, instead of on its front. The furnace: over the coals in its
 * firebox, behind the doors, a little smoke rising off them; the crackle is vanilla's. The smoker keeps vanilla's smoke from its
 * chimney and its sound, and adds small flames licking among the logs and a wisp of smoke seeping out round the chamber's door now
 * and then. The blast furnace: flames over the coals under the grate of its hearth and smoke curling out along the top of its hatch,
 * with vanilla's crackle.
 */
public final class FurnaceFire {
    /** The furnace's firebox inside, block pixels: how deep from the front, how far either side of the middle, how high. */
    private static final Box FURNACE_FIRE = new Box(2.0, 4.5, 3.0, 2.2, 3.4);
    /** Share of ticks a puff of smoke rises with the furnace's flame. */
    private static final float SMOKE = 0.3F;
    /** The smoker's flames, among and over its logs. */
    private static final Box SMOKER_FIRE = new Box(1.5, 4.5, 3.0, 4.0, 6.2);
    /** Where smoke seeps out of the smoker's chamber: just behind the door, high up. */
    private static final Box SMOKER_SEEP = new Box(0.2, 0.6, 4.0, 10.5, 11.8);
    /** Share of ticks a wisp seeps out of the smoker's chamber, and how fast it leaves the front (blocks per tick). */
    private static final float SEEP = 0.35F;
    private static final double SEEP_SPEED = 0.012;
    /** The blast furnace's flames, between the coals and the grate of its hearth. */
    private static final Box BLAST_FIRE = new Box(2.0, 4.5, 3.0, 3.6, 5.2);
    /**
     * Where smoke comes out of the blast furnace's hatch: along its top, over what lies on the grate, so at the station it drifts
     * out under the lifted hatch instead of hanging before the ore.
     */
    private static final Box BLAST_SEEP = new Box(0.2, 0.6, 3.0, 9.8, 10.8);

    private FurnaceFire() {
    }

    /** A space inside the block, pixels: from and to how deep from the front, how far either side of the middle, from and to how high. */
    private record Box(double from, double to, double across, double low, double high) {
    }

    /** Whether the furnace's fire is this one: only for the vanilla block while its station, which brings the model, is on. */
    public static boolean inside(BlockState state) {
        return FurnaceKind.remodelled(state) == FurnaceKind.FURNACE;
    }

    /** The furnace's fire, in place of vanilla's. */
    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(AbstractFurnaceBlock.LIT)) {
            return;
        }
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0F, 1.0F, false);
        }
        // The small flame of a candle: the firebox is four pixels high, a full one would stand out of it.
        double[] at = spot(state, pos, random, FURNACE_FIRE);
        level.addParticle(ParticleTypes.SMALL_FLAME, at[0], at[1], at[2], 0.0, 0.0, 0.0);
        if (random.nextFloat() < SMOKE) {
            level.addParticle(ParticleTypes.SMOKE, at[0], at[1] + 1.0 / 16.0, at[2], 0.0, 0.005, 0.0);
        }
    }

    /** The smoker's fire and the smoke of its chamber, beside vanilla's chimney smoke, while its station brings the model. */
    public static void animateSmoker(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (FurnaceKind.remodelled(state) != FurnaceKind.SMOKER || !state.getValue(AbstractFurnaceBlock.LIT)) {
            return;
        }
        double[] fire = spot(state, pos, random, SMOKER_FIRE);
        level.addParticle(ParticleTypes.SMALL_FLAME, fire[0], fire[1], fire[2], 0.0, 0.0, 0.0);
        if (random.nextFloat() < SEEP) {
            double[] seep = spot(state, pos, random, SMOKER_SEEP);
            Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
            level.addParticle(ParticleTypes.SMOKE, seep[0], seep[1], seep[2], facing.getStepX() * SEEP_SPEED, 0.004,
                    facing.getStepZ() * SEEP_SPEED);
        }
    }

    /** Whether the blast furnace's fire is this one: the vanilla block while its station, which brings the model, is on. */
    public static boolean insideBlast(BlockState state) {
        return FurnaceKind.remodelled(state) == FurnaceKind.BLAST_FURNACE;
    }

    /** The blast furnace's fire, in place of vanilla's smoke on its front. */
    public static void animateBlast(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(AbstractFurnaceBlock.LIT)) {
            return;
        }
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
        double[] fire = spot(state, pos, random, BLAST_FIRE);
        level.addParticle(ParticleTypes.SMALL_FLAME, fire[0], fire[1], fire[2], 0.0, 0.0, 0.0);
        if (random.nextFloat() < SEEP) {
            double[] seep = spot(state, pos, random, BLAST_SEEP);
            Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
            level.addParticle(ParticleTypes.SMOKE, seep[0], seep[1], seep[2], facing.getStepX() * SEEP_SPEED, 0.004,
                    facing.getStepZ() * SEEP_SPEED);
        }
    }

    /** A random point of the box, in the world, turned with the block's front. */
    private static double[] spot(BlockState state, BlockPos pos, RandomSource random, Box box) {
        Direction facing = state.getValue(AbstractFurnaceBlock.FACING);
        double out = 0.5 - (box.from() + random.nextDouble() * (box.to() - box.from())) / 16.0;
        double across = (random.nextDouble() * 2.0 - 1.0) * box.across() / 16.0;
        double up = (box.low() + random.nextDouble() * (box.high() - box.low())) / 16.0;
        double x = pos.getX() + 0.5 + facing.getStepX() * out + (facing.getAxis() == Direction.Axis.Z ? across : 0.0);
        double z = pos.getZ() + 0.5 + facing.getStepZ() * out + (facing.getAxis() == Direction.Axis.X ? across : 0.0);
        return new double[] {x, pos.getY() + up, z};
    }
}
