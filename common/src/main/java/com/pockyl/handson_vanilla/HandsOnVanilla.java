package com.pockyl.handson_vanilla;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/** Constants of the mod. The mod is client-side only: everything else starts in {@code client.HandsOnVanillaClient}. */
public final class HandsOnVanilla {
    public static final String MOD_ID = "handson_vanilla";
    public static final Logger LOGGER = LogUtils.getLogger();

    private HandsOnVanilla() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
