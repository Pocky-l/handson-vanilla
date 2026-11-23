package com.pockyl.handson_vanilla.client;

import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Polymorph, reached by reflection (no compile dependency): the packets its screens' buttons send to choose a recipe, and the
 * choice its server keeps for an open furnace. Polymorph keeps a crafting or smithing table's choice for the player, and a
 * furnace's for the block itself, which it tells the players at that furnace's screen ({@link #listen}). Without Polymorph nothing
 * is sent and nothing is known.
 */
public final class PolymorphLink {
    private static boolean looked;
    /** Polymorph's packet sender: the distributor of its 1.20 API, the network of its 1.21 one. */
    private static Object sender;
    private static Method playerChoice;
    private static Method blockChoice;
    private static Method listener;
    /** The recipe Polymorph's server last named as the open block's choice, and how many of its lists came so far. */
    private static volatile ResourceLocation told;
    private static volatile int lists;

    private PolymorphLink() {
    }

    static boolean present() {
        return sender() != null;
    }

    /** The player's choice, as Polymorph's button at a crafting or smithing table sends it: the server makes that result. */
    static boolean choosePlayerRecipe(ResourceLocation recipe) {
        return send(playerChoice, recipe);
    }

    /** The open furnace's own choice, as Polymorph's furnace button sends it: the furnace cooks into it from now on. */
    static boolean chooseBlockRecipe(ResourceLocation recipe) {
        return send(blockChoice, recipe);
    }

    /**
     * Whether the server tells this player the open furnace's choice, as Polymorph's furnace screen asks while it is open: what the
     * furnace keeps then comes in Polymorph's list ({@link #listArrived}).
     */
    static void listen(boolean on) {
        send(listener, on);
    }

    /** How many of Polymorph's lists have come: a station takes what came after it opened. */
    static int lists() {
        return lists;
    }

    /** The recipe the last list named as the open block's choice, when it came after the first {@code seen}; null otherwise. */
    static ResourceLocation told(int seen) {
        return lists > seen ? told : null;
    }

    /** One of Polymorph's lists of an open block's recipes came from the server ({@code PolymorphListMixin}). */
    public static void listArrived(Object packet) {
        ResourceLocation selected = null;
        try {
            Object value = packet.getClass().getMethod("selected").invoke(packet);
            if (value instanceof Optional<?> optional) {
                value = optional.orElse(null);
            }
            selected = value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Another Polymorph: its lists say nothing here.
        }
        // An empty list names nothing (null): the station keeps what it knew.
        told = selected;
        lists++;
    }

    private static boolean send(Method method, Object argument) {
        Object to = sender();
        if (to == null || method == null) {
            return false;
        }
        try {
            method.invoke(to, argument);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static Object sender() {
        if (!looked) {
            looked = true;
            try {
                Class<?> api = Class.forName("com.illusivesoulworks.polymorph.api.PolymorphApi");
                Object found;
                Class<?> type;
                try {
                    Object instance = api.getMethod("getInstance").invoke(null);
                    found = api.getMethod("getNetwork").invoke(instance);
                    type = Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphNetwork");
                } catch (NoSuchMethodException | ClassNotFoundException older) {
                    Class<?> common = Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphCommon");
                    found = common.getMethod("getPacketDistributor").invoke(api.getMethod("common").invoke(null));
                    type = Class.forName("com.illusivesoulworks.polymorph.api.common.base.IPolymorphPacketDistributor");
                }
                playerChoice = type.getMethod("sendPlayerRecipeSelectionC2S", ResourceLocation.class);
                blockChoice = type.getMethod("sendPersistentRecipeSelectionC2S", ResourceLocation.class);
                listener = type.getMethod("sendBlockEntityListenerC2S", boolean.class);
                sender = found;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                sender = null;
            }
        }
        return sender;
    }
}
