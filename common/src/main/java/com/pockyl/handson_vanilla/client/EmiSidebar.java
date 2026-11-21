package com.pockyl.handson_vanilla.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EMI's sidebars at a station of this mod. EMI draws them, and hears the mouse and the keys for them, over container screens only
 * (it hooks those), and a station's screen is none: once {@code VanillaEmiPlugin} gives EMI the station's middle, this draws them after
 * the station's screen and offers EMI the station's input first ({@code StationInputMixin}), as EMI does for a container screen.
 * It goes through EMI's internal screen manager, looked up by name: with an EMI that has it elsewhere there are no sidebars at the
 * station, logged once, and nothing else changes.
 */
public final class EmiSidebar {
    /** EMI's search field, GUI pixels: its own width and height, and the room left round it under the list. */
    private static final int SEARCH_WIDTH = 160;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SEARCH_MARGIN = 3;
    private static MethodHandle wrap;
    private static MethodHandle render;
    private static MethodHandle foreground;
    private static MethodHandle relayout;
    private static MethodHandle mouseClicked;
    private static MethodHandle mouseReleased;
    private static MethodHandle mouseDragged;
    private static MethodHandle mouseScrolled;
    private static MethodHandle keyPressed;
    private static MethodHandle search;
    private static MethodHandle recipePage;
    private static MethodHandle addWidgets;
    private static MethodHandle clearHistory;
    private static MethodHandle centerSearch;
    /** The station's screen and size EMI's widgets were last laid out for. */
    private static Screen laidOut;
    private static int laidWidth;
    private static int laidHeight;
    private static MethodHandle lookedUp;
    private static boolean looked;
    private static boolean working;
    /** Whether the last frame was drawn moved for the slide: one more frame is laid out anew after it ends. */
    private static boolean slid;

    private EmiSidebar() {
    }

