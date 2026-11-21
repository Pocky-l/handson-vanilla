package com.pockyl.handson_vanilla.client;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Easy Magic's enchanting table: its own menu (the vanilla one with the item and lapis kept in the table) and a button that writes
 * new offers for a price (lapis and experience points, its server config). At the table that button is the right page's corner,
 * turned to a fresh page. Reached by reflection, with no dependency at compile time; only Easy Magic's own names are looked up.
 */
final class EasyMagicReroll {
    /** The menu button Easy Magic's screen presses for new offers. */
    static final int BUTTON = 4;
    private static final String MENU = "fuzs.easymagic.world.inventory.ModEnchantmentMenu";

    private static Bridge bridge;
    private static boolean looked;

    private EasyMagicReroll() {
    }

    /** Whether new offers can be bought at this menu now: Easy Magic's, rerolls on in its config, an enchantable item. */
    static boolean offered(AbstractContainerMenu menu) {
        Bridge api = bridge(menu);
        return api != null && api.enabled() && api.enchantable(menu);
    }

    /** Lapis the new offers cost, and how much the table holds. */
    static int lapisCost(AbstractContainerMenu menu) {
        Bridge api = bridge(menu);
        return api == null ? 0 : api.intField("rerollCatalystCost");
    }

    static int lapisHeld(AbstractContainerMenu menu) {
        Bridge api = bridge(menu);
        return api == null ? 0 : api.catalysts(menu);
    }

    /** Experience points (not levels) the new offers cost, and how many the player has. */
    static int pointsCost(AbstractContainerMenu menu) {
        Bridge api = bridge(menu);
        return api == null ? 0 : api.intField("rerollExperiencePointsCost");
    }

    static int pointsHeld(Player player) {
        return totalExperience(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** Vanilla's points needed to reach {@code level} from 0. */
    private static int totalExperience(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** Whether the player can pay: Easy Magic's own rule (enough lapis and points, or creative). */
    static boolean canPay(AbstractContainerMenu menu, Player player) {
        Bridge api = bridge(menu);
        return api != null && api.canPay(menu, player);
    }

    /**
     * Easy Magic's clues for offer {@code row}, or null when this is not its menu: it leaves vanilla's clue slots empty and sends its
     * own (none, one, or every enchantment of the offer, by its {@code enchantmentHint} server option) into the open menu.
     */
    static List<EnchantmentInstance> clues(AbstractContainerMenu menu, int row) {
        Bridge api = bridge(menu);
        return api == null ? null : api.clues(menu, row);
    }

    private static Bridge bridge(AbstractContainerMenu menu) {
        if (menu == null || !menu.getClass().getName().equals(MENU)) {
            return null;
        }
        if (!looked) {
            looked = true;
            try {
                bridge = new Bridge(menu.getClass());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Easy Magic's menu is here but its reroll was not found; no new offers at the table", e);
                bridge = null;
            }
        }
        return bridge;
    }

    private static final class Bridge {
        private final Method enchantable;
        private final Field clues;
        private final Method canPay;
        private final Method catalysts;
        private final Field config;
        private final Method get;
        private final Class<?> serverConfig;

        Bridge(Class<?> menu) throws ReflectiveOperationException {
            enchantable = menu.getMethod("canEnchantItem");
            clues = menu.getField("clues");
            canPay = menu.getMethod("canUseReroll", Player.class);
            catalysts = menu.getMethod("getRerollCatalystCount");
            Class<?> mod = Class.forName("fuzs.easymagic.EasyMagic");
            config = mod.getField("CONFIG");
            serverConfig = Class.forName("fuzs.easymagic.config.ServerConfig");
            Method found = null;
            for (Method method : config.getType().getMethods()) {
                if (method.getName().equals("get") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Class.class) {
                    found = method;
                }
            }
            if (found == null) {
                throw new NoSuchMethodException("ConfigHolder.get(Class)");
            }
            get = found;
        }

        boolean enabled() {
            Object server = server();
            try {
                return server != null && serverConfig.getField("rerollEnchantments").getBoolean(server);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        int intField(String name) {
            Object server = server();
            try {
                return server == null ? 0 : serverConfig.getField(name).getInt(server);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return 0;
            }
        }

        @SuppressWarnings("unchecked")
        List<EnchantmentInstance> clues(AbstractContainerMenu menu, int row) {
            try {
                List<?> rows = (List<?>) clues.get(menu);
                if (row < rows.size() && rows.get(row) instanceof List<?> list) {
                    return (List<EnchantmentInstance>) list;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Unreadable: no clue.
            }
            return List.of();
        }

        boolean enchantable(AbstractContainerMenu menu) {
            try {
                return (boolean) enchantable.invoke(menu);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        boolean canPay(AbstractContainerMenu menu, Player player) {
            try {
                return (boolean) canPay.invoke(menu, player);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        int catalysts(AbstractContainerMenu menu) {
            try {
                return (int) catalysts.invoke(menu);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return 0;
            }
        }

        private Object server() {
            try {
                return get.invoke(config.get(null), serverConfig);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
    }
}
