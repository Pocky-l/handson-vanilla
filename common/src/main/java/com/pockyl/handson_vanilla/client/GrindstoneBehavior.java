package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;

/**
 * The grindstone worked by hand, however it hangs (floor, wall, ceiling) and from whichever side the player comes. The block is
 * swapped for its legs and its wheel, turned as the block stands; the two stacks stand before the face of the wheel the camera
 * sees, as if held to it, and what they make hovers over the wheel. A click on the wheel or on what hovers grinds: the stacks are
 * pressed to the stone, the wheel turns once round, sparks fly off it (and the enchantments' glints, when there are any), and at
 * the touch the result is taken with the vanilla click on the result slot (plain: onto the cursor; shift: into the inventory).
 * The experience orbs and the grinding sound are the server's own. Clicks during a grind grind again after it. With nothing to
 * make the wheel only turns a quarter. All of it is local.
 */
final class GrindstoneBehavior implements StationBehavior {
    static final String INPUT = "input";
    private static final String BODY = "body";
    private static final String WHEEL = "wheel";
    private static final String RESULT = "result";
    private static final String[] INPUTS = {"input_0", "input_1"};
    /** The vanilla menu's slots: two inputs, the result. */
    private static final int RESULT_SLOT = 2;

    /** The wheel's middle in the block's own model (on the floor), and half its size along its axle, up and across. */
    private static final Vector3f WHEEL_MIDDLE = new Vector3f(8.0F, 10.0F, 8.0F);
    private static final Vector3f WHEEL_HALF = new Vector3f(4.0F, 6.0F, 6.0F);
    /** Where the scene file places things: for a floor grindstone whose rim faces the camera, the rim at z 2. */
    private static final float SCENE_FRONT = 2.0F;
    /** How far before the wheel's face the stacks stand, where they touch it while grinding, and their place across and down. */
    private static final float HELD_OFF = 1.4F;
    private static final float TOUCH_OFF = 0.35F;
    private static final float HELD_ACROSS = 2.6F;
    private static final float HELD_DOWN = 1.8F;
    /** Held to the rim beside the wheel: a little towards the camera from the wheel's middle. */
    private static final float SIDE_FORWARD = 3.0F;
    /** How high over the wheel's top the result hovers. */
    private static final float RESULT_OVER = 1.2F;

    /** Seconds: the stacks pressed to the stone, the turn of the wheel, the stacks let go. */
    private static final float PRESS = 0.12F;
    private static final float TURN = 0.5F;
    private static final float TAKE_AT = 0.12F;
    private static final float LET_GO = 0.15F;
    private static final float NUDGE = 0.4F;
    private static final float SPARKS_EVERY = 0.04F;
    private static final int MOST_QUEUED = 8;
    private static final int SPARK_COLOR = 0xFFFFC870;
    private static final int GLINT_COLOR = 0xFFC890FF;
    /** The result hovering over the wheel. */
    private static final float RESULT_SIZE = 4.4F;

    private enum Phase { IDLE, PRESSING, GRINDING, LETTING_GO }

