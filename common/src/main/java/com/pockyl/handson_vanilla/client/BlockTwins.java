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
 * Easy Magic and Easy Anvils give every enchanting table and anvil a twin block of their own, with their block entity, named
 * {@code <mod>:<namespace>/<path>} ({@code easymagic:minecraft/enchanting_table}, {@code easyanvils:minecraft/anvil}): the item
 * places the twin, and vanilla's block, unless it is in their "unaltered" tag, opens nothing when used. Their 1.21.1 builds answer
 * that use with "invalid block" (unless their common config allows vanilla's block); their later builds (1.21.11 on, classes under
 * {@code fuzs.<mod>.common} from Minecraft 26.x) turn the block into the twin instead (unless their server config says no). The
 * stations open on the twins; on such a vanilla block they stay closed, so the game's own answer comes as without this mod, and the
 * next use opens the twin. Reached by name and reflection, with no compile dependency.
 */
final class BlockTwins {
    static final String EASY_MAGIC = "easymagic";
    static final String EASY_ANVILS = "easyanvils";

    private static final Map<String, Boolean> DISABLED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PRESENT = new ConcurrentHashMap<>();

    private BlockTwins() {
    }

    /** Whether the class is there (the mod is loaded), without loading it. */
    static boolean present(String className) {
        return PRESENT.computeIfAbsent(className, name -> {
            try {
                Class.forName(name, false, BlockTwins.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        });
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

    /**
     * Whether a use of this vanilla enchanting table or anvil opens no menu because Easy Magic / Easy Anvils took it over: refused
     * (1.21.1) or turned into their twin (later builds).
     */
    static boolean refused(BlockState state) {
        if (state.is(Blocks.ENCHANTING_TABLE)) {
            return refusedBy(state, EASY_MAGIC, "EasyMagic", "disableVanillaEnchantingTable",
                    "convertVanillaEnchantingTableWhenInteracting", "unaltered_enchanting_tables", Blocks.ENCHANTING_TABLE);
        }
        if (state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) {
            return refusedBy(state, EASY_ANVILS, "EasyAnvils", "disableVanillaAnvil", "convertVanillaAnvilWhenInteracting",
                    "unaltered_anvils", state.getBlock());
        }
        return false;
    }

    private static boolean refusedBy(BlockState state, String mod, String modClass, String disable, String convert, String unaltered,
            Block vanilla) {
        if (twin(mod, vanilla).get() == vanilla) {
            return false;
        }
        if (state.is(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(mod, unaltered)))) {
            return false;
        }
        return DISABLED.computeIfAbsent(mod, key -> {
            try {
                // 1.21.1: CommonConfig.disableVanilla*; later: ServerConfig.convertVanilla*WhenInteracting, under .common on 26.x.
                String base = present("fuzs." + mod + ".common." + modClass) ? "fuzs." + mod + ".common." : "fuzs." + mod + ".";
                boolean old = present(base + "config.CommonConfig");
                Class<?> type = Class.forName(base + (old ? "config.CommonConfig" : "config.ServerConfig"));
                Object holder = Class.forName(base + modClass).getField("CONFIG").get(null);
                for (Method method : holder.getClass().getMethods()) {
                    if (method.getName().equals("get") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == Class.class) {
                        Field flag = type.getField(old ? disable : convert);
                        return flag.getBoolean(method.invoke(holder, type));
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("{}'s config could not be read; its vanilla blocks are taken as refused", mod, e);
            }
            // Their default.
            return true;
        });
    }
}
