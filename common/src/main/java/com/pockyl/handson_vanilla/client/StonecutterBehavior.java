package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.StonecutterBlock;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.List;

/**
 * The stonecutter worked by hand, from whichever side the player comes: the camera looks down at it from that side, half turned,
 * so the saw is seen aslant however the block stands. The stone lies on the slab beside the saw; what it is cut into is picked in
 * the recipe book hanging beside the stonecutter (as at the table), open from the first visit. A page picked cuts it as a saw is
 * used: the stone is set on the saw's line at its far end and fed along it through the turning saw, bite by bite, shaking and
 * wearing away, stone dust flying where it meets the saw. All the stone on the slab is cut (the vanilla shift-click on the result);
 * Shift cuts one piece (the vanilla click on the result, then on the inventory slot a shift-click would fill), so the cursor stays
 * free either way. As the stone comes out the pieces spring up and drop beside it on the slab, lie there a moment and fly into the
 * inventory where they went; the stone goes back. Picks during a cut are cut after it. A page whose stone is not on the slab lays
 * it there from the inventory first; a page whose stone the player does not have, or with no room for the pieces, makes the slab's
 * stone shake. Nothing goes onto the slab by itself. The cuts are the vanilla menu's own:
 * its button picks the recipe, a click on its result takes it. All of it is local.
 */
final class StonecutterBehavior implements StationBehavior {
    static final String INPUT = "input";
    private static final String CUT_OUT = "cut_out";
    private static final String BOOK = "book_open";

    /** Where the stone rests, where a pass starts and where it ends (scene x, z), with the saw across or along the scene. */
    private static final float[] REST_ACROSS = {8.0F, 3.6F};
    private static final float[] REST_ALONG = {3.6F, 8.0F};
    /** The saw's line (scene z when it runs across, x when along), and how far along it a pass goes. */
    private static final float SAW_LINE = 8.0F;
    private static final float PASS_FROM = 13.0F;
    private static final float PASS_TO = 4.0F;

    /** The cut, seconds: the stone set on the saw's line, the pass through the saw (longer for more pieces), the way back. */
    private static final float TO_SAW = 0.16F;
    private static final float PASS = 0.32F;
    private static final float PASS_MORE = 0.15F;
    private static final float BACK = 0.18F;
    /** How much the stone shakes in the saw, pixels. */
    private static final float SHAKE = 0.12F;
    /** Cutting all: the pass grows by a bite for each piece, up to this many; the stone wears down to this size over it. */
    private static final int MOST_BITES = 4;
    private static final float WORN = 0.35F;
    /** Stone dust off the saw: how many each time, how often (seconds), how fast it is thrown along the feed (pixels a second). */
    private static final int DUST = 4;
    private static final float DUST_EVERY = 0.05F;
    private static final float DUST_THROW = 8.0F;
    /**
     * The cut pieces spring up out of the stone and drop beside it: how high, pixels, for how long, seconds, their size for the frame
     * display then and lying; how long they lie there before they fly into the inventory.
     */
    private static final float POP_HEIGHT = 7.0F;
    private static final float POP = 0.35F;
    private static final float POP_SIZE = 6.0F;
    private static final float PILE_SIZE = 4.0F;
    private static final float LIE = 0.5F;
    /** Where the pieces lie (scene x, z): on the slab's corner nearest the camera, clear of the stone at rest and the saw. */
    private static final float[] PILE_ACROSS = {2.6F, 3.0F};
    private static final float[] PILE_ALONG = {3.0F, 2.6F};
    /** Picks of the same piece kept during a cut, to be cut after it. */
    private static final int MOST_QUEUED = 8;

    private final RecipeBook book = new RecipeBook(new StonecutterBook(this));
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(INPUT, INPUT);
    /** Whether the saw runs across the scene (the player came to its face or back) or along it (to its end). */
    private boolean across = true;

