package com.pockyl.handson_vanilla.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.gametest.NeoForgeGameTests;

/** NeoForge entry point. The mod works on the client only; on a server it does nothing but its game tests. */
@Mod(HandsOnVanilla.MOD_ID)
public final class HandsOnVanillaNeoForge {
    public HandsOnVanillaNeoForge(IEventBus modBus, Dist dist) {
        NeoForgeGameTests.register(modBus);
        if (dist.isClient()) {
            NeoForgeClient.init(modBus);
        }
    }
}
