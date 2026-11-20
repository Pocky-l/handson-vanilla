package com.pockyl.handson_vanilla.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * How many layers a banner may bear at the loom. Vanilla's limit (6) is checked by its loom screen, not its menu, so the station
 * checks it itself, and mods that raise it do so on that screen: Quark's More Banner Layers ({@code getLimit}) and More Banner
 * Features' threads ({@code maxLayers}, per banner). Both are asked here, through reflection, when they are loaded.
 */
public final class BannerLimit {
    private static final String QUARK = "org.violetmoon.quark.content.tweaks.module.MoreBannerLayersModule";
    private static final String MORE_BANNER_FEATURES = "dev.svrt.domiirl.mbf.utils.BannerItems";

    private static boolean looked;
    private static MethodHandle quarkLimit;
    private static MethodHandle bannerFeaturesLimit;

    private BannerLimit() {
    }

    /** The most layers {@code banner} may bear: a pattern is woven onto it only while it has fewer. */
    public static int of(ItemStack banner) {
        lookUp();
        int limit = BannerBlockEntity.MAX_PATTERNS;
        try {
            if (quarkLimit != null) {
                limit = (int) quarkLimit.invokeExact(limit);
            }
            if (bannerFeaturesLimit != null) {
                limit = (int) bannerFeaturesLimit.invokeExact(banner, limit);
            }
        } catch (Throwable e) {
            HandsOnVanilla.LOGGER.warn("A banner layer limit of another mod failed; vanilla's is used", e);
            quarkLimit = null;
            bannerFeaturesLimit = null;
            return BannerBlockEntity.MAX_PATTERNS;
        }
        return limit;
    }

    private static void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        quarkLimit = find(lookup, QUARK, "getLimit", MethodType.methodType(int.class, int.class));
        bannerFeaturesLimit = find(lookup, MORE_BANNER_FEATURES, "maxLayers", MethodType.methodType(int.class, ItemStack.class, int.class));
    }

    private static MethodHandle find(MethodHandles.Lookup lookup, String className, String name, MethodType type) {
        try {
            return lookup.findStatic(Class.forName(className, false, BannerLimit.class.getClassLoader()), name, type);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            HandsOnVanilla.LOGGER.warn("{}.{} not found: its banner layer limit is not used at the loom", className, name);
            return null;
        }
    }
}