    private Phase phase = Phase.IDLE;
    private float phaseTime;
    private float clock;
    private float sparksIn;
    private boolean grindAll;
    private boolean taken;
    private boolean glints;
    private int queued;
    /** How the block's model stands in the scene: the turn about x (wall, ceiling), then about y; the wheel's own turn on top. */
    private float pitch;
    private float yaw;
    private float spun;
    /** Where the wheel's middle is in the scene, the face the camera sees (scene z), and the wheel's top. */
    private final Vector3f middle = new Vector3f(WHEEL_MIDDLE);
    private float front = SCENE_FRONT;
    private float top = WHEEL_MIDDLE.y() + WHEEL_HALF.y();
    /**
     * Whether the wheel's axle runs along the camera's view (the player came to the grindstone's side): the axle and a leg stand
     * before the round face then, so the stacks are held to the rim left and right of the wheel instead, {@code side} from its middle.
     */
    private boolean faceOn;
    private float side;
    private final HoveringResult result = new HoveringResult(RESULT_SIZE);

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(GrindstoneBehavior::usable);
        view.node(RESULT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> {
            GrindstoneMenu menu = grindstone(view);
            ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
            if (phase == Phase.GRINDING && !taken) {
                return;
            }
            result.paint(view.level(), made, clock, poseStack, buffers, light);
        }));
    }

    /** What the grindstone takes: what can be worn down or repaired, and anything enchanted (vanilla's slot rule). */
    static boolean usable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // The open menu's own rule, so what other mods let in counts too (Supplementaries' enchanted golden apple).
        if (StationHover.menu() instanceof net.minecraft.world.inventory.GrindstoneMenu grindstone) {
            return grindstone.getSlot(0).mayPlace(stack) || grindstone.getSlot(1).mayPlace(stack);
        }
        return stack.isDamageableItem() || stack.is(Items.ENCHANTED_BOOK) || stack.isEnchanted();
    }

    @Override
    public void onOpen(StationView view) {
        stand(view);
        phase = Phase.IDLE;
        queued = 0;
        held(view, HELD_OFF, 0.0F);
    }

    /**
     * Turns the legs and the wheel as the block stands, in the blockstate's way (its x turn, then its y turn) and then into the
     * scene, which faces the player; places the stacks, the result and the camera by where the wheel's middle and face are now.
     */
    private void stand(StationView view) {
        BlockState state = view.level().getBlockState(view.pos());
        AttachFace face = state.getOptionalValue(GrindstoneBlock.FACE).orElse(AttachFace.FLOOR);
        Direction facing = state.getOptionalValue(GrindstoneBlock.FACING).orElse(Direction.NORTH);
        int blockX = face == AttachFace.WALL ? 90 : face == AttachFace.CEILING ? 180 : 0;
        int blockY = (turn(facing) + (face == AttachFace.CEILING ? 180 : 0)) % 360;
        // The blockstate turns clockwise as seen from the axis' positive end, JOML the other way.
        pitch = -blockX;
        yaw = turn(view.facing()) - blockY;
        spun = 0.0F;
        Matrix3f rotation = new Matrix3f().rotationYXZ((float) Math.toRadians(yaw), (float) Math.toRadians(pitch), 0.0F);
        rotation.transform(new Vector3f(WHEEL_MIDDLE).sub(8.0F, 8.0F, 8.0F), middle).add(8.0F, 8.0F, 8.0F);
        float depth = 0.0F;
        float height = 0.0F;
        side = 0.0F;
        for (int column = 0; column < 3; column++) {
            depth += Math.abs(rotation.get(column, 2)) * WHEEL_HALF.get(column);
            height += Math.abs(rotation.get(column, 1)) * WHEEL_HALF.get(column);
            side += Math.abs(rotation.get(column, 0)) * WHEEL_HALF.get(column);
        }
        faceOn = Math.abs(rotation.get(0, 2)) > 0.5F;
        front = middle.z - depth;
        top = middle.y + height;
        view.node(BODY).ifPresent(node -> node.rotateTo(pitch, yaw, 0.0F, 0.0F));
        view.node(WHEEL).ifPresent(node -> {
            node.moveTo(middle.x - WHEEL_MIDDLE.x(), middle.y - WHEEL_MIDDLE.y(), middle.z - WHEEL_MIDDLE.z(), 0.0F);
            node.rotateTo(pitch, yaw, 0.0F, 0.0F);
        });
        view.node(RESULT).ifPresent(node -> {
            org.joml.Vector3fc rest = node.definition().transform().pos();
            node.moveTo(middle.x - rest.x(), top + RESULT_OVER - rest.y(), middle.z - rest.z(), 0.0F);
        });
        view.moveCamera(middle.x - WHEEL_MIDDLE.x(), middle.y - WHEEL_MIDDLE.y(), front - SCENE_FRONT);
    }

    /** A direction as the blockstate's y turn: north 0, east 90, south 180, west 270. */
    private static int turn(Direction direction) {
        return ((int) direction.toYRot() + 180) % 360;
    }

    /** The stacks stand {@code off} from the stone: before the rim the camera sees, or at the rim left and right of the wheel. */
    private void held(StationView view, float off, float seconds) {
        for (int i = 0; i < INPUTS.length; i++) {
            Vector3f at = touch(i, off);
            view.node(INPUTS[i]).ifPresent(node -> {
                org.joml.Vector3fc rest = node.definition().transform().pos();
                node.moveTo(at.x - rest.x(), at.y - HELD_DOWN - rest.y(), at.z - rest.z(), seconds);
            });
        }
    }

    /** Where stack {@code i} (0 on the camera's left) is held {@code off} from the stone, at the height of the wheel's middle. */
    private Vector3f touch(int i, float off) {
        float sign = i == 0 ? 1.0F : -1.0F;
        return faceOn ? new Vector3f(middle.x + sign * (side + off), middle.y, middle.z - SIDE_FORWARD)
                : new Vector3f(middle.x + sign * HELD_ACROSS, middle.y, front - off);
    }

    @Override
    public void onClose(StationView view) {
        phase = Phase.IDLE;
        queued = 0;
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        phaseTime += dt;
        boolean shown = view.isBlockHidden();
        view.node(BODY).ifPresent(node -> node.setVisible(shown));
        view.node(WHEEL).ifPresent(node -> node.setVisible(shown));
        GrindstoneMenu menu = grindstone(view);
        ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
        view.node(RESULT).ifPresent(node -> result.frame(node, made, !(phase == Phase.GRINDING && !taken), dt));
        switch (phase) {
            case PRESSING -> {
                if (phaseTime >= PRESS) {
                    spun += 360.0F;
                    view.node(WHEEL).ifPresent(node -> node.rotateTo(pitch + spun, yaw, 0.0F, TURN));
                    to(Phase.GRINDING);
                    taken = false;
                    sparksIn = 0.0F;
                }
            }
            case GRINDING -> {
                if (!taken && phaseTime >= TAKE_AT) {
                    taken = true;
                    take(view);
                }
                sparksIn -= dt;
                if (sparksIn <= 0.0F && phaseTime < TURN * 0.8F) {
                    sparksIn = SPARKS_EVERY;
                    sparks(view);
                }
                if (phaseTime >= TURN) {
                    if (queued > 0 && canTake(view, false)) {
                        queued--;
                        begin(view, false);
                    } else {
                        queued = 0;
                        held(view, HELD_OFF, LET_GO);
                        to(Phase.LETTING_GO);
                    }
                }
            }
            case LETTING_GO -> {
                if (phaseTime >= LET_GO) {
                    to(Phase.IDLE);
                }
            }
            default -> {
            }
        }
    }

    /** A click on the wheel or on the result: a grind, or after the one under way; with nothing to make, a quarter turn. */
    private void grind(StationView view, boolean all) {
        if (phase == Phase.PRESSING || phase == Phase.GRINDING) {
            if (!all && !grindAll) {
                queued = Math.min(MOST_QUEUED, queued + 1);
            }
            return;
        }
        if (!canTake(view, all)) {
            spun += 90.0F;
            view.node(WHEEL).ifPresent(node -> node.rotateTo(pitch + spun, yaw, 0.0F, NUDGE));
            play(view, SoundEvents.GRINDSTONE_USE, WHEEL, 0.12F, 1.4F);
            return;
        }
        begin(view, all);
    }

    private void begin(StationView view, boolean all) {
        grindAll = all;
        glints = false;
        GrindstoneMenu menu = grindstone(view);
        if (menu != null) {
            glints = menu.getSlot(0).getItem().isEnchanted() || menu.getSlot(1).getItem().isEnchanted()
                    || menu.getSlot(0).getItem().is(Items.ENCHANTED_BOOK) || menu.getSlot(1).getItem().is(Items.ENCHANTED_BOOK);
        }
        held(view, TOUCH_OFF, PRESS);
        to(Phase.PRESSING);
    }

    /** The touch: the vanilla click on the result; the stacks shake against the stone, the arm moves. */
    private void take(StationView view) {
        MenuAccess menu = view.menu();
        if (menu != null && canTake(view, grindAll)) {
            menu.click(RESULT_SLOT, 0, grindAll ? ClickType.QUICK_MOVE : ClickType.PICKUP);
        }
        for (String id : INPUTS) {
            view.node(id).ifPresent(node -> node.shake(0.2F, TURN - TAKE_AT));
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND, false);
        }
    }

    /** Sparks off the stone where the stacks touch it, thrown along the turning rim; glints rise while enchantments come off. */
    private void sparks(StationView view) {
        var random = view.level().getRandom();
        for (int i = 0; i < INPUTS.length; i++) {
            if (view.state().getItem(SlotRef.of(INPUT, i)).isEmpty() && !taken) {
                continue;
            }
            Vector3f at = touch(i, TOUCH_OFF);
            // Thrown off along the turning rim: towards the camera before the wheel, down and out beside it.
            Vector3f flight = faceOn ? new Vector3f((i == 0 ? 6.0F : -6.0F), -6.0F, (random.nextFloat() - 0.5F) * 6.0F)
                    : new Vector3f((random.nextFloat() - 0.5F) * 6.0F, -6.0F, -8.0F);
            view.spawnParticles(DiegesisParticles.SPARK, at, flight, 5.0F, 2, SPARK_COLOR);
            if (glints) {
                view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 3.0F, -1.0F), 2.0F, 1, GLINT_COLOR);
            }
        }
    }

    /** Whether a grind now takes something: a result, and room for it on the cursor (plain click) or anywhere (shift). */
    private boolean canTake(StationView view, boolean all) {
        GrindstoneMenu menu = grindstone(view);
        if (menu == null) {
            return false;
        }
        ItemStack made = menu.getSlot(RESULT_SLOT).getItem();
        if (made.isEmpty()) {
            return false;
        }
        if (all) {
            return true;
        }
        ItemStack held = menu.getCarried();
        return held.isEmpty()
                || ItemStack.isSameItemSameComponents(held, made) && held.getCount() + made.getCount() <= held.getMaxStackSize();
    }

    private void to(Phase next) {
        phase = next;
        phaseTime = 0.0F;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && (WHEEL.equals(click.node()) || RESULT.equals(click.node()))) {
            grind(view, Minecraft.getInstance().hasShiftDown());
        }
    }

    /** A stack in the hand: the grind puts the result onto it when it can; nothing else happens to it on the wheel. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (WHEEL.equals(node) || RESULT.equals(node)) {
            grind(view, shift);
            return true;
        }
        return false;
    }

    private static GrindstoneMenu grindstone(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof GrindstoneMenu grindstone ? grindstone : null;
    }

    private static void play(StationView view, SoundEvent sound, String node, float volume, float pitch) {
        float scaled = volume * VanillaConfig.soundVolume();
        if (scaled > 0.0F) {
            view.playSound(sound, view.node(node).isPresent() ? node : null, scaled, pitch);
        }
    }
}
