package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The smithing table worked by hand. The hammer painted on the table's side is a real one at the station: it comes off its hook
 * onto the iron plate while the camera flies in, and goes back on leaving. Template, gear and material lie on the plate in vanilla's
 * order; what they make hovers beside them, growing in when it appears, and the hammer stirs. A click on the hammer or on what
 * hovers strikes: the hammer swings up over the gear and down onto it, the template and the material are pressed onto the gear,
 * sparks fly, the head bounces back, and at the blow the result is taken with the vanilla click on the result slot (plain: onto the
 * cursor; shift: into the inventory). What was made rises off the plate for a moment, glinting in the trim's colour. Vanilla's own
 * smithing sound comes from the server with the take; a quieter knock of its own lands at once. Clicks during a strike strike again
 * after it. With nothing to make, or no room for it, the hammer only knocks once, lightly. All of it is local.
 */
final class SmithingTableBehavior implements StationBehavior {
    static final String TEMPLATE = "template";
    static final String BASE = "base";
    static final String ADDITION = "addition";
    private static final String BODY = "body";
    private static final String HAMMER = "hammer";
    private static final String RESULT = "result";
    private static final String FORGED = "forged";
    /** The vanilla menu's slots: template, base, addition, result. */
    private static final int BASE_SLOT = 1;
    private static final int RESULT_SLOT = 3;

    /** Where the hammer lies on the plate (its grip's end, as the scene places the node): on its side along the far edge. */
    private static final Vector3fc REST = new Vector3f(0.5F, 17.5F, 12.5F);
    private static final Vector3fc REST_TURN = new Vector3f(90.0F, 0.0F, 0.0F);
    /**
     * Where it hangs on the side whose painting it is: the scene's right side when the player came to the table's front or back
     * (body as it is), its front when to a side (body turned a quarter). The handle is pixel column 5 of the side texture, from 3 to
     * 11 high, the head 1.5 pixels out of the wall.
     */
    private static final Vector3fc HANG_RIGHT = new Vector3f(-1.5F, 3.0F, 5.5F);
    private static final Vector3fc HANG_RIGHT_TURN = new Vector3f(0.0F, 90.0F, 90.0F);
    private static final Vector3fc HANG_FRONT = new Vector3f(10.5F, 3.0F, -1.5F);
    private static final Vector3fc HANG_FRONT_TURN = new Vector3f(0.0F, 0.0F, 90.0F);
    private static final float BODY_FRONT_TURN = -90.0F;
    /** The gear's place on the plate; the hammer's head, 5.5 pixels from the grip's end, comes down on it. */
    private static final Vector3fc ANVIL = new Vector3f(8.5F, 16.0F, 5.5F);
    private static final float HEAD_REACH = 5.5F;
    /** Half the head's height under the handle, and how high the gear lies: the face touches the gear at the blow. */
    private static final float HEAD_HALF = 2.5F;
    private static final float GEAR_TOP = 0.6F;
    /** The raised hammer: its grip a little higher, the head up by this turn. */
    private static final float RAISE_LIFT = 1.5F;
    private static final float RAISE_TURN = 65.0F;

    /** Seconds: off the hook (after a short wait), back on it, up over the gear, down, the ring after the blow, back to the plate. */
    private static final float TAKE_WAIT = 0.1F;
    private static final float TAKE = 0.4F;
    private static final float HANG_BACK = 0.3F;
    private static final float RAISE = 0.16F;
    private static final float RAISE_AGAIN = 0.12F;
    private static final float DOWN = 0.07F;
    private static final float RING = 0.1F;
    private static final float LAY_DOWN = 0.3F;
    /** The head bounces up off the gear after the blow: degrees, and the seconds up and back. */
    private static final float RECOIL = 10.0F;
    private static final float RECOIL_UP = 0.04F;
    private static final float RECOIL_BACK = 0.06F;
    /** How high the hammer goes in its arcs, pixels. */
    private static final float ARC = 3.0F;
    /** Picks kept during a strike, to be struck after it. */
    private static final int MOST_QUEUED = 8;
    /** The result hovering, and the gear as it lies on the plate (for what was made rising off it after the blow). */
    private static final float RESULT_SIZE = 5.0F;
    private static final float GEAR_SIZE = 4.4F;
    private static final int SPARK_COLOR = 0xFFFFC870;
    /** Glints off what was made: how many, and their colour for an upgrade (a trim takes its material's). */
    private static final int GLINTS = 6;
    private static final int UPGRADE_GLINT = 0xFFB8ACB2;

