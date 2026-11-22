package com.pockyl.handson_vanilla.client;

import net.minecraft.world.item.component.SwingAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.LightCoordsUtil;
import com.pockyl.diegesis.api.client.SceneBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;

import com.pockyl.handson_vanilla.HandsOnVanilla;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Crafting Tweaks puts three buttons beside a crafting grid: rotate it, balance it (shift: spread it), clear it back into the
 * inventory (shift: even if that drops things). At the table they are charcoal marks on the far edge of the top, its own signs
 * drawn by hand, with its own words in the tooltip; shift turns each to its other use, as it turns the buttons. A click does
 * what the button does, through Crafting Tweaks itself: its packet when the server has it, its own clicks when not. Its keys
 * work at the table too (rotate, balance, spread, clear, refill the last recipe). Nothing shows without Crafting Tweaks, or
 * when its config takes the buttons away.
 */
final class GridTweaks {
    /** A mark: its node, Crafting Tweaks' tweak and the one with shift, and where both drawings lie in the sheet. */
    enum Mark {
        ROTATE("tweak_rotate", "Rotate", "RotateCounterClockwise", 0, 1, SoundEvents.ITEM_FRAME_ROTATE_ITEM),
        BALANCE("tweak_balance", "Balance", "Spread", 2, 3, SoundEvents.ITEM_FRAME_ADD_ITEM),
        CLEAR("tweak_clear", "Clear", "ForceClear", 4, 5, SoundEvents.ITEM_FRAME_REMOVE_ITEM);

        final String node;
        final String tweak;
        final String shiftTweak;
        final int drawing;
        final int shiftDrawing;
        final SoundEvent sound;

        Mark(String node, String tweak, String shiftTweak, int drawing, int shiftDrawing, SoundEvent sound) {
            this.node = node;
            this.tweak = tweak;
            this.shiftTweak = shiftTweak;
            this.drawing = drawing;
            this.shiftDrawing = shiftDrawing;
            this.sound = sound;
        }

        static Mark of(String node) {
            for (Mark mark : values()) {
                if (mark.node.equals(node)) {
                    return mark;
                }
            }
            return null;
        }
    }

    private static final Identifier SHEET = HandsOnVanilla.id("textures/entity/grid_marks.png");
    private static final int DRAWINGS = 7;
    /** Inventory Profiles Next's continuous crafting, drawn by {@link ContinuousCrafting}. */
    static final int REPEAT_DRAWING = 6;
    /** A mark's side in pixels, how much larger it is under the cursor, how high it lies over the wood. */
    private static final float SIZE = 3.0F;
    private static final float HOVER_GROWTH = 1.1F;
    private static final float LIFT = 0.03F;

    private static final String API = "net.blay09.mods.craftingtweaks.";
    private static Bridge bridge;
    private static boolean looked;

    private boolean shown;

    void onSceneReady(StationView view) {
        for (Mark mark : Mark.values()) {
            view.node(mark.node).ifPresent(node -> {
                node.setVisible(false);
                node.setPainter((poseStack, buffers, light) -> paint(node, mark, poseStack, buffers, light));
            });
        }
    }

    /** Every frame at the table: the marks show while Crafting Tweaks has buttons for this grid; their words follow shift. */
    void frame(StationView view) {
        Bridge api = bridge();
        boolean show = api != null && view.menu() != null && view.isFocused() && api.grid(view.menu().handle()).isPresent()
                && api.buttons();
        boolean shift = Minecraft.getInstance().hasShiftDown();
        for (Mark mark : Mark.values()) {
            view.node(mark.node).ifPresent(node -> {
                if (show != shown) {
                    node.setVisible(show);
                }
                if (show) {
                    node.setTooltip(Component.translatable(tooltip(shift ? mark.shiftTweak : mark.tweak)));
                }
            });
        }
        shown = show;
    }

    /** A mark was clicked: Crafting Tweaks does to the grid what its button does. */
    boolean click(StationView view, String node) {
        Mark mark = Mark.of(node);
        Bridge api = bridge();
        if (mark == null || api == null || view.menu() == null) {
            return mark != null;
        }
        if (api.tweak(view.menu().handle(), Minecraft.getInstance().hasShiftDown() ? mark.shiftTweak : mark.tweak)) {
            float volume = 0.3F * VanillaConfig.soundVolume();
            if (volume > 0.0F) {
                view.playSound(mark.sound, node, volume, 1.0F);
            }
            swing();
        }
        return true;
    }

