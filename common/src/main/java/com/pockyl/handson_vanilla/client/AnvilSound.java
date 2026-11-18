package com.pockyl.handson_vanilla.client;

import net.minecraft.core.BlockPos;

/**
 * Vanilla's anvil sound as this client plays it: the server sends it when an anvil's result is taken, and its three hammer blows
 * are what the station's hammer keeps time with. Its pitch is picked on the client (0.9 to 1), and the blows come later at a
 * lower pitch, so the station reads both here.
 */
public final class AnvilSound {
    /** Seconds into the sound of its three blows, at pitch 1 (measured on {@code random/anvil_use.ogg}). */
    static final float[] BLOWS = {0.0F, 0.37F, 0.745F};

    private static BlockPos at;
    private static float pitch;
    private static boolean fresh;

    private AnvilSound() {
    }

    /** From {@code AnvilSoundMixin}: the sound starts playing at a place. */
    public static void heard(double x, double y, double z, float pitch) {
        at = BlockPos.containing(x, y, z);
        AnvilSound.pitch = pitch;
        fresh = true;
    }

    /** The pitch of a sound started at the anvil at {@code pos} since the last call, or 0. */
    static float poll(BlockPos pos) {
        if (!fresh) {
            return 0.0F;
        }
        fresh = false;
        return at.closerThan(pos, 1.5) ? pitch : 0.0F;
    }

    /** Forgets a sound not yet polled: one from before a strike is not its own. */
    static void forget() {
        fresh = false;
    }
}