    /** The cut under way: seconds into it (negative: none), its recipe, whether it takes all, its bites, whether the take went. */
    private float cut = -1.0F;
    /** Seconds before a cut waits to start: the stone is still flying onto the slab. */
    private float startIn = -1.0F;
    private StonecutterBook.Cut cutId;
    private boolean cutAll;
    private int bites;
    private int bitten;
    private boolean sent;
    private float dustIn;
    private int queued;
    /** Another page picked during a cut: cut after it (and after the picks queued of the same piece). */
    private StonecutterBook.Cut nextId;
    private boolean nextAll;
    /**
     * The pieces springing out of the stone, and seconds since they did (negative: none); it runs on through the next cut's start.
     * The inventory slot they went into, and where it is in the scene once they fly there (null: they shrink away where they lie).
     */
    private ItemStack popPiece = ItemStack.EMPTY;
    private float popTime = -1.0F;
    private int popSlot = -1;
    private Vector3f popTo;
    /** What the cut gives, and the stone it is cut from (for its dust). */
    private ItemStack cutPiece = ItemStack.EMPTY;
    private ItemStack cutStone = ItemStack.EMPTY;

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(StonecutterUse::usable);
        view.node(BOOK).ifPresent(node -> node.setPainter(book));
        view.node(CUT_OUT).ifPresent(node -> {
            node.setVisible(false);
            node.setPainter((poseStack, buffers, light) -> {
                if (!popPiece.isEmpty() && popTime >= 0.0F) {
                    pieces(view, poseStack, buffers, light);
                }
            });
        });
    }

    @Override
    public void onOpen(StationView view) {
        StonecutterUse.refresh();
        // The saw runs along the block's sides: across the scene when the player stands at its face or back.
        Direction block = view.level().getBlockState(view.pos()).getOptionalValue(StonecutterBlock.FACING).orElse(Direction.NORTH);
        across = block.getAxis() == view.facing().getAxis();
        stopCutting();
        watch.forget();
        stone(view, 0.0F, 0.0F);
        book.opened(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        stopCutting();
        popPiece = ItemStack.EMPTY;
        popTime = -1.0F;
        popTo = null;
        view.node(INPUT).ifPresent(node -> node.scaleTo(1.0F, 0.0F));
        view.node(CUT_OUT).ifPresent(node -> node.setVisible(false));
    }

    private void stopCutting() {
        cut = -1.0F;
        startIn = -1.0F;
        queued = 0;
        nextId = null;
    }

    /** Stone put onto the slab and taken off it by hand sounds; what the saw uses up does not. */
    @Override
    public void tick(StationView view) {
        watch.tick(view, (node, change) -> {
            if (change > 0) {
                StationHands.play(view, SoundEvents.STONE_HIT, INPUT, 0.3F, 1.1F + 0.2F * StationHands.random(view));
            } else {
                StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, INPUT, 0.3F, 0.9F + 0.2F * StationHands.random(view));
            }
        });
    }

    /** What lies on the slab. */
    ItemStack input(StationView view) {
        return view.state().getItem(SlotRef.of(INPUT, 0));
    }

    /** The recipe the stonecutter is set to cut, or null. */
    StonecutterBook.Cut picked(StationView view) {
        StonecutterMenu cutter = stonecutter(view);
        if (cutter == null) {
            return null;
        }
        int index = cutter.getSelectedRecipeIndex();
        List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes = cutter.getVisibleRecipes().entries();
        return index >= 0 && index < recipes.size() ? new StonecutterBook.Cut(recipes.get(index)) : null;
    }

    /**
     * A page picked in the book: the recipe is set (the menu's button) and cut, all the stone on the slab or one piece, the right
     * stone laid on the slab first when another or none lies there. Without that stone, or without room for the pieces in the
     * inventory, the slab's stone shakes.
     */
    void cut(StationView view, StonecutterBook.Cut id, boolean all) {
        MenuAccess menu = view.menu();
        StonecutterMenu cutter = stonecutter(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu == null || cutter == null || player == null) {
            return;
        }
        if (cut >= 0.0F || startIn >= 0.0F) {
            if (!all && !cutAll && nextId == null && id.equals(cutId)) {
                queued = Math.min(MOST_QUEUED, queued + 1);
            } else {
                nextId = id;
                nextAll = all;
            }
            return;
        }
        Ingredient stone = id.entry().input();
        ItemStack makes = id.makes(view.level());
        if (makes.isEmpty()) {
            return;
        }
        int from = stone.test(input(view)) ? -1 : find(player.getInventory(), stone);
        if (!stone.test(input(view)) && from < 0 || landing(cutter, makes, !all) < 0) {
            refuse(view);
            return;
        }
        float wait = 0.0F;
        if (from >= 0) {
            if (!input(view).isEmpty()) {
                // The other stone goes back to the inventory, as a shift-click on it would send it.
                menu.click(0, 0, ContainerInput.QUICK_MOVE);
            }
            menu.quickMoveFromInventory(from);
            flight(view, from);
            wait = StationHands.FLIGHT_TIME;
        }
        cutId = id;
        cutAll = all;
        startIn = wait;
    }

    /** Nothing to cut this page from, or no room for the pieces: the stone on the slab shakes, the saw only whirs. */
    private static void refuse(StationView view) {
        view.node(INPUT).ifPresent(node -> node.shake(0.3F, 0.2F));
        StationHands.play(view, SoundEvents.UI_STONECUTTER_SELECT_RECIPE, INPUT, 0.3F, 0.6F);
    }

    /** The first inventory slot holding the stone a recipe takes, the hotbar first; -1 when there is none. */
    private static int find(Inventory inventory, Ingredient stone) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (!inventory.getItem(i).isEmpty() && stone.test(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** The cut begins: the stone lies on the slab now, so its recipes are the menu's; the recipe is set by the menu's button. */
    private void begin(StationView view) {
        MenuAccess menu = view.menu();
        StonecutterMenu cutter = stonecutter(view);
        ItemStack stone = input(view);
        int index = -1;
        if (cutter != null) {
            List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> recipes = cutter.getVisibleRecipes().entries();
            for (int i = 0; i < recipes.size(); i++) {
                if (cutId != null && recipes.get(i).equals(cutId.entry())) {
                    index = i;
                }
            }
        }
        if (menu == null || cutter == null || stone.isEmpty() || index < 0) {
            queued = 0;
            return;
        }
        if (index != cutter.getSelectedRecipeIndex()) {
            menu.pressButton(index);
        }
        if (cutter.getSlot(1).getItem().isEmpty()) {
            queued = 0;
            return;
        }
        ItemStack makes = cutter.getSlot(1).getItem();
        cut = 0.0F;
        sent = false;
        dustIn = 0.0F;
        bitten = 0;
        cutStone = stone.copy();
        int pieces = cutAll ? stone.getCount() : 1;
        bites = Math.max(1, Math.min(MOST_BITES, pieces));
        cutPiece = makes.copyWithCount(Math.min(makes.getMaxStackSize(), makes.getCount() * pieces));
    }

    @Override
    public void frame(StationView view, float dt) {
        book.frame(view, dt);
        if (startIn >= 0.0F) {
            startIn -= dt;
            if (startIn < 0.0F) {
                begin(view);
            }
        }
        pop(view, dt);
        if (cut < 0.0F) {
            return;
        }
        cut += dt;
        float pass = PASS + PASS_MORE * (bites - 1);
        float passEnd = TO_SAW + pass;
        float[] rest = across ? REST_ACROSS : REST_ALONG;
        float[] from = spot(PASS_FROM);
        float[] to = spot(PASS_TO);
        if (cut < TO_SAW) {
            float e = ease(cut / TO_SAW);
            stone(view, (from[0] - rest[0]) * e, (from[1] - rest[1]) * e);
        } else if (cut < passEnd) {
            float t = (cut - TO_SAW) / pass;
            // A bite for each piece: the saw bites in again, a little higher each time, and a Shift stone wears away.
            int bite = Math.min(bites - 1, (int) ((cut - TO_SAW) / PASS_MORE));
            if (bitten <= bite && bitten < bites) {
                if (bitten == 0 && cutAll) {
                    view.node(INPUT).ifPresent(node -> node.scaleTo(WORN, pass));
                }
                float rise = bites > 1 ? (float) bitten / (bites - 1) : 0.0F;
                StationHands.play(view, SoundEvents.STONE_HIT, INPUT, bitten == 0 ? 0.35F : 0.3F,
                        0.8F + 0.3F * rise + 0.1F * StationHands.random(view));
                bitten++;
            }
            float x = Mth.lerp(t, from[0], to[0]);
            float z = Mth.lerp(t, from[1], to[1]);
            var random = view.level().getRandom();
            stone(view, x - rest[0] + (random.nextFloat() * 2.0F - 1.0F) * SHAKE, z - rest[1] + (random.nextFloat() * 2.0F - 1.0F) * SHAKE);
            dustIn -= dt;
            if (dustIn <= 0.0F) {
                dustIn = DUST_EVERY;
                dust(view, cutStone, x, z);
            }
        } else if (cut < passEnd + BACK) {
            if (!sent) {
                take(view);
            }
            float e = 1.0F - ease((cut - passEnd) / BACK);
            stone(view, (to[0] - rest[0]) * e, (to[1] - rest[1]) * e);
        } else {
            if (!sent) {
                take(view);
            }
            stone(view, 0.0F, 0.0F);
            cut = -1.0F;
            if (queued > 0) {
                queued--;
                begin(view);
            } else if (nextId != null) {
                StonecutterBook.Cut next = nextId;
                nextId = null;
                cut(view, next, nextAll);
            }
        }
    }

    /**
     * The pieces spring up out of the stone where the pass ended and drop beside it on the slab (a knock as they land), lie there a
     * moment and fly into the inventory slot they went into (vanilla's pickup sound as they arrive).
     */
    private void pop(StationView view, float dt) {
        if (popTime < 0.0F || popPiece.isEmpty()) {
            view.node(CUT_OUT).ifPresent(node -> node.setVisible(false));
            return;
        }
        float before = popTime;
        popTime += dt;
        if (before < POP && popTime >= POP) {
            StationHands.play(view, SoundEvents.STONE_HIT, CUT_OUT, 0.2F, 1.5F + 0.2F * StationHands.random(view));
        }
        if (before < POP + LIE && popTime >= POP + LIE) {
            float[] pile = pile();
            Vector3f lying = new Vector3f(pile[0], 9.0F, pile[1]);
            popTo = StationHands.onScene(view, popSlot, lying.y + 2.0F).filter(to -> to.distance(lying) <= 40.0F).orElse(null);
        }
        if (popTime >= POP + LIE + StationHands.FLIGHT_TIME) {
            popTime = -1.0F;
            if (popTo != null) {
                StationHands.play(view, SoundEvents.ITEM_PICKUP, null, 0.2F, 1.4F + 0.4F * StationHands.random(view));
            }
            view.node(CUT_OUT).ifPresent(node -> node.setVisible(false));
            return;
        }
        view.node(CUT_OUT).ifPresent(node -> {
            node.setVisible(true);
            Vector3fc base = node.definition().transform().pos();
            Vector3f at = popAt(base.y());
            node.moveTo(at.x - base.x(), at.y - base.y(), at.z - base.z(), 0.0F);
        });
    }

    /** Where the pieces are now (scene pixels; {@code slab}: the slab's top). */
    private Vector3f popAt(float slab) {
        float[] end = spot(PASS_TO);
        float[] pile = pile();
        if (popTime < POP) {
            float t = popTime / POP;
            float e = ease(t);
            return new Vector3f(Mth.lerp(e, end[0], pile[0]), slab + POP_HEIGHT * 4.0F * t * (1.0F - t), Mth.lerp(e, end[1], pile[1]));
        }
        Vector3f lying = new Vector3f(pile[0], slab, pile[1]);
        if (popTime < POP + LIE || popTo == null) {
            return lying;
        }
        float t = Math.min(1.0F, (popTime - POP - LIE) / StationHands.FLIGHT_TIME);
        return lying.lerp(popTo, ease(t), new Vector3f()).add(0.0F, StationHands.FLIGHT_ARC * 4.0F * t * (1.0F - t), 0.0F);
    }

    private float[] pile() {
        return across ? PILE_ACROSS : PILE_ALONG;
    }

    /** A point on the saw's line, {@code along} pixels along it, as scene x and z. */
    private float[] spot(float along) {
        return across ? new float[] {along, SAW_LINE} : new float[] {SAW_LINE, along};
    }

    /** The stone's place: where it rests on the slab, moved by an offset (scene x, z). */
    private void stone(StationView view, float dx, float dz) {
        float[] rest = across ? REST_ACROSS : REST_ALONG;
        view.node(INPUT).ifPresent(node -> {
            Vector3fc base = node.definition().transform().pos();
            node.moveTo(rest[0] - base.x() + dx, 0.0F, rest[1] - base.z() + dz, 0.0F);
        });
    }

    private static float ease(float t) {
        float c = Mth.clamp(t, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * The stone has come through the saw: the pieces go into the inventory, all of them (the vanilla shift-click on the result) or
     * one (the click on the result, then on the slot a shift-click would fill, with a stack the player held put away first), and
     * spring up. Sent as it comes out, so the stone is seen whole in the saw all the pass long.
     */
    private void take(StationView view) {
        sent = true;
        MenuAccess menu = view.menu();
        StonecutterMenu cutter = stonecutter(view);
        view.node(INPUT).ifPresent(node -> node.scaleTo(1.0F, 0.0F));
        if (menu == null || cutter == null || cutter.getSlot(1).getItem().isEmpty()) {
            return;
        }
        if (!cutAll && !cutter.getCarried().isEmpty()) {
            view.putAwayCarried();
        }
        ItemStack makes = cutter.getSlot(1).getItem();
        int to = landing(cutter, makes, !cutAll);
        if (to < 0 || !cutAll && !cutter.getCarried().isEmpty()) {
            refuse(view);
            return;
        }
        if (cutAll) {
            menu.click(1, 0, ContainerInput.QUICK_MOVE);
        } else {
            menu.click(1, 0, ContainerInput.PICKUP);
            menu.click(to, 0, ContainerInput.PICKUP);
        }
        // The stone used up is the saw's, not taken by hand.
        watch.forget();
        popPiece = cutPiece;
        popTime = 0.0F;
        popSlot = cutter.getSlot(to).getContainerSlot();
        popTo = null;
        StationHands.swing();
    }

    /**
     * The menu slot the pieces go into, as the vanilla shift-click on the result fills the inventory: a stack of them with room
     * first, then an empty slot, each from the hotbar's right end back; -1 when there is none. {@code whole}: all of {@code made}
     * must fit there (one piece put by hand), else any room does (a shift-click spills over).
     */
    private static int landing(StonecutterMenu cutter, ItemStack made, boolean whole) {
        int first = 2;
        int last = cutter.slots.size() - 1;
        for (int i = last; i >= first; i--) {
            ItemStack there = cutter.getSlot(i).getItem();
            int room = Math.min(there.getMaxStackSize(), cutter.getSlot(i).getMaxStackSize(there)) - there.getCount();
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, made) && (whole ? room >= made.getCount() : room > 0)) {
                return i;
            }
        }
        for (int i = last; i >= first; i--) {
            if (!cutter.getSlot(i).hasItem() && cutter.getSlot(i).mayPlace(made)) {
                return i;
            }
        }
        return -1;
    }

    /** Leaving during a cut: the piece is taken now, so the pick does not go with the menu. */
    private void finishNow(StationView view) {
        if (startIn >= 0.0F) {
            startIn = -1.0F;
            begin(view);
        }
        if (cut >= 0.0F && !sent) {
            take(view);
        }
        stopCutting();
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node())) {
            book.click(view, click.button());
        }
    }

    /** The book takes its click with a stack in the hand too. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            book.click(view, button);
            return true;
        }
        return false;
    }

    /** The wheel over the open book turns its pages. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return BOOK.equals(node) && book.scroll(amount);
    }

    /** Keys go to the book first: its search while it is typed, its page keys. A key that leaves finishes the cut under way. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (book.key(view, keyCode, modifiers)) {
            return true;
        }
        if (StationHands.leaves(keyCode, scanCode)) {
            finishNow(view);
        }
        return false;
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    /**
     * The pieces at their node, turned to the camera: standing as they spring up, a flat item tipping over to lie on the slab as they
     * drop (a block stays on its foot), shrinking as they fly away; more than one piece shows as two or three.
     */
    private void pieces(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        boolean block = Draw.isBlock(popPiece, view.level());
        float lie = popTime < POP ? ease(popTime / POP) : 1.0F;
        float size = Mth.lerp(lie, POP_SIZE, PILE_SIZE);
        if (popTime > POP + LIE) {
            float away = ease((popTime - POP - LIE) / StationHands.FLIGHT_TIME);
            size *= popTo == null ? 1.0F - away : 1.0F - 0.4F * away;
        }
        int copies = popPiece.getCount() >= 16 ? 3 : popPiece.getCount() > 1 ? 2 : 1;
        for (int i = copies - 1; i >= 0; i--) {
            poseStack.pushPose();
            // Flat pieces fanned out across the camera's view, each lying a little over the one before; blocks stacked.
            if (block) {
                poseStack.translate(-0.3F * i, size * 0.5F * i, 0.3F * i);
            } else {
                poseStack.translate(-0.8F * i, 0.3F * lie * i, 0.8F * i);
            }
            float up = block ? size * 0.25F : Mth.lerp(lie, size * 0.5F, size / 32.0F + 0.02F);
            poseStack.translate(0.0F, up, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(225.0F));
            if (!block) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F * lie));
            }
            poseStack.scale(size, size, size);
            Draw.item(popPiece, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    poseStack, DiegesisClient.items(buffers), view.level(), 0);
            poseStack.popPose();
        }
    }

    /** Stone dust where the stone meets the saw (under its middle), in the stone's colour, thrown on along the feed and up off the blade. */
    private void dust(StationView view, ItemStack stone, float x, float z) {
        if (!(stone.getItem() instanceof BlockItem block)) {
            return;
        }
        int color = 0xCC000000 | block.getBlock().defaultBlockState().getMapColor(view.level(), view.pos()).col;
        Vector3f throwing = across ? new Vector3f(-DUST_THROW, 6.0F, 0.0F) : new Vector3f(0.0F, 6.0F, -DUST_THROW);
        view.spawnParticles(DiegesisParticles.DUST, new Vector3f(x, 10.0F, z), throwing, 4.0F, DUST, color);
    }

    /** Sends the stone onto the slab from where it was in the inventory. */
    private void flight(StationView view, int slot) {
        float[] rest = across ? REST_ACROSS : REST_ALONG;
        view.node(INPUT).ifPresent(node -> StationHands.fly(view, INPUT, slot,
                new Vector3f(rest[0], node.definition().transform().pos().y(), rest[1])));
    }

    private static StonecutterMenu stonecutter(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof StonecutterMenu cutter ? cutter : null;
    }
}
