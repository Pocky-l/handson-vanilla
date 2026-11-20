package com.pockyl.handson_vanilla.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;

import com.pockyl.diegesis.api.client.StationView;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Method;

/**
 * Inventory Profiles Next's continuous crafting: after a craft it lays the same ingredients into the grid again from the inventory
 * (and with its modifier held, crafts on by itself). On its crafting screen that is a checkbox; at the table it is a charcoal loop
 * of two arrows on the near edge of the top, faint while off and dark while on, its own words as the tooltip. Its handler only
 * runs under a container screen: {@code ContinuousCraftingMixin} lets it run at the table too, through {@link #atStation}. Its
 * rule for the checkbox on opening (remember, always on, always off) holds at the table. Reached by reflection, with no
 * dependency at compile time; only its own names are looked up.
 */
public final class ContinuousCrafting {
    static final String NODE = "ipn_continuous";
    private static final float FAINT = 0.35F;

    private static Bridge bridge;
    private static boolean looked;
    /** The menu its handler was started for at a station, so it starts again for the next one. */
    private static AbstractContainerMenu tracked;

    void onSceneReady(StationView view) {
        view.node(NODE).ifPresent(node -> {
            node.setVisible(false);
            node.setPainter((poseStack, buffers, light) -> {
                Bridge api = bridge();
                boolean on = api != null && api.enabled();
                GridTweaks.draw(poseStack, buffers, GridTweaks.REPEAT_DRAWING, node.isHovered(), light, on ? 1.0F : FAINT);
            });
        });
    }

    /** At the table's opening: its checkbox rule, as its screen applies it when it opens. */
    void opened() {
        Bridge api = bridge();
        if (api != null) {
            api.applyOpeningRule();
        }
    }

    void frame(StationView view) {
        Bridge api = bridge();
        boolean show = api != null && api.shown() && view.isFocused() && view.menu() != null;
        view.node(NODE).ifPresent(node -> {
            node.setVisible(show);
            if (show) {
                node.setTooltip(Component.literal(api.tooltip()));
            }
        });
    }

    /** A click on the loop turns continuous crafting on or off, as its checkbox does (and saves it, as it does). */
    boolean click(StationView view, String node) {
        if (!NODE.equals(node)) {
            return false;
        }
        Bridge api = bridge();
        if (api == null) {
            return true;
        }
        boolean on = !api.enabled();
        api.setEnabled(on);
        float volume = 0.3F * VanillaConfig.soundVolume();
        if (volume > 0.0F) {
            view.playSound(on ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM, NODE, volume, on ? 1.2F : 0.9F);
        }
        GridTweaks.swing();
        return true;
    }

    void clear(StationView view) {
        view.node(NODE).ifPresent(node -> node.setVisible(false));
    }

    /**
     * Called by the mixin at the head of its handler's tick, {@code handler} being it: true when a crafting table station is worked
     * now, so this ran its handler for that menu and its own screen check is skipped.
     */
    public static boolean atStation(Object handler) {
        AbstractContainerMenu menu = StationHover.menu();
        if (!(menu instanceof CraftingMenu)) {
            tracked = null;
            return false;
        }
        Bridge api = bridge();
        if (api == null || !api.enabled()) {
            return true;
        }
        try {
            if (menu != tracked) {
                tracked = menu;
                api.init.invoke(handler);
            }
            api.handle.invoke(handler);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HandsOnVanilla.LOGGER.warn("Inventory Profiles Next's continuous crafting failed at the table", e);
            bridge = null;
        }
        return true;
    }

    private static Bridge bridge() {
        if (!looked) {
            looked = true;
            try {
                bridge = new Bridge();
            } catch (ClassNotFoundException e) {
                bridge = null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Inventory Profiles Next is here but its continuous crafting was not found", e);
                bridge = null;
            }
        }
        return bridge;
    }

    private static final class Bridge {
        private final Object settings;
        private final Method savedValue;
        private final Method checkboxRule;
        private final Method showCheckbox;
        private final Method inventoryButtons;
        private final Object saver;
        private final Method save;
        final Method init;
        final Method handle;

        Bridge() throws ReflectiveOperationException {
            Class<?> gui = Class.forName("org.anti_ad.mc.ipnext.config.GuiSettings");
            settings = gui.getField("INSTANCE").get(null);
            savedValue = gui.getMethod("getCONTINUOUS_CRAFTING_SAVED_VALUE");
            checkboxRule = gui.getMethod("getCONTINUOUS_CRAFTING_CHECKBOX_VALUE");
            showCheckbox = gui.getMethod("getSHOW_CONTINUOUS_CRAFTING_CHECKBOX");
            inventoryButtons = gui.getMethod("getENABLE_INVENTORY_BUTTONS");
            Class<?> manager = Class.forName("org.anti_ad.mc.ipnext.config.SaveLoadManager");
            saver = manager.getField("INSTANCE").get(null);
            save = manager.getMethod("save");
            Class<?> handler = Class.forName("org.anti_ad.mc.ipnext.event.ContinuousCraftingHandler");
            init = handler.getDeclaredMethod("init");
            init.setAccessible(true);
            handle = handler.getDeclaredMethod("handle");
            handle.setAccessible(true);
        }

        boolean enabled() {
            return bool(option(savedValue));
        }

        /** Whether its checkbox shows on crafting screens: then its loop lies on the table. */
        boolean shown() {
            return bool(option(inventoryButtons)) && bool(option(showCheckbox));
        }

        void setEnabled(boolean on) {
            try {
                Object option = option(savedValue);
                for (Method method : option.getClass().getMethods()) {
                    if (method.getName().equals("setValue") && method.getParameterCount() == 1) {
                        method.invoke(option, on);
                        break;
                    }
                }
                save.invoke(saver);
            } catch (ReflectiveOperationException | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Inventory Profiles Next's continuous crafting could not be set", e);
            }
        }

        void applyOpeningRule() {
            try {
                Object rule = option(checkboxRule).getClass().getMethod("getValue").invoke(option(checkboxRule));
                String name = rule instanceof Enum<?> e ? e.name() : "";
                if (name.equals("CHECKED") && !enabled()) {
                    setEnabled(true);
                } else if (name.equals("UNCHECKED") && enabled()) {
                    setEnabled(false);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Its rule unknown: the saved value stays.
            }
        }

        /** Its checkbox's tooltip, first line (the second names its auto-craft modifier). */
        String tooltip() {
            String text = I18n.get("inventoryprofiles.tooltip.continuous_crafting_checkbox", "");
            int end = text.indexOf('\n');
            return end < 0 ? text : text.substring(0, end);
        }

        private Object option(Method getter) {
            try {
                return getter.invoke(settings);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        private static boolean bool(Object option) {
            if (option == null) {
                return false;
            }
            try {
                return (boolean) option.getClass().getMethod("getBooleanValue").invoke(option);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
