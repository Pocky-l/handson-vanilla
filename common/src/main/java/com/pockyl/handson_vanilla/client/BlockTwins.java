package com.pockyl.handson_vanilla.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Easy Magic and Easy Anvils on 1.21.1 give every enchanting table and anvil a twin block of their own, with their block entity,
 * named {@code <mod>:<namespace>/<path>} ({@code easymagic:minecraft/enchanting_table}, {@code easyanvils:minecraft/anvil}): the
 * item places the twin, world generation converts to it, and vanilla's block, unless their config allows it or it is in their
 * "unaltered" tag, only answers its use with "invalid block". The stations open on the twins; on a refused vanilla block they
 * stay closed, so the game's own message comes as without this mod. Reached by name and reflection, with no compile dependency.
 */
final class BlockTwins {
    static final String EASY_MAGIC = "easymagic";
    static final String EASY_ANVILS = "easyanvils";

    private static final Map<String, Boolean> DISABLED = new ConcurrentHashMap<>();

    private BlockTwins() {
    }

    /** {@code mod}'s twin of {@code vanilla}, looked up once the station first needs it; vanilla's block when there is none. */
    static Supplier<Block> twin(String mod, Block vanilla) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(vanilla);
        Identifier id = Identifier.fromNamespaceAndPath(mod, key.getNamespace() + "/" + key.getPath());
        return () -> BuiltInRegistries.BLOCK.getOptional(id).orElse(vanilla);
    }

    /** The vanilla block {@code state} stands for: itself, or the one an Easy Magic / Easy Anvils twin was made from. */
    static Identifier original(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if ((key.getNamespace().equals(EASY_MAGIC) || key.getNamespace().equals(EASY_ANVILS)) && key.getPath().contains("/")) {
            int slash = key.getPath().indexOf('/');
            return Identifier.fromNamespaceAndPath(key.getPath().substring(0, slash), key.getPath().substring(slash + 1));
        }
        return key;
    }

    /** Whether the game refuses the use of this vanilla enchanting table or anvil because Easy Magic / Easy Anvils took it over. */
    static boolean refused(BlockState state) {
        if (state.is(Blocks.ENCHANTING_TABLE)) {
            return refusedBy(state, EASY_MAGIC, "fuzs.easymagic.EasyMagic", "fuzs.easymagic.config.CommonConfig",
                    "disableVanillaEnchantingTable", "unaltered_enchanting_tables", Blocks.ENCHANTING_TABLE);
        }
        if (state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) {
            return refusedBy(state, EASY_ANVILS, "fuzs.easyanvils.EasyAnvils", "fuzs.easyanvils.config.CommonConfig",
                    "disableVanillaAnvil", "unaltered_anvils", state.getBlock());
        }
        return false;
    }

    private static boolean refusedBy(BlockState state, String mod, String modClass, String configClass, String flag, String unaltered,
            Block vanilla) {
        if (twin(mod, vanilla).get() == vanilla) {
            return false;
        }
        if (state.is(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(mod, unaltered)))) {
            return false;
        }
        return DISABLED.computeIfAbsent(mod + ":" + flag, key -> {
            try {
                Field config = Class.forName(modClass).getField("CONFIG");
                Class<?> type = Class.forName(configClass);
                Object holder = config.get(null);
                for (Method method : holder.getClass().getMethods()) {
                    if (method.getName().equals("get") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == Class.class) {
                        return type.getField(flag).getBoolean(method.invoke(holder, type));
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("{}'s {} could not be read; its vanilla blocks are taken as refused", mod, flag, e);
            }
            // Their default.
            return true;
        });
    }
}
