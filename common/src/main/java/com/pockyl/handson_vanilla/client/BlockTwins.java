package com.pockyl.handson_vanilla.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Easy Magic, Easy Anvils and Visual Workbench on 1.21.1 give every enchanting table, anvil and crafting table a twin block of their
 * own, with their block entity, named {@code <mod>:<namespace>/<path>} ({@code easymagic:minecraft/enchanting_table},
 * {@code easyanvils:minecraft/anvil}, {@code visualworkbench:minecraft/crafting_table}): the item places the twin, and the block it
 * was made from no longer opens its menu. Easy Magic and Easy Anvils answer its use with "invalid block" unless their config allows
 * it or it is in their "unaltered" tag; Visual Workbench turns it into its twin on that use (its server config, on by default) unless
 * it is in its unaltered tag. The stations open on the twins; on such a vanilla block they stay closed, so the game's own answer
 * comes as without this mod. Reached by name and reflection, with no compile dependency.
 */
public final class BlockTwins {
    static final String EASY_MAGIC = "easymagic";
    static final String EASY_ANVILS = "easyanvils";
    static final String VISUAL_WORKBENCH = "visualworkbench";

    private static final Map<String, Boolean> DISABLED = new ConcurrentHashMap<>();
    private static volatile Set<Block> workbenches;
    private static volatile boolean workbenchConfigLost;

    private BlockTwins() {
    }

    /** {@code mod}'s twin of {@code vanilla}, looked up once the station first needs it; vanilla's block when there is none. */
    static Supplier<Block> twin(String mod, Block vanilla) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(vanilla);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(mod, key.getNamespace() + "/" + key.getPath());
        return () -> BuiltInRegistries.BLOCK.getOptional(id).orElse(vanilla);
    }

    /**
     * Every crafting table Visual Workbench made a twin of, vanilla's and other mods', as those twins; empty without it. Looked up
     * once, when the crafting table station first needs it, long after every block is registered.
     */
    static Set<Block> workbenches() {
        Set<Block> found = workbenches;
        if (found == null) {
            found = BuiltInRegistries.BLOCK.entrySet().stream()
                    .filter(entry -> isTwinName(entry.getKey().location(), VISUAL_WORKBENCH))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toUnmodifiableSet());
            workbenches = found;
        }
        return found;
    }

    /** Whether the crafting table station works at this block: vanilla's crafting table, or Visual Workbench's twin of any table. */
    public static boolean craftingTable(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE) || workbenches().contains(state.getBlock());
    }

    /** The vanilla block {@code state} stands for: itself, or the one an Easy Magic, Easy Anvils or Visual Workbench twin was made from. */
    static ResourceLocation original(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (isTwinName(key, EASY_MAGIC) || isTwinName(key, EASY_ANVILS) || isTwinName(key, VISUAL_WORKBENCH)) {
            int slash = key.getPath().indexOf('/');
            return ResourceLocation.fromNamespaceAndPath(key.getPath().substring(0, slash), key.getPath().substring(slash + 1));
        }
        return key;
    }

    private static boolean isTwinName(ResourceLocation key, String mod) {
        return key.getNamespace().equals(mod) && key.getPath().contains("/");
    }

    /**
     * Whether the game answers the use of this vanilla block with something else than its menu because one of the twin mods took it
     * over: Easy Magic's and Easy Anvils' refusal, or Visual Workbench turning the crafting table into its twin.
     */
    static boolean refused(BlockState state) {
        if (state.is(Blocks.CRAFTING_TABLE)) {
            return twin(VISUAL_WORKBENCH, Blocks.CRAFTING_TABLE).get() != Blocks.CRAFTING_TABLE
                    && !state.is(tag(VISUAL_WORKBENCH, "unaltered_workbenches")) && convertsWorkbenches();
        }
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
        if (state.is(tag(mod, unaltered))) {
            return false;
        }
        return DISABLED.computeIfAbsent(mod + ":" + flag, key -> {
            try {
                Object values = config(modClass, configClass);
                return values.getClass().getField(flag).getBoolean(values);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("{}'s {} could not be read; its vanilla blocks are taken as refused", mod, flag, e);
            }
            // Their default.
            return true;
        });
    }

    /**
     * Visual Workbench's {@code convertVanillaWorkbenchWhenInteracting}: a server option, which the client gets from the server it
     * joins, so it is read at every use rather than kept.
     */
    private static boolean convertsWorkbenches() {
        if (!workbenchConfigLost) {
            try {
                Object values = config("fuzs.visualworkbench.VisualWorkbench", "fuzs.visualworkbench.config.ServerConfig");
                return values.getClass().getField("convertVanillaWorkbenchWhenInteracting").getBoolean(values);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                workbenchConfigLost = true;
                HandsOnVanilla.LOGGER.warn("Visual Workbench's server config could not be read; vanilla crafting tables are taken as"
                        + " turned into its own on use", e);
            }
        }
        // Its default.
        return true;
    }

    /** {@code configClass}'s values, through the Puzzles Lib config holder {@code <modClass>.CONFIG} of a mod by Fuzs. */
    private static Object config(String modClass, String configClass) throws ReflectiveOperationException {
        Object holder = Class.forName(modClass).getField("CONFIG").get(null);
        Class<?> type = Class.forName(configClass);
        for (Method method : holder.getClass().getMethods()) {
            if (method.getName().equals("get") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Class.class) {
                return method.invoke(holder, type);
            }
        }
        throw new NoSuchMethodException(modClass + ".CONFIG.get(Class)");
    }

    private static TagKey<Block> tag(String mod, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(mod, path));
    }
}
