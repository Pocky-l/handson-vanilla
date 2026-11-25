package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;

/**
 * The cartography table worked by hand. The map lies in the table's tray of felt as a map, its parchment and its picture, under a
 * pane of glass when it is locked; paper, an empty map or a glass pane lies beside it. What they make hovers over the table as what
 * it will be: the picture on a sheet twice as wide (zoomed out), two maps (copied), the map under glass (locked). A click on it does
 * the work on the table before it is taken: the paper hops under the map and the sheet spreads out round the picture, the picture is
 * drawn onto the empty map line by line, or the pane hops onto the map and settles with a clink; then it is taken (Shift: into the
 * inventory). A piece put that makes nothing with the other makes the second place shake. Vanilla's take sound comes from the server.
 */
final class CartographyTableBehavior implements StationBehavior {
    static final String MAP = "map";
    static final String EXTRA = "extra";
    private static final String RESULT = "result";
    private static final int MAP_SLOT = 0;
    private static final int EXTRA_SLOT = 1;
    private static final int RESULT_SLOT = 2;
    private static final Identifier PARCHMENT = vanilla("textures/map/map_background.png");
    private static final Identifier GLASS = vanilla("block/glass");
    /** The map lying on the table, pixels; the hovering sheet's: a map, the zoomed out sheet, the two copies apart. */
    private static final float LYING = 6.0F;
    /** Another mod's piece lying in the map's place (Map Atlases' atlas), pixels: about the lying map's size. */
    private static final float LYING_ITEM = 5.6F;
    private static final float SHEET = 6.0F;
    private static final float WIDER = 8.0F;
    private static final float COPY_APART = 1.4F;
    /** How far the hovering sheet leans back from upright towards the camera, degrees, and how it bobs and sways. */
    private static final float LEAN = 40.0F;
    private static final float BOB = 0.4F;
    private static final float BOB_RATE = 2.2F;
    private static final float SWAY = 6.0F;
    private static final float SWAY_RATE = 1.3F;
    private static final float GROW = 0.12F;
    private static final float HOVER_SWELL = 0.08F;
    /** The take: the sheet dips and is gone, seconds and pixels. */
    private static final float TAKE = 0.18F;
    private static final float DIP = 1.2F;
    private static final int TAKE_DUST = 0xFFE8DCC0;
    /** A result that is not a map: its size as an item hovering. */
    private static final float OTHER_SIZE = 4.4F;
    /** Ticks after a piece is put before a table that makes nothing says so: the server's answer has come by then. */
    private static final int SETTLE = 8;
    /**
     * The work before a take, seconds: the second piece's hop onto the map, how long the zoomed sheet takes to spread, the copy to be
     * drawn line by line, the pane to settle; and how high the hop arcs, pixels. The pane and the paper hop from their place to the
     * map's ({@code MAP_FROM_EXTRA}, cartography_table.json).
     */
    private static final float HOP = 0.3F;
    private static final float SPREAD = 0.4F;
    private static final float DRAW = 0.75F;
    private static final float SETTLE_GLASS = 0.25F;
    private static final float HOP_HEIGHT = 3.0F;
    private static final Vector3fc MAP_FROM_EXTRA = new Vector3f(6.6F, 0.03F, 0.0F);
    /** The copy being drawn: its size on the empty map, the ink's colour flecking off the line being drawn. */
    private static final float COPY_SIZE = 4.6F;
    private static final int INK = 0xFF2E2418;
    private static final int GLINT = 0xFFE8F4FF;

    /**
     * What the table makes: a wider map, copies, a locked map, a map another mod's ink changed (Supplementaries' antique ink), else
     * another mod's result.
     */
    private enum Kind { NONE, ZOOM, COPY, LOCK, INKED, OTHER }

