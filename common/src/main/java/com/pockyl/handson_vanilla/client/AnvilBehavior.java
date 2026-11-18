package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;

/**
 * The anvil worked by hand. The two pieces lie side by side on its face; what they make hovers between them, growing in when it
 * appears, and over it, turned to the camera like a name tag, the name it will bear and the cost in vanilla's words and colours. A
 * click on the name, or a key typed with the cursor on it, picks it whole and lets the player type a new one (the first key replaces
 * it; Backspace, Ctrl+V, Ctrl+A; Enter, Escape or a click elsewhere ends it), sent to the server on every change as the vanilla
 * field does. The result under the cursor lights up and shows its tooltip. A click on it brings the hammer in from the right, and
 * as its first blow comes down the result is taken with the vanilla click on the result slot (plain: onto the cursor; shift: into
 * the inventory), so the server's answer comes about as it lands.
 * The server answers with vanilla's anvil sound, three hammer blows in one, and the hammer strikes the forged piece twice more in
 * time with it (when and at what pitch this client plays it: {@link AnvilSound}), sparks flying, the head bouncing back each time;
 * then the piece rises off the anvil for a moment, glinting when enchanted. The station makes no blow sound of its own. The levels
 * paid and the anvil's wear are the server's. A result the player cannot pay for, or has no room for, only shakes. All of it is
 * local.
 */
final class AnvilBehavior implements StationBehavior {
    static final String INPUTS = "inputs";
    private static final String LEFT = "left";
    private static final String RIGHT = "right";
    private static final String HAMMER = "hammer";
    private static final String RESULT = "result";
    private static final String NAME = "name";
    private static final String FORGED = "forged";
    /** The vanilla menu's slots: two inputs, the result. */
    private static final int LEFT_SLOT = 0;
    private static final int RESULT_SLOT = 2;
    /** Vanilla's colours of the cost: affordable, and too dear or too expensive. */
    private static final int COST_COLOR = 0xFF80FF20;
    private static final int DEAR_COLOR = 0xFFFF6060;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int TAG_BACKGROUND = 0x55000000;
    private static final int PICKED_BACKGROUND = 0xC02850B0;
    /** The name tag's font size: scene pixels per font pixel. */
    private static final float TEXT_SCALE = 0.07F;

    /** Where the hammer comes from (off the screen's right, high) and how it is raised, relative to its place at the blow. */
    private static final Vector3f FROM = new Vector3f(-14.0F, 12.0F, -6.0F);
    private static final float FROM_TURN = 80.0F;
    private static final float RAISE_LIFT = 1.5F;
    private static final float RAISE_TURN = 60.0F;
    /** Blows a strike takes, as many as vanilla's anvil sound has: the first takes the result, the others keep time with the sound. */
    private static final int BLOWS = AnvilSound.BLOWS.length;
    /** The pitch the sound is taken to have until this client plays it (vanilla picks 0.9 to 1). */
    private static final float GUESSED_PITCH = 0.95F;
    /** Seconds: in and raised, up again between blows (at least), down, the ring after a blow, away. */
    private static final float ENTER = 0.22F;
    private static final float RAISE = 0.14F;
    private static final float DOWN = 0.07F;
    private static final float RING = 0.09F;
    private static final float LEAVE = 0.2F;
    private static final float ARC = 3.0F;
    /** The head bounces up off the piece after the blow: degrees, and the seconds up and back. */
    private static final float RECOIL = 10.0F;
    private static final float RECOIL_UP = 0.04F;
    private static final float RECOIL_BACK = 0.06F;
    private static final int MOST_QUEUED = 8;
    private static final int SPARK_COLOR = 0xFFFFC870;
    /** Glints off an enchanted result, as the grindstone's. */
    private static final int GLINTS = 6;
    private static final int GLINT_COLOR = 0xFFC890FF;
    /** The result hovering, and the left piece as it lies (for the result rising off it after the blow). */
    private static final float RESULT_SIZE = 4.2F;
    private static final float PIECE_SIZE = 4.4F;

    private enum Phase { IDLE, ENTERING, RAISING, STRIKING, RINGING, LEAVING }

