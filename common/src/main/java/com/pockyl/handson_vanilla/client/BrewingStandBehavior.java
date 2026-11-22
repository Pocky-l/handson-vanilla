package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.List;

/**
 * The brewing stand worked by hand, from whichever side the player comes. Its three bottles stand on the stand's own pads, the
 * ingredient on top of the rod, the blaze powder on the ground by its foot; every vanilla click works on them. The brew shows on
 * the stand itself: the ingredient shrinks as the brew goes on, drops fall from it towards the bottles, bubbles of each potion's
 * colour rise in them and a small flame burns at the rod's foot; when it is done the potions in the bottles are the new ones
 * (vanilla's finishing sound is the server's). What the hand held on opening goes to its place by itself. All of it is local.
 */
final class BrewingStandBehavior implements StationBehavior {
    static final String BOTTLES = "bottles";
    static final String INGREDIENT = "ingredient";
    static final String FUEL = "fuel";
    private static final String BODY = "body";
    private static final String[] BOTTLE_NODES = {"bottle_0", "bottle_1", "bottle_2"};
    /** Ticks a brew takes in vanilla: the brewing time counts down from this. */
    private static final float BREW_TICKS = 400.0F;

    /** Where the blaze powder may lie (the stand's own frame, as seen from its north side): the free corners round the pads. */
    private static final Vector3fc[] FUEL_PLACES = {
            new Vector3f(11.5F, 0.02F, 2.5F), new Vector3f(11.5F, 0.02F, 13.5F), new Vector3f(3.5F, 0.02F, 8.0F)};
    /** The ingredient at the end of a brew, as a share of its size. */
    private static final float SHRUNK = 0.35F;
    /** Seconds between bubbles, drops and flames while it brews. */
    private static final float BUBBLE_EVERY = 0.12F;
    private static final float DROP_EVERY = 0.7F;
    private static final float FLAME_EVERY = 0.18F;
    private static final int FLAME_COLOR = 0xFFFFB050;

