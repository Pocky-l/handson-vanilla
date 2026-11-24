package com.pockyl.handson_vanilla.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Visual Workbench on 1.20.1 keeps every crafting table the block it is: its mixin gives {@code CraftingTableBlock} its block entity
 * and menu for each table listed in its {@code config/visual_workbench.json} (vanilla's and, by default, several other mods' tables),
 * which its interface {@code VisualCraftingTableBlock#hasBlockEntity()} tells. The crafting table station works at all of those.
 * Reached by reflection, with no compile dependency.
 */
public final class CraftingTables {
    private static final String VISUAL_TABLE = "fuzs.visualworkbench.world.level.block.VisualCraftingTableBlock";

    private static volatile Set<Block> visualWorkbench;

    private CraftingTables() {
    }

    /**
     * Every crafting table Visual Workbench serves, vanilla's and other mods'; empty without it. Looked up once, when the crafting
     * table station first needs it, long after every block is registered and its list is read.
     */
    static Set<Block> visualWorkbench() {
        Set<Block> found = visualWorkbench;
        if (found == null) {
            found = find();
            visualWorkbench = found;
        }
        return found;
    }

    /** Whether the crafting table station works at this block: vanilla's crafting table, or any table Visual Workbench serves. */
    public static boolean craftingTable(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE) || visualWorkbench().contains(state.getBlock());
    }

    private static Set<Block> find() {
        Class<?> marker;
        Method served;
        try {
            marker = Class.forName(VISUAL_TABLE, false, CraftingTables.class.getClassLoader());
            served = marker.getMethod("hasBlockEntity");
        } catch (ClassNotFoundException e) {
            return Set.of();
        } catch (ReflectiveOperationException | LinkageError e) {
            HandsOnVanilla.LOGGER.warn("Visual Workbench's tables could not be read; only vanilla's crafting table is worked by hand", e);
            return Set.of();
        }
        Set<Block> found = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!marker.isInstance(block)) {
                continue;
            }
            try {
                if ((Boolean) served.invoke(block)) {
                    found.add(block);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Visual Workbench could not say whether it serves {}", BuiltInRegistries.BLOCK.getKey(block), e);
            }
        }
        return Set.copyOf(found);
    }
}
