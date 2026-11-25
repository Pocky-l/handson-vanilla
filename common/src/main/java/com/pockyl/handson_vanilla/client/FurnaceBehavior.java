package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.MeshShape;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.scene.Visual;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.List;
import java.util.Optional;

/**
 * A furnace worked by hand: the furnace, the smoker or the blast furnace ({@link FurnaceKind}). Its own model (this mod's, always)
 * has its cooking place and fire as real hollows behind doors: the furnace a grate under the mouth and lumps of coal that glow while
 * it burns, the smoker a rack in its chamber and logs that burn below, the blast furnace a grate across its hearth over glowing
 * coals. At the station the block is swapped for the scene's copy without doors and the doors' leaves, which swing open as the
 * player comes and shut as they leave: what cooks stands on the left, what it became on the right, the fuel in the middle of the
 * fire. A thin bar floating over the front edge of the cooking place fills from left to right as the cooking goes. Nothing goes in
 * by itself: the player puts in what they choose. Everything is read from the vanilla menu; all of it is local.
 */
final class FurnaceBehavior implements StationBehavior {
    static final String INPUT = "input";
    static final String FUEL = "fuel";
    static final String RESULT = "result";

    private static final ResourceLocation EMBERS = new ResourceLocation("handson_vanilla", "block/furnace_embers");
    /** Seconds the doors take to swing open or shut. */
    private static final float DOOR_SECONDS = 0.35F;

    /**
     * The progress bar floating over the front edge of the grate, in its node's pixels: half its width (the mouth's), its height, how
     * far it bobs and how fast.
     */
    private static final float BAR_HALF = 4.5F;
    private static final float BAR_HIGH = 0.25F;
    private static final float BOB = 0.08F;
    private static final float BOB_RATE = 2.0F;
    /** Bands of the ember texture for the bar's empty track and its glowing fill. */
    private static final int TRACK = 6;
    private static final int FILL = 1;
    /** How long a choice made here holds against what the server says, ms: a list sent before it heard the choice names the old one. */
    private static final long CHOICE_HEARD_MS = 1000L;
    /** How often a furnace nobody works at asks what lies in it (singleplayer), ticks. */
    private static final int IDLE_ASK_TICKS = 20;
    private final FurnaceKind kind;
    private final Visual body;
    private final Visual bodyLit;
    private int idleTicks;
    private int shownFill = -2;
    private float clock;
    private Boolean lit;
    /** How far the doors are open, 0 shut to 1 open, and whether they are opening. */
    private float doors;
    private boolean opening;
    private int input;
    private int fuel;
    private int result;
    private int resultHeld;
    private ItemStack lastResult = ItemStack.EMPTY;
    private boolean known;
    /** With Polymorph: the other things the input cooks into lie beside the result, a click makes one what this furnace cooks. */
    private final RecipeChoice choice = new RecipeChoice(true, 2.2F, 10.0F);
    private boolean listening;
    /** How many of Polymorph's lists had come when the menu opened: only later ones tell this furnace's choice. */
    private int listsAtOpen;
    /** The recipe this furnace keeps, as far as is known (Polymorph's server told it, or it was chosen here); null: its first. */
    private ResourceLocation kept;
    private long chosenAt;
    /** The input the matches were read for, and those matches. */
    private ItemStack choiceInput;
    private List<RecipeChoice.Option> matches = List.of();
    /** What stands see-through in the empty result place: what the furnace will make, while there is a choice. */
    private ItemStack keptShown = ItemStack.EMPTY;

    FurnaceBehavior(FurnaceKind kind) {
        this.kind = kind;
        this.body = body("diegesis/" + kind.id() + "_body");
        this.bodyLit = body("diegesis/" + kind.id() + "_body_on");
    }