    /** After the screen is drawn (and moved for the viewers' slide, {@link ViewerSlide}). */
    public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!on(screen)) {
            return;
        }
        try {
            Object context = wrap.invoke(graphics);
            // EMI bakes its stacks where they are drawn when it lays them out and keeps them there: while the list slides in with
            // the camera, and once when it has arrived, they are laid out anew.
            boolean sliding = ViewerSlide.offset(screen) > 0.5F;
            if (sliding || slid) {
                relayout.invoke();
            }
            slid = sliding;
            layOut(screen);
            // EMI forgets the pages gone through when a container screen shows again, so that Escape on a page goes back to it, not
            // to an older page: the same when the station shows again.
            clearHistory.invoke();
            // Over the station's scene, as over a flat screen: EMI's stacks are drawn at depths the scene has used already.
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            graphics.pose().pushPose();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            render.invoke(context, mouseX, mouseY, partialTick);
            foreground.invoke(context, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        } catch (Throwable failure) {
            broken(failure);
        }
    }

    /**
     * EMI lays out its search field and buttons as a container screen opens, and a station's screen is none: they are laid out when
     * the station is first drawn, after a resize and after one of EMI's pages. A search field EMI puts in the middle at the bottom
     * would lie over the hotbar: it goes under EMI's list instead ({@link #searchSlot}).
     */
    private static void layOut(Screen screen) throws Throwable {
        if (screen != laidOut || screen.width != laidWidth || screen.height != laidHeight) {
            laidOut = screen;
            laidWidth = screen.width;
            laidHeight = screen.height;
            addWidgets.invoke(screen);
        }
        int[] slot = searchSlot(screen);
        if (slot != null && search.invoke() instanceof AbstractWidget field) {
            field.setWidth(Math.min(SEARCH_WIDTH, slot[2] - 2 * SEARCH_MARGIN));
            field.setX(slot[0] + (slot[2] - field.getWidth()) / 2);
            field.setY(slot[1] + SEARCH_MARGIN);
        }
    }

    /**
     * Where EMI's search field goes at a station of this mod, {x, y, width, height} in GUI pixels: the bottom of the right side, under
     * EMI's list (kept out of it as an exclusion area), when EMI's settings put it in the middle at the bottom; else null, EMI's own
     * place being clear of the station.
     */
    public static int[] searchSlot(Screen screen) {
        if (!on(screen)) {
            return null;
        }
        try {
            if (!(boolean) centerSearch.invoke()) {
                return null;
            }
        } catch (Throwable failure) {
            broken(failure);
            return null;
        }
        int middle = StationHover.viewerMiddle(screen.width);
        int height = SEARCH_HEIGHT + 2 * SEARCH_MARGIN;
        return new int[] {middle, screen.height - height, screen.width - middle, height};
    }

    public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        return on(screen) && ask(mouseClicked, mouseX, mouseY, button);
    }

    public static boolean mouseReleased(Screen screen, double mouseX, double mouseY, int button) {
        return on(screen) && ask(mouseReleased, mouseX, mouseY, button);
    }

    public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button, double dragX, double dragY) {
        return on(screen) && ask(mouseDragged, mouseX, mouseY, button, dragX, dragY);
    }

    public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
        return on(screen) && ask(mouseScrolled, mouseX, mouseY, amount);
    }

    /** A key: EMI's (recipes, uses, favourite over an item; its search while typed into), unless the book's search is typed into. */
    public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
        return on(screen) && !RecipeBook.typing() && ask(keyPressed, keyCode, scanCode, modifiers);
    }

    /** A character: into EMI's search while it is typed into. */
    public static boolean charTyped(Screen screen, char character, int modifiers) {
        if (!on(screen) || RecipeBook.typing()) {
            return false;
        }
        try {
            return search.invoke() instanceof GuiEventListener field && field.charTyped(character, modifiers);
        } catch (Throwable failure) {
            broken(failure);
            return false;
        }
    }

    /**
     * EMI's recipe pages for a stack, asked for at a station of this mod: EMI opens them over container screens only, so they open
     * here, over the station's never-shown container screen (going back to it is going back to the station), as EMI opens them.
     * False when not at such a station, EMI going on as before.
     */
    public static boolean openPages(Map<?, ? extends List<?>> pages, Object stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!on(minecraft.screen) || recipePage == null) {
            return false;
        }
        AbstractContainerScreen<?> under = StationHover.menuScreen();
        if (under == null) {
            return false;
        }
        Map<Object, Object> shown = new HashMap<>();
        pages.forEach((category, recipes) -> {
            if (!recipes.isEmpty()) {
                shown.put(category, recipes);
            }
        });
        if (shown.isEmpty()) {
            return true;
        }
        // The page moves EMI's widgets: laid out anew back at the station.
        laidOut = null;
        try {
            lookedUp.invoke(stack);
            minecraft.setScreen((Screen) recipePage.invoke(under, shown));
        } catch (Throwable failure) {
            broken(failure);
        }
        return true;
    }

    private static boolean on(Screen screen) {
        if (!looked) {
            look();
        }
        return working && StationHover.isOurs(screen);
    }

    private static boolean ask(MethodHandle handler, Object... arguments) {
        try {
            return (boolean) handler.invokeWithArguments(arguments);
        } catch (Throwable failure) {
            broken(failure);
            return false;
        }
    }

    private static void broken(Throwable failure) {
        working = false;
        HandsOnVanilla.LOGGER.warn("EMI's sidebars failed at the station, left out from now on", failure);
    }

    private static void look() {
        looked = true;
        ClassLoader loader = EmiSidebar.class.getClassLoader();
        Class<?> manager;
        Class<?> context;
        try {
            manager = Class.forName("dev.emi.emi.screen.EmiScreenManager", false, loader);
            context = Class.forName("dev.emi.emi.runtime.EmiDrawContext", false, loader);
        } catch (ClassNotFoundException | LinkageError absent) {
            // No EMI.
            return;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType drawing = MethodType.methodType(void.class, context, int.class, int.class, float.class);
            MethodType click = MethodType.methodType(boolean.class, double.class, double.class, int.class);
            wrap = lookup.findStatic(context, "wrap", MethodType.methodType(context, GuiGraphics.class));
            render = lookup.findStatic(manager, "render", drawing);
            foreground = lookup.findStatic(manager, "drawForeground", drawing);
            relayout = lookup.findStatic(manager, "forceRecalculate", MethodType.methodType(void.class));
            mouseClicked = lookup.findStatic(manager, "mouseClicked", click);
            mouseReleased = lookup.findStatic(manager, "mouseReleased", click);
            mouseDragged = lookup.findStatic(manager, "mouseDragged",
                    MethodType.methodType(boolean.class, double.class, double.class, int.class, double.class, double.class));
            mouseScrolled = lookup.findStatic(manager, "mouseScrolled",
                    MethodType.methodType(boolean.class, double.class, double.class, double.class));
            keyPressed = lookup.findStatic(manager, "keyPressed", MethodType.methodType(boolean.class, int.class, int.class, int.class));
            search = lookup.findStaticGetter(manager, "search", Class.forName("dev.emi.emi.screen.widget.EmiSearchWidget", false, loader));
            Class<?> ingredient = Class.forName("dev.emi.emi.api.stack.EmiIngredient", false, loader);
            recipePage = lookup.findConstructor(Class.forName("dev.emi.emi.screen.RecipeScreen", false, loader), MethodType.methodType(
                    void.class, AbstractContainerScreen.class, Map.class));
            lookedUp = lookup.findStatic(Class.forName("dev.emi.emi.runtime.EmiSidebars", false, loader), "lookup",
                    MethodType.methodType(void.class, ingredient));
            addWidgets = lookup.findStatic(manager, "addWidgets", MethodType.methodType(void.class, Screen.class));
            clearHistory = lookup.findStatic(Class.forName("dev.emi.emi.runtime.EmiHistory", false, loader), "clear",
                    MethodType.methodType(void.class));
            centerSearch = lookup.findStaticGetter(Class.forName("dev.emi.emi.config.EmiConfig", false, loader), "centerSearchBar",
                    boolean.class);
            working = true;
        } catch (ReflectiveOperationException | LinkageError changed) {
            HandsOnVanilla.LOGGER.warn("This EMI draws its sidebars differently: none at the stations", changed);
        }
    }
}