    /** How the stand stands in the scene (a turn about y), and where its bottles, rod top and rod foot are now. */
    private float yaw;
    private final Vector3f[] bottles = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f rodTop = new Vector3f(8.0F, 14.0F, 8.0F);
    private final Vector3f rodFoot = new Vector3f(8.0F, 2.0F, 6.8F);
    private float bubbleIn;
    private float dropIn;
    private float flameIn;
    private float shownScale = 1.0F;
    private int heldSlot = -1;

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(stack -> usable(view, stack));
        // An empty foot shows a water bottle, as the potions start from one: a scene file cannot name a potion's contents.
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        for (int n = 0; n < 3; n++) {
            view.node("bottle_" + n).ifPresent(node -> node.setGhostItems(List.of(water)));
        }
    }

    /** What the stand takes, by the vanilla menu's own slots (on Forge they also know modded brewing recipes). */
    private static boolean usable(StationView view, ItemStack stack) {
        BrewingStandMenu menu = brewing(view);
        if (stack.isEmpty() || menu == null) {
            return false;
        }
        for (int slot = 0; slot < 5; slot++) {
            if (menu.getSlot(slot).mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onOpen(StationView view) {
        heldSlot = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && !player.getMainHandItem().isEmpty()) {
            heldSlot = player.getInventory().getSelectedSlot();
        }
        yaw = ((int) view.facing().toYRot() + 180) % 360;
        Matrix3f turn = new Matrix3f().rotationY((float) Math.toRadians(yaw));
        view.node(BODY).ifPresent(node -> node.rotateTo(0.0F, yaw, 0.0F, 0.0F));
        for (int i = 0; i < BOTTLE_NODES.length; i++) {
            Vector3f bottle = bottles[i];
            view.node(BOTTLE_NODES[i]).ifPresent(node -> bottle.set(place(node, turn)));
        }
        // The blaze powder lies in the free corner nearest the camera.
        Vector3f fuel = null;
        for (Vector3fc candidate : FUEL_PLACES) {
            Vector3f turned = turned(candidate, turn);
            if (fuel == null || turned.z < fuel.z - 0.5F) {
                fuel = turned;
            }
        }
        Vector3f fuelAt = fuel;
        view.node(FUEL).ifPresent(node -> {
            Vector3fc rest = node.definition().transform().pos();
            node.moveTo(fuelAt.x - rest.x(), fuelAt.y - rest.y(), fuelAt.z - rest.z(), 0.0F);
        });
        shownScale = 1.0F;
        view.node(INGREDIENT).ifPresent(node -> node.scaleTo(1.0F, 0.0F));
    }

    /** A point of the stand's own frame turned about the block's middle into the scene. */
    private static Vector3f turned(Vector3fc point, Matrix3f turn) {
        return turn.transform(new Vector3f(point).sub(8.0F, 0.0F, 8.0F)).add(8.0F, 0.0F, 8.0F);
    }

    /** Moves a node from its place in the scene file to the same place on the turned stand, and says where that is. */
    private static Vector3f place(com.pockyl.diegesis.api.client.NodeView node, Matrix3f turn) {
        Vector3fc rest = node.definition().transform().pos();
        Vector3f at = turned(rest, turn);
        node.moveTo(at.x - rest.x(), at.y - rest.y(), at.z - rest.z(), 0.0F);
        return at;
    }

    @Override
    public void onMenuOpened(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        MenuAccess menu = view.menu();
        if (heldSlot >= 0 && player != null && menu != null && usable(view, player.getInventory().getItem(heldSlot))) {
            // One vanilla shift-click of the held stack: the menu's own rule sends it onto a pad, the rod or the ground.
            menu.quickMoveFromInventory(heldSlot);
        }
        heldSlot = -1;
    }

    @Override
    public void frame(StationView view, float dt) {
        view.node(BODY).ifPresent(node -> node.setVisible(view.isBlockHidden()));
        BrewingStandMenu menu = brewing(view);
        int ticks = menu == null ? 0 : menu.getBrewingTicks();
        boolean brewing = ticks > 0;
        float progress = brewing ? 1.0F - ticks / BREW_TICKS : 0.0F;
        float scale = 1.0F - (1.0F - SHRUNK) * Math.max(0.0F, Math.min(1.0F, progress));
        if (Math.abs(scale - shownScale) > 0.01F) {
            // Grows back at once for the next ingredient; shrinks smoothly between the ticks of the brew.
            float seconds = scale > shownScale ? 0.15F : 0.3F;
            shownScale = scale;
            view.node(INGREDIENT).ifPresent(node -> node.scaleTo(scale, seconds));
        }
        if (!brewing) {
            return;
        }
        RandomSource random = view.level().getRandom();
        bubbleIn -= dt;
        dropIn -= dt;
        flameIn -= dt;
        if (bubbleIn <= 0.0F) {
            bubbleIn = BUBBLE_EVERY;
            int i = random.nextInt(3);
            ItemStack bottle = view.state().getItem(SlotRef.of(BOTTLES, i));
            if (!bottle.isEmpty()) {
                Vector3f at = new Vector3f(bottles[i]).add((random.nextFloat() - 0.5F) * 0.8F, 1.0F, (random.nextFloat() - 0.5F) * 0.8F);
                view.spawnParticles(DiegesisParticles.BUBBLE, at, new Vector3f(0.0F, 2.5F, 0.0F), 0.4F, 1,
                        0xFF000000 | bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor());
            }
        }
        if (dropIn <= 0.0F) {
            dropIn = DROP_EVERY;
            int i = random.nextInt(3);
            ItemStack bottle = view.state().getItem(SlotRef.of(BOTTLES, i));
            if (!bottle.isEmpty()) {
                Vector3f toward = new Vector3f(bottles[i]).sub(rodTop.x, 0.0F, rodTop.z).mul(1.0F, 0.0F, 1.0F).normalize(2.5F);
                view.spawnParticles(DiegesisParticles.DROPLET, new Vector3f(rodTop).add(0.0F, 0.5F, 0.0F), toward.add(0.0F, 1.0F, 0.0F),
                        0.3F, 1, 0xFF000000 | bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor());
            }
        }
        if (flameIn <= 0.0F) {
            flameIn = FLAME_EVERY;
            view.spawnParticles(DiegesisParticles.FLAME, rodFoot, new Vector3f(0.0F, 1.5F, 0.0F), 0.3F, 1, FLAME_COLOR);
        }
    }

    private static BrewingStandMenu brewing(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof BrewingStandMenu stand ? stand : null;
    }
}