    /** The scene's copy of the block, lit as the world lights the block so the two look the same as they swap. */
    private static Visual body(String model) {
        return new Visual(Optional.of(new ResourceLocation("handson_vanilla", model)), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Visual.DEFAULT_ANCHOR, Visual.WHITE, false, false, false, 0.0F, true);
    }

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(this::usable);
        choice.onSceneReady(view);
        shownIdle(view, INPUT, FurnaceContents.INPUT);
        shownIdle(view, FUEL, FurnaceContents.FUEL);
        shownIdle(view, RESULT, FurnaceContents.RESULT);
    }

    /** Whether this furnace can cook or burn the stack. */
    private boolean usable(ItemStack stack) {
        return FurnaceUse.usable(kind.recipes(), stack);
    }

    /**
     * While nobody works at the furnace its slots are empty to the client (a server sends them only with the menu): what is known
     * to lie there stands in them as it does at the station, seen through the doors.
     */
    private void shownIdle(StationView view, String node, int slot) {
        view.node(node).ifPresent(at -> {
            float size = at.definition().slot().map(binding -> binding.shownItemSize()).orElse(3.0F);
            at.setPainter((poseStack, buffers, light) -> {
                if (view.menu() != null || !kind.on()) {
                    // Off, the furnace is a full block again (vanilla's or a resource pack's): nothing can be seen inside.
                    return;
                }
                ItemStack stack = FurnaceContents.get(view.level(), view.pos(), slot);
                if (stack.isEmpty()) {
                    return;
                }
                // Upright, as the scene stands its slot items.
                Minecraft minecraft = Minecraft.getInstance();
                boolean block = minecraft.getItemRenderer().getModel(stack, view.level(), null, 0).isGui3d();
                poseStack.pushPose();
                poseStack.translate(0.0F, block ? size * 0.25F : size * 0.5F, 0.0F);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(size, size, size);
                minecraft.getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.FIXED, light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, buffers, view.level(), slot);
                poseStack.popPose();
            });
        });
    }

    @Override
    public void onOpen(StationView view) {
        FurnaceUse.refresh();
        shownFill = -2;
        known = false;
        opening = true;
        play(view, kind.openSound(), "body", 0.25F, kind.pitch() + 0.1F * random(view));
    }

    @Override
    public void onMenuOpened(StationView view) {
        remember(view);
        if (RecipeChoice.available()) {
            // As Polymorph's furnace screen does while it is open: its server then tells which recipe this furnace keeps.
            listsAtOpen = PolymorphLink.lists();
            kept = null;
            PolymorphLink.listen(true);
            listening = true;
        }
    }

    @Override
    public void onClose(StationView view) {
        known = false;
        if (listening) {
            PolymorphLink.listen(false);
            listening = false;
        }
        choice.clear(view);
        choiceInput = null;
        showKept(view, ItemStack.EMPTY);
        opening = false;
        play(view, kind.closeSound(), "body", 0.25F, kind.pitch() + 0.1F * random(view));
    }

    /** The doors are still swinging shut after the player left: the scene keeps standing in for the block until they are. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && doors > 0.0F;
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        boolean shown = view.isBlockHidden();
        AbstractFurnaceMenu menu = furnace(view);
        boolean burning = menu != null ? menu.isLit()
                : view.level().getBlockState(view.pos()).getOptionalValue(AbstractFurnaceBlock.LIT).orElse(false);
        boolean changed = lit == null || lit != burning;
        lit = burning;
        view.node("body").ifPresent(body -> {
            body.setVisible(shown);
            if (changed) {
                body.setVisual(burning ? bodyLit : this.body);
            }
        });
        doors(view, dt, shown);
        progress(view, menu);
    }

    /**
     * The leaves swing about their hinges with an ease in and out, each as far and as its kind says (the furnace's along the front,
     * the smoker's door out towards the player, the blast furnace's hatch down and out). A node's rotation is on top of the one its
     * scene gives it.
     */
    private void doors(StationView view, float dt, boolean shown) {
        doors = net.minecraft.util.Mth.clamp(doors + (opening ? dt : -dt) / DOOR_SECONDS, 0.0F, 1.0F);
        float eased = doors * doors * (3.0F - 2.0F * doors);
        for (FurnaceKind.Leaf leaf : kind.leaves()) {
            view.node(leaf.node()).ifPresent(node -> {
                node.setVisible(shown);
                node.rotateTo(leaf.openX() * eased, leaf.openY() * eased, 0.0F, 0.0F);
            });
        }
    }

    /**
     * The cooking as a thin bar floating over the front edge of the cooking place: shown while something cooks, filling from the
     * viewer's left.
     */
    private void progress(StationView view, AbstractFurnaceMenu menu) {
        int fill = menu == null ? 0 : menu.getBurnProgress();
        boolean cooking = menu != null
                && (fill > 0 || menu.isLit() && FurnaceUse.cooks(kind.recipes(), view.state().getItem(SlotRef.of(INPUT, 0))));
        view.node("progress").ifPresent(node -> {
            if (cooking) {
                node.moveTo(0.0F, BOB * (float) Math.sin(clock * BOB_RATE), 0.0F, 0.0F);
            }
        });
        int shown = cooking ? fill : -1;
        if (shown == shownFill) {
            return;
        }
        shownFill = shown;
        view.node("progress").ifPresent(node -> {
            node.setVisible(cooking);
            if (cooking) {
                MeshShape.Builder track = MeshShape.builder();
                bar(track, BAR_HALF, -BAR_HALF, TRACK, 0.0F);
                node.setShape(track.build(), EMBERS);
            }
        });
        view.node("progress_fill").ifPresent(node -> {
            boolean any = cooking && fill > 0;
            node.setVisible(any);
            if (any) {
                MeshShape.Builder glow = MeshShape.builder();
                bar(glow, BAR_HALF, BAR_HALF - 2.0F * BAR_HALF * fill / 24.0F, FILL, -0.02F);
                node.setShape(glow.build(), EMBERS, true);
            }
        });
    }

    /** A strip of the bar facing the front from {@code left} to {@code right} (node x), in one band of the ember texture. */
    private static void bar(MeshShape.Builder shape, float left, float right, int band, float z) {
        float v0 = (band * 2 + 0.25F) / 16.0F;
        float v1 = (band * 2 + 0.75F) / 16.0F;
        // One texel of the band stretched along the bar, so it is one even colour.
        float u0 = 13.25F / 16.0F;
        float u1 = 13.75F / 16.0F;
        quad(shape, 0, 0, -1, new float[] {left, 0, z, u0, v1}, new float[] {right, 0, z, u1, v1},
                new float[] {right, BAR_HIGH, z, u1, v0}, new float[] {left, BAR_HIGH, z, u0, v0});
    }

    /** A face from four corners {x, y, z, u, v}, turned to face along its normal whichever way they were given. */
    private static void quad(MeshShape.Builder shape, float nx, float ny, float nz, float[] a, float[] b, float[] c, float[] d) {
        Vector3f normal = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).cross(c[0] - b[0], c[1] - b[1], c[2] - b[2]);
        float[][] corners = normal.dot(nx, ny, nz) >= 0 ? new float[][] {a, b, c, d} : new float[][] {d, c, b, a};
        for (float[] corner : corners) {
            shape.corner(corner[0], corner[1], corner[2], corner[3], corner[4], nx, ny, nz);
        }
    }

    @Override
    public void tick(StationView view) {
        if (view.menu() != null) {
            FurnaceContents.put(view.level(), view.pos(), view.state().getItem(SlotRef.of(INPUT, 0)), view.state().getItem(SlotRef.of(FUEL, 0)),
                    view.state().getItem(SlotRef.of(RESULT, 0)));
        } else if (++idleTicks % IDLE_ASK_TICKS == 0 && kind.on()) {
            FurnaceContents.ask(view.level(), view.pos());
        }
        choices(view);
        if (!view.isFocused() || view.menu() == null) {
            return;
        }
        if (!known) {
            remember(view);
            return;
        }
        int nowInput = count(view, INPUT);
        int nowFuel = count(view, FUEL);
        int nowResult = count(view, RESULT);
        if (nowInput > input) {
            arrived(view, INPUT);
        }
        if (nowFuel > fuel) {
            arrived(view, FUEL);
        }
        if (nowResult < result && held(lastResult) > resultHeld) {
            // Taken by the hand: still hot.
            play(view, SoundEvents.FIRE_EXTINGUISH, RESULT, 0.12F, 1.6F + 0.2F * random(view));
            swing();
        }
        remember(view);
    }

    /**
     * With Polymorph, what else the input cooks into lies beside the result, and what this furnace will make stands see-through in
     * its empty result place. Which recipe the furnace keeps comes from Polymorph's server; until it tells, the first, as the server
     * takes it; a choice made here holds until the server has heard it.
     */
    private void choices(StationView view) {
        if (!listening || view.menu() == null || !view.isFocused()) {
            choice.clear(view);
            choiceInput = null;
            showKept(view, ItemStack.EMPTY);
            return;
        }
        ResourceLocation told = PolymorphLink.told(listsAtOpen);
        if (told != null && net.minecraft.Util.getMillis() - chosenAt > CHOICE_HEARD_MS) {
            kept = told;
        }
        ItemStack in = view.state().getItem(SlotRef.of(INPUT, 0));
        if (choiceInput == null || !ItemStack.isSameItemSameTags(in, choiceInput)) {
            choiceInput = in.copy();
            matches = FurnaceUse.matches(kind.recipes(), in);
        }
        ItemStack making = matches.stream().filter(option -> option.recipe().equals(kept)).findFirst()
                .or(() -> matches.stream().findFirst()).map(RecipeChoice.Option::output).orElse(ItemStack.EMPTY);
        choice.show(view, matches, making);
        showKept(view, RecipeChoice.shownPoints().isEmpty() ? ItemStack.EMPTY : making);
    }

    private void showKept(StationView view, ItemStack making) {
        if (ItemStack.matches(making, keptShown)) {
            return;
        }
        keptShown = making.copy();
        view.node(RESULT).ifPresent(node -> node.setGhostItems(making.isEmpty() ? null : List.of(making)));
    }

    private void choose(StationView view, int place) {
        ResourceLocation chosen = choice.click(view, place);
        if (chosen != null) {
            kept = chosen;
            chosenAt = net.minecraft.Util.getMillis();
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && RecipeChoice.place(click.node()) >= 0) {
            choose(view, RecipeChoice.place(click.node()));
        }
    }

    /** A choice is made with the hand full too: what is held stays held. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (RecipeChoice.place(node) >= 0) {
            choose(view, RecipeChoice.place(node));
            return true;
        }
        return false;
    }

    /** Something came into a slot. */
    private void arrived(StationView view, String slot) {
        play(view, SoundEvents.STONE_HIT, slot, 0.3F, 1.1F + 0.2F * random(view));
    }

    private void remember(StationView view) {
        input = count(view, INPUT);
        fuel = count(view, FUEL);
        ItemStack out = view.state().getItem(SlotRef.of(RESULT, 0));
        result = out.getCount();
        if (!out.isEmpty()) {
            lastResult = out.copy();
        }
        resultHeld = held(lastResult);
        known = true;
    }

    private static int count(StationView view, String slot) {
        return view.state().getItem(SlotRef.of(slot, 0)).getCount();
    }

    private static AbstractFurnaceMenu furnace(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof AbstractFurnaceMenu furnace ? furnace : null;
    }

    /** How many of {@code stack}'s item the local player holds: inventory and cursor. */
    private static int held(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || stack.isEmpty()) {
            return 0;
        }
        int count = player.containerMenu.getCarried().is(stack.getItem()) ? player.containerMenu.getCarried().getCount() : 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack there = player.getInventory().getItem(i);
            if (there.is(stack.getItem())) {
                count += there.getCount();
            }
        }
        return count;
    }

    /** The arm moves as the hand works; only this client sees it, nothing is sent. */
    private static void swing() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND, false);
        }
    }

    private static float random(StationView view) {
        return view.level().getRandom().nextFloat();
    }

    private static void play(StationView view, SoundEvent sound, String node, float volume, float pitch) {
        float scaled = volume * VanillaConfig.soundVolume();
        if (scaled > 0.0F) {
            view.playSound(sound, view.node(node).isPresent() ? node : null, scaled, pitch);
        }
    }
}
