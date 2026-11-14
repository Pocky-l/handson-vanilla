package com.pockyl.handson_vanilla.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.state.SlotRef;

/**
 * The crafting table's feedback, read from what changes in the menu: a quiet tick on the wood when something is put
 * into a cell, a soft one when something is taken, and for a craft a low pop with a knock on the wood. No particles.
 * All of it is local: nobody else hears it.
 */
final class CraftingTableBehavior implements StationBehavior {
    /** At most this many craft sounds for one shift-click that crafts a stack. */
    private static final int MAX_CRAFT_SOUNDS = 4;
    /** Ticks between the sounds of a bulk craft. */
    private static final int CRAFT_SOUND_GAP = 2;

    private final int[] grid = new int[9];
    private ItemStack result = ItemStack.EMPTY;
    private int resultHeld;
    private boolean known;
    private int craftSounds;
    private int craftDelay;
    private final RecipeBook book = new RecipeBook(new CraftingBook());
    private final GhostRecipe ghost = new GhostRecipe();
    private final RecipeChoice choice = new RecipeChoice();
    /** The last laying of a recipe this table has seen: a new one takes the ghost away. */
    private long laidSeen;
    /** How long after a recipe was laid (book, recipe viewer) what lands in the grid flies in from the hotbar. */
    private static final long LAID_FOR_MS = 1500L;
    /** The arc of an item laid in: seconds in the air, height over the way, seconds between one and the next. */
    private static final float FLIGHT_TIME = 0.32F;
    private static final float FLIGHT_ARC = 4.0F;
    private static final float FLIGHT_GAP = 0.06F;
    private static long laidAt;
    /** The grid as it was drawn last frame, to see what a laid recipe brought. */
    private final int[] shown = new int[9];
    private final java.util.ArrayDeque<Flight> flights = new java.util.ArrayDeque<>();
    private float flightClock;

    /** One item flying from the hotbar into its cell, once {@code at} seconds have passed. */
    private record Flight(int cell, float at) {
    }

    /** A recipe was laid into the table; what lands in the grid now comes flying. */
    static void recipeLaid() {
        laidAt = net.minecraft.Util.getMillis();
    }

    @Override
    public void onSceneReady(StationView view) {
        view.setInventoryFilter(CraftingUse::usable);
        view.node("book_open").ifPresent(node -> node.setPainter(book));
        view.node("grid").ifPresent(node -> node.setPainter(ghost.grid()));
        view.node(HandsOnVanillaClient.RESULT).ifPresent(node -> node.setPainter(ghost.result()));
        choice.onSceneReady(view);
    }

