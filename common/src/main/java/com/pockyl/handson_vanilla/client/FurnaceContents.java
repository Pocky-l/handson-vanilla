package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What lies in furnaces nobody works at, so the furnace shows it in its mouth and firebox. A server tells a client what is in a
 * furnace only while its menu is open: in singleplayer it is read from the game's own server now and then; on a server it is what
 * the player saw in it last. Kept for the world the player is in.
 */
final class FurnaceContents {
    /** The furnace's slots: what smelts, the fuel, what it became. */
    static final int INPUT = 0;
    static final int FUEL = 1;
    static final int RESULT = 2;

    private static final Map<Key, ItemStack[]> SEEN = new ConcurrentHashMap<>();
    private static Level seenIn;

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private FurnaceContents() {
    }

    /** The stack last known in a slot of the furnace at {@code pos}; empty when nothing is known. */
    static ItemStack get(Level level, BlockPos pos, int slot) {
        forget(level);
        ItemStack[] stacks = SEEN.get(new Key(level.dimension(), pos));
        return stacks == null ? ItemStack.EMPTY : stacks[slot];
    }

    /** What the player sees in it now, at the station. */
    static void put(Level level, BlockPos pos, ItemStack input, ItemStack fuel, ItemStack result) {
        forget(level);
        SEEN.put(new Key(level.dimension(), pos), new ItemStack[] {input.copy(), fuel.copy(), result.copy()});
    }

    /** In singleplayer: asks the game's own server what lies in the furnace, on its thread; the answer comes a tick later. */
    static void ask(Level level, BlockPos pos) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        Key key = new Key(level.dimension(), pos.immutable());
        server.execute(() -> {
            ServerLevel there = server.getLevel(key.dimension());
            if (there != null && there.getBlockEntity(key.pos()) instanceof AbstractFurnaceBlockEntity furnace) {
                SEEN.put(key, new ItemStack[] {furnace.getItem(INPUT).copy(), furnace.getItem(FUEL).copy(), furnace.getItem(RESULT).copy()});
            } else {
                SEEN.remove(key);
            }
        });
    }

    /** Another world (or server) was joined: what was seen in the last one does not lie here. */
    private static void forget(Level level) {
        if (seenIn != level) {
            seenIn = level;
            SEEN.clear();
        }
    }
}