    private enum Phase { HANGING, TAKING, LYING, RAISING, STRIKING, RINGING, LAYING, HANGING_BACK }

    private Phase phase = Phase.HANGING;
    private float phaseTime;
    private float clock;
    /** Whether the hammer's side is the scene's front (the player came to the table's side). */
    private boolean front;
    private int heldSlot = -1;
    private boolean strikeAll;
    /** Whether the strike under way follows another without the hammer lying down between. */
    private boolean strikeAgain;
    private int queued;
    private boolean recoiled = true;
    /** The inventory slot whose stack went onto the plate by itself, to fly from there when it lands; -1: none. */
    private int flyFrom = -1;
    /** Whether the plate had something to make last frame: a new result stirs the hammer. */
    private boolean hadResult;
    /** What the last blow made, rising off the plate, and the station's clock at the blow. */
    private ItemStack forged = ItemStack.EMPTY;
    private float forgedAt;
    private final HoveringResult result = new HoveringResult(RESULT_SIZE);
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(TEMPLATE, "template", BASE, "base", ADDITION, "addition");
    /** With Polymorph: what else the plate's pieces make lies beside the result, a click makes it the result. */
    private final RecipeChoice choice = new RecipeChoice(false, 2.4F, 35.0F);
    /** The pieces and the result the choices were read for: read again only when one changes. */
    private ItemStack[] choicePieces = new ItemStack[0];
    private ItemStack choiceResult = ItemStack.EMPTY;

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(SmithingUse::usable);
        choice.onSceneReady(view);
        view.node(RESULT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> {
            SmithingMenu menu = smithing(view);
            ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
            if (phase == Phase.STRIKING || phase == Phase.RINGING && phaseTime < RING) {
                return;
            }
            result.paint(view.level(), made, clock, poseStack, buffers, light);
        }));
        view.node(FORGED).ifPresent(node -> node.setPainter((poseStack, buffers, light) ->
                HoveringResult.paintForged(view.level(), forged, clock - forgedAt, GEAR_SIZE, poseStack, buffers, light)));
    }

    @Override
    public void onOpen(StationView view) {
        SmithingUse.refresh();
        heldSlot = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && SmithingUse.usable(player.getMainHandItem())) {
            heldSlot = player.getInventory().selected;
        }
        flyFrom = -1;
        forged = ItemStack.EMPTY;
        watch.forget();
        // The painted hammers are on the table's east and west sides, the tongs on its north and south.
        front = view.facing().getAxis() == net.minecraft.core.Direction.Axis.X;
        view.node(BODY).ifPresent(node -> node.rotateTo(0.0F, front ? BODY_FRONT_TURN : 0.0F, 0.0F, 0.0F));
        queued = 0;
        if (phase == Phase.HANGING || phase == Phase.HANGING_BACK) {
            pose(view, hang(), hangTurn(), 0.0F, false);
            phase = Phase.HANGING;
        }
        phaseTime = 0.0F;
    }

    @Override
    public void onMenuOpened(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        MenuAccess menu = view.menu();
        if (heldSlot >= 0 && player != null && menu != null && SmithingUse.usable(player.getInventory().getItem(heldSlot))) {
            // One vanilla shift-click of the held stack: the menu's own rule sends it to its place on the plate.
            menu.quickMoveFromInventory(heldSlot);
            flyFrom = heldSlot;
        }
        heldSlot = -1;
    }

    @Override
    public void onClose(StationView view) {
        queued = 0;
        choice.clear(view);
        choicePieces = new ItemStack[0];
        press(view, false, 0.0F);
        pose(view, hang(), hangTurn(), HANG_BACK, true);
        to(Phase.HANGING_BACK);
    }

    /** The hammer is still going back onto its hook after the player left: the scene keeps standing in for the block until it is. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && phase == Phase.HANGING_BACK;
    }

    /** Pieces put onto the plate and taken off it by hand sound; the one put by itself on opening flies there from the hotbar. */
    @Override
    public void tick(StationView view) {
        choices(view);
        watch.tick(view, (node, change) -> {
            if (change > 0) {
                if (flyFrom >= 0) {
                    int from = flyFrom;
                    view.node(node).ifPresent(slot -> StationHands.fly(view, node, from, slot.definition().transform().pos()));
                    flyFrom = -1;
                }
                StationHands.play(view, SoundEvents.ANVIL_PLACE, node, 0.08F, 1.7F + 0.2F * StationHands.random(view));
            } else {
                StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, node, 0.3F, 0.9F + 0.2F * StationHands.random(view));
            }
        });
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        phaseTime += dt;
        boolean shown = view.isBlockHidden();
        view.node(BODY).ifPresent(node -> node.setVisible(shown));
        view.node(HAMMER).ifPresent(node -> node.setVisible(shown));
        view.node(FORGED).ifPresent(node -> node.setVisible(!forged.isEmpty() && clock - forgedAt < HoveringResult.FORGED));
        SmithingMenu menu = smithing(view);
        ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
        boolean drawn = !(phase == Phase.STRIKING || phase == Phase.RINGING && phaseTime < RING);
        view.node(RESULT).ifPresent(node -> result.frame(node, made, drawn, dt));
        boolean hasResult = menu != null && view.isInUse() && menu.getSlot(RESULT_SLOT).hasItem();
        if (hasResult && !hadResult && phase == Phase.LYING) {
            // Something to make: the hammer, the thing to click, stirs where it lies.
            view.node(HAMMER).ifPresent(node -> node.shake(0.15F, 0.15F));
        }
        hadResult = hasResult;
        switch (phase) {
            case HANGING -> {
                if (view.isInUse() && phaseTime >= TAKE_WAIT) {
                    pose(view, REST, REST_TURN, TAKE, true);
                    to(Phase.TAKING);
                    StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, HAMMER, 0.25F, 0.8F);
                }
            }
            case TAKING -> {
                if (phaseTime >= TAKE) {
                    to(Phase.LYING);
                }
            }
            case RAISING -> {
                if (phaseTime >= (strikeAgain ? RAISE_AGAIN : RAISE)) {
                    pose(view, struck(), new Vector3f(), DOWN, false);
                    to(Phase.STRIKING);
                }
            }
            case STRIKING -> {
                if (phaseTime >= DOWN) {
                    blow(view);
                    to(Phase.RINGING);
                }
            }
            case RINGING -> {
                if (!recoiled && phaseTime >= RECOIL_UP) {
                    recoiled = true;
                    view.node(HAMMER).ifPresent(node -> node.rotateTo(0.0F, 0.0F, 0.0F, RECOIL_BACK));
                }
                if (phaseTime >= RING) {
                    if (queued > 0 && canTake(view, false)) {
                        queued--;
                        raise(view, true);
                    } else {
                        queued = 0;
                        pose(view, REST, REST_TURN, LAY_DOWN, true);
                        to(Phase.LAYING);
                    }
                }
            }
            case LAYING -> {
                if (phaseTime >= LAY_DOWN) {
                    to(Phase.LYING);
                }
            }
            case HANGING_BACK -> {
                if (phaseTime >= HANG_BACK) {
                    to(Phase.HANGING);
                    StationHands.play(view, SoundEvents.ITEM_FRAME_ADD_ITEM, HAMMER, 0.25F, 0.8F);
                }
            }
            default -> {
            }
        }
    }

    /** A click on the hammer or on the result: a strike, or after the one under way. */
    private void strike(StationView view, boolean all) {
        if (phase == Phase.RAISING || phase == Phase.STRIKING || phase == Phase.RINGING) {
            if (!all && !strikeAll) {
                queued = Math.min(MOST_QUEUED, queued + 1);
            }
            return;
        }
        if (phase != Phase.LYING && phase != Phase.LAYING && phase != Phase.TAKING) {
            return;
        }
        if (!canTake(view, all)) {
            // Nothing to make, or no room for it on the cursor or in the inventory: one light knock where it lies.
            view.node(HAMMER).ifPresent(node -> node.shake(0.35F, 0.2F));
            StationHands.play(view, SoundEvents.ANVIL_LAND, HAMMER, 0.08F, 1.9F);
            return;
        }
        strikeAll = all;
        raise(view, false);
    }

    /** Up over the gear, the head raised; the template and the material slide onto the gear meanwhile. */
    private void raise(StationView view, boolean again) {
        strikeAgain = again;
        Vector3f over = struck().add(0.0F, RAISE_LIFT, 0.0F);
        pose(view, over, new Vector3f(0.0F, 0.0F, RAISE_TURN), again ? RAISE_AGAIN : RAISE, !again);
        press(view, true, again ? RAISE_AGAIN : RAISE);
        to(Phase.RAISING);
    }

    /**
     * The blow: the vanilla click on the result, a knock of its own at once (the server's sound comes with the take, a ping later),
     * what was made rising off the gear and glinting, sparks, the head bouncing up, the arm swings.
     */
    private void blow(StationView view) {
        MenuAccess menu = view.menu();
        SmithingMenu smithing = smithing(view);
        Vector3f at = new Vector3f(ANVIL).add(0.0F, GEAR_TOP, 0.0F);
        if (menu != null && smithing != null && canTake(view, strikeAll)) {
            ItemStack made = smithing.getSlot(RESULT_SLOT).getItem().copy();
            int glint = glint(view, made, smithing.getSlot(BASE_SLOT).getItem());
            menu.click(RESULT_SLOT, 0, strikeAll ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            // The pieces used up are the strike's, not taken by hand.
            watch.forget();
            forged = made;
            forgedAt = clock;
            view.spawnParticles(DiegesisParticles.GLINT, new Vector3f(at).add(0.0F, 2.0F, 0.0F), new Vector3f(0.0F, 4.0F, 0.0F), 3.0F,
                    GLINTS, glint);
        }
        StationHands.play(view, SoundEvents.NETHERITE_BLOCK_HIT, BASE, 0.35F, 1.2F);
        press(view, false, 0.0F);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(0.0F, 14.0F, 0.0F), 10.0F, strikeAll ? 20 : 14, SPARK_COLOR);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(12.0F, 4.0F, 0.0F), 4.0F, 2, SPARK_COLOR);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(-12.0F, 4.0F, 0.0F), 4.0F, 2, SPARK_COLOR);
        view.node(BASE).ifPresent(node -> node.shake(0.35F, 0.12F));
        view.node(HAMMER).ifPresent(node -> node.rotateTo(0.0F, 0.0F, RECOIL, RECOIL_UP));
        recoiled = false;
        StationHands.swing();
    }

    /** The glints' colour: the trim's material when the gear gains or changes its trim; otherwise the upgrade's. */
    private static int glint(StationView view, ItemStack made, ItemStack gear) {
        var access = view.level().registryAccess();
        Optional<ArmorTrim> trim = ArmorTrim.getTrim(access, made);
        if (trim.isPresent() && !trim.equals(ArmorTrim.getTrim(access, gear))) {
            TextColor color = trim.get().material().value().description().getStyle().getColor();
            if (color != null) {
                return 0xFF000000 | color.getValue();
            }
        }
        return UPGRADE_GLINT;
    }

    /** Whether a strike now takes something: a result, and room for it on the cursor (plain click) or in the inventory (shift). */
    private boolean canTake(StationView view, boolean all) {
        SmithingMenu menu = smithing(view);
        if (menu == null) {
            return false;
        }
        ItemStack made = menu.getSlot(RESULT_SLOT).getItem();
        if (made.isEmpty()) {
            return false;
        }
        if (all) {
            return StationHands.inventoryHasRoom(made);
        }
        ItemStack held = menu.getCarried();
        return held.isEmpty()
                || ItemStack.isSameItemSameTags(held, made) && held.getCount() + made.getCount() <= held.getMaxStackSize();
    }

    /** The template and the material slide onto the gear for the blow ({@code onto}), or are back in their places at once. */
    private static void press(StationView view, boolean onto, float seconds) {
        for (String id : new String[] {TEMPLATE, ADDITION}) {
            view.node(id).ifPresent(node -> {
                Vector3fc rest = node.definition().transform().pos();
                if (onto) {
                    node.moveTo(ANVIL.x() - rest.x(), GEAR_TOP * 0.5F, ANVIL.z() - rest.z(), seconds);
                } else {
                    node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
                }
            });
        }
    }

    /** Where the grip's end is at the blow: the head's face down on the gear. */
    private static Vector3f struck() {
        return new Vector3f(ANVIL.x() - HEAD_REACH, ANVIL.y() + GEAR_TOP + HEAD_HALF, ANVIL.z());
    }

    private Vector3fc hang() {
        return front ? HANG_FRONT : HANG_RIGHT;
    }

    private Vector3fc hangTurn() {
        return front ? HANG_FRONT_TURN : HANG_RIGHT_TURN;
    }

    /** Moves the hammer to a place and turn of the scene, in an arc ({@code hop}) or straight, over {@code seconds}. */
    private static void pose(StationView view, Vector3fc at, Vector3fc turn, float seconds, boolean hop) {
        view.node(HAMMER).ifPresent(node -> {
            Vector3fc base = node.definition().transform().pos();
            float x = at.x() - base.x();
            float y = at.y() - base.y();
            float z = at.z() - base.z();
            if (hop && seconds > 0.0F) {
                node.hopTo(x, y, z, ARC, seconds);
            } else {
                node.moveTo(x, y, z, seconds);
            }
            node.rotateTo(turn.x(), turn.y(), turn.z(), seconds);
        });
    }

    /** With Polymorph, what else the pieces make lies beside the result: read once a tick, again only when something changes. */
    private void choices(StationView view) {
        SmithingMenu menu = smithing(view);
        if (!RecipeChoice.available() || menu == null || !view.isFocused()) {
            choice.clear(view);
            choicePieces = new ItemStack[0];
            return;
        }
        ItemStack[] pieces = {view.state().getItem(SlotRef.of(TEMPLATE, 0)), view.state().getItem(SlotRef.of(BASE, 0)),
                view.state().getItem(SlotRef.of(ADDITION, 0))};
        ItemStack shown = menu.getSlot(RESULT_SLOT).getItem();
        if (RecipeChoice.sameStacks(pieces, choicePieces) && ItemStack.matches(shown, choiceResult)) {
            return;
        }
        choicePieces = java.util.Arrays.stream(pieces).map(ItemStack::copy).toArray(ItemStack[]::new);
        choiceResult = shown.copy();
        SimpleContainer inputs = new SimpleContainer(pieces[0].copy(), pieces[1].copy(), pieces[2].copy());
        Level level = view.level();
        List<RecipeChoice.Option> options = new ArrayList<>();
        for (SmithingRecipe recipe : level.getRecipeManager().getRecipesFor(RecipeType.SMITHING, inputs, level)) {
            options.add(new RecipeChoice.Option(recipe.getId(), recipe.assemble(inputs, level.registryAccess())));
        }
        choice.show(view, options, shown);
    }

    private void to(Phase next) {
        phase = next;
        phaseTime = 0.0F;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && (HAMMER.equals(click.node()) || RESULT.equals(click.node()))) {
            strike(view, Screen.hasShiftDown());
        } else if (event instanceof SceneEvent.Click click && RecipeChoice.place(click.node()) >= 0) {
            choice.click(view, RecipeChoice.place(click.node()));
        }
    }

    /** A stack in the hand: the strike puts the result onto it when it can; nothing else happens to it there. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (HAMMER.equals(node) || RESULT.equals(node)) {
            strike(view, shift);
            return true;
        }
        if (RecipeChoice.place(node) >= 0) {
            // A choice is made with the hand full too: what is held stays held.
            choice.click(view, RecipeChoice.place(node));
            return true;
        }
        return false;
    }

    /** Leaving while the hammer is on its way down: the blow lands now, so the click does not go with the menu. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (StationHands.leaves(keyCode, scanCode) && (phase == Phase.RAISING || phase == Phase.STRIKING)) {
            blow(view);
            to(Phase.RINGING);
        }
        return false;
    }

    private static SmithingMenu smithing(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof SmithingMenu smithing ? smithing : null;
    }
}
