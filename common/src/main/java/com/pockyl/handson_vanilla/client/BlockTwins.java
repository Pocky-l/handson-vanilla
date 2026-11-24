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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Easy Magic, Easy Anvils and Visual Workbench give every enchanting table, anvil and crafting table a twin block of their own, with
 * their block entity, named {@code <mod>:<namespace>/<path>} ({@code easymagic:minecraft/enchanting_table},
 * {@code easyanvils:minecraft/anvil}, {@code visualworkbench:minecraft/crafting_table}): the item places the twin, and vanilla's
 * block, unless it is in their "unaltered" tag, opens nothing when used. Easy Magic's and Easy Anvils' 1.21.1 builds answer that use
 * with "invalid block" (unless their common config allows vanilla's block); their later builds and Visual Workbench (1.21.11 on,
 * classes under {@code fuzs.<mod>.common} from Minecraft 26.x) turn the block into the twin instead (unless their server config says
 * no). The stations open on the twins; on such a vanilla block they stay closed, so the game's own answer comes as without this mod,
 * and the next use opens the twin. Reached by name and reflection, with no compile dependency.
 */
final class BlockTwins {
    static final String EASY_MAGIC = "easymagic";
    static final String EASY_ANVILS = "easyanvils";
    static final String VISUAL_WORKBENCH = "visualworkbench";

    private static final Map<String, Boolean> DISABLED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PRESENT = new ConcurrentHashMap<>();
    private static volatile Set<Block> workbenches;
    private static volatile boolean workbenchConfigLost;

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

    /**
     * Every crafting table Visual Workbench made a twin of, vanilla's and other mods', as those twins; empty without it. Looked up
     * once, when the crafting table station first needs it, long after every block is registered.
     */
    static Set<Block> workbenches() {
        Set<Block> found = workbenches;
        if (found == null) {
            found = BuiltInRegistries.BLOCK.entrySet().stream()
                    .filter(entry -> isTwinName(entry.getKey().identifier(), VISUAL_WORKBENCH))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toUnmodifiableSet());
            workbenches = found;
        }
        return found;
    }

    /** Whether the crafting table station works at this block: vanilla's crafting table, or Visual Workbench's twin of any table. */
    static boolean craftingTable(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE) || workbenches().contains(state.getBlock());
    }

    /** The vanilla block {@code state} stands for: itself, or the one an Easy Magic, Easy Anvils or Visual Workbench twin was made from. */
    static Identifier original(BlockState state) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (isTwinName(key, EASY_MAGIC) || isTwinName(key, EASY_ANVILS) || isTwinName(key, VISUAL_WORKBENCH)) {
            int slash = key.getPath().indexOf('/');
            return Identifier.fromNamespaceAndPath(key.getPath().substring(0, slash), key.getPath().substring(slash + 1));
        }
        return key;
    }

    private static boolean isTwinName(Identifier key, String mod) {
        return key.getNamespace().equals(mod) && key.getPath().contains("/");
    }

    /**
     * Whether a use of this vanilla crafting table, enchanting table or anvil opens no menu because Visual Workbench, Easy Magic or
     * Easy Anvils took it over: refused (Easy Magic and Easy Anvils on 1.21.1) or turned into their twin.
     */
    static boolean refused(BlockState state) {
        if (state.is(Blocks.CRAFTING_TABLE)) {
            return twin(VISUAL_WORKBENCH, Blocks.CRAFTING_TABLE).get() != Blocks.CRAFTING_TABLE
                    && !state.is(tag(VISUAL_WORKBENCH, "unaltered_workbenches")) && convertsWorkbenches();
        }
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
        if (state.is(tag(mod, unaltered))) {
            return false;
        }
        return DISABLED.computeIfAbsent(mod, key -> {
            try {
                // 1.21.1: CommonConfig.disableVanilla*; later: ServerConfig.convertVanilla*WhenInteracting, under .common on 26.x.
                String base = base(mod, modClass);
                boolean old = present(base + "config.CommonConfig");
                Class<?> type = Class.forName(base + (old ? "config.CommonConfig" : "config.ServerConfig"));
                Field flag = type.getField(old ? disable : convert);
                return flag.getBoolean(config(base + modClass, type));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("{}'s config could not be read; its vanilla blocks are taken as refused", mod, e);
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
                String base = base(VISUAL_WORKBENCH, "VisualWorkbench");
                Class<?> type = Class.forName(base + "config.ServerConfig");
                return type.getField("convertVanillaWorkbenchWhenInteracting").getBoolean(config(base + "VisualWorkbench", type));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                workbenchConfigLost = true;
                HandsOnVanilla.LOGGER.warn("Visual Workbench's server config could not be read; vanilla crafting tables are taken as"
                        + " turned into its own on use", e);
            }
        }
        // Its default.
        return true;
    }

    private static TagKey<Block> tag(String mod, String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(mod, path));
    }

    /** The package of a mod by Fuzs: {@code fuzs.<mod>.common.} from Minecraft 26.x, {@code fuzs.<mod>.} before. */
    private static String base(String mod, String modClass) {
        return present("fuzs." + mod + ".common." + modClass) ? "fuzs." + mod + ".common." : "fuzs." + mod + ".";
    }

    /** The values of config class {@code type}, through the Puzzles Lib config holder {@code <modClass>.CONFIG}. */
    private static Object config(String modClass, Class<?> type) throws ReflectiveOperationException {
        Object holder = Class.forName(modClass).getField("CONFIG").get(null);
        for (Method method : holder.getClass().getMethods()) {
            if (method.getName().equals("get") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Class.class) {
                return method.invoke(holder, type);
            }
        }
        throw new NoSuchMethodException(modClass + ".CONFIG.get(Class)");
    }
}