    private Phase phase = Phase.IDLE;
    private float phaseTime;
    private float clock;
    private boolean strikeAll;
    private int queued;
    private boolean recoiled = true;
    /** Which blow of the strike comes next or is under way, from 0; the first one takes the result. */
    private int blowIndex;
    /** The station's clock when vanilla's sound started (or was taken to start), and its pitch: when the later blows land. */
    private float soundAt;
    private float soundPitch = GUESSED_PITCH;
    private boolean soundHeard;
    private int heldSlot = -1;
    /** The inventory slot whose stack went onto the anvil by itself, to fly from there when it lands; -1: none. */
    private int flyFrom = -1;
    /** The name being typed, or shown; whether it is being typed. */
    private String name = "";
    private boolean typing;
    /** The whole name is picked, as a click on the tag leaves it: the first key replaces it, as in a field whose text is selected. */
    private boolean picked;
    /** Whether a mouse button was down last frame: a new press away from the tag ends the typing. */
    private boolean mouseWasDown;
    /** The left piece as last seen, to answer its changes as the vanilla screen does. */
    private ItemStack seenLeft = ItemStack.EMPTY;
    /** What the last blow made, rising off the anvil, and the station's clock at the blow. */
    private ItemStack forged = ItemStack.EMPTY;
    private float forgedAt;
    private final HoveringResult result = new HoveringResult(RESULT_SIZE);
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(LEFT, "inputs[0]", RIGHT, "inputs[1]");