    /** Crafting Tweaks' keys, bound as the player bound them: true when one was taken. */
    boolean key(StationView view, int keyCode, int scanCode, int modifiers) {
        Bridge api = bridge();
        if (api == null || view.menu() == null || !api.hotkeys()) {
            return false;
        }
        AbstractContainerMenu menu = view.menu().handle();
        if (api.grid(menu).isEmpty()) {
            return false;
        }
        for (String[] pair : Bridge.KEYS) {
            if (api.pressed(pair[0], keyCode, scanCode, modifiers)) {
                boolean done = pair[1].startsWith("refill") ? api.refill(menu, pair[1].endsWith("stack"))
                        : api.tweak(menu, pair[1]);
                if (done) {
                    swing();
                }
                return true;
            }
        }
        return false;
    }

    void clear(StationView view) {
        shown = false;
        for (Mark mark : Mark.values()) {
            view.node(mark.node).ifPresent(node -> node.setVisible(false));
        }
    }

    /** Lying flat on the wood, the top of the drawing to the far edge; a little larger and lit under the cursor. */
    private void paint(NodeView node, Mark mark, PoseStack poseStack, SceneBuffers buffers, int light) {
        draw(poseStack, buffers, Minecraft.getInstance().hasShiftDown() ? mark.shiftDrawing : mark.drawing, node.isHovered(), light, 1.0F);
    }

