package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.MenuAccess;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The enchanting table worked by hand. Its own floating book comes to the player while the camera flies in, starting where the
 * table's book hovers, and opens before the camera with the three offers written in it: on the right page each offer's runes, the
 * lapis it takes and the levels it needs, on the left what vanilla's tooltip says (the clue, the lapis and levels it costs), and at
 * the left page's foot the player's own level, as the HUD shows it. A click on an offer enchants (the menu's button, at once); one
 * that cannot be paid shakes the book and the thing missing answers. The item and the lapis lie on the table; lapis lays itself.
 * On leaving the book half shuts and goes back to the table's own book where it hovers now, and the table takes over again.
 */
final class EnchantingTableBehavior implements StationBehavior {
    static final String ITEM = "item";
    static final String LAPIS = "lapis";
    private static final String BODY = "body";
    private static final String BOOK = "book";
    private static final int ITEM_SLOT = 0;
    private static final int LAPIS_SLOT = 1;
    /**
     * The written part of a page in units (the flat part of the sheet past the gutter, 4.1 x 8 pixels), the default font one unit a
     * pixel: few enough that the text reads at 1280 x 720 on the book as it is held.
     */
    private static final int UNITS_WIDE = 100;
    private static final int UNITS_HIGH = 156;
    /** The three offers' rows, the same on both pages; the player's level at the left page's foot. */
    private static final float ROW_TOP = 4.0F;
    /**
     * A row is as high as what vanilla's tooltip says for its offer (the least: the right page's runes, lapis and level), all three
     * within the page above the foot.
     */
    private static final float ROW_LEAST = 30.0F;
    private static final float ROW_PAD = 4.0F;
    private static final float ROWS_BOTTOM = 139.0F;
    private static final float FOOT_Y = 143.0F;
    /** The clue's lines and the smaller cost lines under it, units. */
    private static final float CLUE_LINE = 9.0F;
    private static final float COST_LINE = 7.0F;
    private static final float COST_SCALE = 0.67F;
    /** How wide the left page's words may run, and where they start on each page (the fold is on the left page's right). */
    private static final int WRAP = 94;
    private static final float LEFT_X = 3.0F;
    private static final float RIGHT_X = 4.0F;
    private static final float RIGHT_END = 96.0F;
    /** Vanilla's experience orb before the player's level: a frame of its texture, its size in units, its tint's rate. */
    private static final net.minecraft.resources.ResourceLocation ORB = vanilla("textures/entity/experience_orb.png");
    private static final int ORB_ICON = 2;
    private static final float ORB_SIZE = 9.0F;
    private static final float LAPIS_SIZE = 8.0F;
    /**
     * Vanilla's inks: the runes in the enchanting screen's colour, words in the written book's black, the cost in the screen's dark
     * cost green, what is missing in the game's dark red.
     */
    private static final int INK = 0x685E4A;
    private static final int CLUE_INK = 0x000000;
    private static final int COST_INK = 0x000000;
    private static final int GREEN = 0x407F10;
    private static final int RED = 0xAA0000;
    private static final int HOVER_INK = 0x000000;
    private static final int BAND = 0x1E8B5A2B;
    /** The player's level at the foot of the spread, in the enchanting screen's dark green. */
    private static final int LEVEL_GREEN = 0x407F10;
    private static final float UNPAID_ALPHA = 0.45F;
    /**
     * Easy Magic's corner: its side in units (under the foot line, clear of the rows), how much further it turns up under the
     * cursor, how far around it a click still takes it; the fold's back and lit back, and the shade on the page under it.
     */
    private static final float CORNER = 13.0F;
    private static final float CORNER_LIFT = 4.0F;
    private static final float CORNER_REACH = 3.0F;
    private static final int FOLD = 0xE6C68A;
    private static final int FOLD_LIT = 0xF8DEA8;
    private static final int FOLD_SHADOW = 0x4A2E0E;
    /** Seconds: the book's glide in and back, how long it takes to open, the rows fading in one after the other. */
    private static final float ARRIVE = 0.35F;
    private static final float LEAVE = 0.3F;
    private static final float OPEN_TIME = 0.25F;
    private static final float LEVEL_FLASH = 0.4F;
    /** Offers written in the same moment as the last change are that change (its data slots come one by one), seconds. */
    private static final float OFFERS_SAME = 0.15F;
    /** The offers' runes flare as their page comes to rest, and burn down to ink over this long, seconds. */
    private static final float FLARE = 0.9F;
    /** How awake a hovered offer's runes are. */
    private static final float HOVER_GLOW = 0.6F;
    /** Glyphs puffing off the pages a second as they riffle forward. */
    private static final float RIFFLE_PUFF_RATE = 28.0F;
    /** The pages go over by themselves as the table's book's do: how long the book takes to settle into that, and out of it. */
    private static final float IDLE_IN = 0.6F;
    private static final float IDLE_OUT = 0.12F;
    /** How long a press may wait for the server's enchanted item, seconds. */
    private static final float ANSWER_WAIT = 3.0F;
    /** How long the book waits open for the offers of the held stack laid by itself on opening, seconds. */
    private static final float EXPECT_OFFERS = 1.0F;
    /** The covers' angle open, as vanilla's book over the table; the sheets lie a little off the blocks of pages. */
    private static final float OPEN_ANGLE = 1.4F;
    private static final float SHEET_INSET = 0.97F;
    /** How far the model is moved along its own x so that the open pages, not the spine, are where the node is. */
    private static final float READ_SHIFT = 1.3F;
    /** The shut book's half length: shut, it is turned a quarter about its spine so that its cover faces the camera. */
    private static final float SHUT_HALF = 3.0F;
    /** Its size where it is read: the table's book's, so the table and the book read as one thing. */
    private static final float READ_SCALE = WorldBooks.SCALE;
    /** How far into the glide (linear) the table's own book has given way to this one (a short cross-fade at the same size). */
    private static final float HAND_OFF = 0.6F;
    /** The least light the book is read in: a table in a dark room stays readable. */
    private static final int LIT = 12;
    private static final int GLINT = 0xFFC890FF;
    /** The enchanted item rises, turns once and sets down again: how high, pixels, and for how long, seconds. */
    private static final float ITEM_RISE = 2.5F;
    private static final float ITEM_AIR = 0.55F;
    /** A riffle when something is laid on the table (many sheets in about half a second), when it is taken, and on an enchant. */
    private static final int RIFFLE_PUT_SHEETS = 8;
    private static final float RIFFLE_PUT = 0.5F;
    private static final int RIFFLE_TAKE_SHEETS = 4;
    private static final float RIFFLE_TAKE = 0.3F;
    /** Runes streaming from the pressed offer into the item: for how long, how many a second, how long each flies. */
    private static final float STREAM_TIME = 0.6F;
    private static final float STREAM_RATE = 45.0F;
    static final float GLYPH_FLIGHT = 0.5F;
    /** The rule between two offers: its ink, how strong, how far from the page's sides. */
    private static final int DIVIDER_INK = 0x7A6545;
    private static final int DIVIDER_ALPHA = 0x70;
    private static final float DIVIDER_INSET = 18.0F;
    /** A riffle that started this soon before another would start is that one: the offers and the slot both report one laying. */
    private static final float RIFFLE_SAME = 0.3F;
    private static final ItemStack LAPIS_ICON = new ItemStack(Items.LAPIS_LAZULI);

    private final OpenBook book = new OpenBook(UNITS_WIDE, UNITS_HIGH, this::paintPage);

    {
        book.setSpreadPainter(this::paintFoot);
        book.setFiller(this::paintRunes);
        book.setPaper(this::paperOf);
    }
    private final StationHands.SlotWatch watch = new StationHands.SlotWatch(ITEM, "item", LAPIS, "lapis");
    private float clock;
    /** How far the book has come from the table to the reading place, 0 to 1, and whether it is coming or going. */
    private float glide;
    private boolean coming;
    /** How far the book is open where it is read, 0 (shut, cover to the camera) to 1. */
    private float opened;
    /** The offers shown, as they were when they last changed (the runes are made for them). */
    private final int[] costs = new int[3];
    private final int[] clues = {-1, -1, -1};
    private final int[] clueLevels = {-1, -1, -1};
    private final FormattedText[] runes = new FormattedText[3];
    private int offersKey;
    private boolean hasOffers;
    private int hoveredRow = -1;
    /** With Easy Magic: whether its new offers can be bought now (the right page's corner shows), and whether it is under the cursor. */
    private boolean rerollShown;
    private boolean cornerHovered;
    /** Where each offer's row starts on both pages, and where the last one ends, as laid out this frame; the lines of each. */
    private final float[] rowTops = {ROW_TOP, ROW_TOP + 45.0F, ROW_TOP + 90.0F, ROW_TOP + 135.0F};
    private final List<List<Line>> rowLines = new ArrayList<>(List.of(List.of(), List.of(), List.of()));
    private float levelRedUntil = -10.0F;
    private float pressedAt = -10.0F;
    private float hopAt = -1.0F;
    /**
     * The spread the offers are written on (-1: none), and whether they wait for the pages to stop running before a sheet brings
     * them; when they last changed.
     */
    private int offersSpread = NO_SPREAD;
    /** No spread: the book's spreads go below zero as vanilla's flip does, so -1 is a spread like any other. */
    private static final int NO_SPREAD = Integer.MIN_VALUE;
    private boolean offersWaiting;
    private float offersChangedAt = -10.0F;
    /** When the offers' page last came to rest, for the runes' flare. */
    private float landedAt = -10.0F;
    private boolean wasShown;
    /** The offer pressed last, whose runes crumble into the item, and when. */
    private int streamRow = -1;
    private float streamStart = -10.0F;
    /** The pages going over by themselves, as the table's book's: vanilla's flip, its aim and speed; how much it shows, 0 to 1. */
    private float idleFlip;
    private float idleFlipOld;
    private float idleFlipTarget;
    private float idleFlipSpeed;
    private float idle;
    /** While the pages go over by themselves the spread is this plus the pages the flip has turned ({@link OpenBook#flipTurns}). */
    private int idleBase;
    private boolean idleTurning;
    private final net.minecraft.util.RandomSource flipRandom = net.minecraft.util.RandomSource.create();
    /** The writing's clock at the last frame, for the words crumbling since; glyphs of a riffle's puff left to spawn. */
    private float lastNow = -1.0F;
    private float puffCarry;
    /** The scene's free turn the body was last turned back by. */
    private float bodyTurn = Float.NaN;
    /** When the last riffle started and until when its sheets go over: offers coming meanwhile turn no sheet and show after it. */
    private float riffleStart = -10.0F;
    private float riffleEnd = -10.0F;
    /** The item the offers shown were made for: offers of another item come with a riffle, not with one sheet. */
    private ItemStack offersItem = ItemStack.EMPTY;
    /** Page turn sounds waiting to be played: when, and how high. */
    private final List<float[]> sounds = new ArrayList<>();
    /** Runes on their way from a pressed offer into the item: until when, from where, and the part of a rune left to spawn. */
    private float streamUntil = -10.0F;
    private final Vector3f streamFrom = new Vector3f();
    private float streamCarry;
    /** Until when offers are expected for a stack laid by itself, on the station's clock. */
    private float expectingUntil = -10.0F;
    private ItemStack seenItem = ItemStack.EMPTY;
    private int heldSlot = -1;
    /** The inventory slots whose stacks went onto the table by themselves, to fly from there when they land; -1: none. */
    private int itemFrom = -1;
    private int lapisFrom = -1;
    /** The station, for the pages' painter. */
    private StationView station;

    @Override
    public void onSceneReady(StationView view) {
        station = view;
        bodyTurn = Float.NaN;
        view.setInventoryFilter(stack -> usable(view, stack));
        view.node(BOOK).ifPresent(node -> node.setPainter(new NodePainter() {
            @Override
            public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
                drawBook(view, poseStack, buffers, light);
            }

            /** It hangs before the camera with text on it: drawn by the scene, its edges and letters showed steps. */
            @Override
            public boolean smooth() {
                return true;
            }
        }));
    }

    /** What the table takes from the inventory: what can be enchanted, and the lapis (a modded fuel too, by the slot's own rule). */
    private static boolean usable(StationView view, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        EnchantmentMenu menu = enchanting(view);
        boolean fuel = menu != null ? menu.getSlot(LAPIS_SLOT).mayPlace(stack) : stack.is(Items.LAPIS_LAZULI);
        return fuel || stack.copyWithCount(1).isEnchantable();
    }

    @Override
    public void onOpen(StationView view) {
        heldSlot = -1;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && view.openedByUse() && player.getMainHandItem().copyWithCount(1).isEnchantable()) {
            heldSlot = player.getInventory().selected;
        }
        itemFrom = -1;
        lapisFrom = -1;
        coming = true;
        expectingUntil = view.openedByUse() && heldSlot >= 0 ? clock + EXPECT_OFFERS + 0.5F : -10.0F;
        pressedAt = -10.0F;
        offersSpread = NO_SPREAD;
        offersWaiting = false;
        offersChangedAt = -10.0F;
        landedAt = -10.0F;
        wasShown = false;
        streamRow = -1;
        lastNow = -1.0F;
        bodyTurn = Float.NaN;
        // The pages go on going over as they did over the table, from where they were.
        idle = 1.0F;
        if (view.level().getBlockEntity(view.pos()) instanceof EnchantmentTableBlockEntity entity) {
            idleFlip = entity.flip;
            idleFlipOld = entity.oFlip;
            idleFlipTarget = entity.flipT;
            idleFlipSpeed = entity.flipA;
        }
        hoveredRow = -1;
        riffleStart = -10.0F;
        riffleEnd = -10.0F;
        offersItem = ItemStack.EMPTY;
        streamUntil = -10.0F;
        // Open on the pages the table's book shows in the world, so its pages stay the same as it comes.
        idleBase = 0;
        idleTurning = false;
        book.jumpTo(OpenBook.flipTurns(idleFlip));
        sounds.clear();
        hasOffers = false;
        offersKey = 0;
        seenItem = ItemStack.EMPTY;
        watch.forget();
    }

    @Override
    public void onMenuOpened(StationView view) {
        LocalPlayer player = Minecraft.getInstance().player;
        MenuAccess menu = view.menu();
        EnchantmentMenu enchanting = enchanting(view);
        if (player == null || menu == null || enchanting == null) {
            return;
        }
        if (heldSlot >= 0 && player.getInventory().getItem(heldSlot).copyWithCount(1).isEnchantable()) {
            // One vanilla shift-click of the held stack: one of it goes onto the table.
            menu.quickMoveFromInventory(heldSlot);
            itemFrom = heldSlot;
            expectingUntil = clock + EXPECT_OFFERS;
        }
        heldSlot = -1;
        if (!enchanting.getSlot(LAPIS_SLOT).hasItem()) {
            // The lapis lays itself: the first stack the slot takes, the hotbar first, by a vanilla shift-click.
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && slot != itemFrom && enchanting.getSlot(LAPIS_SLOT).mayPlace(stack)) {
                    menu.quickMoveFromInventory(slot);
                    lapisFrom = slot;
                    break;
                }
            }
        }
    }

    @Override
    public void onClose(StationView view) {
        coming = false;
        hoveredRow = -1;
        offersWaiting = false;
    }

    /** The book is still on its way back to the table: the scene keeps standing in for the block until it is there. */
    @Override
    public boolean holdsBlock(StationView view) {
        return !view.isInUse() && glide > 0.0F;
    }

    /** Pieces put onto the table and taken off it by hand sound; the ones laid by themselves fly there from the inventory. */
    @Override
    public void tick(StationView view) {
        // The pages go over by themselves at vanilla's pace, as EnchantmentTableBlockEntity turns its book's with a player near.
        idleFlipOld = idleFlip;
        if (flipRandom.nextInt(40) == 0) {
            // On only, never back as over the table: at the station every page that goes over is a new one.
            idleFlipTarget += 1 + flipRandom.nextInt(2);
        }
        float pull = Mth.clamp((idleFlipTarget - idleFlip) * 0.4F, -0.2F, 0.2F);
        idleFlipSpeed += (pull - idleFlipSpeed) * 0.9F;
        idleFlip += idleFlipSpeed;
        watch.tick(view, (node, change) -> {
            boolean lapis = LAPIS.equals(node);
            if (change > 0) {
                int from = lapis ? lapisFrom : itemFrom;
                if (from >= 0) {
                    view.node(node).ifPresent(slot -> StationHands.fly(view, node, from, slot.definition().transform().pos()));
                    if (lapis) {
                        lapisFrom = -1;
                    } else {
                        itemFrom = -1;
                    }
                }
                float pitch = (lapis ? 1.3F : 0.9F) + 0.2F * StationHands.random(view);
                StationHands.play(view, SoundEvents.ITEM_FRAME_ADD_ITEM, node, 0.3F, pitch);
                if (!lapis && clock - riffleStart > RIFFLE_SAME) {
                    // The book wakes to what was laid: its pages run under the thumb to where the offers are written (unless its
                    // offers came first and already set them running).
                    riffle(view, RIFFLE_PUT_SHEETS, RIFFLE_PUT, 1);
                }
            } else {
                StationHands.play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, node, 0.3F, 0.9F + 0.2F * StationHands.random(view));
                if (!lapis) {
                    riffle(view, RIFFLE_TAKE_SHEETS, RIFFLE_TAKE, -1);
                }
            }
        });
    }

    /** Sheets riffle over with the rustle of a few page turns, rising in pitch as a thumbed riffle does. */
    private void riffle(StationView view, int sheets, float seconds, int way) {
        book.riffle(sheets, seconds, way);
        riffleStart = clock;
        riffleEnd = clock + seconds;
        int rustles = sheets > 5 ? 3 : 2;
        sounds.clear();
        for (int i = 0; i < rustles; i++) {
            sounds.add(new float[] {clock + i * seconds / rustles, 1.1F + 0.2F * i + 0.1F * StationHands.random(view)});
        }
    }

    @Override
    public void frame(StationView view, float dt) {
        station = view;
        clock += dt;
        book.frame(dt);
        for (int i = sounds.size() - 1; i >= 0; i--) {
            float[] sound = sounds.get(i);
            if (clock >= sound[0]) {
                StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, BOOK, 0.3F, sound[1]);
                sounds.remove(i);
            }
        }
        streamRunes(view, dt);
        boolean hidden = view.isBlockHidden();
        view.node(BODY).ifPresent(node -> node.setVisible(hidden));
        if (view.turn() != bodyTurn) {
            // The scene faces the player at their own angle; the table stands square to the world as the block does.
            bodyTurn = view.turn();
            view.node(BODY).ifPresent(node -> node.rotateTo(0.0F, bodyTurn, 0.0F, 0.0F));
        }
        view.node(BOOK).ifPresent(node -> node.setVisible(hidden));
        // It sets off from where the table's book is once the scene draws it instead of the table, so the two never stand apart.
        if (coming && view.isFocused()) {
            glide = hidden ? Math.min(1.0F, glide + dt / ARRIVE) : glide;
        } else {
            glide = Math.max(0.0F, glide - dt / LEAVE);
        }
        EnchantmentMenu menu = view.isFocused() ? enchanting(view) : null;
        // The answer first: its riffle is running when the next offers are read, so they wait for it.
        answered(view, menu);
        readOffers(view, menu);
        if (offersWaiting && hasOffers && !book.isRiffling() && !book.isFlipping()) {
            // They came once the pages had run: a sheet brings them now.
            offersWaiting = false;
            book.turnTo(book.spread() + 1, true);
            offersSpread = book.spread();
            StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, BOOK, 0.35F, 1.0F + 0.2F * StationHands.random(view));
        }
        boolean shown = offersShown();
        if (shown && !wasShown) {
            landed(view);
        }
        wasShown = shown;
        if (streamRow >= 0 && clock - streamStart > ANSWER_WAIT) {
            // No answer came: the offer's runes are back.
            streamRow = -1;
        }
        // A page turned while the table is bare is part of the idle book: its sheets keep fluttering meanwhile.
        boolean idleWanted = offersSpread == NO_SPREAD && !offersWaiting && !book.isRiffling();
        idle = Mth.approach(idle, idleWanted ? 1.0F : 0.0F, dt / (idleWanted ? IDLE_IN : IDLE_OUT));
        if (idleWanted && !book.isFlipping()) {
            // The pages going over by themselves are pages turned: each one that goes over brings the next spread.
            int turned = OpenBook.flipTurns(Mth.lerp(Minecraft.getInstance().getFrameTime(), idleFlipOld, idleFlip));
            if (!idleTurning) {
                idleTurning = true;
                idleBase = book.spread() - turned;
            }
            if (idleBase + turned != book.spread()) {
                book.jumpTo(idleBase + turned);
            }
        } else {
            idleTurning = false;
        }
        // Open the whole time the player is at the table, with or without offers: a book that shuts reads as one that refuses.
        boolean wantOpen = view.isFocused() && coming;
        float target = coming ? (wantOpen ? 1.0F : 0.0F) : 0.5F;
        opened = Mth.approach(opened, target, dt / OPEN_TIME);
        if (hopAt >= 0.0F && clock - hopAt >= ITEM_AIR) {
            // It sets down, turned once round (the same as unturned), with a burst of glints and a chime.
            hopAt = -1.0F;
            view.node(ITEM).ifPresent(node -> {
                node.moveTo(0.0F, 0.0F, 0.0F, 0.15F);
                node.rotateTo(0.0F, 0.0F, 0.0F, 0.0F);
                Vector3f at = new Vector3f(node.definition().transform().pos()).add(0.0F, 1.0F, 0.0F);
                view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 3.0F, 0.0F), 4.0F, 16, GLINT);
            });
            StationHands.play(view, SoundEvents.AMETHYST_BLOCK_CHIME, ITEM, 0.6F, 1.3F + 0.2F * StationHands.random(view));
        }
        if (menu != null && hasOffers) {
            layoutRows(menu);
        }
        magic(view, dt);
        hoveredRow = -1;
        boolean wasOnCorner = cornerHovered;
        cornerHovered = false;
        rerollShown = menu != null && offersShown() && EasyMagicReroll.offered(menu);
        if (menu != null && offersShown() && glide >= 1.0F && opened >= 1.0F) {
            int row = rowUnder(view);
            if (row >= 0 && canPay(menu, row)) {
                hoveredRow = row;
            }
            cornerHovered = rerollShown && onCorner(view);
        }
        if (cornerHovered != wasOnCorner) {
            // Easy Magic's own words over its corner, as over its button.
            boolean on = cornerHovered;
            view.node(BOOK).ifPresent(node -> node.setTooltip(on ? Component.translatable("container.enchant.reroll") : null));
        }
    }

    /** Whether the cursor is on the right page's lower outer corner, Easy Magic's new offers. */
    private boolean onCorner(StationView view) {
        Pose pose = pose(view);
        float from = CORNER + CORNER_REACH;
        return book.hit(view, BOOK, pose.toNode(view), pose.open, pose.right, pose.left)
                .filter(hit -> hit.right() && hit.x() >= UNITS_WIDE - from && hit.y() >= UNITS_HIGH - from)
                .isPresent();
    }

    /** Easy Magic's new offers: its menu button, paid in lapis and experience points; the page turns when they come. */
    private void reroll(StationView view, EnchantmentMenu menu, MenuAccess access) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!EasyMagicReroll.canPay(menu, player)) {
            view.node(BOOK).ifPresent(node -> node.shake(0.3F, 0.2F));
            if (EasyMagicReroll.lapisHeld(menu) < EasyMagicReroll.lapisCost(menu)) {
                view.node(LAPIS).ifPresent(node -> node.shake(0.3F, 0.2F));
            }
            StationHands.play(view, SoundEvents.BOOK_PUT, BOOK, 0.25F, 0.8F);
            return;
        }
        access.pressButton(EasyMagicReroll.BUTTON);
        StationHands.swing();
        StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, BOOK, 0.5F, 1.1F);
    }

    /**
     * The offers as the menu holds them now. When they change (a new item, a new seed after an enchant), once for the change and not
     * for each of its data slots: a sheet turns, the runes are made anew as vanilla's screen makes them, the rows fade in.
     */
    private void readOffers(StationView view, EnchantmentMenu menu) {
        boolean any = false;
        if (menu != null && menu.getSlot(ITEM_SLOT).hasItem()) {
            for (int cost : menu.costs) {
                any |= cost > 0;
            }
        }
        if (!any) {
            hasOffers = false;
            offersKey = 0;
            offersItem = ItemStack.EMPTY;
            offersSpread = NO_SPREAD;
            offersWaiting = false;
            return;
        }
        int key = 31 * Arrays.hashCode(menu.costs) + 17 * Arrays.hashCode(menu.enchantClue) + Arrays.hashCode(menu.levelClue)
                + 7 * menu.getEnchantmentSeed();
        hasOffers = true;
        if (key == offersKey) {
            return;
        }
        offersKey = key;
        System.arraycopy(menu.costs, 0, costs, 0, 3);
        System.arraycopy(menu.enchantClue, 0, clues, 0, 3);
        System.arraycopy(menu.levelClue, 0, clueLevels, 0, 3);
        // The same calls in the same order as the vanilla screen, so the runes are the words the screen would show.
        Font font = Minecraft.getInstance().font;
        EnchantmentNames.getInstance().initSeed(menu.getEnchantmentSeed());
        for (int n = 0; n < 3; n++) {
            runes[n] = costs[n] != 0 ? EnchantmentNames.getInstance().getRandomName(font, 86 - font.width("" + costs[n])) : null;
        }
        ItemStack item = menu.getSlot(ITEM_SLOT).getItem();
        boolean newItem = !ItemStack.isSameItemSameTags(item, offersItem);
        offersItem = item.copy();
        boolean same = clock - offersChangedAt < OFFERS_SAME;
        offersChangedAt = clock;
        if (same && (offersSpread != NO_SPREAD || offersWaiting)) {
            // The rest of the same change: written where the first part went.
            return;
        }
        if (newItem && clock >= riffleEnd + 0.3F) {
            // Offers of something just laid that came before the slot told of it: the riffle starts here.
            riffle(view, RIFFLE_PUT_SHEETS, RIFFLE_PUT, 1);
        }
        if (book.isRiffling() && !book.riffleUncovered()) {
            // Offers of what was just laid (or of the next enchant) come while the pages run: the riffle's last sheet brings the
            // page they are written on, and the one under it is theirs too.
            book.jumpTo(book.spread() + 1);
            offersSpread = book.spread();
            offersWaiting = false;
            return;
        }
        if (book.isRiffling() || book.isFlipping()) {
            // The pages have run already: a sheet brings them once they rest.
            offersSpread = NO_SPREAD;
            offersWaiting = true;
            return;
        }
        book.turnTo(book.spread() + 1, false);
        offersSpread = book.spread();
        offersWaiting = false;
        StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, BOOK, 0.35F, 1.0F + 0.2F * StationHands.random(view));
    }

    /**
     * The offers' page has come to rest open: their runes flare and burn down to ink, a few sparks lifting off them, a soft
     * shimmer of the table's own.
     */
    private void landed(StationView view) {
        landedAt = clock;
        Pose pose = pose(view);
        for (int row = 0; row < 3; row++) {
            if (costs[row] <= 0) {
                continue;
            }
            Vector3f at = pose.toScene(book.onPage(true, UNITS_WIDE / 2.0F, rowTops[row] + 8.0F, pose.open, pose.right, pose.left));
            view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 2.5F, 0.0F), 2.5F, 3, GLINT);
        }
        StationHands.play(view, SoundEvents.ENCHANTMENT_TABLE_USE, BOOK, 0.12F, 1.6F + 0.2F * StationHands.random(view));
    }

    /**
     * The book's own magic while it is read: now and then a word of its runes lights up and crumbles into glyphs that drift off
     * the page ({@link RunePages}), and pages riffling forward puff glyphs off as they go.
     */
    private void magic(StationView view, float dt) {
        float now = RunePages.now();
        float from = lastNow;
        lastNow = now;
        if (glide < 1.0F || opened < 0.9F) {
            puffCarry = 0.0F;
            return;
        }
        Pose pose = pose(view);
        if (from >= 0.0F && now > from && now - from < 1.0F && !book.isRiffling() && !book.isFlipping()) {
            for (int side = 0; side < 2; side++) {
                if (book.spread() == offersSpread) {
                    break;
                }
                boolean rightPage = side == 1;
                RunePages.crumbling(2 * book.spread() + side, from, now, spot -> crumble(view, pose, rightPage, spot));
            }
        }
        if (!book.isRiffling() || !book.riffleForward()) {
            puffCarry = 0.0F;
            return;
        }
        puffCarry += dt * RIFFLE_PUFF_RATE;
        List<net.minecraft.resources.ResourceLocation> glyphs = HandsOnVanillaClient.DRIFTING_GLYPHS;
        while (puffCarry >= 1.0F) {
            puffCarry -= 1.0F;
            float x = 10.0F + 80.0F * StationHands.random(view);
            float y = 10.0F + 130.0F * StationHands.random(view);
            Vector3f at = pose.toScene(book.onPage(true, x, y, pose.open, pose.right, pose.left));
            Vector3f velocity = new Vector3f((StationHands.random(view) - 0.5F) * 3.0F, 4.0F + 3.0F * StationHands.random(view),
                    (StationHands.random(view) - 0.5F) * 3.0F);
            int glyph = Math.min(glyphs.size() - 1, (int) (StationHands.random(view) * glyphs.size()));
            view.spawnParticles(glyphs.get(glyph), at, velocity, 0.5F, 1, 0xFF000000 | MagicInk.any(StationHands.random(view)));
        }
    }

    /** A letter breaking off a page: it flies off as a glyph of its own, in a colour of the glint's. */
    private void crumble(StationView view, Pose pose, boolean rightPage, RunePages.Spot spot) {
        List<net.minecraft.resources.ResourceLocation> glyphs = HandsOnVanillaClient.DRIFTING_GLYPHS;
        Vector3f at = pose.toScene(book.onPage(rightPage, spot.x(), spot.y(), pose.open, pose.right, pose.left));
        // Off the page towards the reader, and up.
        Vector3f velocity = new Vector3f(facing(view)).sub(at).normalize().mul(2.0F + 1.5F * StationHands.random(view))
                .add((StationHands.random(view) - 0.5F) * 1.5F, 1.5F + 1.5F * StationHands.random(view),
                        (StationHands.random(view) - 0.5F) * 1.5F);
        int glyph = Math.min(glyphs.size() - 1, (int) (StationHands.random(view) * glyphs.size()));
        view.spawnParticles(glyphs.get(glyph), at, velocity, 0.1F, 1, 0xFF000000 | MagicInk.any(StationHands.random(view)));
    }

    /** The server's answer to a press: the item on the table came back enchanted. It hops, glints, and two sheets turn quickly. */
    private void answered(StationView view, EnchantmentMenu menu) {
        ItemStack item = menu == null ? ItemStack.EMPTY : menu.getSlot(ITEM_SLOT).getItem();
        if (ItemStack.matches(item, seenItem)) {
            return;
        }
        boolean enchanted = item.isEnchanted() || item.is(Items.ENCHANTED_BOOK);
        boolean was = seenItem.isEnchanted() || seenItem.is(Items.ENCHANTED_BOOK);
        seenItem = item.copy();
        if (!enchanted || was || clock - pressedAt > ANSWER_WAIT) {
            return;
        }
        pressedAt = -10.0F;
        streamRow = -1;
        // The lapis used up is the enchant's, not taken by hand.
        watch.forget();
        hopAt = clock;
        // The item rises, turns once round in the air and comes down enchanted; the book riffles on to the next offers.
        view.node(ITEM).ifPresent(node -> {
            node.moveTo(0.0F, ITEM_RISE, 0.0F, 0.2F);
            node.rotateTo(0.0F, 360.0F, 0.0F, ITEM_AIR);
            Vector3f at = new Vector3f(node.definition().transform().pos()).add(0.0F, 1.5F, 0.0F);
            view.spawnParticles(DiegesisParticles.GLINT, at, new Vector3f(0.0F, 6.0F, 0.0F), 1.5F, 8, GLINT);
        });
        riffle(view, RIFFLE_PUT_SHEETS - 2, RIFFLE_PUT, 1);
    }

    /** Runes of the pressed offer on their way from the page into the item, a steady stream for a moment after the press. */
    private void streamRunes(StationView view, float dt) {
        if (clock >= streamUntil) {
            return;
        }
        streamCarry += dt * STREAM_RATE;
        int count = (int) streamCarry;
        streamCarry -= count;
        if (count <= 0) {
            return;
        }
        view.node(ITEM).ifPresent(node -> {
            Vector3f to = new Vector3f(node.definition().transform().pos()).add(0.0F, 1.0F, 0.0F);
            List<net.minecraft.resources.ResourceLocation> glyphs = HandsOnVanillaClient.GLYPHS;
            for (int i = 0; i < count; i++) {
                // From along the row, spread a little, each straight into the item in the same time.
                Vector3f from = new Vector3f(streamFrom).add((StationHands.random(view) - 0.5F) * 6.0F,
                        (StationHands.random(view) - 0.5F) * 1.5F, (StationHands.random(view) - 0.5F) * 2.0F);
                Vector3f velocity = new Vector3f(to).sub(from).div(GLYPH_FLIGHT);
                int glyph = Math.min(glyphs.size() - 1, (int) (StationHands.random(view) * glyphs.size()));
                view.spawnParticles(glyphs.get(glyph), from, velocity, 0.6F, 1, 0xFF000000 | MagicInk.any(StationHands.random(view)));
            }
        });
    }

    /** The row of the book under the cursor (either page), or -1. */
    private int rowUnder(StationView view) {
        Pose pose = pose(view);
        Optional<OpenBook.Hit> hit = book.hit(view, BOOK, pose.toNode(view), pose.open, pose.right, pose.left);
        if (hit.isEmpty()) {
            return -1;
        }
        float y = hit.get().y();
        for (int row = 0; row < 3; row++) {
            if (y >= rowTops[row] && y < rowTops[row + 1]) {
                return costs[row] > 0 ? row : -1;
            }
        }
        return -1;
    }

    /** Vanilla's rule for a press: lapis and levels enough for the offer, or creative. */
    private static boolean canPay(EnchantmentMenu menu, int row) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || menu.costs[row] <= 0 || !menu.getSlot(ITEM_SLOT).hasItem()) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        return menu.getGoldCount() >= row + 1 && player.experienceLevel >= row + 1 && player.experienceLevel >= menu.costs[row];
    }

    /** Whether the offers are written on the open pages: none while sheets go over, before they are. */
    private boolean offersShown() {
        return hasOffers && offersSpread != NO_SPREAD && offersSpread == book.spread() && !book.isRiffling() && !book.isFlipping();
    }

    /** A click on an offer: the menu's button at once, glints flying from the row to the item; one that cannot be paid is refused. */
    private void press(StationView view) {
        EnchantmentMenu menu = enchanting(view);
        MenuAccess access = view.menu();
        if (menu == null || access == null || !offersShown() || opened < 0.5F) {
            return;
        }
        if (rerollShown && onCorner(view)) {
            reroll(view, menu, access);
            return;
        }
        int row = rowUnder(view);
        if (row < 0) {
            return;
        }
        if (!canPay(menu, row)) {
            view.node(BOOK).ifPresent(node -> node.shake(0.3F, 0.2F));
            LocalPlayer player = Minecraft.getInstance().player;
            if (menu.getGoldCount() < row + 1) {
                view.node(LAPIS).ifPresent(node -> node.shake(0.3F, 0.2F));
            }
            if (player != null && (player.experienceLevel < row + 1 || player.experienceLevel < menu.costs[row])) {
                levelRedUntil = clock + LEVEL_FLASH;
            }
            StationHands.play(view, SoundEvents.BOOK_PUT, BOOK, 0.25F, 0.8F);
            return;
        }
        access.pressButton(row);
        pressedAt = clock;
        StationHands.swing();
        Pose pose = pose(view);
        // The offer's runes lift off the page and stream into the item.
        streamFrom.set(pose.toScene(book.onPage(true, UNITS_WIDE / 2.0F, (rowTops[row] + rowTops[row + 1]) / 2.0F, pose.open,
                pose.right, pose.left)));
        streamUntil = clock + STREAM_TIME;
        streamCarry = 0.0F;
        streamRow = row;
        streamStart = clock;
        StationHands.play(view, SoundEvents.BOOK_PAGE_TURN, BOOK, 0.4F, 0.7F);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node()) && click.button() == 0) {
            press(view);
        }
    }

    /** A stack in the hand does not stop a press: the book takes the click, the stack stays where it is. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node)) {
            if (button == 0) {
                press(view);
            }
            return true;
        }
        return false;
    }

    // Drawing.

    /** The book's place, turn and opening this frame, from the table's book (0) to where it is read (1). */
    private record Pose(Vector3f at, Quaternionf turn, float scale, float open, float right, float left, float ink, float own,
            int light) {
        Matrix4f toScene() {
            return new Matrix4f().translation(at).rotate(turn).scale(scale);
        }

        Vector3f toScene(Vector3f model) {
            return toScene().transformPosition(model);
        }

        Matrix4f toNode(StationView view) {
            Vector3fc node = view.node(BOOK).map(n -> n.definition().transform().pos()).orElse(new Vector3f());
            return new Matrix4f().translation(new Vector3f(at).sub(node)).rotate(turn).scale(scale);
        }
    }

    private Pose pose(StationView view) {
        float k = coming ? HoveringResult.outCubic(glide) : 1.0F - HoveringResult.outCubic(1.0F - glide);
        float partial = Minecraft.getInstance().getFrameTime();
        // Where it is read: lying back on the air, facing extra.faces, as a book held to be read; shut, turned about its spine so
        // that the cover faces the camera.
        Vector3fc centre = view.node(BOOK).map(n -> n.definition().transform().pos()).orElse(new Vector3f(8.0F, 17.5F, 9.5F));
        Vector3f eye = facing(view);
        Vector3f toEye = new Vector3f(eye).sub(centre).normalize();
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        up.sub(new Vector3f(toEye).mul(up.dot(toEye))).normalize();
        Vector3f side = new Vector3f(toEye).cross(up);
        float shut = 1.0F - opened;
        Matrix4f read = new Matrix4f().translation(new Vector3f(centre))
                .mul(new Matrix4f(toEye.x, toEye.y, toEye.z, 0.0F, up.x, up.y, up.z, 0.0F, side.x, side.y, side.z, 0.0F, 0.0F, 0.0F, 0.0F,
                        1.0F))
                .translate(-READ_SHIFT * READ_SCALE * opened, 0.0F, 0.0F)
                .rotateY(Mth.HALF_PI * shut * k)
                .translate(-SHUT_HALF * READ_SCALE * shut * k, 0.0F, 0.0F);
        float readOpen = OPEN_ANGLE * opened;
        float rest = Math.max(0.001F, readOpen * SHEET_INSET);
        float readRight = rest;
        float readLeft = -rest;
        if (idle > 0.0F) {
            // The pages going over by themselves, as vanilla's flip pages go.
            float[] sheets = OpenBook.flipAngles(Mth.lerp(partial, idleFlipOld, idleFlip), rest);
            float ease = idle * idle * (3.0F - 2.0F * idle);
            readRight = Mth.lerp(ease, rest, sheets[0]);
            readLeft = Mth.lerp(ease, -rest, sheets[1]);
        }
        int readLight = LightTexture.FULL_BRIGHT;
        Matrix4f table = read;
        float tableOpen = readOpen;
        float tableRight = readRight;
        float tableLeft = readLeft;
        int tableLight = readLight;
        if (view.level().getBlockEntity(view.pos()) instanceof EnchantmentTableBlockEntity entity) {
            // Exactly what EnchantTableRenderer draws now, in the scene's pixels.
            float time = entity.time + partial;
            float turn = entity.rot - entity.oRot;
            while (turn >= Mth.PI) {
                turn -= Mth.TWO_PI;
            }
            while (turn < -Mth.PI) {
                turn += Mth.TWO_PI;
            }
            Matrix4f world = new Matrix4f().translation(8.0F, 12.0F + 1.6F + Mth.sin(time * 0.1F) * 0.16F, 8.0F)
                    .rotateY(-(entity.oRot + turn * partial)).rotateZ(80.0F * Mth.DEG_TO_RAD);
            table = OpenBook.sceneFromBlock(view).mul(world);
            float flip = Mth.lerp(partial, entity.oFlip, entity.flip);
            tableOpen = (Mth.sin(time * 0.02F) * 0.1F + 1.25F) * Mth.lerp(partial, entity.oOpen, entity.open);
            float[] sheets = OpenBook.flipAngles(flip, tableOpen);
            tableRight = sheets[0];
            tableLeft = sheets[1];
            tableLight = LevelRenderer.getLightColor(view.level(), view.pos());
        }
        Vector3f at = table.getTranslation(new Vector3f()).lerp(read.getTranslation(new Vector3f()), k);
        Quaternionf turn = table.getNormalizedRotation(new Quaternionf()).slerp(read.getNormalizedRotation(new Quaternionf()), k);
        int light = k < 0.5F ? tableLight : LightTexture.pack(Math.max(LightTexture.block(readLight), LIT), LightTexture.sky(readLight));
        float ink = Mth.clamp((k - 0.6F) / 0.4F, 0.0F, 1.0F);
        // Over the linear glide, not the eased one: the eased one leaves the table too quickly for the cross-fade to be seen.
        // The table's book in the world is this one too: nothing to hand over from.
        float own = 1.0F;
        return new Pose(at, turn, Mth.lerp(k, WorldBooks.SCALE, READ_SCALE), Mth.lerp(k, tableOpen, readOpen),
                Mth.lerp(k, tableRight, readRight), Mth.lerp(k, tableLeft, readLeft), ink, own, light);
    }

    /** The point the read book's pages face (the book node's {@code extra.faces}), scene pixels. */
    private static Vector3f facing(StationView view) {
        return view.node(BOOK).map(node -> {
            var extra = node.extra();
            if (extra != null && extra.has("faces")) {
                var at = extra.getAsJsonArray("faces");
                return new Vector3f(at.get(0).getAsFloat(), at.get(1).getAsFloat(), at.get(2).getAsFloat());
            }
            return new Vector3f(8.0F, 29.0F, -6.9F);
        }).orElse(new Vector3f(8.0F, 29.0F, -6.9F));
    }

    private void drawBook(StationView view, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Pose pose = pose(view);
        Vector3fc node = view.node(BOOK).map(n -> n.definition().transform().pos()).orElse(new Vector3f());
        poseStack.pushPose();
        poseStack.translate(pose.at.x - node.x(), pose.at.y - node.y(), pose.at.z - node.z());
        poseStack.mulPose(pose.turn);
        poseStack.scale(pose.scale, pose.scale, pose.scale);
        book.draw(poseStack, buffers, pose.light, pose.open, pose.right, pose.left, pose.ink, pose.own);
        poseStack.popPose();
    }

    /** The current offers on their page: the runes and the price on the right, vanilla's tooltip on the left, the level at its foot. */
    private void paintPage(OpenBook.Canvas canvas, int page, boolean resting) {
        StationView view = station;
        if (view == null || !hasOffers || !isOffersPage(page)) {
            return;
        }
        EnchantmentMenu menu = enchanting(view);
        LocalPlayer player = Minecraft.getInstance().player;
        if (menu == null || player == null) {
            return;
        }
        boolean right = Math.floorMod(page, 2) == 1;
        boolean creative = player.getAbilities().instabuild;
        Font font = Minecraft.getInstance().font;
        for (int n = 1; n < 3; n++) {
            if (costs[n] > 0) {
                // A rule in faded ink between two offers, with a dot in its middle, as a book's sections are parted.
                int a = DIVIDER_ALPHA;
                float y = rowTops[n] - 1.5F;
                canvas.rect(DIVIDER_INSET, y, UNITS_WIDE - 2.0F * DIVIDER_INSET, 0.7F, OpenBook.MARK_LIFT, a << 24 | DIVIDER_INK);
                canvas.rect(UNITS_WIDE / 2.0F - 1.4F, y - 1.05F, 2.8F, 2.8F, OpenBook.MARK_LIFT, a << 24 | DIVIDER_INK);
            }
        }
        for (int n = 0; n < 3; n++) {
            if (costs[n] <= 0) {
                continue;
            }
            // Written on the page as it comes, never faded in.
            float shown = 1.0F;
            float top = rowTops[n];
            float high = rowTops[n + 1] - top;
            boolean paid = canPay(menu, n);
            boolean lit = resting && n == hoveredRow;
            if (lit) {
                canvas.rect(1.0F, top - 1.0F, UNITS_WIDE - 2.0F, high - 2.0F, OpenBook.MARK_LIFT * 0.5F, BAND);
            }
            float alpha = shown * (paid ? 1.0F : UNPAID_ALPHA);
            if (right) {
                if (runes[n] != null) {
                    // The runes vanilla's screen would write, drawn smaller where the page is narrower than its row. They flare as
                    // the page comes to rest, glow under the cursor, and crumble into the item when pressed.
                    FormattedCharSequence line = Language.getInstance().getVisualOrder(runes[n]);
                    float wide = font.width(line);
                    float room = RIGHT_END - RIGHT_X;
                    float scale = Math.min(1.0F, room / wide);
                    int row = n;
                    boolean pressed = n == streamRow;
                    float since = clock - landedAt;
                    float now = RunePages.now();
                    float written = MagicInk.write(canvas, runes[n].getString(), RIGHT_X, top + 4.0F, scale, lit ? HOVER_INK : INK,
                            alpha, now, (index, count, out) -> offerLetter(row, index, count, resting, lit, pressed, out));
                    float strength = resting ? Math.max(0.0F, 1.0F - since / FLARE) : 0.0F;
                    if (lit) {
                        strength = Math.max(strength, HOVER_GLOW);
                    }
                    if (pressed) {
                        strength = Math.max(strength, 1.0F - Mth.clamp((clock - streamStart) / STREAM_TIME, 0.0F, 1.0F));
                    }
                    MagicInk.sparks(canvas, 0x0FFE5L + row * 7919L + offersKey, now, RIGHT_X - 2.0F, top, RIGHT_X + written + 2.0F,
                            top + 13.0F, strength * shown, lit ? 3 : 4);
                }
                for (int i = 0; i <= n; i++) {
                    canvas.item(view.level(), LAPIS_ICON, RIGHT_X + i * (LAPIS_SIZE + 1.0F), top + 17.0F, LAPIS_SIZE, alpha);
                }
                String number = String.valueOf(costs[n]);
                canvas.text(Component.literal(number), RIGHT_END - font.width(number), top + 18.0F, paid ? GREEN : RED, shown);
                if (n == lastOffer() && rerollShown) {
                    paintCorner(canvas, menu, player, resting);
                }
            } else {
                float y = top + 2.0F;
                for (Line line : rowLines.get(n)) {
                    if (y + line.height() > top + high) {
                        // What does not fit is left out rather than written over the next offer.
                        break;
                    }
                    canvas.text(line.text(), LEFT_X, y, line.scale(), line.rgb(), line.dims() ? alpha : shown);
                    y += line.height();
                }
            }
        }
    }

    /**
     * Easy Magic's new offers on the right page's lower outer corner: the corner turned down as a dog-ear, ready to be turned over,
     * and what turning it costs beside it (lapis and experience points, in the offers' inks). Under the cursor the corner turns
     * further up.
     */
    private void paintCorner(OpenBook.Canvas canvas, EnchantmentMenu menu, LocalPlayer player, boolean resting) {
        boolean lifted = resting && cornerHovered;
        int size = Math.round(CORNER + (lifted ? CORNER_LIFT : 0.0F));
        float right = UNITS_WIDE;
        float bottom = UNITS_HIGH;
        for (int i = 0; i < size; i++) {
            // Row by row from the fold's top: the fold's back left of the diagonal, a shade on the page under it right of it,
            // the diagonal itself in faded ink; one step a unit.
            float y = bottom - size + i;
            float edge = right - i - 1.0F;
            canvas.rect(right - size, y, edge - (right - size), 1.0F, OpenBook.MARK_LIFT, 0xFF000000 | (lifted ? FOLD_LIT : FOLD));
            canvas.rect(edge, y, 1.0F, 1.0F, OpenBook.MARK_LIFT * 1.2F, 0x90000000 | INK);
            if (i > 0) {
                canvas.rect(edge + 1.0F, y, i, 1.0F, OpenBook.MARK_LIFT * 0.5F, 0x80000000 | FOLD_SHADOW);
            }
        }
        Font font = Minecraft.getInstance().font;
        boolean creative = player.getAbilities().instabuild;
        int lapis = EasyMagicReroll.lapisCost(menu);
        int points = EasyMagicReroll.pointsCost(menu);
        float x = right - size - 4.0F;
        float y = bottom - LAPIS_SIZE - 3.0F;
        if (!creative && points > 0) {
            String text = String.valueOf(points);
            x -= font.width(text);
            canvas.text(Component.literal(text), x, y + 1.0F, EasyMagicReroll.pointsHeld(player) >= points ? GREEN : RED, 1.0F);
            x -= ORB_SIZE + 1.0F;
            float u0 = ORB_ICON % 4 * 16 / 64.0F;
            float v0 = ORB_ICON / 4 * 16 / 64.0F;
            canvas.image(ORB, x, y, ORB_SIZE, ORB_SIZE, u0, v0, u0 + 0.25F, v0 + 0.25F, 0xFFFF00);
            x -= 4.0F;
        }
        if (!creative && lapis > 0) {
            String text = String.valueOf(lapis);
            x -= font.width(text);
            canvas.text(Component.literal(text), x, y + 1.0F, EasyMagicReroll.lapisHeld(menu) >= lapis ? GREEN : RED, 1.0F);
            x -= LAPIS_SIZE + 1.0F;
            canvas.item(station.level(), LAPIS_ICON, x, y, LAPIS_SIZE, 1.0F);
        }
    }

    /**
     * An offer's rune as it is now: waking letter by letter in a wave as its page comes to rest and burning down to ink, awake
     * under the cursor, and breaking off letter by letter into the item when pressed.
     */
    private void offerLetter(int row, int index, int count, boolean resting, boolean lit, boolean pressed, float[] out) {
        float along = count > 1 ? (float) index / (count - 1) : 0.0F;
        if (resting) {
            float since = clock - landedAt - row * 0.08F - along * 0.3F;
            if (since >= 0.0F) {
                out[1] = Math.max(out[1], Math.max(0.0F, 1.0F - since / FLARE));
                out[2] = Math.max(out[2], Math.max(0.0F, 1.0F - since / 0.15F));
            }
        }
        if (lit) {
            out[1] = Math.max(out[1], HOVER_GLOW);
        }
        if (pressed) {
            float breaks = streamStart + along * STREAM_TIME * 0.9F;
            if (clock >= breaks) {
                out[0] = 0.0F;
            } else {
                out[1] = Math.max(out[1], Mth.clamp((clock - streamStart) / 0.12F, 0.0F, 1.0F));
                out[2] = Math.max(out[2], Mth.clamp(1.0F - (breaks - clock) / 0.15F, 0.0F, 1.0F));
            }
        }
    }

    /**
     * The book's own runes on every page ({@link RunePages}), as over the table in the world: under the offers they give way as the
     * offers are written, and come back as soon as the pages go over again.
     */
    private void paintRunes(OpenBook.Canvas canvas, int page) {
        if (isOffersPage(page)) {
            return;
        }
        RunePages.paint(canvas, page, WorldBooks.RUNES_ALPHA, RunePages.now());
    }

    /** The last offer written, after which the right page's corner is drawn. */
    private int lastOffer() {
        int last = -1;
        for (int n = 0; n < 3; n++) {
            if (costs[n] > 0) {
                last = n;
            }
        }
        return last;
    }

    /** Whether the offers are written on page {@code page}. */
    private boolean isOffersPage(int page) {
        return offersSpread != NO_SPREAD && !OpenBook.isRiffleSheet(page) && Math.floorDiv(page, 2) == offersSpread;
    }

    /** The offers are written on plain paper; every other page on its own ({@link RunePages}). */
    private net.minecraft.resources.ResourceLocation paperOf(int page) {
        return isOffersPage(page) ? OpenBook.PAPER : RunePages.paper(page);
    }

    /**
     * The player's own level at the foot of the spread, over the gutter, after vanilla's experience orb (tinted and pulsing as
     * vanilla tints it): the HUD is off at a station, and the player must see what they can afford. Not in creative.
     */
    private void paintFoot(OpenBook.Canvas canvas) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getAbilities().instabuild) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String level = String.valueOf(player.experienceLevel);
        float wide = ORB_SIZE + 2.0F + font.width(level);
        float x = -wide / 2.0F;
        float pulse = clock * 10.0F;
        int red = (int) ((Mth.sin(pulse) + 1.0F) * 0.5F * 255.0F);
        int blue = (int) ((Mth.sin(pulse + 4.1887903F) + 1.0F) * 0.1F * 255.0F);
        float u0 = ORB_ICON % 4 * 16 / 64.0F;
        float v0 = ORB_ICON / 4 * 16 / 64.0F;
        canvas.image(ORB, x, FOOT_Y - 1.0F, ORB_SIZE, ORB_SIZE, u0, v0, u0 + 0.25F, v0 + 0.25F, red << 16 | 0xFF00 | blue);
        int rgb = clock < levelRedUntil ? RED : LEVEL_GREEN;
        canvas.text(Component.literal(level), x + ORB_SIZE + 2.0F, FOOT_Y, rgb, 1.0F);
    }

    private record Line(FormattedCharSequence text, int rgb, boolean dims, float scale, float height) {
    }

    /**
     * Each offer's row as high as its lines (the clue up to three lines, each cost up to two), so a long name takes room from a
     * short one; when the three do not fit above the foot, the clues give up their third line, and what still does not fit is
     * left out.
     */
    private void layoutRows(EnchantmentMenu menu) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        for (int most = 3; most >= 2; most--) {
            float y = ROW_TOP;
            for (int n = 0; n < 3; n++) {
                List<Line> lines = costs[n] > 0 ? tooltip(menu, player, n, creative, most) : List.of();
                rowLines.set(n, lines);
                float high = ROW_PAD;
                for (Line line : lines) {
                    high += line.height();
                }
                rowTops[n] = y;
                y += Math.max(ROW_LEAST, high);
            }
            rowTops[3] = Math.min(y, ROWS_BOTTOM);
            if (y <= ROWS_BOTTOM) {
                return;
            }
        }
    }

    /**
     * A line of the left page at {@code size} (1 for the clue, smaller for the costs), broken into lines ({@code most} at most)
     * where the page is narrower than it: never drawn smaller to fit, so every line of a size reads alike.
     */
    private static void fit(List<Line> lines, Component text, int rgb, boolean dims, float size, float height, int most) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> split = font.split(text, (int) (WRAP / size));
        for (FormattedCharSequence line : split.subList(0, Math.min(most, split.size()))) {
            lines.add(new Line(line, rgb, dims, size, height));
        }
    }

    /** What vanilla's tooltip over an offer says, in ink: the clue, then the levels needed, or the lapis and levels it takes. */
    private List<Line> tooltip(EnchantmentMenu menu, LocalPlayer player, int n, boolean creative, int clueLines) {
        List<Line> lines = new ArrayList<>();
        Enchantment enchantment = clues[n] >= 0 ? Enchantment.byId(clues[n]) : null;
        Component clue = null;
        if (enchantment != null && clueLevels[n] >= 0) {
            // The name without vanilla's tooltip grey, which does not read on paper; a curse keeps its red, in ink.
            Component name = enchantment.getFullname(clueLevels[n]).copy()
                    .withStyle(style -> style.withColor(enchantment.isCurse() ? TextColor.fromRgb(RED) : null));
            clue = clue(name);
        } else if (I18n.exists("forge.container.enchant.limitedEnchantability")) {
            clue = Component.translatable("forge.container.enchant.limitedEnchantability");
        }
        if (clue != null) {
            fit(lines, clue, CLUE_INK, true, 1.0F, CLUE_LINE, clueLines);
        }
        if (creative) {
            return lines;
        }
        int needed = n + 1;
        if (player.experienceLevel < costs[n]) {
            fit(lines, Component.translatable("container.enchant.level.requirement", costs[n]), RED, false, COST_SCALE, COST_LINE, 2);
        } else {
            Component lapis = needed == 1 ? Component.translatable("container.enchant.lapis.one")
                    : Component.translatable("container.enchant.lapis.many", needed);
            fit(lines, lapis, menu.getGoldCount() >= needed ? COST_INK : RED, false, COST_SCALE, COST_LINE, 2);
            Component levels = needed == 1 ? Component.translatable("container.enchant.level.one")
                    : Component.translatable("container.enchant.level.many", needed);
            fit(lines, levels, COST_INK, false, COST_SCALE, COST_LINE, 2);
        }
        return lines;
    }

    @SuppressWarnings("removal")
    private static net.minecraft.resources.ResourceLocation vanilla(String path) {
        return new net.minecraft.resources.ResourceLocation("minecraft", path);
    }

    /**
     * Vanilla's clue ({@code container.enchant.clue}, "%s . . . ?") around the enchantment's name, the words around the name held
     * together by no-break spaces: broken into lines, the dots stay with the name's last word.
     */
    private static Component clue(Component name) {
        String pattern = Language.getInstance().getOrDefault("container.enchant.clue");
        int at = pattern.indexOf("%s");
        if (at < 0) {
            return Component.translatable("container.enchant.clue", name);
        }
        String before = pattern.substring(0, at).replace(' ', '\u00A0');
        String after = pattern.substring(at + 2).replace(' ', '\u00A0');
        return Component.literal(before).append(name).append(after);
    }

    private static EnchantmentMenu enchanting(StationView view) {
        MenuAccess menu = view.menu();
        return menu != null && menu.handle() instanceof EnchantmentMenu enchanting ? enchanting : null;
    }
}