    @Override
    public void frame(StationView view, float dt) {
        book.frame(view, dt);
        if (laidAt != laidSeen) {
            laidSeen = laidAt;
            ghost.clear();
        }
        ghost.frame(view, dt);
        flightClock += dt;
        boolean laying = net.minecraft.Util.getMillis() - laidAt < LAID_FOR_MS;
        float next = Math.max(flightClock, flights.isEmpty() ? flightClock : flights.peekLast().at() + FLIGHT_GAP);
        for (int cell = 0; cell < shown.length; cell++) {
            int now = view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, cell)).getCount();
            if (laying && now > shown[cell] && view.isFocused()) {
                // Waits down by the hotbar, where it came from, until its turn to fly.
                Vector3f from = start(view, cell);
                if (from != null) {
                    int at = cell;
                    view.node("grid_" + cell).ifPresent(node -> node.moveTo(from.x, from.y, from.z, 0.0F));
                    flights.add(new Flight(at, next));
                    next += FLIGHT_GAP;
                }
            }
            shown[cell] = now;
        }
        while (!flights.isEmpty() && flights.peekFirst().at() <= flightClock) {
            Flight flight = flights.removeFirst();
            view.node("grid_" + flight.cell()).ifPresent(node -> node.hopTo(0.0F, 0.0F, 0.0F, FLIGHT_ARC, FLIGHT_TIME));
            play(view, SoundEvents.ITEM_FRAME_ADD_ITEM, "grid_" + flight.cell(), 0.3F, 1.0F + 0.06F * flights.size());
        }
    }

    /** Where a cell's item starts from, as an offset from the cell: over the hotbar, low in the middle of the screen. */
    private static Vector3f start(StationView view, int cell) {
        return view.node("grid_" + cell).flatMap(node -> {
            org.joml.Vector3fc rest = node.definition().transform().pos();
            return view.screenOn(0.5F, 0.92F, rest.y() + 3.0F).map(at -> at.sub(rest.x(), rest.y(), rest.z(), new Vector3f()));
        }).orElse(null);
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && "book_open".equals(click.node())) {
            book.click(view, click.button());
        } else if (event instanceof SceneEvent.Click click && RecipeChoice.place(click.node()) >= 0) {
            choice.click(view, RecipeChoice.place(click.node()));
        }
    }

    /** The wheel over the open book turns its pages. */
    @Override
    public boolean onScroll(StationView view, String node, double amount) {
        return "book_open".equals(node) && book.scroll(amount);
    }

    /** Keys go to the book first: its search while it is typed, its page keys. */
    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        return book.key(view, keyCode, modifiers);
    }

    @Override
    public boolean onChar(StationView view, char character, int modifiers) {
        return book.typed(view, character);
    }

    /** The book takes its click with a stack in the hand too. */
    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if ("book_open".equals(node)) {
            book.click(view, button);
            return true;
        }
        if (RecipeChoice.place(node) >= 0) {
            // A choice is made with the hand full too: what is held stays held.
            choice.click(view, RecipeChoice.place(node));
            return true;
        }
        return false;
    }

    @Override
    public void onOpen(StationView view) {
        CraftingUse.refresh();
        book.opened(view);
    }

    @Override
    public void onMenuOpened(StationView view) {
        remember(view);
    }

    @Override
    public void onClose(StationView view) {
        book.shut(view);
        ghost.reset();
        choice.clear(view);
        known = false;
        craftSounds = 0;
    }

    @Override
    public void tick(StationView view) {
        if (craftSounds > 0 && --craftDelay <= 0) {
            craftSound(view);
            craftSounds--;
            craftDelay = CRAFT_SOUND_GAP;
        }
        choice.tick(view);
        if (!view.isFocused() || view.menu() == null) {
            return;
        }
        if (!known) {
            remember(view);
            return;
        }
        int put = 0;
        int taken = 0;
        for (int i = 0; i < grid.length; i++) {
            int now = view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, i)).getCount();
            if (now > grid[i]) {
                put++;
            } else if (now < grid[i]) {
                taken++;
            }
        }
        int crafts = 0;
        if (!result.isEmpty() && taken > 0) {
            // A craft: the grid shrank and the player holds more of what the result was.
            int gained = held(result) - resultHeld;
            crafts = gained <= 0 ? 0 : Math.max(1, gained / Math.max(1, result.getCount()));
        }
        if (put > 0) {
            // Something went into the grid: as the vanilla ghost goes at a click on the grid.
            ghost.clear();
        }
        if (crafts > 0) {
            crafted(view, crafts);
        } else if (put > 0 && net.minecraft.Util.getMillis() - laidAt >= LAID_FOR_MS) {
            // A laid recipe's items sound as they land, one by one.
            play(view, SoundEvents.ITEM_FRAME_ADD_ITEM, "grid", 0.35F, 0.9F + 0.2F * random(view));
        } else if (taken > 0) {
            play(view, SoundEvents.ITEM_FRAME_REMOVE_ITEM, "grid", 0.3F, 0.9F + 0.2F * random(view));
            swing();
        }
        remember(view);
    }

    private void crafted(StationView view, int crafts) {
        craftSound(view);
        // A bulk craft thins its repeats: four at the most, close together.
        craftSounds = Math.min(crafts, MAX_CRAFT_SOUNDS) - 1;
        craftDelay = CRAFT_SOUND_GAP;
        swing();
    }

    private void craftSound(StationView view) {
        play(view, SoundEvents.ITEM_PICKUP, HandsOnVanillaClient.RESULT, 0.3F, 0.55F + 0.1F * random(view));
        play(view, SoundEvents.WOOD_HIT, HandsOnVanillaClient.RESULT, 0.25F, 0.9F + 0.1F * random(view));
    }

    private void remember(StationView view) {
        for (int i = 0; i < grid.length; i++) {
            grid[i] = view.state().getItem(SlotRef.of(HandsOnVanillaClient.GRID, i)).getCount();
        }
        result = view.state().getOutput(HandsOnVanillaClient.RESULT).copy();
        resultHeld = result.isEmpty() ? 0 : held(result);
        known = true;
    }

    /** How many of {@code stack}'s item the local player holds: inventory and cursor. */
    private static int held(ItemStack stack) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
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
