package com.pockyl.handson_vanilla.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import com.pockyl.diegesis.api.client.DiegesisClient;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import com.pockyl.handson_vanilla.HandsOnVanilla;
import com.pockyl.handson_vanilla.client.StationHover;

import java.util.Optional;

/**
 * JEI at a station: its keys (recipes, uses, bookmark) work over the item under the cursor, and its "+" lays a crafting recipe into
 * the crafting table station the vanilla recipe book's way, which any server takes. Everywhere else JEI's own transfer works as
 * before.
 */
@JeiPlugin
public final class VanillaJeiPlugin implements IModPlugin {
    /** Room left between the station's right edge on the screen and JEI's list, GUI pixels. */
    private static final int MIDDLE_GAP = 6;

    @Override
    public ResourceLocation getPluginUid() {
        return HandsOnVanilla.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registerStationScreen(registration);
        IIngredientManager ingredients = registration.getJeiHelpers().getIngredientManager();
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
                ItemStack stack = StationHover.stack();
                if (stack.isEmpty()) {
                    return Optional.empty();
                }
                Rect2i area = new Rect2i((int) mouseX - 8, (int) mouseY - 8, 16, 16);
                return ingredients.createTypedIngredient(VanillaTypes.ITEM_STACK, stack).map(typed -> new Clickable<>(typed, area));
            }
        });
    }

    /**
     * JEI works on a screen only when it knows its layout: at a station of this mod the screen tells it the middle (the table, the
     * book, the hotbar) so that JEI's list sits on the right and its keys work. Other stations keep JEI off, as before.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerStationScreen(IGuiHandlerRegistration registration) {
        Class<Screen> station = (Class) DiegesisClient.stationScreenClass();
        registration.addGuiScreenHandler(station, screen -> {
            boolean ours = DiegesisClient.focusedStation()
                    .map(view -> view.type().id().getNamespace().equals(HandsOnVanilla.MOD_ID)).orElse(false);
            // From the click on: while the camera flies, the middle is where the table will be once it is there, so the list
            // takes its place at once and slides in with the camera (ViewerSlide) instead of following the table across the screen.
            if (!ours) {
                return null;
            }
            int width = screen.width;
            int height = screen.height;
            int middle = Math.min(width, Math.round(StationHover.rightEdge()) + MIDDLE_GAP);
            // JEI asks once as the screen opens, before it is sized: it rejects zero sizes with an error in the log, and asks
            // again on the next frame.
            if (width <= 1 || height <= 1 || middle <= 1) {
                return null;
            }
            return new IGuiProperties() {
                @Override
                public Class<? extends Screen> getScreenClass() {
                    return station;
                }

                @Override
                public int getGuiLeft() {
                    return 0;
                }

                @Override
                public int getGuiTop() {
                    return 0;
                }

                @Override
                public int getGuiXSize() {
                    return middle;
                }

                @Override
                public int getGuiYSize() {
                    return height;
                }

                @Override
                public int getScreenWidth() {
                    return width;
                }

                @Override
                public int getScreenHeight() {
                    return height;
                }
            };
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        IRecipeTransferHandler<CraftingMenu, CraftingRecipe> basic = helper.createUnregisteredRecipeTransferHandler(
                helper.createBasicRecipeTransferInfo(CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36));
        registration.addRecipeTransferHandler(new StationTransfer(basic, helper), RecipeTypes.CRAFTING);
    }

    private record Clickable<T>(ITypedIngredient<T> typed, Rect2i area) implements IClickableIngredient<T> {
        @Override
        public ITypedIngredient<T> getTypedIngredient() {
            return typed;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }
    }

    /** At the crafting table station: the vanilla book's packet. Anywhere else: JEI's own crafting transfer, unchanged. */
    private record StationTransfer(IRecipeTransferHandler<CraftingMenu, CraftingRecipe> basic, IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<CraftingMenu, CraftingRecipe> {
        @Override
        public Class<? extends CraftingMenu> getContainerClass() {
            return CraftingMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingMenu>> getMenuType() {
            return Optional.of(MenuType.CRAFTING);
        }

        @Override
        public RecipeType<CraftingRecipe> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public IRecipeTransferError transferRecipe(CraftingMenu container, CraftingRecipe recipe, IRecipeSlotsView slots, Player player,
                boolean maxTransfer, boolean doTransfer) {
            if (StationHover.menu() != container) {
                return basic.transferRecipe(container, recipe, slots, player, maxTransfer, doTransfer);
            }
            if (!(player instanceof LocalPlayer local) || !local.getRecipeBook().contains(recipe)) {
                return helper.createUserErrorWithTooltip(Component.translatable("handson_vanilla.jei.locked"));
            }
            if (doTransfer) {
                StationHover.placeRecipe(recipe.getId(), maxTransfer);
            }
            return null;
        }
    }
}
