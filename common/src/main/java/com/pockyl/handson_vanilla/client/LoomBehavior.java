package com.pockyl.handson_vanilla.client;

import com.pockyl.diegesis.impl.client.render.SceneDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.LightCoordsUtil;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.SlotRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The loom worked by hand. Banner, dye and pattern item lie on the loom in the vanilla screen's order; the banner itself hangs over
 * the loom's far half as its cloth, and the loom's book hangs beside it with a page for
 * each pattern. The pattern under the cursor shows on the banner as one more layer, faint, so a click is never blind; a click weaves
 * it: the menu's button, a shuttle carrying the dye's thread through the cloth, and the woven banner taken and laid back on the loom
 * for the next layer (with several banners, one flies into the inventory; Shift weaves the whole stack into it). A result that stands by
 * itself (a pattern item with one pattern) is taken by a click on it. Vanilla's take sound comes from the server.
 */
final class LoomBehavior implements StationBehavior {
    static final String BANNER = "banner";
    static final String DYE = "dye";
    static final String PATTERN = "pattern";
    private static final String RESULT = "result";
    private static final String WOVEN_OUT = "woven_out";
    private static final String BOOK = "book_open";
    private static final int BANNER_SLOT = 0;
    private static final int DYE_SLOT = 1;
    private static final int RESULT_SLOT = 3;
    /**
     * The banner lies on the loom as its cloth: vanilla's flag (20 x 40 model pixels) flat on the top, its upper edge at the node by the
     * far edge, its foot towards the camera, clear of the three places on the near edge. A model pixel of it in scene pixels.
     */
    private static final float CLOTH = 0.24F;
    /** How the brush leans back towards the camera, degrees. */
    private static final float BRUSH_TILT = 20.0F;
    /** How big woven banners are on their way into the inventory. */
    private static final float FLY_SIZE = 3.6F;
    /** The preview layer: how see-through, how fast it comes. */
    private static final float PREVIEW_ALPHA = 0.65F;
    private static final float PREVIEW_IN = 0.1F;
    /**
     * The weave, done with vanilla's brush: it dips into the dye on the loom, goes to the cloth and paints the pattern where the
     * pattern has colour, band after band from the top, then lifts away. Seconds: the dip, the way to the cloth, the painting (by
     * how much there is to paint), the lift; when the woven banner is taken (early, so the server has it whatever happens next).
     */
    private static final float DIP = 0.35F;
    private static final float TRAVEL = 0.15F;
    private static final float PAINT_LEAST = 0.5F;
    private static final float PAINT_MOST = 1.4F;
    private static final float PAINT_PIXELS = 600.0F;
    private static final float LIFT = 0.2F;
    private static final float TAKE_AT = DIP + 0.05F;
    /** The brush: its size, pixels; where its bristles' tip is in the item (of 1); how far before the cloth it paints, model pixels. */
    private static final ItemStack BRUSH = new ItemStack(net.minecraft.world.item.Items.BRUSH);
    private static final float BRUSH_SIZE = 4.0F;
    private static final float TIP_X = 0.28F;
    private static final float TIP_Y = 0.32F;
    private static final float BRUSH_OFF = 0.8F;
    /** Seconds between two strokes' sounds. */
    private static final float STROKE_SOUND = 0.3F;
    /** A banner laid on the loom unrolls down from the bar, seconds. */
    private static final float UNFURL = 0.35F;
    /** A poke at the cloth makes it bounce: how fast that dies away a second. */
    private static final float POKE_DAMP = 3.0F;
    /** Picks kept during a pass, to be woven after it. */
    private static final int MOST_QUEUED = 4;

    private record Pick(Identifier pattern, boolean all) {
    }

    /** A woven banner on its way from the loom into the inventory (scene pixels), and when it set off. */
    private record Flight(ItemStack stack, Vector3f from, Vector3f to, float at) {
    }