    /** One drawing of the sheet lying flat on the wood at a node, {@code alpha} of its charcoal; larger and lit when hovered. */
    static void draw(PoseStack poseStack, SceneBuffers buffers, int drawing, boolean hovered, int light, float alpha) {
        float half = SIZE * 0.5F * (hovered ? HOVER_GROWTH : 1.0F);
        float u0 = (float) drawing / DRAWINGS;
        float u1 = (float) (drawing + 1) / DRAWINGS;
        int lit = hovered ? LightCoordsUtil.FULL_BRIGHT : light;
        int a = Math.round(255.0F * alpha);
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityTranslucent(SHEET));
        PoseStack.Pose pose = poseStack.last();
        // The camera looks along +z from the near edge, so +x is on its left: the drawing's left edge lies at +x.
        vertex(consumer, pose, half, -half, u0, 1.0F, lit, a);
        vertex(consumer, pose, -half, -half, u1, 1.0F, lit, a);
        vertex(consumer, pose, -half, half, u1, 0.0F, lit, a);
        vertex(consumer, pose, half, half, u0, 0.0F, lit, a);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float z, float u, float v, int light, int alpha) {
        consumer.addVertex(pose, x, LIFT, z).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static String tooltip(String tweak) {
        return switch (tweak) {
            case "Rotate", "RotateCounterClockwise" -> "tooltip.craftingtweaks.rotate";
            case "Balance" -> "tooltip.craftingtweaks.balance";
            case "Spread" -> "tooltip.craftingtweaks.spread";
            case "ForceClear" -> "tooltip.craftingtweaks.forceClear";
            default -> "tooltip.craftingtweaks.clear";
        };
    }

    /** The arm moves as the hand works; only this client sees it. */
    static void swing() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        }
    }

    private static Bridge bridge() {
        if (!looked) {
            looked = true;
            try {
                bridge = new Bridge();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                if (!(e instanceof ClassNotFoundException)) {
                    HandsOnVanilla.LOGGER.warn("Crafting Tweaks is here but its API was not found; no grid tweaks at the table", e);
                }
                bridge = null;
            }
        }
        return bridge;
    }

    /**
     * Crafting Tweaks reached by reflection, with no dependency at compile time. Only its own names are looked up (they are not
     * remapped); Minecraft's types come back as ours.
     */
    private static final class Bridge {
        /** Its key mappings (fields of {@code ModKeyMappings}) and what each does. */
        static final String[][] KEYS = {
                {"keyRotate", "Rotate"}, {"keyRotateCounterClockwise", "RotateCounterClockwise"}, {"keyBalance", "Balance"},
                {"keySpread", "Spread"}, {"keyClear", "Clear"}, {"keyForceClear", "ForceClear"}, {"keyRefillLast", "refill"},
                {"keyRefillLastStack", "refill_stack"}};

        private final Method defaultGrid;
        private final Method createButton;
        private final Method buttonClicked;
        private final Class<?> tweakType;
        private final Method activeConfig;
        private final Method modeFor;
        private final Method gridId;
        private final Method clientProvider;
        private final Method refillLast;
        private final Method matchesKey;
        private final Method modifiersActive;
        private final Class<?> keyMappings;

        Bridge() throws ReflectiveOperationException {
            Class<?> manager = Class.forName(API + "CraftingTweaksProviderManager");
            Class<?> grid = Class.forName(API + "api.CraftingGrid");
            tweakType = Class.forName(API + "api.TweakType");
            defaultGrid = manager.getMethod("getDefaultCraftingGrid", AbstractContainerMenu.class);
            createButton = Class.forName(API + "api.CraftingTweaksClientAPI").getMethod("createTweakButton", grid, int.class, int.class,
                    tweakType);
            buttonClicked = Class.forName(API + "client.GuiTweakButton").getDeclaredMethod("onTweakButtonClicked", Player.class,
                    AbstractContainerMenu.class, grid, tweakType);
            buttonClicked.setAccessible(true);
            Class<?> config = Class.forName(API + "config.CraftingTweaksConfig");
            activeConfig = config.getMethod("getActive");
            modeFor = activeConfig.getReturnType().getMethod("getCraftingTweaksMode", String.class);
            gridId = grid.getMethod("getId");
            Class<?> client = Class.forName(API + "client.CraftingTweaksClient");
            clientProvider = client.getMethod("getClientProvider");
            refillLast = clientProvider.getReturnType().getMethod("refillLastCrafted", Player.class, AbstractContainerMenu.class, grid,
                    boolean.class);
            keyMappings = Class.forName(API + "client.ModKeyMappings");
            Class<?> mapping = Class.forName("net.blay09.mods.kuma.api.ManagedKeyMapping");
            matchesKey = mapping.getMethod("matchesKey", int.class, int.class, int.class);
            modifiersActive = mapping.getMethod("areModifiersActive");
        }

        /** The menu's crafting grid as Crafting Tweaks knows it. */
        Optional<?> grid(AbstractContainerMenu menu) {
            try {
                return (Optional<?>) defaultGrid.invoke(null, menu);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return Optional.empty();
            }
        }

        boolean buttons() {
            String mode = mode();
            return "DEFAULT".equals(mode) || "BUTTONS".equals(mode);
        }

        boolean hotkeys() {
            String mode = mode();
            return "DEFAULT".equals(mode) || "HOTKEYS".equals(mode);
        }

        /** Its mode for the vanilla grid ("DEFAULT", "BUTTONS", "HOTKEYS", "DISABLED"). */
        private String mode() {
            try {
                Object mode = modeFor.invoke(activeConfig.invoke(null), "minecraft");
                return mode instanceof Enum<?> e ? e.name() : "DEFAULT";
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "DEFAULT";
            }
        }

        /** What its button for {@code tweak} does, done by its own button code. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        boolean tweak(AbstractContainerMenu menu, String tweak) {
            Player player = Minecraft.getInstance().player;
            Object grid = grid(menu).orElse(null);
            if (player == null || grid == null) {
                return false;
            }
            try {
                Object type = Enum.valueOf((Class) tweakType, tweak);
                Object button = createButton.invoke(null, grid, 0, 0, type);
                buttonClicked.invoke(button, player, menu, grid, type);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Crafting Tweaks' {} failed at the table", tweak, e);
                return false;
            }
        }

        /** Its refill of the last crafted recipe into the grid: one each, or a stack each. */
        boolean refill(AbstractContainerMenu menu, boolean stack) {
            Player player = Minecraft.getInstance().player;
            Object grid = grid(menu).orElse(null);
            if (player == null || grid == null) {
                return false;
            }
            try {
                refillLast.invoke(clientProvider.invoke(null), player, menu, grid, stack);
                return true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                HandsOnVanilla.LOGGER.warn("Crafting Tweaks' refill failed at the table", e);
                return false;
            }
        }

        /** Whether its key mapping {@code field} is bound to this key, with its modifiers held. */
        boolean pressed(String field, int keyCode, int scanCode, int modifiers) {
            try {
                Field at = keyMappings.getField(field);
                Object mapping = at.get(null);
                return mapping != null && (boolean) matchesKey.invoke(mapping, keyCode, scanCode, modifiers)
                        && (boolean) modifiersActive.invoke(mapping);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
