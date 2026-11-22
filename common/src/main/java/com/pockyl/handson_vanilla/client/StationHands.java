package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.glfw.GLFW;

import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.function.BiConsumer;

/** What the hand-worked stations share: flights from the hotbar, the room a shift-take needs, the keys that leave, sounds. */
final class StationHands {
    /** Seconds an item flies from the inventory onto its place, and the height of its arc, pixels. */
    static final float FLIGHT_TIME = 0.32F;
    static final float FLIGHT_ARC = 2.5F;

    private StationHands() {
    }

    /**
     * Sends a node's item from where it was in the inventory to {@code at} (scene pixels): a hotbar slot from its place on the
     * screen, the rest from the screen's foot.
     */
    static void fly(StationView view, String nodeId, int slot, Vector3fc at) {
        view.node(nodeId).ifPresent(node -> {
            Vector3fc base = node.definition().transform().pos();
            onScene(view, slot, at.y() + 2.0F).map(on -> on.sub(base.x(), base.y(), base.z(), new Vector3f()))
                    .filter(from -> from.length() <= 40.0F).ifPresent(from -> {
                        node.moveTo(from.x, from.y, from.z, 0.0F);
                        node.hopTo(at.x() - base.x(), at.y() - base.y(), at.z() - base.z(), FLIGHT_ARC, FLIGHT_TIME);
                    });
        });
    }

    /**
     * Where inventory slot {@code slot} is on the screen, laid on the scene's level plane at {@code height} (scene pixels): a hotbar
     * slot at its place, the rest at the screen's foot. Things fly between it and the station.
     */
    static java.util.Optional<Vector3f> onScene(StationView view, int slot, float height) {
        var window = Minecraft.getInstance().getWindow();
        float width = window.getGuiScaledWidth();
        float tall = window.getGuiScaledHeight();
        float x = slot >= 0 && slot < Inventory.getSelectionSize() ? (width / 2.0F - 80.0F + 20.0F * slot) / width : 0.5F;
        return view.screenOn(x, (tall - 11.0F) / tall, height);
    }

    /** Whether a shift-take of {@code made} lands anywhere: a free slot, or a stack of it with room. */
    static boolean inventoryHasRoom(ItemStack made) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || made.isEmpty()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        return inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(made) >= 0;
    }

    /** Whether the key leaves the station: Escape or the inventory key. */
    static boolean leaves(int keyCode, int scanCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, 0));
    }

    /** The arm moves as the hand works; only this client sees it, nothing is sent. */
    static void swing() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND, false);
        }
    }

    static void play(StationView view, SoundEvent sound, String node, float volume, float pitch) {
        float scaled = volume * VanillaConfig.soundVolume();
        if (scaled > 0.0F) {
            view.playSound(sound, node != null && view.node(node).isPresent() ? node : null, scaled, pitch);
        }
    }

    static float random(StationView view) {
        return view.level().getRandom().nextFloat();
    }

    /**
     * The counts of a station's slots between ticks, for the sounds of putting and taking by hand: which slot gained, which lost.
     * Forgotten after a station's own take, so the pieces it uses up do not sound as taken.
     */
    static final class SlotWatch {
        private static final int QUIET_TICKS = 3;
        private final String[] nodes;
        private final SlotRef[] slots;
        private final int[] counts;
        private boolean known;
        /** Ticks left that only take the counts as they are: a station's own take is still arriving. */
        private int quiet;

        /** Pairs of node id and slot, as the scene names it ({@code inputs[1]}; a bare name is its group's first). */
        SlotWatch(String... nodeAndSlot) {
            nodes = new String[nodeAndSlot.length / 2];
            slots = new SlotRef[nodes.length];
            counts = new int[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = nodeAndSlot[2 * i];
                String slot = nodeAndSlot[2 * i + 1];
                int open = slot.indexOf('[');
                slots[i] = open < 0 ? SlotRef.of(slot, 0)
                        : SlotRef.of(slot.substring(0, open), Integer.parseInt(slot.substring(open + 1, slot.length() - 1)));
            }
        }

        void forget() {
            known = false;
            quiet = QUIET_TICKS;
        }

        /** Calls back with the node and +1 (more there) or -1 (less there) for each slot that changed since the last tick. */
        void tick(StationView view, BiConsumer<String, Integer> changed) {
            if (!view.isFocused() || view.menu() == null) {
                known = false;
                return;
            }
            for (int i = 0; i < slots.length; i++) {
                int now = view.state().getItem(slots[i]).getCount();
                if (known && quiet == 0 && now != counts[i]) {
                    changed.accept(nodes[i], now > counts[i] ? 1 : -1);
                }
                counts[i] = now;
            }
            known = true;
            quiet = Math.max(0, quiet - 1);
        }
    }
}