    private final RecipeBook book = new RecipeBook(new LoomBook(this));
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(BANNER, "banner", DYE, "dye", PATTERN, "pattern");
    private final ArrayDeque<Pick> queue = new ArrayDeque<>();
    private ModelPart flag;
    private final List<Flight> flights = new ArrayList<>();
    private float clock;
    /** The pattern under the cursor in the book, and how far its layer has come in on the banner. */
    private Identifier preview;
    private Identifier previewShownKey;
    private float previewShown;
    private float hover;
    /** The pass under way: seconds into it (negative: none), whether its take went, which way it runs, whether it weaves all. */
    private float pass = -1.0F;
    private boolean taken;
    private boolean passAll;
    private int dustColor = 0xFFFFFFFF;
    /** The paint of the layer being woven, how long its painting takes and the whole work, and when the brush last sounded. */
    private final BannerPaint paint = new BannerPaint();
    private float paintTime;
    private float work;
    private float strokeSoundAt;
    private boolean dipped;
    /** The banner as it was before the weave under way, and the layer the weave adds: drawn growing until the weave ends. */
    private ItemStack weaveBase = ItemStack.EMPTY;
    private Pair<Holder<BannerPattern>, DyeColor> weaveLayer;
    /** When the banner on the loom was laid (it unrolls), and how hard the cloth swings from a poke. */
    private float unfurlAt = -10.0F;
    private float poke;

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(LoomBehavior::usable);
        view.node(BOOK).ifPresent(node -> node.setPainter(book));
        view.node(RESULT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> paintBanner(view, poseStack, buffers, light)));
        view.node(WOVEN_OUT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> paintFlights(view, poseStack, buffers,
                light)));
    }

    /**
     * What the loom takes from the inventory: what one of its three places takes, by the open menu's own rule (other mods' cloths
     * too, as Supplementaries' flags); before the menu is open, banners, dyes and banner patterns.
     */
    private static boolean usable(ItemStack stack) {
        if (StationHover.menu() instanceof LoomMenu loom) {
            return loom.getSlot(BANNER_SLOT).mayPlace(stack) || loom.getSlot(DYE_SLOT).mayPlace(stack) || loom.getSlot(2).mayPlace(stack);
        }
        return stack.getItem() instanceof BannerItem || stack.is(net.minecraft.tags.ItemTags.LOOM_DYES) && stack.has(DataComponents.DYE)
                || stack.is(net.minecraft.tags.ItemTags.LOOM_PATTERNS) && stack.has(DataComponents.PROVIDES_BANNER_PATTERNS);
    }

    private static final java.util.Map<Class<?>, java.util.Optional<java.lang.reflect.Method>> CLOTH_COLOUR =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The base colour of what lies in the banner's place, or null when it is no cloth: a banner's, or that of another mod's
     * cloth the loom takes there, whose item tells its colour by a {@code getColor()} of its own (Supplementaries' flags, woven
     * here as banners are, their layers kept in the same data).
     */
    static DyeColor clothColour(ItemStack stack) {
        if (stack.getItem() instanceof BannerItem banner) {
            return banner.getColor();
        }
        if (stack.isEmpty()) {
            return null;
        }
        java.util.Optional<java.lang.reflect.Method> method = CLOTH_COLOUR.computeIfAbsent(stack.getItem().getClass(), type -> {
            try {
                java.lang.reflect.Method found = type.getMethod("getColor");
                return found.getReturnType() == DyeColor.class && found.getParameterCount() == 0
                        ? java.util.Optional.of(found) : java.util.Optional.empty();
            } catch (NoSuchMethodException | SecurityException e) {
                return java.util.Optional.empty();
            }
        });
        try {
            return method.isPresent() ? (DyeColor) method.get().invoke(stack.getItem()) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void onOpen(StationView view) {
        queue.clear();
        pass = -1.0F;
        weaveBase = ItemStack.EMPTY;
        weaveLayer = null;
        poke = 0.0F;
        unfurlAt = -10.0F;
        preview = null;
        watch.forget();
        book.opened(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        queue.clear();
        pass = -1.0F;
        preview = null;
    }

    /** Pieces put onto the loom and taken off it by hand sound. */
    @Override
    public void tick(StationView view) {
        watch.tick(view, (node, change) -> {
            if (change > 0) {
                StationHands.play(view, SoundEvents.ITEM_FRAME_ADD_ITEM, node, 0.3F, 0.9F + 0.2F * StationHands.random(view));
                if (BANNER.equals(node)) {
                    // A banner laid on the loom unrolls on the frame with a soft wool sound.
                    unfurlAt = clock;
                    StationHands.play(view, SoundEvents.WOOL_PLACE, RESULT, 0.35F, 1.1F + 0.2F * StationHands.random(view));
                } else if (DYE.equals(node)) {
                    splashDye(view);
                } else if (PATTERN.equals(node)) {
                    StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, node, 0.3F, 1.3F + 0.2F * StationHands.random(view));
                }
            } else {
                StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, node, 0.3F, 0.9F + 0.2F * StationHands.random(view));
            }
        });
    }

    /** A dye laid: a puff of its colour and the dye's own sound, as the dye is ready for the thread. */
    private static void splashDye(StationView view) {
        LoomMenu loom = loom(view);
        DyeColor dyeColour = loom == null ? null : loom.getSlot(DYE_SLOT).getItem().get(DataComponents.DYE);
        if (dyeColour == null) {
            return;
        }
        int color = rgb(dyeColour);
        view.node(DYE).ifPresent(node -> {
            Vector3f at = new Vector3f(node.definition().transform().pos()).add(0.0F, 0.8F, 0.0F);
            view.spawnParticles(DiegesisParticles.DUST, at, new Vector3f(0.0F, 2.5F, 0.0F), 2.0F, 10, color);
        });
        StationHands.play(view, SoundEvents.DYE_USE, DYE, 0.5F, 1.0F + 0.2F * StationHands.random(view));
    }

    private static int rgb(DyeColor color) {
        return 0xFF000000 | color.getTextureDiffuseColor();
    }

    /** How many layers a banner bears. */
    private static int layers(ItemStack banner) {
        return banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
    }

    /** What lies in one of the loom's places. */
    ItemStack stack(StationView view, String group) {
        return view.state().getItem(SlotRef.of(group, 0));
    }

    /** The pattern under the cursor in the book (null: none). */
    void preview(Identifier pattern) {
        preview = pattern;
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        book.frame(view, dt);
        boolean shows = preview != null && pass < 0.0F;
        if (shows && !preview.equals(previewShownKey)) {
            previewShownKey = preview;
            previewShown = 0.0F;
        }
        previewShown = Mth.clamp(previewShown + (shows ? dt : -dt) / PREVIEW_IN, 0.0F, 1.0F);
        LoomMenu loom = loom(view);
        ItemStack result = loom == null ? ItemStack.EMPTY : loom.getSlot(RESULT_SLOT).getItem();
        boolean standing = !result.isEmpty() && pass < 0.0F;
        view.node(RESULT).ifPresent(node -> {
            node.setTooltipItem(standing ? result : ItemStack.EMPTY);
            hover = Mth.lerp(Math.min(1.0F, dt * 14.0F), hover, standing && node.isHovered() ? 1.0F : 0.0F);
        });
        flights.removeIf(flight -> clock - flight.at() >= StationHands.FLIGHT_TIME);
        view.node(WOVEN_OUT).ifPresent(node -> node.setVisible(!flights.isEmpty()));
        poke *= (float) Math.exp(-POKE_DAMP * dt);
        if (pass < 0.0F) {
            return;
        }
        pass += dt;
        if (!dipped && pass >= DIP * 0.5F) {
            // The bristles touch the dye: a puff of its colour and the dye's sound.
            dipped = true;
            view.node(DYE).ifPresent(node -> {
                Vector3f at = new Vector3f(node.definition().transform().pos()).add(0.0F, 0.8F, 0.0F);
                view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 2.0F, 0.0F), 1.5F, 8, dustColor);
            });
            StationHands.play(view, SoundEvents.DYE_USE, DYE, 0.5F, 1.1F + 0.2F * StationHands.random(view));
        }
        float painted = paintedPart();
        if (painted > 0.0F && painted < 1.0F) {
            paint.paintTo(painted);
            if (pass - strokeSoundAt >= STROKE_SOUND) {
                strokeSoundAt = pass;
                StationHands.play(view, SoundEvents.BRUSH_GENERIC, RESULT, 0.45F, 1.1F + 0.3F * StationHands.random(view));
            }
            if (view.level().getRandom().nextFloat() < dt * 18.0F) {
                Vector3f tip = brushTip(view);
                if (tip != null) {
                    view.node(RESULT).ifPresent(node -> view.spawnParticles(DiegesisParticles.DUST,
                            new Vector3f(node.definition().transform().pos()).add(tip), new Vector3f(0.0F, -1.5F, -0.5F), 0.4F, 1,
                            dustColor));
                }
            }
        } else if (painted >= 1.0F) {
            paint.paintTo(1.0F);
        }
        if (!taken && pass >= TAKE_AT) {
            take(view);
        }
        if (pass >= work) {
            pass = -1.0F;
            weaveBase = ItemStack.EMPTY;
            weaveLayer = null;
            if (!queue.isEmpty()) {
                Pick next = queue.poll();
                start(view, next.pattern(), next.all());
            }
        }
    }

    /** How far the painting is, 0 (not begun: dipping, on its way) to 1 (done). */
    private float paintedPart() {
        if (pass < 0.0F || paintTime <= 0.0F) {
            return pass < 0.0F ? 0.0F : pass >= DIP + TRAVEL ? 1.0F : 0.0F;
        }
        return Mth.clamp((pass - DIP - TRAVEL) / paintTime, 0.0F, 1.0F);
    }

    /**
     * Where the brush's bristles touch the cloth now, scene pixels from the banner frame's feet: the flag's pixel (column, row of its
     * front) taken through the cloth's pose (vanilla's flag 20 x 40 lying on the loom); null while not painting.
     */
    private Vector3f brushTip(StationView view) {
        float[] at = paint.brushAt(paintedPart());
        return at == null ? null : clothPoint(at[0], at[1], -2.0F - BRUSH_OFF);
    }

    /** A point of vanilla's flag (its front's column and row, model z) in scene pixels from the cloth's upper edge at the node. */
    private static Vector3f clothPoint(float column, float row, float modelZ) {
        float mx = 9.5F - column;
        float my = row + 0.5F - 32.0F;
        Vector3f p = new Vector3f(-CLOTH * mx, -CLOTH * my - 32.0F * CLOTH, CLOTH * modelZ);
        return p.rotateX(90.0F * Mth.DEG_TO_RAD);
    }

    /** A page picked in the book: woven now, or after the pass under way. */    /** A page picked in the book: woven now, or after the pass under way. */
    void weave(StationView view, Identifier pattern, boolean all) {
        if (pass >= 0.0F) {
            if (queue.size() < MOST_QUEUED) {
                queue.add(new Pick(pattern, all));
            }
            return;
        }
        start(view, pattern, all);
    }

    /**
     * The weave: banner and dye must lie on the loom (else the empty place shakes), the banner bear fewer layers than its limit ({@link BannerLimit}), the pattern
     * be one the loom offers now (looked up at the pick, never kept); then the menu's button and the shuttle's pass, whose middle
     * takes the woven banner.
     */
    private void start(StationView view, Identifier pattern, boolean all) {
        LoomMenu loom = loom(view);
        MenuAccess menu = view.menu();
        if (loom == null || menu == null) {
            return;
        }
        ItemStack banner = loom.getSlot(BANNER_SLOT).getItem();
        ItemStack dye = loom.getSlot(DYE_SLOT).getItem();
        if (banner.isEmpty() || dye.isEmpty()) {
            refuse(view, banner.isEmpty() ? BANNER : DYE);
            return;
        }
        if (layers(banner) >= BannerLimit.of(banner)) {
            refuse(view, RESULT);
            return;
        }
        int index = indexOf(loom.getSelectablePatterns(), pattern);
        if (index < 0 || all && !StationHands.inventoryHasRoom(banner)) {
            refuse(view, RESULT);
            return;
        }
        if (!loom.getCarried().isEmpty()) {
            view.putAwayCarried();
        }
        if (index != loom.getSelectedBannerPatternIndex()) {
            menu.pressButton(index);
        }
        if (!loom.getSlot(RESULT_SLOT).hasItem()) {
            refuse(view, RESULT);
            return;
        }
        // The vanilla screen plays the select sound itself when a pattern is clicked.
        StationHands.play(view, SoundEvents.UI_LOOM_SELECT_PATTERN, RESULT, 0.3F, 1.0F);
        DyeColor color = dye.getOrDefault(DataComponents.DYE, DyeColor.WHITE);
        dustColor = rgb(color);
        pass = 0.0F;
        taken = false;
        dipped = false;
        strokeSoundAt = -10.0F;
        passAll = all;
        // The banner as it is now stays on the frame while the new layer grows up its cloth; the woven one shows once the weave ends.
        weaveBase = banner.copyWithCount(1);
        weaveLayer = Pair.of(loom.getSelectablePatterns().get(index), color);
        // The brush paints the pattern's own pixels: the more there is to paint, the longer, within bounds.
        boolean painting = paint.begin(weaveLayer.getFirst());
        paintTime = painting ? Mth.clamp(PAINT_LEAST + paint.size() / PAINT_PIXELS, PAINT_LEAST, PAINT_MOST) : 0.0F;
        work = DIP + TRAVEL + paintTime + LIFT;
        view.node(RESULT).ifPresent(node -> node.shake(0.15F, 0.2F));
        StationHands.swing();
    }

    /**
     * The take, in the middle of the pass: one banner on the loom goes back onto it woven (the click on the result, then on the
     * banner's place); of several, one goes into the inventory where a shift-click would put it; Shift takes them all (the vanilla
     * shift-click on the result).
     */
    private void take(StationView view) {
        taken = true;
        LoomMenu loom = loom(view);
        MenuAccess menu = view.menu();
        if (loom == null || menu == null || !loom.getSlot(RESULT_SLOT).hasItem()) {
            return;
        }
        ItemStack made = loom.getSlot(RESULT_SLOT).getItem().copy();
        int banners = loom.getSlot(BANNER_SLOT).getItem().getCount();
        if (passAll) {
            if (StationHands.inventoryHasRoom(made)) {
                int to = landing(loom, made);
                int slot = to >= 0 ? loom.getSlot(to).getContainerSlot() : -1;
                menu.click(RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
                for (int i = 0; i < Math.min(3, banners); i++) {
                    flyOut(view, made, slot, work - TAKE_AT + i * 0.06F);
                }
            }
        } else if (banners <= 1) {
            menu.click(RESULT_SLOT, 0, ContainerInput.PICKUP);
            menu.click(BANNER_SLOT, 0, ContainerInput.PICKUP);
        } else {
            int to = landing(loom, made);
            if (to < 0) {
                return;
            }
            menu.click(RESULT_SLOT, 0, ContainerInput.PICKUP);
            menu.click(to, 0, ContainerInput.PICKUP);
            // It leaves the frame once it is woven to the top.
            flyOut(view, made, loom.getSlot(to).getContainerSlot(), work - TAKE_AT);
        }
        // What the weave used up and laid back is the loom's, not taken or put by hand.
        watch.forget();
    }

    /** A woven banner sets off from the cloth over the loom to inventory slot {@code slot} (-1: the inventory as a whole). */
    private void flyOut(StationView view, ItemStack made, int slot, float later) {
        view.node(RESULT).ifPresent(node -> {
            // From the middle of the cloth lying on the loom.
            Vector3f from = new Vector3f(node.definition().transform().pos()).add(0.0F, 1.0F, -20.0F * CLOTH);
            StationHands.onScene(view, slot, from.y).filter(to -> to.distance(from) <= 40.0F)
                    .ifPresent(to -> flights.add(new Flight(made.copyWithCount(1), from, to, clock + later)));
        });
    }

    /** The woven banners flying into the inventory, shrinking a little on the way, in an arc. */
    private void paintFlights(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        Vector3fc base = view.node(WOVEN_OUT).map(node -> node.definition().transform().pos()).orElse(new Vector3f());
        for (Flight flight : flights) {
            float t = (clock - flight.at()) / StationHands.FLIGHT_TIME;
            if (t < 0.0F || t >= 1.0F) {
                continue;
            }
            float e = t * t * (3.0F - 2.0F * t);
            Vector3f at = new Vector3f(flight.from()).lerp(flight.to(), e).add(0.0F, StationHands.FLIGHT_ARC * 4.0F * t * (1.0F - t), 0.0F)
                    .sub(base.x(), base.y(), base.z());
            float size = FLY_SIZE * (1.0F - 0.35F * e);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(size, size, size);
            SceneDraw.item(flight.stack(), ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, poseStack, DiegesisClient.items(buffers), view.level(), 0);
            poseStack.popPose();
        }
    }

    /** The inventory slot one woven banner goes into, as a shift-click fills it: a stack of it with room, else an empty slot; -1: none. */
    private static int landing(LoomMenu loom, ItemStack made) {
        int first = 4;
        int last = loom.slots.size() - 1;
        for (int i = last; i >= first; i--) {
            ItemStack there = loom.getSlot(i).getItem();
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, made) && there.getCount() < there.getMaxStackSize()) {
                return i;
            }
        }
        for (int i = last; i >= first; i--) {
            if (!loom.getSlot(i).hasItem()) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOf(List<Holder<BannerPattern>> list, Identifier pattern) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).unwrapKey().map(key -> key.identifier().equals(pattern)).orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    /** A pick that cannot be woven: the thing missing shakes, a dull knock. */
    private static void refuse(StationView view, String node) {
        view.node(node).ifPresent(it -> it.shake(0.3F, 0.2F));
        StationHands.play(view, SoundEvents.BOOK_PUT, node, 0.25F, 0.8F);
    }

    /** A click on the cloth with nothing to take sets it swinging, with a soft wool sound. */
    private void pokeCloth(StationView view) {
        LoomMenu loom = loom(view);
        if (loom == null || pass >= 0.0F || !loom.getSlot(BANNER_SLOT).hasItem()) {
            return;
        }
        poke = 1.0F;
        StationHands.play(view, SoundEvents.WOOL_HIT, RESULT, 0.35F, 0.9F + 0.2F * StationHands.random(view));
    }

    /**
     * A click on a banner that stands by itself takes it: onto the cursor, or with Shift into the inventory. False when nothing stands
     * there to take.
     */
    private boolean takeStanding(StationView view, boolean all) {
        LoomMenu loom = loom(view);
        MenuAccess menu = view.menu();
        if (loom == null || menu == null || pass >= 0.0F) {
            return pass >= 0.0F;
        }
        ItemStack made = loom.getSlot(RESULT_SLOT).getItem();
        if (made.isEmpty()) {
            return false;
        }
        ItemStack held = loom.getCarried();
        boolean fits = all ? StationHands.inventoryHasRoom(made)
                : held.isEmpty() || ItemStack.isSameItemSameComponents(held, made) && held.getCount() + made.getCount() <= held.getMaxStackSize();
        if (!fits) {
            refuse(view, RESULT);
            return true;
        }
        menu.click(RESULT_SLOT, 0, all ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
        watch.forget();
        StationHands.swing();
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click) {
            if (BOOK.equals(click.node())) {
                book.click(view, click.button());
            } else if (RESULT.equals(click.node()) && click.button() == 0) {
                if (!takeStanding(view, Minecraft.getInstance().hasShiftDown())) {
                    pokeCloth(view);
                }
            }
        }
    }

    /** The book takes its click with a stack in the hand too; a standing banner goes onto a matching stack. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            book.click(view, button);
            return true;
        }
        if (RESULT.equals(node)) {
            if (button == 0 && !takeStanding(view, shift)) {
                pokeCloth(view);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return BOOK.equals(node) && book.scroll(amount);
    }

    /** Keys go to the book first (its search, its page keys). A key that leaves takes the banner of the pass under way first. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (book.key(view, keyCode, modifiers)) {
            return true;
        }
        if (StationHands.leaves(keyCode, scanCode) && pass >= 0.0F && !taken) {
            take(view);
        }
        return false;
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    // The banner.

    /**
     * The banner lying on the loom as its cloth: a woven result standing by itself (lit up and raised under the cursor), else the
     * banner on the loom with the pattern under the cursor as one more layer, faint and breathing, else the banner on the loom as it
     * is; with no banner, nothing.
     */
    private void paintBanner(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        LoomMenu loom = loom(view);
        if (loom == null) {
            return;
        }
        if (flag == null) {
            var root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG);
            flag = root.getChild("flag");
        }
        ItemStack result = loom.getSlot(RESULT_SLOT).getItem();
        ItemStack banner = loom.getSlot(BANNER_SLOT).getItem();
        boolean standing = !result.isEmpty() && pass < 0.0F;
        boolean weaving = pass >= 0.0F && !weaveBase.isEmpty();
        ItemStack shown = weaving ? weaveBase : standing ? result : banner;
        int lit = standing && hover > 0.5F ? LightCoordsUtil.FULL_BRIGHT : light;
        float size = 16.0F * CLOTH;
        poseStack.pushPose();
        // Flat on the loom, a hair over its top; raised a little when it can be taken (under the cursor) and bouncing when poked.
        poseStack.translate(0.0F, 0.05F + 0.3F * hover + 0.6F * poke * Math.abs(Mth.sin(clock * 11.0F)), 0.0F);
        // Vanilla's banner, its face (not its mirrored back) upwards, its upper edge at the node; model y runs down the cloth.
        poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
        poseStack.translate(0.0F, -32.0F * CLOTH, 0.0F);
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(size, -size, -size);
        DyeColor base = clothColour(shown);
        if (base != null) {
            Pair<Holder<BannerPattern>, DyeColor> layer = null;
            if (!standing && previewShown > 0.0F && previewShownKey != null
                    && loom.getSlot(DYE_SLOT).getItem().get(DataComponents.DYE) instanceof DyeColor dyeColour
                    && layers(banner) < BannerLimit.of(banner)) {
                List<Holder<BannerPattern>> list = LoomBook.patterns(view, loom.getSlot(2).getItem());
                int index = indexOf(list, previewShownKey);
                if (index >= 0) {
                    layer = Pair.of(list.get(index), dyeColour);
                }
            }
            // It lies still: no wave.
            flag.xRot = 0.0F;
            flag.y = -32.0F;
            // Laid on the loom, it unrolls from its upper edge towards the camera.
            float unrolled = Mth.clamp((clock - unfurlAt) / UNFURL, 0.0F, 1.0F);
            flag.yScale = weaving || standing ? 1.0F : Math.max(0.02F, 1.0F - (1.0F - unrolled) * (1.0F - unrolled) * (1.0F - unrolled));
            patterns(poseStack, buffers, lit, flag, base, shown.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
            if (weaving && weaveLayer != null && paintedPart() > 0.0F) {
                // The paint so far, on the flag's own faces (so at its depth), drawn after the banner's layers, in the dye's colour.
                flag.render(poseStack, buffers.getBuffer(RenderTypes.entityTranslucent(BannerPaint.texture())), lit,
                        OverlayTexture.NO_OVERLAY, rgb(weaveLayer.getSecond()));
                layer = null;
            }
            flag.yScale = 1.0F;
            if (layer != null) {
                Pair<Holder<BannerPattern>, DyeColor> faint = layer;
                float alpha = PREVIEW_ALPHA * previewShown * (0.85F + 0.15F * Mth.sin(clock * 3.0F));
                int argb = Math.round(alpha * 255.0F) << 24 | rgb(faint.getSecond()) & 0xFFFFFF;
                TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(Sheets.getBannerSprite(faint.getFirst()));
                flag.render(poseStack, sprite.wrap(buffers.getBuffer(RenderTypes.entityTranslucent(sprite.atlasLocation()))), lit,
                        OverlayTexture.NO_OVERLAY, argb);
            }
        }
        poseStack.popPose();
        paintBrush(view, poseStack, buffers, light);
    }

    /**
     * The flag's cloth, its base layer in {@code base} and its layers, as {@code BannerRenderer.submitPatterns} submits them for a
     * banner; drawn into the scene's buffers.
     */
    private static void patterns(PoseStack poseStack, SceneBuffers buffers, int light, ModelPart flag, DyeColor base,
            BannerPatternLayers patterns) {
        TextureAtlasSprite cloth = Minecraft.getInstance().getAtlasManager().get(Sheets.BANNER_BASE);
        flag.render(poseStack, cloth.wrap(buffers.getBuffer(RenderTypes.entitySolid(cloth.atlasLocation()))), light,
                OverlayTexture.NO_OVERLAY);
        patternLayer(poseStack, buffers, light, flag, Sheets.BANNER_PATTERN_BASE, base);
        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            BannerPatternLayers.Layer layer = patterns.layers().get(i);
            patternLayer(poseStack, buffers, light, flag, Sheets.getBannerSprite(layer.pattern()), layer.color());
        }
    }

    private static void patternLayer(PoseStack poseStack, SceneBuffers buffers, int light, ModelPart flag,
            net.minecraft.client.resources.model.sprite.SpriteId id, DyeColor colour) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(id);
        flag.render(poseStack, sprite.wrap(buffers.getBuffer(RenderTypes.bannerPattern(sprite.atlasLocation()))), light,
                OverlayTexture.NO_OVERLAY, colour.getTextureDiffuseColor());
    }

    /**
     * Vanilla's brush at work, in the frame's feet's space: over the dye, dipping into it; on its way to the cloth; its bristles on
     * the pixel being painted, flicking a little; lifting away once the pattern is painted.
     */
    private void paintBrush(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        if (pass < 0.0F || weaveBase.isEmpty()) {
            return;
        }
        Vector3fc feet = view.node(RESULT).map(node -> node.definition().transform().pos()).orElse(new Vector3f());
        Vector3f overDye = view.node(DYE).map(node -> new Vector3f(node.definition().transform().pos()).sub(feet).add(0.0F, 2.5F, 0.0F))
                .orElse(new Vector3f(0.0F, 2.5F, -6.0F));
        float[] first = paint.brushAt(0.0F);
        Vector3f onCloth = first == null ? clothPoint(10.0F, 20.0F, -2.0F - BRUSH_OFF) : clothPoint(first[0], first[1], -2.0F - BRUSH_OFF);
        Vector3f tip;
        float size = BRUSH_SIZE;
        float flick = 0.0F;
        if (pass < DIP) {
            float t = pass / DIP;
            tip = new Vector3f(overDye).sub(0.0F, 1.9F * Mth.sin(t * Mth.PI), 0.0F);
        } else if (pass < DIP + TRAVEL) {
            float t = (pass - DIP) / TRAVEL;
            tip = new Vector3f(overDye).lerp(onCloth, t * t * (3.0F - 2.0F * t));
        } else if (paintedPart() < 1.0F) {
            tip = brushTip(view);
            flick = 12.0F * Mth.sin(clock * 30.0F);
        } else {
            float t = Mth.clamp((pass - DIP - TRAVEL - paintTime) / LIFT, 0.0F, 1.0F);
            float[] last = paint.brushAt(1.0F);
            Vector3f from = last == null ? onCloth : clothPoint(last[0], last[1], -2.0F - BRUSH_OFF);
            tip = from.add(0.0F, 2.0F * t, -2.0F * t);
            size *= 1.0F - t;
        }
        if (tip == null || size <= 0.05F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(tip.x, tip.y, tip.z);
        // Held from above and behind, its bristles down on the cloth.
        poseStack.rotate(Axis.XP.rotationDegrees(BRUSH_TILT));
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        poseStack.rotate(Axis.ZP.rotationDegrees(180.0F + flick));
        poseStack.translate(-TIP_X * size, -TIP_Y * size, 0.0F);
        poseStack.scale(size, size, size);
        SceneDraw.item(BRUSH, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                poseStack, DiegesisClient.items(buffers), view.level(), 0);
        poseStack.popPose();
    }

    static LoomMenu loom(StationView view) {
        MenuAccess menu = view == null ? null : view.menu();
        return menu != null && menu.handle() instanceof LoomMenu loom ? loom : null;
    }
}