    @Override
    public void onSceneReady(StationView view) {
        // An anvil renames anything.
        view.setInventoryFilter(stack -> !stack.isEmpty());
        view.node(RESULT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> {
            AnvilMenu menu = anvil(view);
            ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
            if (beingTaken()) {
                return;
            }
            result.paint(view.level(), made, clock, poseStack, buffers, light);
        }));
        view.node(NAME).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> tag(view, node.isHovered(), poseStack, buffers)));
        view.node(FORGED).ifPresent(node -> node.setPainter((poseStack, buffers, light) ->
                HoveringResult.paintForged(view.level(), forged, Math.max(0.0F, clock - forgedAt), PIECE_SIZE, poseStack, buffers, light)));
    }

    /** The first blow is falling or the result is taken: it is going onto the cursor, so it is not drawn hovering. */
    private boolean beingTaken() {
        return phase == Phase.STRIKING
                || (blowIndex > 0 || phase == Phase.RINGING) && !forged.isEmpty() && phase != Phase.IDLE && phase != Phase.LEAVING;
    }

    /** When the blow {@code index} lands on the station's clock: the first at once, the others on the sound's blows. */
    private float landsAt(int index) {
        return index == 0 ? Float.NEGATIVE_INFINITY : soundAt + AnvilSound.BLOWS[index] / soundPitch;
    }

    /**
     * The name tag: the name the result will bear (being typed: with a blinking caret), and under it the cost as the vanilla screen
     * words and colours it. Turned to the camera like the counts of a station's stacks: drawn in the camera's axes where the node is.
     */
    private void tag(StationView view, boolean hovered, com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffers) {
        AnvilMenu menu = anvil(view);
        if (menu == null || menu.getSlot(LEFT_SLOT).getItem().isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Matrix4f pose = poseStack.last().pose();
        Vector3f at = pose.getTranslation(new Vector3f());
        float unit = new Vector3f(pose.m00(), pose.m01(), pose.m02()).length();
        Matrix4f matrix = new Matrix4f().translation(at).scale(TEXT_SCALE * unit, -TEXT_SCALE * unit, TEXT_SCALE * unit);
        String shown = typing && !picked ? name + ((int) (clock * 3.0F) % 2 == 0 ? "_" : " ") : name;
        Component line = Component.literal(shown.isEmpty() ? " " : shown)
                .withStyle(Style.EMPTY.withUnderlined(hovered && !typing).withItalic(!typing && isRenamed(menu)));
        FormattedCharSequence text = line.getVisualOrderText();
        font.drawInBatch(text, -font.width(text) / 2.0F, -font.lineHeight, NAME_COLOR, false, matrix, buffers, Font.DisplayMode.NORMAL,
                typing && picked ? PICKED_BACKGROUND : TAG_BACKGROUND, LightTexture.FULL_BRIGHT);
        Component cost = costLine(menu);
        if (cost != null) {
            boolean dear = !canPay(menu);
            font.drawInBatch(cost, -font.width(cost) / 2.0F, 2.0F, dear ? DEAR_COLOR : COST_COLOR, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, TAG_BACKGROUND, LightTexture.FULL_BRIGHT);
        }
    }

    /** The cost as the vanilla screen shows it: too expensive, the levels the result costs, or nothing. */
    private static Component costLine(AnvilMenu menu) {
        int cost = menu.getCost();
        LocalPlayer player = Minecraft.getInstance().player;
        if (cost <= 0 || player == null) {
            return null;
        }
        if (cost >= 40 && !player.getAbilities().instabuild) {
            return Component.translatable("container.repair.expensive");
        }
        return menu.getSlot(RESULT_SLOT).hasItem() ? Component.translatable("container.repair.cost", cost) : null;
    }

    /** Whether the result can be taken now: vanilla's rule (levels enough, or creative, and a cost). */
    private static boolean canPay(AnvilMenu menu) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && menu.getSlot(RESULT_SLOT).hasItem() && menu.getSlot(RESULT_SLOT).mayPickup(player);
    }

    /** Whether the name differs from the piece's own: shown in italics, as a renamed item's is. */
    private boolean isRenamed(AnvilMenu menu) {
        ItemStack made = menu.getSlot(RESULT_SLOT).getItem();
        return !made.isEmpty() && made.has(DataComponents.CUSTOM_NAME);
    }

    @Override
    public void onOpen(StationView view) {
        heldSlot = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && worked(player.getMainHandItem())) {
            heldSlot = player.getInventory().selected;
        }
        phase = Phase.IDLE;
        queued = 0;
        typing = false;
        name = "";
        seenLeft = ItemStack.EMPTY;
        flyFrom = -1;
        forged = ItemStack.EMPTY;
        watch.forget();
        view.node(HAMMER).ifPresent(node -> node.setVisible(false));
    }

    @Override
    public void onMenuOpened(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        MenuAccess menu = view.menu();
        if (heldSlot >= 0 && player != null && menu != null && worked(player.getInventory().getItem(heldSlot))) {
            // One vanilla shift-click of the held stack: it goes onto the anvil's left.
            menu.quickMoveFromInventory(heldSlot);
            flyFrom = heldSlot;
        }
        heldSlot = -1;
    }

    /**
     * What goes onto the anvil by itself when held as it is used: what is mended, combined or already named. Anything can be renamed
     * from the rows, but an apple in the hand is not meant for the anvil.
     */
    private static boolean worked(ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamageableItem() || stack.isEnchanted() || stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                || stack.has(DataComponents.CUSTOM_NAME));
    }

    @Override
    public void onClose(StationView view) {
        typing = false;
        phase = Phase.IDLE;
        view.node(HAMMER).ifPresent(node -> node.setVisible(false));
        view.node(FORGED).ifPresent(node -> node.setVisible(false));
        forged = ItemStack.EMPTY;
    }

    /** Pieces put onto the anvil and taken off it by hand sound; the one put by itself on opening flies there from the hotbar. */
    @Override
    public void tick(StationView view) {
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
        AnvilMenu menu = anvil(view);
        if (menu != null) {
            ItemStack left = menu.getSlot(LEFT_SLOT).getItem();
            if (!ItemStack.matches(left, seenLeft)) {
                // As the vanilla screen: a new left piece puts its own name into the field and sends it.
                seenLeft = left.copy();
                typing = false;
                rename(view, left.isEmpty() ? "" : left.getHoverName().getString());
            }
        }
        endTypingOnClickAway(view);
        ItemStack made = menu == null ? ItemStack.EMPTY : menu.getSlot(RESULT_SLOT).getItem();
        view.node(RESULT).ifPresent(node -> result.frame(node, made, !beingTaken(), dt));
        hear(view);
        view.node(FORGED).ifPresent(node -> node.setVisible(!forged.isEmpty() && clock - forgedAt < HoveringResult.FORGED));
        switch (phase) {
            case ENTERING, RAISING -> {
                if (phaseTime >= (phase == Phase.ENTERING ? ENTER : RAISE) && clock >= landsAt(blowIndex) - DOWN) {
                    if (blowIndex == 0) {
                        take(view);
                    }
                    pose(view, new Vector3f(), 0.0F, DOWN, false);
                    to(Phase.STRIKING);
                }
            }
            case STRIKING -> {
                if (phaseTime >= DOWN) {
                    knock(view);
                    to(Phase.RINGING);
                }
            }
            case RINGING -> {
                if (!recoiled && phaseTime >= RECOIL_UP) {
                    recoiled = true;
                    view.node(HAMMER).ifPresent(node -> node.rotateTo(0.0F, 0.0F, 0.0F, RECOIL_BACK));
                }
                if (phaseTime >= RING) {
                    if (blowIndex > 0 && blowIndex < BLOWS - 1 || blowIndex == 0 && !forged.isEmpty()) {
                        blowIndex++;
                        raise(view);
                    } else if (queued > 0 && canTake(view, false)) {
                        // A strike asked for during this one: the hammer is at hand.
                        queued--;
                        blowIndex = 0;
                        raise(view);
                    } else {
                        queued = 0;
                        pose(view, FROM, FROM_TURN, LEAVE, true);
                        to(Phase.LEAVING);
                    }
                }
            }
            case LEAVING -> {
                if (phaseTime >= LEAVE) {
                    view.node(HAMMER).ifPresent(node -> node.setVisible(false));
                    to(Phase.IDLE);
                }
            }
            default -> {
            }
        }
    }

    /**
     * A press of a mouse button anywhere but on the tag ends the typing: clicks on slots and the inventory reach the menu without a
     * scene event, and the keys after them (E, the number keys) must be the station's again.
     */
    private void endTypingOnClickAway(StationView view) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (down && !mouseWasDown && typing && !view.node(NAME).map(node -> node.isHovered()).orElse(false)) {
            typing = false;
        }
        mouseWasDown = down;
    }

    /**
     * The name field changed: vanilla's rule for what is sent (the piece's own name, uncustomised, is sent as none, so the result
     * keeps or drops nothing by accident), the menu told and the server with it.
     */
    private void rename(StationView view, String typed) {
        name = typed;
        AnvilMenu menu = anvil(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu == null || player == null || !menu.getSlot(LEFT_SLOT).hasItem()) {
            return;
        }
        ItemStack left = menu.getSlot(LEFT_SLOT).getItem();
        String sent = !left.has(DataComponents.CUSTOM_NAME) && typed.equals(left.getHoverName().getString()) ? "" : typed;
        if (menu.setItemName(sent)) {
            player.connection.send(new ServerboundRenameItemPacket(sent));
        }
    }

    /** A click on the result: a strike, or after the one under way; a result that cannot be taken only shakes. */
    private void strike(StationView view, boolean all) {
        typing = false;
        if (phase == Phase.ENTERING || phase == Phase.RAISING || phase == Phase.STRIKING || phase == Phase.RINGING) {
            if (!all && !strikeAll) {
                queued = Math.min(MOST_QUEUED, queued + 1);
            }
            return;
        }
        if (!canTake(view, all)) {
            AnvilMenu menu = anvil(view);
            if (menu != null && menu.getSlot(LEFT_SLOT).hasItem()) {
                view.node(RESULT).ifPresent(node -> node.shake(0.35F, 0.2F));
                view.node(NAME).ifPresent(node -> node.shake(0.25F, 0.2F));
                StationHands.play(view, SoundEvents.ANVIL_LAND, RESULT, 0.08F, 1.9F);
            }
            return;
        }
        strikeAll = all;
        blowIndex = 0;
        forged = ItemStack.EMPTY;
        view.node(HAMMER).ifPresent(node -> {
            node.moveTo(FROM.x, FROM.y, FROM.z, 0.0F);
            node.rotateTo(0.0F, 0.0F, FROM_TURN, 0.0F);
            node.setVisible(true);
        });
        pose(view, new Vector3f(0.0F, RAISE_LIFT, 0.0F), RAISE_TURN, ENTER, true);
        to(Phase.ENTERING);
    }

    /** Up for the next blow; it waits raised until its blow in the sound is due. */
    private void raise(StationView view) {
        pose(view, new Vector3f(0.0F, RAISE_LIFT, 0.0F), RAISE_TURN, RAISE, false);
        to(Phase.RAISING);
    }

    /**
     * Vanilla's sound, once this client plays it after the take: the later blows land on its blows. Until then they are timed from
     * the take, as in singleplayer, where the sound comes a tick later.
     */
    private void hear(StationView view) {
        if (soundHeard || forged.isEmpty()) {
            return;
        }
        float pitch = AnvilSound.poll(view.pos());
        if (pitch > 0.0F) {
            soundHeard = true;
            soundAt = clock;
            soundPitch = pitch;
        }
    }

    /**
     * As the first blow comes down: the vanilla click on the result (the server's sound starts with it), and the forged piece lies
     * where the left one was. Nothing to take (the menu changed meanwhile): the blow is a knock with no sound.
     */
    private void take(StationView view) {
        MenuAccess menu = view.menu();
        AnvilMenu anvil = anvil(view);
        forged = ItemStack.EMPTY;
        if (menu != null && anvil != null && canTake(view, strikeAll)) {
            ItemStack made = anvil.getSlot(RESULT_SLOT).getItem().copy();
            AnvilSound.forget();
            menu.click(RESULT_SLOT, 0, strikeAll ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            // The pieces used up are the strike's, not taken by hand.
            watch.forget();
            forged = made;
            forgedAt = Float.POSITIVE_INFINITY;
            soundAt = clock + DOWN;
            soundPitch = GUESSED_PITCH;
            soundHeard = false;
        }
    }

    /**
     * A blow on the forged piece: sparks, the piece jumps, the head bounces up, the arm swings; the sound is vanilla's. After the
     * last one the piece rises off the anvil, glinting when enchanted.
     */
    private void knock(StationView view) {
        Vector3f at = view.node(LEFT).map(node -> new Vector3f(node.definition().transform().pos())).orElse(new Vector3f(8.0F, 16.0F, 8.0F))
                .add(0.0F, 0.6F, 0.0F);
        boolean last = blowIndex >= BLOWS - 1 || forged.isEmpty();
        // The forged piece lies still, swollen, until the last blow; then it rises.
        forgedAt = last ? clock : Float.POSITIVE_INFINITY;
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(0.0F, 14.0F, 0.0F), 10.0F, last && strikeAll ? 20 : 12, SPARK_COLOR);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(12.0F, 4.0F, 0.0F), 4.0F, 2, SPARK_COLOR);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(-12.0F, 4.0F, 0.0F), 4.0F, 2, SPARK_COLOR);
        if (last && (forged.isEnchanted() || forged.is(net.minecraft.world.item.Items.ENCHANTED_BOOK))) {
            view.spawnParticles(DiegesisParticles.GLINT, new Vector3f(at).add(0.0F, 2.0F, 0.0F), new Vector3f(0.0F, 4.0F, 0.0F), 3.0F,
                    GLINTS, GLINT_COLOR);
        }
        view.node(LEFT).ifPresent(node -> node.shake(0.35F, 0.12F));
        view.node(FORGED).ifPresent(node -> node.shake(0.25F, 0.1F));
        view.node(HAMMER).ifPresent(node -> node.rotateTo(0.0F, 0.0F, RECOIL, RECOIL_UP));
        recoiled = false;
        StationHands.swing();
    }

    /** Whether a strike now takes something: a result the player can pay for, and room for it on the cursor or in the inventory. */
    private boolean canTake(StationView view, boolean all) {
        AnvilMenu menu = anvil(view);
        if (menu == null || !canPay(menu)) {
            return false;
        }
        ItemStack made = menu.getSlot(RESULT_SLOT).getItem();
        if (all) {
            return StationHands.inventoryHasRoom(made);
        }
        ItemStack held = menu.getCarried();
        return held.isEmpty()
                || ItemStack.isSameItemSameComponents(held, made) && held.getCount() + made.getCount() <= held.getMaxStackSize();
    }

    /** The hammer to an offset from its place at the blow and a raise (a turn about z), in an arc or straight. */
    private static void pose(StationView view, Vector3f offset, float turn, float seconds, boolean hop) {
        view.node(HAMMER).ifPresent(node -> {
            if (hop) {
                node.hopTo(offset.x, offset.y, offset.z, ARC, seconds);
            } else {
                node.moveTo(offset.x, offset.y, offset.z, seconds);
            }
            node.rotateTo(0.0F, 0.0F, turn, seconds);
        });
    }

    private void to(Phase next) {
        phase = next;
        phaseTime = 0.0F;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (!(event instanceof SceneEvent.Click click)) {
            return;
        }
        if (RESULT.equals(click.node())) {
            strike(view, Screen.hasShiftDown());
        } else if (NAME.equals(click.node())) {
            startTyping(view);
        } else {
            typing = false;
        }
    }

    /** The name picked whole for typing, when there is a piece to name. */
    private void startTyping(StationView view) {
        AnvilMenu menu = anvil(view);
        typing = menu != null && menu.getSlot(LEFT_SLOT).hasItem();
        picked = typing;
    }

    /** A stack in the hand: the strike puts the result onto it when it can; on the name it does nothing. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (RESULT.equals(node)) {
            strike(view, shift);
            return true;
        }
        typing = false;
        return NAME.equals(node);
    }

    /**
     * While a name is typed every key is the typing's: Backspace, Ctrl+V, and Enter or Escape to end it. A key that types a character
     * with the cursor on the tag starts the typing, as the vanilla field is ready for it. Leaving before the first blow lands it
     * first, so the click does not go with the menu.
     */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (!typing) {
            if (types(keyCode, modifiers) && view.node(NAME).map(node -> node.isHovered()).orElse(false)) {
                startTyping(view);
                return typing;
            }
            if (StationHands.leaves(keyCode, scanCode) && (phase == Phase.ENTERING || phase == Phase.RAISING) && blowIndex == 0) {
                take(view);
                knock(view);
                to(Phase.RINGING);
            }
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> typing = false;
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> {
                if (picked) {
                    picked = false;
                    rename(view, "");
                } else if (!name.isEmpty() && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    rename(view, name.substring(0, name.offsetByCodePoints(name.length(), -1)));
                }
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_HOME -> picked = false;
            default -> {
                if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown()) {
                    type(view, Minecraft.getInstance().keyboardHandler.getClipboard());
                } else if (keyCode == GLFW.GLFW_KEY_A && Screen.hasControlDown()) {
                    picked = true;
                }
            }
        }
        return true;
    }

    /** Whether a key types a character: the printable keys and the keypad's digits, without Ctrl, Alt or Super. */
    private static boolean types(int keyCode, int modifiers) {
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0) {
            return false;
        }
        return keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT
                || keyCode == GLFW.GLFW_KEY_WORLD_1 || keyCode == GLFW.GLFW_KEY_WORLD_2
                || keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9;
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        if (!typing) {
            return false;
        }
        type(view, String.valueOf(character));
        return true;
    }

    /** Adds what was typed or pasted, as the vanilla field takes it: allowed characters only, at most 50 in all. */
    private void type(StationView view, String typed) {
        StringBuilder allowed = new StringBuilder();
        for (char character : typed.toCharArray()) {
            if (StringUtil.isAllowedChatCharacter(character)) {
                allowed.append(character);
            }
        }
        if (allowed.isEmpty()) {
            return;
        }
        String next = (picked ? "" : name) + allowed;
        picked = false;
        rename(view, next.length() > AnvilMenu.MAX_NAME_LENGTH ? next.substring(0, AnvilMenu.MAX_NAME_LENGTH) : next);
    }

    private static AnvilMenu anvil(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof AnvilMenu anvil ? anvil : null;
    }
}
