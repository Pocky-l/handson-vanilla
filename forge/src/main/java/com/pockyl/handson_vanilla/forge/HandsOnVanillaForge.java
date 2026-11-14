package com.pockyl.handson_vanilla.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import com.pockyl.handson_vanilla.HandsOnVanilla;

/** Forge entry point. The mod works on the client only; on a server it does nothing. */
@Mod(HandsOnVanilla.MOD_ID)
public final class HandsOnVanillaForge {
    public HandsOnVanillaForge() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeClient::init);
    }
}
