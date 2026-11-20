package com.pockyl.handson_vanilla.client;

import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The vanilla blocks this mod remodels in the world, through the built-in pack ({@code furnace_pack}, fixed above every resource
 * pack on Forge, read straight from the mod on Fabric): the furnace family, whose hollows must be where the station's slots, flames
 * and doors are. Each only while its station is on: turned off, the block is vanilla's, or the resource pack's, again.
 */
public final class RemodelledBlocks {
    /**
     * One remodelled block: its id (the block state file's name), the block, whether its station is on, the models the pack serves
     * for it ({@code models/block/<name>.json}), and whether the model has holes (drawn cut out).
     */
    public record Entry(String id, Block block, BooleanSupplier on, List<String> models, boolean cutout) {
    }

    private RemodelledBlocks() {
    }

    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        for (FurnaceKind kind : FurnaceKind.values()) {
            entries.add(new Entry(kind.id(), kind.block(), kind::on, List.of(kind.id(), kind.id() + "_on"), true));
        }
        return entries;
    }

    public static boolean anyOn() {
        return all().stream().anyMatch(entry -> entry.on().getAsBoolean());
    }

    /**
     * Whether a file of the built-in pack ({@code blockstates/smoker.json}, {@code models/block/smoker_on.json}) is served: only those
     * of blocks whose station is on.
     */
    public static boolean served(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".json")) {
            name = name.substring(0, name.length() - ".json".length());
        }
        for (Entry entry : all()) {
            if (name.equals(entry.id()) || entry.models().contains(name)) {
                return entry.on().getAsBoolean();
            }
        }
        return false;
    }
}