    private final HoveringResult other = new HoveringResult(OTHER_SIZE);
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(MAP, "map", EXTRA, "extra");
    private float clock;
    private float appearedAt = -10.0F;
    private float hover;
    private ItemStack seen = ItemStack.EMPTY;
    /** The sheet being taken (drawn dipping away) and when. */
    private ItemStack taking = ItemStack.EMPTY;
    private Kind takingKind = Kind.NONE;
    private float takenAt = -10.0F;
    private int checkIn = -1;
    /** The work under way before a take: what it makes, since when (negative: none), whether the take goes to the inventory. */
    private Kind working = Kind.NONE;
    private float workAt = -1.0F;
    private boolean workAll;
    private boolean landed;

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(CartographyTableBehavior::usable);
        view.node(MAP).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> paintLying(view, poseStack, buffers, light)));
        view.node(RESULT).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> paintResult(view, poseStack, buffers, light)));
        view.node(EXTRA).ifPresent(node -> node.setPainter((poseStack, buffers, light) -> paintCopying(view, poseStack, buffers, light)));
    }

    /** What the table takes: filled maps, paper, empty maps and glass panes. */
    private static boolean usable(ItemStack stack) {
        // The open menu's own rule, so what other mods let in counts too (Supplementaries' antique ink).
        if (StationHover.menu() instanceof net.minecraft.world.inventory.CartographyTableMenu table) {
            return table.getSlot(0).mayPlace(stack) || table.getSlot(1).mayPlace(stack);
        }
        return stack.is(Items.FILLED_MAP) || stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.GLASS_PANE);
    }

    @Override
    public void onOpen(StationView view) {
        taking = ItemStack.EMPTY;
        checkIn = -1;
        endWork(view);
        watch.forget();
    }

    /** Pieces put and taken by hand sound (paper on wood, glass on wood); a piece that makes nothing is answered a moment later. */
    @Override
    public void tick(StationView view) {
        watch.tick(view, (node, change) -> {
            if (change > 0) {
                ItemStack put = stack(view, node.equals(MAP) ? MAP_SLOT : EXTRA_SLOT);
                if (put.is(Items.GLASS_PANE)) {
                    StationHands.play(view, SoundEvents.GLASS_PLACE, node, 0.15F, 1.4F);
                } else {
                    StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, node, 0.25F, 1.3F + 0.2F * StationHands.random(view));
                }
                checkIn = SETTLE;
            } else {
                StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, node, 0.3F, 0.9F + 0.2F * StationHands.random(view));
            }
        });
        if (checkIn > 0 && --checkIn == 0) {
            CartographyTableMenu menu = cartography(view);
            if (menu != null && menu.getSlot(MAP_SLOT).hasItem() && menu.getSlot(EXTRA_SLOT).hasItem()
                    && !menu.getSlot(RESULT_SLOT).hasItem()) {
                // The red cross of vanilla's screen: the two make nothing (a locked map, the largest map with paper).
                view.node(EXTRA).ifPresent(node -> node.shake(0.3F, 0.2F));
                StationHands.play(view, SoundEvents.BOOK_PUT, EXTRA, 0.25F, 0.8F);
            }
        }
    }

    private static ItemStack stack(StationView view, int slot) {
        CartographyTableMenu menu = cartography(view);
        return menu == null ? ItemStack.EMPTY : menu.getSlot(slot).getItem();
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        ItemStack made = stack(view, RESULT_SLOT);
        if (!ItemStack.matches(made, seen)) {
            if (!made.isEmpty() && (seen.isEmpty() || !ItemStack.isSameItem(made, seen))) {
                appearedAt = clock;
            }
            seen = made.copy();
        }
        view.node(RESULT).ifPresent(node -> {
            node.setTooltipItem(made);
            hover = Mth.lerp(Math.min(1.0F, dt * 14.0F), hover, !made.isEmpty() && node.isHovered() ? 1.0F : 0.0F);
            other.frame(node, made, true, dt);
        });
        work(view);
    }

    /** How far into the work before a take, seconds; negative while there is none. */
    private float worked() {
        return workAt < 0.0F ? -1.0F : clock - workAt;
    }

    private static float length(Kind kind) {
        return switch (kind) {
            case ZOOM -> HOP + SPREAD;
            case COPY -> DRAW + 0.1F;
            case LOCK -> HOP + SETTLE_GLASS;
            default -> 0.0F;
        };
    }

    /** The work under way: the piece lands on the map with its sound, ink flecks off the copy's line, and at its end the take. */
    private void work(StationView view) {
        float t = worked();
        if (t < 0.0F) {
            return;
        }
        if (stack(view, RESULT_SLOT).isEmpty()) {
            // Someone else's hand (or the server) emptied the table: nothing to finish.
            endWork(view);
            return;
        }
        if (!landed && t >= HOP && working != Kind.COPY) {
            landed = true;
            view.node(EXTRA).ifPresent(node -> node.setVisible(false));
            Vector3fc at = view.node(MAP).map(node -> node.definition().transform().pos()).orElse(new Vector3f());
            if (working == Kind.LOCK) {
                // The pane settles on the map: a clink, and light runs across the glass.
                StationHands.play(view, SoundEvents.GLASS_HIT, MAP, 0.35F, 1.6F + 0.2F * StationHands.random(view));
                view.spawnParticles(DiegesisParticles.GLINT, new Vector3f(at.x() + 2.0F, at.y() + 0.4F, at.z() + 2.0F),
                        new Vector3f(-9.0F, 0.0F, -9.0F), 0.8F, 8, GLINT);
            } else {
                StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, MAP, 0.35F, 0.9F + 0.2F * StationHands.random(view));
            }
        }
        if (working == Kind.COPY && t < DRAW && view.level().getRandom().nextFloat() < 0.5F) {
            float line = Mth.clamp(t / DRAW, 0.0F, 1.0F);
            view.node(EXTRA).ifPresent(node -> {
                Vector3fc at = node.definition().transform().pos();
                // The line being drawn runs from the far edge of the copy towards the camera.
                float z = at.z() + COPY_SIZE / 2.0F - COPY_SIZE * line;
                view.spawnParticles(DiegesisParticles.DUST, new Vector3f(at.x() + (StationHands.random(view) - 0.5F) * COPY_SIZE,
                        at.y() + 0.3F, z), new Vector3f(0.0F, 1.2F, 0.0F), 0.3F, 1, INK);
            });
        }
        if (t >= length(working)) {
            boolean all = workAll;
            endWork(view);
            takeNow(view, all);
        }
    }

    /** The work is over or given up: the second piece is back in its place and seen. */
    private void endWork(StationView view) {
        working = Kind.NONE;
        workAt = -1.0F;
        landed = false;
        view.node(EXTRA).ifPresent(node -> {
            node.moveTo(0.0F, 0.0F, 0.0F, 0.0F);
            node.setVisible(true);
        });
    }

    /** What the result is, by what lies in the second place. */
    private static Kind kind(StationView view, ItemStack made) {
        if (made.isEmpty()) {
            return Kind.NONE;
        }
        ItemStack extra = stack(view, EXTRA_SLOT);
        if (!made.is(Items.FILLED_MAP)) {
            return Kind.OTHER;
        }
        if (!stack(view, MAP_SLOT).is(Items.FILLED_MAP)) {
            // A map made from another mod's piece in the map's place (one cut out of Map Atlases' atlas): a sheet with its picture.
            return Kind.INKED;
        }
        if (extra.is(Items.PAPER)) {
            return Kind.ZOOM;
        }
        if (extra.is(Items.MAP)) {
            return Kind.COPY;
        }
        if (extra.is(Items.GLASS_PANE)) {
            return Kind.LOCK;
        }
        return extra.isEmpty() ? Kind.OTHER : Kind.INKED;
    }

    /**
     * A click on what hovers: the work on the table, then the take (onto the cursor, or with Shift into the inventory); one it cannot
     * take makes it shake. Another mod's result is taken at once.
     */
    private void take(StationView view, boolean all) {
        CartographyTableMenu table = cartography(view);
        if (table == null || worked() >= 0.0F) {
            return;
        }
        ItemStack made = table.getSlot(RESULT_SLOT).getItem();
        if (made.isEmpty()) {
            return;
        }
        ItemStack held = table.getCarried();
        boolean fits = all ? StationHands.inventoryHasRoom(made)
                : held.isEmpty() || ItemStack.isSameItemSameComponents(held, made) && held.getCount() + made.getCount() <= held.getMaxStackSize();
        if (!fits) {
            view.node(RESULT).ifPresent(node -> node.shake(0.3F, 0.2F));
            StationHands.play(view, SoundEvents.BOOK_PUT, RESULT, 0.25F, 0.8F);
            return;
        }
        Kind kind = kind(view, made);
        if (kind == Kind.OTHER) {
            takeNow(view, all);
            return;
        }
        working = kind;
        workAt = clock;
        workAll = all;
        landed = false;
        StationHands.swing();
        if (kind == Kind.COPY) {
            // A cartographer's pen at work, as the villager's at this table.
            StationHands.play(view, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, EXTRA, 0.6F, 1.0F + 0.1F * StationHands.random(view));
        } else {
            view.node(EXTRA).ifPresent(node -> node.hopTo(MAP_FROM_EXTRA.x(), MAP_FROM_EXTRA.y(), MAP_FROM_EXTRA.z(), HOP_HEIGHT, HOP));
            StationHands.play(view, kind == Kind.LOCK ? SoundEvents.GLASS_PLACE : SoundEvents.BOOK_PAGE_TURN, EXTRA, 0.2F, 1.3F);
            if (kind == Kind.ZOOM) {
                StationHands.play(view, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, MAP, 0.45F, 1.15F);
            }
        }
    }

    /** The take itself, once the work is done: the menu's click on the result, the sheet dipping away from where it hovered. */
    private void takeNow(StationView view, boolean all) {
        CartographyTableMenu table = cartography(view);
        MenuAccess menu = view.menu();
        if (table == null || menu == null) {
            return;
        }
        ItemStack made = table.getSlot(RESULT_SLOT).getItem();
        if (made.isEmpty()) {
            return;
        }
        taking = made.copy();
        takingKind = kind(view, made);
        takenAt = clock;
        menu.click(RESULT_SLOT, 0, all ? ClickType.QUICK_MOVE : ClickType.PICKUP);
        watch.forget();
        view.node(RESULT).ifPresent(node -> {
            Vector3fc at = node.definition().transform().pos();
            view.spawnParticles(DiegesisParticles.DUST, new Vector3f(at.x(), at.y() + 3.0F, at.z()), new Vector3f(0.0F, 1.5F, 0.0F),
                    2.0F, 6, TAKE_DUST);
        });
        StationHands.swing();
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && RESULT.equals(click.node()) && click.button() == 0) {
            take(view, Minecraft.getInstance().hasShiftDown());
        }
    }

    /** A key that leaves during the work finishes it first: the take is never lost. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (StationHands.leaves(keyCode, scanCode) && worked() >= 0.0F) {
            boolean all = workAll;
            endWork(view);
            takeNow(view, all);
        }
        return false;
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (RESULT.equals(node)) {
            if (button == 0) {
                take(view, shift);
            }
            return true;
        }
        return false;
    }

    // Drawing.

    /**
     * The map lying on the table as a map: its parchment, its picture (or the parchment alone, unknown), glass over a locked one.
     * Anything else another mod lets into the map's place (Map Atlases' atlas) lies there flat as the item.
     */
    private void paintLying(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        ItemStack map = stack(view, MAP_SLOT);
        if (map.isEmpty()) {
            return;
        }
        if (!map.is(Items.FILLED_MAP)) {
            HoveringResult.paintLying(view.level(), map, LYING_ITEM, poseStack, buffers, light);
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.02F, 0.0F);
        float t = worked();
        if (working == Kind.ZOOM && landed) {
            // The paper under it spreads out round the picture: the sheet grows to the zoomed size, the picture shrinks to half.
            float g = HoveringResult.outCubic(Mth.clamp((t - HOP) / SPREAD, 0.0F, 1.0F));
            float size = Mth.lerp(g, LYING, WIDER);
            float picture = Mth.lerp(g, 1.0F, 0.5F);
            blankSheet(size, poseStack, buffers, light);
            poseStack.translate(0.0F, 0.03F, 0.0F);
            sheet(view.level(), map, size, picture, poseStack, buffers, light);
        } else {
            sheet(view.level(), map, LYING, 1.0F, poseStack, buffers, light, working == Kind.LOCK && landed);
        }
        poseStack.popPose();
    }

    /**
     * The copy being drawn on the empty map, line by line from its far edge: the map's picture on a parchment the size of the empty
     * map, its part not yet drawn covered by blank parchment.
     */
    private void paintCopying(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        float t = worked();
        if (working != Kind.COPY || t < 0.0F) {
            return;
        }
        ItemStack map = stack(view, MAP_SLOT);
        if (!map.is(Items.FILLED_MAP)) {
            return;
        }
        float drawn = Mth.clamp(t / DRAW, 0.0F, 1.0F);
        poseStack.pushPose();
        // Over the empty map lying there, so its icon does not show through the copy.
        poseStack.translate(0.0F, 0.45F, 0.0F);
        sheet(view.level(), map, COPY_SIZE, 1.0F, poseStack, buffers, light);
        if (drawn < 1.0F) {
            // Blank parchment over the part not drawn yet, on the near side (map units: y runs towards the camera).
            float unit = COPY_SIZE / 142.0F;
            poseStack.pushPose();
            poseStack.mulPose(new Matrix4f(-unit, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -unit, 0.0F, 0.0F, -unit, 0.0F, 0.0F,
                    COPY_SIZE / 2.0F, 0.0F, COPY_SIZE / 2.0F, 1.0F));
            float from = 142.0F * drawn;
            quad(buffers.getBuffer(RenderTypes.text(PARCHMENT)), poseStack.last().pose(), 0.0F, from, 142.0F, 142.0F, 0.0F, drawn, 1.0F,
                    1.0F, 0xFFFFFFFF, light, -1.2F);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * What the two make, hovering over the table and leaning back towards the camera: grown in when it appears, bobbing, swaying a
     * little, lit and swelling under the cursor; dipping away when taken.
     */
    private void paintResult(StationView view, PoseStack poseStack, SceneBuffers buffers, int light) {
        ItemStack made = stack(view, RESULT_SLOT);
        Kind kind = kind(view, made);
        float since = clock - takenAt;
        float dip = 0.0F;
        if (since < TAKE && !taking.isEmpty()) {
            made = taking;
            kind = takingKind;
            dip = since / TAKE;
        } else if (made.isEmpty()) {
            return;
        }
        if (kind == Kind.OTHER) {
            // Another mod's result in this menu: the item itself, hovering as at the other stations.
            other.paint(view.level(), made, clock, poseStack, buffers, light);
            return;
        }
        float grow = HoveringResult.outCubic((clock - appearedAt) / GROW) * (1.0F - dip) * (1.0F + HOVER_SWELL * hover);
        if (grow <= 0.01F) {
            return;
        }
        int lit = hover > 0.5F || worked() >= 0.0F ? LightTexture.FULL_BRIGHT : light;
        poseStack.pushPose();
        poseStack.translate(0.0F, BOB * (1.0F + Mth.sin(clock * BOB_RATE)) - DIP * dip, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(SWAY * Mth.sin(clock * SWAY_RATE)));
        poseStack.translate(0.0F, WIDER / 2.0F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-(90.0F - LEAN)));
        poseStack.scale(grow, grow, grow);
        switch (kind) {
            case ZOOM -> {
                // The same picture in the middle of a sheet twice as wide: twice the land around it, as the vanilla screen shows.
                blankSheet(WIDER, poseStack, buffers, lit);
                poseStack.translate(0.0F, 0.03F, 0.0F);
                sheet(view.level(), made, WIDER, 0.5F, poseStack, buffers, lit);
            }
            case COPY -> {
                poseStack.pushPose();
                poseStack.translate(-COPY_APART, -0.05F, -COPY_APART);
                sheet(view.level(), made, SHEET, 1.0F, poseStack, buffers, lit);
                poseStack.popPose();
                poseStack.translate(COPY_APART * 0.4F, 0.05F, COPY_APART * 0.4F);
                sheet(view.level(), made, SHEET, 1.0F, poseStack, buffers, lit);
            }
            case LOCK -> sheet(view.level(), made, SHEET, 1.0F, poseStack, buffers, lit, true);
            // The map as the other mod made it (its own look, as its screen shows it); the map on the table until its data is here.
            case INKED -> sheet(view.level(), MapItem.getSavedData(made, view.level()) != null ? made : stack(view, MAP_SLOT), SHEET,
                    1.0F, poseStack, buffers, lit);
            default -> {
            }
        }
        poseStack.popPose();
    }

    private static void sheet(Level level, ItemStack map, float size, float picture, PoseStack poseStack, SceneBuffers buffers,
            int light) {
        sheet(level, map, size, picture, poseStack, buffers, light, false);
    }

    /**
     * A map lying in the node's level plane, face up, {@code size} pixels square about the origin, its top away from the camera:
     * vanilla's parchment (142 units, the picture's 128 within it, as a map in the hand) and the picture at {@code picture} of
     * that; under glass when {@code glass} or when the map is locked.
     */
    private static void sheet(Level level, ItemStack map, float size, float picture, PoseStack poseStack, SceneBuffers buffers,
            int light, boolean glass) {
        net.minecraft.world.level.saveddata.maps.MapId id = map.get(net.minecraft.core.component.DataComponents.MAP_ID);
        MapItemSavedData data = id != null ? MapItem.getSavedData(id, level) : null;
        float unit = size / 142.0F;
        poseStack.pushPose();
        // Map units: x to the camera's right (scene -x), y towards the camera (scene -z), drawn face up.
        poseStack.mulPose(new Matrix4f(-unit, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -unit, 0.0F, 0.0F, -unit, 0.0F, 0.0F,
                size / 2.0F, 0.0F, size / 2.0F, 1.0F));
        if (picture >= 1.0F) {
            quad(buffers.getBuffer(RenderTypes.text(PARCHMENT)), poseStack.last().pose(), 0.0F, 0.0F, 142.0F, 142.0F, 0.0F, 0.0F, 1.0F, 1.0F,
                    0xFFFFFFFF, light, 0.0F);
        }
        if (data != null && id != null) {
            poseStack.pushPose();
            float inset = picture >= 1.0F ? 7.0F : (142.0F - 128.0F * picture) / 2.0F;
            poseStack.translate(inset, inset, -0.1F);
            poseStack.scale(picture, picture, 1.0F);
            // As a map held in the hand, with the player's marker; a frame's map hides it.
            net.minecraft.client.renderer.MapRenderer maps = Minecraft.getInstance().getMapRenderer();
            net.minecraft.client.renderer.state.MapRenderState state = new net.minecraft.client.renderer.state.MapRenderState();
            maps.extractRenderState(id, data, state);
            maps.render(state, poseStack, buffers.submits(), false, light);
            poseStack.popPose();
        }
        if (glass || data != null && data.locked) {
            TextureAtlasSprite pane = com.pockyl.diegesis.impl.client.render.SceneDraw.blockSprite(GLASS);
            quad(buffers.getBuffer(RenderTypes.text(TextureAtlas.LOCATION_BLOCKS)), poseStack.last().pose(), 0.0F, 0.0F, 142.0F, 142.0F,
                    pane.getU0(), pane.getV0(), pane.getU1(), pane.getV1(), 0xFFFFFFFF, light, -0.6F);
        }
        poseStack.popPose();
    }

    /** A blank parchment, {@code size} pixels square, lying face up as {@link #sheet} lays a map. */
    private static void blankSheet(float size, PoseStack poseStack, SceneBuffers buffers, int light) {
        float unit = size / 142.0F;
        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f(-unit, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -unit, 0.0F, 0.0F, -unit, 0.0F, 0.0F,
                size / 2.0F, 0.0F, size / 2.0F, 1.0F));
        quad(buffers.getBuffer(RenderTypes.text(PARCHMENT)), poseStack.last().pose(), 0.0F, 0.0F, 142.0F, 142.0F, 0.0F, 0.0F, 1.0F, 1.0F,
                0xFFFFFFFF, light, 0.0F);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float x0, float y0, float x1, float y1, float u0, float v0, float u1,
            float v1, int argb, int light, float z) {
        int a = argb >>> 24;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        consumer.addVertex(pose, x0, y1, z).setColor(r, g, b, a).setUv(u0, v1).setLight(light);
        consumer.addVertex(pose, x1, y1, z).setColor(r, g, b, a).setUv(u1, v1).setLight(light);
        consumer.addVertex(pose, x1, y0, z).setColor(r, g, b, a).setUv(u1, v0).setLight(light);
        consumer.addVertex(pose, x0, y0, z).setColor(r, g, b, a).setUv(u0, v0).setLight(light);
    }

    @SuppressWarnings("removal")
    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace( path);
    }

    static CartographyTableMenu cartography(StationView view) {
        MenuAccess menu = view == null ? null : view.menu();
        return menu != null && menu.handle() instanceof CartographyTableMenu table ? table : null;
    }
}
